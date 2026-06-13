import hashlib
import os
from datetime import datetime, timedelta, timezone

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session

from database import get_db
from models import User, Business

SECRET_KEY = os.environ.get("PLAIN_SECRET_KEY", "plain-super-secret-key-change-in-prod")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_DAYS = 30

security = HTTPBearer(auto_error=False)


def hash_password(password: str) -> str:
    """Hash password using SHA-256 with salt prefix."""
    salt = os.urandom(16).hex()
    return f"{salt}:{hashlib.sha256((salt + password).encode()).hexdigest()}"


def verify_password(password: str, hashed: str) -> bool:
    """Verify a password against its hash."""
    try:
        salt, stored_hash = hashed.split(":", 1)
        return stored_hash == hashlib.sha256((salt + password).encode()).hexdigest()
    except (ValueError, AttributeError):
        return False


def create_access_token(user_id: int) -> str:
    """Create JWT token for a regular user."""
    expire = datetime.now(timezone.utc) + timedelta(days=ACCESS_TOKEN_EXPIRE_DAYS)
    payload = {
        "sub": str(user_id),
        "type": "user",
        "exp": expire,
        "iat": datetime.now(timezone.utc),
    }
    return jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)


def create_business_access_token(business_id: int) -> str:
    """Create JWT token for a business."""
    expire = datetime.now(timezone.utc) + timedelta(days=ACCESS_TOKEN_EXPIRE_DAYS)
    payload = {
        "sub": str(business_id),
        "type": "business",
        "exp": expire,
        "iat": datetime.now(timezone.utc),
    }
    return jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)


def decode_token(token: str) -> dict:
    """Decode JWT token and return payload. Raises on invalid."""
    try:
        return jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
    except (jwt.ExpiredSignatureError, jwt.InvalidTokenError, ValueError, KeyError):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token inválido o expirado",
        )


def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: Session = Depends(get_db),
) -> User:
    """Dependency: returns the current authenticated user. Raises 401 if missing."""
    if not credentials:
        raise HTTPException(status_code=401, detail="Autenticación requerida")
    payload = decode_token(credentials.credentials)
    if payload.get("type") != "user":
        raise HTTPException(status_code=401, detail="Token inválido para usuario")
    user = db.query(User).filter(User.id == int(payload["sub"])).first()
    if not user:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")
    return user


def get_current_business(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: Session = Depends(get_db),
) -> Business:
    """Dependency: returns the current authenticated business."""
    if not credentials:
        raise HTTPException(status_code=401, detail="Autenticación requerida")
    payload = decode_token(credentials.credentials)
    if payload.get("type") != "business":
        raise HTTPException(status_code=401, detail="Token inválido para negocio")
    business = db.query(Business).filter(Business.id == int(payload["sub"])).first()
    if not business:
        raise HTTPException(status_code=404, detail="Negocio no encontrado")
    return business
