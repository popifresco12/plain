from pydantic import BaseModel, EmailStr
from typing import Optional
from datetime import datetime


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

    class Config:
        from_attributes = True


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
    is_default: bool
    created_by: Optional[int] = None
    created_at: datetime

    class Config:
        from_attributes = True


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

    class Config:
        from_attributes = True


class WebhookTriggerRequest(BaseModel):
    plan_id: int
