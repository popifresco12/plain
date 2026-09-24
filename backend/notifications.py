"""Avisos (notificaciones) de PLAIN.

`notify()` guarda el aviso y lo empuja por WebSocket si el usuario está
conectado. La app, además, los consulta cada ~15 min en segundo plano, así que
llegan aunque la app esté cerrada (sin Firebase: no hace falta cuenta ni
google-services.json).
"""
from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any, Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session

from auth import get_current_user
from database import get_db
from models import Notification, User
from realtime import hub

router = APIRouter(prefix="/api/notifications", tags=["notifications"])


def serialize(n: Notification) -> dict[str, Any]:
    try:
        data = json.loads(n.data or "{}")
    except (TypeError, ValueError):
        data = {}
    return {
        "id": n.id,
        "kind": n.kind,
        "title": n.title,
        "body": n.body or "",
        "data": data,
        "collapse_key": n.collapse_key,
        "created_at": n.created_at.isoformat() if n.created_at else None,
        "read": n.read_at is not None,
    }


def notify(
    db: Session,
    user_id: int,
    kind: str,
    title: str,
    body: str = "",
    data: Optional[dict[str, Any]] = None,
    collapse_key: Optional[str] = None,
) -> Notification:
    """Crea (o actualiza, si hay uno sin leer con la misma collapse_key) un aviso.

    Hace commit propio: se llama después de que la acción principal ya esté
    guardada, para que un fallo aquí nunca deshaga la acción del usuario.
    """
    n = None
    if collapse_key:
        n = (
            db.query(Notification)
            .filter(
                Notification.user_id == user_id,
                Notification.collapse_key == collapse_key,
                Notification.read_at.is_(None),
            )
            .order_by(Notification.id.desc())
            .first()
        )
    if n is not None:
        # Se sustituye por uno nuevo (id mayor): el cliente avanza por id para
        # saber qué ha mostrado ya, así que actualizarlo en sitio no lo re-avisaría.
        db.delete(n)
        db.flush()
    n = Notification(
        user_id=user_id,
        kind=kind,
        title=title[:200],
        body=(body or "")[:500],
        data=json.dumps(data or {}, ensure_ascii=False),
        collapse_key=collapse_key,
    )
    db.add(n)
    db.commit()
    db.refresh(n)
    hub.publish(user_id, {"type": "notification", "notification": serialize(n)})
    return n


def notify_safe(db: Session, *args, **kwargs) -> None:
    """Igual que notify() pero nunca rompe la petición que lo llama."""
    try:
        notify(db, *args, **kwargs)
    except Exception as e:  # pragma: no cover - defensivo
        db.rollback()
        print(f"⚠️ notify falló (no crítico): {e}")


def unread_count(db: Session, user_id: int) -> int:
    return (
        db.query(Notification)
        .filter(Notification.user_id == user_id, Notification.read_at.is_(None))
        .count()
    )


@router.get("")
def list_notifications(
    since_id: int = 0,
    limit: int = 50,
    unread_only: bool = False,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Avisos del usuario, del más nuevo al más viejo. `since_id` para pedir solo los nuevos."""
    limit = max(1, min(int(limit or 50), 100))
    q = db.query(Notification).filter(Notification.user_id == user.id)
    if since_id:
        q = q.filter(Notification.id > int(since_id))
    if unread_only:
        q = q.filter(Notification.read_at.is_(None))
    items = q.order_by(Notification.id.desc()).limit(limit).all()
    return {"items": [serialize(n) for n in items], "unread": unread_count(db, user.id)}


class MarkReadIn(BaseModel):
    ids: list[int] = []
    all: bool = False
    collapse_key: Optional[str] = None


@router.post("/read")
def mark_read(
    data: MarkReadIn,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    q = db.query(Notification).filter(
        Notification.user_id == user.id, Notification.read_at.is_(None)
    )
    if not data.all:
        if data.collapse_key:
            q = q.filter(Notification.collapse_key == data.collapse_key)
        elif data.ids:
            q = q.filter(Notification.id.in_(data.ids))
        else:
            return {"updated": 0, "unread": unread_count(db, user.id)}
    now = datetime.now(timezone.utc)
    updated = 0
    for n in q.all():
        n.read_at = now
        updated += 1
    db.commit()
    return {"updated": updated, "unread": unread_count(db, user.id)}
