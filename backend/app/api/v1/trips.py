"""Active Trip Tracking and Progress Monitoring API."""
import math
from datetime import datetime, timezone
from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from sqlalchemy.orm import selectinload
from app.database.session import get_db
from app.models.planned_route import PlannedRoute, ActiveTrip
from app.services.routing.base import haversine_distance_km
from app.schemas.route_plan import (
    StartTripRequest,
    StartTripResponse,
    TripProgressRequest,
    TripProgressResponse,
    TripStatusResponse,
    TripSummaryResponse,
    RouteWaypointCondition,
    TrackPointsSyncRequest,
    TrackPointsSyncResponse,
)

router = APIRouter(prefix="/trips", tags=["Active Trip Monitoring"])


def _parse_waypoints(conditions_data: list) -> list:
    waypoints = []
    for idx, c in enumerate(conditions_data):
        if isinstance(c, dict):
            c_copy = dict(c)
            if "index" not in c_copy:
                c_copy["index"] = c_copy.get("waypoint_index", idx)
            try:
                waypoints.append(RouteWaypointCondition(**c_copy))
            except Exception:
                try:
                    c_copy["weather"] = None
                    waypoints.append(RouteWaypointCondition(**c_copy))
                except Exception:
                    pass
    return waypoints


@router.get("", response_model=List[TripSummaryResponse])
async def list_trips(
    status: Optional[str] = None,
    limit: int = 50,
    db: AsyncSession = Depends(get_db),
):
    """Retrieves all tracked trips with associated route summaries (active and completed)."""
    stmt = (
        select(ActiveTrip)
        .options(selectinload(ActiveTrip.route))
        .order_by(ActiveTrip.started_at.desc())
        .limit(min(limit, 100))
    )
    if status:
        stmt = stmt.where(ActiveTrip.status == status.upper())

    result = await db.execute(stmt)
    trips = result.scalars().all()

    summaries = []
    for t in trips:
        r = t.route
        summaries.append(
            TripSummaryResponse(
                trip_id=t.id,
                route_id=t.route_id,
                status=t.status,
                started_at=t.started_at.isoformat() if t.started_at else "",
                completed_at=t.completed_at.isoformat() if t.completed_at else None,
                route_title=r.title if r else None,
                origin_name=r.origin_name if r else None,
                destination_name=r.destination_name if r else None,
                travel_mode=r.travel_mode if r else None,
                distance_km=r.total_distance_km if r else None,
                last_latitude=t.last_latitude,
                last_longitude=t.last_longitude,
                progress_pct=t.progress_pct,
            )
        )
    return summaries


@router.post("/start", response_model=StartTripResponse, status_code=status.HTTP_201_CREATED)
async def start_trip(
    request: StartTripRequest,
    db: AsyncSession = Depends(get_db),
):
    """Starts active monitoring for a planned route."""
    stmt = select(PlannedRoute).where(PlannedRoute.id == request.route_id)
    result = await db.execute(stmt)
    db_route = result.scalar_one_or_none()

    if not db_route:
        raise HTTPException(status_code=404, detail="Planned route not found")

    now = datetime.now(timezone.utc)
    trip = ActiveTrip(
        route_id=db_route.id,
        user_id=request.user_id,
        device_id=request.device_id,
        status="ACTIVE",
        started_at=now,
        last_latitude=db_route.origin_lat,
        last_longitude=db_route.origin_lon,
        current_segment_index=0,
        progress_pct=0.0,
        last_synced_at=now,
    )
    db.add(trip)
    await db.commit()
    await db.refresh(trip)

    return StartTripResponse(
        trip_id=trip.id,
        route_id=trip.route_id,
        status=trip.status,
        started_at=trip.started_at.isoformat(),
        message="Active route monitoring initiated.",
    )


