import ipaddress
import json
import os
import socket
from typing import Optional
from urllib.parse import urlparse

import httpx
import stripe
from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address
from sqlalchemy.orm import Session

from auth import (
    create_access_token,
    create_business_access_token,
    get_current_user,
    get_current_business,
    hash_password,
    verify_password,
)
from database import Base, SessionLocal, engine, get_db
from models import Business, DislikedTag, Favorite, Plan, User, WebhookConfig
from schemas import (
    BudgetTopUp,
    BusinessLogin,
    BusinessRegister,
    BusinessResponse,
    BusinessStats,
    BusinessTokenResponse,
    DislikeTagsRequest,
    FavoriteResponse,
    PlanCreate,
    PlanResponse,
    SponsoredPlanCreate,
    SponsoredPlanResponse,
    TokenResponse,
    UserLogin,
    UserRegister,
    UserResponse,
    WebhookConfigCreate,
    WebhookConfigResponse,
)

# Create tables
Base.metadata.create_all(bind=engine)

app = FastAPI(title="PLAIN API", version="2.1.0")

# Rate limiting — auth endpoints son bruteforceables
limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# Stripe configuration
STRIPE_SECRET_KEY = os.environ.get("STRIPE_SECRET_KEY", "")
STRIPE_WEBHOOK_SECRET = os.environ.get("STRIPE_WEBHOOK_SECRET", "")
if STRIPE_SECRET_KEY:
    stripe.api_key = STRIPE_SECRET_KEY
    print("✅ Stripe configured")
else:
    print("⚠️  STRIPE_SECRET_KEY not set — Stripe payments disabled")

# CORS for Android app + web panel — orígenes explícitos, nunca "*"
ALLOWED_ORIGINS = [
    o.strip()
    for o in os.environ.get(
        "PLAIN_ALLOWED_ORIGINS",
        "http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173",
    ).split(",")
    if o.strip()
]
app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Serve static web panel
web_dir = os.path.join(os.path.dirname(__file__), "web")
os.makedirs(web_dir, exist_ok=True)
app.mount("/business", StaticFiles(directory=web_dir, html=True), name="business")


# === Seed data ===

