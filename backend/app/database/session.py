"""Database session management with SQLAlchemy 2.0 AsyncIO."""
import logging
from typing import AsyncGenerator
from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from app.config import settings
from app.database.base import Base

logger = logging.getLogger(__name__)


def _create_engine(url: str):
    connect_args = {}
    if "sqlite" in url:
        connect_args["check_same_thread"] = False
    return create_async_engine(
        url,
        echo=settings.DATABASE_ECHO,
        future=True,
        connect_args=connect_args,
    )


engine = _create_engine(settings.DATABASE_URL)
async_session_maker = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False,
)
AsyncSessionLocal = async_session_maker


async def init_db():
    """Initializes database schema. Falls back to SQLite if PostgreSQL is unreachable."""
    global engine, async_session_maker, AsyncSessionLocal
    try:
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
        logger.info("Database schema initialized successfully.")
    except Exception as e:
        logger.warning("Could not connect to primary database (%s): %s", settings.DATABASE_URL.split("@")[-1], e)
        # If the failed URL is not SQLite, fall back to bundled SQLite database so service starts cleanly
        if "sqlite" not in settings.DATABASE_URL:
            logger.info("Falling back to local SQLite database: sqlite+aiosqlite:///./tourist.db")
            fallback_url = "sqlite+aiosqlite:///./tourist.db"
            engine = _create_engine(fallback_url)
            async_session_maker = async_sessionmaker(
                engine,
                class_=AsyncSession,
                expire_on_commit=False,
                autocommit=False,
                autoflush=False,
            )
            AsyncSessionLocal = async_session_maker
            async with engine.begin() as conn:
                await conn.run_sync(Base.metadata.create_all)
            logger.info("Fallback SQLite database initialized successfully.")
        else:
            raise


async def close_db():
    """Disposes database engine connections."""
    await engine.dispose()


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """Dependency that yields an async database session per request."""
    async with async_session_maker() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()

