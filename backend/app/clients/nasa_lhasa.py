"""
NASA LHASA (Landslide Hazard Assessment for Situational Awareness) Client.

Optional, non-blocking experimental integration for NASA Global Landslide Nowcast.
Handles upstream outages gracefully without blocking the route planning pipeline.
"""

import logging
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker

logger = logging.getLogger(__name__)


class NASALHASAClient:
    def __init__(self):
        self.base_url = settings.NASA_LHASA_BASE_URL
        self.circuit_breaker = CircuitBreaker("NASA_LHASA", failure_threshold=2, recovery_time_seconds=300.0)
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(connect=5.0, read=5.0, write=5.0, pool=5.0),
            headers={"User-Agent": "TouristSafetyPlatform/1.0 (GIS-Research)"},
        )
        self.last_status: str = "UNTESTED"
        self.last_check: Optional[datetime] = None

    async def fetch_landslide_nowcast(
        self,
        min_lat: float,
        min_lon: float,
        max_lat: float,
        max_lon: float,
    ) -> Dict[str, Any]:
        """
        Attempts to fetch landslide nowcast data from NASA LHASA portal.
        Degrades gracefully if NCCS server is unreachable.
        """
        now = datetime.now(timezone.utc)
        self.last_check = now

        if not self.circuit_breaker.can_execute():
            return {
                "status": "CIRCUIT_OPEN",
                "available": False,
                "nowcasts": [],
                "message": "NASA LHASA upstream portal is currently offline or unreachable.",
                "checked_at": now.isoformat(),
            }

        try:
            # Query LHASA GeoJSON/API endpoint
            resp = await self.client.get(f"{self.base_url}/nowcast.json")
            if resp.status_code == 200:
                data = resp.json()
                self.circuit_breaker.record_success()
                self.last_status = "HEALTHY"
                return {
                    "status": "HEALTHY",
                    "available": True,
                    "nowcasts": data.get("features", []),
                    "checked_at": now.isoformat(),
                }
            else:
                self.circuit_breaker.record_failure()
                self.last_status = f"HTTP_{resp.status_code}"
                return {
                    "status": f"HTTP_{resp.status_code}",
                    "available": False,
                    "nowcasts": [],
                    "message": f"NASA LHASA responded with HTTP {resp.status_code}",
                    "checked_at": now.isoformat(),
                }
        except Exception as e:
            logger.debug(f"NASA LHASA request timed out or failed: {e}")
            self.circuit_breaker.record_failure()
            self.last_status = "UNAVAILABLE_UPSTREAM"
            return {
                "status": "UNAVAILABLE_UPSTREAM",
                "available": False,
                "nowcasts": [],
                "message": "NASA LHASA NCCS portal timed out. Upstream server unreachable; continuing with verified GSI/USDMA historical records.",
                "checked_at": now.isoformat(),
            }

    async def close(self):
        await self.client.aclose()


nasa_lhasa_client = NASALHASAClient()
