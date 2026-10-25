"""OpenStreetMap Overpass API client for querying mapped forest and woodland geometries."""
import asyncio
import logging
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker
from app.cache.redis_cache import cache_service

logger = logging.getLogger(__name__)

ATTRIBUTION_NOTICE = "Forest data © OpenStreetMap contributors under ODbL. Mapped forest cover does not confirm accessibility, ground truth, or absence of hazards."


class OSMForestClient:
    """Client for retrieving mapped forest and woodland areas from OpenStreetMap Overpass API."""

    def __init__(self):
        self.api_url = settings.OVERPASS_API_URL
        self.circuit_breaker = CircuitBreaker("OSMOverpass", failure_threshold=2, recovery_time_seconds=30.0)
        self._client: Optional[httpx.AsyncClient] = None
        self._loop = None
        self.last_attempt_at: Optional[datetime] = None
        self.last_success_at: Optional[datetime] = None
        self.last_status_code: Optional[int] = None
        self.last_error: Optional[str] = None
        self.last_record_count: int = 0

    async def get_client(self) -> httpx.AsyncClient:
        import asyncio
        loop = asyncio.get_running_loop()
        if self._client is None or self._client.is_closed or self._loop != loop:
            self._loop = loop
            self._client = httpx.AsyncClient(
                timeout=httpx.Timeout(connect=3.0, read=8.0, write=3.0, pool=3.0),
                headers={
                    "User-Agent": "UttarakhandTrekSafety/2.0 (Contact: safety@touristsafety.org; OpenStreetMap Ingestion)",
                    "Accept": "application/json",
                },
            )
        return self._client

    async def fetch_forest_features(
        self,
        min_lat: float,
        min_lon: float,
        max_lat: float,
        max_lon: float,
    ) -> Dict[str, Any]:
        """Queries Overpass for ways and relations with landuse=forest or natural=wood."""
        now = datetime.now(timezone.utc)
        self.last_attempt_at = now

        # Enforce bounded area check to prevent excessive public Overpass load
        lat_span = abs(max_lat - min_lat)
        lon_span = abs(max_lon - min_lon)
        if lat_span > 1.0 or lon_span > 1.0:
            # Clamp to 1 degree max bounding box
            center_lat = (min_lat + max_lat) / 2.0
            center_lon = (min_lon + max_lon) / 2.0
            min_lat = center_lat - 0.25
            max_lat = center_lat + 0.25
            min_lon = center_lon - 0.25
            max_lon = center_lon + 0.25

        cache_key = f"forest_geom:{round(min_lat, 2)}:{round(min_lon, 2)}:{round(max_lat, 2)}:{round(max_lon, 2)}"
        cached = await cache_service.get(cache_key)
        if cached:
            cached["is_cached"] = True
            return cached

        if not self.circuit_breaker.can_execute():
            logger.warning("OSM Overpass circuit breaker is OPEN.")
            return {
                "type": "FeatureCollection",
                "features": [],
                "record_count": 0,
                "provider_status": "CIRCUIT_OPEN",
                "attribution": ATTRIBUTION_NOTICE,
                "is_cached": False,
                "data_state": "UNAVAILABLE",
                "retrieved_at": now.isoformat(),
                "diagnostic_message": "Overpass circuit breaker is temporarily open due to upstream service failures.",
            }

        # Overpass QL query: forest landuse and natural wood
        # bbox order in Overpass: (south, west, north, east) -> (min_lat, min_lon, max_lat, max_lon)
        query = f"""
        [out:json][timeout:15];
        (
          way["landuse"="forest"]({min_lat},{min_lon},{max_lat},{max_lon});
          way["natural"="wood"]({min_lat},{min_lon},{max_lat},{max_lon});
          relation["landuse"="forest"]({min_lat},{min_lon},{max_lat},{max_lon});
          relation["natural"="wood"]({min_lat},{min_lon},{max_lat},{max_lon});
        );
        out tags geom 50;
        """

        try:
            client = await self.get_client()
            response = await client.post(self.api_url, data={"data": query})
            self.last_status_code = response.status_code

            if response.status_code == 200:
                data = response.json()
                features = self._elements_to_geojson_features(data.get("elements", []))
                self.circuit_breaker.record_success()
                self.last_success_at = now
                self.last_error = None
                self.last_record_count = len(features)

                result = {
                    "type": "FeatureCollection",
                    "features": features,
                    "record_count": len(features),
                    "provider_status": "HEALTHY",
                    "attribution": ATTRIBUTION_NOTICE,
                    "is_cached": False,
                    "retrieved_at": now.isoformat(),
                    "source_name": "OpenStreetMap Overpass API",
                    "source_url": "https://wiki.openstreetmap.org/wiki/Overpass_API",
                }

                if features:
                    await cache_service.set(cache_key, result, ttl_seconds=settings.FOREST_CACHE_TTL_SECONDS)
                return result

            elif response.status_code == 429:
                self.last_error = "Rate limit exceeded (HTTP 429) on public Overpass server."
                self.circuit_breaker.record_failure()
                logger.warning(f"Overpass API rate limit reached: {response.status_code}")
                provider_status = "RATE_LIMITED"
            else:
                self.last_error = f"Upstream Overpass error (HTTP {response.status_code})"
                self.circuit_breaker.record_failure()
                provider_status = f"HTTP_{response.status_code}"

        except (httpx.RequestError, httpx.TimeoutException) as exc:
            self.last_error = f"Network or timeout error contacting Overpass: {exc}"
            self.circuit_breaker.record_failure()
            logger.warning(f"Overpass connection failure: {exc}")
            provider_status = "DOWN"

        return {
            "type": "FeatureCollection",
            "features": [],
            "record_count": 0,
            "provider_status": provider_status,
            "attribution": ATTRIBUTION_NOTICE,
            "is_cached": False,
            "data_state": "ERROR",
            "retrieved_at": now.isoformat(),
            "diagnostic_message": f"Upstream Overpass API failure: {self.last_error}",
        }

    def _elements_to_geojson_features(self, elements: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Converts Overpass elements with geometry into GeoJSON features (handling ways and multipolygon relations)."""
        features = []
        for el in elements:
            el_type = el.get("type")
            tags = el.get("tags", {})
            name = tags.get("name", "Unnamed Forest / Woodland")
            wood_type = tags.get("wood", tags.get("leaf_type", "mixed"))

            # Handle relations (e.g. multipolygon)
            if el_type == "relation":
                members = el.get("members", [])
                outer_rings = []
                inner_rings = []
                for m in members:
                    role = m.get("role", "outer")
                    m_geom = m.get("geometry", [])
                    if len(m_geom) >= 3:
                        ring = [[pt["lon"], pt["lat"]] for pt in m_geom]
                        if ring[0] != ring[-1]:
                            ring.append(ring[0])
                        if role == "inner":
                            inner_rings.append(ring)
                        else:
                            outer_rings.append(ring)

                if outer_rings:
                    if len(outer_rings) == 1:
                        # Single polygon with possible holes
                        poly_coords = [outer_rings[0]] + inner_rings
                        features.append({
                            "type": "Feature",
                            "id": f"osm_rel_{el.get('id')}",
                            "geometry": {
                                "type": "Polygon",
                                "coordinates": poly_coords,
                            },
                            "properties": {
                                "osm_id": el.get("id"),
                                "name": name,
                                "landuse": tags.get("landuse"),
                                "natural": tags.get("natural"),
                                "wood_type": wood_type,
                                "source": "OpenStreetMap",
                            },
                        })
                    else:
                        # MultiPolygon: list of [outer, inners...]
                        mp_coords = [[[outer]] for outer in outer_rings]
                        features.append({
                            "type": "Feature",
                            "id": f"osm_rel_{el.get('id')}",
                            "geometry": {
                                "type": "MultiPolygon",
                                "coordinates": mp_coords,
                            },
                            "properties": {
                                "osm_id": el.get("id"),
                                "name": name,
                                "landuse": tags.get("landuse"),
                                "natural": tags.get("natural"),
                                "wood_type": wood_type,
                                "source": "OpenStreetMap",
                            },
                        })
                continue

            # Handle ways
            geometry_type = "Polygon"
            coords = []

            raw_geom = el.get("geometry", [])
            if raw_geom and len(raw_geom) >= 3:
                ring = [[pt["lon"], pt["lat"]] for pt in raw_geom]
                # Ensure closed ring for Polygon
                if ring[0] != ring[-1]:
                    ring.append(ring[0])
                coords = [ring]
            elif raw_geom and len(raw_geom) >= 2:
                geometry_type = "LineString"
                coords = [[pt["lon"], pt["lat"]] for pt in raw_geom]
            else:
                continue

            features.append({
                "type": "Feature",
                "id": f"osm_{el.get('type')}_{el.get('id')}",
                "geometry": {
                    "type": geometry_type,
                    "coordinates": coords,
                },
                "properties": {
                    "osm_id": el.get("id"),
                    "name": name,
                    "landuse": tags.get("landuse"),
                    "natural": tags.get("natural"),
                    "wood_type": wood_type,
                    "source": "OpenStreetMap",
                },
            })
        return features

    async def close(self):
        if self._client and not self._client.is_closed:
            await self._client.aclose()


osm_forest_client = OSMForestClient()
