"""Service for analyzing environmental conditions and multi-hazard risks along a route corridor."""
import math
import logging
from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any, Optional, Tuple
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.clients.open_meteo import open_meteo_client
from app.clients.osm_forest import osm_forest_client
from app.clients.osm_amenities import osm_amenities_client
from app.clients.nasa_firms import nasa_firms_client
from app.models.disaster_alert import DisasterAlert
from app.services.routing.base import RouteResult, haversine_distance_km
from app.services.terrain.elevation_service import elevation_service
from app.services.risk.trailguard_risk_engine import trailguard_risk_engine
from app.schemas.route_plan import (
    RouteWaypointCondition,
    RouteWeatherCondition,
    RouteForestInfo,
    RouteWildfireInfo,
    RouteDisasterAlertInfo,
    RouteAlternativeSchema,
    PlannedRouteResponse,
    ElevationProfilePoint,
    SafeHavenInfo,
)

logger = logging.getLogger(__name__)


class RouteConditionAnalyzer:
    """Analyzes weather, DEM elevation slope, active wildfires, and disaster alerts using TrailGuard intelligence."""

    async def analyze_route(
        self,
        db: AsyncSession,
        route: RouteResult,
        departure_time: Optional[datetime] = None,
    ) -> PlannedRouteResponse:
        """Evaluates segment-by-segment conditions and computes TrailGuard mountain hazard index."""
        now = datetime.now(timezone.utc)
        dep_time = departure_time or now

        # Extract coordinates for elevation profile and spatial bounding box
        raw_coords = route.geometry_geojson.get("coordinates", [])
        if raw_coords:
            # GeoJSON coordinates are [lon, lat]
            route_coords_lat_lon = [(pt[1], pt[0]) for pt in raw_coords]
        else:
            route_coords_lat_lon = [(wp.latitude, wp.longitude) for wp in route.waypoints]

        # Sample coordinates for elevation profile (max 100 points for performance)
        sample_step = max(1, len(route_coords_lat_lon) // 80)
        sampled_coords = [
            route_coords_lat_lon[i] for i in range(0, len(route_coords_lat_lon), sample_step)
        ]
        if sampled_coords[-1] != route_coords_lat_lon[-1]:
            sampled_coords.append(route_coords_lat_lon[-1])

        # 1. Compute DEM Elevation Profile and Tobler hiking speeds
        elevation_data = await elevation_service.compute_elevation_profile(
            sampled_coords, departure_time=dep_time
        )
        elev_points = elevation_data.get("points", [])

        # Compute bounding box of the entire route for spatial queries
        lats = [pt[0] for pt in route_coords_lat_lon]
        lons = [pt[1] for pt in route_coords_lat_lon]
        min_lat = min(lats) - 0.08
        max_lat = max(lats) + 0.08
        min_lon = min(lons) - 0.08
        max_lon = max(lons) + 0.08

        # 2. Query active disaster alerts from DB
        stmt = select(DisasterAlert).where(
            DisasterAlert.status.in_(["active", "updated"]),
            DisasterAlert.center_latitude >= min_lat - 0.1,
            DisasterAlert.center_latitude <= max_lat + 0.1,
            DisasterAlert.center_longitude >= min_lon - 0.1,
            DisasterAlert.center_longitude <= max_lon + 0.1,
        )
        result = await db.execute(stmt)
        active_alerts: List[DisasterAlert] = list(result.scalars().all())

        # 3. Query forest data along corridor
        forest_features = []
        try:
            forest_data = await osm_forest_client.fetch_forest_features(min_lat, min_lon, max_lat, max_lon)
            if forest_data and "features" in forest_data:
                forest_features = forest_data["features"]
        except Exception as e:
            logger.warning(f"Error querying forest data for route: {e}")

        # 4. Query NASA FIRMS fire detections along corridor
        active_fires = []
        try:
            firms_data = await nasa_firms_client.fetch_active_fires(min_lat, min_lon, max_lat, max_lon)
            if firms_data and "records" in firms_data:
                active_fires = firms_data["records"]
        except Exception as e:
            logger.warning(f"Error querying FIRMS fire data for route: {e}")

        # 5. Query Emergency Safe Havens (hospitals, shelters, police, mountain posts)
        safe_havens: List[SafeHavenInfo] = []
        try:
            amenity_items = await osm_amenities_client.fetch_amenities(min_lat, min_lon, max_lat, max_lon)
            for item in amenity_items:
                a_lat = item.get("latitude", 0.0)
                a_lon = item.get("longitude", 0.0)
                # Find min distance to route
                min_route_dist = min(
                    haversine_distance_km(a_lat, a_lon, wp.latitude, wp.longitude)
                    for wp in route.waypoints
                )
                if min_route_dist <= 8.0:
                    safe_havens.append(
                        SafeHavenInfo(
                            name=item.get("name", "Emergency Assistance Point"),
                            category=item.get("category", "shelter"),
                            latitude=a_lat,
                            longitude=a_lon,
                            distance_km=round(min_route_dist, 2),
                            is_egress_point=True,
                        )
                    )
            # Sort by proximity
            safe_havens.sort(key=lambda sh: sh.distance_km)
            safe_havens = safe_havens[:10]
        except Exception as e:
            logger.warning(f"Error querying Safe Havens: {e}")

        # Analyze each waypoint with terrain and TrailGuard Risk Engine
        waypoint_conditions: List[RouteWaypointCondition] = []
        segment_evaluations: List[Dict[str, Any]] = []

        for wp in route.waypoints:
            # Find closest elevation profile point to estimate elevation and slope
            closest_elev_pt = None
            min_dist_to_elev = 999.0
            for ep in elev_points:
                d = haversine_distance_km(wp.latitude, wp.longitude, ep["latitude"], ep["longitude"])
                if d < min_dist_to_elev:
                    min_dist_to_elev = d
                    closest_elev_pt = ep

            wp_elevation = closest_elev_pt["elevation_m"] if closest_elev_pt else 0.0
            wp_slope_deg = closest_elev_pt["slope_degrees"] if closest_elev_pt else 0.0

            # Compute estimated arrival time based on Tobler hiking speed if available
            if closest_elev_pt and "cumulative_minutes" in closest_elev_pt:
                arr_time = dep_time + timedelta(minutes=closest_elev_pt["cumulative_minutes"])
            else:
                arr_time = dep_time + timedelta(minutes=wp.estimated_travel_time_minutes)

            # Weather forecast at calculated arrival time
            w_dict = await open_meteo_client.get_forecast_for_waypoint(wp.latitude, wp.longitude, arr_time)
            weather_cond = RouteWeatherCondition(
                temperature_c=w_dict["temperature_c"],
                relative_humidity_pct=w_dict["relative_humidity_pct"],
                precipitation_probability_pct=w_dict["precipitation_probability_pct"],
                precipitation_mm=w_dict["precipitation_mm"],
                wind_speed_kmh=w_dict["wind_speed_kmh"],
                weather_code=w_dict["weather_code"],
                weather_description=w_dict["weather_description"],
                warning_level=w_dict["warning_level"],
                forecast_valid_at=w_dict["forecast_valid_at"],
                source=w_dict["source"],
                is_simulated=w_dict.get("is_simulated", False),
            )

            # Forest proximity
            forest_info = RouteForestInfo(in_forest=False)
            if forest_features:
                min_f_dist = 999.0
                f_name = None
                f_type = None
                for feat in forest_features[:20]:
                    geom = feat.get("geometry", {})
                    props = feat.get("properties", {})
                    f_coords = geom.get("coordinates", [])
                    if geom.get("type") == "Point" and len(f_coords) >= 2:
                        d = haversine_distance_km(wp.latitude, wp.longitude, f_coords[1], f_coords[0])
                        if d < min_f_dist:
                            min_f_dist = d
                            f_name = props.get("name")
                            f_type = props.get("type") or "forest"
                    elif geom.get("type") == "Polygon" and f_coords and f_coords[0]:
                        poly_pt = f_coords[0][0]
                        d = haversine_distance_km(wp.latitude, wp.longitude, poly_pt[1], poly_pt[0])
                        if d < min_f_dist:
                            min_f_dist = d
                            f_name = props.get("name")
                            f_type = props.get("type") or "forest"

                if min_f_dist <= 1.2:
                    forest_info = RouteForestInfo(
                        in_forest=True,
                        forest_name=f_name or "Mapped Forest Area",
                        forest_type=f_type or "woodland",
                        distance_to_edge_m=round(min_f_dist * 1000.0, 1),
                    )

            # Wildfire proximity
            wildfire_info = RouteWildfireInfo(has_fire_within_5km=False)
            wildfire_eval_dict = {"has_fire_within_5km": False}
            if active_fires:
                min_fire_dist = 999.0
                closest_fire = None
                for fire in active_fires:
                    f_lat = fire.get("latitude", 0.0)
                    f_lon = fire.get("longitude", 0.0)
                    d = haversine_distance_km(wp.latitude, wp.longitude, f_lat, f_lon)
                    if d < min_fire_dist:
                        min_fire_dist = d
                        closest_fire = fire

                if min_fire_dist <= 5.0 and closest_fire:
                    acq_str = f"{closest_fire.get('acquisition_date', '')} {closest_fire.get('acquisition_time', '')}".strip()
                    frp_val = closest_fire.get("frp_mw") or closest_fire.get("frp")
                    wildfire_info = RouteWildfireInfo(
                        has_fire_within_5km=True,
                        nearest_fire_distance_km=round(min_fire_dist, 2),
                        fire_radiative_power=frp_val,
                        confidence=str(closest_fire.get("confidence", "nominal")),
                        acquisition_time=acq_str if acq_str else "NRT",
                    )
                    wildfire_eval_dict = {
                        "has_fire_within_5km": True,
                        "nearest_fire_distance_km": min_fire_dist,
                        "fire_radiative_power": frp_val,
                        "confidence": str(closest_fire.get("confidence", "nominal")),
                    }

            # Disaster alerts intersection
            matched_alerts: List[RouteDisasterAlertInfo] = []
            alerts_for_engine: List[Dict[str, Any]] = []
            for alert in active_alerts:
                dist_km = haversine_distance_km(
                    wp.latitude, wp.longitude, alert.center_latitude, alert.center_longitude
                )
                radius_km = (alert.affected_radius_meters or 5000.0) / 1000.0
                if dist_km <= radius_km:
                    matched_alerts.append(
                        RouteDisasterAlertInfo(
                            alert_id=alert.id,
                            title=alert.title,
                            hazard_category=alert.hazard_category,
                            severity=alert.severity,
                            distance_to_alert_km=round(dist_km, 2),
                            required_protective_instructions=alert.required_protective_instructions,
                            is_simulated=alert.is_simulated,
                        )
                    )
                    alerts_for_engine.append(
                        {"title": alert.title, "severity": alert.severity, "category": alert.hazard_category}
                    )

            # TrailGuard Multi-Factor Risk Assessment
            seg_risk = trailguard_risk_engine.evaluate_segment_risk(
                weather={
                    "precipitation_mm": weather_cond.precipitation_mm,
                    "precipitation_probability_pct": weather_cond.precipitation_probability_pct,
                    "wind_speed_kmh": weather_cond.wind_speed_kmh,
                    "temperature_c": weather_cond.temperature_c,
                    "warning_level": weather_cond.warning_level,
                },
                slope_deg=wp_slope_deg,
                elevation_m=wp_elevation,
                alerts=alerts_for_engine,
                wildfire=wildfire_eval_dict,
            )
            segment_evaluations.append(seg_risk)

            waypoint_conditions.append(
                RouteWaypointCondition(
                    index=wp.index,
                    name=wp.name,
                    latitude=wp.latitude,
                    longitude=wp.longitude,
                    distance_from_start_km=wp.distance_from_start_km,
                    estimated_arrival_at=arr_time.isoformat(),
                    hazard_level=seg_risk["risk_level"],
                    elevation_m=wp_elevation,
                    slope_degrees=wp_slope_deg,
                    weather=weather_cond,
                    forest=forest_info,
                    wildfire=wildfire_info,
                    alerts=matched_alerts,
                    risk_breakdown=seg_risk["breakdown"],
                    contributing_factors=seg_risk["contributing_factors"],
                )
            )

        # Aggregate overall route risk
        agg_risk = trailguard_risk_engine.aggregate_route_risk(segment_evaluations)

        # Prepare alternatives with comparative real hazard scoring
        alt_schemas: List[RouteAlternativeSchema] = []
        for alt in route.alternatives:
            alt_score = 10.0
            alt_hazards: List[str] = []

            alt_coords = alt.geometry_geojson.get("coordinates", [])
            step = max(1, len(alt_coords) // 20)
            sampled_alt_pts = [alt_coords[i] for i in range(0, len(alt_coords), step)]

            for pt in sampled_alt_pts:
                p_lon, p_lat = pt[0], pt[1]
                for alert in active_alerts:
                    d_km = haversine_distance_km(p_lat, p_lon, alert.center_latitude, alert.center_longitude)
                    r_km = (alert.affected_radius_meters or 5000.0) / 1000.0
                    if d_km <= r_km:
                        if alert.severity == "CRITICAL":
                            alt_score = max(alt_score, 90.0)
                            alt_hazards.append(f"Critical: {alert.title}")
                        elif alert.severity == "SEVERE":
                            alt_score = max(alt_score, 75.0)
                            alt_hazards.append(f"Severe: {alert.title}")
                        else:
                            alt_score = max(alt_score, 45.0)
                            alt_hazards.append(f"Warning: {alert.title}")
                for fire in active_fires:
                    f_lat = fire.get("latitude", 0.0)
                    f_lon = fire.get("longitude", 0.0)
                    d_f = haversine_distance_km(p_lat, p_lon, f_lat, f_lon)
                    if d_f <= 2.0:
                        alt_score = max(alt_score, 85.0)
                        alt_hazards.append("Active wildfire within 2 km")
                    elif d_f <= 5.0:
                        alt_score = max(alt_score, 60.0)

            if alt_score >= 70.0:
                safety_label = f"High Risk: {len(alt_hazards)} hazards detected"
            elif alt_score >= 35.0:
                safety_label = "Caution: Secondary hazards nearby"
            elif agg_risk["overall_score"] >= 50.0 and alt_score < 30.0:
                safety_label = "Recommended: Bypasses hazard corridor (Safest Path)"
            else:
                safety_label = "Clear & Safe Alternative"

            alt_schemas.append(
                RouteAlternativeSchema(
                    id=alt.id,
                    title=alt.title,
                    description=alt.description,
                    distance_km=alt.distance_km,
                    duration_minutes=alt.duration_minutes,
                    geometry_geojson=alt.geometry_geojson,
                    hazard_score=round(alt_score, 1),
                    safety_label=safety_label,
                )
            )

        # Generate contrastive explanation
        decision_explanation = trailguard_risk_engine.generate_contrastive_explanation(
            agg_risk, segment_evaluations, [a.model_dump() for a in alt_schemas]
        )

        elevation_profile_points = [
            ElevationProfilePoint(
                index=p["index"],
                latitude=p["latitude"],
                longitude=p["longitude"],
                elevation_m=p["elevation_m"],
                distance_from_start_km=p["distance_from_start_km"],
                slope_degrees=p["slope_degrees"],
                hiking_speed_kmh=p["hiking_speed_kmh"],
                cumulative_minutes=p["cumulative_minutes"],
                estimated_arrival_iso=p["estimated_arrival_iso"],
            )
            for p in elev_points
        ]

        freshness = {
            "weather_provider": "Open-Meteo Hourly Forecast",
            "weather_retrieved_at": now.isoformat(),
            "elevation_provider": "Copernicus DEM (Open-Meteo 30m Grid)",
            "forest_provider": "OpenStreetMap Overpass",
            "wildfire_provider": "NASA FIRMS (MODIS/VIIRS)",
            "disaster_alerts_active_count": len(active_alerts),
            "is_stale": False,
            "offline_cached": False,
        }

        return PlannedRouteResponse(
            route_id=route.route_id,
            title=route.title,
            origin_name=route.origin_name,
            origin_lat=route.origin_lat,
            origin_lon=route.origin_lon,
            destination_name=route.destination_name,
            dest_lat=route.destination_lat,
            dest_lon=route.destination_lon,
            travel_mode=route.travel_mode,
            distance_km=route.distance_km,
            duration_minutes=route.duration_minutes,
            geometry_geojson=route.geometry_geojson,
            overall_hazard_score=agg_risk["overall_score"],
            overall_safety_status=agg_risk["overall_status"],
            high_risk_segments_count=agg_risk["critical_segments_count"],
            total_ascent_m=elevation_data.get("total_ascent_m", 0.0),
            total_descent_m=elevation_data.get("total_descent_m", 0.0),
            min_elevation_m=elevation_data.get("min_elevation_m", 0.0),
            max_elevation_m=elevation_data.get("max_elevation_m", 0.0),
            decision_explanation=decision_explanation,
            conditions=waypoint_conditions,
            elevation_profile=elevation_profile_points,
            safe_havens=safe_havens,
            alternatives=alt_schemas,
            data_freshness=freshness,
            created_at=now.isoformat(),
        )


route_condition_analyzer = RouteConditionAnalyzer()
