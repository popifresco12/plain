import json
import os
from typing import Optional

import httpx
from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from auth import (
    create_access_token,
    get_current_user,
    hash_password,
    verify_password,
)
from database import Base, SessionLocal, engine, get_db
from models import Plan, User, WebhookConfig
from schemas import (
    PlanCreate,
    PlanResponse,
    TokenResponse,
    UserLogin,
    UserRegister,
    UserResponse,
    WebhookConfigCreate,
    WebhookConfigResponse,
)

# Create tables
Base.metadata.create_all(bind=engine)

app = FastAPI(title="PLAIN API", version="1.0.0")

# CORS for Android app
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# === Seed data ===

SEED_PLANS = [
    # Barcelona
    {"title": "Subir al Tibidabo al atardecer", "description": "Bus hasta el Parque de Atracciones y subida a pie. Vistas 360° de toda la ciudad al atardecer.", "location": "Tibidabo", "price": "0€", "plan_type": "AMBOS", "duration": "2h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🌅"},
    {"title": "Tapeo por El Born", "description": "De bar en bar: calles medievales, vinos y tapas. Imprescindible: La Vinya del Senyor.", "location": "El Born", "price": "10-15€", "plan_type": "PAREJA", "duration": "3h", "category": "Gastronomía", "city": "BARCELONA", "emoji": "🥘"},
    {"title": "Mercat de la Boqueria", "description": "Degustación de jugos, tapas y frutas exóticas. Ideal para ir solo y perderse entre puestos.", "location": "La Rambla", "price": "5-10€", "plan_type": "SOLO", "duration": "1.5h", "category": "Gastronomía", "city": "BARCELONA", "emoji": "🍤"},
    {"title": "Bunkers del Carmel", "description": "Las mejores vistas de Barcelona gratis. Lleva cerveza y ponte al atardecer.", "location": "Turó de la Rovira", "price": "0€", "plan_type": "AMBOS", "duration": "1.5h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "📸"},
    {"title": "Ruta graffiti por el Raval", "description": "Arte urbano, murales enormes y galerías callejeras. Recorrido autoguiado.", "location": "El Raval", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Cultura", "city": "BARCELONA", "emoji": "🎨"},
    {"title": "Picnic en la Ciutadella", "description": "El parque más bonito de la ciudad. Ideal para llevar queso, vino y manta.", "location": "Parc de la Ciutadella", "price": "5€", "plan_type": "PAREJA", "duration": "2h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🧺"},
    {"title": "Museo Picasso (domingo gratis)", "description": "Entrada gratuita desde las 15h los domingos. Una de las mejores colecciones.", "location": "El Born", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Cultura", "city": "BARCELONA", "emoji": "🖼️"},
    {"title": "Baño en la Barceloneta + vermut", "description": "Día de playa urbana con baño y luego vermut en un chiringuito.", "location": "Barceloneta", "price": "0€", "plan_type": "AMBOS", "duration": "3h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🏖️"},
    {"title": "Ruta modernista por el Eixample", "description": "Recorrido gratuito: Casa Batlló, La Pedrera, Sagrada Família desde fuera.", "location": "Eixample", "price": "0€", "plan_type": "SOLO", "duration": "2.5h", "category": "Cultura", "city": "BARCELONA", "emoji": "🏛️"},
    {"title": "Mercat dels Encants", "description": "Mercadillo de domingo con gangas, antigüedades y objetos únicos.", "location": "Glòries", "price": "0€", "plan_type": "AMBOS", "duration": "2h", "category": "Compras", "city": "BARCELONA", "emoji": "🛍️"},
    {"title": "Pasear por el Laberinto de Horta", "description": "El jardín laberíntico más antiguo de Barcelona. Entrada 3€.", "location": "Horta", "price": "3€", "plan_type": "PAREJA", "duration": "1.5h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🌳"},
    {"title": "Ruta gótica + calles escondidas", "description": "Descubre el Barri Gòtic: el Puente del Obispo, la Catedral y plazas secretas.", "location": "Barri Gòtic", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Cultura", "city": "BARCELONA", "emoji": "📷"},
    {"title": "Montjuïc: jardins + castillo", "description": "Subida a pie o en teleférico, jardines botánicos y vistas al puerto.", "location": "Montjuïc", "price": "0€", "plan_type": "SOLO", "duration": "3h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🏰"},
    {"title": "Sónar de día", "description": "Entrada de día al Sónar. Música, arte digital y ambiente único.", "location": "Fira Gran Via", "price": "12€", "plan_type": "AMBOS", "duration": "4h", "category": "Música", "city": "BARCELONA", "emoji": "🎧"},
    # Villena
    {"title": "Castillo de la Atalaya", "description": "Impresionante castillo medieval con vistas a todo el Valle. Visita guiada 3€.", "location": "Castillo", "price": "3€", "plan_type": "AMBOS", "duration": "1.5h", "category": "Cultura", "city": "VILLENA", "emoji": "🏰"},
    {"title": "Ruta senderismo Sierra de la Villa", "description": "Ruta circular de 6km por la sierra con vistas al castillo y al valle.", "location": "Sierra de la Villa", "price": "0€", "plan_type": "SOLO", "duration": "3h", "category": "Naturaleza", "city": "VILLENA", "emoji": "🥾"},
    {"title": "Paseo casco antiguo + tapas", "description": "Calles empedradas, plazas con encanto y tapeo de calidad a precios de pueblo.", "location": "Casco antiguo", "price": "10€", "plan_type": "PAREJA", "duration": "2h", "category": "Gastronomía", "city": "VILLENA", "emoji": "🥘"},
    {"title": "Street Food Market", "description": "Comida internacional, música en directo y artesanía. Entrada gratuita.", "location": "Recinto Ferial", "price": "0€", "plan_type": "AMBOS", "duration": "3h", "category": "Gastronomía", "city": "VILLENA", "emoji": "🍔"},
    {"title": "Ruta en bici por Las Virtudes", "description": "Ruta fácil en bici hasta el Santuario de Las Virtudes, rodeado de naturaleza.", "location": "Las Virtudes", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Deporte", "city": "VILLENA", "emoji": "🚴"},
    {"title": "Mercado de diseño", "description": "Puestos de cerámica, ilustración y diseño local.", "location": "Recinto Ferial", "price": "0€", "plan_type": "PAREJA", "duration": "1h", "category": "Compras", "city": "VILLENA", "emoji": "🎨"},
    {"title": "Día de piscina natural", "description": "Baño en el Pantano de Villena. Lleva nevera y sombrilla.", "location": "Pantano de Villena", "price": "0€", "plan_type": "SOLO", "duration": "Todo el día", "category": "Naturaleza", "city": "VILLENA", "emoji": "🏊"},
    {"title": "Teatro Chapí", "description": "Obra de teatro o cine de cartelera en el teatro histórico.", "location": "Teatro Chapí", "price": "5-8€", "plan_type": "PAREJA", "duration": "2h", "category": "Cultura", "city": "VILLENA", "emoji": "🎭"},
    {"title": "Fiestas del Medievo", "description": "Mercado medieval, justas, música y animación callejera.", "location": "Centro histórico", "price": "0€", "plan_type": "AMBOS", "duration": "4h", "category": "Cultura", "city": "VILLENA", "emoji": "⚔️"},
    {"title": "Cata de vinos local", "description": "Degustación de vinos de la DOP Alicante en bodegas familiares.", "location": "Bodega local", "price": "5-10€", "plan_type": "PAREJA", "duration": "1.5h", "category": "Gastronomía", "city": "VILLENA", "emoji": "🍷"},
]


def seed_plans():
    """Insert seed plans if DB is empty."""
    db = SessionLocal()
    try:
        count = db.query(Plan).filter(Plan.is_default == True).count()
        if count == 0:
            for p in SEED_PLANS:
                plan = Plan(**p, is_default=True)
                db.add(plan)
            db.commit()
            print(f"✅ Seeded {len(SEED_PLANS)} default plans")
        else:
            print(f"📦 {count} default plans already in DB")
    finally:
        db.close()


# === Routes ===


@app.get("/")
def root():
    return {"app": "PLAIN API", "version": "1.0.0"}


# --- Auth ---


@app.post("/api/register", response_model=TokenResponse)
def register(data: UserRegister, db: Session = Depends(get_db)):
    # Check existing
    if db.query(User).filter(User.username == data.username).first():
        raise HTTPException(status_code=400, detail="Usuario ya existe")
    if db.query(User).filter(User.email == data.email).first():
        raise HTTPException(status_code=400, detail="Email ya registrado")

    user = User(
        username=data.username,
        email=data.email,
        password_hash=hash_password(data.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    return TokenResponse(
        access_token=create_access_token(user.id),
        user=UserResponse.model_validate(user),
    )


@app.post("/api/login", response_model=TokenResponse)
def login(data: UserLogin, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.username == data.username).first()
    if not user or not verify_password(data.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Usuario o contraseña incorrectos")

    return TokenResponse(
        access_token=create_access_token(user.id),
        user=UserResponse.model_validate(user),
    )


@app.get("/api/me", response_model=UserResponse)
def get_me(user: User = Depends(get_current_user)):
    return user


# --- Plans ---


@app.get("/api/plans", response_model=list[PlanResponse])
def list_plans(
    city: Optional[str] = None,
    plan_type: Optional[str] = None,
    category: Optional[str] = None,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    query = db.query(Plan)
    if city:
        query = query.filter(Plan.city == city.upper())
    if plan_type:
        query = query.filter(Plan.plan_type == plan_type.upper())
    if category:
        query = query.filter(Plan.category == category)
    return query.order_by(Plan.id).all()


@app.post("/api/plans", response_model=PlanResponse)
def create_plan(
    data: PlanCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    plan = Plan(
        **data.model_dump(),
        created_by=user.id,
    )
    db.add(plan)
    db.commit()
    db.refresh(plan)
    return plan


# --- Webhook ---


@app.get("/api/webhook", response_model=WebhookConfigResponse)
def get_webhook(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    wh = db.query(WebhookConfig).filter(WebhookConfig.user_id == user.id).first()
    if not wh:
        raise HTTPException(status_code=404, detail="Sin webhook configurado")
    return wh


@app.post("/api/webhook", response_model=WebhookConfigResponse)
def save_webhook(
    data: WebhookConfigCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    wh = db.query(WebhookConfig).filter(WebhookConfig.user_id == user.id).first()
    if wh:
        wh.url = data.url
        wh.api_key = data.api_key
        wh.active = True
    else:
        wh = WebhookConfig(user_id=user.id, url=data.url, api_key=data.api_key)
        db.add(wh)
    db.commit()
    db.refresh(wh)
    return wh


@app.post("/api/webhook/trigger/{plan_id}")
def trigger_webhook(
    plan_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    wh = db.query(WebhookConfig).filter(WebhookConfig.user_id == user.id).first()
    if not wh or not wh.url:
        raise HTTPException(status_code=400, detail="No hay webhook configurado")

    plan = db.query(Plan).filter(Plan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan no encontrado")

    payload = {
        "event": "plan_liked",
        "user": {"id": user.id, "username": user.username},
        "plan": {
            "id": plan.id,
            "title": plan.title,
            "description": plan.description,
            "location": plan.location,
            "price": plan.price,
            "category": plan.category,
            "city": plan.city,
            "duration": plan.duration,
            "emoji": plan.emoji,
        },
    }

    headers = {"Content-Type": "application/json"}
    if wh.api_key:
        headers["Authorization"] = f"Bearer {wh.api_key}"

    try:
        with httpx.Client(timeout=10) as client:
            resp = client.post(wh.url, json=payload, headers=headers)
            return {
                "status": "sent",
                "webhook_url": wh.url,
                "response_code": resp.status_code,
                "response_body": resp.text[:500],
            }
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Error al llamar al webhook: {str(e)}")


# --- Start ---

if __name__ == "__main__":
    import uvicorn

    seed_plans()
    uvicorn.run(app, host="0.0.0.0", port=8000)
