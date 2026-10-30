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
import app.models.sar
import app.models.trail_report

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
    else:
        # PostgreSQL / asyncpg production-ready pool tuning
        # Disabling JIT prevents query latency spikes on short spatial PostGIS queries
        connect_args["server_settings"] = {"jit": "off"}
        return create_async_engine(
            url,
            echo=settings.DATABASE_ECHO,
            future=True,
            pool_size=10,
            max_overflow=5,
            pool_recycle=300,
            pool_pre_ping=True,
            pool_timeout=30.0,
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
        "CREATE TABLE IF NOT EXISTS sar_incidents (id VARCHAR(64) PRIMARY KEY, trip_id VARCHAR(64) NOT NULL, device_id VARCHAR(128), latitude FLOAT NOT NULL, longitude FLOAT NOT NULL, elevation_m FLOAT DEFAULT 0.0, battery_pct INTEGER DEFAULT 100, emergency_type VARCHAR(50) DEFAULT 'MANUAL_PANIC', status VARCHAR(50) DEFAULT 'ACTIVE_RESCUE', escalation_level VARCHAR(50) DEFAULT 'RED_PRIORITY_1', details TEXT, route_id VARCHAR(64), created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)",
        "CREATE INDEX IF NOT EXISTS ix_sar_incidents_trip_id ON sar_incidents (trip_id)",
        "CREATE TABLE IF NOT EXISTS trekker_checkins (id VARCHAR(64) PRIMARY KEY, trip_id VARCHAR(64) NOT NULL, device_id VARCHAR(128), latitude FLOAT NOT NULL, longitude FLOAT NOT NULL, elevation_m FLOAT DEFAULT 0.0, gps_accuracy_m FLOAT DEFAULT 10.0, battery_pct INTEGER DEFAULT 100, status VARCHAR(50) DEFAULT 'OK', notes TEXT, recorded_at TIMESTAMP WITH TIME ZONE NOT NULL, received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, next_check_in_due TIMESTAMP WITH TIME ZONE NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)",
        "CREATE INDEX IF NOT EXISTS ix_trekker_checkins_trip_id ON trekker_checkins (trip_id)",
        "CREATE INDEX IF NOT EXISTS ix_disaster_alerts_coords ON disaster_alerts (center_latitude, center_longitude)",
        "CREATE INDEX IF NOT EXISTS ix_planned_routes_endpoints ON planned_routes (origin_lat, origin_lon, destination_lat, destination_lon)",
        "CREATE INDEX IF NOT EXISTS ix_planned_routes_created_at ON planned_routes (created_at DESC)",
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
            if engine is not None:
                try:
                    await engine.dispose()
                except Exception:
                    pass
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

