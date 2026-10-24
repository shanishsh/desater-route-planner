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


async def _apply_schema_migrations(conn):
    """Safely applies missing column migrations across existing tables."""
    statements = [
        "ALTER TABLE planned_routes ADD COLUMN IF NOT EXISTS route_version INTEGER DEFAULT 1",
        "ALTER TABLE planned_routes ADD COLUMN IF NOT EXISTS routing_provider VARCHAR(50) DEFAULT 'OSRM'",
        "ALTER TABLE planned_routes ADD COLUMN IF NOT EXISTS analysis_data JSON",
        "ALTER TABLE planned_routes ADD COLUMN IF NOT EXISTS last_analyzed_at TIMESTAMP WITH TIME ZONE",
        "ALTER TABLE disaster_alerts ADD COLUMN IF NOT EXISTS area_description TEXT",
        "ALTER TABLE disaster_alerts ADD COLUMN IF NOT EXISTS geocode_info TEXT",
        "ALTER TABLE disaster_alerts ADD COLUMN IF NOT EXISTS external_identifier VARCHAR(100)",
        "ALTER TABLE disaster_alerts ADD COLUMN IF NOT EXISTS polygon_geojson TEXT",
        "ALTER TABLE disaster_alerts ADD COLUMN IF NOT EXISTS center_latitude FLOAT",
        "ALTER TABLE disaster_alerts ADD COLUMN IF NOT EXISTS center_longitude FLOAT",
        "ALTER TABLE disaster_alerts ADD COLUMN IF NOT EXISTS affected_radius_meters FLOAT DEFAULT 5000.0",
        "CREATE INDEX IF NOT EXISTS ix_disaster_alerts_external_identifier ON disaster_alerts (external_identifier)",
        "ALTER TABLE map_points ADD COLUMN IF NOT EXISTS osm_id VARCHAR(100)",
        "ALTER TABLE map_points ADD COLUMN IF NOT EXISTS osm_type VARCHAR(20)",
        "CREATE INDEX IF NOT EXISTS ix_map_points_osm_id ON map_points (osm_id)",
    ]
    for stmt in statements:
        try:
            from sqlalchemy import text
            await conn.execute(text(stmt))
        except Exception as ex:
            # Handle SQLite syntax variations or existing column nuances gracefully
            logger.debug("Migration statement skipped or already applied (%s): %s", stmt, ex)


async def init_db():
    """Initializes database schema and ensures all table columns are up to date."""
    global engine, async_session_maker, AsyncSessionLocal
    try:
        async with engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)
            await _apply_schema_migrations(conn)
        logger.info("Database schema initialized and columns synchronized successfully.")
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

