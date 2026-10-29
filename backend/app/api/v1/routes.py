"""Route Planning and Multi-Hazard Corridor Safety Scanning API."""
import hashlib
import json
import logging
from typing import List, Dict, Any, Optional

logger = logging.getLogger(__name__)
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database.session import get_db
from app.models.planned_route import PlannedRoute
from app.services.routing.osrm_provider import osrm_routing_provider
from app.services.routing.trail_routing_engine import trail_routing_engine
from app.services.route_condition_analyzer import route_condition_analyzer
from app.services.ai.route_summary_assistant import route_summary_assistant
from app.services.spatial.region_boundary_service import region_boundary_service
from app.services.routing.verified_corridors_registry import (
    get_verified_corridors,
    is_pedestrian_only_corridor,
)
from app.schemas.route_plan import (
    PlanRouteRequest,
    PlannedRouteResponse,
    RouteWaypointCondition,
    RouteAlternativeSchema,
    RouteAnalysisRequest,
    RouteAnalysisResponse,
    CustomRouteAnalysisRequest,
)

router = APIRouter(prefix="/routes", tags=["Route Planning & Safety"])


@router.post("/plan", response_model=PlannedRouteResponse, status_code=status.HTTP_201_CREATED)
async def plan_route(
    request: PlanRouteRequest,
    db: AsyncSession = Depends(get_db),
):
    """
    Plans a route between origin and destination, performs segment-by-segment corridor hazard scanning,
    correlates hourly weather forecasts at estimated arrival times, and persists the analysis.
    """
    if request.origin_lat is None or request.origin_lon is None:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Valid origin coordinates (latitude and longitude) are required.",
        )
    if request.destination_lat is None or request.destination_lon is None:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Valid destination coordinates (latitude and longitude) are required.",
        )

    # 1. Geographic Coordinate Validation
    if not (-90.0 <= request.origin_lat <= 90.0 and -180.0 <= request.origin_lon <= 180.0):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Origin coordinates ({request.origin_lat}, {request.origin_lon}) are outside valid WGS84 bounds.",
        )
    if not (-90.0 <= request.destination_lat <= 90.0 and -180.0 <= request.destination_lon <= 180.0):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Destination coordinates ({request.destination_lat}, {request.destination_lon}) are outside valid WGS84 bounds.",
        )

    # 1b. Authoritative Uttarakhand State Boundary Check
    if not region_boundary_service.is_point_in_uttarakhand(request.origin_lat, request.origin_lon):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Origin coordinates ({request.origin_lat:.4f}, {request.origin_lon:.4f}) are outside the state of Uttarakhand. Route planning and hazard scanning are strictly scoped to Uttarakhand, India.",
        )
    if not region_boundary_service.is_point_in_uttarakhand(request.destination_lat, request.destination_lon):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Destination coordinates ({request.destination_lat:.4f}, {request.destination_lon:.4f}) are outside the state of Uttarakhand. Route planning and hazard scanning are strictly scoped to Uttarakhand, India.",
        )

    # 2. Travel Mode Verification for Known Pedestrian-Only Alpine Trek Tracks (e.g. Kedarnath trail)
    is_ped_only, ped_reason = is_pedestrian_only_corridor(
        request.origin_lat, request.origin_lon,
        request.destination_lat, request.destination_lon
    )
    if is_ped_only and (request.travel_mode or "").lower() == "driving":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=ped_reason or "Motor vehicles are prohibited on this trekking corridor. Please select walking or hiking mode.",
        )

    # 3. Calculate genuine route geometry and alternatives via configured routing provider
    t_mode = (request.travel_mode or "driving").lower()
    if t_mode == "hiking":
        route_result = await trail_routing_engine.calculate_hiking_route(
            origin_lat=request.origin_lat,
            origin_lon=request.origin_lon,
            origin_name=request.origin_name or "Current Location",
            dest_lat=request.destination_lat,
            dest_lon=request.destination_lon,
            dest_name=request.destination_name,
        )
    elif t_mode == "walking":
        # Check if points are on a known verified Himalayan trail first
        origin_snap = trail_routing_engine.find_nearest_trail(request.origin_lat, request.origin_lon, max_dist_km=1.5)
        dest_snap = trail_routing_engine.find_nearest_trail(request.destination_lat, request.destination_lon, max_dist_km=1.5)
        if (
            origin_snap
            and dest_snap
            and origin_snap["trail_feature"]["id"] == dest_snap["trail_feature"]["id"]
        ):
            route_result = await trail_routing_engine.calculate_hiking_route(
                origin_lat=request.origin_lat,
                origin_lon=request.origin_lon,
                origin_name=request.origin_name or "Current Location",
                dest_lat=request.destination_lat,
                dest_lon=request.destination_lon,
                dest_name=request.destination_name,
            )
        else:
            # Pedestrian routing along connected walking and road networks
            try:
                route_result = await osrm_routing_provider.calculate_route(
                    origin_lat=request.origin_lat,
                    origin_lon=request.origin_lon,
                    origin_name=request.origin_name or "Current Location",
                    dest_lat=request.destination_lat,
                    dest_lon=request.destination_lon,
                    dest_name=request.destination_name,
                    travel_mode="walking",
                )
            except Exception:
                # Connected road network fallback with pedestrian pace calculation
                drive_res = await osrm_routing_provider.calculate_route(
                    origin_lat=request.origin_lat,
                    origin_lon=request.origin_lon,
                    origin_name=request.origin_name or "Current Location",
                    dest_lat=request.destination_lat,
                    dest_lon=request.destination_lon,
                    dest_name=request.destination_name,
                    travel_mode="driving",
                )
                drive_res.travel_mode = "walking"
                drive_res.duration_minutes = round((drive_res.distance_km / 3.5) * 60.0, 1)
                drive_res.duration_seconds = drive_res.duration_minutes * 60.0
                drive_res.verification_status = "PARTIALLY_VERIFIED"
                drive_res.unverified_reason = "Route follows connected public road network with pedestrian access."
                route_result = drive_res
    else:
        route_result = await osrm_routing_provider.calculate_route(
            origin_lat=request.origin_lat,
            origin_lon=request.origin_lon,
            origin_name=request.origin_name or "Current Location",
            dest_lat=request.destination_lat,
            dest_lon=request.destination_lon,
            dest_name=request.destination_name,
            travel_mode=t_mode,
        )

    # 3b. Verify calculated route polyline remains within Uttarakhand state boundary
    raw_coords = (route_result.geometry_geojson or {}).get("coordinates", [])
    coords_lat_lon = [(pt[1], pt[0]) for pt in raw_coords if len(pt) >= 2]
    is_in_state, out_reason = region_boundary_service.is_route_in_uttarakhand(
        coords_lat_lon,
        travel_mode=request.travel_mode or "driving",
    )
    if not is_in_state:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=out_reason or "Calculated route path exits the state boundary of Uttarakhand.",
        )

    # 4. Analyze corridor conditions (weather at arrival times, fires, forests, disaster alerts)
    corr_m = request.corridor_distance_meters or (
        (request.corridor_distance_km * 1000.0) if request.corridor_distance_km else 5000.0
    )
    analysis = await route_condition_analyzer.analyze_route(
        db=db,
        route=route_result,
        departure_time=request.departure_time,
        corridor_distance_meters=corr_m,
    )

    # 3. Persist PlannedRoute
    db_route = PlannedRoute(
        id=analysis.route_id,
        title=analysis.title,
        origin_name=analysis.origin_name,
        origin_lat=analysis.origin_lat,
        origin_lon=analysis.origin_lon,
        destination_name=analysis.destination_name,
        dest_lat=analysis.dest_lat,
        dest_lon=analysis.dest_lon,
        travel_mode=analysis.travel_mode,
        distance_km=analysis.distance_km,
        duration_minutes=analysis.duration_minutes,
        geometry_geojson=analysis.geometry_geojson,
        overall_hazard_score=analysis.overall_hazard_score,
        overall_safety_status=analysis.overall_safety_status,
        route_version=1,
        routing_provider="OSRM",
        conditions_data=[c.model_dump() for c in analysis.conditions],
        alternatives_data=[a.model_dump() for a in analysis.alternatives],
        data_freshness_data=analysis.data_freshness,
        analysis_data=analysis.model_dump(),
    )
    try:
        db.add(db_route)
        await db.commit()
        await db.refresh(db_route)
    except Exception as e:
        logger.error(f"Failed to persist planned route to database cache: {e}")
        await db.rollback()

    return analysis


