"""Weather forecast schemas."""
from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field


class WeatherResponse(BaseModel):
    latitude: float
    longitude: float
    temperature_c: float
    relative_humidity_pct: float
    wind_speed_kmh: float
    precipitation_mm: float
    precipitation_probability_pct: float
    weather_code: int
    weather_description: str
    forecast_valid_at: datetime
    retrieved_at: datetime
    expires_at: datetime
    is_cached: bool = False
    is_fallback: bool = False
    is_stale: bool = False
    provider_notice: str = "Weather data provided by Open-Meteo under CC BY 4.0 (Non-Commercial Free Tier)"

