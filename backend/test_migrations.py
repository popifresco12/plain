"""Migraciones (Alembic): BD nueva, BD anterior a Alembic con datos, e idempotencia."""
import os
import tempfile

from sqlalchemy import create_engine, inspect, text

from database import Base
from main import run_migrations
import models  # noqa: F401


def _engine(tmpdir):
    path = os.path.join(tmpdir, "m.db")
    return create_engine(f"sqlite:///{path}", connect_args={"check_same_thread": False})


def _version(eng):
    with eng.connect() as c:
        return c.execute(text("SELECT version_num FROM alembic_version")).scalar()


def test_bd_nueva_se_crea_con_upgrade():
    with tempfile.TemporaryDirectory() as d:
        eng = _engine(d)
        assert run_migrations(eng) == "upgrade"
        tablas = set(inspect(eng).get_table_names())
        assert {"users", "plans", "notifications", "refresh_tokens", "alembic_version"} <= tablas
        assert _version(eng) == "0001_baseline"
        eng.dispose()


def test_bd_anterior_se_completa_y_se_marca_sin_perder_datos():
    with tempfile.TemporaryDirectory() as d:
        eng = _engine(d)
        # Simula la BD de producción de 0.8.x: sin tablas nuevas ni users.token_version
        viejas = [t for n, t in Base.metadata.tables.items() if n not in ("notifications", "refresh_tokens")]
        Base.metadata.create_all(eng, tables=viejas)
        with eng.begin() as c:
            c.execute(text("ALTER TABLE users DROP COLUMN token_version"))
            c.execute(text("INSERT INTO users (username, email, password_hash) VALUES ('ana', 'a@x.com', 'h')"))
        assert run_migrations(eng) == "stamp"
        insp = inspect(eng)
        assert "notifications" in insp.get_table_names()
        assert "token_version" in {c["name"] for c in insp.get_columns("users")}
        with eng.connect() as c:
            assert c.execute(text("SELECT username FROM users")).scalar() == "ana"
        assert _version(eng) == "0001_baseline"
        # segunda vez: ya versionada, no hace nada raro
        assert run_migrations(eng) == "upgrade"
        eng.dispose()


def test_modelos_y_migraciones_no_se_desincronizan():
    """Si alguien cambia models.py sin crear migración, este test falla."""
    from alembic.autogenerate import compare_metadata
    from alembic.migration import MigrationContext

    with tempfile.TemporaryDirectory() as d:
        eng = _engine(d)
        run_migrations(eng)
        with eng.connect() as c:
            diff = compare_metadata(MigrationContext.configure(c), Base.metadata)
        assert diff == [], f"Falta una migración para: {diff}"
        eng.dispose()
