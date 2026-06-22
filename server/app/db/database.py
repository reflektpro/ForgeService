"""
Database configuration for ForgeService.
Supports SQLite (local), PostgreSQL and MySQL (Railway/Render) via env vars.
"""

from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy import event
import os
from pathlib import Path

# --- Configurable paths via environment variables (great for cloud deploys) ---
# Local SQLite:
#   DATABASE_URL=sqlite+aiosqlite:///data/forgeservice.db
# Railway PostgreSQL (auto-injected, or copy from Postgres service):
#   DATABASE_URL=postgresql://user:pass@host:port/railway
# Railway MySQL:
#   DATABASE_URL=mysql://user:pass@host:port/railway

PHOTOS_DIR = Path(os.getenv("PHOTOS_DIR", "data/photos"))
DB_PATH = Path(os.getenv("DB_PATH", "data/forgeservice.db"))

PHOTOS_DIR.mkdir(parents=True, exist_ok=True)
DB_PATH.parent.mkdir(parents=True, exist_ok=True)


def _raw_database_url() -> str:
    return os.getenv("DATABASE_URL", f"sqlite+aiosqlite:///{DB_PATH}")


def normalize_async_url(url: str) -> str:
    """Convert platform DATABASE_URL to SQLAlchemy async driver format."""
    if url.startswith("postgres://"):
        url = url.replace("postgres://", "postgresql://", 1)
    if url.startswith("postgresql://"):
        return url.replace("postgresql://", "postgresql+asyncpg://", 1)
    if url.startswith("mysql://"):
        return url.replace("mysql://", "mysql+aiomysql://", 1)
    return url


def normalize_sync_url(url: str) -> str:
    """Sync URL for seed scripts and migrations."""
    if url.startswith("postgres://"):
        url = url.replace("postgres://", "postgresql://", 1)
    if "sqlite+aiosqlite" in url:
        return url.replace("sqlite+aiosqlite", "sqlite")
    if url.startswith("postgresql+asyncpg://"):
        return url.replace("postgresql+asyncpg://", "postgresql+psycopg2://", 1)
    if url.startswith("postgresql://"):
        return url.replace("postgresql://", "postgresql+psycopg2://", 1)
    if url.startswith("mysql+aiomysql://"):
        return url.replace("mysql+aiomysql://", "mysql+pymysql://", 1)
    if url.startswith("mysql://"):
        return url.replace("mysql://", "mysql+pymysql://", 1)
    return url


RAW_DATABASE_URL = _raw_database_url()
DATABASE_URL = normalize_async_url(RAW_DATABASE_URL)
SYNC_DATABASE_URL = normalize_sync_url(RAW_DATABASE_URL)

connect_args = {}
if "sqlite" in DATABASE_URL:
    connect_args = {"check_same_thread": False}

engine_kwargs = {
    "echo": False,
    "future": True,
    "connect_args": connect_args,
}
if "postgresql" in DATABASE_URL or "mysql" in DATABASE_URL:
    engine_kwargs["pool_pre_ping"] = True

engine = create_async_engine(DATABASE_URL, **engine_kwargs)

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
    sync_engine = create_engine(SYNC_DATABASE_URL, echo=False, pool_pre_ping=True)
    Base.metadata.create_all(sync_engine)
    print("[DB] Tables created via sync engine (seed safety)")


# Enable foreign keys for SQLite (only when using SQLite)
if DATABASE_URL.startswith("sqlite"):
    @event.listens_for(engine.sync_engine, "connect")
    def set_sqlite_pragma(dbapi_conn, connection_record):
        cursor = dbapi_conn.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()