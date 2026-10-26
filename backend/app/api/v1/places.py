"""Settlement and Tourist Destination Search API."""
from typing import List, Optional
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
    Searches settlements, trailheads, and tourist destinations within the supported Uttarakhand region.
    Filters out any results outside the Uttarakhand state boundary.
    Never fabricates placeholder settlements.
    """
    raw_results = await open_meteo_client.search_settlements(query=q, count=max(count, 10))

    results = []
    for r in raw_results:
        lat = r["latitude"]
        lon = r["longitude"]
        # Filter strictly to the verified Uttarakhand administrative boundary
        if not region_boundary_service.is_point_in_uttarakhand(lat, lon):
            continue

        results.append(
            PlaceSearchResult(
                name=r["name"],
                latitude=lat,
                longitude=lon,
                country=r.get("country", "India"),
                admin1=r.get("admin1", "Uttarakhand"),
                display_name=r.get("display_name", f"{r['name']}, Uttarakhand"),
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
    Preserves exact selected coordinates without substitution and flags coordinates outside Uttarakhand.
    """
    is_in_uk = region_boundary_service.is_point_in_uttarakhand(lat, lon)
    if is_in_uk:
        coord_label = f"Map Pin ({lat:.4f}, {lon:.4f})"
        country = "India"
        admin1 = "Uttarakhand"
        disp_name = coord_label
    else:
        coord_label = f"Map Pin ({lat:.4f}, {lon:.4f})"
        country = "Outside Supported Region"
        admin1 = "Unsupported"
        disp_name = f"Map Pin ({lat:.4f}, {lon:.4f}) [Outside Uttarakhand - Unsupported]"

    return PlaceSearchResult(
        name=coord_label,
        latitude=lat,
        longitude=lon,
        country=country,
        admin1=admin1,
        display_name=disp_name,
    )


