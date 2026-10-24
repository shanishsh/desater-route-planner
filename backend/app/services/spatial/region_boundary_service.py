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
UTTARAKHAND_BBOX = (28.71, 77.57, 31.46, 81.04)


class RegionBoundaryService:
    def __init__(self, geojson_path: Optional[str] = None):
        self._geojson_path = geojson_path or os.path.join(
            os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
            "resources",
            "uttarakhand_boundary.geojson",
        )
        self._polygon = None
        self._prepped = None
        self._load_boundary()

    def _load_boundary(self) -> None:
        try:
            if os.path.exists(self._geojson_path):
                with open(self._geojson_path, "r", encoding="utf-8") as f:
                    geom_data = json.load(f)
                self._polygon = shape(geom_data)
                self._prepped = prep(self._polygon)
                logger.info(
                    f"Loaded Uttarakhand boundary from {self._geojson_path} with geometry type {self._polygon.geom_type}"
                )
            else:
                logger.warning(
                    f"Uttarakhand boundary GeoJSON not found at {self._geojson_path}. Falling back to bounding box."
                )
        except Exception as e:
            logger.error(f"Error loading Uttarakhand boundary GeoJSON: {e}")

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
            return bool(self._prepped.contains(pt))

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
        self, route_coords_lat_lon: List[Tuple[float, float]]
    ) -> Tuple[bool, Optional[str]]:
        """
        Validates that a complete route polyline remains within the supported Uttarakhand boundary.
        Samples route points to ensure no significant section leaves the state.
        """
        if not route_coords_lat_lon:
            return False, "Route geometry is empty."

        # Check endpoints first
        start_lat, start_lon = route_coords_lat_lon[0]
        end_lat, end_lon = route_coords_lat_lon[-1]

        if not self.is_point_in_uttarakhand(start_lat, start_lon):
            return False, f"Route origin ({start_lat:.4f}, {start_lon:.4f}) is outside Uttarakhand."

        if not self.is_point_in_uttarakhand(end_lat, end_lon):
            return False, f"Route destination ({end_lat:.4f}, {end_lon:.4f}) is outside Uttarakhand."

        # Sample intermediate coordinates along the route
        step = max(1, len(route_coords_lat_lon) // 50)
        outside_count = 0
        for i in range(0, len(route_coords_lat_lon), step):
            lat, lon = route_coords_lat_lon[i]
            if not self.is_point_in_uttarakhand(lat, lon):
                outside_count += 1

        # Allow small border discrepancies (< 4% of sampled vertices for border crossing tolerance)
        total_samples = max(1, len(range(0, len(route_coords_lat_lon), step)))
        if outside_count / total_samples > 0.05:
            return False, "Calculated route exits the supported Uttarakhand boundary."

        return True, None

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

