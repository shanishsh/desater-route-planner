"""OpenCelliD client for cell tower coordinates and radio coverage reference."""
import math
import logging
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker
from app.cache.redis_cache import cache_service

logger = logging.getLogger(__name__)

DISCLAIMER_NOTICE = (
    "Cell-tower coordinate records from OpenCelliD provide fixed mast locations and nominal cell technology (LTE/GSM/UMTS). "
    "They do NOT guarantee live handset signal reception, carrier roaming, or continued operation during power outages or disasters."
)


class OpenCellIDClient:
    """Client for querying cellular base stations and towers from OpenCelliD."""

    def __init__(self):
        self.base_url = settings.OPENCELLID_BASE_URL
        self.circuit_breaker = CircuitBreaker("OpenCellID", failure_threshold=3, recovery_time_seconds=60.0)
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(connect=5.0, read=15.0, write=5.0, pool=5.0),
            headers={"User-Agent": "TouristSafetyPlatform/1.0 (Telecom-Safety-Research)"},
        )
        self.last_attempt_at: Optional[datetime] = None
        self.last_success_at: Optional[datetime] = None
        self.last_status_code: Optional[int] = None
        self.last_error: Optional[str] = None
        self.last_record_count: int = 0

    def is_configured(self) -> bool:
        """Returns True if a non-empty OPENCELLID_API_KEY is configured in settings."""
        key = settings.OPENCELLID_API_KEY
        return bool(key and key.strip() and key.strip() != "YOUR_OPENCELLID_KEY")

    async def fetch_nearby_cells(
        self,
        latitude: float,
        longitude: float,
        radius_m: int = 5000,
    ) -> Dict[str, Any]:
        """Fetches nearby cell towers within a radius from OpenCelliD."""
        now = datetime.now(timezone.utc)
        self.last_attempt_at = now

        if not self.is_configured():
            msg = "OPENCELLID_API_KEY is unconfigured. Register at https://opencellid.org to obtain an API key."
            self.last_error = msg
            return {
                "cells": [],
                "record_count": 0,
                "provider_status": "UNCONFIGURED",
                "is_configured": False,
                "diagnostic_message": msg,
                "disclaimer": DISCLAIMER_NOTICE,
                "retrieved_at": now.isoformat(),
                "is_cached": False,
            }

        cache_key = f"opencellid:{round(latitude, 3)}:{round(longitude, 3)}:{radius_m}"
        cached = await cache_service.get(cache_key)
        if cached:
            cached["is_cached"] = True
            return cached

        if not self.circuit_breaker.can_execute():
            return {
                "cells": [],
                "record_count": 0,
                "provider_status": "CIRCUIT_OPEN",
                "is_configured": True,
                "diagnostic_message": "OpenCelliD circuit breaker is temporarily OPEN.",
                "disclaimer": DISCLAIMER_NOTICE,
                "retrieved_at": now.isoformat(),
                "is_cached": False,
            }

        # OpenCellID restricts BBOX to 4,000,000 sq. meters (~2 km x 2 km).
        # We clamp the radius to 950 meters so the bounding box is ~1.9 km x 1.9 km (~3.61 sq km).
        clamped_radius = min(max(radius_m, 100), 950)
        dlat = clamped_radius / 111320.0
        cos_lat = math.cos(math.radians(latitude))
        dlon = clamped_radius / (111320.0 * max(abs(cos_lat), 0.01))

        min_lat = round(latitude - dlat, 4)
        max_lat = round(latitude + dlat, 4)
        min_lon = round(longitude - dlon, 4)
        max_lon = round(longitude + dlon, 4)
        bbox = f"{min_lat},{min_lon},{max_lat},{max_lon}"

        target_url = self.base_url
        if target_url.endswith("/cell/get"):
            target_url = target_url.replace("/cell/get", "/cell/getInArea")

        params = {
            "key": settings.OPENCELLID_API_KEY.strip(),
            "BBOX": bbox,
            "format": "json",
            "limit": 50,
        }

        try:
            response = await self.client.get(target_url, params=params)
            self.last_status_code = response.status_code

            if response.status_code == 200:
                data = response.json()
                cells = []

                if isinstance(data, dict) and "error" in data:
                    err_code = data.get("code")
                    err_msg = data.get("error", "")
                    if err_code == 1:
                        # Cell not found in area - valid empty result
                        pass
                    elif err_code == 2:
                        self.last_error = f"OpenCelliD authentication failed: {err_msg}"
                        self.circuit_breaker.record_failure()
                        return self._build_error_response("AUTH_FAILED", now)
                    elif err_code == 7:
                        self.last_error = "OpenCelliD daily limit exceeded."
                        self.circuit_breaker.record_failure()
                        return self._build_error_response("RATE_LIMITED", now)
                    else:
                        self.last_error = f"OpenCelliD API error: {err_msg} (code {err_code})"
                        self.circuit_breaker.record_failure()
                        return self._build_error_response("API_ERROR", now)
                elif isinstance(data, dict):
                    raw_cells = data.get("cells", [])
                    if not raw_cells and "lat" in data and "lon" in data:
                        raw_cells = [data]

                    for item in raw_cells:
                        try:
                            mcc = item.get("mcc")
                            mnc = item.get("mnc") if item.get("mnc") is not None else item.get("net")
                            cellid = item.get("cellid")
                            cells.append({
                                "id": f"cell_{mcc}_{mnc}_{cellid}",
                                "latitude": float(item["lat"]),
                                "longitude": float(item["lon"]),
                                "radio": item.get("radio", "LTE"),
                                "mcc": mcc,
                                "mnc": mnc,
                                "lac": item.get("lac"),
                                "cellid": cellid,
                                "range_m": item.get("range", 1000),
                            })
                        except (KeyError, ValueError, TypeError) as parse_err:
                            logger.debug("Skipping unparseable cell item %s: %s", item, parse_err)

                self.circuit_breaker.record_success()
                self.last_success_at = now
                self.last_error = None
                self.last_record_count = len(cells)

                result = {
                    "cells": cells,
                    "record_count": len(cells),
                    "provider_status": "HEALTHY",
                    "is_configured": True,
                    "diagnostic_message": f"Successfully retrieved {len(cells)} cell records.",
                    "disclaimer": DISCLAIMER_NOTICE,
                    "retrieved_at": now.isoformat(),
                    "source_name": "OpenCelliD",
                    "source_url": "https://opencellid.org/",
                    "is_cached": False,
                }
                await cache_service.set(cache_key, result, ttl_seconds=3600)
                return result

            elif response.status_code in (401, 403):
                self.last_error = "OpenCelliD authentication failed. Check API key."
                self.circuit_breaker.record_failure()
                status = "AUTH_FAILED"
            elif response.status_code == 429:
                self.last_error = "OpenCelliD rate limit reached (HTTP 429)."
                self.circuit_breaker.record_failure()
                status = "RATE_LIMITED"
            else:
                self.last_error = f"OpenCelliD server error (HTTP {response.status_code})"
                self.circuit_breaker.record_failure()
                status = f"HTTP_{response.status_code}"

        except (httpx.RequestError, httpx.TimeoutException) as exc:
            self.last_error = f"Failed to contact OpenCelliD: {exc}"
            self.circuit_breaker.record_failure()
            status = "DOWN"

        return self._build_error_response(status, now)

    def _build_error_response(self, status: str, now: datetime) -> Dict[str, Any]:
        return {
            "cells": [],
            "record_count": 0,
            "provider_status": status,
            "is_configured": True,
            "diagnostic_message": self.last_error,
            "disclaimer": DISCLAIMER_NOTICE,
            "retrieved_at": now.isoformat(),
            "is_cached": False,
        }

    async def close(self):
        await self.client.aclose()


opencellid_client = OpenCellIDClient()
