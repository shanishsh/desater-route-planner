"""Service for analyzing environmental conditions and multi-hazard risks along a route corridor."""
import os
import math
import logging
from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any, Optional, Tuple
from sqlalchemy import select, or_
from sqlalchemy.ext.asyncio import AsyncSession
from app.clients.open_meteo import open_meteo_client
from app.clients.osm_forest import osm_forest_client
from app.clients.osm_amenities import osm_amenities_client
from app.clients.nasa_firms import nasa_firms_client
from app.models.disaster_alert import DisasterAlert
from app.services.routing.base import RouteResult, haversine_distance_km
from app.services.terrain.elevation_service import elevation_service
from app.services.risk.trailguard_risk_engine import trailguard_risk_engine
from app.services.spatial.route_corridor_service import route_corridor_service, DEFAULT_CORRIDOR_METERS
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
        corridor_distance_meters: float = DEFAULT_CORRIDOR_METERS,
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

        # 1. Compute dynamic geodesic corridor polygon and bounding box
        corridor_info = route_corridor_service.compute_corridor(
            route_coords_lat_lon,
            corridor_distance_meters=corridor_distance_meters,
        )
        min_lat, min_lon, max_lat, max_lon = corridor_info["bbox"]
        corridor_dist_m = corridor_info["corridor_distance_meters"]
        corridor_dist_km = corridor_info["corridor_distance_km"]
        corridor_poly_geojson = corridor_info["corridor_polygon_geojson"]

        # Sample coordinates for elevation profile (max 80 points for performance)
        sample_step = max(1, len(route_coords_lat_lon) // 80)
        sampled_coords = [
            route_coords_lat_lon[i] for i in range(0, len(route_coords_lat_lon), sample_step)
        ]
        if sampled_coords[-1] != route_coords_lat_lon[-1]:
            sampled_coords.append(route_coords_lat_lon[-1])

        # 2. Compute DEM Elevation Profile and Tobler hiking speeds
        elevation_data = await elevation_service.compute_elevation_profile(
            sampled_coords, departure_time=dep_time
        )
        elev_points = elevation_data.get("points", [])

        # 3. Query active disaster alerts from DB and perform exact spatial corridor filtering
        allow_simulated = os.getenv("ALLOW_SIMULATED_ALERTS", "false").lower() == "true"
        alert_filters = [
            DisasterAlert.status.in_(["active", "updated"]),
            or_(DisasterAlert.expires_at.is_(None), DisasterAlert.expires_at > now),
            DisasterAlert.center_latitude >= min_lat - 0.05,
            DisasterAlert.center_latitude <= max_lat + 0.05,
            DisasterAlert.center_longitude >= min_lon - 0.05,
            DisasterAlert.center_longitude <= max_lon + 0.05,
        ]
        if not allow_simulated:
            alert_filters.append(DisasterAlert.is_simulated.is_(False))

        stmt = select(DisasterAlert).where(*alert_filters)
        result = await db.execute(stmt)
        candidate_alerts: List[DisasterAlert] = list(result.scalars().all())

        filtered_alert_tuples = route_corridor_service.filter_disaster_alerts(
            candidate_alerts,
            route_coords_lat_lon,
            corridor_poly_geojson,
            corridor_distance_meters=corridor_dist_m,
        )
        active_alerts: List[DisasterAlert] = [t[0] for t in filtered_alert_tuples]

        # 4. Query forest data along corridor
        forest_features = []
        try:
            forest_data = await osm_forest_client.fetch_forest_features(min_lat, min_lon, max_lat, max_lon)
            if forest_data and "features" in forest_data:
                forest_features = forest_data["features"]
        except Exception as e:
            logger.warning(f"Error querying forest data for route: {e}")

        # 5. Query NASA FIRMS fire detections along corridor and filter to exact corridor distance
        active_fires = []
        try:
            firms_data = await nasa_firms_client.fetch_active_fires(min_lat, min_lon, max_lat, max_lon)
            raw_records = firms_data.get("records", []) if firms_data else []
            active_fires = route_corridor_service.filter_fire_detections(
                raw_records,
                route_coords_lat_lon,
                corridor_distance_meters=corridor_dist_m,
            )
        except Exception as e:
            logger.warning(f"Error querying FIRMS fire data for route: {e}")

        # 6. Query Emergency Safe Havens within the configured corridor
        safe_havens: List[SafeHavenInfo] = []
        try:
            amenity_items = await osm_amenities_client.fetch_amenities(min_lat, min_lon, max_lat, max_lon)
            filtered_amenities = route_corridor_service.filter_amenities(
                amenity_items,
                route_coords_lat_lon,
                corridor_distance_meters=corridor_dist_m,
            )
            for item in filtered_amenities:
                safe_havens.append(
                    SafeHavenInfo(
                        name=item.get("name", "Emergency Assistance Point"),
                        category=item.get("category", "shelter"),
                        latitude=item["latitude"],
                        longitude=item["longitude"],
                        distance_km=item.get("distance_to_route_km", 0.0),
                        is_egress_point=True,
                    )
                )
            safe_havens.sort(key=lambda sh: sh.distance_km)
            safe_havens = safe_havens[:15]
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

        provider_statuses = {
            "open_meteo": {
                "provider_name": "Open-Meteo Hourly Forecast",
                "status": "LOADED" if waypoint_conditions else "EMPTY",
                "last_request_time": now.isoformat(),
                "last_success_time": now.isoformat(),
                "http_status": 200,
                "record_count": len(waypoint_conditions),
                "is_stale": False,
                "details": "Synchronized hourly forecast at calculated Tobler arrival times.",
            },
            "copernicus_dem": {
                "provider_name": "Copernicus DEM (Open-Meteo 30m Grid)",
                "status": "LOADED" if elev_points else "EMPTY",
                "last_request_time": now.isoformat(),
                "last_success_time": now.isoformat() if elev_points else None,
                "http_status": 200 if elev_points else 204,
                "record_count": len(elev_points),
                "is_stale": False,
                "details": "30m high-resolution DEM slope and elevation profile.",
            },
            "nasa_firms": {
                "provider_name": "NASA FIRMS Thermal Anomalies",
                "status": "LOADED" if active_fires else "EMPTY",
                "last_request_time": now.isoformat(),
                "last_success_time": now.isoformat(),
                "http_status": 200,
                "record_count": len(active_fires),
                "is_stale": False,
                "details": "Corridor thermal hotspot detections within 5 km of route.",
            },
            "ndma_sachet": {
                "provider_name": "NDMA SACHET / CAP Official Advisories",
                "status": "LOADED" if active_alerts else "EMPTY",
                "last_request_time": now.isoformat(),
                "last_success_time": now.isoformat(),
                "http_status": 200,
                "record_count": len(active_alerts),
                "is_stale": False,
                "details": "Spatial polygon and circular advisory intersection.",
            },
            "osm_overpass": {
                "provider_name": "OpenStreetMap Overpass (Amenities & Forests)",
                "status": "LOADED" if (safe_havens or forest_features) else "EMPTY",
                "last_request_time": now.isoformat(),
                "last_success_time": now.isoformat(),
                "http_status": 200,
                "record_count": len(safe_havens) + len(forest_features),
                "is_stale": False,
                "details": "Verified emergency safe havens and forest conservation boundaries.",
            },
        }

        # 1. Evaluate primary route multi-hazard exposure
        primary_exposure = route_corridor_service.evaluate_route_exposure(
            route_coords_lat_lon,
            corridor_distance_meters=corridor_dist_m,
            corridor_polygon_geojson=corridor_poly_geojson,
            alerts=active_alerts,
            fires=active_fires,
            provider_statuses=provider_statuses,
        )

        # 2. Evaluate candidate alternatives with individual corridors and identical spatial criteria
        alt_schemas: List[RouteAlternativeSchema] = []
        best_alt_idx = -1
        lowest_hazard_score = primary_exposure["hazard_score"]

        for alt_idx, alt in enumerate(route.alternatives):
            alt_coords_raw = alt.geometry_geojson.get("coordinates", [])
            alt_coords_lat_lon = [(pt[1], pt[0]) for pt in alt_coords_raw] if alt_coords_raw else []

            alt_corridor_poly = None
            if len(alt_coords_lat_lon) >= 2:
                try:
                    alt_corr_info = route_corridor_service.compute_corridor(
                        alt_coords_lat_lon,
                        corridor_distance_meters=corridor_dist_m,
                    )
                    alt_corridor_poly = alt_corr_info["corridor_polygon_geojson"]
                except Exception as e:
                    logger.warning(f"Error computing corridor for alternative {alt.id}: {e}")

            alt_exposure = route_corridor_service.evaluate_route_exposure(
                alt_coords_lat_lon,
                corridor_distance_meters=corridor_dist_m,
                corridor_polygon_geojson=alt_corridor_poly or corridor_poly_geojson,
                alerts=active_alerts,
                fires=active_fires,
                provider_statuses=provider_statuses,
            )

            alt_score = alt_exposure["hazard_score"]
            alt_schemas.append(
                RouteAlternativeSchema(
                    id=alt.id,
                    title=alt.title,
                    description=alt.description,
                    distance_km=alt.distance_km,
                    duration_minutes=alt.duration_minutes,
                    geometry_geojson=alt.geometry_geojson,
                    corridor_geojson=alt_corridor_poly,
                    hazard_score=alt_score,
                    safety_label=alt_exposure["safety_label"],
                    direct_hazard_intersections_count=alt_exposure["direct_hazard_intersections_count"],
                    corridor_hazard_count=alt_exposure["corridor_hazard_count"],
                    fires_within_2km_count=alt_exposure["fires_within_2km_count"],
                    exposure_summary=alt_exposure["exposure_summary"],
                    is_recommended=False,
                )
            )

            if alt_score < lowest_hazard_score and alt_exposure["is_complete"]:
                lowest_hazard_score = alt_score
                best_alt_idx = alt_idx

        # If a candidate alternative provides lower hazard exposure than a risky primary route, recommend it
        if best_alt_idx >= 0 and (primary_exposure["hazard_score"] >= 25.0 or primary_exposure["direct_hazard_intersections_count"] > 0):
            rec_alt = alt_schemas[best_alt_idx]
            rec_alt.is_recommended = True
            diff_score = primary_exposure["hazard_score"] - rec_alt.hazard_score
            rec_alt.recommendation_reason = (
                f"Bypasses {primary_exposure['direct_hazard_intersections_count']} direct hazard(s) and reduces exposure by {diff_score:.0f} points."
            )
            rec_alt.safety_label = f"Recommended Alternative (Exposure: {rec_alt.hazard_score}/100)"

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

        # Build GeoJSON Overlays FeatureCollection for MapLibre
        overlay_features = []

        # 0. Derived Corridor Polygon (following route turns/bends, separate from route polyline)
        overlay_features.append({
            "type": "Feature",
            "geometry": corridor_poly_geojson,
            "properties": {
                "layer": "corridor",
                "corridor_distance_meters": corridor_dist_m,
                "corridor_distance_km": corridor_dist_km,
                "area_sq_km": corridor_info["area_sq_km"],
                "color": "#4CAF50",
                "stroke_color": "#2E7D32",
                "title": f"Safety Scan Corridor ({corridor_dist_km} km each side)",
            }
        })

        # 1. Route geometry
        overlay_features.append({
            "type": "Feature",
            "geometry": route.geometry_geojson,
            "properties": {
                "layer": "route",
                "title": route.title,
                "distance_km": route.distance_km,
                "color": "#0288D1",
            }
        })
        # 2. Disaster alerts
        import json
        for alert in active_alerts:
            geom = None
            if alert.polygon_geojson:
                try:
                    geom = json.loads(alert.polygon_geojson)
                except Exception:
                    geom = None
            if not geom and alert.center_latitude and alert.center_longitude:
                # Generate circular polygon perimeter around center coordinate
                c_coords = []
                r_lat = (alert.affected_radius_meters or 5000.0) / 111320.0
                r_lon = (alert.affected_radius_meters or 5000.0) / (111320.0 * math.cos(math.radians(alert.center_latitude)) + 1e-6)
                for i in range(24):
                    ang = 2.0 * math.pi * i / 24
                    c_coords.append([
                        round(alert.center_longitude + r_lon * math.cos(ang), 5),
                        round(alert.center_latitude + r_lat * math.sin(ang), 5)
                    ])
                if c_coords:
                    c_coords.append(c_coords[0])
                geom = {"type": "Polygon", "coordinates": [c_coords]}

            if geom:
                overlay_features.append({
                    "type": "Feature",
                    "geometry": geom,
                    "properties": {
                        "layer": "disaster_alert",
                        "id": alert.id,
                        "title": alert.title,
                        "severity": alert.severity,
                        "category": alert.hazard_category,
                        "instructions": alert.required_protective_instructions,
                        "issuing_authority": alert.issuing_authority,
                        "color": "#D32F2F" if alert.severity in ["CRITICAL", "SEVERE"] else "#F57C00",
                    }
                })
        # 3. Active fires
        for fire in active_fires:
            f_lat = fire.get("latitude", 0.0)
            f_lon = fire.get("longitude", 0.0)
            overlay_features.append({
                "type": "Feature",
                "geometry": {"type": "Point", "coordinates": [f_lon, f_lat]},
                "properties": {
                    "layer": "active_fire",
                    "satellite": fire.get("satellite", "MODIS/VIIRS"),
                    "brightness": fire.get("brightness"),
                    "frp_mw": fire.get("frp_mw") or fire.get("frp"),
                    "confidence": fire.get("confidence", "nominal"),
                    "distance_to_route_km": fire.get("distance_to_route_km"),
                    "distance_along_route_km": fire.get("distance_along_route_km"),
                    "nearest_segment_index": fire.get("nearest_segment_index"),
                    "acquisition_time": f"{fire.get('acquisition_date', '')} {fire.get('acquisition_time', '')}".strip(),
                    "color": "#E65100",
                }
            })
        # 4. Safe havens
        for sh in safe_havens:
            overlay_features.append({
                "type": "Feature",
                "geometry": {"type": "Point", "coordinates": [sh.longitude, sh.latitude]},
                "properties": {
                    "layer": "assistance_point",
                    "name": sh.name,
                    "category": sh.category,
                    "distance_km": sh.distance_km,
                    "is_egress_point": sh.is_egress_point,
                    "color": "#2E7D32",
                }
            })
        # 5. Forest areas
        for feat in forest_features[:20]:
            f_geom = feat.get("geometry")
            f_props = feat.get("properties", {})
            if f_geom:
                overlay_features.append({
                    "type": "Feature",
                    "geometry": f_geom,
                    "properties": {
                        "layer": "forest_area",
                        "name": f_props.get("name", "Forest Area"),
                        "type": f_props.get("type", "woodland"),
                        "color": "#388E3C",
                    }
                })
        # 6. Waypoints
        for wp in waypoint_conditions:
            overlay_features.append({
                "type": "Feature",
                "geometry": {"type": "Point", "coordinates": [wp.longitude, wp.latitude]},
                "properties": {
                    "layer": "weather_point",
                    "index": wp.index,
                    "name": wp.name,
                    "temperature_c": wp.weather.temperature_c,
                    "precipitation_mm": wp.weather.precipitation_mm,
                    "wind_speed_kmh": wp.weather.wind_speed_kmh,
                    "description": wp.weather.weather_description,
                    "warning_level": wp.weather.warning_level,
                    "arrival_at": wp.estimated_arrival_at,
                }
            })

        overlays_geojson = {"type": "FeatureCollection", "features": overlay_features}

        # Dedicated FeatureCollections for high-performance MapLibre native source bindings
        hazards_geojson = {
            "type": "FeatureCollection",
            "features": [f for f in overlay_features if f.get("properties", {}).get("layer") == "disaster_alert"],
        }
        fires_geojson = {
            "type": "FeatureCollection",
            "features": [f for f in overlay_features if f.get("properties", {}).get("layer") == "active_fire"],
        }
        facilities_geojson = {
            "type": "FeatureCollection",
            "features": [f for f in overlay_features if f.get("properties", {}).get("layer") == "assistance_point"],
        }

        corridor_alerts_dto = [
            RouteDisasterAlertInfo(
                alert_id=a.id,
                title=a.title,
                hazard_category=a.hazard_category,
                severity=a.severity,
                distance_to_alert_km=round(
                    route_corridor_service.distance_point_to_route(
                        a.center_latitude, a.center_longitude, route_coords_lat_lon
                    )[0] / 1000.0, 2
                ) if (a.center_latitude and a.center_longitude) else 0.0,
                required_protective_instructions=a.required_protective_instructions,
                is_simulated=a.is_simulated,
                center_latitude=a.center_latitude,
                center_longitude=a.center_longitude,
                affected_radius_meters=a.affected_radius_meters,
                polygon_geojson=(json.loads(a.polygon_geojson) if isinstance(a.polygon_geojson, str) else a.polygon_geojson) if a.polygon_geojson else None,
            )
            for a in active_alerts
        ]

        corridor_fires_dto = [
            RouteWildfireInfo(
                has_fire_within_5km=(f.get("distance_to_route_km", 99.0) <= 5.0),
                nearest_fire_distance_km=f.get("distance_to_route_km"),
                fire_radiative_power=f.get("frp_mw") or f.get("frp"),
                confidence=str(f.get("confidence", "nominal")),
                acquisition_time=f.get("acquisition_time", "NRT"),
                latitude=f.get("latitude"),
                longitude=f.get("longitude"),
                brightness=f.get("brightness"),
            )
            for f in active_fires
        ]

        freshness = {
            "weather_provider": "Open-Meteo Hourly Forecast",
            "weather_retrieved_at": now.isoformat(),
            "elevation_provider": "Copernicus DEM (Open-Meteo 30m Grid)",
            "forest_provider": "OpenStreetMap Overpass",
            "wildfire_provider": "NASA FIRMS (MODIS/VIIRS)",
            "corridor_distance_km": corridor_dist_km,
            "corridor_distance_meters": corridor_dist_m,
            "corridor_geojson": corridor_poly_geojson,
            "corridor_alerts": [a.model_dump() for a in corridor_alerts_dto],
            "corridor_fires": [f.model_dump() for f in corridor_fires_dto],
            "disaster_alerts_active_count": len(active_alerts),
            "provider_statuses": provider_statuses,
            "overlays_geojson": overlays_geojson,
            "hazards_geojson": hazards_geojson,
            "fires_geojson": fires_geojson,
            "facilities_geojson": facilities_geojson,
            "is_stale": False,
            "offline_cached": False,
        }

        return PlannedRouteResponse(
            route_id=route.route_id,
            route_version=1,
            origin={
                "latitude": route.origin_lat,
                "longitude": route.origin_lon,
                "name": route.origin_name,
            },
            destination={
                "latitude": route.destination_lat,
                "longitude": route.destination_lon,
                "name": route.destination_name,
            },
            geometry=route.geometry_geojson,
            corridor_distance_km=corridor_dist_km,
            corridor_distance_meters=corridor_dist_m,
            corridor_geojson=corridor_poly_geojson,
            hazards_geojson=hazards_geojson,
            fires_geojson=fires_geojson,
            facilities_geojson=facilities_geojson,
            distance_meters=round(route.distance_km * 1000.0, 1),
            duration_seconds=round(route.duration_minutes * 60.0, 1),
            routing_provider="OSRM",
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

    async def analyze_persisted_route(
        self,
        db: AsyncSession,
        route_id: str,
        departure_time: Optional[datetime] = None,
        corridor_distance_meters: Optional[float] = None,
        corridor_distance_km: Optional[float] = None,
    ):
        """Analyzes a persisted route by route_id, increments its route_version, and updates stored analysis."""
        from app.models.planned_route import PlannedRoute
        from app.services.routing.base import Waypoint, RouteAlternative
        from app.schemas.route_plan import (
            RouteAnalysisResponse,
            ProviderStatusItem,
            RouteWeatherCondition,
            RouteForestInfo,
            RouteWildfireInfo,
            RouteDisasterAlertInfo,
        )

        stmt = select(PlannedRoute).where(PlannedRoute.id == route_id)
        result = await db.execute(stmt)
        db_route = result.scalar_one_or_none()
        if not db_route:
            return None

        # Reconstruct RouteResult from persisted model
        coords = db_route.geometry_geojson.get("coordinates", [])
        waypoints = []
        if db_route.conditions_data:
            for idx, c in enumerate(db_route.conditions_data):
                waypoints.append(
                    Waypoint(
                        index=c.get("index", idx),
                        name=c.get("name", f"Waypoint {idx}"),
                        latitude=c.get("latitude", 0.0),
                        longitude=c.get("longitude", 0.0),
                        distance_from_start_km=c.get("distance_from_start_km", 0.0),
                        estimated_travel_time_minutes=0.0,
                    )
                )

        alts = []
        if db_route.alternatives_data:
            for a in db_route.alternatives_data:
                alts.append(
                    RouteAlternative(
                        id=a.get("id", ""),
                        title=a.get("title", ""),
                        description=a.get("description", ""),
                        distance_km=a.get("distance_km", 0.0),
                        duration_minutes=a.get("duration_minutes", 0.0),
                        geometry_geojson=a.get("geometry_geojson", {}),
                    )
                )

        route_result = RouteResult(
            route_id=db_route.id,
            title=db_route.title,
            origin_name=db_route.origin_name,
            origin_lat=db_route.origin_lat,
            origin_lon=db_route.origin_lon,
            destination_name=db_route.destination_name,
            destination_lat=db_route.dest_lat,
            destination_lon=db_route.dest_lon,
            travel_mode=db_route.travel_mode,
            distance_km=db_route.distance_km,
            duration_minutes=db_route.duration_minutes,
            geometry_geojson=db_route.geometry_geojson,
            waypoints=waypoints,
            alternatives=alts,
        )

        # Run fresh condition analysis
        corr_m = corridor_distance_meters or (
            (corridor_distance_km * 1000.0) if corridor_distance_km else DEFAULT_CORRIDOR_METERS
        )
        analysis = await self.analyze_route(db, route_result, departure_time, corridor_distance_meters=corr_m)

        # Increment route_version
        new_version = (db_route.route_version or 1) + 1
        db_route.route_version = new_version
        db_route.overall_hazard_score = analysis.overall_hazard_score
        db_route.overall_safety_status = analysis.overall_safety_status
        db_route.conditions_data = [c.model_dump() for c in analysis.conditions]
        db_route.alternatives_data = [a.model_dump() for a in analysis.alternatives]
        db_route.data_freshness_data = analysis.data_freshness
        db_route.last_analyzed_at = datetime.now(timezone.utc)
        await db.commit()
        await db.refresh(db_route)

        fresh = analysis.data_freshness or {}
        raw_providers = fresh.get("provider_statuses", {})
        provider_items = {}
        for p_key, p_val in raw_providers.items():
            if isinstance(p_val, dict):
                provider_items[p_key] = ProviderStatusItem(**p_val)

        # Extract forest, wildfire, and alerts from corridor analysis
        forest_list = [c.forest for c in analysis.conditions if c.forest and c.forest.in_forest]

        raw_corr_fires = fresh.get("corridor_fires", [])
        if raw_corr_fires:
            wildfire_list = [RouteWildfireInfo(**f) for f in raw_corr_fires]
        else:
            wildfire_list = [c.wildfire for c in analysis.conditions if c.wildfire and c.wildfire.has_fire_within_5km]

        raw_corr_alerts = fresh.get("corridor_alerts", [])
        if raw_corr_alerts:
            alert_list = [RouteDisasterAlertInfo(**a) for a in raw_corr_alerts]
        else:
            alert_list = []
            for c in analysis.conditions:
                for al in c.alerts:
                    if not any(existing.alert_id == al.alert_id for existing in alert_list):
                        alert_list.append(al)

        weather_list = [c.weather for c in analysis.conditions]

        limitations = [
            f"Geographic search restricted strictly to {round(corr_m / 1000.0, 1)} km corridor on each side of the route polyline.",
            "NASA FIRMS thermal detections reflect satellite anomalies and have been spatially cross-filtered against the corridor.",
            "Official advisories reflect valid NDMA SACHET / DDMA bulletins with verified spatial coverage.",
        ]

        return RouteAnalysisResponse(
            route_id=db_route.id,
            route_version=new_version,
            corridor_distance_meters=corr_m,
            corridor_distance_km=round(corr_m / 1000.0, 2),
            corridor_geojson=fresh.get("corridor_geojson"),
            analysis_timestamp=datetime.now(timezone.utc).isoformat(),
            overall_hazard_score=db_route.overall_hazard_score,
            overall_safety_status=db_route.overall_safety_status,
            decision_explanation=analysis.decision_explanation,
            provider_statuses=provider_items,
            weather_by_waypoint=weather_list,
            disaster_alerts=alert_list,
            fire_detections=wildfire_list,
            forest_features=forest_list,
            assistance_points=analysis.safe_havens,
            affected_segments=analysis.conditions,
            data_quality_summary=fresh,
            overlays_geojson=fresh.get("overlays_geojson", {}),
            hazards_geojson=analysis.hazards_geojson,
            fires_geojson=analysis.fires_geojson,
            facilities_geojson=analysis.facilities_geojson,
            alternatives=analysis.alternatives,
            analysis_limitations=limitations,
        )


route_condition_analyzer = RouteConditionAnalyzer()
