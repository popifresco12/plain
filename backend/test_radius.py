#!/usr/bin/env python3
"""Tests del radio de kms: ver planes de ciudades cercanas sin cambiar de ciudad.

Ejecutar: .venv/Scripts/python.exe -m pytest test_radius.py -v
"""
import json
import uuid

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

import geo
from database import Base, get_db
from main import app
from models import Plan, User

TEST_DATABASE_URL = "sqlite:///./test_radius.db"
test_engine = create_engine(TEST_DATABASE_URL, connect_args={"check_same_thread": False})
TestSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)


def override_get_db():
    db = TestSessionLocal()
    try:
        yield db
    finally:
        db.close()


app.dependency_overrides[get_db] = override_get_db
Base.metadata.create_all(bind=test_engine)
client = TestClient(app)


@pytest.fixture(autouse=True)
def clean_plans():
    """Cada test parte de cero: los planes creados no deben contaminar a los demás."""
    db = TestSessionLocal()
    db.query(Plan).delete()
    db.commit()
    db.close()
    yield


def make_user() -> str:
    from auth import create_access_token, hash_password

    db = TestSessionLocal()
    uniq = uuid.uuid4().hex[:8]
    user = User(
        username=f"radius_{uniq}",
        email=f"radius_{uniq}@example.com",
        password_hash=hash_password("password123"),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    token = create_access_token(user.id)
    db.close()
    return token


def make_plan(city: str, title: str) -> int:
    db = TestSessionLocal()
    plan = Plan(
        title=title, description="test", location="centro", price="0€",
        plan_type="AMBOS", duration="2h", category="Ocio", city=city,
        emoji="📍", tags=json.dumps([]), is_default=True,
    )
    db.add(plan)
    db.commit()
    db.refresh(plan)
    pid = plan.id
    db.close()
    return pid


def auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


# ── geografía ───────────────────────────────────────────────────────────────

def test_haversine_barcelona_madrid():
    """Barcelona–Madrid son ~505 km en línea recta."""
    d = geo.haversine_km(41.389, 2.159, 40.416, -3.703)
    assert abs(d - 504) < 12, f"distancia inesperada: {d}"


def test_homonym_prefers_country_hint():
    """VALENCIA existe en ES y VE: sin pista gana la más poblada, con pista manda la pista."""
    assert geo.resolve("VALENCIA", "ES")[2] == "ES"
    assert geo.resolve("VALENCIA")[2] == "VE"
    assert abs(geo.resolve("VALENCIA", "ES")[0] - 39.47) < 0.1


def test_cities_in_radius_filters_far_cities():
    near = geo.cities_in_radius(41.389, 2.159, 30)
    assert "BARCELONA" in near
    assert "MADRID" not in near
    assert all(v[2] <= 30 for v in near.values())


# ── endpoint ────────────────────────────────────────────────────────────────

def test_radius_zero_behaves_as_before():
    """Sin radio, solo la ciudad pedida y sin campo de distancia."""
    token = make_user()
    make_plan("BARCELONA", "Plan BCN")
    make_plan("MATARO", "Plan Mataró")

    r = client.get("/api/plans?city=BARCELONA", headers=auth(token))
    assert r.status_code == 200
    data = r.json()
    assert {p["city"] for p in data} == {"BARCELONA"}
    assert data[0]["distance_km"] is None


def test_radius_includes_nearby_cities_with_distance():
    """Con radio 60 km desde Barcelona entran Barcelona y Mataró, pero no Madrid."""
    token = make_user()
    make_plan("BARCELONA", "Plan BCN 60")
    make_plan("MATARO", "Plan Mataró 60")      # ~28 km
    make_plan("MADRID", "Plan Madrid 60")      # ~504 km

    r = client.get("/api/plans?city=BARCELONA&radius_km=60", headers=auth(token))
    assert r.status_code == 200
    dist = {p["city"]: p["distance_km"] for p in r.json()}
    assert set(dist) == {"BARCELONA", "MATARO"}
    assert dist["BARCELONA"] == 0.0
    assert 20 < dist["MATARO"] < 40
    # el más cercano va primero
    assert r.json()[0]["city"] == "BARCELONA"


def test_bigger_radius_reaches_far_city():
    token = make_user()
    make_plan("BARCELONA", "Plan BCN 700")
    make_plan("MADRID", "Plan Madrid 700")

    r = client.get("/api/plans?city=BARCELONA&radius_km=700", headers=auth(token))
    dist = {p["city"]: p["distance_km"] for p in r.json()}
    assert set(dist) == {"BARCELONA", "MADRID"}
    assert 450 < dist["MADRID"] < 550


def test_radius_case_insensitive_city():
    """La ciudad puede llegar en cualquier caja ('barcelona' / 'BARCELONA')."""
    token = make_user()
    make_plan("BARCELONA", "Plan BCN case")
    r = client.get("/api/plans?city=barcelona&radius_km=25", headers=auth(token))
    assert r.status_code == 200
    assert {p["city"] for p in r.json()} == {"BARCELONA"}


def test_unknown_city_falls_back_to_exact_match():
    """Ciudad que no está en el índice: se comporta como antes (match exacto)."""
    token = make_user()
    make_plan("TAMRAGHT", "Plan Tamraght")
    r = client.get("/api/plans?city=TAMRAGHT&radius_km=100", headers=auth(token))
    assert r.status_code == 200
    assert {p["city"] for p in r.json()} == {"TAMRAGHT"}


def test_radius_is_clamped():
    """Un radio absurdo se recorta (no explota ni se acepta negativo)."""
    token = make_user()
    make_plan("BARCELONA", "Plan BCN clamp")
    for bad in ("-50", "99999"):
        r = client.get(f"/api/plans?city=BARCELONA&radius_km={bad}", headers=auth(token))
        assert r.status_code == 200
