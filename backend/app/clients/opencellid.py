"""OpenCelliD client for cell tower coordinates and radio coverage reference."""
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

        # Query OpenCelliD
        params = {
            "key": settings.OPENCELLID_API_KEY.strip(),
            "lat": latitude,
            "lon": longitude,
            "format": "json",
        }

        try:
            response = await self.client.get(self.base_url, params=params)
            self.last_status_code = response.status_code

            if response.status_code == 200:
                data = response.json()
                cells = []
                if isinstance(data, dict) and "lat" in data and "lon" in data:
                    cells.append({
                        "id": f"cell_{data.get('mcc')}_{data.get('net')}_{data.get('cellid')}",
                        "latitude": float(data.get("lat")),
                        "longitude": float(data.get("lon")),
                        "radio": data.get("radio", "LTE"),
                        "mcc": data.get("mcc"),
                        "mnc": data.get("net"),
                        "range_m": data.get("range", 1000),
                    })

                self.circuit_breaker.record_success()
                self.last_success_at = now
                self.last_error = None
                self.last_record_count = len(cells)

                result = {
                    "cells": cells,
                    "record_count": len(cells),
                    "provider_status": "HEALTHY",
                    "is_configured": True,
                    "diagnostic_message": f"Successfully retrieved cell records.",
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
            else:
                self.last_error = f"OpenCelliD server error (HTTP {response.status_code})"
                self.circuit_breaker.record_failure()
                status = f"HTTP_{response.status_code}"

        except (httpx.RequestError, httpx.TimeoutException) as exc:
            self.last_error = f"Failed to contact OpenCelliD: {exc}"
            self.circuit_breaker.record_failure()
            status = "DOWN"

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
