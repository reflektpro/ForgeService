"""
Database configuration for ForgeService.
Supports both local SQLite and cloud (Railway/Render) via env vars.
"""

from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy import event
import os
from pathlib import Path

# --- Configurable paths via environment variables (great for cloud deploys) ---
# On Railway with Volume mounted at /data:
#   DATABASE_URL=sqlite+aiosqlite:////data/forgeservice.db
#   PHOTOS_DIR=/data/photos

PHOTOS_DIR = Path(os.getenv("PHOTOS_DIR", "data/photos"))
DB_PATH = Path(os.getenv("DB_PATH", "data/forgeservice.db"))

# Ensure directories exist (works locally and on volumes)
PHOTOS_DIR.mkdir(parents=True, exist_ok=True)
DB_PATH.parent.mkdir(parents=True, exist_ok=True)

# DATABASE_URL can be overridden completely (for Postgres later: postgresql+asyncpg://...)
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    f"sqlite+aiosqlite:///{DB_PATH}"
)

# Create async engine
engine = create_async_engine(
    DATABASE_URL,
    echo=False,  # Set to True for SQL debugging during development
    future=True,
    connect_args={"check_same_thread": False},
)

# Session factory
AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    expire_on_commit=False,
    class_=AsyncSession,
)


class Base(DeclarativeBase):
    """Base class for all SQLAlchemy models."""
    pass


async def get_db() -> AsyncSession:
    """Dependency for FastAPI routes."""
    async with AsyncSessionLocal() as session:
        yield session


async def init_db():
    """Create all tables. Call on startup."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


def init_db_sync():
    """Synchronous table creation (useful for seed scripts and first deploy)."""
    from sqlalchemy import create_engine
    # Handle both sqlite+aiosqlite and plain sqlite
    sync_url = DATABASE_URL.replace("sqlite+aiosqlite", "sqlite")
    sync_engine = create_engine(sync_url, echo=False)
    Base.metadata.create_all(sync_engine)
    print("[DB] Tables created via sync engine (seed safety)")


# Enable foreign keys for SQLite (only when using SQLite)
if DATABASE_URL.startswith("sqlite"):
    @event.listens_for(engine.sync_engine, "connect")
    def set_sqlite_pragma(dbapi_conn, connection_record):
        cursor = dbapi_conn.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()