"""
Official India Meteorological Department (IMD) API Client.

Reference: https://api.imd.gov.in/public/api_reference.html
Authentication: X-API-KEY and OAuth JWT Bearer token acquired from https://api.imd.gov.in/api/oauth/token.php
"""
import asyncio
import logging
from datetime import datetime, timezone, timedelta
from typing import Dict, Any, List, Optional, Tuple
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker

logger = logging.getLogger(__name__)

# Authoritative Uttarakhand 13 Districts with known IMD aliases
UTTARAKHAND_DISTRICTS = {
    "DEHRADUN": {"name": "Dehradun", "aliases": ["dehradun", "ddn"]},
    "UTTARKASHI": {"name": "Uttarkashi", "aliases": ["uttarkashi", "uttar kashi"]},
    "CHAMOLI": {"name": "Chamoli", "aliases": ["chamoli", "joshimath", "badrinath"]},
    "RUDRAPRAYAG": {"name": "Rudraprayag", "aliases": ["rudraprayag", "kedarnath"]},
    "TEHRI_GARHWAL": {"name": "Tehri Garhwal", "aliases": ["tehri garhwal", "tehri"]},
    "PAURI_GARHWAL": {"name": "Pauri Garhwal", "aliases": ["pauri garhwal", "pauri", "kotdwar", "srinagar"]},
    "PITHORAGARH": {"name": "Pithoragarh", "aliases": ["pithoragarh", "dharchula", "munsiyari"]},
    "BAGESHWAR": {"name": "Bageshwar", "aliases": ["bageshwar"]},
    "ALMORA": {"name": "Almora", "aliases": ["almora", "ranikhet"]},
    "CHAMPAWAT": {"name": "Champawat", "aliases": ["champawat"]},
    "NAINITAL": {"name": "Nainital", "aliases": ["nainital", "haldwani"]},
    "UDHAM_SINGH_NAGAR": {"name": "Udham Singh Nagar", "aliases": ["udham singh nagar", "us nagar", "pantnagar"]},
    "HARIDWAR": {"name": "Haridwar", "aliases": ["haridwar", "hardwar", "roorkee"]},
}


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
        self._auth_lock = asyncio.Lock()

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
        """Fetches JWT bearer token using registered API credentials with concurrency protection."""
        if not self.is_configured():
            return None

        now = datetime.now(timezone.utc)
        if self._jwt_token and self._token_expires_at and now < self._token_expires_at:
            return self._jwt_token

        async with self._auth_lock:
            # Double check inside lock
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
                        # Refresh 5 minutes before expiration
                        self._token_expires_at = now + timedelta(seconds=max(60, int(expires_in) - 300))
                        return self._jwt_token
                    elif res.status_code in (401, 403):
                        self.last_error = f"IMD OAuth auth failed (HTTP {res.status_code}). Verify IP binding on IMD portal."
                        return None
                    else:
                        self.last_error = f"IMD OAuth failed HTTP {res.status_code}: {res.text[:150]}"
                        return None
            except Exception as e:
                self.last_error = f"IMD OAuth error: {str(e)}"
                return None

    async def _make_authenticated_request(
        self, endpoint: str, params: Optional[Dict[str, Any]] = None
    ) -> Tuple[Optional[Any], Optional[int], Optional[str]]:
        """Helper to execute an authenticated GET request with 1 token-retry on 401."""
        if not self.is_configured():
            return None, None, "IMD API credentials unconfigured"

        if not self.circuit_breaker.can_execute():
            return None, None, "IMD API circuit breaker is OPEN"

        token = await self.fetch_token()
        headers = {
            "X-API-KEY": settings.IMD_API_KEY,
            "Accept": "application/json",
            "User-Agent": "TouristSafetyPlatform/1.0 (IMD-Client)",
        }
        if token:
            headers["Authorization"] = f"Bearer {token}"

        url = f"{settings.IMD_BASE_URL.rstrip('/')}{endpoint}"
        now = datetime.now(timezone.utc)
        self.last_attempt_at = now

        try:
            async with httpx.AsyncClient(timeout=12.0, verify=False) as client:
                res = await client.get(url, headers=headers, params=params)
                self.last_status_code = res.status_code

                if res.status_code == 401:
                    # Token may have expired prematurely; force clear and retry once
                    self._jwt_token = None
                    new_token = await self.fetch_token()
                    if new_token:
                        headers["Authorization"] = f"Bearer {new_token}"
                        res = await client.get(url, headers=headers, params=params)
                        self.last_status_code = res.status_code

                if res.status_code == 200:
                    self.circuit_breaker.record_success()
                    self.last_success_at = now
                    self.last_error = None
                    return res.json(), 200, None
                else:
                    err_msg = f"HTTP {res.status_code}: {res.text[:200]}"
                    self.last_error = err_msg
                    self.circuit_breaker.record_failure()
                    return None, res.status_code, err_msg
        except Exception as e:
            err_msg = str(e)
            self.last_error = err_msg
            self.circuit_breaker.record_failure()
            return None, None, err_msg

    async def fetch_district_warnings(self, state_name: str = "UTTARAKHAND") -> List[Dict[str, Any]]:
        """
        Fetches official severe weather warnings from /api/v1/districtwarning.
        Returns color-coded district warnings (Green, Yellow, Orange, Red) with issue and validity dates.
        """
        data, status, err = await self._make_authenticated_request(
            "/api/v1/districtwarning", params={"state": state_name}
        )
        if not data:
            return []

        records = data if isinstance(data, list) else data.get("warnings", data.get("data", []))
        self.last_record_count = len(records)
        return records

    async def fetch_district_nowcast(self, district_name: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        Fetches 3-hour severe convective nowcast from /api/v1/districtnowcast.
        Identifies impending severe thunderstorms, squalls, lightning, or localized cloudbursts.
        """
        params = {}
        if district_name:
            params["district"] = district_name
        data, status, err = await self._make_authenticated_request("/api/v1/districtnowcast", params=params)
        if not data:
            return []

        records = data if isinstance(data, list) else data.get("nowcasts", data.get("data", []))
        return records

    async def fetch_state_district_rainfall_forecast(self, state_name: str = "UTTARAKHAND") -> List[Dict[str, Any]]:
        """
        Fetches multi-day district quantitative precipitation forecast from /api/v1/state_district_rainfall_forecast.
        """
        data, status, err = await self._make_authenticated_request(
            "/api/v1/state_district_rainfall_forecast", params={"state": state_name}
        )
        if not data:
            return []

        records = data if isinstance(data, list) else data.get("forecasts", data.get("data", []))
        return records

    async def fetch_basin_qpf(self, basin_name: Optional[str] = "Ganga") -> List[Dict[str, Any]]:
        """
        Fetches River Basin Quantitative Precipitation Forecast from /api/v1/basinqpf.
        Provides meteorological precipitation context for major river catchment areas (e.g. Alaknanda, Bhagirathi).
        """
        params = {}
        if basin_name:
            params["basin"] = basin_name
        data, status, err = await self._make_authenticated_request("/api/v1/basinqpf", params=params)
        if not data:
            return []

        records = data if isinstance(data, list) else data.get("basin_qpf", data.get("data", []))
        return records

    async def fetch_current_wx(self, station_id: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        Fetches surface weather station observations from /api/v1/current_wx.
        """
        params = {}
        if station_id:
            params["station_id"] = station_id
        data, status, err = await self._make_authenticated_request("/api/v1/current_wx", params=params)
        if not data:
            return []

        records = data if isinstance(data, list) else data.get("stations", data.get("data", []))
        return records


imd_client = IMDClient()
