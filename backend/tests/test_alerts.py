"""Tests for alerts and dynamic freshness engine."""
from datetime import datetime, timezone, timedelta
import pytest
from app.services.alert_service import AlertService


@pytest.mark.asyncio
async def test_alert_freshness_computation(client, admin_token, test_db):
    now = datetime.now(timezone.utc)
    fresh_time = (now - timedelta(minutes=5)).isoformat()
    stale_time = (now - timedelta(minutes=75)).isoformat()

    # 1. Create a fresh alert (age 5m, threshold 60m)
    fresh_payload = {
        "title": "Fresh Mountain Wind Warning",
        "description": "High gust advisory for higher elevations.",
        "severity": "medium",
        "category": "weather",
        "source_name": "Weather Bureau",
        "source_published_at": fresh_time,
        "freshness_threshold_seconds": 3600,
        "is_simulation": True,
    }
    res1 = await client.post("/api/v1/admin/alerts", json=fresh_payload, headers={"Authorization": admin_token})
    assert res1.status_code == 200
    d1 = res1.json()
    assert d1["is_stale"] is False
    assert d1["age_minutes"] == 5

    # 2. Create a stale alert (age 75m, threshold 60m)
    stale_payload = {
        "title": "Stale Rockfall Notice",
        "description": "Debris cleared earlier this morning.",
        "severity": "high",
        "category": "hazard",
        "source_name": "Trail Rangers",
        "source_published_at": stale_time,
        "freshness_threshold_seconds": 3600,
        "is_simulation": True,
    }
    res2 = await client.post("/api/v1/admin/alerts", json=stale_payload, headers={"Authorization": admin_token})
    assert res2.status_code == 200
    d2 = res2.json()
    assert d2["is_stale"] is True
    assert d2["age_minutes"] == 75


@pytest.mark.asyncio
async def test_alert_future_clock_skew_rejected(client, admin_token):
    # Future timestamp (beyond 30s) should fail schema validation
    future_time = (datetime.now(timezone.utc) + timedelta(minutes=10)).isoformat()
    payload = {
        "title": "Future Alert",
        "description": "This timestamp is in the future.",
        "severity": "low",
        "category": "weather",
        "source_name": "Faulty Clock",
        "source_published_at": future_time,
        "freshness_threshold_seconds": 3600,
        "is_simulation": True,
    }
    res = await client.post("/api/v1/admin/alerts", json=payload, headers={"Authorization": admin_token})
    assert res.status_code == 422


@pytest.mark.asyncio
async def test_seeded_three_simulated_alerts(client, test_db):
    # Seed the three simulated alerts directly via AlertService
    alert_service = AlertService(test_db)
    seeded = await alert_service.seed_demo_alerts()
    assert len(seeded) == 3

    # Query public /api/v1/alerts without include_simulated -> MUST BE EMPTY in production mode
    response_prod = await client.get("/api/v1/alerts")
    assert response_prod.status_code == 200
    data_prod = response_prod.json()
    assert data_prod["total"] == 0
    assert len(data_prod["items"]) == 0

    # Query with include_simulated=true -> yields the 3 demo alerts
    response = await client.get("/api/v1/alerts?include_simulated=true")
    assert response.status_code == 200
    data = response.json()
    assert data["total"] == 3

    items = data["items"]
    # Check that all 3 are marked as simulation
    for item in items:
        assert item["is_simulation"] is True
        assert "[SIMULATED]" in item["title"]

    # Verify freshness status of the 3 alerts:
    rain_alert = next(a for a in items if "Rain-Risk" in a["title"])
    timber_alert = next(a for a in items if "Temporary Trail Obstruction" in a["title"])
    transit_alert = next(a for a in items if "Shuttle Bus" in a["title"])

    assert rain_alert["is_stale"] is False
    assert rain_alert["age_minutes"] == 5

    assert timber_alert["is_stale"] is True
    assert timber_alert["age_minutes"] == 75

    assert transit_alert["is_stale"] is True
    assert transit_alert["age_minutes"] == 180


@pytest.mark.asyncio
async def test_admin_demo_reset_endpoint(client, admin_token, test_db):
    response = await client.post("/api/v1/admin/demo/reset", headers={"Authorization": admin_token})
    assert response.status_code == 200
    alerts = response.json()
    assert len(alerts) == 3