@router.post("/custom/analyze", response_model=PlannedRouteResponse, status_code=status.HTTP_201_CREATED)
async def analyze_custom_drawn_route(
    request: CustomRouteAnalysisRequest,
    db: AsyncSession = Depends(get_db),
):
    """
    Accepts a user-drawn polyline geometry (from 'Draw my route' or GPS recorded trek),
    validates coordinate geometry against the Uttarakhand boundary, calculates actual
    polyline path distance, performs corridor multi-hazard analysis, and marks the route as
    'USER_DRAWN_UNVERIFIED' (or requested drawing_mode).
    """
    import uuid
    from app.services.routing.base import RouteResult, haversine_distance_km, sample_points_along_polyline, Waypoint

    raw_coords = []
    if request.geometry_geojson and isinstance(request.geometry_geojson, dict):
        raw_coords = request.geometry_geojson.get("coordinates", [])
    elif request.coordinates:
        raw_coords = request.coordinates

    if not raw_coords or len(raw_coords) < 2:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="User-drawn route geometry must contain at least 2 coordinate points.",
        )

    # Validate coordinate ordering [lon, lat] and boundary
    coords_lat_lon = []
    dist_km = 0.0
    for idx, pt in enumerate(raw_coords):
        if len(pt) < 2:
            continue
        lon, lat = pt[0], pt[1]
        if not (-90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"Coordinate at index {idx} ({lat}, {lon}) is out of WGS84 bounds.",
            )
        if not region_boundary_service.is_point_in_uttarakhand(lat, lon):
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"Point ({lat:.4f}, {lon:.4f}) is outside Uttarakhand state boundaries.",
            )
        coords_lat_lon.append((lat, lon))
        if idx > 0:
            dist_km += haversine_distance_km(coords_lat_lon[idx-1][0], coords_lat_lon[idx-1][1], lat, lon)

    dist_km = round(dist_km, 2)
    dur_mins = round((dist_km / 3.0) * 60.0, 1)  # Himalayan hiking speed ~3 km/h

    # Sample waypoints along the drawn polyline
    sampled = sample_points_along_polyline(raw_coords, interval_km=max(1.0, dist_km / 10.0), max_points=12)
    waypoints = [
        Waypoint(
            index=i,
            name=f"Drawn Waypoint {i+1}",
            latitude=round(w[0], 5),
            longitude=round(w[1], 5),
            distance_from_start_km=round(w[2], 2),
            estimated_travel_time_minutes=round((w[2] / 3.0) * 60.0, 1),
        )
        for i, w in enumerate(sampled)
    ]

    route_id = f"user-route-{uuid.uuid4().hex[:12]}"
    origin_name = "User Route Start"
    dest_name = "User Route Destination"

    v_status = "USER_DRAWN_UNVERIFIED" if request.drawing_mode != "RECORDED_TREK" else "RECORDED_TREK"

    route_result = RouteResult(
        route_id=route_id,
        title=request.title or request.route_name or "User-Drawn Route",
        origin_name=origin_name,
        origin_lat=coords_lat_lon[0][0],
        origin_lon=coords_lat_lon[0][1],
        destination_name=dest_name,
        destination_lat=coords_lat_lon[-1][0],
        destination_lon=coords_lat_lon[-1][1],
        travel_mode="hiking",
        distance_km=dist_km,
        duration_minutes=dur_mins,
        geometry_geojson={"type": "LineString", "coordinates": raw_coords},
        waypoints=waypoints,
        alternatives=[],
        verification_status=v_status,
        unverified_reason="Route geometry is user-defined. Walkability and terrain obstacles are unverified.",
        drawing_mode=request.drawing_mode,
        is_user_drawn=True,
    )

    corr_m = (request.corridor_distance_km or 2.0) * 1000.0
    analysis = await route_condition_analyzer.analyze_route(
        db=db,
        route=route_result,
        corridor_distance_meters=corr_m,
    )

    db_route = PlannedRoute(
        id=analysis.route_id,
        title=analysis.title,
        origin_name=analysis.origin_name,
        origin_lat=analysis.origin_lat,
        origin_lon=analysis.origin_lon,
        destination_name=analysis.destination_name,
        dest_lat=analysis.dest_lat,
        dest_lon=analysis.dest_lon,
        travel_mode=analysis.travel_mode,
        distance_km=analysis.distance_km,
        duration_minutes=analysis.duration_minutes,
        geometry_geojson=analysis.geometry_geojson,
        overall_hazard_score=analysis.overall_hazard_score,
        overall_safety_status=analysis.overall_safety_status,
        route_version=1,
        routing_provider="USER_DRAWN",
        conditions_data=[c.model_dump() for c in analysis.conditions],
        alternatives_data=[a.model_dump() for a in analysis.alternatives],
        data_freshness_data=analysis.data_freshness,
        analysis_data=analysis.model_dump(),
    )
    try:
        db.add(db_route)
        await db.commit()
    except Exception as e:
        logger.error(f"Failed to persist custom route to database: {e}")
        await db.rollback()

    return analysis


@router.post("/{route_id}/analyze", response_model=RouteAnalysisResponse)
async def analyze_route_corridor(
    route_id: str,
    request: Optional[RouteAnalysisRequest] = None,
    corridor_distance_km: Optional[float] = Query(None, description="Buffer distance on each side in km (e.g. 1, 2, 5, 10)"),
    db: AsyncSession = Depends(get_db),
):
    """
    Retrieves the selected, persisted route geometry from the backend,
    constructs a dynamic geographic corridor (following all turns and bends),
    queries real weather samples, official disaster advisories, active wildfires,
    and emergency safe havens strictly filtered to the corridor, increments
    the route version, and returns a unified route-analysis response with MapLibre GeoJSON overlays.
    """
    corr_m = None
    if request:
        if request.corridor_distance_meters:
            corr_m = request.corridor_distance_meters
        elif request.corridor_distance_km:
            corr_m = request.corridor_distance_km * 1000.0
    if corr_m is None and corridor_distance_km is not None:
        corr_m = corridor_distance_km * 1000.0

    analysis = await route_condition_analyzer.analyze_persisted_route(
        db=db,
        route_id=route_id,
        corridor_distance_meters=corr_m,
    )
    if not analysis:
        raise HTTPException(status_code=404, detail=f"Route with id '{route_id}' not found")

    try:
        summary_obj = await route_summary_assistant.generate_grounded_summary_async(analysis.model_dump())
        analysis.grounded_summary = summary_obj.model_dump()
    except Exception as e:
        logger.warning(f"Could not generate grounded route summary: {e}")

    return analysis



