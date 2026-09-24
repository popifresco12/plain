"""Tests de 0.9.0: avisos, match, chat en tiempo real, «Para ti» y sesiones renovables."""
import json
import uuid

import pytest
from fastapi.testclient import TestClient

from auth import create_access_token, hash_password
from conftest import TestingSessionLocal
from main import app
from models import Favorite, Plan, PlanEvent, User
from ratelimit import limiter

client = TestClient(app)


@pytest.fixture(autouse=True)
def _sin_limites():
    """Estos tests hacen muchas peticiones seguidas: el limitador no es lo que se prueba aquí."""
    antes = limiter.enabled
    limiter.enabled = False
    yield
    limiter.enabled = antes


def make_user(prefix="u"):
    db = TestingSessionLocal()
    uniq = uuid.uuid4().hex[:8]
    u = User(username=f"{prefix}_{uniq}", email=f"{prefix}_{uniq}@example.com",
             password_hash=hash_password("password123"))
    db.add(u)
    db.commit()
    db.refresh(u)
    uid, name = u.id, u.username
    db.close()
    return uid, name, create_access_token(uid)


def make_plan(title="Ruta al castillo", tags=None, category="Naturaleza", city="VILLENA", desc="Subida tranquila"):
    db = TestingSessionLocal()
    p = Plan(title=title, description=desc, location=city.title(), city=city, category=category,
             tags=json.dumps(tags or ["senderismo"]), is_default=True)
    db.add(p)
    db.commit()
    db.refresh(p)
    pid = p.id
    db.close()
    return pid


def H(t):
    return {"Authorization": f"Bearer {t}"}


def notifs(token, **params):
    r = client.get("/api/notifications", headers=H(token), params=params)
    assert r.status_code == 200, r.text
    return r.json()


def new_group(token, plan_id, title="Quedada test", seats=4):
    r = client.post(f"/api/plans/{plan_id}/groups", headers=H(token),
                    json={"plan_id": plan_id, "title": title, "seats": seats})
    assert r.status_code == 200, r.text
    return r.json()["id"]


# ---------------------------------------------------------------- sesiones

def test_login_da_refresh_token_y_rota():
    r = client.post("/api/register", json={"username": "rt_" + uuid.uuid4().hex[:6],
                                           "email": f"rt_{uuid.uuid4().hex[:6]}@example.com",
                                           "password": "password123"})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["refresh_token"] and body["expires_in"] > 0
    r1 = client.post("/api/token/refresh", json={"refresh_token": body["refresh_token"]})
    assert r1.status_code == 200, r1.text
    nuevo = r1.json()
    assert nuevo["refresh_token"] != body["refresh_token"]
    assert client.get("/api/me", headers=H(nuevo["access_token"])).status_code == 200


def test_reusar_refresh_revocado_cierra_todas_las_sesiones():
    r = client.post("/api/register", json={"username": "rr_" + uuid.uuid4().hex[:6],
                                           "email": f"rr_{uuid.uuid4().hex[:6]}@example.com",
                                           "password": "password123"})
    viejo = r.json()["refresh_token"]
    nuevo = client.post("/api/token/refresh", json={"refresh_token": viejo}).json()["refresh_token"]
    # alguien reutiliza el viejo (robado) -> 401 y además el nuevo deja de valer
    assert client.post("/api/token/refresh", json={"refresh_token": viejo}).status_code == 401
    assert client.post("/api/token/refresh", json={"refresh_token": nuevo}).status_code == 401


def test_logout_all_invalida_access_tokens():
    _, _, tok = make_user("lo")
    assert client.get("/api/me", headers=H(tok)).status_code == 200
    assert client.post("/api/logout-all", headers=H(tok)).status_code == 200
    assert client.get("/api/me", headers=H(tok)).status_code == 401


def test_refresh_invalido():
    assert client.post("/api/token/refresh", json={"refresh_token": "no-existe"}).status_code == 401


# ---------------------------------------------------------------- avisos

def test_unirse_avisa_al_creador_y_se_marca_leido():
    owner_id, _, t_owner = make_user("own")
    _, name_b, t_b = make_user("b")
    pid = make_plan()
    gid = new_group(t_owner, pid)
    assert client.post(f"/api/groups/{gid}/join", headers=H(t_b)).status_code == 200
    n = notifs(t_owner)
    assert n["unread"] == 1
    item = n["items"][0]
    assert item["kind"] == "join" and name_b in item["title"]
    assert item["data"]["group_id"] == gid
    # since_id: no devuelve lo ya visto
    assert notifs(t_owner, since_id=item["id"])["items"] == []
    r = client.post("/api/notifications/read", headers=H(t_owner), json={"ids": [item["id"]]})
    assert r.json()["unread"] == 0


