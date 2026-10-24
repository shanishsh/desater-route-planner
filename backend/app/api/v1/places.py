"""Settlement and Tourist Destination Search API."""
from typing import List
from fastapi import APIRouter, Query
from app.clients.open_meteo import open_meteo_client
from app.schemas.route_plan import PlaceSearchResult
from app.services.spatial.region_boundary_service import region_boundary_service

router = APIRouter(prefix="/places", tags=["Places & Geocoding"])


@router.get("/search", response_model=List[PlaceSearchResult])
async def search_places(
    q: str = Query(..., min_length=2, description="Place or settlement name to search"),
    count: int = Query(8, ge=1, le=20, description="Max results to return"),
):
    """
    Searches settlements and tourist destinations scoped and prioritized for Uttarakhand.
    """
    raw_results = await open_meteo_client.search_settlements(query=q, count=count * 3)

    # Filter and prioritize places inside the supported Uttarakhand region
    uk_results = []
    other_results = []

    for r in raw_results:
        lat = r["latitude"]
        lon = r["longitude"]
        admin1 = (r.get("admin1") or "").lower()
        is_uk = admin1 == "uttarakhand" or region_boundary_service.is_point_in_uttarakhand(lat, lon)
        place = PlaceSearchResult(
            name=r["name"],
            latitude=lat,
            longitude=lon,
            country=r.get("country", ""),
            admin1=r.get("admin1", ""),
            display_name=r.get("display_name", r["name"]),
        )
        if is_uk:
            uk_results.append(place)
        else:
            other_results.append(place)

    # Return Uttarakhand results first, up to requested count
    return (uk_results + other_results)[:count]