@router.get("/verified-corridors")
async def list_verified_corridors():
    """Returns tested and supported Uttarakhand pilot corridors."""
    return {
        "region": "Uttarakhand",
        "state_code": "UK",
        "corridors": get_verified_corridors(),
    }


@router.get("/region-info")
async def get_region_scoping_info():
    """Returns active regional scoping metadata for Uttarakhand."""
    return region_boundary_service.get_region_metadata()


@router.get("/region-boundary")
async def get_region_boundary():
    """Returns the authoritative administrative GeoJSON boundary for Uttarakhand."""
    return region_boundary_service.get_boundary_geojson()


@router.get("/region-mask")
async def get_region_mask():
    """Returns the inverted outside-state polygon mask GeoJSON for MapLibre rendering."""
    return region_boundary_service.get_mask_geojson()


@router.get("/{route_id}", response_model=PlannedRouteResponse)
async def get_planned_route(
    route_id: str,
    db: AsyncSession = Depends(get_db),
):
    """Retrieves a previously planned route with full condition analysis."""
    stmt = select(PlannedRoute).where(PlannedRoute.id == route_id)
    result = await db.execute(stmt)
    db_route = result.scalar_one_or_none()

    if not db_route:
        raise HTTPException(status_code=404, detail="Route not found")

    if db_route.analysis_data:
        try:
            return PlannedRouteResponse(**db_route.analysis_data)
        except Exception as e:
            logger.warning(f"Error restoring cached analysis_data for {route_id}: {e}")

    conditions = [RouteWaypointCondition(**c) for c in (db_route.conditions_data or [])]
    alternatives = [RouteAlternativeSchema(**a) for a in (db_route.alternatives_data or [])]

    return PlannedRouteResponse(
        route_id=db_route.id,
        route_version=db_route.route_version or 1,
        origin={
            "latitude": db_route.origin_lat,
            "longitude": db_route.origin_lon,
            "name": db_route.origin_name,
        },
        destination={
            "latitude": db_route.dest_lat,
            "longitude": db_route.dest_lon,
            "name": db_route.destination_name,
        },
        geometry=db_route.geometry_geojson,
        distance_meters=round((db_route.distance_km or 0.0) * 1000.0, 1),
        duration_seconds=round((db_route.duration_minutes or 0.0) * 60.0, 1),
        routing_provider=db_route.routing_provider or "OSRM",
        title=db_route.title,
        origin_name=db_route.origin_name,
        origin_lat=db_route.origin_lat,
        origin_lon=db_route.origin_lon,
        destination_name=db_route.destination_name,
        dest_lat=db_route.dest_lat,
        dest_lon=db_route.dest_lon,
        travel_mode=db_route.travel_mode,
        distance_km=db_route.distance_km,
        duration_minutes=db_route.duration_minutes,
        geometry_geojson=db_route.geometry_geojson,
        overall_hazard_score=db_route.overall_hazard_score,
        overall_safety_status=db_route.overall_safety_status,
        high_risk_segments_count=sum(1 for c in conditions if c.hazard_level in ["HIGH", "CRITICAL"]),
        conditions=conditions,
        alternatives=alternatives,
        data_freshness=db_route.data_freshness_data or {},
        created_at=db_route.created_at.isoformat() if db_route.created_at else "",
    )


