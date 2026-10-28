"""Forest cover and woodland geometry endpoints powered by OpenStreetMap Overpass API."""
from fastapi import APIRouter, Query
from typing import Dict, Any
from app.clients.osm_forest import osm_forest_client

router = APIRouter(prefix="/forest-areas", tags=["Forest Cover"])


@router.get("")
async def get_forest_areas(
    min_lat: float = Query(30.00, description="South bounding latitude (Uttarakhand Garhwal corridor)"),
    min_lon: float = Query(78.80, description="West bounding longitude (Uttarakhand Garhwal corridor)"),
    max_lat: float = Query(30.80, description="North bounding latitude (Uttarakhand Garhwal corridor)"),
    max_lon: float = Query(79.60, description="East bounding longitude (Uttarakhand Garhwal corridor)"),
) -> Dict[str, Any]:
    """Queries mapped forest and woodland geometries from OpenStreetMap Overpass API with Uttarakhand baseline fallback."""
    return await osm_forest_client.fetch_forest_features(
        min_lat=min_lat,
        min_lon=min_lon,
        max_lat=max_lat,
        max_lon=max_lon,
    )
