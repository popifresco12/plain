"""Recomendación «Para ti» por similitud de contenido.

Cada plan se convierte en un vector TF-IDF (título + descripción + categoría +
etiquetas, con más peso a categoría y etiquetas). El perfil del usuario es la
media de los vectores de lo que le ha gustado menos la mitad de lo que ha
descartado. La afinidad es el coseno entre perfil y plan.

Por qué no embeddings neuronales: no caben en el Render gratuito (512 MB) y
requerirían una API de pago por petición. Esto es instantáneo, gratis,
determinista y además permite EXPLICAR la recomendación (los términos que
comparte con lo que te gustó), cosa que un embedding no da.
"""
from __future__ import annotations

import math
import re
import unicodedata
from collections import Counter
from typing import Iterable

from sqlalchemy.orm import Session

from models import DislikedTag, Favorite, Plan, PlanEvent

STOP = set(
    "de la el en y a los las un una con para por del al que se su sus lo es o mas muy sin sobre "
    "tu te mi como entre desde hasta este esta estos estas ese esa son ser hay todo toda todos "
    "the and of to in for with on at".split()
)
_WORD = re.compile(r"[a-z0-9ñ]+")


def _norm(text: str) -> str:
    text = unicodedata.normalize("NFKD", (text or "").lower())
    text = "".join(c for c in text if not unicodedata.combining(c) or c == "\u0303")
    return unicodedata.normalize("NFC", text)


def tokens(text: str) -> list[str]:
    return [w for w in _WORD.findall(_norm(text)) if len(w) >= 3 and w not in STOP]


def plan_terms(p: Plan) -> list[str]:
    tags = []
    for t in p.get_tags():
        tags.extend(tokens(t))
    cat = tokens(p.category or "")
    # Peso por repetición: etiquetas ×3, categoría ×2, texto ×1
    return tokens(p.title or "") + tokens(p.description or "") + cat * 2 + tags * 3


def _idf(docs: Iterable[list[str]]) -> dict[str, float]:
    docs = list(docs)
    n = len(docs) or 1
    df: Counter = Counter()
    for d in docs:
        df.update(set(d))
    return {t: math.log((1 + n) / (1 + c)) + 1.0 for t, c in df.items()}


def _vec(terms: list[str], idf: dict[str, float]) -> dict[str, float]:
    tf = Counter(terms)
    v = {t: (1 + math.log(c)) * idf.get(t, 1.0) for t, c in tf.items()}
    norm = math.sqrt(sum(x * x for x in v.values())) or 1.0
    return {t: x / norm for t, x in v.items()}


def _add(acc: dict[str, float], v: dict[str, float], w: float) -> None:
    for t, x in v.items():
        acc[t] = acc.get(t, 0.0) + w * x


def _cos(a: dict[str, float], b: dict[str, float]) -> float:
    if len(a) > len(b):
        a, b = b, a
    dot = sum(x * b.get(t, 0.0) for t, x in a.items())
    na = math.sqrt(sum(x * x for x in a.values())) or 1.0
    nb = math.sqrt(sum(x * x for x in b.values())) or 1.0
    return dot / (na * nb)


def user_signals(db: Session, user_id: int) -> tuple[set[int], set[int], set[str]]:
    liked = {pid for (pid,) in db.query(Favorite.plan_id).filter(Favorite.user_id == user_id).all()}
    disliked: set[int] = set()
    try:
        for pid, ev in db.query(PlanEvent.plan_id, PlanEvent.event).filter(PlanEvent.user_id == user_id).all():
            if pid is None:
                continue
            if ev in ("like", "favorite"):
                liked.add(pid)
            elif ev == "dislike":
                disliked.add(pid)
    except Exception:
        db.rollback()
    disliked -= liked
    bad_tags = set()
    for (t,) in db.query(DislikedTag.tag).filter(DislikedTag.user_id == user_id, DislikedTag.count > 0).all():
        bad_tags.update(tokens(t))
    return liked, disliked, bad_tags


def rank_for_user(db: Session, user_id: int, candidates: list[Plan]) -> dict[int, tuple[float, str | None]]:
    """Devuelve {plan_id: (afinidad -1..1, motivo o None)} para los candidatos.

    Sin historial suficiente (nada marcado) devuelve {} y quien llama deja el
    orden que ya tenía: no se inventa un «para ti» sin datos.
    """
    liked, disliked, bad_tags = user_signals(db, user_id)
    if not liked and not disliked and not bad_tags:
        return {}

    ref_ids = liked | disliked
    ref_plans = db.query(Plan).filter(Plan.id.in_(ref_ids)).all() if ref_ids else []
    by_id = {p.id: p for p in candidates}
    for p in ref_plans:
        by_id.setdefault(p.id, p)

    terms = {pid: plan_terms(p) for pid, p in by_id.items()}
    idf = _idf(terms.values())
    vecs = {pid: _vec(t, idf) for pid, t in terms.items()}

    profile: dict[str, float] = {}
    if liked:
        for pid in liked:
            if pid in vecs:
                _add(profile, vecs[pid], 1.0 / len(liked))
    if disliked:
        for pid in disliked:
            if pid in vecs:
                _add(profile, vecs[pid], -0.5 / len(disliked))
    for t in bad_tags:
        profile[t] = profile.get(t, 0.0) - 0.3
    if not any(v > 0 for v in profile.values()):
        # Solo señales negativas: sirven para hundir, no para explicar
        return {p.id: (_cos(profile, vecs[p.id]), None) for p in candidates if p.id in vecs}

    out: dict[int, tuple[float, str | None]] = {}
    for p in candidates:
        v = vecs.get(p.id)
        if not v:
            continue
        sim = _cos(profile, v)
        shared = sorted(
            ((t, profile[t] * v[t]) for t in v if profile.get(t, 0) > 0),
            key=lambda kv: -kv[1],
        )[:3]
        reason = None
        if sim > 0.08 and shared:
            reason = "Porque te gustaron planes de " + ", ".join("#" + t for t, _ in shared)
        out[p.id] = (sim, reason)
    return out
