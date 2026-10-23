"""Health check endpoints (liveness & readiness)."""
from datetime import datetime, timezone
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from app.database.session import get_db
from app.cache.redis_cache import cache_service
from app.schemas.common import HealthResponse

router = APIRouter(prefix="/health", tags=["Health"])


@router.get("/live", response_model=dict)
async def liveness():
    """Liveness probe: verifies process is alive and accepting connections."""
    return {"status": "alive", "timestamp": datetime.now(timezone.utc).isoformat()}


@router.get("/ready", response_model=HealthResponse)
async def readiness(db: AsyncSession = Depends(get_db)):
    """Readiness probe: checks database and Redis connectivity."""
    db_status = "ok"
    try:
        await db.execute(text("SELECT 1"))
    except Exception as e:
        db_status = f"unhealthy: {str(e)}"

    redis_status = "ok"
    try:
        client = await cache_service.get_client()
        if client:
            await client.ping()
        else:
            redis_status = "degraded (in-memory mode)"
    except Exception as e:
        redis_status = f"degraded: {str(e)}"

    overall_status = "ok" if "unhealthy" not in db_status else "degraded"

    return HealthResponse(
        status=overall_status,
        version="0.1.0",
        db_status=db_status,
        redis_status=redis_status,
        timestamp=datetime.now(timezone.utc).isoformat(),
    )

