"""Refresh tokens rotativos para usuarios.

- Login/registro devuelven access token (24 h por defecto) + refresh token (60 días).
- `POST /api/token/refresh` revoca el refresh usado y emite un par nuevo (rotación).
- Si alguien presenta un refresh YA revocado, es que se ha filtrado: se revocan
  todos los del usuario y tiene que volver a entrar.
- `POST /api/logout` revoca uno; `POST /api/logout-all` revoca todos y sube
  `token_version`, lo que invalida también los access tokens ya emitidos.

Del refresh token solo se guarda el SHA-256: leer la base de datos no permite usarlo.
"""

import hashlib
import os
import secrets
from datetime import datetime, timedelta, timezone
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel
from sqlalchemy.orm import Session

from auth import USER_ACCESS_TOKEN_MINUTES, create_access_token, get_current_user
from database import get_db
from models import RefreshToken, User
from ratelimit import limiter

REFRESH_DAYS = int(os.environ.get("REFRESH_TOKEN_DAYS", "60"))

router = APIRouter(prefix="/api", tags=["auth"])


def _hash(raw: str) -> str:
    return hashlib.sha256(raw.encode()).hexdigest()


def _naive_utc(dt: datetime) -> datetime:
    return dt.replace(tzinfo=None) if dt.tzinfo else dt


def issue_refresh_token(db: Session, user_id: int) -> str:
    raw = secrets.token_urlsafe(48)
    db.add(RefreshToken(
        user_id=user_id,
        token_hash=_hash(raw),
        expires_at=_naive_utc(datetime.now(timezone.utc) + timedelta(days=REFRESH_DAYS)),
    ))
    db.commit()
    return raw


def token_pair(db: Session, user: User) -> dict:
    return {
        "access_token": create_access_token(user.id, getattr(user, "token_version", 0) or 0),
        "refresh_token": issue_refresh_token(db, user.id),
        "expires_in": USER_ACCESS_TOKEN_MINUTES * 60,
    }


class RefreshIn(BaseModel):
    refresh_token: str


@router.post("/token/refresh")
@limiter.limit("30/minute")
def refresh(request: Request, data: RefreshIn, db: Session = Depends(get_db)):
    row = db.query(RefreshToken).filter(RefreshToken.token_hash == _hash(data.refresh_token)).first()
    if row is None:
        raise HTTPException(status_code=401, detail="Refresh token inválido")
    if row.revoked:
        # Reutilización de un token ya rotado: posible robo → se cierra todo
        db.query(RefreshToken).filter(RefreshToken.user_id == row.user_id).update({"revoked": True})
        db.commit()
        raise HTTPException(status_code=401, detail="Sesión revocada por seguridad. Vuelve a entrar")
    if _naive_utc(row.expires_at) < _naive_utc(datetime.now(timezone.utc)):
        raise HTTPException(status_code=401, detail="Sesión caducada. Vuelve a entrar")
    user = db.query(User).filter(User.id == row.user_id).first()
    if user is None:
        raise HTTPException(status_code=401, detail="Usuario no encontrado")
    row.revoked = True
    db.commit()
    return {**token_pair(db, user), "token_type": "bearer"}


class LogoutIn(BaseModel):
    refresh_token: Optional[str] = None


@router.post("/logout")
@limiter.limit("30/minute")
def logout(request: Request, data: LogoutIn, db: Session = Depends(get_db)):
    if data.refresh_token:
        db.query(RefreshToken).filter(RefreshToken.token_hash == _hash(data.refresh_token)).update({"revoked": True})
        db.commit()
    return {"status": "ok"}


@router.post("/logout-all")
def logout_all(db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    db.query(RefreshToken).filter(RefreshToken.user_id == user.id).update({"revoked": True})
    user.token_version = (getattr(user, "token_version", 0) or 0) + 1
    db.commit()
    return {"status": "ok"}