def test_solicitud_pendiente_no_ve_el_chat_hasta_que_la_aceptan():
    owner_id, _, t_owner = make_user("own")
    b_id, _, t_b = make_user("b")
    pid = make_plan()
    gid = new_group(t_owner, pid)
    client.post(f"/api/groups/{gid}/rules", headers=H(t_owner), json={"join_mode": "approval"})
    client.post(f"/api/groups/{gid}/request", headers=H(t_b))
    # antes: bastaba con tener fila en members (aunque fuese 'pending') para leer el chat
    assert client.get(f"/api/groups/{gid}/messages", headers=H(t_b)).status_code == 403
    assert notifs(t_owner)["items"][0]["kind"] == "request"
    client.post(f"/api/groups/{gid}/approve", headers=H(t_owner), json={"user_id": b_id, "approve": True})
    assert client.get(f"/api/groups/{gid}/messages", headers=H(t_b)).status_code == 200
    assert notifs(t_b)["items"][0]["kind"] == "approved"


def test_mensajes_se_agrupan_en_un_aviso_por_chat():
    _, _, t_owner = make_user("own")
    _, _, t_b = make_user("b")
    pid = make_plan()
    gid = new_group(t_owner, pid)
    client.post(f"/api/groups/{gid}/join", headers=H(t_b))
    for txt in ("hola", "¿a qué hora?", "yo llevo agua"):
        assert client.post(f"/api/groups/{gid}/messages", headers=H(t_b), json={"text": txt}).status_code == 200
    msgs = [i for i in notifs(t_owner)["items"] if i["kind"] == "message"]
    assert len(msgs) == 1 and "yo llevo agua" in msgs[0]["body"]
    # el autor no se avisa a sí mismo
    assert not [i for i in notifs(t_b)["items"] if i["kind"] == "message"]
    # abrir el chat = marcar leídos por collapse_key
    r = client.post("/api/notifications/read", headers=H(t_owner), json={"collapse_key": f"msg:{gid}"})
    assert r.json()["updated"] == 1


# ---------------------------------------------------------------- match

def test_match_cuando_dos_marcan_el_mismo_plan():
    _, name_a, t_a = make_user("a")
    _, name_b, t_b = make_user("b")
    pid = make_plan("Cata de vinos")
    assert client.post(f"/api/favorites/{pid}", headers=H(t_a)).json()["matches"] == 0
    assert client.post(f"/api/favorites/{pid}", headers=H(t_b)).json()["matches"] == 1
    na = [i for i in notifs(t_a)["items"] if i["kind"] == "match"]
    nb = [i for i in notifs(t_b)["items"] if i["kind"] == "match"]
    assert na and name_b in na[0]["body"]
    assert nb and name_a in nb[0]["body"]
    m = client.get("/api/matches", headers=H(t_a)).json()["items"]
    assert m[0]["plan_id"] == pid and m[0]["people_count"] == 1
    assert m[0]["people"][0]["username"] == name_b


def test_quedada_nueva_avisa_a_quien_marco_el_plan():
    _, _, t_a = make_user("a")
    _, _, t_b = make_user("b")
    pid = make_plan()
    client.post(f"/api/favorites/{pid}", headers=H(t_b))
    gid = new_group(t_a, pid, title="Subimos el sábado")
    nuevas = [i for i in notifs(t_b)["items"] if i["kind"] == "group_new"]
    assert nuevas and nuevas[0]["data"]["group_id"] == gid
    grupos = client.get("/api/matches", headers=H(t_b)).json()["items"]
    # b solo ve match si a también marcó el plan: aquí no lo ha hecho
    assert grupos == []


# ---------------------------------------------------------------- tiempo real

def test_websocket_rechaza_token_malo():
    from starlette.websockets import WebSocketDisconnect
    with pytest.raises(WebSocketDisconnect) as exc:
        with client.websocket_connect("/ws?token=basura") as ws:
            ws.receive_json()
    assert exc.value.code == 4401


