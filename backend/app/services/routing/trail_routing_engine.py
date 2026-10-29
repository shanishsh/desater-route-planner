"""
Trail Routing Engine for Uttarakhand Himalayan Trekking.

Enforces evidence-based hiking routing:
1. Routes over genuine, connected, surveyed Himalayan trails.
2. Evaluates snapping distance (<= 300m mapped, 300m-1500m partial with connector, > 1500m unverified).
3. Evaluates natural barriers (e.g. unbridged river crossings) using waterway spatial queries.
4. Never invents straight-line connectors across rivers, cliffs, or inaccessible terrain.
5. Returns explicit verification statuses: MAPPED_TRAIL, PARTIALLY_VERIFIED, NO_VERIFIED_ROUTE.
"""

import json
import logging
import math
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from fastapi import HTTPException, status
from app.services.routing.base import (
    RouteResult,
    Waypoint,
    RouteAlternative,
    haversine_distance_km,
    sample_points_along_polyline,
)
from app.services.routing.osrm_provider import osrm_routing_provider
from app.services.spatial.waterway_service import waterway_service

logger = logging.getLogger(__name__)

RESOURCE_PATH = Path(__file__).resolve().parent.parent.parent / "resources" / "uttarakhand_verified_trails.geojson"


def point_to_segment_distance_km(
    plat: float, plon: float,
    alat: float, alon: float,
    blat: float, blon: float
) -> Tuple[float, float, float]:
    """
    Computes shortest distance from point P to line segment AB, returning (distance_km, proj_lat, proj_lon).
    Uses equirectangular planar projection locally for segment projection, then haversine distance.
    """
    d_ab = haversine_distance_km(alat, alon, blat, blon)
    if d_ab < 1e-6:
        dist = haversine_distance_km(plat, plon, alat, alon)
        return dist, alat, alon

    # Local metric coordinates with respect to A
    cos_lat = math.cos(math.radians((alat + blat) / 2.0))
    ax, ay = 0.0, 0.0
    bx = (blon - alon) * 111.320 * cos_lat
    by = (blat - alat) * 110.574
    px = (plon - alon) * 111.320 * cos_lat
    py = (plat - alat) * 110.574

    seg_len_sq = bx * bx + by * by
    if seg_len_sq == 0:
        dist = haversine_distance_km(plat, plon, alat, alon)
        return dist, alat, alon

    t = (px * bx + py * by) / seg_len_sq
    t = max(0.0, min(1.0, t))

    proj_x = ax + t * bx
    proj_y = ay + t * by

    proj_lat = alat + (proj_y / 110.574)
    proj_lon = alon + (proj_x / (111.320 * cos_lat))

    dist_km = haversine_distance_km(plat, plon, proj_lat, proj_lon)
    return dist_km, proj_lat, proj_lon


