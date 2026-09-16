"""Configuración común de los tests del backend.

Antes cada archivo de test definía su propia base de datos y su propio
`dependency_overrides[get_db]`. Como pytest importa TODOS los módulos antes de
ejecutarlos, el último override ganaba y los tests de los demás archivos corrían
contra una BD vacía (404/401 sin sentido). Aquí hay una sola BD en memoria y un
solo override para toda la suite.
"""
import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from database import Base, get_db
from main import app

# En memoria y compartida entre conexiones (StaticPool): sin archivo, sin WinError 32.
engine = create_engine(
    "sqlite://",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def _override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


app.dependency_overrides[get_db] = _override_get_db


@pytest.fixture(autouse=True)
def clean_database():
    """Cada test arranca con las tablas recién creadas y vacías."""
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield
