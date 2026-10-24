"""Settlement and Tourist Destination Search API."""
from typing import List, Optional
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
    Searches settlements, trailheads, and tourist destinations dynamically across any supported region.
    Never fabricates placeholder settlements.
    """
    raw_results = await open_meteo_client.search_settlements(query=q, count=count)

    results = []
    for r in raw_results:
        results.append(
            PlaceSearchResult(
                name=r["name"],
                latitude=r["latitude"],
                longitude=r["longitude"],
                country=r.get("country", ""),
                admin1=r.get("admin1", ""),
                display_name=r.get("display_name", r["name"]),
            )
        )
    return results[:count]


@router.get("/reverse", response_model=PlaceSearchResult)
async def reverse_geocode(
    lat: float = Query(..., ge=-90.0, le=90.0, description="Latitude"),
    lon: float = Query(..., ge=-180.0, le=180.0, description="Longitude"),
):
    """
    Returns place information or structured coordinate label for a user-selected map pin.
    Preserves exact selected coordinates without substitution.
    """
    coord_label = f"Map Pin ({lat:.4f}, {lon:.4f})"
    return PlaceSearchResult(
        name=coord_label,
        latitude=lat,
        longitude=lon,
        country="",
        admin1="",
        display_name=coord_label,
    )