class TrailRoutingEngine:
    """Specialist routing engine for alpine hiking paths and trail networks."""

    def __init__(self):
        self._trails_cache: List[Dict[str, Any]] = []
        self._load_trails()

    def _load_trails(self):
        if not RESOURCE_PATH.exists():
            logger.warning(f"Verified trails dataset not found at {RESOURCE_PATH}")
            self._trails_cache = []
            return
        try:
            with open(RESOURCE_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)
            self._trails_cache = data.get("features", [])
            logger.info(f"Loaded {len(self._trails_cache)} verified trails from {RESOURCE_PATH.name}")
        except Exception as e:
            logger.error(f"Failed to load verified trails dataset: {e}")
            self._trails_cache = []

    def find_nearest_trail(
        self, lat: float, lon: float, max_dist_km: float = 2.0
    ) -> Optional[Dict[str, Any]]:
        """
        Finds the closest trail feature and projection point within max_dist_km.
        Returns dict with trail feature, min_dist_km, proj_lat, proj_lon, segment_idx.
        """
        best_match = None
        min_dist = float("inf")

        for feature in self._trails_cache:
            coords = feature.get("geometry", {}).get("coordinates", [])
            if len(coords) < 2:
                continue

            for idx in range(len(coords) - 1):
                alon, alat = coords[idx][0], coords[idx][1]
                blon, blat = coords[idx + 1][0], coords[idx + 1][1]

                dist, proj_lat, proj_lon = point_to_segment_distance_km(lat, lon, alat, alon, blat, blon)
                if dist < min_dist:
                    min_dist = dist
                    best_match = {
                        "trail_feature": feature,
                        "distance_km": dist,
                        "distance_m": dist * 1000.0,
                        "proj_lat": proj_lat,
                        "proj_lon": proj_lon,
                        "segment_index": idx,
                    }

        if best_match and min_dist <= max_dist_km:
            return best_match
        return None

    def check_barrier_intersection(
        self,
        lat1: float, lon1: float,
        lat2: float, lon2: float
    ) -> Tuple[bool, Optional[str]]:
        """
        Evaluates whether a straight-line connection crosses a major Himalayan river
        channel without a designated bridge or ford.
        """
        dist_km = haversine_distance_km(lat1, lon1, lat2, lon2)
        if dist_km < 0.05:  # Under 50m, direct proximity
            return False, None

        # Sample points along proposed connector line and check distance to river
        samples = sample_points_along_polyline([[lon1, lat1], [lon2, lat2]], interval_km=0.05, max_points=20)
        for s_lat, s_lon, _ in samples:
            # Query waterway service near sample
            nearby_rivers = waterway_service.find_waterways_near_point(s_lat, s_lon, radius_km=0.06)
            if nearby_rivers:
                r_name = nearby_rivers[0].get("name", "River")
                return True, f"Proposed connector crosses {r_name} without a mapped bridge or foot crossing."

        return False, None

    async def calculate_hiking_route(
        self,
        origin_lat: float,
        origin_lon: float,
        origin_name: str,
        dest_lat: float,
        dest_lon: float,
        dest_name: str,
    ) -> RouteResult:
        """
        Calculates a hiking route over actual mapped trail geometry.
        Returns a verified RouteResult with evidence metadata or raises an explicit 404/422.
        """
        # 1. Search for candidate verified trails
        origin_snap = self.find_nearest_trail(origin_lat, origin_lon, max_dist_km=2.0)
        dest_snap = self.find_nearest_trail(dest_lat, dest_lon, max_dist_km=2.0)

        # 2. Case A: Both origin and destination match the SAME verified trail network
        if origin_snap and dest_snap and (
            origin_snap["trail_feature"]["id"] == dest_snap["trail_feature"]["id"]
        ):
            return self._build_trail_network_route(
                origin_lat, origin_lon, origin_name, origin_snap,
                dest_lat, dest_lon, dest_name, dest_snap,
            )

        # 3. Case B: Outside primary verified dataset -> Query OSRM walking with connected road network fallback
        logger.info(f"Querying OSRM walking provider for pedestrian route: ({origin_lat},{origin_lon}) to ({dest_lat},{dest_lon})")
        osrm_res = None
        last_service_err = None

        # 3a. Attempt dedicated pedestrian / walking profile first
        try:
            res = await osrm_routing_provider.calculate_route(
                origin_lat=origin_lat,
                origin_lon=origin_lon,
                origin_name=origin_name,
                dest_lat=dest_lat,
                dest_lon=dest_lon,
                dest_name=dest_name,
                travel_mode="walking",
            )
            straight_line_km = haversine_distance_km(origin_lat, origin_lon, dest_lat, dest_lon)
            if res.distance_km >= (straight_line_km * 0.85):
                osrm_res = res
            else:
                logger.warning(f"OSRM walking returned bogus segment ({res.distance_km}km vs straight-line {straight_line_km:.2f}km)")
        except HTTPException as he:
            if he.status_code == 422:
                raise
            if he.status_code in (500, 502, 503, 504, 429):
                last_service_err = he
        except Exception as e:
            logger.info(f"OSRM walking profile failed ({e}), checking connected road network...")

        # 3b. Fallback to connected public road network with pedestrian speed calculation if walking profile had gaps
        if osrm_res is None:
            try:
                drive_res = await osrm_routing_provider.calculate_route(
                    origin_lat=origin_lat,
                    origin_lon=origin_lon,
                    origin_name=origin_name,
                    dest_lat=dest_lat,
                    dest_lon=dest_lon,
                    dest_name=dest_name,
                    travel_mode="driving",
                )
                straight_line_km = haversine_distance_km(origin_lat, origin_lon, dest_lat, dest_lon)
                if drive_res.distance_km >= (straight_line_km * 0.85):
                    # Adapt driving geometry for pedestrian travel
                    drive_res.travel_mode = "walking"
                    drive_res.duration_minutes = round((drive_res.distance_km / 3.5) * 60.0, 1)  # 3.5 km/h pedestrian pace
                    drive_res.duration_seconds = drive_res.duration_minutes * 60.0
                    drive_res.verification_status = "PARTIALLY_VERIFIED"
                    drive_res.unverified_reason = "Route follows connected public road network with pedestrian access."
                    osrm_res = drive_res
                    logger.info(f"Connected road network pedestrian route found: {drive_res.distance_km} km")
            except HTTPException as he:
                if he.status_code == 422:
                    raise
                if he.status_code in (500, 502, 503, 504, 429):
                    last_service_err = he
            except Exception as e:
                logger.warning(f"Road network pedestrian fallback also failed: {e}")

        # 3c. If a valid route was found, validate barrier crossings & snap distances
        if osrm_res is not None:
            coords = osrm_res.geometry_geojson.get("coordinates", [])
            if coords:
                start_lon, start_lat = coords[0][0], coords[0][1]
                has_barrier, reason = self.check_barrier_intersection(origin_lat, origin_lon, start_lat, start_lon)
                if has_barrier:
                    raise HTTPException(
                        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                        detail=f"Route cannot be verified: {reason}. Walking across natural barriers without trails is prohibited.",
                    )

            origin_off_trail_m = haversine_distance_km(origin_lat, origin_lon, coords[0][1], coords[0][0]) * 1000.0 if coords else 0.0
            dest_off_trail_m = haversine_distance_km(dest_lat, dest_lon, coords[-1][1], coords[-1][0]) * 1000.0 if coords else 0.0

            if max(origin_off_trail_m, dest_off_trail_m) <= 300.0 and osrm_res.verification_status == "VERIFIED":
                osrm_res.verification_status = "MAPPED_TRAIL"
            elif osrm_res.verification_status != "PARTIALLY_VERIFIED":
                osrm_res.verification_status = "PARTIALLY_VERIFIED"
                osrm_res.unverified_reason = (
                    f"Trail access requires off-trail walk of {int(max(origin_off_trail_m, dest_off_trail_m))}m. "
                    "Connection is not on a mapped public trail."
                )

            return osrm_res

        # 3d. If both failed due to an upstream service failure (e.g. 503, 429), surface it accurately
        if last_service_err is not None:
            raise last_service_err

        # 3e. Genuine lack of connected trails and roads across alpine wilderness
        straight_line_km = haversine_distance_km(origin_lat, origin_lon, dest_lat, dest_lon)
        detail_msg = (
            f"No verified connected hiking trail found between '{origin_name}' and '{dest_name}' "
            f"({straight_line_km:.1f} km apart). The path crosses unmapped alpine terrain, dense forest, or cliffs. "
            "The system refuses to invent a fake straight line across hazardous terrain. "
            "You can switch to 'Draw my route' to trace your intended path manually."
        )
        logger.info(detail_msg)
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=detail_msg,
        )

    def _build_trail_network_route(
        self,
        origin_lat: float, origin_lon: float, origin_name: str, origin_snap: Dict[str, Any],
        dest_lat: float, dest_lon: float, dest_name: str, dest_snap: Dict[str, Any],
    ) -> RouteResult:
        """Slices verified trail geometry and builds an evidence-backed RouteResult."""
        trail = origin_snap["trail_feature"]
        props = trail.get("properties", {})
        coords = trail.get("geometry", {}).get("coordinates", [])

        idx_orig = origin_snap["segment_index"]
        idx_dest = dest_snap["segment_index"]

        # Check river barrier on connectors
        has_barr_orig, barr_reason_orig = self.check_barrier_intersection(
            origin_lat, origin_lon, origin_snap["proj_lat"], origin_snap["proj_lon"]
        )
        if has_barr_orig:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"Route blocked: {barr_reason_orig}",
            )
        has_barr_dest, barr_reason_dest = self.check_barrier_intersection(
            dest_lat, dest_lon, dest_snap["proj_lat"], dest_snap["proj_lon"]
        )
        if has_barr_dest:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"Route blocked: {barr_reason_dest}",
            )

        # Slice coordinates along trail
        if idx_orig <= idx_dest:
            sliced = (
                [[origin_snap["proj_lon"], origin_snap["proj_lat"]]]
                + coords[idx_orig + 1 : idx_dest + 1]
                + [[dest_snap["proj_lon"], dest_snap["proj_lat"]]]
            )
        else:
            sliced = (
                [[origin_snap["proj_lon"], origin_snap["proj_lat"]]]
                + list(reversed(coords[idx_dest + 1 : idx_orig + 1]))
                + [[dest_snap["proj_lon"], dest_snap["proj_lat"]]]
            )

        # Clean duplicates
        clean_coords = []
        for c in sliced:
            if not clean_coords or (clean_coords[-1][0] != c[0] or clean_coords[-1][1] != c[1]):
                clean_coords.append(c)

        if len(clean_coords) < 2:
            clean_coords = [
                [origin_snap["proj_lon"], origin_snap["proj_lat"]],
                [dest_snap["proj_lon"], dest_snap["proj_lat"]]
            ]

        # Calculate exact distance along sliced polyline
        dist_km = 0.0
        for i in range(len(clean_coords) - 1):
            p1 = clean_coords[i]
            p2 = clean_coords[i + 1]
            dist_km += haversine_distance_km(p1[1], p1[0], p2[1], p2[0])
        dist_km = round(dist_km, 2)

        # Base hiking pace in Himalayas ~ 3.0 km/h with elevation adjustment
        avg_speed_kmh = 3.2
        dur_mins = round((dist_km / avg_speed_kmh) * 60.0, 1)

        # Evaluate snap distances for verification status
        snap_origin_m = origin_snap["distance_m"]
        snap_dest_m = dest_snap["distance_m"]
        max_snap_m = max(snap_origin_m, snap_dest_m)

        proposed_connector = None
        if max_snap_m <= 300.0:
            status_label = "MAPPED_TRAIL"
            unverified_reason = None
        elif max_snap_m <= 1500.0:
            status_label = "PARTIALLY_VERIFIED"
            unverified_reason = (
                f"Route connects to mapped trail via a {int(max_snap_m)}m off-trail approach. "
                "Exercise caution on unmapped approach terrain."
            )
            # Create proposed connector GeoJSON line
            proposed_connector = {
                "type": "FeatureCollection",
                "features": [
                    {
                        "type": "Feature",
                        "geometry": {
                            "type": "LineString",
                            "coordinates": [
                                [origin_lon, origin_lat],
                                [origin_snap["proj_lon"], origin_snap["proj_lat"]]
                            ]
                        },
                        "properties": {
                            "type": "proposed_connector",
                            "distance_m": round(snap_origin_m, 1)
                        }
                    }
                ]
            }
        else:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=(
                    f"Requested coordinates are {int(max_snap_m)}m away from the nearest trail. "
                    "Cannot establish a safe trail connection without traversing unmapped wilderness."
                )
            )

        # Sample waypoints along genuine trail polyline
        sampled = sample_points_along_polyline(clean_coords, interval_km=2.0, max_points=12)
        waypoints = []
        for idx, (w_lat, w_lon, w_dist) in enumerate(sampled):
            w_time = (w_dist / avg_speed_kmh) * 60.0 if avg_speed_kmh > 0 else 0.0
            name = (
                f"Trailhead: {origin_name}"
                if idx == 0
                else (f"Destination: {dest_name}" if idx == len(sampled) - 1 else f"Trail Waypoint {idx}")
            )
            waypoints.append(
                Waypoint(
                    index=idx,
                    name=name,
                    latitude=round(w_lat, 5),
                    longitude=round(w_lon, 5),
                    distance_from_start_km=round(w_dist, 2),
                    estimated_travel_time_minutes=round(w_time, 1),
                )
            )

        import uuid
        route_id = str(uuid.uuid4())
        trail_name = props.get("name", "Verified Himalayan Trail")

        res = RouteResult(
            route_id=route_id,
            title=f"{origin_name} to {dest_name} (via {trail_name})",
            origin_name=origin_name,
            origin_lat=origin_lat,
            origin_lon=origin_lon,
            destination_name=dest_name,
            destination_lat=dest_lat,
            destination_lon=dest_lon,
            travel_mode="hiking",
            distance_km=dist_km,
            duration_minutes=dur_mins,
            geometry_geojson={"type": "LineString", "coordinates": clean_coords},
            waypoints=waypoints,
            alternatives=[],
        )
        res.verification_status = status_label
        res.unverified_reason = unverified_reason
        res.proposed_connector_geojson = proposed_connector
        res.trail_name = trail_name
        return res


trail_routing_engine = TrailRoutingEngine()
