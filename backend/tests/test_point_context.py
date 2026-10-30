"""Tests for Point Context & Inspection API endpoint."""
from datetime import datetime, timezone, timedelta
from unittest.mock import AsyncMock, patch
import pytest
from app.clients.open_meteo import open_meteo_client


@pytest.mark.asyncio
async def test_point_context_within_uttarakhand(client, test_db):
    """Verify point inspection within Uttarakhand returns weather, settlement, and elevation context."""
    now = datetime.now(timezone.utc)
    mock_weather = {
        "latitude": 30.7346,
        "longitude": 79.0669,
        "temperature_c": 8.0,
        "relative_humidity_pct": 70.0,
        "wind_speed_kmh": 15.0,
        "precipitation_mm": 0.5,
        "precipitation_probability_pct": 20.0,
        "weather_code": 61,
        "weather_description": "Slight rain",
        "forecast_valid_at": now,
        "retrieved_at": now,
        "expires_at": now + timedelta(minutes=15),
    }

    with patch.object(open_meteo_client, "fetch_current_weather", new_callable=AsyncMock) as mock_fetch:
        mock_fetch.return_value = mock_weather

        # Kedarnath coordinates
        response = await client.get("/api/v1/places/point-context?lat=30.7346&lon=79.0669&radius_km=5.0")
        assert response.status_code == 200
        data = response.json()

        assert data["latitude"] == 30.7346
        assert data["longitude"] == 79.0669
        assert data["in_uttarakhand"] is True
        assert data["nearest_settlement"] is not None
        assert "Kedarnath" in data["nearest_settlement"]
        assert data["weather"] is not None
        assert data["weather"]["temperature_c"] == 8.0
        assert data["weather"]["weather_description"] == "Slight rain"
        assert isinstance(data["nearby_facilities"], list)
        assert isinstance(data["active_warnings"], list)
        assert "cellular_estimate" in data
        assert "disclaimer" in data


@pytest.mark.asyncio
async def test_point_context_outside_uttarakhand(client, test_db):
    """Verify point inspection outside Uttarakhand flags in_uttarakhand as False."""
    now = datetime.now(timezone.utc)
    mock_weather = {
        "latitude": 28.6139,
        "longitude": 77.2090,
        "temperature_c": 28.0,
        "relative_humidity_pct": 40.0,
        "wind_speed_kmh": 8.0,
        "precipitation_mm": 0.0,
        "precipitation_probability_pct": 0.0,
        "weather_code": 0,
        "weather_description": "Clear sky",
        "forecast_valid_at": now,
        "retrieved_at": now,
        "expires_at": now + timedelta(minutes=15),
    }

    with patch.object(open_meteo_client, "fetch_current_weather", new_callable=AsyncMock) as mock_fetch:
        mock_fetch.return_value = mock_weather

        # New Delhi coordinates
        response = await client.get("/api/v1/places/point-context?lat=28.6139&lon=77.2090")
        assert response.status_code == 200
        data = response.json()

        assert data["latitude"] == 28.6139
        assert data["longitude"] == 77.2090
        assert data["in_uttarakhand"] is False
        assert data["cellular_estimate"]["status"] == "OUTSIDE_REGION"


@pytest.mark.asyncio
async def test_point_context_coordinate_validation(client):
    """Verify invalid coordinates return 422 Unprocessable Entity."""
    response = await client.get("/api/v1/places/point-context?lat=195.0&lon=79.0")
    assert response.status_code == 422

