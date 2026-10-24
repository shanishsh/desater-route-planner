"""
Official India Meteorological Department (IMD) API Client.

Reference: https://api.imd.gov.in/public/api_reference.html
Authentication: X-API-KEY and OAuth JWT Bearer token acquired from https://api.imd.gov.in/api/oauth/token.php
"""
import logging
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker

logger = logging.getLogger(__name__)


class IMDClient:
    """Client for official India Meteorological Department (IMD) Warning and Nowcast APIs."""

    def __init__(self):
        self.circuit_breaker = CircuitBreaker("IMD_API", failure_threshold=3, recovery_time_seconds=60.0)
        self.last_attempt_at: Optional[datetime] = None
        self.last_success_at: Optional[datetime] = None
        self.last_status_code: Optional[int] = None
        self.last_error: Optional[str] = None
        self.last_record_count: int = 0
        self._jwt_token: Optional[str] = None
        self._token_expires_at: Optional[datetime] = None

    def is_configured(self) -> bool:
        """Returns True if the IMD API key credentials are configured in the environment."""
        return bool(settings.IMD_API_KEY.strip())

    def get_service_status(self) -> Dict[str, Any]:
        """Returns the operational status, health, and configuration requirements for IMD."""
        configured = self.is_configured()
        if not configured:
            status = "UNCONFIGURED"
            diag = (
                "IMD API requires an organizational account, API key, and static server IP whitelisting "
                "on https://api.imd.gov.in/. Configure IMD_API_KEY and credentials in backend .env. "
                "Severe weather warnings from IMD are also aggregated publicly via NDMA SACHET CAP feeds."
            )
        elif self.last_error:
            status = "ERROR"
            diag = self.last_error
        elif self.last_success_at:
            status = "HEALTHY"
            diag = f"Connected to official IMD API portal. {self.last_record_count} active meteorological advisories retrieved."
        else:
            status = "STANDBY"
            diag = "IMD API credentials configured and ready for district warning requests."

        return {
            "provider_id": "imd_warning",
            "name": "India Meteorological Department (IMD) Severe Weather",
            "category": "weather_warning",
            "is_configured": configured,
            "status": status,
            "coverage_scope": "COUNTRY_INDIA",
            "supported_bbox": [6.0, 68.0, 37.5, 97.5],
            "last_attempt_at": self.last_attempt_at.isoformat() if self.last_attempt_at else None,
            "last_success_at": self.last_success_at.isoformat() if self.last_success_at else None,
            "last_http_status": self.last_status_code,
            "record_count": self.last_record_count,
            "cache_age_seconds": 0,
            "data_state": "LIVE" if configured and self.last_success_at else ("UNCONFIGURED" if not configured else "STANDBY"),
            "is_stale": False,
            "diagnostic_message": diag,
            "attribution": "India Meteorological Department (IMD), Ministry of Earth Sciences, Govt of India",
            "source_url": "https://api.imd.gov.in/public/api_reference.html",
            "registration_url": "https://api.imd.gov.in/public/api_reference.html",
        }

    async def fetch_token(self) -> Optional[str]:
        """Fetches JWT bearer token using registered API credentials."""
        if not self.is_configured():
            return None

        now = datetime.now(timezone.utc)
        if self._jwt_token and self._token_expires_at and now < self._token_expires_at:
            return self._jwt_token

        token_url = f"{settings.IMD_BASE_URL.rstrip('/')}/api/oauth/token.php"
        headers = {
            "X-API-KEY": settings.IMD_API_KEY,
            "Accept": "application/json",
            "User-Agent": "TouristSafetyPlatform/1.0 (IMD-Client)",
        }
        data = {
            "api_key": settings.IMD_API_KEY,
            "api_secret": settings.IMD_API_SECRET,
        }

        try:
            async with httpx.AsyncClient(timeout=10.0, verify=False) as client:
                res = await client.post(token_url, headers=headers, json=data)
                if res.status_code == 200:
                    payload = res.json()
                    self._jwt_token = payload.get("token") or payload.get("access_token")
                    expires_in = payload.get("expires_in", 3600)
                    self._token_expires_at = now + timedelta(seconds=int(expires_in) - 60)
                    return self._jwt_token
                else:
                    self.last_error = f"IMD OAuth failed HTTP {res.status_code}: {res.text[:150]}"
                    return None
        except Exception as e:
            self.last_error = f"IMD OAuth error: {str(e)}"
            return None

    async def fetch_district_warnings(self, district_code: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        Fetches official severe weather warnings from the IMD warning endpoint.
        Never fabricates records when credentials are missing or upstream is unavailable.
        """
        if not self.is_configured():
            return []

        now = datetime.now(timezone.utc)
        self.last_attempt_at = now

        if not self.circuit_breaker.can_execute():
            logger.warning("IMD API circuit breaker is OPEN; skipping upstream fetch.")
            return []

        token = await self.fetch_token()
        headers = {
            "X-API-KEY": settings.IMD_API_KEY,
            "Accept": "application/json",
            "User-Agent": "TouristSafetyPlatform/1.0 (IMD-Client)",
        }
        if token:
            headers["Authorization"] = f"Bearer {token}"

        endpoint = f"{settings.IMD_BASE_URL.rstrip('/')}/api/v1/warning/district"
        params = {}
        if district_code:
            params["district_code"] = district_code

        try:
            async with httpx.AsyncClient(timeout=12.0, verify=False) as client:
                res = await client.get(endpoint, headers=headers, params=params)
                self.last_status_code = res.status_code

                if res.status_code == 200:
                    self.last_success_at = now
                    self.last_error = None
                    self.circuit_breaker.record_success()
                    data = res.json()
                    records = data if isinstance(data, list) else data.get("warnings", data.get("data", []))
                    self.last_record_count = len(records)
                    return records
                else:
                    self.last_error = f"HTTP {res.status_code}: {res.text[:200]}"
                    self.circuit_breaker.record_failure()
                    return []
        except Exception as e:
            self.last_error = str(e)
            self.circuit_breaker.record_failure()
            logger.warning(f"Error calling IMD warning API: {e}")
            return []


imd_client = IMDClient()

