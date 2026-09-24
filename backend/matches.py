"""Match: dos personas que han dado «me gusta» al mismo plan.

En PLAIN no se hace swipe a personas sino a planes, así que el match natural
es coincidir en el plan: «a @ana también le apetece la ruta del castillo».
Desde el match se crea (o se une uno a) una quedada y se abre su chat.

Privacidad: solo se ve a gente que coincide contigo en un plan que TÚ también
has marcado; nunca se expone el género ni el email.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from auth import get_current_user
from database import get_db
from models import Favorite, Plan, TripGroup, TripGroupMember, User
from notifications import notify_safe

router = APIRouter(prefix="/api/matches", tags=["matches"])

MAX_NOTIFY = 25  # a cuántos avisar como mucho por evento (evita spam en planes muy populares)


def _others_who_liked(db: Session, plan_id: int, exclude_user_id: int, limit: int = MAX_NOTIFY):
    return (
        db.query(User)
        .join(Favorite, Favorite.user_id == User.id)
        .filter(Favorite.plan_id == plan_id, User.id != exclude_user_id)
        .order_by(Favorite.created_at.desc())
        .limit(limit)
        .all()
    )


def on_favorite(db: Session, user: User, plan: Plan) -> int:
    """Llamar justo después de guardar el favorito. Devuelve cuántos matches hay."""
    others = _others_who_liked(db, plan.id, user.id)
    if not others:
        return 0
    data = {"plan_id": plan.id, "plan_title": plan.title, "city": plan.city}
    for o in others:
        notify_safe(
            db, o.id, "match",
            f"✨ Match en «{plan.title}»",
            f"A @{user.username} también le apetece. ¿Montáis una quedada?",
            data, collapse_key=f"match:{plan.id}",
        )
    names = ", ".join("@" + o.username for o in others[:3])
    extra = f" y {len(others) - 3} más" if len(others) > 3 else ""
    notify_safe(
        db, user.id, "match",
        f"✨ {len(others)} match{'es' if len(others) != 1 else ''} en «{plan.title}»",
        f"{names}{extra} también quieren ir.",
        data, collapse_key=f"match:{plan.id}",
    )
    return len(others)


def on_group_created(db: Session, owner: User, group: TripGroup, plan: Plan) -> None:
    """Avisa a quien marcó el plan de que hay una quedada nueva a la que unirse."""
    data = {"plan_id": plan.id, "plan_title": plan.title, "group_id": group.id, "group_title": group.title}
    for o in _others_who_liked(db, plan.id, owner.id):
        notify_safe(
            db, o.id, "group_new",
            f"🚗 Quedada nueva para «{plan.title}»",
            f"@{owner.username} ha montado «{group.title}». Quedan {max(0, (group.seats or 1) - 1)} plazas.",
            data, collapse_key=f"group_new:{group.id}",
        )


@router.get("")
def list_matches(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Planes que te gustan y en los que coincides con más gente, con sus quedadas."""
    my_plan_ids = [pid for (pid,) in db.query(Favorite.plan_id).filter(Favorite.user_id == user.id).all()]
    if not my_plan_ids:
        return {"items": []}

    items = []
    for plan in db.query(Plan).filter(Plan.id.in_(my_plan_ids)).all():
        people = _others_who_liked(db, plan.id, user.id, limit=12)
        if not people:
            continue
        groups = []
        for g in db.query(TripGroup).filter(TripGroup.plan_id == plan.id).all():
            joined = [m for m in g.members if (m.status or "joined") == "joined"]
            groups.append({
                "id": g.id,
                "title": g.title,
                "seats": g.seats,
                "seats_taken": len(joined),
                "join_mode": g.join_mode or "open",
                "i_am_member": any(m.user_id == user.id for m in joined),
            })
        total = db.query(Favorite).filter(Favorite.plan_id == plan.id, Favorite.user_id != user.id).count()
        items.append({
            "plan_id": plan.id,
            "title": plan.title,
            "city": plan.city,
            "emoji": plan.emoji,
            "image_url": plan.image_url,
            "category": plan.category,
            "people": [{"user_id": p.id, "username": p.username} for p in people],
            "people_count": total,
            "groups": groups,
        })
    items.sort(key=lambda x: -x["people_count"])
    return {"items": items}
