import json
from typing import Optional
from sqlalchemy import Column, Integer, String, Float, DateTime, ForeignKey, Text, Boolean, UniqueConstraint
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
    favorites = relationship("Favorite", back_populates="user", cascade="all, delete-orphan")
    disliked_tags = relationship("DislikedTag", back_populates="user", cascade="all, delete-orphan")


class Business(Base):
    __tablename__ = "businesses"

    id = Column(Integer, primary_key=True, index=True)
    company_name = Column(String(100), nullable=False)
    email = Column(String(100), unique=True, index=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    balance_cents = Column(Integer, default=0)  # Prepaid balance in euro cents
    stripe_customer_id = Column(String(255), nullable=True)  # Stripe customer ID
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    sponsored_plans = relationship("Plan", back_populates="business", foreign_keys="Plan.business_id")


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
    tags = Column(Text, nullable=False, default="[]")  # JSON array
    created_by = Column(Integer, ForeignKey("users.id"), nullable=True)
    is_default = Column(Boolean, default=False)  # Seed plans

    # Sponsored fields
    business_id = Column(Integer, ForeignKey("businesses.id"), nullable=True)
    is_sponsored = Column(Boolean, default=False)
    budget_cents = Column(Integer, default=0)       # Total budget in cents
    spent_cents = Column(Integer, default=0)         # Spent so far
    cost_per_like_cents = Column(Integer, default=0) # Cost per like in cents
    is_active = Column(Boolean, default=True)        # Active while budget remains

    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    creator = relationship("User", back_populates="plans")
    business = relationship("Business", back_populates="sponsored_plans", foreign_keys=[business_id])

    def get_tags(self) -> list[str]:
        try:
            return json.loads(self.tags) if self.tags else []
        except (json.JSONDecodeError, TypeError):
            return []

    @property
    def budget_remaining_cents(self) -> int:
        return max(0, self.budget_cents - self.spent_cents)

    @property
    def likes_remaining(self) -> int:
        if self.cost_per_like_cents <= 0:
            return 0
        return self.budget_remaining_cents // self.cost_per_like_cents


class Favorite(Base):
    __tablename__ = "favorites"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    plan_id = Column(Integer, ForeignKey("plans.id"), nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    user = relationship("User", back_populates="favorites")
    plan = relationship("Plan")


class DislikedTag(Base):
    __tablename__ = "disliked_tags"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    tag = Column(String(50), nullable=False)
    count = Column(Integer, default=1)

    user = relationship("User", back_populates="disliked_tags")

    __table_args__ = (
        UniqueConstraint("user_id", "tag", name="uq_user_tag"),
    )


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
