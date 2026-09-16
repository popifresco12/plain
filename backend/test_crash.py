#!/usr/bin/env python3
"""Tests de la captura de fallos de la app (/api/crash-reports).

Existe porque sin emulador ni analytics un crash en el móvil era invisible:
el usuario solo podía decir «no va».
"""
import uuid

import pytest
from fastapi.testclient import TestClient

from conftest import TestingSessionLocal
from main import app
from models import User

client = TestClient(app)


def make_token() -> str:
    from auth import create_access_token, hash_password

    db = TestingSessionLocal()
    uniq = uuid.uuid4().hex[:8]
    user = User(
        username=f"crash_{uniq}",
        email=f"crash_{uniq}@example.com",
        password_hash=hash_password("password123"),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    token = create_access_token(user.id)
    db.close()
    return token


EJEMPLO = {
    "app_version": "0.5.0",
    "android_version": "14",
    "device": "Oppo Find X7 Ultra",
    "screen": "SwipeScreen",
    "message": "java.lang.NullPointerException",
    "stacktrace": "at com.plain.app.ui.SwipeScreenKt$SwipeScreen$1.invoke(SwipeScreen.kt:123)",
    "username": "uitester",
}


def test_report_crash_without_auth():
    """La app puede petar antes de iniciar sesión: el informe debe entrar igual."""
    r = client.post("/api/crash-reports", json=EJEMPLO)
    assert r.status_code == 201
    assert r.json()["status"] == "ok"
    assert isinstance(r.json()["id"], int)


def test_report_is_stored_and_readable_with_auth():
    token = make_token()
    client.post("/api/crash-reports", json=EJEMPLO)

    r = client.get("/api/crash-reports", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 200
    informes = r.json()
    assert len(informes) == 1
    assert informes[0]["device"] == "Oppo Find X7 Ultra"
    assert informes[0]["screen"] == "SwipeScreen"
    assert "NullPointerException" in informes[0]["message"]


def test_list_crashes_requires_auth():
    assert client.get("/api/crash-reports").status_code == 401


def test_long_fields_are_truncated():
    """Un stack trace gigante no debe romper la inserción."""
    token = make_token()
    r = client.post("/api/crash-reports", json={**EJEMPLO, "stacktrace": "x" * 20000})
    assert r.status_code == 201

    informes = client.get("/api/crash-reports", headers={"Authorization": f"Bearer {token}"}).json()
    assert len(informes[0]["stacktrace"]) <= 8000


def test_limit_is_clamped():
    token = make_token()
    for _ in range(3):
        client.post("/api/crash-reports", json=EJEMPLO)
    r = client.get("/api/crash-reports?limit=99999", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 200
