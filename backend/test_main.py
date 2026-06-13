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
from main import app, seed_plans
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

# Seed plans for tests (uses test db via SessionLocal override)
# We'll seed manually in fixtures instead to avoid cross-test pollution

client = TestClient(app)

# Save original SessionLocal to restore after seeding
_original_session = None


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
    """Create a test user."""
    from auth import hash_password
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


@pytest.fixture
def seed_test_plans(db_session):
    """Seed plans in the test DB."""
    from main import SEED_PLANS
    import json
    count = db_session.query(Plan).count()
    if count == 0:
        for p in SEED_PLANS:
            tags = p.pop("tags", [])
            plan = Plan(**p, tags=json.dumps(tags), is_default=True)
            db_session.add(plan)
        db_session.commit()
    return db_session.query(Plan).count()


def test_root_endpoint():
    """Test root endpoint returns app info."""
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["app"] == "PLAIN API"
    assert data["version"] == "2.0.0"


def test_register_user():
    """Test user registration."""
    import uuid
    unique = uuid.uuid4().hex[:8]
    response = client.post("/api/register", json={
        "username": f"newuser_{unique}",
        "email": f"new_{unique}@example.com",
        "password": "password123"
    })
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["user"]["username"] == f"newuser_{unique}"


def test_register_duplicate_username():
    """Test registering with existing username fails."""
    import uuid
    unique = uuid.uuid4().hex[:8]
    client.post("/api/register", json={
        "username": f"dupuser_{unique}",
        "email": f"dup1_{unique}@example.com",
        "password": "password123"
    })
    response = client.post("/api/register", json={
        "username": f"dupuser_{unique}",
        "email": f"dup2_{unique}@example.com",
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
    """Test that seed data has correct structure including tags."""
    from main import SEED_PLANS
    assert len(SEED_PLANS) == 24
    barcelona = [p for p in SEED_PLANS if p["city"] == "BARCELONA"]
    villena = [p for p in SEED_PLANS if p["city"] == "VILLENA"]
    assert len(barcelona) == 14
    assert len(villena) == 10
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
        assert "tags" in plan
        assert isinstance(plan["tags"], list)
        assert len(plan["tags"]) > 0, f"Plan '{plan['title']}' has no tags"


def test_favorites_lifecycle(auth_token, seed_test_plans):
    """Test add, list, remove favorites."""
    # First list plans to get an ID
    plans_resp = client.get("/api/plans", headers={"Authorization": f"Bearer {auth_token}"})
    assert plans_resp.status_code == 200
    plans = plans_resp.json()
    assert len(plans) > 0  # seeded
    plan_id = plans[0]["id"]

    # Add favorite
    add_resp = client.post(f"/api/favorites/{plan_id}", headers={"Authorization": f"Bearer {auth_token}"})
    assert add_resp.status_code == 200
    assert add_resp.json()["status"] == "favorited"

    # Duplicate add
    dup_resp = client.post(f"/api/favorites/{plan_id}", headers={"Authorization": f"Bearer {auth_token}"})
    assert dup_resp.json()["status"] == "already_favorited"

    # List favorites
    list_resp = client.get("/api/favorites", headers={"Authorization": f"Bearer {auth_token}"})
    assert list_resp.status_code == 200
    favs = list_resp.json()
    assert len(favs) == 1
    assert favs[0]["plan_id"] == plan_id
    assert "plan" in favs[0]  # Nested plan data

    # Remove favorite
    del_resp = client.delete(f"/api/favorites/{plan_id}", headers={"Authorization": f"Bearer {auth_token}"})
    assert del_resp.status_code == 200

    # Verify empty
    list_resp2 = client.get("/api/favorites", headers={"Authorization": f"Bearer {auth_token}"})
    assert len(list_resp2.json()) == 0


def test_dislike_tags(auth_token, seed_test_plans):
    """Test recording disliked tags and plan deprioritization."""
    resp = client.post("/api/dislike-tags", json={
        "tags": ["naturaleza", "senderismo"]
    }, headers={"Authorization": f"Bearer {auth_token}"})
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "updated"
    assert "naturaleza" in data["tags"]

    # Increment existing tag
    resp2 = client.post("/api/dislike-tags", json={
        "tags": ["naturaleza"]
    }, headers={"Authorization": f"Bearer {auth_token}"})
    assert resp2.status_code == 200

    # Now plans with "naturaleza" tag should be deprioritized
    plans_resp = client.get("/api/plans", headers={"Authorization": f"Bearer {auth_token}"})
    assert plans_resp.status_code == 200


def test_plans_with_tags_in_response(auth_token):
    """Test plans include tags in the API response."""
    response = client.get("/api/plans", headers={"Authorization": f"Bearer {auth_token}"})
    assert response.status_code == 200
    data = response.json()
    if data:
        assert "tags" in data[0]
        assert isinstance(data[0]["tags"], list)


def test_favorites_requires_auth():
    """Test favorites endpoints require auth."""
    assert client.get("/api/favorites").status_code == 401
    assert client.post("/api/favorites/1").status_code == 401
    assert client.delete("/api/favorites/1").status_code == 401
    assert client.post("/api/dislike-tags", json={"tags": ["test"]}).status_code == 401


def teardown_module(module):
    """Clean up test database after all tests."""
    if os.path.exists("./test.db"):
        os.remove("./test.db")


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
