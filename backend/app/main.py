"""FastAPI application entrypoint."""
import sys
from pathlib import Path

# Ensure backend root is on sys.path so the module can run from any working directory
_backend_dir = str(Path(__file__).resolve().parent.parent)
if _backend_dir not in sys.path:
    sys.path.insert(0, _backend_dir)

from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from app.config import settings
from app.core.logging import setup_logging
from app.database.base import Base
from app.database.session import init_db, close_db
from app.api.v1.router import api_v1_router
from app.middleware.correlation import CorrelationIdMiddleware
from app.middleware.rate_limit import RateLimitMiddleware
from app.middleware.error_handler import ErrorHandlingMiddleware
from app.middleware.metrics import PrometheusMetricsMiddleware
from app.observability.metrics import get_metrics_response
from app.cache.redis_cache import cache_service
from app.clients.open_meteo import open_meteo_client
from app.clients.osm_forest import osm_forest_client
from app.clients.nasa_firms import nasa_firms_client
from app.clients.opencellid import opencellid_client
from app.clients.groq_client import groq_client


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan management."""
    setup_logging(settings.LOG_LEVEL)

    # Initialize tables on startup with graceful fallback
    await init_db()

    yield

    # Cleanup resources on shutdown
    await open_meteo_client.close()
    await osm_forest_client.close()
    await nasa_firms_client.close()
    await opencellid_client.close()
    await groq_client.close()
    await cache_service.close()
    await close_db()


app = FastAPI(
    title=settings.PROJECT_NAME,
    description="Backend API for the Tourist Safety & Assistance Platform",
    version="0.1.0",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

# Custom Middlewares (outermost executed first on request, last on response)
app.add_middleware(ErrorHandlingMiddleware)
app.add_middleware(RateLimitMiddleware)
app.add_middleware(CorrelationIdMiddleware)
app.add_middleware(PrometheusMetricsMiddleware)
app.add_middleware(GZipMiddleware, minimum_size=1000)

# CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(api_v1_router, prefix=settings.API_V1_PREFIX)


# Metrics endpoint
@app.get("/metrics", tags=["Observability"])
async def metrics():
    return get_metrics_response()


@app.get("/", tags=["Root"])
async def root():
    return {
        "message": "Welcome to the Tourist Safety & Assistance Platform API",
        "documentation": "/docs",
        "health": f"{settings.API_V1_PREFIX}/health/live",
    }


@app.get("/health", tags=["Health"])
async def root_health():
    """Root health check for cloud platform liveness probes (e.g. Render)."""
    return {"status": "alive"}


if __name__ == "__main__":
    import uvicorn
    server_port = getattr(settings, "PORT", getattr(settings, "API_PORT", 8000))
    server_host = getattr(settings, "API_HOST", "0.0.0.0")
    is_debug = getattr(settings, "DEBUG", settings.ENVIRONMENT == "development")
    uvicorn.run("app.main:app", host=server_host, port=server_port, reload=is_debug)
