"""Satellite active fire detection endpoints powered by NASA FIRMS."""
from fastapi import APIRouter, Query
from typing import Dict, Any
from app.clients.nasa_firms import nasa_firms_client

router = APIRouter(prefix="/wildfires", tags=["Wildfires"])


@router.get("")
async def get_active_fires(
    min_lat: float = Query(32.0, description="South bounding latitude"),
    min_lon: float = Query(77.0, description="West bounding longitude"),
    max_lat: float = Query(32.5, description="North bounding latitude"),
    max_lon: float = Query(77.5, description="East bounding longitude"),
    days: int = Query(1, ge=1, le=5, description="Historical day range (1 to 5)"),
    source: str = Query("VIIRS_SNPP_NRT", description="Satellite sensor (VIIRS_SNPP_NRT, MODIS_NRT)"),
) -> Dict[str, Any]:
    """Queries near-real-time satellite active fire hotspots from NASA FIRMS."""
    return await nasa_firms_client.fetch_active_fires(
        min_lat=min_lat,
        min_lon=min_lon,
        max_lat=max_lat,
        max_lon=max_lon,
        source=source,
        day_range=days,
    )