SEED_PLANS = [
    # Barcelona
    {"title": "Subir al Tibidabo al atardecer", "description": "Bus hasta el Parque de Atracciones y subida a pie. Vistas 360° de toda la ciudad al atardecer.", "location": "Tibidabo", "price": "0€", "plan_type": "AMBOS", "duration": "2h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🌅", "tags": ["naturaleza", "gratis", "vistas", "atardecer"]},
    {"title": "Tapeo por El Born", "description": "De bar en bar: calles medievales, vinos y tapas. Imprescindible: La Vinya del Senyor.", "location": "El Born", "price": "10-15€", "plan_type": "PAREJA", "duration": "3h", "category": "Gastronomía", "city": "BARCELONA", "emoji": "🥘", "tags": ["comida", "romantico", "paseo", "cultura"]},
    {"title": "Mercat de la Boqueria", "description": "Degustación de jugos, tapas y frutas exóticas. Ideal para ir solo y perderse entre puestos.", "location": "La Rambla", "price": "5-10€", "plan_type": "SOLO", "duration": "1.5h", "category": "Gastronomía", "city": "BARCELONA", "emoji": "🍤", "tags": ["comida", "mercado", "solo"]},
    {"title": "Bunkers del Carmel", "description": "Las mejores vistas de Barcelona gratis. Lleva cerveza y ponte al atardecer.", "location": "Turó de la Rovira", "price": "0€", "plan_type": "AMBOS", "duration": "1.5h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "📸", "tags": ["vistas", "gratis", "atardecer", "foto"]},
    {"title": "Ruta graffiti por el Raval", "description": "Arte urbano, murales enormes y galerías callejeras. Recorrido autoguiado.", "location": "El Raval", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Cultura", "city": "BARCELONA", "emoji": "🎨", "tags": ["arte", "gratis", "paseo", "solo"]},
    {"title": "Picnic en la Ciutadella", "description": "El parque más bonito de la ciudad. Ideal para llevar queso, vino y manta.", "location": "Parc de la Ciutadella", "price": "5€", "plan_type": "PAREJA", "duration": "2h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🧺", "tags": ["naturaleza", "romantico", "picnic", "barato"]},
    {"title": "Museo Picasso (domingo gratis)", "description": "Entrada gratuita desde las 15h los domingos. Una de las mejores colecciones.", "location": "El Born", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Cultura", "city": "BARCELONA", "emoji": "🖼️", "tags": ["arte", "cultura", "gratis", "museo"]},
    {"title": "Baño en la Barceloneta + vermut", "description": "Día de playa urbana con baño y luego vermut en un chiringuito.", "location": "Barceloneta", "price": "0€", "plan_type": "AMBOS", "duration": "3h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🏖️", "tags": ["playa", "gratis", "comida", "verano"]},
    {"title": "Ruta modernista por el Eixample", "description": "Recorrido gratuito: Casa Batlló, La Pedrera, Sagrada Família desde fuera.", "location": "Eixample", "price": "0€", "plan_type": "SOLO", "duration": "2.5h", "category": "Cultura", "city": "BARCELONA", "emoji": "🏛️", "tags": ["arquitectura", "cultura", "gratis", "paseo", "solo"]},
    {"title": "Mercat dels Encants", "description": "Mercadillo de domingo con gangas, antigüedades y objetos únicos.", "location": "Glòries", "price": "0€", "plan_type": "AMBOS", "duration": "2h", "category": "Compras", "city": "BARCELONA", "emoji": "🛍️", "tags": ["compras", "mercadillo", "gratis"]},
    {"title": "Pasear por el Laberinto de Horta", "description": "El jardín laberíntico más antiguo de Barcelona. Entrada 3€.", "location": "Horta", "price": "3€", "plan_type": "PAREJA", "duration": "1.5h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🌳", "tags": ["naturaleza", "jardines", "romantico", "barato"]},
    {"title": "Ruta gótica + calles escondidas", "description": "Descubre el Barri Gòtic: el Puente del Obispo, la Catedral y plazas secretas.", "location": "Barri Gòtic", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Cultura", "city": "BARCELONA", "emoji": "📷", "tags": ["arquitectura", "paseo", "gratis", "foto", "solo"]},
    {"title": "Montjuïc: jardins + castillo", "description": "Subida a pie o en teleférico, jardines botánicos y vistas al puerto.", "location": "Montjuïc", "price": "0€", "plan_type": "SOLO", "duration": "3h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🏰", "tags": ["naturaleza", "vistas", "gratis", "paseo", "solo"]},
    {"title": "Sónar de día", "description": "Entrada de día al Sónar. Música, arte digital y ambiente único.", "location": "Fira Gran Via", "price": "12€", "plan_type": "AMBOS", "duration": "4h", "category": "Música", "city": "BARCELONA", "emoji": "🎧", "tags": ["musica", "festival", "arte", "pago"]},
    # Villena
    {"title": "Castillo de la Atalaya", "description": "Impresionante castillo medieval con vistas a todo el Valle. Visita guiada 3€.", "location": "Castillo", "price": "3€", "plan_type": "AMBOS", "duration": "1.5h", "category": "Cultura", "city": "VILLENA", "emoji": "🏰", "tags": ["castillo", "historia", "cultura", "barato"]},
    {"title": "Ruta senderismo Sierra de la Villa", "description": "Ruta circular de 6km por la sierra con vistas al castillo y al valle.", "location": "Sierra de la Villa", "price": "0€", "plan_type": "SOLO", "duration": "3h", "category": "Naturaleza", "city": "VILLENA", "emoji": "🥾", "tags": ["senderismo", "naturaleza", "gratis", "deporte", "solo"]},
    {"title": "Paseo casco antiguo + tapas", "description": "Calles empedradas, plazas con encanto y tapeo de calidad a precios de pueblo.", "location": "Casco antiguo", "price": "10€", "plan_type": "PAREJA", "duration": "2h", "category": "Gastronomía", "city": "VILLENA", "emoji": "🥘", "tags": ["comida", "paseo", "romantico", "cultura"]},
    {"title": "Street Food Market", "description": "Comida internacional, música en directo y artesanía. Entrada gratuita.", "location": "Recinto Ferial", "price": "0€", "plan_type": "AMBOS", "duration": "3h", "category": "Gastronomía", "city": "VILLENA", "emoji": "🍔", "tags": ["comida", "mercado", "gratis", "musica"]},
    {"title": "Ruta en bici por Las Virtudes", "description": "Ruta fácil en bici hasta el Santuario de Las Virtudes, rodeado de naturaleza.", "location": "Las Virtudes", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Deporte", "city": "VILLENA", "emoji": "🚴", "tags": ["deporte", "naturaleza", "gratis", "bici", "solo"]},
    {"title": "Mercado de diseño", "description": "Puestos de cerámica, ilustración y diseño local.", "location": "Recinto Ferial", "price": "0€", "plan_type": "PAREJA", "duration": "1h", "category": "Compras", "city": "VILLENA", "emoji": "🎨", "tags": ["compras", "arte", "mercadillo", "gratis"]},
    {"title": "Día de piscina natural", "description": "Baño en el Pantano de Villena. Lleva nevera y sombrilla.", "location": "Pantano de Villena", "price": "0€", "plan_type": "SOLO", "duration": "Todo el día", "category": "Naturaleza", "city": "VILLENA", "emoji": "🏊", "tags": ["naturaleza", "gratis", "verano", "baño", "solo"]},
    {"title": "Teatro Chapí", "description": "Obra de teatro o cine de cartelera en el teatro histórico.", "location": "Teatro Chapí", "price": "5-8€", "plan_type": "PAREJA", "duration": "2h", "category": "Cultura", "city": "VILLENA", "emoji": "🎭", "tags": ["teatro", "cultura", "romantico", "barato"]},
    {"title": "Fiestas del Medievo", "description": "Mercado medieval, justas, música y animación callejera.", "location": "Centro histórico", "price": "0€", "plan_type": "AMBOS", "duration": "4h", "category": "Cultura", "city": "VILLENA", "emoji": "⚔️", "tags": ["fiestas", "cultura", "gratis", "historia"]},
    {"title": "Cata de vinos local", "description": "Degustación de vinos de la DOP Alicante en bodegas familiares.", "location": "Bodega local", "price": "5-10€", "plan_type": "PAREJA", "duration": "1.5h", "category": "Gastronomía", "city": "VILLENA", "emoji": "🍷", "tags": ["comida", "vino", "romantico", "barato"]},
]


def seed_plans():
    """Insert seed plans if DB is empty."""
    db = SessionLocal()
    try:
        count = db.query(Plan).filter(Plan.is_default == True).count()
        if count == 0:
            for p in SEED_PLANS:
                tags = p.pop("tags", [])
                plan = Plan(**p, tags=json.dumps(tags), is_default=True)
                db.add(plan)
            db.commit()
            print(f"✅ Seeded {len(SEED_PLANS)} default plans with tags")
        else:
            print(f"📦 {count} default plans already in DB")
    finally:
        db.close()


# === Routes ===


@app.get("/")
def root():
    return {"app": "PLAIN API", "version": "2.0.0"}


# --- User Auth ---


@app.post("/api/register", response_model=TokenResponse)
@limiter.limit("10/minute")
def register(request: Request, data: UserRegister, db: Session = Depends(get_db)):
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
@limiter.limit("10/minute")
def login(request: Request, data: UserLogin, db: Session = Depends(get_db)):
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


# --- Business Auth ---


@app.post("/api/business/register", response_model=BusinessTokenResponse)
@limiter.limit("10/minute")
def register_business(request: Request, data: BusinessRegister, db: Session = Depends(get_db)):
    if db.query(Business).filter(Business.email == data.email).first():
        raise HTTPException(status_code=400, detail="Email ya registrado")
    biz = Business(
        company_name=data.company_name,
        email=data.email,
        password_hash=hash_password(data.password),
    )
    db.add(biz)
    db.commit()
    db.refresh(biz)
    return BusinessTokenResponse(
        access_token=create_business_access_token(biz.id),
        business=BusinessResponse.model_validate(biz),
    )


@app.post("/api/business/login", response_model=BusinessTokenResponse)
@limiter.limit("10/minute")
def login_business(request: Request, data: BusinessLogin, db: Session = Depends(get_db)):
    biz = db.query(Business).filter(Business.email == data.email).first()
    if not biz or not verify_password(data.password, biz.password_hash):
        raise HTTPException(status_code=401, detail="Email o contraseña incorrectos")
    return BusinessTokenResponse(
        access_token=create_business_access_token(biz.id),
        business=BusinessResponse.model_validate(biz),
    )


@app.get("/api/business/me", response_model=BusinessResponse)
def get_business_me(business: Business = Depends(get_current_business)):
    return business


# --- Sponsored Plans (Business) ---


@app.post("/api/business/plans", response_model=SponsoredPlanResponse)
def create_sponsored_plan(
    data: SponsoredPlanCreate,
    db: Session = Depends(get_db),
    business: Business = Depends(get_current_business),
):
    """Create a sponsored plan. Budget is deducted from business balance."""
    if business.balance_cents < data.budget_cents:
        raise HTTPException(status_code=400, detail="Saldo insuficiente. Recarga tu cuenta.")
    plan = Plan(
        title=data.title,
        description=data.description,
        location=data.location,
        price=data.price,
        plan_type=data.plan_type,
        duration=data.duration,
        category=data.category,
        city=data.city,
        emoji=data.emoji,
        tags=json.dumps(data.tags),
        business_id=business.id,
        is_sponsored=True,
        budget_cents=data.budget_cents,
        cost_per_like_cents=data.cost_per_like_cents,
        is_active=True,
    )
    # Reserve budget
    business.balance_cents -= data.budget_cents
    db.add(plan)
    db.commit()
    db.refresh(plan)
    return plan


@app.get("/api/business/plans", response_model=list[SponsoredPlanResponse])
def list_sponsored_plans(
    db: Session = Depends(get_db),
    business: Business = Depends(get_current_business),
):
    plans = db.query(Plan).filter(
        Plan.business_id == business.id
    ).order_by(Plan.created_at.desc()).all()
    return plans


@app.delete("/api/business/plans/{plan_id}")
def delete_sponsored_plan(
    plan_id: int,
    db: Session = Depends(get_db),
    business: Business = Depends(get_current_business),
):
    plan = db.query(Plan).filter(
        Plan.id == plan_id,
        Plan.business_id == business.id
    ).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan no encontrado")
    # Return remaining budget to business
    remaining = plan.budget_cents - plan.spent_cents
    if remaining > 0:
        business.balance_cents += remaining
    db.delete(plan)
    db.commit()
    return {"status": "deleted", "refunded_cents": remaining}


@app.get("/api/business/stats", response_model=BusinessStats)
def business_stats(
    db: Session = Depends(get_db),
    business: Business = Depends(get_current_business),
):
    plans = db.query(Plan).filter(Plan.business_id == business.id).all()
    total_plans = len(plans)
    active_plans = sum(1 for p in plans if p.is_active)
    total_budget = sum(p.budget_cents for p in plans)
    total_spent = sum(p.spent_cents for p in plans)
    total_likes = db.query(Favorite).filter(
        Favorite.plan_id.in_([p.id for p in plans])
    ).count() if plans else 0

    return BusinessStats(
        total_plans=total_plans,
        active_plans=active_plans,
        total_budget_cents=total_budget,
        total_spent_cents=total_spent,
        total_likes=total_likes,
        balance_cents=business.balance_cents,
    )


@app.post("/api/business/top-up")
def top_up_balance(
    data: BudgetTopUp,
    db: Session = Depends(get_db),
    business: Business = Depends(get_current_business),
):
    """Top up business balance (pre-payment simulation, wire to Stripe later)."""
    # Seguridad: en producción (Stripe configurado) el crédito SOLO entra vía webhook firmado.
    if STRIPE_SECRET_KEY:
        raise HTTPException(
            status_code=403,
            detail="Top-up manual deshabilitado en producción. Usa Stripe Checkout.",
        )
    business.balance_cents += data.amount_cents
    db.commit()
    return {
        "status": "ok",
        "new_balance_cents": business.balance_cents,
    }


# --- Plans (User) ---


@app.get("/api/plans", response_model=list[PlanResponse])
def list_plans(
    city: Optional[str] = None,
    plan_type: Optional[str] = None,
    category: Optional[str] = None,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """List plans: free + active sponsored, sorted with user's disliked tags last."""
    query = db.query(Plan).filter(
        Plan.is_default == True  # Free seed plans
    )
    if city:
        query = query.filter(Plan.city == city.upper())
    if plan_type:
        query = query.filter(Plan.plan_type == plan_type.upper())
    if category:
        query = query.filter(Plan.category == category)

    free_plans = query.all()

    # Also fetch active sponsored plans for this city
    sponsored_query = db.query(Plan).filter(
        Plan.is_sponsored == True,
        Plan.is_active == True,
    )
    if city:
        sponsored_query = sponsored_query.filter(Plan.city == city.upper())
    sponsored_plans = sponsored_query.all()

    # Combine: free first, then sponsored (interleaved)
    all_plans = list(free_plans) + list(sponsored_plans)

    # Deprioritize by disliked tags
    disliked: list[DislikedTag] = db.query(DislikedTag).filter(
        DislikedTag.user_id == user.id,
        DislikedTag.count > 0,
    ).all()
    disliked_tag_set = {dt.tag for dt in disliked}

    if disliked_tag_set:
        def plan_score(p: Plan) -> int:
            p_tags = p.get_tags()
            return sum(1 for t in p_tags if t in disliked_tag_set)
        all_plans.sort(key=plan_score)

    return all_plans


@app.post("/api/plans", response_model=PlanResponse)
def create_plan(
    data: PlanCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    plan = Plan(
        **data.model_dump(exclude={"tags"}),
        tags=json.dumps(data.tags),
        created_by=user.id,
    )
    db.add(plan)
    db.commit()
    db.refresh(plan)
    return plan


# --- Favorites ---


@app.get("/api/favorites", response_model=list[FavoriteResponse])
def list_favorites(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    favorites = (
        db.query(Favorite)
        .filter(Favorite.user_id == user.id)
        .order_by(Favorite.created_at.desc())
        .all()
    )
    return favorites


@app.post("/api/favorites/{plan_id}")
def add_favorite(
    plan_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    plan = db.query(Plan).filter(Plan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan no encontrado")

    existing = db.query(Favorite).filter(
        Favorite.user_id == user.id,
        Favorite.plan_id == plan_id,
    ).first()
    if existing:
        return {"status": "already_favorited"}

    fav = Favorite(user_id=user.id, plan_id=plan_id)
    db.add(fav)

    # If sponsored: deduct cost per like from budget
    if plan.is_sponsored and plan.is_active:
        plan.spent_cents = (plan.spent_cents or 0) + plan.cost_per_like_cents
        if plan.spent_cents >= plan.budget_cents:
            plan.is_active = False

    db.commit()
    return {"status": "favorited"}


@app.delete("/api/favorites/{plan_id}")
def remove_favorite(
    plan_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    fav = db.query(Favorite).filter(
        Favorite.user_id == user.id,
        Favorite.plan_id == plan_id,
    ).first()
    if not fav:
        raise HTTPException(status_code=404, detail="Favorito no encontrado")
    db.delete(fav)
    db.commit()
    return {"status": "removed"}


# --- Disliked Tags ---


@app.post("/api/dislike-tags")
def dislike_tags(
    data: DislikeTagsRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    for tag in data.tags:
        existing = db.query(DislikedTag).filter(
            DislikedTag.user_id == user.id,
            DislikedTag.tag == tag,
        ).first()
        if existing:
            existing.count += 1
        else:
            db.add(DislikedTag(user_id=user.id, tag=tag, count=1))
    db.commit()
    return {"status": "updated", "tags": data.tags}


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

    # SSRF protection: solo http/https, nunca IPs privadas/loopback/metadata cloud
    parsed = urlparse(wh.url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise HTTPException(status_code=400, detail="URL de webhook inválida (solo http/https)")

    hostname = parsed.hostname
    try:
        ip = socket.gethostbyname(hostname)
        addr = ipaddress.ip_address(ip)
        if addr.is_private or addr.is_loopback or addr.is_link_local or addr.is_reserved or addr.is_multicast:
            raise HTTPException(status_code=400, detail="URL de webhook apunta a una dirección bloqueada")
    except (socket.gaierror, socket.timeout, OSError, ValueError):
        raise HTTPException(status_code=400, detail="No se pudo resolver el host del webhook")

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
            "tags": plan.get_tags(),
            "is_sponsored": plan.is_sponsored,
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
        # No devolver detalles internos del error (fuga de información)
        raise HTTPException(status_code=502, detail="Error al llamar al webhook")


# --- Stripe Payments ---


@app.post("/api/business/create-checkout-session")
def create_checkout_session(
    request: Request,
    db: Session = Depends(get_db),
    business: Business = Depends(get_current_business),
    amount_cents: int = 1000,
):
    """Create a Stripe Checkout session for top-up."""
    if not STRIPE_SECRET_KEY:
        raise HTTPException(status_code=400, detail="Stripe no configurado. Usa /api/business/top-up (simulación)")

    if amount_cents < 100 or amount_cents > 100000:
        raise HTTPException(status_code=400, detail="Importe entre 1€ y 1.000€")

    try:
        # Create or reuse Stripe customer
        customer_id = business.stripe_customer_id
        if not customer_id:
            customer = stripe.Customer.create(
                email=business.email,
                name=business.company_name,
                metadata={"business_id": str(business.id)},
            )
            customer_id = customer.id
            business.stripe_customer_id = customer_id
            db.commit()

        session = stripe.checkout.Session.create(
            customer=customer_id,
            payment_method_types=["card"],
            line_items=[{
                "price_data": {
                    "currency": "eur",
                    "product_data": {"name": f"Recarga PLAIN - {amount_cents//100}€"},
                    "unit_amount": amount_cents,
                },
                "quantity": 1,
            }],
            mode="payment",
            success_url=str(request.base_url) + "business?payment=success",
            cancel_url=str(request.base_url) + "business?payment=cancelled",
            metadata={"business_id": str(business.id)},
        )
        return {"url": session.url, "session_id": session.id}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error Stripe: {str(e)}")


@app.post("/api/stripe/webhook")
async def stripe_webhook(request: Request, db: Session = Depends(get_db)):
    """Handle Stripe webhook events (checkout.session.completed)."""
    # Seguridad: sin STRIPE_WEBHOOK_SECRET el webhook se rechaza SIEMPRE (fail-closed).
    # Un atacante podría POSTear un checkout.session.completed falso y recibir crédito gratis.
    if not STRIPE_WEBHOOK_SECRET:
        raise HTTPException(
            status_code=503,
            detail="Webhook no configurado (falta STRIPE_WEBHOOK_SECRET).",
        )
    payload = await request.body()
    sig_header = request.headers.get("stripe-signature")
    try:
        event = stripe.Webhook.construct_event(payload, sig_header, STRIPE_WEBHOOK_SECRET)
    except stripe.error.SignatureVerificationError:
        raise HTTPException(status_code=400, detail="Invalid signature")

    if event["type"] == "checkout.session.completed":
        session = event["data"]["object"]
        business_id = int(session.get("metadata", {}).get("business_id", 0))
        amount_cents = session.get("amount_total", 0)

        if business_id and amount_cents > 0:
            biz = db.query(Business).filter(Business.id == business_id).first()
            if biz:
                biz.balance_cents += amount_cents
                db.commit()
                print(f"✅ Stripe: {biz.company_name} recargó {amount_cents}¢ (balance: {biz.balance_cents}¢)")

    return {"status": "ok"}


# --- Start ---

if __name__ == "__main__":
    import uvicorn

    if not os.environ.get("PLAIN_SECRET_KEY"):
        print("⚠️  WARNING: PLAIN_SECRET_KEY no está configurada. Usando clave temporal para desarrollo.")
        print("   Para producción: export PLAIN_SECRET_KEY=***")

    seed_plans()

    # Create web directory placeholder
    web_dir = os.path.join(os.path.dirname(__file__), "web")
    os.makedirs(web_dir, exist_ok=True)

    uvicorn.run(app, host="0.0.0.0", port=8000)
