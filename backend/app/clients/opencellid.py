"""OpenCelliD client for cell tower coordinates and radio coverage reference."""
import json
import math
import logging
from pathlib import Path
from datetime import datetime, timezone
from typing import Optional, Dict, Any, List
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker
from app.cache.redis_cache import cache_service

logger = logging.getLogger(__name__)

BASE_TOWERS_FILE = Path(__file__).resolve().parent.parent / "resources" / "uttarakhand_cell_towers.json"


def _load_baseline_towers() -> List[Dict[str, Any]]:
    """Loads authoritative baseline cell towers in Uttarakhand."""
    if BASE_TOWERS_FILE.exists():
        try:
            with open(BASE_TOWERS_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            logger.warning(f"Failed to load baseline cell towers from {BASE_TOWERS_FILE}: {e}")
    return []


def _haversine_distance_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculates great-circle distance between two coordinates in meters."""
    r = 6371000.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (
        math.sin(dlat / 2.0) ** 2
        + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2.0) ** 2
    )
    return 2.0 * r * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))

DISCLAIMER_NOTICE = (
    "Cell-tower coordinate records from OpenCelliD provide fixed mast locations and nominal cell technology (LTE/GSM/UMTS). "
    "They do NOT guarantee live handset signal reception, carrier roaming, or continued operation during power outages or disasters."
)

# India MCC (404, 405) MNC Operator Translation
OPERATOR_MAP = {
    # Reliance Jio (MCC 405)
    (405, 840): "Reliance Jio", (405, 854): "Reliance Jio", (405, 855): "Reliance Jio",
    (405, 856): "Reliance Jio", (405, 857): "Reliance Jio", (405, 858): "Reliance Jio",
    (405, 859): "Reliance Jio", (405, 860): "Reliance Jio", (405, 861): "Reliance Jio",
    (405, 862): "Reliance Jio", (405, 863): "Reliance Jio", (405, 864): "Reliance Jio",
    (405, 865): "Reliance Jio", (405, 866): "Reliance Jio", (405, 867): "Reliance Jio",
    (405, 868): "Reliance Jio", (405, 869): "Reliance Jio", (405, 870): "Reliance Jio",
    (405, 871): "Reliance Jio", (405, 872): "Reliance Jio", (405, 873): "Reliance Jio",
    (405, 874): "Reliance Jio",

    # Bharti Airtel (MCC 404 & 405)
    (404, 2): "Bharti Airtel", (404, 3): "Bharti Airtel", (404, 10): "Bharti Airtel",
    (404, 16): "Bharti Airtel", (404, 31): "Bharti Airtel", (404, 40): "Bharti Airtel",
    (404, 45): "Bharti Airtel", (404, 49): "Bharti Airtel", (404, 70): "Bharti Airtel",
    (404, 85): "Bharti Airtel", (404, 90): "Bharti Airtel", (404, 92): "Bharti Airtel",
    (404, 93): "Bharti Airtel", (404, 94): "Bharti Airtel", (404, 95): "Bharti Airtel",
    (404, 96): "Bharti Airtel", (404, 97): "Bharti Airtel", (404, 98): "Bharti Airtel",
    (405, 51): "Bharti Airtel", (405, 52): "Bharti Airtel", (405, 53): "Bharti Airtel",

    # BSNL / MTNL (MCC 404)
    (404, 34): "BSNL", (404, 38): "BSNL", (404, 51): "BSNL", (404, 53): "BSNL",
    (404, 54): "BSNL", (404, 55): "BSNL", (404, 57): "BSNL", (404, 58): "BSNL",
    (404, 59): "BSNL", (404, 62): "BSNL", (404, 64): "BSNL", (404, 66): "BSNL",
    (404, 71): "BSNL", (404, 72): "BSNL", (404, 74): "BSNL", (404, 76): "BSNL",
    (404, 77): "BSNL", (404, 80): "BSNL", (404, 81): "BSNL", (405, 818): "BSNL",

    # Vodafone Idea (Vi)
    (404, 4): "Vodafone Idea", (404, 5): "Vodafone Idea", (404, 7): "Vodafone Idea",
    (404, 11): "Vodafone Idea", (404, 12): "Vodafone Idea", (404, 13): "Vodafone Idea",
    (404, 14): "Vodafone Idea", (404, 15): "Vodafone Idea", (404, 19): "Vodafone Idea",
    (404, 20): "Vodafone Idea", (404, 21): "Vodafone Idea", (404, 22): "Vodafone Idea",
    (404, 24): "Vodafone Idea", (404, 27): "Vodafone Idea", (404, 43): "Vodafone Idea",
    (404, 44): "Vodafone Idea", (404, 46): "Vodafone Idea", (404, 60): "Vodafone Idea",
    (404, 84): "Vodafone Idea", (404, 86): "Vodafone Idea", (404, 88): "Vodafone Idea",
}

def resolve_operator_name(mcc: Optional[int], mnc: Optional[int]) -> str:
    """Translates MCC/MNC numeric codes into human-readable Indian telecom carrier."""
    if mcc is None or mnc is None:
        return "Unknown Carrier"
    try:
        key = (int(mcc), int(mnc))
        return OPERATOR_MAP.get(key, f"Carrier ({mcc}-{mnc})")
    except (ValueError, TypeError):
        return "Unknown Carrier"


class OpenCellIDClient:
    """Client for querying cellular base stations and towers from OpenCelliD."""

    def __init__(self):
        self.base_url = settings.OPENCELLID_BASE_URL
        self.circuit_breaker = CircuitBreaker("OpenCellID", failure_threshold=3, recovery_time_seconds=60.0)
        self._client: Optional[httpx.AsyncClient] = None
        self._loop = None
        self.last_attempt_at: Optional[datetime] = None
        self.last_success_at: Optional[datetime] = None
        self.last_status_code: Optional[int] = None
        self.last_error: Optional[str] = None
        self.last_record_count: int = 0

    async def get_client(self) -> httpx.AsyncClient:
        import asyncio
        try:
            loop = asyncio.get_running_loop()
        except RuntimeError:
            loop = None
        if self._client is None or self._client.is_closed or self._loop != loop:
            self._loop = loop
            self._client = httpx.AsyncClient(
                timeout=httpx.Timeout(connect=5.0, read=15.0, write=5.0, pool=5.0),
                headers={"User-Agent": "TouristSafetyPlatform/1.0 (Telecom-Safety-Research)"},
            )
        return self._client

    @property
    def client(self) -> httpx.AsyncClient:
        """Backward-compatible property for accessing active client on current loop."""
        import asyncio
        try:
            loop = asyncio.get_running_loop()
        except RuntimeError:
            loop = None
        if self._client is None or self._client.is_closed or self._loop != loop:
            self._loop = loop
            self._client = httpx.AsyncClient(
                timeout=httpx.Timeout(connect=5.0, read=15.0, write=5.0, pool=5.0),
                headers={"User-Agent": "TouristSafetyPlatform/1.0 (Telecom-Safety-Research)"},
            )
        return self._client

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
            client = await self.get_client()
            response = await client.get(target_url, params=params)
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
                            operator_name = resolve_operator_name(mcc, mnc)
                            cells.append({
                                "id": f"cell_{mcc}_{mnc}_{cellid}",
                                "latitude": float(item["lat"]),
                                "longitude": float(item["lon"]),
                                "radio": item.get("radio", "LTE"),
                                "operator": operator_name,
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

    async def fetch_corridor_cells(
        self,
        coordinates: List[List[float]],
        sample_step: int = 15,
        radius_m: int = 950,
    ) -> List[Dict[str, Any]]:
        """
        Samples coordinates along a route polyline and queries OpenCelliD for nearby cell sites.
        Supplements and falls back to verified Uttarakhand telecom baseline infrastructure.
        Aggregates unique cell sites across sampled waypoints.
        Coordinates are expected as [longitude, latitude] or [latitude, longitude].
        """
        if not coordinates:
            return []

        unique_cells: Dict[str, Dict[str, Any]] = {}

        # Parse route points to (lat, lon)
        route_pts: List[tuple] = []
        for pt in coordinates:
            if len(pt) >= 2:
                if pt[0] > 50.0:  # lon, lat
                    route_pts.append((float(pt[1]), float(pt[0])))
                else:  # lat, lon
                    route_pts.append((float(pt[0]), float(pt[1])))

        if not route_pts:
            return []

        # 1. Check verified Uttarakhand baseline cell towers along the corridor
        baseline_towers = _load_baseline_towers()
        step = max(1, len(route_pts) // sample_step)
        sampled_pts = [route_pts[i] for i in range(0, len(route_pts), step)]
        if route_pts[-1] not in sampled_pts:
            sampled_pts.append(route_pts[-1])

        for tower in baseline_towers:
            t_lat = tower.get("latitude")
            t_lon = tower.get("longitude")
            t_range = float(tower.get("range_m", 3000.0))
            if t_lat is not None and t_lon is not None:
                for r_lat, r_lon in sampled_pts:
                    dist = _haversine_distance_m(r_lat, r_lon, float(t_lat), float(t_lon))
                    if dist <= (t_range + 3000.0):  # within tower coverage or proximity
                        unique_cells[tower["id"]] = tower
                        break

        # 2. If OpenCellID is configured and circuit breaker allows, query live OpenCellID
        if self.is_configured() and self.circuit_breaker.can_execute():
            for lat, lon in sampled_pts:
                try:
                    res = await self.fetch_nearby_cells(latitude=lat, longitude=lon, radius_m=radius_m)
                    for cell in res.get("cells", []):
                        unique_cells[cell["id"]] = cell
                except Exception as e:
                    logger.debug(f"OpenCelliD point query failed: {e}")

        return list(unique_cells.values())

    async def close(self):
        if self._client is not None and not self._client.is_closed:
            try:
                await self._client.aclose()
            except Exception:
                pass
        self._client = None


opencellid_client = OpenCellIDClient()
