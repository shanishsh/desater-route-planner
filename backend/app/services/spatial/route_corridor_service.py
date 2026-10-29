"""Spatial Route Corridor Service.

Constructs geodesic / projected corridors around route polylines, performs exact
cross-track distance filtering against route geometry, and intersects spatial hazard polygons.
"""
import json
import math
import logging
from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any, Tuple, Optional
from shapely.geometry import LineString, shape, mapping, Polygon
from shapely.ops import transform

logger = logging.getLogger(__name__)

# WGS84 Earth mean radius in meters
EARTH_RADIUS_METERS = 6371000.0

# Supported corridor distances on each side of the route (in meters)
DEFAULT_CORRIDOR_METERS = 5000.0  # 5 km each side = ~10 km total width
MIN_CORRIDOR_METERS = 500.0       # 0.5 km minimum
MAX_CORRIDOR_METERS = 25000.0     # 25 km maximum to prevent unbounded queries


class RouteCorridorService:
    """Service for constructing and querying geographic route corridors."""

    def clamp_corridor_distance(self, distance_meters: Optional[float]) -> float:
        """Clamps the corridor distance on each side within safe operational bounds."""
        if distance_meters is None or distance_meters <= 0:
            return DEFAULT_CORRIDOR_METERS
        return max(MIN_CORRIDOR_METERS, min(distance_meters, MAX_CORRIDOR_METERS))

    def compute_corridor(
        self,
        route_coords: List[Tuple[float, float]],
        corridor_distance_meters: float = DEFAULT_CORRIDOR_METERS,
    ) -> Dict[str, Any]:
        """
        Constructs a derived corridor polygon around a route polyline using a local metric projection.

        Args:
            route_coords: List of (latitude, longitude) coordinate pairs.
            corridor_distance_meters: Buffer distance in meters on each side of the route.

        Returns:
            Dictionary containing:
                - corridor_polygon_geojson: GeoJSON Polygon dict
                - corridor_distance_meters: Applied buffer in meters
                - corridor_distance_km: Applied buffer in kilometers
                - bbox: (min_lat, min_lon, max_lat, max_lon)
                - area_sq_km: Surface area of the corridor in square kilometers
                - route_length_m: Length of the route in meters
        """
        if len(route_coords) < 2:
            raise ValueError("Route must contain at least 2 coordinate points to form a corridor.")

        clamped_distance = self.clamp_corridor_distance(corridor_distance_meters)

        # 1. Compute route centroid as projection origin
        lat0 = sum(p[0] for p in route_coords) / len(route_coords)
        lon0 = sum(p[1] for p in route_coords) / len(route_coords)
        lat0_rad = math.radians(lat0)
        cos_lat0 = math.cos(lat0_rad)

        if abs(cos_lat0) < 1e-6:
            cos_lat0 = 1e-6  # Guard against division by zero at poles

        # 2. Project WGS84 degrees into local metric Cartesian coordinates
        metric_pts = [
            (
                EARTH_RADIUS_METERS * math.radians(lon - lon0) * cos_lat0,
                EARTH_RADIUS_METERS * math.radians(lat - lat0),
            )
            for lat, lon in route_coords
        ]

        # 3. Create metric LineString and buffer by exact meters
        line = LineString(metric_pts)
        buffer_line = line.simplify(5.0, preserve_topology=True) if len(metric_pts) > 50 else line
        buffered_metric = buffer_line.buffer(
            clamped_distance,
            cap_style="round",
            join_style="round",
            quad_segs=16,
        )

        # 4. Project buffered polygon/multipolygon vertices back to WGS84 (lon, lat)
        def to_wgs84(x: float, y: float) -> Tuple[float, float]:
            lon = lon0 + math.degrees(x / (EARTH_RADIUS_METERS * cos_lat0))
            lat = lat0 + math.degrees(y / EARTH_RADIUS_METERS)
            return (lon, lat)

        # Handle both Polygon and MultiPolygon buffered geometries gracefully
        all_lats: List[float] = []
        all_lons: List[float] = []

        if buffered_metric.geom_type == "Polygon":
            ext_wgs = [to_wgs84(x, y) for x, y in buffered_metric.exterior.coords]
            rings = [ext_wgs]
            for interior in buffered_metric.interiors:
                rings.append([to_wgs84(x, y) for x, y in interior.coords])
            polygon_geojson = {
                "type": "Polygon",
                "coordinates": rings,
            }
            for ring in rings:
                for pt in ring:
                    all_lons.append(pt[0])
                    all_lats.append(pt[1])
        elif buffered_metric.geom_type == "MultiPolygon":
            multi_rings: List[List[List[Tuple[float, float]]]] = []
            for poly in buffered_metric.geoms:
                ext_wgs = [to_wgs84(x, y) for x, y in poly.exterior.coords]
                poly_rings = [ext_wgs]
                for interior in poly.interiors:
                    poly_rings.append([to_wgs84(x, y) for x, y in interior.coords])
                multi_rings.append(poly_rings)
                for ring in poly_rings:
                    for pt in ring:
                        all_lons.append(pt[0])
                        all_lats.append(pt[1])
            polygon_geojson = {
                "type": "MultiPolygon",
                "coordinates": multi_rings,
            }
        else:
            # Fallback for unexpected empty or geometry collection
            polygon_geojson = {"type": "GeometryCollection", "geometries": []}

        # 5. Compute tight bounding box of corridor
        if all_lats and all_lons:
            bbox = (min(all_lats), min(all_lons), max(all_lats), max(all_lons))
        else:
            bbox = (0.0, 0.0, 0.0, 0.0)

        area_sq_km = buffered_metric.area / 1_000_000.0

        return {
            "corridor_polygon_geojson": polygon_geojson,
            "corridor_distance_meters": clamped_distance,
            "corridor_distance_km": round(clamped_distance / 1000.0, 2),
            "bbox": bbox,
            "area_sq_km": round(area_sq_km, 2),
            "route_length_m": round(line.length, 1),
        }

    def distance_point_to_route(
        self,
        point_lat: float,
        point_lon: float,
        route_coords: List[Tuple[float, float]],
    ) -> Tuple[float, float, int]:
        """
        Calculates the minimum cross-track distance from a point to a route polyline,
        the distance along the route to that projection point, and the nearest segment index.

        Returns:
            Tuple of (min_distance_m, distance_along_route_m, nearest_segment_index)
        """
        if not route_coords:
            return float("inf"), 0.0, 0

        if len(route_coords) == 1:
            d = self.haversine_distance_m(point_lat, point_lon, route_coords[0][0], route_coords[0][1])
            return d, 0.0, 0

        # Local projection around route centroid
        lat0 = sum(p[0] for p in route_coords) / len(route_coords)
        lon0 = sum(p[1] for p in route_coords) / len(route_coords)
        cos_lat0 = math.cos(math.radians(lat0))

        def to_metric(lat: float, lon: float) -> Tuple[float, float]:
            return (
                EARTH_RADIUS_METERS * math.radians(lon - lon0) * cos_lat0,
                EARTH_RADIUS_METERS * math.radians(lat - lat0),
            )

        px, py = to_metric(point_lat, point_lon)
        m_pts = [to_metric(lat, lon) for lat, lon in route_coords]

        min_dist = float("inf")
        best_along = 0.0
        best_seg = 0
        accum_dist = 0.0

        for i in range(len(m_pts) - 1):
            ax, ay = m_pts[i]
            bx, by = m_pts[i + 1]
            dx = bx - ax
            dy = by - ay
            seg_len = math.hypot(dx, dy)
            if seg_len == 0.0:
                continue

            t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (seg_len * seg_len)))
            cx = ax + t * dx
            cy = ay + t * dy
            dist = math.hypot(px - cx, py - cy)

            if dist < min_dist:
                min_dist = dist
                best_along = accum_dist + (t * seg_len)
                best_seg = i

            accum_dist += seg_len

        return min_dist, best_along, best_seg

    def is_point_in_corridor(
        self,
        point_lat: float,
        point_lon: float,
        route_coords: List[Tuple[float, float]],
        corridor_distance_meters: float = DEFAULT_CORRIDOR_METERS,
    ) -> bool:
        """Determines whether a point falls within the corridor distance of the route."""
        dist_m, _, _ = self.distance_point_to_route(point_lat, point_lon, route_coords)
        return dist_m <= corridor_distance_meters

    def filter_fire_detections(
        self,
        raw_fires: List[Dict[str, Any]],
        route_coords: List[Tuple[float, float]],
        corridor_distance_meters: float = DEFAULT_CORRIDOR_METERS,
    ) -> List[Dict[str, Any]]:
        """
        Filters raw NASA FIRMS detections to only those strictly within the route corridor.
        Attaches calculated distance to route, distance along route, and nearest segment index.
        """
        filtered = []
        for fire in raw_fires:
            f_lat = fire.get("latitude")
            f_lon = fire.get("longitude")
            if f_lat is None or f_lon is None:
                continue

            dist_m, along_m, seg_idx = self.distance_point_to_route(float(f_lat), float(f_lon), route_coords)
            if dist_m <= corridor_distance_meters:
                enriched = dict(fire)
                enriched["distance_to_route_km"] = round(dist_m / 1000.0, 2)
                enriched["distance_along_route_km"] = round(along_m / 1000.0, 2)
                enriched["nearest_segment_index"] = seg_idx
                enriched["is_within_corridor"] = True
                filtered.append(enriched)

        return filtered

    def filter_disaster_alerts(
        self,
        alerts: List[Any],
        route_coords: List[Tuple[float, float]],
        corridor_polygon_geojson: Dict[str, Any],
        corridor_distance_meters: float = DEFAULT_CORRIDOR_METERS,
        corridor_district_keywords: Optional[List[str]] = None,
    ) -> List[Tuple[Any, Dict[str, Any]]]:
        """
        Filters disaster alerts (from DB or RSS feed) against the actual route corridor.

        - If the alert has polygon_geojson: tests genuine polygon intersection with corridor polygon.
        - If the alert has center coordinates: tests if circle perimeter intersects the corridor.
        - If the alert has district text (area_description): matches corridor district keywords without fabricating coordinates.
        - If the alert has NO usable geometry or matching district: it is EXCLUDED from corridor-specific overlays.

        Returns:
            List of (alert_object, spatial_metadata_dict)
        """
        corridor_shape = shape(corridor_polygon_geojson)
        matching = []

        keywords_lower = [k.lower() for k in (corridor_district_keywords or []) if k]

        for alert in alerts:
            # Check for genuine polygon geometry
            poly_json = getattr(alert, "polygon_geojson", None)
            matched = False
            meta: Dict[str, Any] = {
                "intersection_type": "none",
                "distance_to_route_km": 0.0,
            }

            if poly_json:
                try:
                    alert_geom_dict = json.loads(poly_json) if isinstance(poly_json, str) else poly_json
                    alert_shape = shape(alert_geom_dict)
                    if alert_shape.intersects(corridor_shape):
                        matched = True
                        meta["intersection_type"] = "polygon_intersection"
                        meta["geometry_type"] = "Polygon"
                except Exception as e:
                    logger.warning(f"Error parsing alert {getattr(alert, 'id', '')} polygon: {e}")

            if not matched:
                # Check point/radius geometry
                c_lat = getattr(alert, "center_latitude", None)
                c_lon = getattr(alert, "center_longitude", None)
                radius_m = getattr(alert, "affected_radius_meters", 5000.0) or 5000.0

                # Must have valid, non-zero coordinates
                if c_lat is not None and c_lon is not None and not (c_lat == 0.0 and c_lon == 0.0):
                    dist_m, along_m, seg_idx = self.distance_point_to_route(c_lat, c_lon, route_coords)
                    # Check if the alert's affected circle overlaps the route corridor
                    if dist_m <= (corridor_distance_meters + radius_m):
                        matched = True
                        meta["intersection_type"] = "circular_zone_overlap"
                        meta["distance_to_route_km"] = round(dist_m / 1000.0, 2)
                        meta["distance_along_route_km"] = round(along_m / 1000.0, 2)
                        meta["nearest_segment_index"] = seg_idx
                        meta["geometry_type"] = "Circle"

            if not matched:
                # Check genuine district/area description without fabricating fake coordinates
                area_desc = getattr(alert, "area_description", None)
                if area_desc and keywords_lower:
                    desc_lower = area_desc.lower()
                    if any(kw in desc_lower for kw in keywords_lower):
                        matched = True
                        meta["intersection_type"] = "district_advisory"
                        meta["geometry_type"] = "DistrictText"
                        meta["distance_to_route_km"] = 0.0
                        meta["is_non_spatial_advisory"] = True

            if matched:
                matching.append((alert, meta))

        return matching

    def filter_amenities(
        self,
        amenities: List[Dict[str, Any]],
        route_coords: List[Tuple[float, float]],
        corridor_distance_meters: float = DEFAULT_CORRIDOR_METERS,
    ) -> List[Dict[str, Any]]:
        """Filters emergency safe havens and amenities to those within the corridor distance."""
        filtered = []
        for am in amenities:
            a_lat = am.get("latitude")
            a_lon = am.get("longitude")
            if a_lat is None or a_lon is None:
                continue

            dist_m, along_m, seg_idx = self.distance_point_to_route(float(a_lat), float(a_lon), route_coords)
            if dist_m <= corridor_distance_meters:
                enriched = dict(am)
                enriched["distance_to_route_km"] = round(dist_m / 1000.0, 2)
                enriched["distance_along_route_km"] = round(along_m / 1000.0, 2)
                enriched["nearest_segment_index"] = seg_idx
                filtered.append(enriched)

        return filtered

    def generate_weather_sample_points(
        self,
        route_coords: List[Tuple[float, float]],
        total_distance_km: float,
        total_duration_minutes: float,
        departure_time: datetime,
        spacing_km: float = 4.0,
        max_samples: int = 10,
    ) -> List[Dict[str, Any]]:
        """
        Generates evenly spaced weather sample points along the route polyline.

        Returns:
            List of sample point specs with coordinates, distance along route, and estimated arrival time.
        """
        if len(route_coords) < 2:
            return []

        # Target sample count based on route length
        target_count = max(2, min(max_samples, int(total_distance_km / max(1.0, spacing_km)) + 1))
        step_idx = max(1, (len(route_coords) - 1) // (target_count - 1))

        sampled_indices = list(range(0, len(route_coords), step_idx))
        if sampled_indices[-1] != len(route_coords) - 1:
            sampled_indices.append(len(route_coords) - 1)

        samples = []
        for s_idx, r_idx in enumerate(sampled_indices):
            lat, lon = route_coords[r_idx]
            frac = r_idx / max(1, len(route_coords) - 1)
            dist_along_km = round(frac * total_distance_km, 2)
            est_minutes = frac * total_duration_minutes
            arr_time = departure_time + timedelta(minutes=est_minutes)

            samples.append({
                "sample_index": s_idx,
                "latitude": lat,
                "longitude": lon,
                "distance_along_route_km": dist_along_km,
                "estimated_arrival_at": arr_time.isoformat(),
                "arrival_datetime": arr_time,
            })

        return samples

    @staticmethod
    def haversine_distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        """Calculates great-circle distance between two points in meters."""
        phi1 = math.radians(lat1)
        phi2 = math.radians(lat2)
        delta_phi = math.radians(lat2 - lat1)
        delta_lambda = math.radians(lon2 - lon1)

        a = math.sin(delta_phi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
        c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c

    def evaluate_route_exposure(
        self,
        route_coords: List[Tuple[float, float]],
        corridor_distance_meters: float,
        corridor_polygon_geojson: Dict[str, Any],
        alerts: List[Any],
        fires: List[Dict[str, Any]],
        provider_statuses: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """
        Evaluates explainable exposure for a route polyline against hazards and fires.
        Distinguishes direct route polyline intersection vs corridor presence.
        Never reports '100% safe' or 'disaster-free'.
        """
        if len(route_coords) < 2:
            return {
                "hazard_score": 0.0,
                "safety_status": "NORMAL",
                "safety_label": "Normal: Insufficient geometry for corridor analysis",
                "direct_hazard_intersections_count": 0,
                "corridor_hazard_count": 0,
                "fires_within_2km_count": 0,
                "corridor_fires_count": 0,
                "is_complete": False,
                "completeness_label": "INCOMPLETE",
                "exposure_summary": "Insufficient coordinates for spatial analysis.",
            }

        # WGS84 GeoJSON coordinate line [lon, lat]
        route_line_wgs = LineString([(p[1], p[0]) for p in route_coords])
        corridor_shape = shape(corridor_polygon_geojson)

        direct_intersections = []
        corridor_presences = []

        direct_sev = {"CRITICAL": 0, "SEVERE": 0, "WARNING": 0, "INFO": 0}
        corridor_sev = {"CRITICAL": 0, "SEVERE": 0, "WARNING": 0, "INFO": 0}

        for alert in alerts:
            poly_json = getattr(alert, "polygon_geojson", None)
            matched_direct = False
            matched_corridor = False

            if poly_json:
                try:
                    alert_dict = json.loads(poly_json) if isinstance(poly_json, str) else poly_json
                    alert_shape = shape(alert_dict)
                    if alert_shape.intersects(route_line_wgs):
                        matched_direct = True
                    elif alert_shape.intersects(corridor_shape):
                        matched_corridor = True
                except Exception as e:
                    logger.warning(f"Error evaluating alert polygon: {e}")

            if not matched_direct and not matched_corridor:
                c_lat = getattr(alert, "center_latitude", None)
                c_lon = getattr(alert, "center_longitude", None)
                radius_m = getattr(alert, "affected_radius_meters", 5000.0) or 5000.0

                if c_lat is not None and c_lon is not None and not (c_lat == 0.0 and c_lon == 0.0):
                    dist_m, _, _ = self.distance_point_to_route(c_lat, c_lon, route_coords)
                    if dist_m <= radius_m:
                        matched_direct = True
                    elif dist_m <= (corridor_distance_meters + radius_m):
                        matched_corridor = True
                elif getattr(alert, "area_description", None):
                    # District/regional warning without specific coordinates: corridor presence
                    matched_corridor = True

            sev = getattr(alert, "severity", "WARNING").upper()
            if sev not in direct_sev:
                sev = "WARNING"

            if matched_direct:
                direct_intersections.append(alert)
                direct_sev[sev] += 1
            elif matched_corridor:
                corridor_presences.append(alert)
                corridor_sev[sev] += 1

        # Evaluate FIRMS active fire proximity
        fires_within_2km = []
        corridor_fires = []
        for fire in fires:
            f_lat = fire.get("latitude")
            f_lon = fire.get("longitude")
            if f_lat is None or f_lon is None:
                continue
            dist_m, _, _ = self.distance_point_to_route(float(f_lat), float(f_lon), route_coords)
            if dist_m <= 2000.0:
                fires_within_2km.append(fire)
            elif dist_m <= corridor_distance_meters:
                corridor_fires.append(fire)

        # Multi-factor exposure score
        score = 0.0
        score += direct_sev["CRITICAL"] * 45.0
        score += direct_sev["SEVERE"] * 30.0
        score += direct_sev["WARNING"] * 15.0
        score += direct_sev["INFO"] * 5.0
        score += corridor_sev["CRITICAL"] * 20.0
        score += corridor_sev["SEVERE"] * 12.0
        score += corridor_sev["WARNING"] * 6.0
        score += corridor_sev["INFO"] * 2.0
        score += len(fires_within_2km) * 25.0
        score += len(corridor_fires) * 8.0
        hazard_score = min(100.0, round(score, 1))

        # Check provider statuses for analysis completeness
        is_complete = True
        limitations = []
        if provider_statuses:
            for p_key, p_val in provider_statuses.items():
                p_status = p_val.get("status") if isinstance(p_val, dict) else getattr(p_val, "status", "")
                if p_status in ["FAILED", "UNAVAILABLE"]:
                    is_complete = False
                    p_name = p_val.get("provider_name", p_key) if isinstance(p_val, dict) else p_key
                    limitations.append(f"{p_name} feed currently unavailable.")

        completeness_label = "COMPLETE" if is_complete else "PARTIAL"

        # Determine safety status and calibrated explainable label
        if hazard_score >= 60.0 or len(direct_intersections) > 0:
            safety_status = "HIGH_RISK"
            if direct_intersections:
                safety_label = f"High Risk: {len(direct_intersections)} active hazard(s) directly intersect route"
            else:
                safety_label = f"High Risk: Major hazard exposure detected along corridor ({hazard_score}/100)"
        elif hazard_score >= 25.0 or (corridor_sev["CRITICAL"] + corridor_sev["SEVERE"]) > 0 or len(fires_within_2km) > 0:
            safety_status = "CAUTION"
            reasons = []
            if corridor_presences:
                reasons.append(f"{len(corridor_presences)} alert(s) in corridor")
            if fires_within_2km:
                reasons.append(f"{len(fires_within_2km)} fire(s) within 2 km")
            safety_label = f"Caution: {', '.join(reasons)}" if reasons else "Caution: Environmental hazards in corridor"
        elif hazard_score > 0.0:
            safety_status = "LOW_RISK"
            safety_label = f"Low Risk: Minor advisory in vicinity ({hazard_score}/100)"
        else:
            safety_status = "NORMAL"
            safety_label = "Normal: No active hazards detected in analyzed corridor"

        exposure_summary = (
            f"{len(direct_intersections)} direct intersections, "
            f"{len(corridor_presences)} corridor alerts, "
            f"{len(fires_within_2km)} fires within 2 km."
        )

        return {
            "hazard_score": hazard_score,
            "safety_status": safety_status,
            "safety_label": safety_label,
            "direct_hazard_intersections_count": len(direct_intersections),
            "corridor_hazard_count": len(corridor_presences),
            "fires_within_2km_count": len(fires_within_2km),
            "corridor_fires_count": len(corridor_fires),
            "is_complete": is_complete,
            "completeness_label": completeness_label,
            "exposure_summary": exposure_summary,
            "limitations": limitations,
        }


route_corridor_service = RouteCorridorService()
