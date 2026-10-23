"""Open-Meteo weather API client with timeouts, retries, and circuit breaker."""
import asyncio
import random
import logging
from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, Any, List
import httpx
from app.config import settings
from app.clients.circuit_breaker import CircuitBreaker

logger = logging.getLogger(__name__)

# Weather code descriptions from WMO interpretation table
WMO_WEATHER_CODES = {
    0: "Clear sky",
    1: "Mainly clear",
    2: "Partly cloudy",
    3: "Overcast",
    45: "Fog",
    48: "Depositing rime fog",
    51: "Light drizzle",
    53: "Moderate drizzle",
    55: "Dense drizzle",
    61: "Slight rain",
    63: "Moderate rain",
    65: "Heavy rain",
    71: "Slight snow fall",
    73: "Moderate snow fall",
    75: "Heavy snow fall",
    77: "Snow grains",
    80: "Slight rain showers",
    81: "Moderate rain showers",
    82: "Violent rain showers",
    85: "Slight snow showers",
    86: "Heavy snow showers",
    95: "Thunderstorm: slight or moderate",
    96: "Thunderstorm with slight hail",
    99: "Thunderstorm with heavy hail",
}


class OpenMeteoClient:
    """Client for fetching weather forecasts and settlement geocoding from Open-Meteo."""

    def __init__(self):
        self.base_url = settings.OPEN_METEO_BASE_URL
        self.geocoding_url = "https://geocoding-api.open-meteo.com/v1/search"
        self.circuit_breaker = CircuitBreaker("OpenMeteo", failure_threshold=3, recovery_time_seconds=30.0)
        self._client: Optional[httpx.AsyncClient] = None
        self._loop = None
        self._hourly_cache: Dict[str, Dict[str, Any]] = {}
        self._geocoding_cache: Dict[str, List[Dict[str, Any]]] = {}
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
                timeout=httpx.Timeout(connect=3.0, read=5.0, write=3.0, pool=5.0),
                headers={"User-Agent": "TouristSafetyPlatform/1.0 (Non-Commercial; Safety)"},
            )
        return self._client

    async def fetch_current_weather(self, latitude: float, longitude: float) -> Optional[Dict[str, Any]]:
        """Fetches current weather with retries, jitter, and circuit breaker protection."""
        now = datetime.now(timezone.utc)
        self.last_attempt_at = now

        if not self.circuit_breaker.can_execute():
            logger.warning("Open-Meteo circuit is OPEN. Skipping external request.")
            self.last_error = "Open-Meteo circuit breaker is OPEN."
            return None

        params = {
            "latitude": round(latitude, 4),
            "longitude": round(longitude, 4),
            "current": "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation,weather_code",
            "timezone": "UTC",
        }

        max_retries = 3
        backoff_base = 0.5

        client = await self.get_client()
        for attempt in range(max_retries):
            try:
                response = await client.get(self.base_url, params=params)
                self.last_status_code = response.status_code
                if response.status_code == 200:
                    data = response.json()
                    current = data.get("current", {})
                    weather_code = current.get("weather_code", 0)
                    weather_desc = WMO_WEATHER_CODES.get(weather_code, "Unknown")

                    self.circuit_breaker.record_success()
                    self.last_success_at = now
                    self.last_error = None
                    self.last_record_count = 1
                    return {
                        "latitude": latitude,
                        "longitude": longitude,
                        "temperature_c": float(current.get("temperature_2m", 0.0)),
                        "relative_humidity_pct": float(current.get("relative_humidity_2m", 0.0)),
                        "wind_speed_kmh": float(current.get("wind_speed_10m", 0.0)),
                        "precipitation_mm": float(current.get("precipitation", 0.0)),
                        "precipitation_probability_pct": 0.0,
                        "weather_code": weather_code,
                        "weather_description": weather_desc,
                        "forecast_valid_at": now,
                        "retrieved_at": now,
                        "expires_at": now + timedelta(seconds=settings.WEATHER_CACHE_TTL_SECONDS),
                    }
                else:
                    self.last_error = f"Open-Meteo returned status {response.status_code}"
                    logger.warning(
                        f"Open-Meteo returned status {response.status_code} (attempt {attempt + 1}/{max_retries})"
                    )
            except (httpx.RequestError, httpx.TimeoutException) as exc:
                self.last_error = f"Open-Meteo connection error: {exc}"
                logger.warning(f"Open-Meteo request error on attempt {attempt + 1}: {exc}")

            if attempt < max_retries - 1:
                jitter = random.uniform(0.1, 0.3)
                sleep_time = (backoff_base * (2 ** attempt)) + jitter
                await asyncio.sleep(sleep_time)

        self.circuit_breaker.record_failure()
        return None

    async def fetch_hourly_forecast(self, latitude: float, longitude: float) -> Optional[Dict[str, Any]]:
        """Fetches 48-hour hourly forecast for a coordinate with in-memory caching."""
        cache_key = f"{round(latitude, 3)},{round(longitude, 3)}"
        now = datetime.now(timezone.utc)
        self.last_attempt_at = now

        if cache_key in self._hourly_cache:
            entry = self._hourly_cache[cache_key]
            if entry["expires_at"] > now:
                return entry["data"]

        if not self.circuit_breaker.can_execute():
            logger.warning("Open-Meteo circuit is OPEN for hourly forecast.")
            return None

        params = {
            "latitude": round(latitude, 4),
            "longitude": round(longitude, 4),
            "hourly": "temperature_2m,relative_humidity_2m,precipitation_probability,precipitation,wind_speed_10m,weather_code",
            "forecast_days": 2,
            "timezone": "UTC",
        }

        try:
            client = await self.get_client()
            response = await client.get(self.base_url, params=params)
            self.last_status_code = response.status_code
            if response.status_code == 200:
                data = response.json()
                self.circuit_breaker.record_success()
                self.last_success_at = now
                self.last_error = None
                self._hourly_cache[cache_key] = {
                    "data": data,
                    "expires_at": now + timedelta(minutes=15),
                }
                return data
            else:
                self.last_error = f"Open-Meteo hourly returned status {response.status_code}"
                logger.warning(f"Open-Meteo hourly returned status {response.status_code}")
        except Exception as exc:
            self.last_error = f"Failed to fetch Open-Meteo hourly forecast: {exc}"
            logger.warning(f"Failed to fetch Open-Meteo hourly forecast: {exc}")
            self.circuit_breaker.record_failure()

        return None

    async def get_forecast_for_waypoint(
        self, latitude: float, longitude: float, arrival_time: datetime
    ) -> Dict[str, Any]:
        """Returns forecast slice corresponding to estimated arrival hour at waypoint."""
        data = await self.fetch_hourly_forecast(latitude, longitude)
        now = datetime.now(timezone.utc)

        if data and "hourly" in data:
            hourly = data["hourly"]
            times = hourly.get("time", [])
            target_str = arrival_time.strftime("%Y-%m-%dT%H:00")

            idx = 0
            if target_str in times:
                idx = times.index(target_str)
            else:
                # Find closest index
                min_diff = None
                for i, t_str in enumerate(times):
                    try:
                        t_dt = datetime.fromisoformat(t_str).replace(tzinfo=timezone.utc)
                        diff = abs((t_dt - arrival_time).total_seconds())
                        if min_diff is None or diff < min_diff:
                            min_diff = diff
                            idx = i
                    except Exception:
                        pass

            try:
                temp_c = float(hourly["temperature_2m"][idx])
                rh = float(hourly["relative_humidity_2m"][idx])
                precip_prob = float(hourly["precipitation_probability"][idx])
                precip_mm = float(hourly["precipitation"][idx])
                wind_kmh = float(hourly["wind_speed_10m"][idx])
                w_code = int(hourly["weather_code"][idx])
                w_desc = WMO_WEATHER_CODES.get(w_code, "Unknown")

                # Compute warning level
                warning_level = "NORMAL"
                if w_code in [95, 96, 99] or precip_mm >= 15.0 or wind_kmh >= 55.0:
                    warning_level = "SEVERE"
                elif precip_prob >= 60.0 or precip_mm >= 5.0 or wind_kmh >= 35.0:
                    warning_level = "CAUTION"

                return {
                    "temperature_c": temp_c,
                    "relative_humidity_pct": rh,
                    "precipitation_probability_pct": precip_prob,
                    "precipitation_mm": precip_mm,
                    "wind_speed_kmh": wind_kmh,
                    "weather_code": w_code,
                    "weather_description": w_desc,
                    "warning_level": warning_level,
                    "forecast_valid_at": times[idx],
                    "retrieved_at": now.isoformat(),
                    "source": "Open-Meteo Hourly Forecast",
                    "is_simulated": False,
                }
            except (KeyError, IndexError) as e:
                logger.warning(f"Error parsing hourly forecast index {idx}: {e}")

        # Upstream forecast unavailable - return clearly marked unavailable condition without fabricating values
        return {
            "temperature_c": 0.0,
            "relative_humidity_pct": 0.0,
            "precipitation_probability_pct": 0.0,
            "precipitation_mm": 0.0,
            "wind_speed_kmh": 0.0,
            "weather_code": 0,
            "weather_description": "Weather forecast unavailable",
            "warning_level": "UNKNOWN",
            "forecast_valid_at": arrival_time.isoformat(),
            "retrieved_at": now.isoformat(),
            "source": "Open-Meteo (Unavailable)",
            "is_simulated": False,
        }

    async def search_settlements(self, query: str, count: int = 8) -> List[Dict[str, Any]]:
        """Search settlements and tourist destinations via Open-Meteo Geocoding API."""
        clean_q = query.strip().lower()
        if not clean_q:
            return []

        if clean_q in self._geocoding_cache:
            return self._geocoding_cache[clean_q]

        results = []
        try:
            client = await self.get_client()
            response = await client.get(
                self.geocoding_url,
                params={"name": clean_q, "count": count, "language": "en", "format": "json"},
            )
            self.last_status_code = response.status_code
            if response.status_code == 200:
                data = response.json()
                for item in data.get("results", []):
                    results.append({
                        "name": item.get("name", "Unknown"),
                        "latitude": float(item.get("latitude")),
                        "longitude": float(item.get("longitude")),
                        "country": item.get("country", ""),
                        "admin1": item.get("admin1", ""),
                        "display_name": f"{item.get('name')}, {item.get('admin1', '')} ({item.get('country', '')})".strip(", ()"),
                    })
                self.last_success_at = datetime.now(timezone.utc)
                self.last_error = None
            else:
                self.last_error = f"Geocoding upstream status {response.status_code}"
        except Exception as exc:
            self.last_error = f"Geocoding search error for '{query}': {exc}"
            logger.warning(self.last_error)

        # Do NOT substitute fake or hardcoded settlements. Empty search results remain empty.
        self._geocoding_cache[clean_q] = results
        return results

    async def close(self):
        if self._client and not self._client.is_closed:
            await self._client.aclose()


open_meteo_client = OpenMeteoClient()
