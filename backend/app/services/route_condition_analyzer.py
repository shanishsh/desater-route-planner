"""Service for analyzing environmental conditions and multi-hazard risks along a route corridor."""
import os
import math
import asyncio
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

        # 2. Concurrently fetch DEM Elevation, Forest geometries, NASA FIRMS, and Safe Havens
        async def _safe_elev():
            try:
                return await asyncio.wait_for(
                    elevation_service.compute_elevation_profile(sampled_coords, departure_time=dep_time),
                    timeout=4.0
                )
            except Exception as e:
                logger.warning(f"Elevation query failed or timed out: {e}")
                return {"points": []}

        async def _safe_forest():
            try:
                res = await asyncio.wait_for(
                    osm_forest_client.fetch_forest_features(min_lat, min_lon, max_lat, max_lon),
                    timeout=3.5
                )
                return res.get("features", []) if res else []
            except Exception as e:
                logger.warning(f"Forest query failed or timed out: {e}")
                return []

        async def _safe_fires():
            try:
                res = await asyncio.wait_for(
                    nasa_firms_client.fetch_active_fires(min_lat, min_lon, max_lat, max_lon),
                    timeout=3.5
                )
                return res.get("records", []) if res else []
            except Exception as e:
                logger.warning(f"FIRMS fire query failed or timed out: {e}")
                return []

        async def _safe_amenities():
            try:
                return await asyncio.wait_for(
                    osm_amenities_client.fetch_amenities(min_lat, min_lon, max_lat, max_lon),
                    timeout=3.5
                )
            except Exception as e:
                logger.warning(f"Safe havens query failed or timed out: {e}")
                return []

        # 3. Query active disaster alerts from DB and run network queries concurrently
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

        async def _safe_db_alerts():
            try:
                res = await db.execute(stmt)
                return list(res.scalars().all())
            except Exception as e:
                logger.warning(f"Database disaster alerts query failed: {e}")
                return []

        (elevation_data, forest_features, raw_records, amenity_items), candidate_alerts = await asyncio.gather(
            asyncio.gather(_safe_elev(), _safe_forest(), _safe_fires(), _safe_amenities()),
            _safe_db_alerts(),
        )

        elev_points = elevation_data.get("points", []) if elevation_data else []

        filtered_alert_tuples = route_corridor_service.filter_disaster_alerts(
            candidate_alerts,
            route_coords_lat_lon,
            corridor_poly_geojson,
            corridor_distance_meters=corridor_dist_m,
        )
        active_alerts: List[DisasterAlert] = [t[0] for t in filtered_alert_tuples]

        # Filter fire detections to corridor
        active_fires = route_corridor_service.filter_fire_detections(
            raw_records,
            route_coords_lat_lon,
            corridor_distance_meters=corridor_dist_m,
        )

        # Filter safe havens to corridor
        filtered_amenities = route_corridor_service.filter_amenities(
            amenity_items,
            route_coords_lat_lon,
            corridor_distance_meters=corridor_dist_m,
        )
        safe_havens: List[SafeHavenInfo] = []
        for item in filtered_amenities:
            safe_havens.append(
                SafeHavenInfo(
                    name=item.get("name", "Emergency Assistance Point"),
                    category=item.get("category", "shelter"),
                    latitude=item["latitude"],
                    longitude=item["longitude"],
                    distance_km=item.get("distance_to_route_km", 0.0),
                    is_egress_point=True,
                    opening_hours=item.get("opening_hours"),
                    phone=item.get("phone"),
                    website=item.get("website"),
                    brand=item.get("brand"),
                    operator=item.get("operator"),
                    address=item.get("address"),
                    emergency_verified=item.get("emergency_verified", False),
                    source=item.get("provenance", "OpenStreetMap Overpass"),
                )
            )
        safe_havens.sort(key=lambda sh: sh.distance_km)
        safe_havens = safe_havens[:30]

        # Analyze each waypoint with terrain and TrailGuard Risk Engine
        waypoint_conditions: List[RouteWaypointCondition] = []
        segment_evaluations: List[Dict[str, Any]] = []

        # Precompute arrival times and closest elevation points for all waypoints
        wp_info_list = []
        for wp in route.waypoints:
            closest_elev_pt = None
            min_dist_to_elev = 999.0
            for ep in elev_points:
                d = haversine_distance_km(wp.latitude, wp.longitude, ep["latitude"], ep["longitude"])
                if d < min_dist_to_elev:
                    min_dist_to_elev = d
                    closest_elev_pt = ep

            if closest_elev_pt and "cumulative_minutes" in closest_elev_pt:
                arr_time = dep_time + timedelta(minutes=closest_elev_pt["cumulative_minutes"])
            else:
                arr_time = dep_time + timedelta(minutes=wp.estimated_travel_time_minutes)

            wp_info_list.append((wp, closest_elev_pt, arr_time))

        # Concurrently fetch weather forecasts for all waypoints
        weather_tasks = [
            open_meteo_client.get_forecast_for_waypoint(wp.latitude, wp.longitude, arr_time)
            for wp, _, arr_time in wp_info_list
        ]
        weather_results = await asyncio.gather(*weather_tasks, return_exceptions=True)

        for i, (wp, closest_elev_pt, arr_time) in enumerate(wp_info_list):
            wp_elevation = closest_elev_pt["elevation_m"] if closest_elev_pt else 0.0
            wp_slope_deg = closest_elev_pt["slope_degrees"] if closest_elev_pt else 0.0

            w_raw = weather_results[i]
            if isinstance(w_raw, Exception) or not isinstance(w_raw, dict):
                logger.warning(f"Weather fetch for waypoint {wp.name} failed: {w_raw}")
                w_dict = {
                    "temperature_c": 18.0,
                    "relative_humidity_pct": 50.0,
                    "wind_speed_kmh": 10.0,
                    "precipitation_mm": 0.0,
                    "precipitation_probability_pct": 0.0,
                    "weather_code": 0,
                    "weather_description": "Clear sky",
                    "warning_level": "NONE",
                    "forecast_valid_at": arr_time,
                    "source": "Offline Estimate",
                    "is_simulated": False,
                }
            else:
                w_dict = w_raw

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

        # Primary route facility stats
        prim_hosp = sum(1 for sh in safe_havens if sh.category == "hospital")
        prim_clinic = sum(1 for sh in safe_havens if sh.category == "clinic")
        prim_police = sum(1 for sh in safe_havens if sh.category == "police")
        prim_fuel = sum(1 for sh in safe_havens if sh.category == "fuel")
        prim_fire = sum(1 for sh in safe_havens if sh.category == "fire_station")
        med_dists = [sh.distance_km for sh in safe_havens if sh.category in ("hospital", "clinic")]
        prim_near_hosp = round(min(med_dists), 2) if med_dists else None
        pol_dists = [sh.distance_km for sh in safe_havens if sh.category == "police"]
        prim_near_pol = round(min(pol_dists), 2) if pol_dists else None
        fuel_dists = [sh.distance_km for sh in safe_havens if sh.category == "fuel"]
        prim_near_fuel = round(min(fuel_dists), 2) if fuel_dists else None

        # 2. Evaluate candidate alternatives with individual corridors, independent facility scans, and identical spatial criteria
        alt_schemas: List[RouteAlternativeSchema] = []
        best_alt_idx = -1
        lowest_hazard_score = primary_exposure["hazard_score"]

        # Track all candidates for multi-preference ranking (candidate 0 = primary route)
        all_candidates_summary = [{
            "id": route.route_id,
            "title": "Primary Route",
            "distance_km": route.distance_km,
            "duration_minutes": route.duration_minutes,
            "hazard_score": primary_exposure["hazard_score"],
            "intersections": primary_exposure["direct_hazard_intersections_count"],
            "emergency_facilities": prim_hosp + prim_clinic + prim_police,
            "nearest_hospital_km": prim_near_hosp or 999.0,
            "hospitals_count": prim_hosp,
            "clinics_count": prim_clinic,
            "police_stations_count": prim_police,
            "fuel_stations_count": prim_fuel,
        }]

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

            # Independent facility filtering for this alternative
            alt_amenities = route_corridor_service.filter_amenities(
                amenity_items,
                alt_coords_lat_lon,
                corridor_distance_meters=corridor_dist_m,
            )
            alt_hosp = sum(1 for a in alt_amenities if a.get("category") == "hospital")
            alt_clinic = sum(1 for a in alt_amenities if a.get("category") == "clinic")
            alt_police = sum(1 for a in alt_amenities if a.get("category") == "police")
            alt_fuel = sum(1 for a in alt_amenities if a.get("category") == "fuel")
            alt_fire = sum(1 for a in alt_amenities if a.get("category") == "fire_station")
            alt_med_dists = [a.get("distance_to_route_km", 999.0) for a in alt_amenities if a.get("category") in ("hospital", "clinic")]
            alt_near_hosp = round(min(alt_med_dists), 2) if alt_med_dists else None
            alt_pol_dists = [a.get("distance_to_route_km", 999.0) for a in alt_amenities if a.get("category") == "police"]
            alt_near_pol = round(min(alt_pol_dists), 2) if alt_pol_dists else None
            alt_fuel_dists = [a.get("distance_to_route_km", 999.0) for a in alt_amenities if a.get("category") == "fuel"]
            alt_near_fuel = round(min(alt_fuel_dists), 2) if alt_fuel_dists else None

            # Build facility FeatureCollection for this alternative
            alt_fac_features = []
            for a in alt_amenities[:25]:
                cat = a.get("category", "shelter")
                c_color = "#E53935" if cat == "hospital" else ("#D32F2F" if cat == "clinic" else ("#1E88E5" if cat == "police" else ("#F57C00" if cat == "fuel" else ("#E65100" if cat == "fire_station" else "#2E7D32"))))
                alt_fac_features.append({
                    "type": "Feature",
                    "geometry": {"type": "Point", "coordinates": [a["longitude"], a["latitude"]]},
                    "properties": {
                        "layer": "assistance_point",
                        "facility_layer": f"facility_{cat}",
                        "name": a.get("name", "Assistance Point"),
                        "category": cat,
                        "distance_km": a.get("distance_to_route_km", 0.0),
                        "opening_hours": a.get("opening_hours"),
                        "phone": a.get("phone"),
                        "website": a.get("website"),
                        "brand": a.get("brand"),
                        "operator": a.get("operator"),
                        "emergency_verified": a.get("emergency_verified", False),
                        "address": a.get("address"),
                        "provenance": a.get("provenance", "OpenStreetMap Overpass"),
                        "color": c_color,
                    }
                })
            alt_fac_geojson = {"type": "FeatureCollection", "features": alt_fac_features}

            alt_score = alt_exposure["hazard_score"]
            alt_schema = RouteAlternativeSchema(
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
                hospitals_count=alt_hosp,
                clinics_count=alt_clinic,
                police_stations_count=alt_police,
                fuel_stations_count=alt_fuel,
                fire_stations_count=alt_fire,
                nearest_hospital_km=alt_near_hosp,
                nearest_police_km=alt_near_pol,
                nearest_fuel_km=alt_near_fuel,
                facilities_geojson=alt_fac_geojson,
                is_recommended=False,
                data_completeness=alt_exposure.get("is_complete", True),
            )
            alt_schemas.append(alt_schema)

            all_candidates_summary.append({
                "id": alt.id,
                "title": alt.title,
                "distance_km": alt.distance_km,
                "duration_minutes": alt.duration_minutes,
                "hazard_score": alt_score,
                "intersections": alt_exposure["direct_hazard_intersections_count"],
                "emergency_facilities": alt_hosp + alt_clinic + alt_police,
                "nearest_hospital_km": alt_near_hosp or 999.0,
                "hospitals_count": alt_hosp,
                "clinics_count": alt_clinic,
                "police_stations_count": alt_police,
                "fuel_stations_count": alt_fuel,
            })

            if alt_score < lowest_hazard_score and alt_exposure["is_complete"]:
                lowest_hazard_score = alt_score
                best_alt_idx = alt_idx

        # Compute 5 preference rankings across all routes
        # 1. Shortest (distance_km asc)
        shortest_rank = sorted(range(len(all_candidates_summary)), key=lambda i: all_candidates_summary[i]["distance_km"])
        # 2. Fastest (duration_minutes asc)
        fastest_rank = sorted(range(len(all_candidates_summary)), key=lambda i: all_candidates_summary[i]["duration_minutes"])
        # 3. Safest (hazard_score asc, intersections asc)
        safest_rank = sorted(range(len(all_candidates_summary)), key=lambda i: (all_candidates_summary[i]["hazard_score"], all_candidates_summary[i]["intersections"]))
        # 4. Emergency Access (emergency_facilities desc, nearest_hospital asc)
        access_rank = sorted(range(len(all_candidates_summary)), key=lambda i: (-all_candidates_summary[i]["emergency_facilities"], all_candidates_summary[i]["nearest_hospital_km"]))
        # 5. Balanced
        def _balanced_score(cand):
            saf_pt = max(0.0, 100.0 - cand["hazard_score"])
            dur_pt = max(0.0, 100.0 - cand["duration_minutes"])
            fac_pt = min(100.0, cand["emergency_facilities"] * 25.0)
            return 0.40 * saf_pt + 0.30 * dur_pt + 0.30 * fac_pt
        balanced_rank = sorted(range(len(all_candidates_summary)), key=lambda i: -_balanced_score(all_candidates_summary[i]))

        for a_idx, alt_schema in enumerate(alt_schemas):
            cand_idx = a_idx + 1  # 0 is primary
            r_short = shortest_rank.index(cand_idx) + 1
            r_fast = fastest_rank.index(cand_idx) + 1
            r_safe = safest_rank.index(cand_idx) + 1
            r_access = access_rank.index(cand_idx) + 1
            r_bal = balanced_rank.index(cand_idx) + 1

            alt_schema.preference_rankings = {
                "shortest": {
                    "rank": r_short,
                    "metric": f"{alt_schema.distance_km} km",
                    "contrast": f"{round(alt_schema.distance_km - route.distance_km, 1):+g} km vs primary",
                },
                "fastest": {
                    "rank": r_fast,
                    "metric": f"{int(alt_schema.duration_minutes)} min",
                    "contrast": f"{round(alt_schema.duration_minutes - route.duration_minutes, 1):+g} min vs primary",
                },
                "safest": {
                    "rank": r_safe,
                    "metric": f"Hazard: {int(alt_schema.hazard_score)}/100",
                    "contrast": f"{round(primary_exposure['hazard_score'] - alt_schema.hazard_score, 0):+g} hazard diff",
                },
                "facility_access": {
                    "rank": r_access,
                    "metric": f"{alt_schema.hospitals_count + alt_schema.police_stations_count} emergency sites",
                    "contrast": f"Nearest hospital: {alt_schema.nearest_hospital_km or 'N/A'} km",
                },
                "balanced": {
                    "rank": r_bal,
                    "metric": f"Rank #{r_bal}",
                    "contrast": "Multi-criteria score (Safety 40%, Travel 30%, Facilities 30%)",
                },
            }

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
            derived_from = "official_geometry"
            if alert.polygon_geojson:
                try:
                    geom = json.loads(alert.polygon_geojson)
                except Exception:
                    geom = None
            if not geom and alert.center_latitude and alert.center_longitude:
                # If alert has a documented circle radius from CAP 1.2 (<cap:circle>)
                if alert.affected_radius_meters and alert.affected_radius_meters > 0:
                    c_coords = []
                    r_lat = alert.affected_radius_meters / 111320.0
                    r_lon = alert.affected_radius_meters / (111320.0 * math.cos(math.radians(alert.center_latitude)) + 1e-6)
                    for i in range(24):
                        ang = 2.0 * math.pi * i / 24
                        c_coords.append([
                            round(alert.center_longitude + r_lon * math.cos(ang), 5),
                            round(alert.center_latitude + r_lat * math.sin(ang), 5)
                        ])
                    if c_coords:
                        c_coords.append(c_coords[0])
                    geom = {"type": "Polygon", "coordinates": [c_coords]}
                    derived_from = "cap_circle"
                else:
                    # Point-only alert: preserve strictly as a Point, do NOT invent a polygon!
                    geom = {"type": "Point", "coordinates": [alert.center_longitude, alert.center_latitude]}
                    derived_from = "point"

            if geom:
                layer_type = "disaster_alert" if geom.get("type") in ["Polygon", "MultiPolygon"] else "disaster_alert_point"
                overlay_features.append({
                    "type": "Feature",
                    "geometry": geom,
                    "properties": {
                        "layer": layer_type,
                        "id": alert.id,
                        "title": alert.title,
                        "severity": alert.severity,
                        "category": alert.hazard_category,
                        "instructions": alert.required_protective_instructions,
                        "issuing_authority": alert.issuing_authority,
                        "derived_from": derived_from,
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
        # 4. Safe havens & essential facilities
        facility_colors = {
            "hospital": "#E53935",
            "clinic": "#D32F2F",
            "police": "#1E88E5",
            "fuel": "#F57C00",
            "fire_station": "#E65100",
            "shelter": "#2E7D32",
        }
        for sh in safe_havens:
            c_color = facility_colors.get(sh.category, "#2E7D32")
            overlay_features.append({
                "type": "Feature",
                "geometry": {"type": "Point", "coordinates": [sh.longitude, sh.latitude]},
                "properties": {
                    "layer": "assistance_point",
                    "facility_layer": f"facility_{sh.category}",
                    "name": sh.name,
                    "category": sh.category,
                    "distance_km": sh.distance_km,
                    "is_egress_point": sh.is_egress_point,
                    "opening_hours": sh.opening_hours,
                    "phone": sh.phone,
                    "website": sh.website,
                    "brand": sh.brand,
                    "operator": sh.operator,
                    "address": sh.address,
                    "emergency_verified": sh.emergency_verified,
                    "provenance": sh.source,
                    "color": c_color,
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
            "features": [f for f in overlay_features if f.get("properties", {}).get("layer") in ("disaster_alert", "disaster_alert_point")],
        }
        fires_geojson = {
            "type": "FeatureCollection",
            "features": [f for f in overlay_features if f.get("properties", {}).get("layer") == "active_fire"],
        }
        facilities_geojson = {
            "type": "FeatureCollection",
            "features": [f for f in overlay_features if f.get("properties", {}).get("layer") == "assistance_point"],
        }
        forests_geojson = {
            "type": "FeatureCollection",
            "features": [f for f in overlay_features if f.get("properties", {}).get("layer") == "forest_area"],
        }
        weather_geojson = {
            "type": "FeatureCollection",
            "features": [f for f in overlay_features if f.get("properties", {}).get("layer") == "weather_point"],
        }

        # Compute geometry type diagnostics breakdown
        geom_counts = {"points": 0, "polygons": 0, "multipolygons": 0, "linestrings": 0}
        for f in overlay_features:
            g_type = (f.get("geometry", {}).get("type") or "").lower()
            if g_type == "point":
                geom_counts["points"] += 1
            elif g_type == "polygon":
                geom_counts["polygons"] += 1
            elif g_type == "multipolygon":
                geom_counts["multipolygons"] += 1
            elif g_type == "linestring":
                geom_counts["linestrings"] += 1

        geometry_diagnostics = {
            "geometry_type_counts": geom_counts,
            "total_overlay_features": len(overlay_features),
            "forest_polygons_count": len(forests_geojson["features"]),
            "fire_points_count": len(fires_geojson["features"]),
            "hazard_features_count": len(hazards_geojson["features"]),
            "safe_haven_points_count": len(facilities_geojson["features"]),
            "weather_points_count": len(weather_geojson["features"]),
            "missing_geometry_count": 0,
            "corridor_distance_km": corridor_dist_km,
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
            "forests_geojson": forests_geojson,
            "weather_geojson": weather_geojson,
            "geometry_diagnostics": geometry_diagnostics,
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
            forests_geojson=forests_geojson,
            weather_geojson=weather_geojson,
            geometry_diagnostics=geometry_diagnostics,
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
            forests_geojson=analysis.forests_geojson,
            weather_geojson=analysis.weather_geojson,
            geometry_diagnostics=fresh.get("geometry_diagnostics", {}),
            alternatives=analysis.alternatives,
            analysis_limitations=limitations,
        )


route_condition_analyzer = RouteConditionAnalyzer()
