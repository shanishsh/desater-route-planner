"""Service for digital elevation profiling, slope analysis, and Tobler hiking velocity calculations."""
import math
import logging
from datetime import datetime, timezone, timedelta
from typing import List, Tuple, Dict, Any, Optional
import httpx

logger = logging.getLogger(__name__)


def haversine_distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate great-circle distance between two coordinates in meters."""
    r = 6371000.0  # Earth radius in meters
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    a = (
        math.sin(delta_phi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r * c


def tobler_hiking_speed_kmh(slope_fraction: float) -> float:
    """
    Tobler's Hiking Velocity Function:
    v = 6 * exp(-3.5 * |s + 0.05|) in km/h
    where s is fractional slope (dh / dx).
    Clamped between 0.8 km/h (severe uphill scramble) and 5.5 km/h (optimal gentle downhill).
    """
    speed = 6.0 * math.exp(-3.5 * abs(slope_fraction + 0.05))
    return max(0.8, min(5.5, speed))


class ElevationTerrainService:
    """Fetches DEM elevation data and analyzes terrain slope and hiking transit times."""

    def __init__(self):
        self.elevation_api_url = "https://api.open-meteo.com/v1/elevation"

    async def fetch_elevations_for_coordinates(
        self, coordinates: List[Tuple[float, float]]
    ) -> List[float]:
        """
        Fetches elevations in meters for a list of (latitude, longitude) tuples.
        Batches queries to prevent URI overflow.
        """
        if not coordinates:
            return []

        elevations: List[float] = []
        batch_size = 80  # Keep URLs safe and well under 2KB

        for i in range(0, len(coordinates), batch_size):
            batch = coordinates[i : i + batch_size]
            lats_str = ",".join(f"{pt[0]:.5f}" for pt in batch)
            lons_str = ",".join(f"{pt[1]:.5f}" for pt in batch)

            try:
                async with httpx.AsyncClient(timeout=3.5) as client:
                    resp = await client.get(
                        self.elevation_api_url,
                        params={"latitude": lats_str, "longitude": lons_str},
                    )
                    if resp.status_code == 200:
                        data = resp.json()
                        batch_elevs = data.get("elevation", [])
                        elevations.extend([float(e) if e is not None else 0.0 for e in batch_elevs])
                    else:
                        logger.warning(f"Elevation API error {resp.status_code}: {resp.text}")
                        elevations.extend([0.0] * len(batch))
            except Exception as e:
                logger.warning(f"Elevation request failed: {e}")
                elevations.extend([0.0] * len(batch))

        return elevations

    async def compute_elevation_profile(
        self,
        coordinates: List[Tuple[float, float]],
        departure_time: Optional[datetime] = None,
    ) -> Dict[str, Any]:
        """
        Given a list of (lat, lon) coordinates along a trail:
        1. Retrieves elevation profile from DEM
        2. Computes segment slope, elevation changes, and Tobler transit speeds
        3. Generates arrival timestamps for each waypoint
        """
        if not coordinates:
            return {
                "points": [],
                "total_ascent_m": 0.0,
                "total_descent_m": 0.0,
                "min_elevation_m": 0.0,
                "max_elevation_m": 0.0,
                "total_distance_km": 0.0,
                "total_duration_minutes": 0.0,
            }

        elevations = await self.fetch_elevations_for_coordinates(coordinates)
        dep_time = departure_time or datetime.now(timezone.utc)

        profile_points = []
        total_dist_m = 0.0
        total_ascent_m = 0.0
        total_descent_m = 0.0
        cumulative_minutes = 0.0

        for i, (lat, lon) in enumerate(coordinates):
            elev = elevations[i] if i < len(elevations) else 0.0

            if i == 0:
                slope_fraction = 0.0
                slope_deg = 0.0
                speed_kmh = 4.5  # default base walking speed
                seg_minutes = 0.0
            else:
                prev_lat, prev_lon = coordinates[i - 1]
                prev_elev = elevations[i - 1] if i - 1 < len(elevations) else 0.0

                seg_dist_m = haversine_distance_m(prev_lat, prev_lon, lat, lon)
                total_dist_m += seg_dist_m

                elev_diff_m = elev - prev_elev
                if elev_diff_m > 0:
                    total_ascent_m += elev_diff_m
                else:
                    total_descent_m += abs(elev_diff_m)

                # Slope fraction = rise / run
                if seg_dist_m > 0.5:
                    slope_fraction = elev_diff_m / seg_dist_m
                    slope_deg = math.degrees(math.atan(abs(slope_fraction)))
                else:
                    slope_fraction = 0.0
                    slope_deg = 0.0

                speed_kmh = tobler_hiking_speed_kmh(slope_fraction)
                # seg_minutes = (seg_dist_km / speed_kmh) * 60
                seg_dist_km = seg_dist_m / 1000.0
                seg_minutes = (seg_dist_km / speed_kmh) * 60.0
                cumulative_minutes += seg_minutes

            arr_time = dep_time + timedelta(minutes=cumulative_minutes)

            profile_points.append(
                {
                    "index": i,
                    "latitude": lat,
                    "longitude": lon,
                    "elevation_m": round(elev, 1),
                    "distance_from_start_km": round(total_dist_m / 1000.0, 3),
                    "slope_degrees": round(slope_deg, 1),
                    "slope_fraction": round(slope_fraction, 4),
                    "hiking_speed_kmh": round(speed_kmh, 2),
                    "cumulative_minutes": round(cumulative_minutes, 1),
                    "estimated_arrival_iso": arr_time.isoformat(),
                }
            )

        valid_elevs = [p["elevation_m"] for p in profile_points if p["elevation_m"] > 0]
        min_elev = min(valid_elevs) if valid_elevs else 0.0
        max_elev = max(valid_elevs) if valid_elevs else 0.0

        return {
            "points": profile_points,
            "total_ascent_m": round(total_ascent_m, 1),
            "total_descent_m": round(total_descent_m, 1),
            "min_elevation_m": round(min_elev, 1),
            "max_elevation_m": round(max_elev, 1),
            "total_distance_km": round(total_dist_m / 1000.0, 2),
            "total_duration_minutes": round(cumulative_minutes, 1),
        }


elevation_service = ElevationTerrainService()

