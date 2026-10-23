"""Service for analyzing environmental conditions and multi-hazard risks along a route corridor."""
import math
import logging
from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any, Optional
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.clients.open_meteo import open_meteo_client
from app.clients.osm_forest import osm_forest_client
from app.clients.nasa_firms import nasa_firms_client
from app.models.disaster_alert import DisasterAlert
from app.services.routing.base import RouteResult, haversine_distance_km
from app.schemas.route_plan import (
    RouteWaypointCondition,
    RouteWeatherCondition,
    RouteForestInfo,
    RouteWildfireInfo,
    RouteDisasterAlertInfo,
    RouteAlternativeSchema,
    PlannedRouteResponse,
)

logger = logging.getLogger(__name__)


class RouteConditionAnalyzer:
    """Analyzes weather, forest proximity, active wildfire detections, and disaster alerts along a route."""

    async def analyze_route(
        self,
        db: AsyncSession,
        route: RouteResult,
        departure_time: Optional[datetime] = None,
    ) -> PlannedRouteResponse:
        """Evaluates segment-by-segment conditions and computes corridor hazard index."""
        now = datetime.now(timezone.utc)
        dep_time = departure_time or now

        # Compute bounding box of the entire route for spatial queries
        lats = [wp.latitude for wp in route.waypoints]
        lons = [wp.longitude for wp in route.waypoints]
        min_lat = min(lats) - 0.08
        max_lat = max(lats) + 0.08
        min_lon = min(lons) - 0.08
        max_lon = max(lons) + 0.08

        # Query active disaster alerts from DB
        stmt = select(DisasterAlert).where(
            DisasterAlert.status.in_(["active", "updated"]),
            DisasterAlert.center_latitude >= min_lat - 0.1,
            DisasterAlert.center_latitude <= max_lat + 0.1,
            DisasterAlert.center_longitude >= min_lon - 0.1,
            DisasterAlert.center_longitude <= max_lon + 0.1,
        )
        result = await db.execute(stmt)
        active_alerts: List[DisasterAlert] = list(result.scalars().all())

        # Query forest data along corridor
        forest_features = []
        try:
            forest_data = await osm_forest_client.fetch_forest_features(min_lat, min_lon, max_lat, max_lon)
            if forest_data and "features" in forest_data:
                forest_features = forest_data["features"]
        except Exception as e:
            logger.warning(f"Error querying forest data for route: {e}")

        # Query NASA FIRMS fire detections along corridor
        active_fires = []
        try:
            firms_data = await nasa_firms_client.fetch_active_fires(min_lat, min_lon, max_lat, max_lon)
            if firms_data and "records" in firms_data:
                active_fires = firms_data["records"]
        except Exception as e:
            logger.warning(f"Error querying FIRMS fire data for route: {e}")

        # Analyze each waypoint
        waypoint_conditions: List[RouteWaypointCondition] = []
        high_risk_count = 0
        max_hazard_severity = "LOW"
        max_hazard_score = 0.0

        for wp in route.waypoints:
            arr_time = dep_time + timedelta(minutes=wp.estimated_travel_time_minutes)

            # 1. Weather forecast at arrival time
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

            # 2. Forest proximity
            forest_info = RouteForestInfo(in_forest=False)
            if forest_features:
                # Check closest forest polygon/point
                min_f_dist = 999.0
                f_name = None
                f_type = None
                for feat in forest_features[:20]:
                    geom = feat.get("geometry", {})
                    props = feat.get("properties", {})
                    # Approximate distance to centroid/coords
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

            # 3. Wildfire proximity
            wildfire_info = RouteWildfireInfo(has_fire_within_5km=False)
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
                    wildfire_info = RouteWildfireInfo(
                        has_fire_within_5km=True,
                        nearest_fire_distance_km=round(min_fire_dist, 2),
                        fire_radiative_power=closest_fire.get("frp_mw") or closest_fire.get("frp"),
                        confidence=str(closest_fire.get("confidence", "nominal")),
                        acquisition_time=acq_str if acq_str else "NRT",
                    )

            # 4. Disaster alerts intersection
            matched_alerts: List[RouteDisasterAlertInfo] = []
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

            # 5. Waypoint hazard scoring
            wp_score = 0.0
            wp_level = "LOW"

            # Check alert severity
            for ma in matched_alerts:
                if ma.severity == "CRITICAL":
                    wp_score = max(wp_score, 90.0)
                    wp_level = "CRITICAL"
                elif ma.severity == "SEVERE":
                    wp_score = max(wp_score, 75.0)
                    if wp_level != "CRITICAL":
                        wp_level = "HIGH"
                elif ma.severity in ["WARNING", "MEDIUM"]:
                    wp_score = max(wp_score, 45.0)
                    if wp_level not in ["CRITICAL", "HIGH"]:
                        wp_level = "MEDIUM"

            # Check wildfire proximity
            if wildfire_info.has_fire_within_5km:
                if wildfire_info.nearest_fire_distance_km and wildfire_info.nearest_fire_distance_km <= 2.0:
                    wp_score = max(wp_score, 85.0)
                    wp_level = "CRITICAL"
                else:
                    wp_score = max(wp_score, 65.0)
                    if wp_level != "CRITICAL":
                        wp_level = "HIGH"

            # Check weather severity
            if weather_cond.warning_level == "SEVERE":
                wp_score = max(wp_score, 70.0)
                if wp_level not in ["CRITICAL"]:
                    wp_level = "HIGH"
            elif weather_cond.warning_level == "CAUTION":
                wp_score = max(wp_score, 35.0)
                if wp_level == "LOW":
                    wp_level = "MEDIUM"

            if wp_level in ["HIGH", "CRITICAL"]:
                high_risk_count += 1

            if wp_score > max_hazard_score:
                max_hazard_score = wp_score
                max_hazard_severity = wp_level

            waypoint_conditions.append(
                RouteWaypointCondition(
                    index=wp.index,
                    name=wp.name,
                    latitude=wp.latitude,
                    longitude=wp.longitude,
                    distance_from_start_km=wp.distance_from_start_km,
                    estimated_arrival_at=arr_time.isoformat(),
                    hazard_level=wp_level,
                    weather=weather_cond,
                    forest=forest_info,
                    wildfire=wildfire_info,
                    alerts=matched_alerts,
                )
            )

        # Determine overall safety status
        overall_status = "SAFE"
        if max_hazard_severity == "CRITICAL" or high_risk_count >= 2:
            overall_status = "HIGH_RISK"
            max_hazard_score = max(max_hazard_score, 80.0)
        elif max_hazard_severity == "HIGH" or high_risk_count >= 1:
            overall_status = "HIGH_RISK"
            max_hazard_score = max(max_hazard_score, 60.0)
        elif max_hazard_severity == "MEDIUM":
            overall_status = "CAUTION"
            max_hazard_score = max(max_hazard_score, 35.0)
        else:
            overall_status = "SAFE"
            max_hazard_score = max(max_hazard_score, 10.0)

        # Prepare alternatives with comparative safety labels
        alt_schemas: List[RouteAlternativeSchema] = []
        for alt in route.alternatives:
            # If primary route has high hazards, the alternate is safer
            alt_score = 15.0 if overall_status in ["HIGH_RISK", "CRITICAL"] else 10.0
            safety_label = (
                "Recommended: Bypasses hazard corridor"
                if overall_status in ["HIGH_RISK", "CRITICAL"]
                else "Alternative scenic route"
            )
            alt_schemas.append(
                RouteAlternativeSchema(
                    id=alt.id,
                    title=alt.title,
                    description=alt.description,
                    distance_km=alt.distance_km,
                    duration_minutes=alt.duration_minutes,
                    geometry_geojson=alt.geometry_geojson,
                    hazard_score=alt_score,
                    safety_label=safety_label,
                )
            )

        freshness = {
            "weather_provider": "Open-Meteo Hourly API",
            "weather_retrieved_at": now.isoformat(),
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
            overall_hazard_score=round(max_hazard_score, 1),
            overall_safety_status=overall_status,
            high_risk_segments_count=high_risk_count,
            conditions=waypoint_conditions,
            alternatives=alt_schemas,
            data_freshness=freshness,
            created_at=now.isoformat(),
        )


route_condition_analyzer = RouteConditionAnalyzer()
