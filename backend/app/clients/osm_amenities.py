"""OpenStreetMap Overpass API client for querying real-world emergency amenities."""
import logging
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker
from app.cache.redis_cache import cache_service

logger = logging.getLogger(__name__)

ATTRIBUTION_NOTICE = "Amenity data © OpenStreetMap contributors under ODbL."


class OSMAmenitiesClient:
    """Client for retrieving genuine hospitals, police stations, clinics, and fire stations from OpenStreetMap Overpass."""

    def __init__(self):
        self.api_url = settings.OVERPASS_API_URL
        self.circuit_breaker = CircuitBreaker("OSMAmenities", failure_threshold=2, recovery_time_seconds=30.0)
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
                timeout=httpx.Timeout(connect=3.0, read=10.0, write=3.0, pool=3.0),
                headers={"User-Agent": "TouristSafetyPlatform/1.0 (Emergency-Amenities; OSM-Overpass)"},
            )
        return self._client

    async def fetch_amenities(
        self,
        min_lat: float,
        min_lon: float,
        max_lat: float,
        max_lon: float,
        category: Optional[str] = None,
    ) -> List[Dict[str, Any]]:
        """Queries Overpass for real-world emergency amenities in a bounding box."""
        now = datetime.now(timezone.utc)
        self.last_attempt_at = now

        # Clamp bbox to max 0.5 degrees to avoid overloading public Overpass
        lat_span = abs(max_lat - min_lat)
        lon_span = abs(max_lon - min_lon)
        if lat_span > 0.5 or lon_span > 0.5:
            center_lat = (min_lat + max_lat) / 2.0
            center_lon = (min_lon + max_lon) / 2.0
            min_lat = center_lat - 0.2
            max_lat = center_lat + 0.2
            min_lon = center_lon - 0.2
            max_lon = center_lon + 0.2

        amenity_filter = "hospital|clinic|police|fire_station"
        if category == "medical":
            amenity_filter = "hospital|clinic|doctors"
        elif category == "police":
            amenity_filter = "police"
        elif category in ("fire_station", "fire"):
            amenity_filter = "fire_station"

        cache_key = f"osm_amenities:{round(min_lat, 2)}:{round(min_lon, 2)}:{round(max_lat, 2)}:{round(max_lon, 2)}:{category}"
        cached = await cache_service.get(cache_key)
        if cached and isinstance(cached, list):
            return cached

        if not self.circuit_breaker.can_execute():
            logger.warning("OSM Amenities circuit breaker is OPEN.")
            return []

        query = f"""
        [out:json][timeout:10];
        (
          node["amenity"~"{amenity_filter}"]({min_lat},{min_lon},{max_lat},{max_lon});
          way["amenity"~"{amenity_filter}"]({min_lat},{min_lon},{max_lat},{max_lon});
        );
        out center tags 30;
        """

        try:
            client = await self.get_client()
            response = await client.post(self.api_url, data={"data": query})
            self.last_status_code = response.status_code

            if response.status_code == 200:
                data = response.json()
                elements = data.get("elements", [])
                results = []
                for el in elements:
                    tags = el.get("tags", {})
                    name = tags.get("name")
                    if not name:
                        continue

                    lat = el.get("lat") or el.get("center", {}).get("lat")
                    lon = el.get("lon") or el.get("center", {}).get("lon")
                    if lat is None or lon is None:
                        continue

                    amenity = tags.get("amenity", "emergency_service")
                    cat = "medical" if amenity in ("hospital", "clinic", "doctors") else (
                        "police" if amenity == "police" else (
                            "fire_station" if amenity == "fire_station" else "emergency_service"
                        )
                    )

                    phone = tags.get("phone") or tags.get("contact:phone")
                    address = tags.get("addr:full") or tags.get("addr:street") or f"{lat:.4f}, {lon:.4f}"

                    results.append({
                        "id": f"osm_{el.get('type')}_{el.get('id')}",
                        "name": name,
                        "category": cat,
                        "latitude": float(lat),
                        "longitude": float(lon),
                        "phone": phone,
                        "address": address,
                        "description": f"Mapped {amenity.replace('_', ' ')} verified via OpenStreetMap",
                        "verification_status": "verified",
                        "verification_timestamp": now.isoformat(),
                        "provenance": "OpenStreetMap contributors (ODbL)",
                    })

                self.circuit_breaker.record_success()
                self.last_success_at = now
                self.last_error = None
                self.last_record_count = len(results)

                if results:
                    await cache_service.set(cache_key, results, ttl_seconds=settings.MAP_POINTS_CACHE_TTL_SECONDS)
                return results

            elif response.status_code == 429:
                self.last_error = "Rate limit exceeded (HTTP 429) on public Overpass."
                self.circuit_breaker.record_failure()
            else:
                self.last_error = f"Upstream Overpass error (HTTP {response.status_code})"
                self.circuit_breaker.record_failure()

        except Exception as exc:
            self.last_error = f"Error querying OSM amenities: {exc}"
            self.circuit_breaker.record_failure()

        return []

    async def close(self):
        if self._client and not self._client.is_closed:
            await self._client.aclose()


osm_amenities_client = OSMAmenitiesClient()

