from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase
import os

# Railway proporciona DATABASE_URL (PostgreSQL) automáticamente
# Local usa SQLite por defecto
DATABASE_URL = os.environ.get("DATABASE_URL", "sqlite:///./plain.db")

# Si es PostgreSQL, no necesita check_same_thread
connect_args = {}
if DATABASE_URL.startswith("sqlite"):
    connect_args["check_same_thread"] = False
else:
    # Render/PostgreSQL exige SSL
    if "sslmode" not in DATABASE_URL:
        sep = "&" if "?" in DATABASE_URL else "?"
        DATABASE_URL = f"{DATABASE_URL}{sep}sslmode=require"

engine = create_engine(
    DATABASE_URL,
    connect_args=connect_args,
    # Neon (Postgres serverless) cierra las conexiones SSL inactivas. Sin
    # pool_pre_ping, el pool reutiliza una conexión muerta y la primera consulta
    # tras un rato en reposo da "SSL connection has been closed unexpectedly"
    # (500 en registro/login). pre_ping la detecta y reconecta; recycle la
    # renueva antes de que Neon la tumbe por inactividad.
    pool_pre_ping=True,
    pool_recycle=300,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
