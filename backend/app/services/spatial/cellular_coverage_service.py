"""
Cellular Network Coverage and Infrastructure Service.

Processes OpenCelliD cell mast records along route corridors, constructs GeoJSON point features
for known cellular infrastructure, and classifies route segments into defensible coverage categories:
- PROBABLE_COVERAGE: Route segment in proximity to reported cell infrastructure with estimated line-of-sight.
- COVERAGE_UNKNOWN: Remote mountain trail or valley corridor without verified cell mast telemetry.

CRITICAL ARCHITECTURAL SAFETY PRINCIPLES:
1. Missing OpenCelliD records DO NOT mean zero cellular reception. In mountainous terrain, distant
   high-ridge towers may propagate signal across valleys, while close towers may be shadowed by terrain.
2. The platform NEVER labels unverified mountain trail sections as "No network" or "Dead zone".
   Instead, unverified sections are strictly labeled "COVERAGE_UNKNOWN".
3. The platform NEVER guarantees that emergency calls (112), SMS, or satellite SOS will succeed or fail
   based on cell mast proximity.
"""

import math
from typing import Any, Dict, List, Optional, Tuple


def haversine_distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculates great-circle distance between two coordinates in meters."""
    r = 6371000.0  # Earth radius in meters
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = (
        math.sin(d_lat / 2.0) ** 2
        + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(d_lon / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r * c


class CellularCoverageService:
    """Builds GeoJSON layers and defensible coverage assessments along route corridors."""

    CELLULAR_DISCLAIMER = (
        "Cellular coverage is an estimate derived from reported OpenCelliD mast locations and nominal "
        "radio propagation. Himalayan topography, steep ridges, and valleys create significant RF shadow zones. "
        "Coverage cannot be guaranteed. Emergency call (112) or SMS success is not guaranteed."
    )

    def build_cellular_sites_geojson(self, cell_records: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Converts raw cell mast records into a GeoJSON Point FeatureCollection.
        Preserves operator, radio technology, mast range, and provenance metadata.
        """
        features = []
        for cell in cell_records:
            lat = cell.get("latitude")
            lon = cell.get("longitude")
            if lat is None or lon is None:
                continue

            operator = cell.get("operator", "Unknown Carrier")
            radio = cell.get("radio", "LTE")
            range_m = float(cell.get("range_m", 1500.0))
            cell_id = cell.get("id") or f"cell_{round(lat, 4)}_{round(lon, 4)}"

            features.append({
                "type": "Feature",
                "id": cell_id,
                "geometry": {
                    "type": "Point",
                    "coordinates": [round(float(lon), 6), round(float(lat), 6)],
                },
                "properties": {
                    "id": cell_id,
                    "name": f"{operator} ({radio})",
                    "icon_id": "poi-cell-tower",
                    "category": "CELL_TOWER",
                    "operator": operator,
                    "radio": radio,
                    "range_m": range_m,
                    "mcc": cell.get("mcc"),
                    "mnc": cell.get("mnc"),
                    "source": cell.get("source", "OpenCelliD"),
                    "source_url": cell.get("source_url", "https://opencellid.org/"),
                    "disclaimer": self.CELLULAR_DISCLAIMER,
                },
            })

        return {
            "type": "FeatureCollection",
            "features": features,
        }

    def build_cellular_range_geojson(
        self,
        cell_records: List[Dict[str, Any]],
        num_points: int = 32,
    ) -> Dict[str, Any]:
        """
        Builds a GeoJSON Polygon FeatureCollection representing estimated transmission range halos
        for each known cell tower. Uses geodesic circular polygon calculation with radius range_m.
        """
        features = []
        r_earth = 6371000.0  # Earth radius in meters

        for cell in cell_records:
            lat = cell.get("latitude")
            lon = cell.get("longitude")
            if lat is None or lon is None:
                continue

            try:
                lat_f = float(lat)
                lon_f = float(lon)
                range_m = max(float(cell.get("range_m", 2500.0)), 500.0)
            except (ValueError, TypeError):
                continue

            operator = cell.get("operator", "Unknown Carrier")
            radio = cell.get("radio", "LTE")
            cell_id = cell.get("id") or f"cell_{round(lat_f, 4)}_{round(lon_f, 4)}"

            # Compute geodesic circular polygon vertices
            coords = []
            lat_rad = math.radians(lat_f)
            lon_rad = math.radians(lon_f)
            d_div_r = range_m / r_earth

            for i in range(num_points + 1):
                bearing = 2.0 * math.pi * (i % num_points) / num_points
                pt_lat = math.asin(
                    math.sin(lat_rad) * math.cos(d_div_r)
                    + math.cos(lat_rad) * math.sin(d_div_r) * math.cos(bearing)
                )
                pt_lon = lon_rad + math.atan2(
                    math.sin(bearing) * math.sin(d_div_r) * math.cos(lat_rad),
                    math.cos(d_div_r) - math.sin(lat_rad) * math.sin(pt_lat),
                )
                coords.append([round(math.degrees(pt_lon), 6), round(math.degrees(pt_lat), 6)])

            features.append({
                "type": "Feature",
                "id": f"range_{cell_id}",
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [coords],
                },
                "properties": {
                    "id": cell_id,
                    "tower_id": cell_id,
                    "operator": operator,
                    "radio": radio,
                    "range_m": range_m,
                    "fill_color": "#7C4DFF",
                    "stroke_color": "#651FFF",
                },
            })

        return {
            "type": "FeatureCollection",
            "features": features,
        }

    def build_cellular_coverage_geojson(
        self,
        route_coords: List[List[float]],
        cell_records: List[Dict[str, Any]],
        default_range_m: float = 2500.0,
    ) -> Tuple[Dict[str, Any], Dict[str, Any]]:
        """
        Segments route coordinates into PROBABLE_COVERAGE and COVERAGE_UNKNOWN LineStrings.
        Returns (coverage_geojson, summary_dict).
        """
        if not route_coords or len(route_coords) < 2:
            return {"type": "FeatureCollection", "features": []}, {
                "total_km": 0.0,
                "probable_coverage_km": 0.0,
                "unknown_coverage_km": 0.0,
                "coverage_status": "COVERAGE_UNKNOWN",
                "operators_detected": [],
                "disclaimer": self.CELLULAR_DISCLAIMER,
            }

        # Normalize coordinates to [[lon, lat], ...]
        normalized: List[Tuple[float, float]] = []
        for pt in route_coords:
            if len(pt) >= 2:
                if pt[0] > 50.0:  # lon, lat
                    normalized.append((pt[0], pt[1]))
                else:  # lat, lon
                    normalized.append((pt[1], pt[0]))

        # Prepare cell mast positions and ranges
        cells_data = []
        detected_operators = set()
        for c in cell_records:
            clat = c.get("latitude")
            clon = c.get("longitude")
            if clat is not None and clon is not None:
                op = c.get("operator", "Unknown Carrier")
                if op != "Unknown Carrier":
                    detected_operators.add(op)
                rng = float(c.get("range_m", default_range_m))
                cells_data.append((clat, clon, max(rng, 1000.0), op))

        features = []
        total_dist_m = 0.0
        covered_dist_m = 0.0
        unknown_dist_m = 0.0

        current_category: Optional[str] = None
        current_segment: List[List[float]] = []
        current_operators: set = set()

        for i in range(len(normalized) - 1):
            p1 = normalized[i]
            p2 = normalized[i + 1]
            seg_dist = haversine_distance_m(p1[1], p1[0], p2[1], p2[0])
            total_dist_m += seg_dist

            mid_lon = (p1[0] + p2[0]) / 2.0
            mid_lat = (p1[1] + p2[1]) / 2.0

            # Check if midpoint is within range of any known cell mast
            is_covered = False
            seg_ops = set()
            for clat, clon, crng, cop in cells_data:
                d = haversine_distance_m(mid_lat, mid_lon, clat, clon)
                if d <= crng:
                    is_covered = True
                    if cop != "Unknown Carrier":
                        seg_ops.add(cop)

            category = "PROBABLE_COVERAGE" if is_covered else "COVERAGE_UNKNOWN"

            if category == "PROBABLE_COVERAGE":
                covered_dist_m += seg_dist
            else:
                unknown_dist_m += seg_dist

            if current_category is None:
                current_category = category
                current_segment = [[p1[0], p1[1]], [p2[0], p2[1]]]
                current_operators = seg_ops
            elif current_category == category:
                current_segment.append([p2[0], p2[1]])
                current_operators.update(seg_ops)
            else:
                # Flush previous segment
                features.append({
                    "type": "Feature",
                    "geometry": {
                        "type": "LineString",
                        "coordinates": current_segment,
                    },
                    "properties": {
                        "coverage_status": current_category,
                        "label": "Probable Mobile Network" if current_category == "PROBABLE_COVERAGE" else "Coverage Unknown (No Cell Mast Records)",
                        "operators": list(current_operators),
                        "color": "#4CAF50" if current_category == "PROBABLE_COVERAGE" else "#9E9E9E",
                        "disclaimer": self.CELLULAR_DISCLAIMER,
                    },
                })
                current_category = category
                current_segment = [[p1[0], p1[1]], [p2[0], p2[1]]]
                current_operators = seg_ops

        # Flush final segment
        if current_segment and current_category:
            features.append({
                "type": "Feature",
                "geometry": {
                    "type": "LineString",
                    "coordinates": current_segment,
                },
                "properties": {
                    "coverage_status": current_category,
                    "label": "Probable Mobile Network" if current_category == "PROBABLE_COVERAGE" else "Coverage Unknown (No Cell Mast Records)",
                    "operators": list(current_operators),
                    "color": "#4CAF50" if current_category == "PROBABLE_COVERAGE" else "#9E9E9E",
                    "disclaimer": self.CELLULAR_DISCLAIMER,
                },
            })

        total_km = round(total_dist_m / 1000.0, 2)
        covered_km = round(covered_dist_m / 1000.0, 2)
        unknown_km = round(unknown_dist_m / 1000.0, 2)

        summary = {
            "total_km": total_km,
            "probable_coverage_km": covered_km,
            "unknown_coverage_km": unknown_km,
            "coverage_percentage": round((covered_km / max(total_km, 0.01)) * 100.0, 1),
            "operators_detected": sorted(list(detected_operators)),
            "cell_sites_count": len(cell_records),
            "disclaimer": self.CELLULAR_DISCLAIMER,
        }

        return {
            "type": "FeatureCollection",
            "features": features,
        }, summary


cellular_coverage_service = CellularCoverageService()
