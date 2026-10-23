"""Provider Registry endpoints for checking upstream provider health, latency, and refresh."""
from fastapi import APIRouter, Query, Header, HTTPException, status
from typing import List, Dict, Any, Optional
from app.services.provider_registry import provider_registry
from app.config import settings

router = APIRouter(prefix="/providers", tags=["Providers"])


@router.get("/status")
async def get_providers_status() -> List[Dict[str, Any]]:
    """Returns the operational status, latency, and freshness metadata of all external providers."""
    return await provider_registry.get_all_provider_statuses()


@router.post("/refresh")
async def refresh_providers(
    lat: float = Query(32.2396, description="Test latitude for provider smoke validation"),
    lon: float = Query(77.1887, description="Test longitude for provider smoke validation"),
    x_api_key: Optional[str] = Header(None, description="Optional admin API key for rate limit bypass"),
) -> Dict[str, Any]:
    """Triggers an isolated upstream provider check and updates local status caches."""
    # Light rate protection: if API key provided, verify it; otherwise allow bounded check
    if x_api_key and x_api_key != settings.ADMIN_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Invalid admin authorization credentials."
        )

    return await provider_registry.refresh_all(test_lat=lat, test_lon=lon)

