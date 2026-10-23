"""Tests for Weather API, caching, and fallback mechanisms."""
from datetime import datetime, timezone, timedelta
from unittest.mock import AsyncMock, patch
import pytest
from app.clients.open_meteo import open_meteo_client
from app.models.weather import WeatherSnapshot


@pytest.mark.asyncio
async def test_weather_success_and_caching(client, test_db):
    now = datetime.now(timezone.utc)
    mock_weather = {
        "latitude": 32.2396,
        "longitude": 77.1887,
        "temperature_c": 14.5,
        "relative_humidity_pct": 65.0,
        "wind_speed_kmh": 12.0,
        "precipitation_mm": 0.0,
        "precipitation_probability_pct": 0.0,
        "weather_code": 1,
        "weather_description": "Mainly clear",
        "forecast_valid_at": now,
        "retrieved_at": now,
        "expires_at": now + timedelta(minutes=15),
    }

    with patch.object(open_meteo_client, "fetch_current_weather", new_callable=AsyncMock) as mock_fetch:
        mock_fetch.return_value = mock_weather

        # 1. First fetch (live fetch)
        res1 = await client.get("/api/v1/weather?lat=32.2396&lon=77.1887")
        assert res1.status_code == 200
        d1 = res1.json()
        assert d1["temperature_c"] == 14.5
        assert d1["is_cached"] is False
        assert d1["is_fallback"] is False
        assert mock_fetch.call_count == 1

        # 2. Second fetch (should hit cache)
        res2 = await client.get("/api/v1/weather?lat=32.2396&lon=77.1887")
        assert res2.status_code == 200
        d2 = res2.json()
        assert d2["temperature_c"] == 14.5
        assert d2["is_cached"] is True
        # Upstream client should not be called again
        assert mock_fetch.call_count == 1


@pytest.mark.asyncio
async def test_weather_fallback_to_postgres_snapshot(client, test_db):
    now = datetime.now(timezone.utc)
    # Seed a historical weather snapshot in DB
    snapshot = WeatherSnapshot(
        latitude=32.2396,
        longitude=77.1887,
        temperature_c=11.0,
        relative_humidity_pct=70.0,
        wind_speed_kmh=15.0,
        precipitation_mm=1.5,
        precipitation_probability_pct=40.0,
        weather_code=61,
        weather_description="Slight rain",
        forecast_valid_at=now - timedelta(hours=1),
        retrieved_at=now - timedelta(hours=1),
        expires_at=now - timedelta(minutes=45),
    )
    test_db.add(snapshot)
    await test_db.commit()

    with patch.object(open_meteo_client, "fetch_current_weather", new_callable=AsyncMock) as mock_fetch:
        # Simulate upstream failure
        mock_fetch.return_value = None

        res = await client.get("/api/v1/weather?lat=32.2396&lon=77.1887")
        assert res.status_code == 200
        data = res.json()
        assert data["is_fallback"] is True
        assert data["temperature_c"] == 11.0
        assert data["weather_description"] == "Slight rain"