@router.get("/{route_id}/conditions", response_model=List[RouteWaypointCondition])
async def get_route_conditions(
    route_id: str,
    db: AsyncSession = Depends(get_db),
):
    """Returns the waypoint-by-waypoint safety conditions for a planned route."""
    stmt = select(PlannedRoute).where(PlannedRoute.id == route_id)
    result = await db.execute(stmt)
    db_route = result.scalar_one_or_none()

    if not db_route:
        raise HTTPException(status_code=404, detail="Route not found")

    return [RouteWaypointCondition(**c) for c in (db_route.conditions_data or [])]


@router.get("/{route_id}/alternatives", response_model=List[RouteAlternativeSchema])
async def get_route_alternatives(
    route_id: str,
    db: AsyncSession = Depends(get_db),
):
    """Returns alternative routes bypassing identified hazard corridors."""
    stmt = select(PlannedRoute).where(PlannedRoute.id == route_id)
    result = await db.execute(stmt)
    db_route = result.scalar_one_or_none()

    if not db_route:
        raise HTTPException(status_code=404, detail="Route not found")

    return [RouteAlternativeSchema(**a) for a in (db_route.alternatives_data or [])]


@router.get("/{route_id}/comparison")
async def get_route_comparison(
    route_id: str,
    db: AsyncSession = Depends(get_db),
):
    """Returns side-by-side comparative analysis of primary route and all alternatives."""
    stmt = select(PlannedRoute).where(PlannedRoute.id == route_id)
    result = await db.execute(stmt)
    db_route = result.scalar_one_or_none()

    if not db_route:
        raise HTTPException(status_code=404, detail="Route not found")

    alternatives = [RouteAlternativeSchema(**a) for a in (db_route.alternatives_data or [])]
    freshness = db_route.data_freshness_data or {}
    primary_summary = {
        "id": db_route.id,
        "title": "Primary Route",
        "distance_km": db_route.distance_km,
        "duration_minutes": db_route.duration_minutes,
        "hazard_score": db_route.overall_hazard_score,
        "safety_label": db_route.overall_safety_status,
        "is_selected": True,
    }

    return {
        "route_id": db_route.id,
        "primary_route": primary_summary,
        "alternatives": [a.model_dump() for a in alternatives],
        "travel_mode": db_route.travel_mode,
        "comparison_preferences": ["shortest", "fastest", "safest", "facility_access", "balanced"],
        "analyzed_at": db_route.last_analyzed_at.isoformat() if db_route.last_analyzed_at else None,
    }


