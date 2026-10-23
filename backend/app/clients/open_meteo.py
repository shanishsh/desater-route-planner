"""Open-Meteo weather API client with timeouts, retries, and circuit breaker."""
import asyncio
import random
import logging
from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, Any
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
    95: "Thunderstorm",
    96: "Thunderstorm with slight hail",
    99: "Thunderstorm with heavy hail",
}


class OpenMeteoClient:
    """Client for fetching weather forecasts from Open-Meteo."""

    def __init__(self):
        self.base_url = settings.OPEN_METEO_BASE_URL
        self.circuit_breaker = CircuitBreaker("OpenMeteo", failure_threshold=3, recovery_time_seconds=30.0)
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(connect=3.0, read=5.0, write=3.0, pool=5.0),
            headers={"User-Agent": "TouristSafetyPlatform/1.0 (Non-Commercial; Safety)"},
        )

    async def fetch_current_weather(self, latitude: float, longitude: float) -> Optional[Dict[str, Any]]:
        """Fetches current weather with retries, jitter, and circuit breaker protection."""
        if not self.circuit_breaker.can_execute():
            logger.warning("Open-Meteo circuit is OPEN. Skipping external request.")
            return None

        params = {
            "latitude": round(latitude, 4),
            "longitude": round(longitude, 4),
            "current": "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation,weather_code",
            "timezone": "UTC",
        }

        max_retries = 3
        backoff_base = 0.5

        for attempt in range(max_retries):
            try:
                response = await self.client.get(self.base_url, params=params)
                if response.status_code == 200:
                    data = response.json()
                    current = data.get("current", {})
                    weather_code = current.get("weather_code", 0)
                    weather_desc = WMO_WEATHER_CODES.get(weather_code, "Unknown")
                    now = datetime.now(timezone.utc)

                    self.circuit_breaker.record_success()
                    return {
                        "latitude": latitude,
                        "longitude": longitude,
                        "temperature_c": float(current.get("temperature_2m", 20.0)),
                        "relative_humidity_pct": float(current.get("relative_humidity_2m", 50.0)),
                        "wind_speed_kmh": float(current.get("wind_speed_10m", 5.0)),
                        "precipitation_mm": float(current.get("precipitation", 0.0)),
                        "precipitation_probability_pct": 0.0,
                        "weather_code": weather_code,
                        "weather_description": weather_desc,
                        "forecast_valid_at": now,
                        "retrieved_at": now,
                        "expires_at": now + timedelta(seconds=settings.WEATHER_CACHE_TTL_SECONDS),
                    }
                else:
                    logger.warning(
                        f"Open-Meteo returned status {response.status_code} (attempt {attempt + 1}/{max_retries})"
                    )
            except (httpx.RequestError, httpx.TimeoutException) as exc:
                logger.warning(f"Open-Meteo request error on attempt {attempt + 1}: {exc}")

            if attempt < max_retries - 1:
                jitter = random.uniform(0.1, 0.3)
                sleep_time = (backoff_base * (2 ** attempt)) + jitter
                await asyncio.sleep(sleep_time)

        self.circuit_breaker.record_failure()
        return None

    async def close(self):
        await self.client.aclose()


open_meteo_client = OpenMeteoClient()

