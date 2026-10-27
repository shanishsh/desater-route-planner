"""
Waterway and River Proximity Service.

Identifies major Himalayan river channels and glacial torrents in proximity to a route.
Calculates minimum distance to riverbed to flag high-risk flash flood and debris surge corridors.
"""

import json
import logging
import math
import os
from typing import Any, Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371000.0
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = (
        math.sin(d_lat / 2.0) ** 2
        + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(d_lon / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r * c


class WaterwayService:
    def __init__(self, geojson_path: Optional[str] = None):
        self._geojson_path = geojson_path or os.path.join(
            os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
            "resources",
            "uttarakhand_major_waterways.geojson",
        )
        self._features: List[Dict[str, Any]] = []
        self._load()

    def _load(self):
        try:
            if os.path.exists(self._geojson_path):
                with open(self._geojson_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                self._features = data.get("features", [])
                logger.info(f"Loaded {len(self._features)} waterway features from {self._geojson_path}")
            else:
                logger.warning(f"Waterways GeoJSON not found at {self._geojson_path}")
        except Exception as e:
            logger.error(f"Failed to load waterways dataset: {e}")

    def get_corridor_waterways(
        self,
        route_coords: List[List[float]],
        corridor_radius_m: float = 3000.0,
    ) -> Tuple[Dict[str, Any], Dict[str, Any]]:
        """
        Finds river features within corridor_radius_m of route coordinates.
        Returns: (waterways_geojson, summary_dict)
        """
        if not self._features or not route_coords:
            return {"type": "FeatureCollection", "features": []}, {
                "rivers_detected": [],
                "nearest_river_name": None,
                "nearest_distance_m": None,
                "is_river_gorge_route": False,
            }

        # Normalize route points to [(lat, lon)]
        route_pts = []
        for pt in route_coords:
            if len(pt) >= 2:
                if pt[0] > 50.0:
                    route_pts.append((pt[1], pt[0]))
                else:
                    route_pts.append((pt[0], pt[1]))

        matching_features = []
        min_overall_dist_m = float("inf")
        nearest_river_name = None
        detected_names = set()

        for feat in self._features:
            line_coords = feat.get("geometry", {}).get("coordinates", [])
            props = feat.get("properties", {})
            river_name = props.get("name", "Unnamed River")

            # Calculate minimum distance from river line vertices to route points
            min_dist = float("inf")
            for r_lon, r_lat in line_coords:
                for pt_lat, pt_lon in route_pts:
                    d = haversine_m(r_lat, r_lon, pt_lat, pt_lon)
                    if d < min_dist:
                        min_dist = d

            if min_dist <= corridor_radius_m:
                detected_names.add(river_name)
                enriched = dict(props)
                enriched["distance_to_route_m"] = round(min_dist, 1)
                matching_features.append({
                    "type": "Feature",
                    "id": feat.get("id"),
                    "geometry": feat.get("geometry"),
                    "properties": enriched,
                })

                if min_dist < min_overall_dist_m:
                    min_overall_dist_m = min_dist
                    nearest_river_name = river_name

        summary = {
            "rivers_detected": sorted(list(detected_names)),
            "nearest_river_name": nearest_river_name,
            "nearest_distance_m": round(min_overall_dist_m, 1) if min_overall_dist_m != float("inf") else None,
            "is_river_gorge_route": min_overall_dist_m <= 500.0 if min_overall_dist_m != float("inf") else False,
        }

        return {
            "type": "FeatureCollection",
            "features": matching_features,
        }, summary


waterway_service = WaterwayService()
