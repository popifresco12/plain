import ipaddress
import json
import os
import secrets
import socket
from datetime import date, datetime, timedelta, timezone
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
from sqlalchemy import func
from sqlalchemy.orm import Session

import geo
from auth import (
    create_access_token,
    create_business_access_token,
    get_current_user,
    get_current_business,
    hash_password,
    verify_password,
)
from database import Base, SessionLocal, engine, get_db
from models import (
    AccountCode,
    Business,
    CrashReport,
    DislikedTag,
    Favorite,
    GroupMessage,
    Plan,
    PlanEvent,
    PlanReport,
    TripGroup,
    TripGroupMember,
    User,
    WebhookConfig,
)
from schemas import (
    EventsIn,
    ForgotPasswordIn,
    ImageSearchResult,
    PlanReportIn,
    ProfileUpdateIn,
    ResetPasswordIn,
    VerifyEmailIn,
    CrashReportIn,
    CrashReportOut,
    BootstrapResult,
    BudgetTopUp,
    BusinessLogin,
    BusinessRegister,
    BusinessResponse,
    BusinessStats,
    BusinessTokenResponse,
    DislikeTagsRequest,
    FavoriteResponse,
    GroupMessageCreate,
    GroupMessageOut,
    PlanCreate,
    PlanResponse,
    SponsoredPlanCreate,
    SponsoredPlanResponse,
    TokenResponse,
    TripGroupCreate,
    TripGroupResponse,
    UserLogin,
    UserRegister,
    UserResponse,
    WebhookConfigCreate,
    WebhookConfigResponse,
    TripGroupCreate,
    TripGroupMemberOut,
    TripGroupResponse,
    AccountDeleteIn,
)

# Create tables
Base.metadata.create_all(bind=engine)

from fastapi.responses import FileResponse, HTMLResponse
app = FastAPI(title="PLAIN API", version="2.1.0")

@app.on_event("startup")
def seed_on_startup():
    """Seed automático en producción (uvicorn main:app no pasa por __main__)."""
    try:
        ensure_schema()
    except Exception as e:
        print(f"⚠️ ensure_schema falló (no crítico): {e}")
    try:
        seed_plans()
    except Exception as e:
        print(f"⚠️ seed_plans falló (no crítico): {e}")

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


APP_VERSION = "0.6.0"

# Verificación de email: implementada pero APAGADA por defecto (para poder probar
# sin depender del correo). Pon REQUIRE_EMAIL_VERIFICATION=1 en el entorno para activarla.
REQUIRE_EMAIL_VERIFICATION = os.environ.get("REQUIRE_EMAIL_VERIFICATION", "0").lower() in ("1", "true", "yes")

# Límite de planes creados por usuario (anti-spam; los planes semilla no cuentan)
MAX_PLANS_PER_USER = int(os.environ.get("MAX_PLANS_PER_USER", "25"))

# Códigos de un solo uso (recuperar contraseña / verificar email)
CODE_TTL_MINUTES = 30
SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", "587"))
SMTP_USER = os.environ.get("SMTP_USER")
SMTP_PASS = os.environ.get("SMTP_PASS")

# Búsqueda de fotos: Openverse agrega imágenes Creative Commons y NO necesita API key
OPENVERSE_URL = "https://api.openverse.org/v1/images/"


@app.get("/health")
def health_check():
    """Healthcheck para orquestadores (Render, Docker HEALTHCHECK).

    Incluye la versión y el commit desplegado: Render lo inyecta en
    RENDER_GIT_COMMIT, así que el workflow puede comprobar de verdad si
    producción está al día (el auto-deploy ya falló en silencio una vez).
    """
    commit = os.environ.get("RENDER_GIT_COMMIT") or os.environ.get("GIT_COMMIT") or "desconocido"
    return {
        "status": "ok",
        "version": APP_VERSION,
        "commit": commit[:8],
    }


# === Seed data ===

