"""Route Planning and Multi-Hazard Corridor Safety Scanning API."""
from typing import List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, status
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
    analysis = await route_condition_analyzer.analyze_route(
        db=db,
        route=route_result,
        departure_time=request.departure_time,
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
        conditions_data=[c.model_dump() for c in analysis.conditions],
        alternatives_data=[a.model_dump() for a in analysis.alternatives],
        data_freshness_data=analysis.data_freshness,
    )
    db.add(db_route)
    await db.commit()
    await db.refresh(db_route)

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
