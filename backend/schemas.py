from pydantic import BaseModel, ConfigDict, Field, field_validator
from typing import Optional
from datetime import datetime, date
import json


# === Auth ===

class UserRegister(BaseModel):
    username: str
    email: str
    password: str


class UserLogin(BaseModel):
    username: str
    password: str


class UserResponse(BaseModel):
    id: int
    username: str
    email: str
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserResponse


# === Plans ===

class PlanCreate(BaseModel):
    title: str
    description: str
    location: str
    price: str = "0€"
    plan_type: str = "AMBOS"
    duration: str = "2h"
    availability: str = "Todo el año"
    category: str = "Ocio"
    city: str
    emoji: str = "📍"
    tags: list[str] = []
    # Availability dates (auto activation)
    available_from: Optional[date] = None
    available_until: Optional[date] = None
    recurring: Optional[str] = None  # "MON,WED,FRI" o None


class PlanResponse(BaseModel):
    id: int
    title: str
    description: str
    location: str
    price: str
    plan_type: str
    duration: str
    availability: str = "Todo el año"
    category: str
    city: str
    emoji: str
    tags: list[str] = []
    is_default: bool
    created_by: Optional[int] = None
    created_at: datetime
    is_sponsored: bool = False
    business_id: Optional[int] = None
    budget_cents: int = 0
    spent_cents: int = 0
    cost_per_like_cents: int = 0
    is_active: bool = True
    # Availability dates + computed flag
    available_from: Optional[date] = None
    available_until: Optional[date] = None
    recurring: Optional[str] = None
    is_available_now: bool = True  # Computed: active AND within dates

    model_config = ConfigDict(from_attributes=True)

    @field_validator("tags", mode="before")
    @classmethod
    def parse_tags(cls, v):
        if isinstance(v, str):
            return json.loads(v) if v else []
        return v or []


# === Business ===

class BusinessRegister(BaseModel):
    company_name: str
    email: str
    password: str


class BusinessLogin(BaseModel):
    email: str
    password: str


class BusinessResponse(BaseModel):
    id: int
    company_name: str
    email: str
    balance_cents: int = 0
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class BusinessTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    business: BusinessResponse


class SponsoredPlanCreate(BaseModel):
    title: str
    description: str
    location: str
    price: str = "0€"
    plan_type: str = "AMBOS"
    duration: str = "2h"
    category: str = "Ocio"
    city: str
    emoji: str = "📍"
    tags: list[str] = []
    budget_cents: int = Field(500, gt=0)       # Default 5€ budget, must be > 0
    cost_per_like_cents: int = Field(10, gt=0)  # Default 0.10€ per like, must be > 0
    available_from: Optional[date] = None
    available_until: Optional[date] = None
    recurring: Optional[str] = None


class SponsoredPlanResponse(BaseModel):
    id: int
    title: str
    description: str
    location: str
    price: str
    plan_type: str
    duration: str
    category: str
    city: str
    emoji: str
    tags: list[str] = []
    is_sponsored: bool
    budget_cents: int
    spent_cents: int
    cost_per_like_cents: int
    is_active: bool
    likes_remaining: int = 0
    created_at: datetime
    available_from: Optional[date] = None
    available_until: Optional[date] = None
    recurring: Optional[str] = None
    is_available_now: bool = True

    model_config = ConfigDict(from_attributes=True)

    @field_validator("tags", mode="before")
    @classmethod
    def parse_tags(cls, v):
        if isinstance(v, str):
            return json.loads(v) if v else []
        return v or []


class BusinessStats(BaseModel):
    total_plans: int
    active_plans: int
    total_budget_cents: int
    total_spent_cents: int
    total_likes: int
    balance_cents: int


class BudgetTopUp(BaseModel):
    amount_cents: int = Field(..., gt=0)


# === Favorites ===

class FavoriteResponse(BaseModel):
    id: int
    plan_id: int
    created_at: datetime
    plan: PlanResponse

    model_config = ConfigDict(from_attributes=True)


# === Disliked Tags ===

class DislikeTagsRequest(BaseModel):
    tags: list[str]


# === Webhook ===

class WebhookConfigCreate(BaseModel):
    url: str
    api_key: Optional[str] = None


class WebhookConfigResponse(BaseModel):
    id: int
    url: Optional[str] = None
    api_key: Optional[str] = None
    active: bool
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)


class WebhookTriggerRequest(BaseModel):
    plan_id: int


# === Trip groups (BlaBlaCar-style) ===

class TripGroupCreate(BaseModel):
    plan_id: int
    title: str
    meeting_point: Optional[str] = None
    meet_at: Optional[datetime] = None
    seats: int = 4
    transport: str = "COCHE"  # COCHE, ANDANDO, BUS, MOTO
    notes: Optional[str] = None


class TripGroupMemberOut(BaseModel):
    user_id: int
    username: str
    joined_at: datetime

    model_config = ConfigDict(from_attributes=True)


class TripGroupResponse(BaseModel):
    id: int
    plan_id: int
    plan_title: str = ""
    owner_id: int
    owner_username: str = ""
    title: str
    meeting_point: Optional[str] = None
    meet_at: Optional[datetime] = None
    seats: int
    transport: str
    notes: Optional[str] = None
    created_at: datetime
    members: list[TripGroupMemberOut] = []
    seats_taken: int = 0

    model_config = ConfigDict(from_attributes=True)


class TripGroupJoin(BaseModel):
    pass
