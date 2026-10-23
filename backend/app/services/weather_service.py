"""Weather service orchestrating Redis cache, Open-Meteo client, and PostgreSQL fallback."""
import logging
from datetime import datetime, timezone
from typing import Optional
from sqlalchemy.ext.asyncio import AsyncSession
from app.clients.open_meteo import open_meteo_client
from app.cache.redis_cache import cache_service
from app.repositories.weather_repo import WeatherRepository
from app.models.weather import WeatherSnapshot
from app.schemas.weather import WeatherResponse
from app.core.exceptions import UpstreamServiceException
from app.config import settings

logger = logging.getLogger(__name__)


class WeatherService:
    """Manages weather retrieval with multi-layer caching, stampede lock, and durable fallback."""

    def __init__(self, session: AsyncSession):
        self.session = session
        self.weather_repo = WeatherRepository(session)

    async def get_weather(self, latitude: float, longitude: float) -> WeatherResponse:
        """Fetches weather forecast with cache-aside, stampede protection, and durable fallback."""
        # 1. Round coordinates to 2 decimal places (~1.1 km) for optimal cache hit ratio
        norm_lat = round(latitude, 2)
        norm_lon = round(longitude, 2)
        cache_key = f"weather:{norm_lat}:{norm_lon}"

        # 2. Check Redis cache
        cached_data = await cache_service.get(cache_key)
        if cached_data:
            cached_data["is_cached"] = True
            return WeatherResponse(**cached_data)

        # 3. Stampede lock: only one worker fetches live from Open-Meteo
        lock_key = f"fetch_weather:{norm_lat}:{norm_lon}"
        lock_acquired = await cache_service.acquire_lock(lock_key, timeout_seconds=10)

        live_data = None
        if lock_acquired:
            try:
                live_data = await open_meteo_client.fetch_current_weather(latitude, longitude)
            finally:
                await cache_service.release_lock(lock_key)

        now = datetime.now(timezone.utc)

        if live_data:
            # 4. Save durable snapshot in PostgreSQL
            snapshot = WeatherSnapshot(
                latitude=latitude,
                longitude=longitude,
                temperature_c=live_data["temperature_c"],
                relative_humidity_pct=live_data["relative_humidity_pct"],
                wind_speed_kmh=live_data["wind_speed_kmh"],
                precipitation_mm=live_data["precipitation_mm"],
                precipitation_probability_pct=live_data["precipitation_probability_pct"],
                weather_code=live_data["weather_code"],
                weather_description=live_data["weather_description"],
                forecast_valid_at=live_data["forecast_valid_at"],
                retrieved_at=live_data["retrieved_at"],
                expires_at=live_data["expires_at"],
            )
            await self.weather_repo.save_snapshot(snapshot)

            # 5. Store in Redis cache
            live_data["is_cached"] = False
            live_data["is_fallback"] = False
            live_data["is_stale"] = False
            await cache_service.set(cache_key, live_data, ttl_seconds=settings.WEATHER_CACHE_TTL_SECONDS)

            return WeatherResponse(**live_data)

        # 6. Fallback to last-known-good PostgreSQL snapshot
        logger.warning(f"Upstream weather unavailable for ({latitude}, {longitude}). Falling back to DB snapshot.")
        last_good = await self.weather_repo.get_latest_snapshot(latitude, longitude)
        if last_good:
            is_stale = (now - last_good.retrieved_at.replace(tzinfo=timezone.utc)).total_seconds() > settings.WEATHER_CACHE_TTL_SECONDS
            return WeatherResponse(
                latitude=last_good.latitude,
                longitude=last_good.longitude,
                temperature_c=last_good.temperature_c,
                relative_humidity_pct=last_good.relative_humidity_pct,
                wind_speed_kmh=last_good.wind_speed_kmh,
                precipitation_mm=last_good.precipitation_mm,
                precipitation_probability_pct=last_good.precipitation_probability_pct,
                weather_code=last_good.weather_code,
                weather_description=last_good.weather_description,
                forecast_valid_at=last_good.forecast_valid_at,
                retrieved_at=last_good.retrieved_at,
                expires_at=last_good.expires_at,
                is_cached=False,
                is_fallback=True,
                is_stale=is_stale,
            )

        raise UpstreamServiceException(
            message="Weather service currently unavailable and no cached fallback is available for this location."
        )