@router.post("/{trip_id}/progress", response_model=TripProgressResponse)
async def record_trip_progress(
    trip_id: str,
    progress: TripProgressRequest,
    db: AsyncSession = Depends(get_db),
):
    """
    Records GPS progress along the route, recalculates distance to upcoming hazards,
    detects path deviation (> 500m off-route), and suggests alternatives if hazards emerge.
    """
    stmt = select(ActiveTrip).where(ActiveTrip.id == trip_id)
    result = await db.execute(stmt)
    trip = result.scalar_one_or_none()

    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")

    # Load route
    route_stmt = select(PlannedRoute).where(PlannedRoute.id == trip.route_id)
    route_result = await db.execute(route_stmt)
    route = route_result.scalar_one_or_none()

    if not route:
        raise HTTPException(status_code=404, detail="Associated route not found")

    conditions_data = route.conditions_data or []
    waypoints = _parse_waypoints(conditions_data)

    # Find closest waypoint
    closest_idx = 0
    min_dist_to_wp = 999.0
    for idx, wp in enumerate(waypoints):
        d = haversine_distance_km(progress.latitude, progress.longitude, wp.latitude, wp.longitude)
        if d < min_dist_to_wp:
            min_dist_to_wp = d
            closest_idx = idx

    is_off_route = min_dist_to_wp > 0.6  # > 600m away from any planned waypoint

    # Remaining distance to destination
    dest_dist = haversine_distance_km(
        progress.latitude, progress.longitude, route.dest_lat, route.dest_lon
    )
    total_dist = max(0.1, route.distance_km)
    progress_pct = max(0.0, min(100.0, round(((total_dist - dest_dist) / total_dist) * 100.0, 1)))

    # Scan upcoming waypoints for hazards
    nearest_hazard_dist = None
    nearest_hazard_title = None
    hazard_warning_level = "LOW"
    reroute_suggested = False
    suggested_alt_id = None

    upcoming_wps = waypoints[closest_idx:]
    for wp in upcoming_wps:
        if wp.hazard_level in ["HIGH", "CRITICAL"]:
            dist_to_hazard = haversine_distance_km(
                progress.latitude, progress.longitude, wp.latitude, wp.longitude
            )
            if nearest_hazard_dist is None or dist_to_hazard < nearest_hazard_dist:
                nearest_hazard_dist = round(dist_to_hazard, 2)
                # Pick title from alerts or weather
                if wp.alerts:
                    nearest_hazard_title = wp.alerts[0].title
                elif wp.wildfire and wp.wildfire.has_fire_within_5km:
                    nearest_hazard_title = f"Satellite Active Fire near {wp.name}"
                else:
                    weather_desc = wp.weather.weather_description if wp.weather else "Severe condition"
                    nearest_hazard_title = f"{weather_desc} at {wp.name}"
                hazard_warning_level = wp.hazard_level

            # If approaching hazard within 4 km, suggest reroute
            if dist_to_hazard <= 4.0:
                reroute_suggested = True
                alternatives = route.alternatives_data or []
                if alternatives:
                    suggested_alt_id = alternatives[0].get("id")

    # Next waypoint condition
    next_wp = waypoints[min(len(waypoints) - 1, closest_idx + 1)] if waypoints else None

    # Update trip in DB
    now = datetime.now(timezone.utc)
    trip.last_latitude = progress.latitude
    trip.last_longitude = progress.longitude
    trip.current_segment_index = closest_idx
    trip.progress_pct = progress_pct
    trip.deviation_detected = is_off_route
    trip.reroute_suggested = reroute_suggested
    trip.last_synced_at = now

    # Auto complete if close to destination
    if dest_dist <= 0.15:
        trip.status = "COMPLETED"
        trip.completed_at = now

    await db.commit()

    message = (
        "Approaching destination."
        if trip.status == "COMPLETED"
        else (
            f"Caution: Approaching {nearest_hazard_title} in {nearest_hazard_dist} km"
            if nearest_hazard_dist and nearest_hazard_dist <= 4.0
            else ("Off planned route" if is_off_route else "On route. Conditions normal.")
        )
    )

    return TripProgressResponse(
        trip_id=trip.id,
        status=trip.status,
        current_segment_index=closest_idx,
        progress_pct=progress_pct,
        distance_remaining_km=round(dest_dist, 2),
        distance_to_nearest_hazard_km=nearest_hazard_dist,
        nearest_hazard_title=nearest_hazard_title,
        hazard_warning_level=hazard_warning_level,
        is_off_route=is_off_route,
        reroute_suggested=reroute_suggested,
        suggested_alternative_id=suggested_alt_id,
        next_waypoint=next_wp,
        message=message,
    )