SEED_PLANS = [
    # Barcelona
    {"title": "Subir al Tibidabo al atardecer", "description": "Bus hasta el Parque de Atracciones y subida a pie. Vistas 360° de toda la ciudad al atardecer.", "location": "Tibidabo", "price": "0€", "plan_type": "AMBOS", "duration": "2h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🌅", "tags": ["naturaleza", "gratis", "vistas", "atardecer"]},
    {"title": "Tapeo por El Born", "description": "De bar en bar: calles medievales, vinos y tapas. Imprescindible: La Vinya del Senyor.", "location": "El Born", "price": "10-15€", "plan_type": "PAREJA", "duration": "3h", "category": "Gastronomía", "city": "BARCELONA", "emoji": "🥘", "tags": ["comida", "romantico", "paseo", "cultura"]},
    {"title": "Mercat de la Boqueria", "description": "Degustación de jugos, tapas y frutas exóticas. Ideal para ir solo y perderse entre puestos.", "location": "La Rambla", "price": "5-10€", "plan_type": "SOLO", "duration": "1.5h", "category": "Gastronomía", "city": "BARCELONA", "emoji": "🍤", "tags": ["comida", "mercado", "solo"]},
    {"title": "Bunkers del Carmel", "description": "Las mejores vistas de Barcelona gratis. Lleva cerveza y ponte al atardecer.", "location": "Turó de la Rovira", "price": "0€", "plan_type": "AMBOS", "duration": "1.5h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "📸", "tags": ["vistas", "gratis", "atardecer", "foto"]},
    {"title": "Ruta graffiti por el Raval", "description": "Arte urbano, murales enormes y galerías callejeras. Recorrido autoguiado.", "location": "El Raval", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Cultura", "city": "BARCELONA", "emoji": "🎨", "tags": ["arte", "gratis", "paseo", "solo"]},
    {"title": "Picnic en la Ciutadella", "description": "El parque más bonito de la ciudad. Ideal para llevar queso, vino y manta.", "location": "Parc de la Ciutadella", "price": "5€", "plan_type": "PAREJA", "duration": "2h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🧺", "tags": ["naturaleza", "romantico", "picnic", "barato"]},
    {"title": "Museo Picasso (domingo gratis)", "description": "Entrada gratuita desde las 15h los domingos. Una de las mejores colecciones.", "location": "El Born", "price": "0€", "plan_type": "SOLO", "duration": "2h", "availability": "Domingos 15h", "category": "Cultura", "city": "BARCELONA", "emoji": "🖼️", "tags": ["arte", "cultura", "gratis", "museo"], "recurring": "SUN"},
    {"title": "Baño en la Barceloneta + vermut", "description": "Día de playa urbana con baño y luego vermut en un chiringuito.", "location": "Barceloneta", "price": "0€", "plan_type": "AMBOS", "duration": "3h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🏖️", "tags": ["playa", "gratis", "comida", "verano"]},
    {"title": "Ruta modernista por el Eixample", "description": "Recorrido gratuito: Casa Batlló, La Pedrera, Sagrada Família desde fuera.", "location": "Eixample", "price": "0€", "plan_type": "SOLO", "duration": "2.5h", "category": "Cultura", "city": "BARCELONA", "emoji": "🏛️", "tags": ["arquitectura", "cultura", "gratis", "paseo", "solo"]},
    {"title": "Mercat dels Encants", "description": "Mercadillo de domingo con gangas, antigüedades y objetos únicos.", "location": "Glòries", "price": "0€", "plan_type": "AMBOS", "duration": "2h", "availability": "Lun-Mié-Vie-Sáb", "category": "Compras", "city": "BARCELONA", "emoji": "🛍️", "tags": ["compras", "mercadillo", "gratis"], "recurring": "MON,WED,FRI,SAT"},
    {"title": "Pasear por el Laberinto de Horta", "description": "El jardín laberíntico más antiguo de Barcelona. Entrada 3€.", "location": "Horta", "price": "3€", "plan_type": "PAREJA", "duration": "1.5h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🌳", "tags": ["naturaleza", "jardines", "romantico", "barato"]},
    {"title": "Ruta gótica + calles escondidas", "description": "Descubre el Barri Gòtic: el Puente del Obispo, la Catedral y plazas secretas.", "location": "Barri Gòtic", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Cultura", "city": "BARCELONA", "emoji": "📷", "tags": ["arquitectura", "paseo", "gratis", "foto", "solo"]},
    {"title": "Montjuïc: jardins + castillo", "description": "Subida a pie o en teleférico, jardines botánicos y vistas al puerto.", "location": "Montjuïc", "price": "0€", "plan_type": "SOLO", "duration": "3h", "category": "Naturaleza", "city": "BARCELONA", "emoji": "🏰", "tags": ["naturaleza", "vistas", "gratis", "paseo", "solo"]},
    {"title": "Sónar de día", "description": "Entrada de día al Sónar. Música, arte digital y ambiente único.", "location": "Fira Gran Via", "price": "12€", "plan_type": "AMBOS", "duration": "4h", "availability": "Junio", "category": "Música", "city": "BARCELONA", "emoji": "🎧", "tags": ["musica", "festival", "arte", "pago"], "available_from": "2026-06-16", "available_until": "2026-06-21"},
    # Villena
    {"title": "Castillo de la Atalaya", "description": "Impresionante castillo medieval con vistas a todo el Valle. Visita guiada 3€.", "location": "Castillo", "price": "3€", "plan_type": "AMBOS", "duration": "1.5h", "category": "Cultura", "city": "VILLENA", "emoji": "🏰", "tags": ["castillo", "historia", "cultura", "barato"]},
    {"title": "Ruta senderismo Sierra de la Villa", "description": "Ruta circular de 6km por la sierra con vistas al castillo y al valle.", "location": "Sierra de la Villa", "price": "0€", "plan_type": "SOLO", "duration": "3h", "category": "Naturaleza", "city": "VILLENA", "emoji": "🥾", "tags": ["senderismo", "naturaleza", "gratis", "deporte", "solo"]},
    {"title": "Paseo casco antiguo + tapas", "description": "Calles empedradas, plazas con encanto y tapeo de calidad a precios de pueblo.", "location": "Casco antiguo", "price": "10€", "plan_type": "PAREJA", "duration": "2h", "category": "Gastronomía", "city": "VILLENA", "emoji": "🥘", "tags": ["comida", "paseo", "romantico", "cultura"]},
    {"title": "Street Food Market", "description": "Comida internacional, música en directo y artesanía. Entrada gratuita.", "location": "Recinto Ferial", "price": "0€", "plan_type": "AMBOS", "duration": "3h", "availability": "Eventos puntuales", "category": "Gastronomía", "city": "VILLENA", "emoji": "🍔", "tags": ["comida", "mercado", "gratis", "musica"]},
    {"title": "Ruta en bici por Las Virtudes", "description": "Ruta fácil en bici hasta el Santuario de Las Virtudes, rodeado de naturaleza.", "location": "Las Virtudes", "price": "0€", "plan_type": "SOLO", "duration": "2h", "category": "Deporte", "city": "VILLENA", "emoji": "🚴", "tags": ["deporte", "naturaleza", "gratis", "bici", "solo"]},
    {"title": "Mercado de diseño", "description": "Puestos de cerámica, ilustración y diseño local.", "location": "Recinto Ferial", "price": "0€", "plan_type": "PAREJA", "duration": "1h", "availability": "Fechas concretas", "category": "Compras", "city": "VILLENA", "emoji": "🎨", "tags": ["compras", "arte", "mercadillo", "gratis"]},
    {"title": "Día de piscina natural", "description": "Baño en el Pantano de Villena. Lleva nevera y sombrilla.", "location": "Pantano de Villena", "price": "0€", "plan_type": "SOLO", "duration": "Todo el día", "category": "Naturaleza", "city": "VILLENA", "emoji": "🏊", "tags": ["naturaleza", "gratis", "verano", "baño", "solo"]},
    {"title": "Teatro Chapí", "description": "Obra de teatro o cine de cartelera en el teatro histórico.", "location": "Teatro Chapí", "price": "5-8€", "plan_type": "PAREJA", "duration": "2h", "category": "Cultura", "city": "VILLENA", "emoji": "🎭", "tags": ["teatro", "cultura", "romantico", "barato"]},
    {"title": "Fiestas del Medievo", "description": "Mercado medieval, justas, música y animación callejera.", "location": "Centro histórico", "price": "0€", "plan_type": "AMBOS", "duration": "4h", "availability": "Septiembre", "category": "Cultura", "city": "VILLENA", "emoji": "⚔️", "tags": ["fiestas", "cultura", "gratis", "historia"], "available_from": "2026-09-04", "available_until": "2026-09-08"},
    {"title": "Cata de vinos local", "description": "Degustación de vinos de la DOP Alicante en bodegas familiares.", "location": "Bodega local", "price": "5-10€", "plan_type": "PAREJA", "duration": "1.5h", "category": "Gastronomía", "city": "VILLENA", "emoji": "🍷", "tags": ["comida", "vino", "romantico", "barato"]},
    # Alicante (nueva ciudad)
    {"title": "Castillo de Santa Bárbara", "description": "El castillo más grande de la zona, sube en ascensor gratis y disfruta las vistas al mar.", "location": "Monte Benacantil", "price": "0€", "plan_type": "AMBOS", "duration": "2h", "category": "Cultura", "city": "ALICANTE", "emoji": "🏰", "tags": ["castillo", "cultura", "gratis", "vistas"]},
    {"title": "Explanada de España + puerto", "description": "Paseo por el paseo de mosaicos, el puerto deportivo y helado artesanal.", "location": "Explanada", "price": "0€", "plan_type": "PAREJA", "duration": "1.5h", "category": "Ocio", "city": "ALICANTE", "emoji": "🌴", "tags": ["paseo", "gratis", "romantico", "mar"]},
    {"title": "Hogueras de San Juan", "description": "Monumentos de fuego, música y ambiente en la noche más mágica del año.", "location": "Centro ciudad", "price": "0€", "plan_type": "AMBOS", "duration": "4h", "category": "Cultura", "city": "ALICANTE", "emoji": "🔥", "tags": ["fiestas", "fuego", "cultura", "gratis"], "available_from": "2026-06-19", "available_until": "2026-06-25"},
    {"title": "Isla de Tabarca", "description": "Barco de ida y vuelta a la única isla habitada de la Comunitat. Playa y arroz.", "location": "Puerto de Alicante", "price": "15-20€", "plan_type": "PAREJA", "duration": "Todo el día", "category": "Naturaleza", "city": "ALICANTE", "emoji": "⛴️", "tags": ["isla", "playa", "barco", "romantico"], "available_from": "2026-06-01", "available_until": "2026-09-30"},
    {"title": "Mercado Central de Alicante", "description": "Mercado modernista con productos frescos: fruta, pescado y dulces típicos.", "location": "Av. Alfonso X", "price": "0€", "plan_type": "SOLO", "duration": "1h", "category": "Compras", "city": "ALICANTE", "emoji": "🍊", "tags": ["mercado", "comida", "gratis", "solo"]},
    {"title": "Playa del Postiguet", "description": "La playa urbana de Alicante, a 5 minutos del centro. Atardecer espectacular.", "location": "Postiguet", "price": "0€", "plan_type": "AMBOS", "duration": "3h", "category": "Naturaleza", "city": "ALICANTE", "emoji": "🏖️", "tags": ["playa", "gratis", "verano", "mar"]},
    {"title": "Museo Arqueológico MARQ", "description": "Una de las mejores colecciones arqueológicas de España. Entrada 3€.", "location": "Plaza Dr. Gómez Ulla", "price": "3€", "plan_type": "SOLO", "duration": "2h", "category": "Cultura", "city": "ALICANTE", "emoji": "🏺", "tags": ["museo", "cultura", "arqueologia", "barato"]},
    {"title": "Ruta por el Casco Antiguo (El Barrio)", "description": "Calles con escaleras, muralla y las mejores vistas al puerto al atardecer.", "location": "Casco Antiguo", "price": "0€", "plan_type": "PAREJA", "duration": "2h", "category": "Cultura", "city": "ALICANTE", "emoji": "🏘️", "tags": ["paseo", "vistas", "gratis", "romantico"]},
]


def _new_code() -> str:
    """Código de 6 caracteres legible (sin letras que se confunden)."""
    alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "".join(secrets.choice(alfabeto) for _ in range(6))


def _store_code(db: Session, user_id: int, purpose: str) -> str:
    """Guarda (hasheado) un código nuevo para el usuario y devuelve el claro."""
    db.query(AccountCode).filter(
        AccountCode.user_id == user_id,
        AccountCode.purpose == purpose,
        AccountCode.used == False,
    ).update({"used": True})
    codigo = _new_code()
    db.add(AccountCode(
        user_id=user_id,
        purpose=purpose,
        code_hash=hash_password(codigo),
        expires_at=datetime.now(timezone.utc) + timedelta(minutes=CODE_TTL_MINUTES),
    ))
    db.commit()
    return codigo


def _check_code(db: Session, user_id: int, purpose: str, code: str) -> bool:
    fila = (
        db.query(AccountCode)
        .filter(
            AccountCode.user_id == user_id,
            AccountCode.purpose == purpose,
            AccountCode.used == False,
        )
        .order_by(AccountCode.created_at.desc())
        .first()
    )
    if not fila:
        return False
    expira = fila.expires_at
    if expira.tzinfo is None:
        expira = expira.replace(tzinfo=timezone.utc)
    if expira < datetime.now(timezone.utc):
        return False
    if not verify_password(code.strip().upper(), fila.code_hash):
        return False
    fila.used = True
    db.commit()
    return True


def _send_code_email(email: str, code: str, purpose: str) -> bool:
    """Envía el código por email. Sin SMTP configurado devuelve False (modo pruebas)."""
    if not (SMTP_USER and SMTP_PASS):
        return False
    asunto = "PLAIN - código para recuperar tu contraseña" if purpose == "reset" else "PLAIN - verifica tu email"
    cuerpo = (
        f"Tu código es: {code}\n\n"
        f"Caduca en {CODE_TTL_MINUTES} minutos. Si no has sido tú, ignora este mensaje.\n\n"
        "— PLAIN"
    )
    try:
        import smtplib
        from email.message import EmailMessage

        msg = EmailMessage()
        msg["From"] = SMTP_USER
        msg["To"] = email
        msg["Subject"] = asunto
        msg.set_content(cuerpo)
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=12) as smtp:
            smtp.starttls()
            smtp.login(SMTP_USER, SMTP_PASS)
            smtp.send_message(msg)
        return True
    except Exception:
        return False


def ensure_schema() -> None:
    """Añade columnas nuevas a tablas ya existentes (create_all no las añade).

    Sin esto, en producción (base de datos que ya existe) las columnas nuevas
    no aparecerían y todo fallaría con «no such column».
    """
    columnas = [
        ("plans", "image_url", "VARCHAR(500)"),
        # OJO: en PostgreSQL "DEFAULT 0" para BOOLEAN es inválido (y el try/except
        # de abajo lo ocultaba, dejando la columna sin crear y rompiendo el login).
        ("users", "email_verified", "BOOLEAN DEFAULT FALSE"),
    ]
    with engine.connect() as conn:
        for tabla, columna, tipo in columnas:
            try:
                conn.exec_driver_sql(f"ALTER TABLE {tabla} ADD COLUMN {columna} {tipo}")
                print(f"🛠️  +{tabla}.{columna}")
            except Exception as e:
                if "duplicate column" in str(e).lower() or "already exists" in str(e).lower():
                    pass  # ya existe: correcto
                else:
                    print(f"⚠️ no pude añadir {tabla}.{columna}: {e}")
        conn.commit()


def seed_plans():
    """Insert seed plans if DB is empty."""
    db = SessionLocal()
    try:
        count = db.query(Plan).filter(Plan.is_default == True).count()
        if count == 0:
            for p in SEED_PLANS:
                data = dict(p)
                tags = data.pop("tags", [])   # copia: no mutar SEED_PLANS
                # parsear fechas a date objects (PostgreSQL exige Date, no str)
                if data.get("available_from"):
                    data["available_from"] = date.fromisoformat(str(data["available_from"]))
                if data.get("available_until"):
                    data["available_until"] = date.fromisoformat(str(data["available_until"]))
                plan = Plan(**data, tags=json.dumps(tags), is_default=True)
                db.add(plan)
            db.commit()
            print(f"✅ Seeded {len(SEED_PLANS)} default plans with tags")
        else:
            print(f"📦 {count} default plans already in DB")
    finally:
        db.close()


# === Routes ===


# --- Crash reports (fallos de la app) ---


@app.post("/api/crash-reports", status_code=201)
@limiter.limit("60/hour")
def report_crash(request: Request, data: CrashReportIn, db: Session = Depends(get_db)):
    """Recibe un fallo de la app.

    Sin autenticación a propósito: la app puede reventar antes de que el usuario
    inicie sesión, y en ese caso el informe es justo el que más interesa.
    """
    def rec(v, n):
        return (v or "")[:n] or None

    report = CrashReport(
        app_version=rec(data.app_version, 24),
        android_version=rec(data.android_version, 24),
        device=rec(data.device, 80),
        screen=rec(data.screen, 80),
        message=rec(data.message, 2000),
        stacktrace=rec(data.stacktrace, 8000),
        username=rec(data.username, 50),
    )
    db.add(report)
    db.commit()
    db.refresh(report)
    return {"status": "ok", "id": report.id}


@app.get("/api/crash-reports", response_model=list[CrashReportOut])
def list_crash_reports(
    limit: int = 50,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Últimos fallos registrados (requiere sesión), para depurar."""
    limit = max(1, min(int(limit or 50), 200))
    return (
        db.query(CrashReport)
        .order_by(CrashReport.created_at.desc())
        .limit(limit)
        .all()
    )


# --- Fotos de planes (búsqueda online, sin almacenamiento propio) ---


@app.get("/api/images/search", response_model=list[ImageSearchResult])
@limiter.limit("40/hour")
def search_plan_images(
    request: Request,
    q: str,
    user: User = Depends(get_current_user),
):
    """Busca fotos para un plan en Openverse (Creative Commons, sin API key).

    No guardamos ficheros: Render borra el disco en cada despliegue. Se devuelve
    la URL de la imagen elegida y el negocio/usuario decide cuál usar.
    """
    q = (q or "").strip()
    if len(q) < 3:
        raise HTTPException(status_code=400, detail="Escribe al menos 3 caracteres")

    try:
        with httpx.Client(timeout=12, headers={"User-Agent": "PLAIN/1.0 (app de planes)"}) as client:
            r = client.get(OPENVERSE_URL, params={
                "q": q,
                "page_size": 12,
                "license_type": "commercial",
                "mature": "false",
            })
        if r.status_code != 200:
            raise HTTPException(status_code=502, detail="El buscador de imágenes no responde")
        datos = r.json()
    except httpx.HTTPError:
        raise HTTPException(status_code=502, detail="El buscador de imágenes no responde")

    salida = []
    for item in datos.get("results", [])[:12]:
        url = item.get("url")
        if not url:
            continue
        salida.append(ImageSearchResult(
            title=(item.get("title") or "Sin título")[:120],
            url=url,
            thumb=item.get("thumbnail") or url,
            license=(item.get("license") or "") + (" " + item.get("license_version", "") if item.get("license_version") else ""),
            attribution=(item.get("creator") or item.get("source") or "")[:120],
        ))
    return salida


# ======================= 0.8.0: sin IA =======================
# --- Borrado de cuenta y datos (RGPD / Play Store) ---
def _borrar_datos_usuario(db: Session, uid: int) -> dict:
    """Borra todo lo que cuelga de un usuario. Busca la columna FK por introspeccion."""
    borrados = {}
    for modelo in (Favorite, DislikedTag, PlanEvent, WebhookConfig,
                   TripGroupMember, GroupMessage, CrashReport, PlanReport, AccountCode):
        cols = [c.name for c in modelo.__table__.columns]
        col = next((c for c in ("user_id", "owner_id", "reporter_id", "created_by") if c in cols), None)
        if col is None:
            continue
        try:
            n = db.query(modelo).filter(getattr(modelo, col) == uid).delete(synchronize_session=False)
            borrados[modelo.__name__] = n
        except Exception:
            db.rollback()
    return borrados


@app.post("/api/me/delete")
@limiter.limit("10/hour")
def delete_my_account(
    request: Request,
    data: AccountDeleteIn,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Borra la cuenta y todos sus datos asociados (derecho al olvido)."""
    if not verify_password(data.password, user.password_hash):
        raise HTTPException(status_code=403, detail="Contrasena incorrecta")
    uid = user.id
    borrados = _borrar_datos_usuario(db, uid)
    # grupos que creo el solo y webhooks propios
    for modelo, campo in ((TripGroup, "owner_id"), (TripGroup, "creator_id"), (TripGroup, "user_id")):
        cols = [c.name for c in modelo.__table__.columns]
        if campo in cols:
            try:
                db.query(modelo).filter(getattr(modelo, campo) == uid).delete(synchronize_session=False)
            except Exception:
                db.rollback()
    db.query(User).filter(User.id == uid).delete(synchronize_session=False)
    db.commit()
    return {"deleted": True, "user_id": uid, "detalle": borrados}


# --- Plan publico (para el enlace compartible) ---
@app.get("/api/plans/{plan_id}/public")
@limiter.limit("120/hour")
def public_plan(request: Request, plan_id: int, db: Session = Depends(get_db)):
    """Datos publicos de un plan, sin autenticacion y sin datos de usuarios."""
    plan = db.query(Plan).filter(Plan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan no encontrado")
    likes = 0
    try:
        likes = db.query(PlanEvent).filter(PlanEvent.plan_id == plan_id).count()
    except Exception:
        db.rollback()
    return {
        "id": plan.id,
        "title": getattr(plan, "title", None) or getattr(plan, "name", None),
        "description": getattr(plan, "description", None),
        "city": getattr(plan, "city", None),
        "price": getattr(plan, "price", None),
        "price_eur": getattr(plan, "price_eur", None),
        "image_url": getattr(plan, "image_url", None),
        "category": getattr(plan, "category", None),
        "availability": getattr(plan, "availability", None),
        "likes": likes,
    }


@app.get("/share/{plan_id}", response_class=HTMLResponse)
def share_page(plan_id: int):
    """Pagina publica que se comparte por WhatsApp/Telegram y abre la app."""
    f = os.path.join(web_dir, "plan.html")
    if not os.path.exists(f):
        raise HTTPException(status_code=404, detail="Pagina no disponible")
    return FileResponse(f)


# --- Planes cerca de mi (sin mapa) ---
@app.get("/api/plans/nearby")
@limiter.limit("60/hour")
def plans_nearby(
    request: Request,
    lat: float,
    lng: float,
    radius_km: int = 100,
    limit: int = 20,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Planes ordenados por distancia real a la posicion del usuario (sin mapa).

    geo.cities_in_radius devuelve {CIUDAD: (lat, lng, km)}.
    """
    from geo import cities_in_radius
    cercanas = cities_in_radius(lat, lng, radius_km)
    por_ciudad = {str(k).upper(): v for k, v in (cercanas or {}).items()}
    if not por_ciudad:
        return {"items": [], "cities": []}

    def _km(v):
        if isinstance(v, (tuple, list)) and len(v) >= 3:
            return float(v[2])
        try:
            return float(v)
        except Exception:
            return None

    planes = db.query(Plan).filter(Plan.city.in_(list(por_ciudad.keys()))).all()
    salida = []
    for p in planes:
        c = (getattr(p, "city", None) or "").upper()
        km = _km(por_ciudad.get(c))
        if km is None:
            continue
        salida.append({
            "id": p.id,
            "title": getattr(p, "title", None),
            "city": c,
            "price": getattr(p, "price", None),
            "image_url": getattr(p, "image_url", None),
            "distance_km": round(km, 1),
        })
    salida.sort(key=lambda x: x["distance_km"])
    ciudades = sorted(
        [[k, round(_km(v), 1)] for k, v in por_ciudad.items() if _km(v) is not None],
        key=lambda x: x[1],
    )
    return {"items": salida[:limit], "cities": ciudades}


# ======================= fin 0.8.0 =======================


# ======================= 0.8.1: recomendacion + chat IA =======================
MODELO_CHAT = os.environ.get("PLAIN_CHAT_MODEL", "deepseek/deepseek-v4-flash")


def _categoria_de(plan):
    return (getattr(plan, "category", None) or "").strip().lower()


def _perfil_gustos(db: Session, uid: int):
    """Devuelve (categorias_que_le_gustan, categorias_que_no, planes_que_ya_vio)."""
    gustan, disgustan, vistos = {}, {}, set()
    try:
        for f in db.query(Favorite).filter(Favorite.user_id == uid).all():
            vistos.add(getattr(f, "plan_id", None))
        for f in db.query(Favorite).filter(Favorite.user_id == uid).all():
            p = db.query(Plan).filter(Plan.id == getattr(f, "plan_id", None)).first()
            if p:
                gustan[_categoria_de(p)] = gustan.get(_categoria_de(p), 0) + 1
    except Exception:
        db.rollback()
    try:
        for d in db.query(DislikedTag).filter(DislikedTag.user_id == uid).all():
            t = (getattr(d, "tag", None) or getattr(d, "name", None) or "").strip().lower()
            if t:
                disgustan[t] = disgustan.get(t, 0) + 1
    except Exception:
        db.rollback()
    return gustan, disgustan, vistos


@app.get("/api/recommendations")
@limiter.limit("120/hour")
def recommendations(
    request: Request,
    city: Optional[str] = None,
    limit: int = 20,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Planes ordenados por afinidad con lo que le ha gustado al usuario.

    Puntuacion explicita y sin caja negra:
      +3 por cada like en esa categoria   |  -6 si la categoria esta entre sus 'no'
      +2 si es de su ciudad               |  + popularidad global (hasta +3)
      +1 si nunca lo ha visto             |  -4 si ya le dio like (no repetir)
    """
    gustan, disgustan, vistos = _perfil_gustos(db, user.id)
    q = db.query(Plan)
    if city:
        q = q.filter(Plan.city == city.strip().upper())
    planes = q.all()

    populares = {}
    try:
        from sqlalchemy import func as _f
        for pid, n in db.query(PlanEvent.plan_id, _f.count(PlanEvent.id)).group_by(PlanEvent.plan_id).all():
            populares[pid] = n
    except Exception:
        db.rollback()
    tope = max(populares.values()) if populares else 0

    filas = []
    for p in planes:
        cat = _categoria_de(p)
        pts = 0.0
        motivos = []
        if cat and gustan.get(cat):
            pts += 3 * gustan[cat]
            motivos.append("te gustan los planes de " + cat)
        if cat and disgustan.get(cat):
            pts -= 6 * disgustan[cat]
        if city and (getattr(p, "city", "") or "").upper() == city.strip().upper():
            pts += 2
        if tope and populares.get(p.id):
            pts += 3 * (populares[p.id] / tope)
            if populares[p.id] >= max(3, tope * 0.5):
                motivos.append("es de los mas populares")
        if p.id not in vistos:
            pts += 1
        else:
            pts -= 4
        filas.append({
            "id": p.id,
            "title": getattr(p, "title", None),
            "city": getattr(p, "city", None),
            "category": getattr(p, "category", None),
            "price": getattr(p, "price", None),
            "image_url": getattr(p, "image_url", None),
            "score": round(pts, 2),
            "reason": (", ".join(motivos) if motivos else "encaja con lo que sueles elegir"),
        })
    filas.sort(key=lambda x: -x["score"])
    return {"items": filas[:limit], "basado_en": {"likes_por_categoria": gustan, "no_quiere": sorted(disgustan)}}


# ---- Chat: entiende la frase -> filtros -> BD -> respuesta ----
def _filtros_por_reglas(texto: str, cities):
    """Respaldo sin IA: busca ciudad y palabras clave en la frase."""
    t = (texto or "").lower()
    filtros = {"city": None, "max_price": None, "category": None, "keywords": []}
    for c in cities:
        if c.lower() in t:
            filtros["city"] = c
            break
    for k, cats in (("barato", None), ("gratis", None), ("comer", "gastronomia"), ("tapas", "gastronomia"),
                    ("tranquil", None), ("naturaleza", "naturaleza"), ("sender", "naturaleza"),
                    ("cultura", "cultura"), ("museo", "cultura"), ("noche", "ocio"), ("fiesta", "ocio")):
        if k in t:
            filtros["keywords"].append(k)
            if cats and not filtros["category"]:
                filtros["category"] = cats
    if "gratis" in t:
        filtros["max_price"] = 0
    elif "barato" in t:
        filtros["max_price"] = 15
    return filtros


def _filtros_con_ia(texto: str, cities, categorias):
    """El modelo SOLO convierte lenguaje natural en filtros. No propone planes."""
    key = os.environ.get("OPENROUTER_API_KEY", "").strip()
    if not key:
        return None
    prompt = (
        "Eres un extractor de filtros para una app de planes. Devuelve SOLO un JSON valido, sin texto extra:\n"
        '{"city": <una de la lista o null>, "max_price": <numero o null>, "category": <una de la lista o null>, '
        '"keywords": [<palabras clave>], "momento": <"manana"|"tarde"|"noche"|null>}\n'
        "Ciudades: " + ", ".join(cities) + "\n"
        "Categorias: " + ", ".join(categorias) + "\n"
        'Frase del usuario: "' + texto[:300] + '"'
    )
    try:
        r = httpx.post(
            "https://openrouter.ai/api/v1/chat/completions",
            headers={"Authorization": "Bearer " + key, "Content-Type": "application/json"},
            json={"model": MODELO_CHAT, "messages": [{"role": "user", "content": prompt}],
                  "temperature": 0, "max_tokens": 200},
            timeout=30.0,
        )
        r.raise_for_status()
        contenido = r.json()["choices"][0]["message"]["content"].strip()
        contenido = contenido[contenido.find("{"): contenido.rfind("}") + 1]
        return json.loads(contenido)
    except Exception as e:
        print("ask: fallo el modelo, uso reglas:", str(e)[:120])
        return None


@app.post("/api/ask")
@limiter.limit("30/hour")
def ask_ai(
    request: Request,
    data: dict,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Pregunta en lenguaje natural. Devuelve una respuesta y planes REALES de la BD."""
    texto = (data.get("text") or "").strip()
    if not texto:
        raise HTTPException(status_code=400, detail="Escribe que te apetece")
    city_hint = (data.get("city") or "").strip().upper() or None

    cities = sorted({(p.city or "").upper() for p in db.query(Plan).all() if p.city})
    categorias = sorted({_categoria_de(p) for p in db.query(Plan).all() if _categoria_de(p)})

    filtros = _filtros_con_ia(texto, cities, categorias) or _filtros_por_reglas(texto, cities)
    ciudad = (filtros.get("city") or city_hint or "").upper() or None

    q = db.query(Plan)
    if ciudad:
        q = q.filter(Plan.city == ciudad)
    planes = q.all()

    def _precio(p):
        v = getattr(p, "price", None)
        if v is None:
            return None
        if isinstance(v, (int, float)):
            return float(v)
        try:
            return float(str(v).replace("€", "").replace(",", ".").strip())
        except Exception:
            return None

    maxp = filtros.get("max_price")
    if isinstance(maxp, (int, float)):
        planes = [p for p in planes if (_precio(p) is None or _precio(p) <= maxp)]
    cat = (filtros.get("category") or "").strip().lower()
    if cat:
        pref = [p for p in planes if _categoria_de(p) == cat]
        planes = pref or planes
    kws = [k for k in (filtros.get("keywords") or []) if isinstance(k, str)]
    if kws:
        def _puntua(p):
            blob = " ".join(str(getattr(p, a, "") or "").lower() for a in ("title", "description", "category"))
            return sum(1 for k in kws if k.lower() in blob)
        orden = sorted(planes, key=_puntua, reverse=True)
        con = [p for p in orden if _puntua(p) > 0]
        planes = con or orden

    gustan, disgustan, vistos = _perfil_gustos(db, user.id)
    planes.sort(key=lambda p: (-(gustan.get(_categoria_de(p), 0) * 3) + (6 if _categoria_de(p) in disgustan else 0)))
    top = planes[:6]

    if not top:
        respuesta = "No encuentro planes que encajen con eso" + (" en " + ciudad.title() if ciudad else "") + ". Prueba a quitar algun filtro."
    elif filtros.get("city"):
        respuesta = "Estos son los que mejor encajan con lo que pides en " + str(ciudad).title() + ":"
    else:
        respuesta = "Esto es lo que mejor encaja con lo que pides:"

    return {
        "answer": respuesta,
        "filters": filtros,
        "used_ai": bool(os.environ.get("OPENROUTER_API_KEY", "").strip()),
        "plans": [{
            "id": p.id,
            "title": getattr(p, "title", None),
            "description": getattr(p, "description", None),
            "city": getattr(p, "city", None),
            "category": getattr(p, "category", None),
            "price": getattr(p, "price", None),
            "image_url": getattr(p, "image_url", None),
        } for p in top],
    }


# ======================= fin 0.8.1 =======================


@app.put("/api/me")
def update_profile(
    data: ProfileUpdateIn,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Edita el perfil (email o nombre de usuario)."""
    if data.email and data.email != user.email:
        nuevo = data.email.strip().lower()
        if db.query(User).filter(User.email == nuevo, User.id != user.id).first():
            raise HTTPException(status_code=400, detail="Ese email ya está en uso")
        user.email = nuevo
        user.email_verified = False          # al cambiarlo hay que volver a verificar
    if data.username and data.username != user.username:
        nuevo = data.username.strip()
        if len(nuevo) < 3:
            raise HTTPException(status_code=400, detail="El nombre debe tener al menos 3 caracteres")
        if db.query(User).filter(User.username == nuevo, User.id != user.id).first():
            raise HTTPException(status_code=400, detail="Ese nombre ya está en uso")
        user.username = nuevo
    db.commit()
    db.refresh(user)
    return {
        "id": user.id,
        "username": user.username,
        "email": user.email,
        "email_verified": bool(getattr(user, "email_verified", False)),
        "created_at": user.created_at,
    }


@app.get("/api/me/status")
def my_status(user: User = Depends(get_current_user)):
    """Estado de la cuenta (si la verificación de email está activa, informa)."""
    return {
        "email_verified": bool(getattr(user, "email_verified", False)),
        "requires_verification": REQUIRE_EMAIL_VERIFICATION,
        "max_plans": MAX_PLANS_PER_USER,
    }


# --- Recuperar contraseña / verificar email ---


@app.post("/api/password/forgot")
@limiter.limit("10/hour")
def forgot_password(request: Request, data: ForgotPasswordIn, db: Session = Depends(get_db)):
    """Genera un código de recuperación y lo manda por email si hay SMTP.

    Sin SMTP configurado devuelve el código en la respuesta (modo pruebas) y avisa.
    """
    email = (data.email or "").strip().lower()
    user = db.query(User).filter(User.email == email).first()
    # Respuesta idéntica exista o no el email (no revelamos qué correos están dados de alta)
    if not user:
        return {"status": "ok", "sent": False}

    codigo = _store_code(db, user.id, "reset")
    enviado = _send_code_email(user.email, codigo, "reset")
    respuesta = {"status": "ok", "sent": enviado}
    if not enviado:
        respuesta["dev_code"] = codigo
        respuesta["warning"] = "SMTP no configurado: el código se devuelve aquí (solo para pruebas)"
    return respuesta


@app.post("/api/password/reset")
@limiter.limit("20/hour")
def reset_password(request: Request, data: ResetPasswordIn, db: Session = Depends(get_db)):
    email = (data.email or "").strip().lower()
    user = db.query(User).filter(User.email == email).first()
    if not user or not _check_code(db, user.id, "reset", data.code):
        raise HTTPException(status_code=400, detail="Código incorrecto o caducado")
    if len(data.new_password or "") < 8:
        raise HTTPException(status_code=400, detail="La contraseña debe tener al menos 8 caracteres")
    user.password_hash = hash_password(data.new_password)
    db.commit()
    return {"status": "ok"}


@app.post("/api/email/verify/request")
@limiter.limit("10/hour")
def request_email_verification(request: Request, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    codigo = _store_code(db, user.id, "verify_email")
    enviado = _send_code_email(user.email, codigo, "verify_email")
    respuesta = {"status": "ok", "sent": enviado}
    if not enviado:
        respuesta["dev_code"] = codigo
        respuesta["warning"] = "SMTP no configurado: el código se devuelve aquí (solo para pruebas)"
    return respuesta


@app.post("/api/email/verify")
def verify_email(data: VerifyEmailIn, db: Session = Depends(get_db)):
    email = (data.email or "").strip().lower()
    user = db.query(User).filter(User.email == email).first()
    if not user or not _check_code(db, user.id, "verify_email", data.code):
        raise HTTPException(status_code=400, detail="Código incorrecto o caducado")
    user.email_verified = True
    db.commit()
    return {"status": "ok", "email_verified": True}


# --- Moderación ---


@app.post("/api/plans/{plan_id}/report", status_code=201)
@limiter.limit("20/hour")
def report_plan(request: Request, plan_id: int, data: PlanReportIn,
                db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    """Reporta un plan (moderación mínima)."""
    plan = db.query(Plan).filter(Plan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan no encontrado")
    ya = db.query(PlanReport).filter(PlanReport.plan_id == plan_id, PlanReport.user_id == user.id).first()
    if ya:
        return {"status": "already_reported"}
    db.add(PlanReport(
        plan_id=plan_id,
        user_id=user.id,
        reason=(data.reason or "otro")[:60],
        comment=(data.comment or "")[:500] or None,
    ))
    db.commit()
    return {"status": "ok"}


@app.get("/api/reports")
def list_reports(limit: int = 50, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    """Reportes recibidos, para revisarlos (requiere sesión)."""
    limit = max(1, min(int(limit or 50), 200))
    filas = db.query(PlanReport).order_by(PlanReport.created_at.desc()).limit(limit).all()
    return [
        {"id": f.id, "plan_id": f.plan_id, "reason": f.reason,
         "comment": f.comment, "created_at": f.created_at}
        for f in filas
    ]


# --- Analítica de producto ---


@app.post("/api/events", status_code=202)
@limiter.limit("200/hour")
def track_events(request: Request, data: EventsIn, db: Session = Depends(get_db),
                 user: User = Depends(get_current_user)):
    """Guarda eventos de la app (visto, me gusta, descartado, abierto).

    Se envían en lote para no castigar la batería ni la red del móvil.
    """
    validos = {"view", "like", "dislike", "open", "favorite"}
    guardados = 0
    for ev in (data.events or [])[:100]:
        if ev.event not in validos:
            continue
        db.add(PlanEvent(
            plan_id=ev.plan_id,
            user_id=user.id,
            city=(ev.city or "")[:50] or None,
            event=ev.event,
        ))
        guardados += 1
    db.commit()
    return {"status": "ok", "stored": guardados}


@app.get("/api/stats/product")
def product_stats(days: int = 30, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    """Resumen de producto: qué se ve, qué gusta y qué se descarta."""
    days = max(1, min(int(days or 30), 365))
    desde = datetime.now(timezone.utc) - timedelta(days=days)
    filas = db.query(PlanEvent).filter(PlanEvent.created_at >= desde).all()
    por_evento: dict[str, int] = {}
    por_ciudad: dict[str, dict[str, int]] = {}
    for f in filas:
        por_evento[f.event] = por_evento.get(f.event, 0) + 1
        if f.city:
            por_ciudad.setdefault(f.city, {})
            por_ciudad[f.city][f.event] = por_ciudad[f.city].get(f.event, 0) + 1
    likes = por_evento.get("like", 0)
    descartes = por_evento.get("dislike", 0)
    total = likes + descartes
    return {
        "desde": desde.isoformat(),
        "dias": days,
        "eventos": por_evento,
        "por_ciudad": por_ciudad,
        "tasa_like": round(likes / total, 3) if total else None,
    }


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
    if REQUIRE_EMAIL_VERIFICATION and not getattr(user, "email_verified", False):
        raise HTTPException(
            status_code=403,
            detail="Verifica tu email antes de entrar (revisa tu correo o pide otro código)",
        )
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
        image_url=data.image_url,
        business_id=business.id,
        is_sponsored=True,
        budget_cents=data.budget_cents,
        cost_per_like_cents=data.cost_per_like_cents,
        is_active=True,
        available_from=data.available_from,
        available_until=data.available_until,
        recurring=data.recurring,
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
    for p in plans:
        p.is_available_now = plan_is_available(p)
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


def plan_is_available(p: Plan) -> bool:
    """Comprueba disponibilidad por fechas: ventana + días recurrentes."""
    if not p.is_active:
        return False
    today = date.today()
    if p.available_from and today < p.available_from:
        return False
    if p.available_until and today > p.available_until:
        return False
    if p.recurring:
        # Mapa fijo (locale-independiente) de weekday() -> código
        day_map = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]
        today_code = day_map[today.weekday()]
        days = [d.strip().upper() for d in p.recurring.split(",") if d.strip()]
        if days and today_code not in days:
            return False
    return True


@app.get("/api/plans", response_model=list[PlanResponse])
def list_plans(
    city: Optional[str] = None,
    radius_km: int = 0,
    limit: int = 200,
    offset: int = 0,
    plan_type: Optional[str] = None,
    category: Optional[str] = None,
    only_available: bool = False,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """List plans: free + active sponsored, sorted with user's disliked tags last.

    `radius_km` > 0 amplía el resultado a las ciudades dentro de ese radio
    (para ver planes de alrededor sin cambiar de ciudad); cada plan devuelve
    `distance_km`. La ciudad del usuario siempre se incluye.
    """
    radius_km = max(0, min(int(radius_km or 0), 2000))

    query = db.query(Plan).filter(
        (Plan.is_default == True) | (Plan.created_by != None)  # Free seed + user-created
    )
    sponsored_query = db.query(Plan).filter(
        Plan.is_sponsored == True,
        Plan.is_active == True,
    )

    if plan_type:
        query = query.filter(Plan.plan_type == plan_type.upper())
        sponsored_query = sponsored_query.filter(Plan.plan_type == plan_type.upper())
    if category:
        query = query.filter(Plan.category == category)
        sponsored_query = sponsored_query.filter(Plan.category == category)

    distances: dict[int, float] = {}

    if city and radius_km > 0:
        # Resolver la ciudad (desempatando homónimos por el país donde ya hay planes)
        plan_cities = tuple(c for (c,) in db.query(Plan.city).distinct().all() if c)
        hint = geo.country_hint(plan_cities)
        base = geo.resolve(city, hint)

        if base is None:
            # Ciudad fuera del índice (p. ej. Tamraght): match exacto, como sin radio
            city_up = city.strip().upper()
            query = query.filter(func.upper(Plan.city) == city_up)
            sponsored_query = sponsored_query.filter(func.upper(Plan.city) == city_up)
            all_plans = list(query.all()) + list(sponsored_query.all())
        else:
            lat, lng, _cc = base
            near = geo.cities_in_radius(lat, lng, radius_km)
            base_key = geo.norm(city)

            def _distance(plan_city: str) -> Optional[float]:
                key = geo.norm(plan_city)
                if key == base_key:
                    return 0.0
                hit = near.get(key)
                return None if hit is None else round(hit[2], 1)

            free_all = query.all()
            spons_all = sponsored_query.all()
            free_all = [p for p in free_all if _distance(p.city) is not None]
            spons_all = [p for p in spons_all if _distance(p.city) is not None]
            for p in list(free_all) + list(spons_all):
                distances[p.id] = _distance(p.city)
            all_plans = sorted(free_all, key=lambda p: distances[p.id]) + sorted(spons_all, key=lambda p: distances[p.id])
    else:
        if city:
            city_up = city.strip().upper()
            query = query.filter(func.upper(Plan.city) == city_up)
            sponsored_query = sponsored_query.filter(func.upper(Plan.city) == city_up)
        all_plans = list(query.all()) + list(sponsored_query.all())

    # Date availability filter
    if only_available:
        all_plans = [p for p in all_plans if plan_is_available(p)]

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

    # Paginación: por defecto 200 (la app sigue pidiendo una sola página)
    limit = max(1, min(int(limit or 200), 500))
    offset = max(0, int(offset or 0))
    all_plans = all_plans[offset:offset + limit]

    # Annotate availability + distance
    for p in all_plans:
        p.is_available_now = plan_is_available(p)
        p.distance_km = distances.get(p.id)

    return all_plans


@app.post("/api/plans", response_model=PlanResponse)
def create_plan(
    data: PlanCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Crea un plan. Limitado por usuario para evitar que una cuenta inunde la ciudad."""
    creados_por_usuario = db.query(Plan).filter(Plan.created_by == user.id).count()
    if creados_por_usuario >= MAX_PLANS_PER_USER:
        raise HTTPException(
            status_code=429,
            detail=f"Has alcanzado el límite de {MAX_PLANS_PER_USER} planes. Elimina alguno para crear más.",
        )
    plan = Plan(
        **data.model_dump(exclude={"tags", "image_url"}),
        tags=json.dumps(data.tags),
        image_url=data.image_url,
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

    # If sponsored: refund the like cost (so like/unlike cycles don't drain budget)
    plan = db.query(Plan).filter(Plan.id == plan_id).first()
    if plan and plan.is_sponsored:
        plan.spent_cents = max(0, (plan.spent_cents or 0) - plan.cost_per_like_cents)
        if plan.spent_cents < plan.budget_cents:
            plan.is_active = True

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


# === Trip groups (BlaBlaCar-style quedadas) ===

@app.get("/api/plans/{plan_id}/groups", response_model=list[TripGroupResponse])
def list_plan_groups(
    plan_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Lista los grupos (quedadas) de un plan."""
    plan = db.query(Plan).filter(Plan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan no encontrado")
    groups = db.query(TripGroup).filter(TripGroup.plan_id == plan_id).all()
    result = []
    for g in groups:
        resp = TripGroupResponse(
            id=g.id, plan_id=g.plan_id, plan_title=plan.title,
            owner_id=g.owner_id, owner_username=g.owner.username if g.owner else "",
            title=g.title, meeting_point=g.meeting_point, meet_at=g.meet_at,
            seats=g.seats, transport=g.transport, notes=g.notes, created_at=g.created_at,
        )
        resp.members = [TripGroupMemberOut(user_id=m.user_id, username=m.user.username if m.user else "", joined_at=m.joined_at) for m in g.members]
        resp.seats_taken = len(g.members)
        result.append(resp)
    return result


@app.post("/api/plans/{plan_id}/groups", response_model=TripGroupResponse)
def create_plan_group(
    plan_id: int,
    data: TripGroupCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Crea una quedada (grupo) para ir a un plan, tipo BlaBlaCar."""
    plan = db.query(Plan).filter(Plan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan no encontrado")
    group = TripGroup(
        plan_id=plan_id, owner_id=user.id, title=data.title,
        meeting_point=data.meeting_point, meet_at=data.meet_at,
        seats=max(1, data.seats), transport=data.transport, notes=data.notes,
    )
    db.add(group)
    db.flush()
    # El creador es miembro automáticamente
    member = TripGroupMember(group_id=group.id, user_id=user.id)
    db.add(member)
    db.commit()
    db.refresh(group)
    resp = TripGroupResponse(
        id=group.id, plan_id=group.plan_id, plan_title=plan.title,
        owner_id=group.owner_id, owner_username=user.username,
        title=group.title, meeting_point=group.meeting_point, meet_at=group.meet_at,
        seats=group.seats, transport=group.transport, notes=group.notes, created_at=group.created_at,
    )
    resp.members = [TripGroupMemberOut(user_id=user.id, username=user.username, joined_at=member.joined_at)]
    resp.seats_taken = 1
    return resp


@app.post("/api/groups/{group_id}/join", response_model=TripGroupResponse)
def join_group(
    group_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Apúntate a una quedada si quedan plazas."""
    group = db.query(TripGroup).filter(TripGroup.id == group_id).first()
    if not group:
        raise HTTPException(status_code=404, detail="Grupo no encontrado")
    exists = db.query(TripGroupMember).filter(
        TripGroupMember.group_id == group_id, TripGroupMember.user_id == user.id
    ).first()
    if exists:
        raise HTTPException(status_code=400, detail="Ya estás en este grupo")
    if len(group.members) >= group.seats:
        raise HTTPException(status_code=400, detail="Grupo completo")
    member = TripGroupMember(group_id=group_id, user_id=user.id)
    db.add(member)
    db.commit()
    db.refresh(group)
    plan = db.query(Plan).filter(Plan.id == group.plan_id).first()
    resp = TripGroupResponse(
        id=group.id, plan_id=group.plan_id, plan_title=plan.title if plan else "",
        owner_id=group.owner_id, owner_username=group.owner.username if group.owner else "",
        title=group.title, meeting_point=group.meeting_point, meet_at=group.meet_at,
        seats=group.seats, transport=group.transport, notes=group.notes, created_at=group.created_at,
    )
    resp.members = [TripGroupMemberOut(user_id=m.user_id, username=m.user.username if m.user else "", joined_at=m.joined_at) for m in group.members]
    resp.seats_taken = len(group.members)
    return resp


@app.delete("/api/groups/{group_id}/leave")
def leave_group(
    group_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Sal de una quedada."""
    member = db.query(TripGroupMember).filter(
        TripGroupMember.group_id == group_id, TripGroupMember.user_id == user.id
    ).first()
    if not member:
        raise HTTPException(status_code=404, detail="No estás en este grupo")
    db.delete(member)
    db.commit()
    return {"status": "ok"}


# --- CHAT DE QUEDADAS ---

def _require_membership(db: Session, group_id: int, user: User) -> TripGroup:
    """Comprueba que el grupo existe y el usuario es miembro (o dueño)."""
    group = db.query(TripGroup).filter(TripGroup.id == group_id).first()
    if not group:
        raise HTTPException(status_code=404, detail="Quedada no encontrada")
    is_member = db.query(TripGroupMember).filter(
        TripGroupMember.group_id == group_id, TripGroupMember.user_id == user.id
    ).first() is not None
    if not is_member and group.owner_id != user.id:
        raise HTTPException(status_code=403, detail="Únete a la quedada para ver el chat")
    return group


@app.get("/api/groups/{group_id}/messages", response_model=list[GroupMessageOut])
def get_group_messages(
    group_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Mensajes de la quedada (solo miembros)."""
    _require_membership(db, group_id, user)
    rows = (
        db.query(GroupMessage, User.username)
        .join(User, GroupMessage.user_id == User.id)
        .filter(GroupMessage.group_id == group_id)
        .order_by(GroupMessage.created_at.asc())
        .limit(200)
        .all()
    )
    return [
        GroupMessageOut(
            id=m.id, group_id=m.group_id, user_id=m.user_id,
            username=uname, text=m.text, created_at=m.created_at,
        )
        for m, uname in rows
    ]


@app.post("/api/groups/{group_id}/messages", response_model=GroupMessageOut)
def post_group_message(
    group_id: int,
    data: GroupMessageCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Envía un mensaje a la quedada (solo miembros)."""
    _require_membership(db, group_id, user)
    msg = GroupMessage(group_id=group_id, user_id=user.id, text=data.text.strip())
    db.add(msg)
    db.commit()
    db.refresh(msg)
    return GroupMessageOut(
        id=msg.id, group_id=msg.group_id, user_id=msg.user_id,
        username=user.username, text=msg.text, created_at=msg.created_at,
    )


# --- CIUDADES DINÁMICAS (bootstrap) ---

# Plantillas genéricas por categoría: se usan cuando alguien pide planes
# de una ciudad que no está en el seed. Se personalizan con el nombre de la ciudad.
CITY_PLAN_TEMPLATES = [
    {"title": "Café con encanto en {city}", "description": "Un café tranquilo para charlar con buena compañía y algo dulce.", "category": "Gastronomía", "plan_type": "AMBOS", "duration": "1h 30min", "price": "3-6€", "emoji": "☕", "tags": ["cafe", "charla", "barato"]},
    {"title": "Paseo por el centro de {city}", "description": "Caminar sin prisa por las calles del casco histórico, viendo escaparates y plazas.", "category": "Ocio", "plan_type": "AMBOS", "duration": "2h", "price": "0€", "emoji": "🚶", "tags": ["paseo", "gratis", "casco"]},
    {"title": "Mirador de {city} al atardecer", "description": "El mejor punto para ver la puesta de sol y sacar fotos. Lleva chaqueta.", "category": "Naturaleza", "plan_type": "PAREJA", "duration": "1h", "price": "0€", "emoji": "🌅", "tags": ["atardecer", "vistas", "gratis", "romantico"]},
    {"title": "Museo principal de {city}", "description": "La colección más interesante de la ciudad. Entrada económica y visita guiada opcional.", "category": "Cultura", "plan_type": "SOLO", "duration": "2h", "price": "3-8€", "emoji": "🏛️", "tags": ["museo", "cultura", "barato"]},
    {"title": "Mercado local de {city}", "description": "Productos frescos, ambiente de barrio y buena comida al paso.", "category": "Compras", "plan_type": "AMBOS", "duration": "1h", "price": "5-15€", "emoji": "🧺", "tags": ["mercado", "comida", "barrio"]},
    {"title": "Cervecita y tapas en {city}", "description": "Ruta corta de bares con tapas. Ideal para hacer nuevos amigos.", "category": "Gastronomía", "plan_type": "AMBOS", "duration": "3h", "price": "10-20€", "emoji": "🍺", "tags": ["tapas", "cerveza", "social"]},
    {"title": "Parque principal de {city}", "description": "Zona verde para correr, leer o simplemente tumbarse al sol.", "category": "Deporte", "plan_type": "AMBOS", "duration": "1h", "price": "0€", "emoji": "🌳", "tags": ["parque", "deporte", "gratis", "aire libre"]},
    {"title": "Cine o teatro en {city}", "description": "Sesión de tarde con la cartelera local. Buena opción para días de lluvia.", "category": "Cultura", "plan_type": "PAREJA", "duration": "2h 30min", "price": "6-12€", "emoji": "🎬", "tags": ["cine", "teatro", "cultura", "lluvia"]},
    {"title": "Quedada para conocer gente en {city}", "description": "Encuentro informal de gente nueva para tomar algo y hacer plan juntos.", "category": "Ocio", "plan_type": "AMBOS", "duration": "2h", "price": "5-10€", "emoji": "👋", "tags": ["social", "conocer gente", "amigos"]},
    {"title": "Excursión cercana a {city}", "description": "Ruta corta de senderismo a un mirador o pueblo cercano. Se sale por la mañana.", "category": "Naturaleza", "plan_type": "AMBOS", "duration": "Todo el día", "price": "5-10€", "emoji": "🥾", "tags": ["senderismo", "excursion", "naturaleza"]},
]


@app.post("/api/cities/{city}/bootstrap", response_model=BootstrapResult)
def bootstrap_city(
    city: str,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    """Si una ciudad no tiene planes, genera planes locales al instante.

    Idempotente: si la ciudad YA tiene planes, no crea nada (devuelve
    created=0) para no duplicar contenido.
    """
    city_norm = city.strip().upper()
    if len(city_norm) < 2 or len(city_norm) > 60:
        raise HTTPException(status_code=400, detail="Ciudad no válida")

    existing = db.query(Plan).filter(
        func.upper(Plan.city) == city_norm, Plan.is_active == True
    ).count()
    if existing > 0:
        return BootstrapResult(city=city_norm, created=0, plans=[])

    pretty = city.strip().title()
    created_plans = []
    for tmpl in CITY_PLAN_TEMPLATES:
        p = dict(tmpl)
        p["title"] = p["title"].format(city=pretty)
        p["description"] = p["description"].format(city=pretty)
        p["location"] = f"{pretty} (centro)"
        p["city"] = city_norm
        tags = p.pop("tags", [])
        plan = Plan(
            **p,
            tags=json.dumps(tags),
            is_default=False,
            created_by=user.id,
        )
        db.add(plan)
        created_plans.append(p)
    db.commit()
    return BootstrapResult(city=city_norm, created=len(created_plans), plans=created_plans)


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
