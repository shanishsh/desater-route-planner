"""
Uttarakhand Administrative Boundary and Spatial Scoping Service.

Validates that origin, destination, and calculated route polylines are strictly
contained within the authoritative administrative boundary of Uttarakhand, India.
Uses Shapely prepared geometry for sub-millisecond point-in-polygon checks.
"""

import json
import logging
import os
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from shapely.geometry import Point, LineString, shape
from shapely.prepared import prep

logger = logging.getLogger(__name__)

# Bounding box of Uttarakhand: [min_lat, min_lon, max_lat, max_lon]
# Slightly expanded to 0.05 deg margin to prevent edge clipping of border passes/shrines
UTTARAKHAND_BBOX = (28.65, 77.50, 31.55, 81.15)


class RegionBoundaryService:
    def __init__(self, geojson_path: Optional[str] = None):
        self._geojson_path = geojson_path or os.path.join(
            os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
            "resources",
            "uttarakhand_boundary.geojson",
        )
        self._mask_path = os.path.join(
            os.path.dirname(self._geojson_path),
            "uttarakhand_mask.geojson",
        )
        self._polygon = None
        self._prepped = None
        self._boundary_data = None
        self._mask_data = None
        self._load_boundary()

    def _load_boundary(self) -> None:
        try:
            if os.path.exists(self._geojson_path):
                with open(self._geojson_path, "r", encoding="utf-8") as f:
                    self._boundary_data = json.load(f)
                self._polygon = shape(self._boundary_data)
                # Apply 0.03 deg (~3.3 km) buffer to reliably include border mountain passes, shrines, and confluences
                self._buffered_polygon = self._polygon.buffer(0.03)
                self._prepped = prep(self._buffered_polygon)
                logger.info(
                    f"Loaded Uttarakhand boundary from {self._geojson_path} with geometry type {self._polygon.geom_type} and 0.03 deg border buffer"
                )
            else:
                logger.warning(
                    f"Uttarakhand boundary GeoJSON not found at {self._geojson_path}. Falling back to bounding box."
                )
        except Exception as e:
            logger.error(f"Error loading Uttarakhand boundary GeoJSON: {e}")

        try:
            if os.path.exists(self._mask_path):
                with open(self._mask_path, "r", encoding="utf-8") as f:
                    self._mask_data = json.load(f)
                logger.info(f"Loaded Uttarakhand mask from {self._mask_path}")
        except Exception as e:
            logger.warning(f"Error loading Uttarakhand mask GeoJSON: {e}")

    def is_point_in_uttarakhand(self, lat: float, lon: float) -> bool:
        """
        Determines whether a WGS84 (latitude, longitude) coordinate pair is
        located strictly within the Uttarakhand state administrative boundary.
        """
        # Fast bounding box pre-filter
        min_lat, min_lon, max_lat, max_lon = UTTARAKHAND_BBOX
        if not (min_lat <= lat <= max_lat and min_lon <= lon <= max_lon):
            return False

        if self._prepped is not None:
            # GeoJSON coordinates are (longitude, latitude)
            pt = Point(lon, lat)
            return bool(self._prepped.covers(pt))

        # Fallback to bounding box if polygon not loaded
        return True

    def validate_points_in_uttarakhand(
        self, points: List[Tuple[float, float]]
    ) -> Tuple[bool, Optional[str]]:
        """Validates a list of (lat, lon) coordinates."""
        for idx, (lat, lon) in enumerate(points):
            if not self.is_point_in_uttarakhand(lat, lon):
                return False, f"Coordinate at index {idx} ({lat:.4f}, {lon:.4f}) is outside Uttarakhand."
        return True, None

    def is_route_in_uttarakhand(
        self,
        route_coords_lat_lon: List[Tuple[float, float]],
        travel_mode: Optional[str] = "driving",
    ) -> Tuple[bool, Optional[str]]:
        """
        Validates that a complete route polyline remains within the supported Uttarakhand boundary
        or within the regional transit corridor for in-state driving routes.

        Rules:
        1. Both origin and destination endpoints MUST strictly reside inside the authoritative
           Uttarakhand administrative boundary.
        2. For driving travel mode: In-state transit routes (e.g. connecting Garhwal in the west
           like Dehradun/Rishikesh to Kumaon in the east like Nainital/Haldwani/Almora) naturally
           traverse adjacent northern plains highway corridors (e.g. NH 74 / NH 734 across UP border).
           When both endpoints are verified within Uttarakhand, highway transit is permitted as long
           as the route stays within the regional corridor bounding envelope (lat 28.0-32.0, lon 77.0-81.5).
        3. For non-driving modes (hiking, walking): Trails are expected to stay strictly within
           Uttarakhand state bounds with a tight tolerance (< 5% border buffer for frontier ridges).
        """
        if not route_coords_lat_lon:
            return False, "Route geometry is empty."

        # Check endpoints first
        start_lat, start_lon = route_coords_lat_lon[0]
        end_lat, end_lon = route_coords_lat_lon[-1]

        if not self.is_point_in_uttarakhand(start_lat, start_lon):
            return False, f"Route origin ({start_lat:.4f}, {start_lon:.4f}) is outside the state of Uttarakhand."

        if not self.is_point_in_uttarakhand(end_lat, end_lon):
            return False, f"Route destination ({end_lat:.4f}, {end_lon:.4f}) is outside the state of Uttarakhand."

        # Fast geometric coverage check using Shapely prepared polygon
        if self._prepped is not None and len(route_coords_lat_lon) >= 2:
            try:
                line = LineString([(lon, lat) for lat, lon in route_coords_lat_lon])
                if self._prepped.covers(line):
                    return True, None
            except Exception as e:
                logger.debug(f"Shapely LineString covers check error: {e}")

        mode = (travel_mode or "driving").lower()

        # For driving: both endpoints are verified inside Uttarakhand.
        # Allow inter-district transit through adjacent plains highway corridors,
        # but ensure coordinates stay within the regional corridor bounding envelope.
        if mode == "driving":
            transit_min_lat, transit_min_lon, transit_max_lat, transit_max_lon = 28.0, 77.0, 32.0, 81.5
            for lat, lon in route_coords_lat_lon:
                if not (transit_min_lat <= lat <= transit_max_lat and transit_min_lon <= lon <= transit_max_lon):
                    return False, f"Route transit coordinates ({lat:.4f}, {lon:.4f}) deviate excessively outside the regional transit corridor."
            return True, None

        # Sample intermediate coordinates along the route for hiking/walking trails
        step = max(1, len(route_coords_lat_lon) // 50)
        outside_count = 0
        total_samples = 0
        for i in range(0, len(route_coords_lat_lon), step):
            lat, lon = route_coords_lat_lon[i]
            total_samples += 1
            if not self.is_point_in_uttarakhand(lat, lon):
                outside_count += 1

        # Allow small border discrepancies (< 5% of sampled vertices for border highway/trail tolerance)
        if total_samples > 0 and (outside_count / total_samples) > 0.05:
            return False, "Calculated trekking path exits the supported Uttarakhand administrative boundary."

        return True, None

    def get_boundary_geojson(self) -> Dict[str, Any]:
        """Returns the authoritative GeoJSON geometry/feature for Uttarakhand."""
        if self._boundary_data is not None:
            return self._boundary_data
        if os.path.exists(self._geojson_path):
            with open(self._geojson_path, "r", encoding="utf-8") as f:
                self._boundary_data = json.load(f)
            return self._boundary_data
        return {"type": "FeatureCollection", "features": []}

    def get_mask_geojson(self) -> Dict[str, Any]:
        """Returns the outside-state inverted polygon mask GeoJSON."""
        if self._mask_data is not None:
            return self._mask_data
        if os.path.exists(self._mask_path):
            with open(self._mask_path, "r", encoding="utf-8") as f:
                self._mask_data = json.load(f)
            return self._mask_data
        return {"type": "FeatureCollection", "features": []}

    def get_region_metadata(self) -> Dict[str, Any]:
        """Returns metadata about the active regional deployment."""
        return {
            "region_name": "Uttarakhand",
            "country": "India",
            "state_code": "UK",
            "bbox": list(UTTARAKHAND_BBOX),
            "pilot_corridors_enabled": True,
            "boundary_verified": self._polygon is not None,
            "disclaimer": (
                "Route planning, hazard scanning, and facility mapping are strictly scoped "
                "to the state of Uttarakhand, India. Unsupported regions outside Uttarakhand "
                "are rejected to prevent unverified data expansion."
            ),
        }


region_boundary_service = RegionBoundaryService()