@router.post("/{trip_id}/sync-points", response_model=TrackPointsSyncResponse)
async def sync_trip_points(
    trip_id: str,
    payload: TrackPointsSyncRequest,
    db: AsyncSession = Depends(get_db),
):
    """
    Synchronizes a batch of queued offline GPS track points for an active trip.
    Computes sequential distance, updates progress and proximity to upcoming hazards,
    and returns authoritative tracking state.
    """
    stmt = select(ActiveTrip).where(ActiveTrip.id == trip_id)
    result = await db.execute(stmt)
    trip = result.scalar_one_or_none()

    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")

    # Load associated route
    route_stmt = select(PlannedRoute).where(PlannedRoute.id == trip.route_id)
    route_result = await db.execute(route_stmt)
    route = route_result.scalar_one_or_none()

    if not route:
        raise HTTPException(status_code=404, detail="Associated route not found")

    points = payload.points
    synced_count = len(points)

    # If no points submitted, return current status
    if not points:
        dest_dist = (
            haversine_distance_km(trip.last_latitude, trip.last_longitude, route.dest_lat, route.dest_lon)
            if trip.last_latitude and trip.last_longitude
            else route.distance_km
        )
        return TrackPointsSyncResponse(
            trip_id=trip.id,
            status=trip.status,
            synced_count=0,
            traveled_distance_km=0.0,
            progress_pct=trip.progress_pct,
            distance_remaining_km=round(dest_dist, 2),
            distance_to_nearest_hazard_km=None,
            nearest_hazard_title=None,
            hazard_warning_level="LOW",
            is_off_route=trip.deviation_detected,
            message="No points to sync; current state returned.",
        )

    # Accumulate sequential distance across the batch
    batch_distance_km = 0.0
    for i in range(1, len(points)):
        p1 = points[i - 1]
        p2 = points[i]
        d = haversine_distance_km(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        # Filter stationary drift (< 2.5m) and impossible jumps (> 1.5km within consecutive points)
        if 0.0025 <= d <= 1.5:
            batch_distance_km += d

    # Latest point represents current trekker position
    latest_pt = points[-1]
    conditions_data = route.conditions_data or []
    waypoints = _parse_waypoints(conditions_data)

    # Find closest waypoint along route
    closest_idx = 0
    min_dist_to_wp = 999.0
    for idx, wp in enumerate(waypoints):
        d = haversine_distance_km(latest_pt.latitude, latest_pt.longitude, wp.latitude, wp.longitude)
        if d < min_dist_to_wp:
            min_dist_to_wp = d
            closest_idx = idx

    is_off_route = min_dist_to_wp > 0.6  # > 600m off planned route corridor

    # Remaining distance to destination
    dest_dist = haversine_distance_km(
        latest_pt.latitude, latest_pt.longitude, route.dest_lat, route.dest_lon
    )
    total_dist = max(0.1, route.distance_km)
    progress_pct = max(0.0, min(100.0, round(((total_dist - dest_dist) / total_dist) * 100.0, 1)))

    # Scan upcoming waypoints for hazards
    nearest_hazard_dist = None
    nearest_hazard_title = None
    hazard_warning_level = "LOW"

    upcoming_wps = waypoints[closest_idx:]
    for wp in upcoming_wps:
        if wp.hazard_level in ["HIGH", "CRITICAL"]:
            dist_to_hazard = haversine_distance_km(
                latest_pt.latitude, latest_pt.longitude, wp.latitude, wp.longitude
            )
            if nearest_hazard_dist is None or dist_to_hazard < nearest_hazard_dist:
                nearest_hazard_dist = round(dist_to_hazard, 2)
                if wp.alerts:
                    nearest_hazard_title = wp.alerts[0].title
                elif wp.wildfire and wp.wildfire.has_fire_within_5km:
                    nearest_hazard_title = f"Satellite Active Fire near {wp.name}"
                else:
                    weather_desc = wp.weather.weather_description if wp.weather else "Severe condition"
                    nearest_hazard_title = f"{weather_desc} at {wp.name}"
                hazard_warning_level = wp.hazard_level

    # Update trip in database
    now = datetime.now(timezone.utc)
    trip.last_latitude = latest_pt.latitude
    trip.last_longitude = latest_pt.longitude
    trip.current_segment_index = closest_idx
    trip.progress_pct = progress_pct
    trip.deviation_detected = is_off_route
    trip.last_synced_at = now

    # Auto complete if close to destination
    if dest_dist <= 0.15:
        trip.status = "COMPLETED"
        trip.completed_at = now

    await db.commit()

    return TrackPointsSyncResponse(
        trip_id=trip.id,
        status=trip.status,
        synced_count=synced_count,
        traveled_distance_km=round(batch_distance_km, 2),
        progress_pct=progress_pct,
        distance_remaining_km=round(dest_dist, 2),
        distance_to_nearest_hazard_km=nearest_hazard_dist,
        nearest_hazard_title=nearest_hazard_title,
        hazard_warning_level=hazard_warning_level,
        is_off_route=is_off_route,
        message=f"Successfully synchronized {synced_count} queued track points.",
    )


@router.get("/{trip_id}/status", response_model=TripStatusResponse)
async def get_trip_status(
    trip_id: str,
    db: AsyncSession = Depends(get_db),
):
    """Retrieves current trip status and tracking metrics."""
    stmt = select(ActiveTrip).where(ActiveTrip.id == trip_id)
    result = await db.execute(stmt)
    trip = result.scalar_one_or_none()

    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")

    # Check route safety
    route_stmt = select(PlannedRoute).where(PlannedRoute.id == trip.route_id)
    route_result = await db.execute(route_stmt)
    route = route_result.scalar_one_or_none()
    safety_status = route.overall_safety_status if route else "SAFE"

    return TripStatusResponse(
        trip_id=trip.id,
        route_id=trip.route_id,
        status=trip.status,
        started_at=trip.started_at.isoformat(),
        last_latitude=trip.last_latitude,
        last_longitude=trip.last_longitude,
        progress_pct=trip.progress_pct,
        current_segment_index=trip.current_segment_index,
        overall_safety_status=safety_status,
        completed_at=trip.completed_at.isoformat() if trip.completed_at else None,
    )


@router.post("/{trip_id}/stop")
async def stop_trip(
    trip_id: str,
    db: AsyncSession = Depends(get_db),
):
    """Stops active tracking for the trip."""
    stmt = select(ActiveTrip).where(ActiveTrip.id == trip_id)
    result = await db.execute(stmt)
    trip = result.scalar_one_or_none()

    if not trip:
        raise HTTPException(status_code=404, detail="Trip not found")

    trip.status = "COMPLETED"
    trip.completed_at = datetime.now(timezone.utc)
    await db.commit()

    return {"message": "Trip monitoring terminated successfully.", "trip_id": trip_id, "status": "COMPLETED"}

