"""Forest cover and woodland geometry endpoints powered by OpenStreetMap Overpass API."""
from fastapi import APIRouter, Query
from typing import Dict, Any
from app.clients.osm_forest import osm_forest_client

router = APIRouter(prefix="/forest-areas", tags=["Forest Cover"])


@router.get("")
async def get_forest_areas(
    min_lat: float = Query(32.15, description="South bounding latitude"),
    min_lon: float = Query(77.10, description="West bounding longitude"),
    max_lat: float = Query(32.35, description="North bounding latitude"),
    max_lon: float = Query(77.30, description="East bounding longitude"),
) -> Dict[str, Any]:
    """Queries mapped forest and woodland geometries from OpenStreetMap Overpass API."""
    return await osm_forest_client.fetch_forest_features(
        min_lat=min_lat,
        min_lon=min_lon,
        max_lat=max_lat,
        max_lon=max_lon,
    )

