"""
Historical Landslide and Chronic Slope Instability Service.

Loads verified historical landslide and chronic mass-movement records (from GSI Bhukosh,
USDMA, WIHG, and BRO) and computes spatial intersection and proximity along route corridors.
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


class HistoricalLandslideService:
    def __init__(self, geojson_path: Optional[str] = None):
        self._geojson_path = geojson_path or os.path.join(
            os.path.dirname(os.path.dirname(os.path.dirname(__file__))),
            "resources",
            "uttarakhand_historical_landslides.geojson",
        )
        self._records: List[Dict[str, Any]] = []
        self._load()

    def _load(self):
        try:
            if os.path.exists(self._geojson_path):
                with open(self._geojson_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                self._records = data.get("features", [])
                logger.info(f"Loaded {len(self._records)} historical landslide records from {self._geojson_path}")
            else:
                logger.warning(f"Historical landslide dataset not found at {self._geojson_path}")
        except Exception as e:
            logger.error(f"Failed to load historical landslide dataset: {e}")

    def get_corridor_landslides(
        self,
        route_coords: List[List[float]],
        corridor_radius_m: float = 3000.0,
    ) -> Tuple[Dict[str, Any], Dict[str, Any]]:
        """
        Identifies historical landslides within corridor_radius_m of route coordinates.
        Returns: (landslides_geojson, summary_dict)
        """
        if not self._records or not route_coords:
            return {"type": "FeatureCollection", "features": []}, {
                "count": 0,
                "nearest_landslide_name": None,
                "nearest_distance_m": None,
                "high_risk_zones_intersecting": 0,
            }

        # Normalize route coordinates to [(lat, lon)]
        route_pts = []
        for pt in route_coords:
            if len(pt) >= 2:
                if pt[0] > 50.0:  # lon, lat
                    route_pts.append((pt[1], pt[0]))
                else:  # lat, lon
                    route_pts.append((pt[0], pt[1]))

        intersecting_features = []
        min_overall_dist_m = float("inf")
        nearest_record_name = None
        high_risk_count = 0

        for feat in self._records:
            coords = feat.get("geometry", {}).get("coordinates", [])
            if len(coords) < 2:
                continue
            ls_lon, ls_lat = coords[0], coords[1]
            props = feat.get("properties", {})
            hazard_radius = float(props.get("radius_meters", 1000))
            threshold_dist = corridor_radius_m + hazard_radius

            # Find minimum distance from this landslide to any route point
            min_dist_to_route = float("inf")
            for rlat, rlon in route_pts:
                d = haversine_m(ls_lat, ls_lon, rlat, rlon)
                if d < min_dist_to_route:
                    min_dist_to_route = d

            if min_dist_to_route <= threshold_dist:
                is_direct_intersection = min_dist_to_route <= hazard_radius
                if is_direct_intersection or props.get("severity") in ("HIGH", "CATASTROPHIC"):
                    high_risk_count += 1

                enriched_props = dict(props)
                enriched_props["distance_to_route_m"] = round(min_dist_to_route, 1)
                enriched_props["intersects_route"] = is_direct_intersection

                intersecting_features.append({
                    "type": "Feature",
                    "id": feat.get("id"),
                    "geometry": feat.get("geometry"),
                    "properties": enriched_props,
                })

                if min_dist_to_route < min_overall_dist_m:
                    min_overall_dist_m = min_dist_to_route
                    nearest_record_name = props.get("name")

        summary = {
            "count": len(intersecting_features),
            "nearest_landslide_name": nearest_record_name,
            "nearest_distance_m": round(min_overall_dist_m, 1) if min_overall_dist_m != float("inf") else None,
            "high_risk_zones_intersecting": high_risk_count,
        }

        return {
            "type": "FeatureCollection",
            "features": intersecting_features,
        }, summary


historical_landslide_service = HistoricalLandslideService()
