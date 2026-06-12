#!/usr/bin/env python3
"""
Tests for PLAIN backend API.
Run: pytest test_main.py -v
"""
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from database import Base, get_db
from models import User, Plan
from main import app
import os

# Use file-based SQLite for testing (in-memory is per-connection)
TEST_DATABASE_URL = "sqlite:///./test.db"
test_engine = create_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
TestSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)

# Override the get_db dependency BEFORE creating TestClient
def override_get_db():
    db = TestSessionLocal()
    try:
        yield db
    finally:
        db.close()

app.dependency_overrides[get_db] = override_get_db

# Create tables BEFORE any tests run
Base.metadata.create_all(bind=test_engine)

client = TestClient(app)


@pytest.fixture
def db_session():
    """Create a database session for each test."""
    db = TestSessionLocal()
    try:
        yield db
    finally:
        db.close()


@pytest.fixture
def test_user(db_session):
    """Create a test user with valid password hash."""
    from auth import hash_password
    # Use unique email/username per test run to avoid conflicts
    import uuid
    unique = uuid.uuid4().hex[:8]
    user = User(
        username=f"testuser_{unique}",
        email=f"test_{unique}@example.com",
        password_hash=hash_password("password123"),
    )
    db_session.add(user)
    db_session.commit()
    db_session.refresh(user)
    return user


@pytest.fixture
def auth_token(test_user):
    """Generate auth token for test user."""
    from auth import create_access_token
    return create_access_token(test_user.id)


def test_root_endpoint():
    """Test root endpoint returns app info."""
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["app"] == "PLAIN API"
    assert data["version"] == "1.0.0"


def test_register_user(db_session):
    """Test user registration."""
    response = client.post("/api/register", json={
        "username": "newuser_test",
        "email": "new_test@example.com",
        "password": "password123"
    })
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["user"]["username"] == "newuser_test"
    assert data["user"]["email"] == "new_test@example.com"


def test_register_duplicate_username():
    """Test registering with existing username fails."""
    # First user
    client.post("/api/register", json={
        "username": "dupuser",
        "email": "dup1@example.com",
        "password": "password123"
    })
    
    # Second user with same username
    response = client.post("/api/register", json={
        "username": "dupuser",
        "email": "dup2@example.com",
        "password": "password123"
    })
    assert response.status_code == 400
    assert "Usuario ya existe" in response.json()["detail"]


def test_login_success(test_user):
    """Test successful login."""
    response = client.post("/api/login", json={
        "username": test_user.username,
        "password": "password123"
    })
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["user"]["username"] == test_user.username


def test_get_me(auth_token, test_user):
    """Test getting current user info."""
    response = client.get("/api/me", headers={"Authorization": f"Bearer {auth_token}"})
    assert response.status_code == 200
    data = response.json()
    assert data["username"] == test_user.username


def test_list_plans_requires_auth():
    """Test plans endpoint requires authentication."""
    response = client.get("/api/plans")
    assert response.status_code == 401


def test_list_plans_with_auth(auth_token):
    """Test listing plans with authentication."""
    response = client.get("/api/plans", headers={"Authorization": f"Bearer {auth_token}"})
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)


def test_filter_plans_by_city(auth_token):
    """Test filtering plans by city."""
    response = client.get("/api/plans?city=BARCELONA", headers={"Authorization": f"Bearer {auth_token}"})
    assert response.status_code == 200
    data = response.json()
    for plan in data:
        assert plan["city"] == "BARCELONA"


def test_webhook_endpoints_require_auth():
    """Test webhook endpoints require authentication."""
    response = client.get("/api/webhook")
    assert response.status_code == 401
    
    response = client.post("/api/webhook", json={"url": "https://example.com"})
    assert response.status_code == 401


def test_seed_data_structure():
    """Test that seed data has correct structure."""
    from main import SEED_PLANS
    
    assert len(SEED_PLANS) == 24
    barcelona = [p for p in SEED_PLANS if p["city"] == "BARCELONA"]
    villena = [p for p in SEED_PLANS if p["city"] == "VILLENA"]
    assert len(barcelona) == 14
    assert len(villena) == 10
    
    # Verify all required fields
    for plan in SEED_PLANS:
        assert "title" in plan
        assert "description" in plan
        assert "location" in plan
        assert "price" in plan
        assert "plan_type" in plan
        assert "duration" in plan
        assert "category" in plan
        assert "city" in plan
        assert "emoji" in plan


def teardown_module(module):
    """Clean up test database after all tests."""
    if os.path.exists("./test.db"):
        os.remove("./test.db")


if __name__ == "__main__":
    pytest.main([__file__, "-v"])