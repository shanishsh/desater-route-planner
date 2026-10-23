"""OSRM (Open Source Routing Machine) Provider."""
import uuid
import logging
from typing import Optional, List, Dict, Any
import httpx
from fastapi import HTTPException, status
from app.clients.circuit_breaker import CircuitBreaker
from app.services.routing.base import (
    RouteResult,
    Waypoint,
    RouteAlternative,
    haversine_distance_km,
    sample_points_along_polyline,
)

logger = logging.getLogger(__name__)


class OSRMRoutingProvider:
    """Routing provider that integrates OSRM for genuine road geometry and routing."""

    def __init__(self):
        self.circuit_breaker = CircuitBreaker("OSRM", failure_threshold=3, recovery_time_seconds=30.0)
        self._client: Optional[httpx.AsyncClient] = None
        self._loop = None
        self.last_attempt_at = None
        self.last_success_at = None
        self.last_status_code = None
        self.last_error = None

    async def get_client(self) -> httpx.AsyncClient:
        import asyncio
        loop = asyncio.get_running_loop()
        if self._client is None or self._client.is_closed or self._loop != loop:
            self._loop = loop
            self._client = httpx.AsyncClient(
                timeout=httpx.Timeout(connect=5.0, read=15.0, write=5.0, pool=5.0),
                headers={"User-Agent": "TouristSafetyPlatform/1.0 (Non-Commercial; Routing)"},
            )
        return self._client

    async def calculate_route(
        self,
        origin_lat: float,
        origin_lon: float,
        origin_name: str,
        dest_lat: float,
        dest_lon: float,
        dest_name: str,
        travel_mode: str = "driving",
    ) -> RouteResult:
        """Calculates primary route and alternatives between origin and destination using genuine OSRM responses."""
        profile = "driving" if travel_mode == "driving" else "walking"
        route_id = str(uuid.uuid4())
        title = f"{origin_name} to {dest_name}"

        if not self.circuit_breaker.can_execute():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Routing provider (OSRM) circuit breaker is currently open due to upstream service failures.",
            )

        client = await self.get_client()

        try:
            url = (
                f"https://router.project-osrm.org/route/v1/{profile}/"
                f"{origin_lon:.5f},{origin_lat:.5f};{dest_lon:.5f},{dest_lat:.5f}"
            )
            params = {
                "overview": "full",
                "geometries": "geojson",
                "alternatives": "3",
                "steps": "true",
            }
            response = await client.get(url, params=params)
            self.last_status_code = response.status_code

            if response.status_code == 200:
                data = response.json()
                routes = data.get("routes", [])
                if not routes:
                    raise HTTPException(
                        status_code=status.HTTP_404_NOT_FOUND,
                        detail="No viable route found between the specified origin and destination coordinates.",
                    )

                primary = routes[0]
                coords = primary["geometry"]["coordinates"]
                dist_km = round(primary["distance"] / 1000.0, 2)
                dur_mins = round(primary["duration"] / 60.0, 1)

                # Sample waypoints along genuine road polyline every ~3 km
                sampled = sample_points_along_polyline(coords, interval_km=3.0, max_points=12)
                waypoints = []
                avg_speed_kmh = (dist_km / (dur_mins / 60.0)) if dur_mins > 0 else 30.0

                for idx, (w_lat, w_lon, w_dist) in enumerate(sampled):
                    w_time = (w_dist / avg_speed_kmh) * 60.0 if avg_speed_kmh > 0 else 0.0
                    name = (
                        f"Start: {origin_name}"
                        if idx == 0
                        else (f"Destination: {dest_name}" if idx == len(sampled) - 1 else f"Waypoint {idx}")
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

                # Parse legitimate alternatives returned by OSRM
                alternatives = []
                for alt_idx, alt in enumerate(routes[1:], start=1):
                    alt_dist = round(alt["distance"] / 1000.0, 2)
                    alt_dur = round(alt["duration"] / 60.0, 1)
                    alternatives.append(
                        RouteAlternative(
                            id=f"alt-{route_id[:8]}-{alt_idx}",
                            title=f"Alternative Route {alt_idx}",
                            description=f"Alternative route via secondary corridor. {alt_dist} km, ~{int(alt_dur)} mins.",
                            distance_km=alt_dist,
                            duration_minutes=alt_dur,
                            geometry_geojson=alt["geometry"],
                        )
                    )

                self.circuit_breaker.record_success()
                self.last_success_at = None
                self.last_error = None

                return RouteResult(
                    route_id=route_id,
                    title=title,
                    origin_name=origin_name,
                    origin_lat=origin_lat,
                    origin_lon=origin_lon,
                    destination_name=dest_name,
                    destination_lat=dest_lat,
                    destination_lon=dest_lon,
                    travel_mode=travel_mode,
                    distance_km=dist_km,
                    duration_minutes=dur_mins,
                    geometry_geojson=primary["geometry"],
                    waypoints=waypoints,
                    alternatives=alternatives,
                )
            else:
                self.last_error = f"OSRM upstream HTTP {response.status_code}: {response.text}"
                self.circuit_breaker.record_failure()
                raise HTTPException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    detail=f"Upstream routing provider returned status {response.status_code}",
                )

        except HTTPException:
            raise
        except Exception as e:
            logger.warning(f"OSRM request failed: {e}")
            self.circuit_breaker.record_failure()
            self.last_error = str(e)
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"Unable to calculate route via routing provider: {e}",
            )

    async def close(self):
        if self._client and not self._client.is_closed:
            await self._client.aclose()


osrm_routing_provider = OSRMRoutingProvider()
