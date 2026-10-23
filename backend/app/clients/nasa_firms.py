"""NASA FIRMS (Fire Information for Resource Management System) client with typed CSV parser."""
import csv
import io
import logging
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker
from app.cache.redis_cache import cache_service

logger = logging.getLogger(__name__)

DISCLAIMER_NOTICE = (
    "Satellite thermal anomalies from NASA FIRMS (MODIS/VIIRS) indicate detected heat signatures, "
    "not confirmed ground wildfires. Detections may be subject to satellite pass timing, cloud cover, "
    "or industrial heat sources, and do not represent instantaneous ground truth."
)


class NASAFIRMSClient:
    """Client for querying satellite active fire and thermal anomaly detections from NASA FIRMS."""

    def __init__(self):
        self.base_url = settings.NASA_FIRMS_BASE_URL.rstrip("/")
        self.circuit_breaker = CircuitBreaker("NASAFIRMS", failure_threshold=3, recovery_time_seconds=60.0)
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(connect=5.0, read=25.0, write=5.0, pool=5.0),
            headers={"User-Agent": "TouristSafetyPlatform/1.0 (Wildfire-Safety-Research)"},
        )
        self.last_attempt_at: Optional[datetime] = None
        self.last_success_at: Optional[datetime] = None
        self.last_status_code: Optional[int] = None
        self.last_error: Optional[str] = None
        self.last_record_count: int = 0

    def is_configured(self) -> bool:
        """Returns True if a non-empty MAP_KEY is configured in settings."""
        key = settings.NASA_FIRMS_MAP_KEY
        return bool(key and key.strip() and key.strip() != "YOUR_NASA_FIRMS_MAP_KEY")

    async def fetch_active_fires(
        self,
        min_lat: float,
        min_lon: float,
        max_lat: float,
        max_lon: float,
        source: str = "VIIRS_SNPP_NRT",
        day_range: int = 1,
        days: Optional[int] = None,
    ) -> Dict[str, Any]:
        """Fetches active fire hotspot records for a bounding box from NASA FIRMS via CSV API."""
        if days is not None:
            day_range = days
        now = datetime.now(timezone.utc)
        self.last_attempt_at = now

        if not self.is_configured():
            msg = "NASA FIRMS MAP_KEY is unconfigured. Obtain a free key at https://firms.modaps.eosdis.nasa.gov/api/map_key/ and configure NASA_FIRMS_MAP_KEY in the environment."
            self.last_error = msg
            return {
                "records": [],
                "record_count": 0,
                "provider_status": "UNCONFIGURED",
                "is_configured": False,
                "diagnostic_message": msg,
                "disclaimer": DISCLAIMER_NOTICE,
                "retrieved_at": now.isoformat(),
                "is_cached": False,
            }

        cache_key = f"firms:{source}:{round(min_lat, 2)}:{round(min_lon, 2)}:{round(max_lat, 2)}:{round(max_lon, 2)}:{day_range}"
        cached = await cache_service.get(cache_key)
        if cached:
            cached["is_cached"] = True
            return cached

        if not self.circuit_breaker.can_execute():
            logger.warning("NASA FIRMS circuit breaker is OPEN.")
            return {
                "records": [],
                "record_count": 0,
                "provider_status": "CIRCUIT_OPEN",
                "is_configured": True,
                "diagnostic_message": "NASA FIRMS circuit breaker is temporarily OPEN due to recent upstream failures.",
                "disclaimer": DISCLAIMER_NOTICE,
                "retrieved_at": now.isoformat(),
                "is_cached": False,
            }

        # URL format: /api/area/csv/{MAP_KEY}/{source}/{min_lon},{min_lat},{max_lon},{max_lat}/{day_range}
        # Note: NASA FIRMS area endpoint specifies coordinate order: W,S,E,N (min_lon, min_lat, max_lon, max_lat)
        map_key = settings.NASA_FIRMS_MAP_KEY.strip()
        area_coords = f"{round(min_lon, 4)},{round(min_lat, 4)},{round(max_lon, 4)},{round(max_lat, 4)}"
        endpoint_url = f"{self.base_url}/area/csv/{map_key}/{source}/{area_coords}/{day_range}"

        try:
            response = await self.client.get(endpoint_url)
            self.last_status_code = response.status_code

            if response.status_code == 200:
                raw_csv = response.text
                records = self.parse_firms_csv(raw_csv, source)
                self.circuit_breaker.record_success()
                self.last_success_at = now
                self.last_error = None
                self.last_record_count = len(records)

                result = {
                    "records": records,
                    "record_count": len(records),
                    "provider_status": "HEALTHY",
                    "is_configured": True,
                    "diagnostic_message": f"Successfully retrieved {len(records)} active fire detections from NASA FIRMS.",
                    "disclaimer": DISCLAIMER_NOTICE,
                    "retrieved_at": now.isoformat(),
                    "source_name": f"NASA FIRMS ({source})",
                    "source_url": "https://firms.modaps.eosdis.nasa.gov/",
                    "is_cached": False,
                }
                await cache_service.set(cache_key, result, ttl_seconds=settings.FIRE_CACHE_TTL_SECONDS)
                return result

            elif response.status_code in (401, 403):
                self.last_error = "NASA FIRMS authentication rejected (HTTP 401/403). Verify that NASA_FIRMS_MAP_KEY is valid."
                self.circuit_breaker.record_failure()
                status = "AUTH_FAILED"
            elif response.status_code == 429:
                self.last_error = "NASA FIRMS rate limit or transaction allowance exceeded (HTTP 429)."
                self.circuit_breaker.record_failure()
                status = "RATE_LIMITED"
            else:
                self.last_error = f"NASA FIRMS upstream server error (HTTP {response.status_code})"
                self.circuit_breaker.record_failure()
                status = f"HTTP_{response.status_code}"

        except (httpx.RequestError, httpx.TimeoutException) as exc:
            self.last_error = f"Connection failure reaching NASA FIRMS: {exc}"
            self.circuit_breaker.record_failure()
            status = "DOWN"

        return {
            "records": [],
            "record_count": 0,
            "provider_status": status,
            "is_configured": True,
            "diagnostic_message": self.last_error,
            "disclaimer": DISCLAIMER_NOTICE,
            "retrieved_at": now.isoformat(),
            "is_cached": False,
        }

    def parse_firms_csv(self, raw_csv: str, source: str) -> List[Dict[str, Any]]:
        """Parses NASA FIRMS CSV text into a typed list of fire detection dictionaries."""
        if not raw_csv or not raw_csv.strip():
            return []

        # Check for error text in the response
        first_line = raw_csv.strip().split("\n")[0].lower()
        if "invalid" in first_line or "error" in first_line or "bad request" in first_line:
            logger.warning(f"NASA FIRMS returned error response in body: {first_line}")
            self.last_error = f"NASA FIRMS error: {first_line}"
            return []

        records = []
        try:
            reader = csv.DictReader(io.StringIO(raw_csv.strip()))
            for row in reader:
                try:
                    lat = float(row.get("latitude", 0.0))
                    lon = float(row.get("longitude", 0.0))
                    if lat == 0.0 and lon == 0.0:
                        continue

                    # Brightness column differs between VIIRS (bright_ti4/bright_ti5) and MODIS (brightness/bright_t31)
                    brightness = float(
                        row.get("bright_ti4")
                        or row.get("brightness")
                        or row.get("bright_ti5")
                        or 0.0
                    )
                    bright_t5 = float(row.get("bright_ti5") or row.get("bright_t31") or 0.0)
                    scan = float(row.get("scan", 1.0))
                    track = float(row.get("track", 1.0))
                    acq_date = row.get("acq_date", "")
                    acq_time = row.get("acq_time", "")
                    satellite = row.get("satellite", "VIIRS/MODIS")
                    instrument = row.get("instrument", source)
                    confidence = str(row.get("confidence", "nominal"))
                    version = str(row.get("version", "NRT"))
                    frp = float(row.get("frp", 0.0))  # Fire Radiative Power (MW)
                    daynight = str(row.get("daynight", "D")).upper()

                    # Deduplication ID
                    record_id = f"firms_{round(lat, 4)}_{round(lon, 4)}_{acq_date}_{acq_time}_{satellite}"

                    records.append({
                        "id": record_id,
                        "latitude": lat,
                        "longitude": lon,
                        "brightness": brightness,
                        "bright_ti5": bright_t5,
                        "scan": scan,
                        "track": track,
                        "acquisition_date": acq_date,
                        "acquisition_time": acq_time,
                        "satellite": satellite,
                        "instrument": instrument,
                        "confidence": confidence,
                        "version": version,
                        "frp_mw": frp,
                        "daynight": daynight,
                        "is_simulated": False,
                        "source": "NASA FIRMS",
                    })
                except (ValueError, TypeError) as field_err:
                    logger.debug(f"Skipping malformed FIRMS CSV row: {row} - {field_err}")
                    continue
        except Exception as csv_err:
            logger.error(f"Failed to parse NASA FIRMS CSV: {csv_err}")

        return records

    async def close(self):
        await self.client.aclose()


nasa_firms_client = NASAFIRMSClient()
