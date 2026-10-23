"""API v1 router aggregator."""
from fastapi import APIRouter
from app.api.v1.health import router as health_router
from app.api.v1.itineraries import router as itineraries_router
from app.api.v1.map_points import router as map_points_router
from app.api.v1.alerts import router as alerts_router
from app.api.v1.weather import router as weather_router
from app.api.v1.crowd import router as crowd_router
from app.api.v1.sources import router as sources_router
from app.api.v1.auth import router as auth_router
from app.api.v1.admin import router as admin_router

api_v1_router = APIRouter()

api_v1_router.include_router(health_router)
api_v1_router.include_router(itineraries_router)
api_v1_router.include_router(map_points_router)
api_v1_router.include_router(alerts_router)
api_v1_router.include_router(weather_router)
api_v1_router.include_router(crowd_router)
api_v1_router.include_router(sources_router)
api_v1_router.include_router(auth_router)
api_v1_router.include_router(admin_router)

