"""Settlement and Tourist Destination Search API."""
from typing import List, Optional
from fastapi import APIRouter, Query
from app.clients.open_meteo import open_meteo_client
from app.schemas.route_plan import PlaceSearchResult
from app.services.spatial.region_boundary_service import region_boundary_service
from app.services.spatial.uttarakhand_settlements_registry import (
    search_uttarakhand_settlements,
    find_nearest_settlement,
)

router = APIRouter(prefix="/places", tags=["Places & Geocoding"])


@router.get("/search", response_model=List[PlaceSearchResult])
async def search_places(
    q: str = Query(..., min_length=2, description="Place or settlement name to search"),
    count: int = Query(8, ge=1, le=20, description="Max results to return"),
):
    """
    Searches settlements, trailheads, and tourist destinations within the supported Uttarakhand region.
    Integrates the authoritative Uttarakhand settlements registry with phonetic/typo tolerance
    (e.g. 'almore' -> 'Almora', 'kedar' -> 'Kedarnath') alongside Open-Meteo geocoding.
    Filters out any results outside the Uttarakhand state boundary.
    """
    results: List[PlaceSearchResult] = []
    seen_coords = set()

    # 1. Authoritative Uttarakhand settlements registry search (handles typos, aliases, local names)
    local_matches = search_uttarakhand_settlements(query=q, limit=count)
    for m in local_matches:
        coord_key = (round(m["latitude"], 2), round(m["longitude"], 2))
        seen_coords.add(coord_key)
        results.append(
            PlaceSearchResult(
                name=m["name"],
                latitude=m["latitude"],
                longitude=m["longitude"],
                country="India",
                admin1="Uttarakhand",
                display_name=m["display_name"],
            )
        )

    # 2. Open-Meteo upstream geocoding search for broader settlements/peaks
    try:
        raw_results = await open_meteo_client.search_settlements(query=q, count=max(count, 10))
        for r in raw_results:
            lat = r["latitude"]
            lon = r["longitude"]
            coord_key = (round(lat, 2), round(lon, 2))
            if coord_key in seen_coords:
                continue

            # Filter strictly to the verified Uttarakhand administrative boundary
            if not region_boundary_service.is_point_in_uttarakhand(lat, lon):
                continue

            seen_coords.add(coord_key)
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
    except Exception:
        pass

    return results[:count]


@router.get("/reverse", response_model=PlaceSearchResult)
async def reverse_geocode(
    lat: float = Query(..., ge=-90.0, le=90.0, description="Latitude"),
    lon: float = Query(..., ge=-180.0, le=180.0, description="Longitude"),
):
    """
    Returns place information or structured coordinate label for a user-selected map pin.
    Preserves exact selected coordinates without substitution and matches nearest known
    settlement within Uttarakhand when available.
    """
    is_in_uk = region_boundary_service.is_point_in_uttarakhand(lat, lon)
    if is_in_uk:
        nearest = find_nearest_settlement(lat, lon, max_dist_km=25.0)
        if nearest:
            coord_label = f"Map Pin: {nearest['name']} ({lat:.4f}, {lon:.4f})"
            country = "India"
            admin1 = "Uttarakhand"
            disp_name = f"{nearest['name']} Area ({nearest['district']}), Uttarakhand"
        else:
            coord_label = f"Map Pin ({lat:.4f}, {lon:.4f})"
            country = "India"
            admin1 = "Uttarakhand"
            disp_name = f"Map Pin ({lat:.4f}, {lon:.4f}), Uttarakhand"
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


