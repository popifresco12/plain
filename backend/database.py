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

engine = create_engine(DATABASE_URL, connect_args=connect_args)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
