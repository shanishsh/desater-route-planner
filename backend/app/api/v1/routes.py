"""Route Planning and Multi-Hazard Corridor Safety Scanning API."""
from typing import List, Dict, Any, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database.session import get_db
from app.models.planned_route import PlannedRoute
from app.services.routing.osrm_provider import osrm_routing_provider
from app.services.route_condition_analyzer import route_condition_analyzer
from app.schemas.route_plan import (
    PlanRouteRequest,
    PlannedRouteResponse,
    RouteWaypointCondition,
    RouteAlternativeSchema,
    RouteAnalysisRequest,
    RouteAnalysisResponse,
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

    # 1. Calculate route geometry and waypoints
    route_result = await osrm_routing_provider.calculate_route(
        origin_lat=request.origin_lat,
        origin_lon=request.origin_lon,
        origin_name=request.origin_name or "Current Location",
        dest_lat=request.destination_lat,
        dest_lon=request.destination_lon,
        dest_name=request.destination_name,
        travel_mode=request.travel_mode or "driving",
    )

    # 2. Analyze corridor conditions (weather at arrival times, fires, forests, disaster alerts)
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
    )
    db.add(db_route)
    await db.commit()
    await db.refresh(db_route)

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
    return analysis


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

