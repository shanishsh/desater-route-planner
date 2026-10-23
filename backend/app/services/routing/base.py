"""Base interfaces and spatial utilities for routing services."""
import math
from dataclasses import dataclass, field
from datetime import datetime
from typing import List, Tuple, Dict, Any, Optional


@dataclass
class Waypoint:
    index: int
    name: str
    latitude: float
    longitude: float
    distance_from_start_km: float
    estimated_travel_time_minutes: float


@dataclass
class RouteAlternative:
    id: str
    title: str
    description: str
    distance_km: float
    duration_minutes: float
    geometry_geojson: Dict[str, Any]
    hazard_score: float = 0.0
    safety_label: str = "Safe"


@dataclass
class RouteResult:
    route_id: str
    title: str
    origin_name: str
    origin_lat: float
    origin_lon: float
    destination_name: str
    destination_lat: float
    destination_lon: float
    travel_mode: str
    distance_km: float
    duration_minutes: float
    geometry_geojson: Dict[str, Any]  # GeoJSON LineString
    waypoints: List[Waypoint] = field(default_factory=list)
    alternatives: List[RouteAlternative] = field(default_factory=list)


def haversine_distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate the great-circle distance between two points in kilometers."""
    R = 6371.0  # Earth's radius in kilometers
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (
        math.sin(dlat / 2.0) ** 2
        + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return R * c


def sample_points_along_polyline(
    coordinates: List[List[float]],
    interval_km: float = 3.0,
    max_points: int = 15,
) -> List[Tuple[float, float, float]]:
    """
    Given a list of [lon, lat] coordinates, samples points along the path approximately every interval_km.
    Returns list of tuples: (lat, lon, cumulative_distance_km).
    """
    if not coordinates:
        return []

    if len(coordinates) == 1:
        return [(coordinates[0][1], coordinates[0][0], 0.0)]

    # Compute cumulative distances
    points_with_dist = []
    total_dist = 0.0
    points_with_dist.append((coordinates[0][1], coordinates[0][0], 0.0))

    prev_lat, prev_lon = coordinates[0][1], coordinates[0][0]
    next_sample_dist = interval_km

    for coord in coordinates[1:]:
        curr_lon, curr_lat = coord[0], coord[1]
        segment_dist = haversine_distance_km(prev_lat, prev_lon, curr_lat, curr_lon)
        total_dist += segment_dist

        while total_dist >= next_sample_dist and len(points_with_dist) < max_points - 1:
            # Interpolate point
            overshoot = total_dist - next_sample_dist
            ratio = 1.0 - (overshoot / max_dist) if (max_dist := segment_dist) > 0 else 1.0
            ratio = max(0.0, min(1.0, ratio))
            interp_lat = prev_lat + (curr_lat - prev_lat) * ratio
            interp_lon = prev_lon + (curr_lon - prev_lon) * ratio
            points_with_dist.append((interp_lat, interp_lon, next_sample_dist))
            next_sample_dist += interval_km

        prev_lat, prev_lon = curr_lat, curr_lon

    # Always include final destination point
    dest_coord = coordinates[-1]
    if len(points_with_dist) == 1 or points_with_dist[-1][2] < total_dist - 0.5:
        points_with_dist.append((dest_coord[1], dest_coord[0], total_dist))

    return points_with_dist