@router.post("/{route_id}/select-alternative", response_model=PlannedRouteResponse)
async def select_route_alternative(
    route_id: str,
    alternative_id: str = Query(..., description="ID of the alternative to select as primary"),
    db: AsyncSession = Depends(get_db),
):
    """Promotes an alternative route candidate to be the active primary route."""
    stmt = select(PlannedRoute).where(PlannedRoute.id == route_id)
    result = await db.execute(stmt)
    db_route = result.scalar_one_or_none()

    if not db_route:
        raise HTTPException(status_code=404, detail="Route not found")

    alts = db_route.alternatives_data or []
    target_alt = next((a for a in alts if a.get("id") == alternative_id), None)
    if not target_alt:
        raise HTTPException(status_code=404, detail=f"Alternative route '{alternative_id}' not found")

    # Swap primary route geometry and distance/duration with selected alternative
    old_primary_alt = {
        "id": f"alt-prev-{db_route.id[:8]}",
        "title": f"Previous: {db_route.title}",
        "description": f"Previously selected route. {db_route.distance_km} km, ~{int(db_route.duration_minutes)} mins.",
        "distance_km": db_route.distance_km,
        "duration_minutes": db_route.duration_minutes,
        "geometry_geojson": db_route.geometry_geojson,
        "hazard_score": db_route.overall_hazard_score,
        "safety_label": db_route.overall_safety_status,
    }

    new_alts = [a for a in alts if a.get("id") != alternative_id]
    new_alts.append(old_primary_alt)

    db_route.geometry_geojson = target_alt["geometry_geojson"]
    db_route.distance_km = target_alt["distance_km"]
    db_route.duration_minutes = target_alt["duration_minutes"]
    db_route.overall_hazard_score = target_alt.get("hazard_score", 0.0)
    db_route.overall_safety_status = target_alt.get("safety_label", "SAFE")
    db_route.alternatives_data = new_alts
    db_route.route_version = (db_route.route_version or 1) + 1
    db_route.last_analyzed_at = datetime.now(timezone.utc)

    await db.commit()
    await db.refresh(db_route)

    # Re-analyze with new primary geometry
    analysis = await route_condition_analyzer.analyze_persisted_route(
        db=db,
        route_id=route_id,
    )
    try:
        summary_obj = await route_summary_assistant.generate_grounded_summary_async(analysis.model_dump())
        analysis.grounded_summary = summary_obj.model_dump()
    except Exception as e:
        logger.warning(f"Could not generate grounded route summary: {e}")
    return analysis

