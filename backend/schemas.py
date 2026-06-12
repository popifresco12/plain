from pydantic import BaseModel, ConfigDict, field_validator
from typing import Optional
from datetime import datetime
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
    category: str = "Ocio"
    city: str
    emoji: str = "📍"
    tags: list[str] = []


class PlanResponse(BaseModel):
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
    is_default: bool
    created_by: Optional[int] = None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)

    @field_validator("tags", mode="before")
    @classmethod
    def parse_tags(cls, v):
        if isinstance(v, str):
            return json.loads(v) if v else []
        return v or []


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
