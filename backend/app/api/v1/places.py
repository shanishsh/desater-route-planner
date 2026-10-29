"""Settlement and Tourist Destination Search API."""
import math
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Query, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.database.session import get_db
from app.clients.open_meteo import open_meteo_client
from app.schemas.route_plan import PlaceSearchResult
from app.schemas.point_context import PointContextResponse, NearbyHazardItem, NearbyFacilityItem
from app.services.weather_service import WeatherService
from app.services.terrain.elevation_service import ElevationTerrainService, haversine_distance_m
from app.services.spatial.region_boundary_service import region_boundary_service
from app.services.spatial.uttarakhand_settlements_registry import (
    search_uttarakhand_settlements,
    find_nearest_settlement,
)
from app.services.spatial.waterway_service import waterway_service
from app.models.disaster_alert import DisasterAlert
from app.models.map_point import MapPoint

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


@router.get("/point-context", response_model=PointContextResponse)
async def get_point_context(
    lat: float = Query(..., ge=-90.0, le=90.0, description="Latitude in decimal degrees"),
    lon: float = Query(..., ge=-180.0, le=180.0, description="Longitude in decimal degrees"),
    radius_km: float = Query(5.0, ge=0.5, le=25.0, description="Inspection search radius in kilometers"),
    db: AsyncSession = Depends(get_db),
):
    """
    Unified Point Inspection Endpoint:
    Returns atmospheric weather forecast, official disaster warnings, nearby verified facilities,
    elevation, river proximity, and cellular coverage status for any arbitrary or route-tapped map location.
    """
    now = datetime.now(timezone.utc)
    in_uk = region_boundary_service.is_point_in_uttarakhand(lat, lon)

    # 1. Settlement Context
    nearest_settlement_name = None
    settlement_dist_km = None
    nearest_settlement = find_nearest_settlement(lat, lon, max_dist_km=35.0)
    if nearest_settlement:
        nearest_settlement_name = f"{nearest_settlement['name']} ({nearest_settlement.get('district', 'Uttarakhand')})"
        settlement_dist_km = round(nearest_settlement.get("distance_km", 0.0), 2)

    # 2. Weather
    weather_service = WeatherService(db)
    weather_data = None
    try:
        weather_data = await weather_service.get_weather(lat, lon)
    except Exception:
        pass

    # 3. Elevation
    elevation_m = None
    try:
        elev_service = ElevationTerrainService()
        elev_pts = await elev_service.fetch_elevations_for_coordinates([(lat, lon)])
        if elev_pts:
            elevation_m = elev_pts[0].get("elevation_m")
    except Exception:
        pass

    # 4. Active Warnings & Nearby Hazards
    active_warnings = []
    nearby_hazards = []
    try:
        stmt = select(DisasterAlert).where(DisasterAlert.status == "active")
        res = await db.execute(stmt)
        alerts = res.scalars().all()
        for a in alerts:
            dist_km = None
            dist_m = 999999.0
            if a.center_latitude is not None and a.center_longitude is not None:
                dist_m = haversine_distance_m(lat, lon, a.center_latitude, a.center_longitude)
                dist_km = round(dist_m / 1000.0, 2)

            is_intersecting = False
            if dist_km is not None and a.affected_radius_meters:
                if dist_m <= a.affected_radius_meters:
                    is_intersecting = True
            elif dist_km is not None and dist_km <= radius_km:
                is_intersecting = True

            if is_intersecting or (dist_km is not None and dist_km <= radius_km * 2):
                active_warnings.append(a)
                nearby_hazards.append(
                    NearbyHazardItem(
                        id=str(a.id),
                        category=a.hazard_category,
                        title=a.title,
                        distance_km=dist_km if dist_km is not None else 0.0,
                        severity=a.severity,
                        source=a.issuing_authority or "Disaster Authority",
                        is_intersecting=is_intersecting,
                        latitude=a.center_latitude,
                        longitude=a.center_longitude,
                        details={"instructions": a.required_protective_instructions or "Monitor local advisories."}
                    )
                )
    except Exception:
        pass

    # 5. Nearby Facilities (hospitals, police, petrol pumps, clinics, shelters)
    nearby_facilities = []
    try:
        lat_delta = radius_km / 111.0
        lon_delta = radius_km / (111.0 * max(0.1, math.cos(math.radians(lat))))
        f_stmt = (
            select(MapPoint)
            .where(
                MapPoint.latitude.between(lat - lat_delta, lat + lat_delta),
                MapPoint.longitude.between(lon - lon_delta, lon + lon_delta)
            )
            .limit(30)
        )
        f_res = await db.execute(f_stmt)
        points = f_res.scalars().all()
        f_items = []
        for p in points:
            d_m = haversine_distance_m(lat, lon, p.latitude, p.longitude)
            d_km = round(d_m / 1000.0, 2)
            if d_km <= radius_km:
                f_items.append(
                    NearbyFacilityItem(
                        id=str(p.id),
                        name=p.name,
                        category=p.category,
                        distance_km=d_km,
                        latitude=p.latitude,
                        longitude=p.longitude,
                        phone=p.phone,
                        address=p.address,
                        is_verified=p.verification_status == "VERIFIED"
                    )
                )
        f_items.sort(key=lambda x: x.distance_km)
        nearby_facilities = f_items[:10]
    except Exception:
        pass

    # 6. Nearest Waterway
    nearest_waterway = None
    try:
        water_res = waterway_service.find_waterways_near_point(lat, lon, max_dist_km=radius_km)
        if water_res:
            nearest_waterway = water_res
    except Exception:
        pass

    # 7. Cellular Coverage Estimate
    cellular_estimate = {
        "status": "VARIABLE_OR_ROAMING",
        "description": "Standard Himalayan alpine terrain coverage varies by valley geometry and line of sight to tower masts.",
        "primary_carrier": "BSNL / Jio / Airtel (Band 3/5/40)"
    }
    if not in_uk:
        cellular_estimate["status"] = "OUTSIDE_REGION"
        cellular_estimate["description"] = "Coordinate is outside Uttarakhand cellular analysis zone."

    return PointContextResponse(
        latitude=round(lat, 5),
        longitude=round(lon, 5),
        in_uttarakhand=in_uk,
        nearest_settlement=nearest_settlement_name,
        settlement_distance_km=settlement_dist_km,
        elevation_m=elevation_m,
        weather=weather_data,
        active_warnings=active_warnings,
        nearby_hazards=nearby_hazards,
        nearby_facilities=nearby_facilities,
        nearest_waterway=nearest_waterway,
        cellular_estimate=cellular_estimate,
        retrieved_at=now,
        is_cached=weather_data.is_cached if weather_data else False,
        is_stale=weather_data.is_stale if weather_data else False
    )


