"""Settlement and Tourist Destination Search API."""
from typing import List
from fastapi import APIRouter, Query
from app.clients.open_meteo import open_meteo_client
from app.schemas.route_plan import PlaceSearchResult

router = APIRouter(prefix="/places", tags=["Places & Geocoding"])


@router.get("/search", response_model=List[PlaceSearchResult])
async def search_places(
    q: str = Query(..., min_length=2, description="Place or settlement name to search"),
    count: int = Query(8, ge=1, le=20, description="Max results to return"),
):
    """
    Searches settlements and tourist destinations via Open-Meteo Geocoding API with local mountain fallbacks.
    """
    raw_results = await open_meteo_client.search_settlements(query=q, count=count)
    return [
        PlaceSearchResult(
            name=r["name"],
            latitude=r["latitude"],
            longitude=r["longitude"],
            country=r.get("country", ""),
            admin1=r.get("admin1", ""),
            display_name=r.get("display_name", r["name"]),
        )
        for r in raw_results
    ]