@router.get("/{route_id}/offline-pack")
async def get_route_offline_pack(
    route_id: str,
    db: AsyncSession = Depends(get_db),
):
    """
    Generates an authoritative Pre-Trek Offline Safety Pack manifest for the specified route.
    Bundles genuine route geometry, corridor boundary, active hazard alerts, fire detections,
    essential facilities, weather snapshot, and freshness timestamps.
    """
    stmt = select(PlannedRoute).where(PlannedRoute.id == route_id)
    result = await db.execute(stmt)
    db_route = result.scalar_one_or_none()

    if not db_route:
        raise HTTPException(status_code=404, detail="Route not found")

    freshness = db_route.data_freshness_data or {}
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(hours=24)

    # Compute bounding box with corridor margin
    origin_lat, origin_lon = db_route.origin_lat, db_route.origin_lon
    dest_lat, dest_lon = db_route.dest_lat, db_route.dest_lon
    corr_km = db_route.corridor_distance_km or 5.0
    margin = (corr_km / 111.0) * 1.5
    min_lat = min(origin_lat, dest_lat) - margin
    max_lat = max(origin_lat, dest_lat) + margin
    min_lon = min(origin_lon, dest_lon) - margin
    max_lon = max(origin_lon, dest_lon) + margin

    return {
        "pack_id": f"pack-{db_route.id[:8]}-v{db_route.route_version or 1}",
        "route_id": db_route.id,
        "route_version": db_route.route_version or 1,
        "title": db_route.title,
        "origin_name": db_route.origin_name,
        "destination_name": db_route.destination_name,
        "origin": {"latitude": origin_lat, "longitude": origin_lon},
        "destination": {"latitude": dest_lat, "longitude": dest_lon},
        "bbox": [round(min_lat, 5), round(min_lon, 5), round(max_lat, 5), round(max_lon, 5)],
        "created_at": now.isoformat(),
        "expires_at": expires_at.isoformat(),
        "is_stale": False,
        "route_geometry": db_route.geometry_geojson,
        "corridor_geojson": freshness.get("corridor_geojson"),
        "hazards_geojson": freshness.get("hazards_geojson") or (db_route.analysis_data or {}).get("hazards_geojson") or {"type": "FeatureCollection", "features": []},
        "fires_geojson": freshness.get("fires_geojson") or (db_route.analysis_data or {}).get("fires_geojson") or {"type": "FeatureCollection", "features": []},
        "facilities_geojson": freshness.get("facilities_geojson") or (db_route.analysis_data or {}).get("facilities_geojson") or {"type": "FeatureCollection", "features": []},
        "terrain_slope_geojson": (db_route.analysis_data or {}).get("terrain_slope_geojson") or {"type": "FeatureCollection", "features": []},
        "cellular_sites_geojson": (db_route.analysis_data or {}).get("cellular_sites_geojson") or {"type": "FeatureCollection", "features": []},
        "cellular_coverage_geojson": (db_route.analysis_data or {}).get("cellular_coverage_geojson") or {"type": "FeatureCollection", "features": []},
        "waterways_geojson": (db_route.analysis_data or {}).get("waterways_geojson") or {"type": "FeatureCollection", "features": []},
        "historical_landslides_geojson": (db_route.analysis_data or {}).get("historical_landslides_geojson") or {"type": "FeatureCollection", "features": []},
        "terrain_exposure": (db_route.analysis_data or {}).get("terrain_exposure"),
        "cellular_summary": (db_route.analysis_data or {}).get("cellular_summary"),
        "weather_snapshot": [c.get("weather") for c in (db_route.conditions_data or []) if c.get("weather")],
        "conditions": db_route.conditions_data or [],
        "alternatives": db_route.alternatives_data or [],
        "data_provenance": {
            "routing": "OSRM (OpenStreetMap contributors)",
            "disasters": "NDMA SACHET CAP 1.2 Feed",
            "weather": "India Meteorological Department (IMD) / Open-Meteo",
            "wildfires": "NASA FIRMS NRT (MODIS/VIIRS)",
            "facilities": "OpenStreetMap Contributors under ODbL",
            "elevation": "Copernicus DEM 30m Grid",
            "cellular": "OpenCelliD Project (CC BY-SA 4.0)",
            "landslides": "Geological Survey of India (GSI) Bhukosh / USDMA",
            "waterways": "OpenStreetMap Waterways / NRSC Glacial Lake Atlas",
        },
        "sha256_checksum": hashlib.sha256(
            json.dumps(db_route.geometry_geojson or {}, sort_keys=True).encode("utf-8")
        ).hexdigest(),
        "offline_usage_disclaimer": (
            "Pre-trek snapshot saved for offline mountain navigation. "
            "New disaster warnings, weather changes, and road closures cannot be received without active network coverage. "
            "Treat cached data as advisory historical snapshot."
        ),
    }