def test_websocket_recibe_mensajes_y_escribiendo():
    a_id, name_a, t_a = make_user("a")
    b_id, _, t_b = make_user("b")
    pid = make_plan()
    gid = new_group(t_a, pid)
    client.post(f"/api/groups/{gid}/join", headers=H(t_b))
    with client.websocket_connect(f"/ws?token={t_b}") as ws_b:
        assert ws_b.receive_json() == {"type": "hello", "user_id": b_id}
        ws_b.send_json({"type": "ping"})
        assert ws_b.receive_json()["type"] == "pong"
        with client.websocket_connect(f"/ws?token={t_a}") as ws_a:
            ws_a.receive_json()  # hello
            ws_a.send_json({"type": "typing", "group_id": gid})
            ev = ws_b.receive_json()
            assert ev["type"] == "typing" and ev["username"] == name_a
            r = client.post(f"/api/groups/{gid}/messages", headers=H(t_a), json={"text": "¡en camino!"})
            assert r.status_code == 200
            got = [ws_b.receive_json(), ws_b.receive_json()]
            kinds = {g["type"] for g in got}
            assert kinds == {"message", "notification"}
            msg = next(g for g in got if g["type"] == "message")
            assert msg["group_id"] == gid and msg["message"]["text"] == "¡en camino!"


def test_typing_de_quien_no_es_miembro_no_llega():
    _, _, t_a = make_user("a")
    b_id, _, t_b = make_user("b")
    _, _, t_intruso = make_user("x")
    pid = make_plan()
    gid = new_group(t_a, pid)
    client.post(f"/api/groups/{gid}/join", headers=H(t_b))
    with client.websocket_connect(f"/ws?token={t_b}") as ws_b:
        ws_b.receive_json()
        with client.websocket_connect(f"/ws?token={t_intruso}") as ws_x:
            ws_x.receive_json()
            ws_x.send_json({"type": "typing", "group_id": gid})
            ws_b.send_json({"type": "ping"})
            assert ws_b.receive_json()["type"] == "pong"   # lo siguiente es el pong, no un typing


# ---------------------------------------------------------------- para ti

def test_para_ti_prioriza_lo_parecido_y_explica_por_que():
    uid, _, tok = make_user("fy")
    visto = make_plan("Ruta por la sierra", tags=["senderismo", "montaña"], category="Naturaleza",
                      desc="Senderismo con vistas")
    parecido = make_plan("Senderismo al amanecer", tags=["senderismo", "montaña"], category="Naturaleza",
                         desc="Ruta de montaña corta")
    distinto = make_plan("Noche de karaoke", tags=["musica", "fiesta"], category="Ocio",
                         desc="Cantar con amigos en un bar")
    client.post(f"/api/favorites/{visto}", headers=H(tok))
    r = client.get("/api/plans", headers=H(tok), params={"city": "VILLENA", "for_you": "true"})
    assert r.status_code == 200, r.text
    ids = [p["id"] for p in r.json()]
    assert ids.index(parecido) < ids.index(distinto)
    razon = next(p["reason"] for p in r.json() if p["id"] == parecido)
    assert razon and "#senderismo" in razon
    assert next(p["reason"] for p in r.json() if p["id"] == distinto) is None


def test_para_ti_sin_historial_no_inventa():
    _, _, tok = make_user("nuevo")
    make_plan("A")
    make_plan("B", tags=["cine"])
    r = client.get("/api/plans", headers=H(tok), params={"city": "VILLENA", "for_you": "true"})
    assert all(p["reason"] is None for p in r.json())


def test_para_ti_hunde_lo_descartado():
    uid, _, tok = make_user("dis")
    malo = make_plan("Discoteca techno", tags=["fiesta", "noche"], category="Ocio", desc="Música electrónica")
    parecido_malo = make_plan("Fiesta de noche", tags=["fiesta", "noche"], category="Ocio", desc="Bailar toda la noche")
    neutro = make_plan("Museo de arte", tags=["museo"], category="Cultura", desc="Pintura clásica")
    db = TestingSessionLocal()
    db.add(PlanEvent(plan_id=malo, user_id=uid, event="dislike", city="VILLENA"))
    db.commit()
    db.close()
    ids = [p["id"] for p in client.get("/api/plans", headers=H(tok),
                                        params={"city": "VILLENA", "for_you": "true"}).json()]
    assert ids.index(neutro) < ids.index(parecido_malo)
