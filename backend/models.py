from sqlalchemy import Column, Integer, String, Float, DateTime, ForeignKey, Text, Boolean
from sqlalchemy.orm import relationship
from datetime import datetime, timezone

from database import Base


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(50), unique=True, index=True, nullable=False)
    email = Column(String(100), unique=True, index=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    plans = relationship("Plan", back_populates="creator")
    webhook = relationship("WebhookConfig", back_populates="user", uselist=False)


class Plan(Base):
    __tablename__ = "plans"

    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(200), nullable=False)
    description = Column(Text, nullable=False)
    location = Column(String(200), nullable=False)
    price = Column(String(50), nullable=False, default="0€")
    plan_type = Column(String(20), nullable=False, default="AMBOS")  # SOLO, PAREJA, AMBOS
    duration = Column(String(50), nullable=False, default="2h")
    category = Column(String(100), nullable=False, default="Ocio")
    city = Column(String(50), nullable=False)  # BARCELONA, VILLENA
    emoji = Column(String(10), nullable=False, default="📍")
    created_by = Column(Integer, ForeignKey("users.id"), nullable=True)
    is_default = Column(Boolean, default=False)  # Seed plans
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    creator = relationship("User", back_populates="plans")


class WebhookConfig(Base):
    __tablename__ = "webhooks"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), unique=True, nullable=False)
    url = Column(String(500), nullable=True)
    api_key = Column(String(255), nullable=True)
    active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc))

    user = relationship("User", back_populates="webhook")
