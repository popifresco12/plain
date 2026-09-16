#!/usr/bin/env python3
"""Tests de las funciones añadidas: moderación, recuperar contraseña,
perfil, analítica de producto y límite de planes."""
import uuid

import pytest
from fastapi.testclient import TestClient

from conftest import TestingSessionLocal
from main import app
from models import User

client = TestClient(app)


def make_user() -> tuple[str, str]:
    """Devuelve (token, email) de un usuario nuevo."""
    from auth import create_access_token, hash_password

    db = TestingSessionLocal()
    uniq = uuid.uuid4().hex[:8]
    email = f"feat_{uniq}@example.com"
    user = User(username=f"feat_{uniq}", email=email, password_hash=hash_password("password123"))
    db.add(user)
    db.commit()
    db.refresh(user)
    token = create_access_token(user.id)
    db.close()
    return token, email


def auth(t: str) -> dict:
    return {"Authorization": f"Bearer {t}"}


def test_profile_update_and_status():
    token, email = make_user()
    r = client.put("/api/me", json={"username": "nuevo_nombre"}, headers=auth(token))
    assert r.status_code == 200
    assert r.json()["username"] == "nuevo_nombre"
    assert r.json()["email_verified"] is False

    st = client.get("/api/me/status", headers=auth(token)).json()
    assert st["requires_verification"] is False      # la verificación está apagada
    assert st["max_plans"] > 0


def test_forgot_password_flow_without_smtp():
    """Sin SMTP, el código vuelve en la respuesta (modo pruebas) y sirve para cambiar la clave."""
    token, email = make_user()
    r = client.post("/api/password/forgot", json={"email": email})
    assert r.status_code == 200
    cuerpo = r.json()
    assert cuerpo["sent"] is False
    codigo = cuerpo.get("dev_code")
    assert codigo, "debe devolver el código cuando no hay SMTP"

    malo = client.post("/api/password/reset", json={"email": email, "code": "XXXXXX", "new_password": "otraclave123"})
    assert malo.status_code == 400

    bueno = client.post("/api/password/reset", json={"email": email, "code": codigo, "new_password": "otraclave123"})
    assert bueno.status_code == 200

    # el código es de un solo uso
    repetido = client.post("/api/password/reset", json={"email": email, "code": codigo, "new_password": "terceraclave1"})
    assert repetido.status_code == 400


def test_forgot_password_unknown_email_is_not_revealed():
    r = client.post("/api/password/forgot", json={"email": "noexiste@example.com"})
    assert r.status_code == 200
    assert r.json()["sent"] is False
    assert "dev_code" not in r.json()


def test_email_verification_endpoints():
    token, email = make_user()
    r = client.post("/api/email/verify/request", headers=auth(token))
    codigo = r.json().get("dev_code")
    assert codigo

    ok = client.post("/api/email/verify", json={"email": email, "code": codigo})
    assert ok.status_code == 200
    assert ok.json()["email_verified"] is True


def test_plan_report_and_listing():
    token, _ = make_user()
    crear = client.post("/api/plans", headers=auth(token), json={
        "title": "Plan para reportar", "description": "d", "location": "Villena",
        "price": "0€", "plan_type": "AMBOS", "duration": "1h",
        "category": "Ocio", "city": "VILLENA", "emoji": "🎯", "tags": [],
    })
    assert crear.status_code == 200
    plan_id = crear.json()["id"]

    r = client.post(f"/api/plans/{plan_id}/report", headers=auth(token),
                    json={"reason": "spam", "comment": "duplicado"})
    assert r.status_code == 201

    repetido = client.post(f"/api/plans/{plan_id}/report", headers=auth(token), json={"reason": "spam"})
    assert repetido.json()["status"] == "already_reported"

    lista = client.get("/api/reports", headers=auth(token))
    assert lista.status_code == 200 and len(lista.json()) == 1


def test_plan_limit_per_user(monkeypatch):
    """El límite anti-spam corta con 429 en vez de dejar inundar la ciudad."""
    import main
    monkeypatch.setattr(main, "MAX_PLANS_PER_USER", 2)
    token, _ = make_user()
    for i in range(2):
        r = client.post("/api/plans", headers=auth(token), json={
            "title": f"Plan {i}", "description": "d", "location": "V",
            "price": "0€", "plan_type": "AMBOS", "duration": "1h",
            "category": "Ocio", "city": "VILLENA", "emoji": "🎯", "tags": [],
        })
        assert r.status_code == 200
    tercero = client.post("/api/plans", headers=auth(token), json={
        "title": "Plan de más", "description": "d", "location": "V",
        "price": "0€", "plan_type": "AMBOS", "duration": "1h",
        "category": "Ocio", "city": "VILLENA", "emoji": "🎯", "tags": [],
    })
    assert tercero.status_code == 429


def test_product_events_and_stats():
    token, _ = make_user()
    r = client.post("/api/events", headers=auth(token), json={"events": [
        {"plan_id": 1, "event": "view", "city": "VILLENA"},
        {"plan_id": 1, "event": "like", "city": "VILLENA"},
        {"plan_id": 2, "event": "dislike", "city": "VILLENA"},
        {"plan_id": 2, "event": "inventado", "city": "VILLENA"},   # se ignora
    ]})
    assert r.status_code == 202
    assert r.json()["stored"] == 3

    st = client.get("/api/stats/product", headers=auth(token)).json()
    assert st["eventos"]["like"] == 1
    assert st["eventos"]["dislike"] == 1
    assert st["tasa_like"] == 0.5


def test_plans_pagination_params():
    token, _ = make_user()
    r = client.get("/api/plans?limit=1&offset=0", headers=auth(token))
    assert r.status_code == 200
    assert len(r.json()) <= 1
