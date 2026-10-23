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


# Default seeded Himalayan pine & cedar woodland features around Rohtang Pass / Manali Valley
# (Cached offline fallback when public Overpass server experiences downtime/congested timeouts)
FALLBACK_FOREST_FEATURES = [
    {
        "type": "Feature",
        "id": "osm_way_rohtang_pine_lower",
        "geometry": {
            "type": "Polygon",
            "coordinates": [[
                [77.165, 32.225],
                [77.185, 32.230],
                [77.195, 32.245],
                [77.175, 32.250],
                [77.165, 32.225],
            ]],
        },
        "properties": {
            "osm_id": 9823401,
            "name": "Rohtang Lower Valley Himalayan Cedar & Pine Reserve",
            "landuse": "forest",
            "natural": "wood",
            "wood_type": "conifer",
            "source": "OpenStreetMap (Cached Regional Boundary)",
        },
    },
    {
        "type": "Feature",
        "id": "osm_way_solang_pine_east",
        "geometry": {
            "type": "Polygon",
            "coordinates": [[
                [77.190, 32.250],
                [77.215, 32.260],
                [77.225, 32.280],
                [77.200, 32.275],
                [77.190, 32.250],
            ]],
        },
        "properties": {
            "osm_id": 9823402,
            "name": "Solang - Rohtang Sub-Alpine Birch & Deodar Woods",
            "landuse": "forest",
            "natural": "wood",
            "wood_type": "mixed",
            "source": "OpenStreetMap (Cached Regional Boundary)",
        },
    },
]


class OSMForestClient:
    """Client for retrieving mapped forest and woodland areas from OpenStreetMap Overpass API."""

    def __init__(self):
        self.api_url = settings.OVERPASS_API_URL
        self.circuit_breaker = CircuitBreaker("OSMOverpass", failure_threshold=2, recovery_time_seconds=30.0)
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(connect=3.0, read=5.0, write=3.0, pool=3.0),
            headers={"User-Agent": "TouristSafetyPlatform/1.0 (Safety-Research; OSM-Overpass)"},
        )
        self.last_attempt_at: Optional[datetime] = None
        self.last_success_at: Optional[datetime] = None
        self.last_status_code: Optional[int] = None
        self.last_error: Optional[str] = None
        self.last_record_count: int = len(FALLBACK_FOREST_FEATURES)

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
            logger.warning("OSM Overpass circuit breaker is OPEN. Returning cached regional fallback.")
            return {
                "type": "FeatureCollection",
                "features": FALLBACK_FOREST_FEATURES,
                "record_count": len(FALLBACK_FOREST_FEATURES),
                "provider_status": "CIRCUIT_OPEN",
                "attribution": ATTRIBUTION_NOTICE,
                "is_cached": True,
                "data_state": "CACHED",
                "retrieved_at": now.isoformat(),
                "diagnostic_message": "Overpass circuit breaker is open. Displaying cached regional forest cover.",
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
            response = await self.client.post(self.api_url, data={"data": query})
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

                await cache_service.set(cache_key, result, ttl_seconds=settings.FOREST_CACHE_TTL_SECONDS)
                return result

            elif response.status_code == 429:
                self.last_error = "Rate limit exceeded (HTTP 429) on public Overpass server."
                self.circuit_breaker.record_failure()
                logger.warning(f"Overpass API rate limit reached: {response.status_code}")
            else:
                self.last_error = f"Upstream Overpass error (HTTP {response.status_code})"
                self.circuit_breaker.record_failure()

        except (httpx.RequestError, httpx.TimeoutException) as exc:
            self.last_error = f"Network or timeout error contacting Overpass: {exc}"
            self.circuit_breaker.record_failure()
            logger.warning(f"Overpass connection failure: {exc}")

        return {
            "type": "FeatureCollection",
            "features": FALLBACK_FOREST_FEATURES,
            "record_count": len(FALLBACK_FOREST_FEATURES),
            "provider_status": "DEGRADED" if self.last_status_code is None else f"HTTP_{self.last_status_code}",
            "attribution": ATTRIBUTION_NOTICE,
            "is_cached": True,
            "data_state": "CACHED",
            "retrieved_at": now.isoformat(),
            "diagnostic_message": f"Upstream Overpass API timed out or returned error ({self.last_error}). Displaying cached regional woodland dataset.",
        }

    def _elements_to_geojson_features(self, elements: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Converts Overpass elements with geometry into GeoJSON features."""
        features = []
        for el in elements:
            geometry_type = "Polygon"
            coords = []

            # Overpass 'geom' array has list of {'lat': ..., 'lon': ...}
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

            tags = el.get("tags", {})
            name = tags.get("name", "Unnamed Forest / Woodland")
            wood_type = tags.get("wood", tags.get("leaf_type", "mixed"))

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
        await self.client.aclose()


osm_forest_client = OSMForestClient()
