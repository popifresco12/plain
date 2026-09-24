"""Entorno de Alembic: usa el mismo engine y los mismos modelos que la app."""
from logging.config import fileConfig

from alembic import context

import models  # noqa: F401  (registra todas las tablas en Base.metadata)
from database import Base, engine

config = context.config
if config.config_file_name is not None and config.attributes.get("configure_logger", True):
    fileConfig(config.config_file_name, disable_existing_loggers=False)

target_metadata = Base.metadata


def run_migrations_offline() -> None:
    context.configure(url=str(engine.url), target_metadata=target_metadata,
                      literal_binds=True, render_as_batch=True)
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    # Si quien llama pasa una conexión (arranque de la app / tests), se reutiliza.
    conn = config.attributes.get("connection")
    if conn is not None:
        _run(conn)
    else:
        with engine.connect() as connection:
            _run(connection)


def _run(connection) -> None:
    context.configure(
        connection=connection,
        target_metadata=target_metadata,
        render_as_batch=connection.dialect.name == "sqlite",  # SQLite no soporta ALTER completo
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
