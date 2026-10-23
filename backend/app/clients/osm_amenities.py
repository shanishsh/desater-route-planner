"""OpenStreetMap Overpass API client for querying real-world emergency and essential facilities."""
import math
import logging
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker
from app.cache.redis_cache import cache_service

logger = logging.getLogger(__name__)

ATTRIBUTION_NOTICE = "Facility data © OpenStreetMap contributors under ODbL."


def _haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6371000.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)
    a = (
        math.sin(delta_phi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return R * c


class OSMAmenitiesClient:
    """Client for retrieving genuine hospitals, clinics, police stations, fuel stations, and fire stations from OpenStreetMap Overpass."""

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
                timeout=httpx.Timeout(connect=2.5, read=4.5, write=2.5, pool=2.5),
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
        """Queries Overpass for real-world emergency and essential facilities in a bounding box."""
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

        cache_key = f"osm_amenities_v2:{round(min_lat, 2)}:{round(min_lon, 2)}:{round(max_lat, 2)}:{round(max_lon, 2)}:{category}"
        cached = await cache_service.get(cache_key)
        if cached and isinstance(cached, list):
            return cached

        if not self.circuit_breaker.can_execute():
            logger.warning("OSM Amenities circuit breaker is OPEN.")
            return []

        # Build specific or broad Overpass amenity filter
        if category == "hospital":
            amenity_filter = "hospital"
        elif category == "clinic":
            amenity_filter = "clinic"
        elif category == "medical":
            amenity_filter = "hospital|clinic|doctors"
        elif category == "police":
            amenity_filter = "police"
        elif category in ("fuel", "petrol_pump"):
            amenity_filter = "fuel"
        elif category in ("fire_station", "fire"):
            amenity_filter = "fire_station"
        else:
            amenity_filter = "hospital|clinic|police|fuel|fire_station|shelter"

        query = f"""
        [out:json][timeout:10];
        (
          node["amenity"~"{amenity_filter}"]({min_lat},{min_lon},{max_lat},{max_lon});
          way["amenity"~"{amenity_filter}"]({min_lat},{min_lon},{max_lat},{max_lon});
          node["healthcare"~"hospital|clinic"]({min_lat},{min_lon},{max_lat},{max_lon});
          way["healthcare"~"hospital|clinic"]({min_lat},{min_lon},{max_lat},{max_lon});
        );
        out center tags 50;
        """

        try:
            client = await self.get_client()
            response = await client.post(self.api_url, data={"data": query})
            self.last_status_code = response.status_code

            if response.status_code == 200:
                data = response.json()
                elements = data.get("elements", [])
                raw_items = []
                for el in elements:
                    tags = el.get("tags", {})
                    lat = el.get("lat") or el.get("center", {}).get("lat")
                    lon = el.get("lon") or el.get("center", {}).get("lon")
                    if lat is None or lon is None:
                        continue

                    amenity = tags.get("amenity", "")
                    healthcare = tags.get("healthcare", "")

                    # Categorize precisely
                    if amenity == "hospital" or healthcare == "hospital":
                        cat = "hospital"
                        fallback_name = "Hospital"
                    elif amenity == "clinic" or healthcare == "clinic":
                        cat = "clinic"
                        fallback_name = "Clinic"
                    elif amenity == "police":
                        cat = "police"
                        fallback_name = "Police Station"
                    elif amenity == "fuel":
                        cat = "fuel"
                        fallback_name = "Fuel Station"
                    elif amenity == "fire_station":
                        cat = "fire_station"
                        fallback_name = "Fire Station"
                    elif amenity == "shelter":
                        cat = "shelter"
                        fallback_name = "Emergency Shelter"
                    else:
                        continue

                    name = tags.get("name") or tags.get("brand") or tags.get("operator") or fallback_name

                    opening_hours = tags.get("opening_hours")
                    phone = tags.get("phone") or tags.get("contact:phone")
                    website = tags.get("website") or tags.get("contact:website")
                    brand = tags.get("brand")
                    operator = tags.get("operator")
                    emergency_tag = tags.get("emergency")

                    # Verify emergency capability strictly
                    emergency_verified = (emergency_tag == "yes") or (cat == "hospital" and emergency_tag != "no")

                    address = (
                        tags.get("addr:full")
                        or (
                            f"{tags.get('addr:street', '')} {tags.get('addr:city', '')}".strip()
                            if tags.get("addr:street") or tags.get("addr:city")
                            else None
                        )
                    )

                    raw_items.append({
                        "id": f"osm_{el.get('type')}_{el.get('id')}",
                        "osm_id": str(el.get("id")),
                        "osm_type": el.get("type"),
                        "name": name,
                        "category": cat,
                        "latitude": float(lat),
                        "longitude": float(lon),
                        "opening_hours": opening_hours,
                        "phone": phone,
                        "website": website,
                        "brand": brand,
                        "operator": operator,
                        "address": address,
                        "emergency_verified": emergency_verified,
                        "description": f"Verified {cat.replace('_', ' ')} via OpenStreetMap",
                        "verification_status": "verified",
                        "verification_timestamp": now.isoformat(),
                        "provenance": ATTRIBUTION_NOTICE,
                    })

                # Deduplicate elements: if same category and normalized name within 50 meters, merge
                deduped: List[Dict[str, Any]] = []
                for item in raw_items:
                    norm_name = item["name"].strip().lower()
                    matched = False
                    for existing in deduped:
                        if existing["category"] == item["category"]:
                            dist_m = _haversine_meters(
                                item["latitude"], item["longitude"],
                                existing["latitude"], existing["longitude"]
                            )
                            # Same normalized name or very close (<50m)
                            if dist_m <= 50.0 and (norm_name == existing["name"].strip().lower() or not existing.get("phone")):
                                matched = True
                                # Merge fields, preferring non-empty values
                                for k in ["phone", "opening_hours", "website", "brand", "operator", "address"]:
                                    if not existing.get(k) and item.get(k):
                                        existing[k] = item[k]
                                if item.get("emergency_verified"):
                                    existing["emergency_verified"] = True
                                break
                    if not matched:
                        deduped.append(item)

                self.circuit_breaker.record_success()
                self.last_success_at = now
                self.last_error = None
                self.last_record_count = len(deduped)

                if deduped:
                    await cache_service.set(cache_key, deduped, ttl_seconds=settings.MAP_POINTS_CACHE_TTL_SECONDS)
                return deduped

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
