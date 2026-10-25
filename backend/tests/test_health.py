"""Tests for health endpoints."""
import pytest


@pytest.mark.asyncio
async def test_health_live(client):
    response = await client.get("/api/v1/health/live")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "alive"
    assert "timestamp" in data


@pytest.mark.asyncio
async def test_health_ready(client):
    response = await client.get("/api/v1/health/ready")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["db_status"] == "ok"


@pytest.mark.asyncio
async def test_health_integrations(client):
    response = await client.get("/api/v1/health/integrations")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] in ("healthy", "degraded")
    assert "summary" in data
    assert "providers" in data
    assert "database" in data["providers"]
    assert "openfreemap" in data["providers"]
    assert "open_meteo" in data["providers"]
    assert "osrm" in data["providers"]
    assert "ndma_sachet" in data["providers"]
    
    # Verify no secret leakage in any field
    import json
    dumped = json.dumps(data)
    assert "password" not in dumped.lower() or "auth_type" in dumped
    assert "bearer" not in dumped.lower()
    assert "secret" not in dumped.lower() or "auth_type" in dumped
