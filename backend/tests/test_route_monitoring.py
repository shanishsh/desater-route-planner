"""Tests for Places Search, Route Planning, Corridor Safety Analysis, and Active Trips."""
import pytest
from datetime import datetime, timezone, timedelta
from app.models.disaster_alert import DisasterAlert


@pytest.mark.asyncio
async def test_search_places(client):
    """Test searching for settlements via Open-Meteo or fallback."""
    response = await client.get("/api/v1/places/search?q=Shimla&count=5")
    assert response.status_code == 200
    data = response.json()
    assert len(data) >= 1
    first = data[0]
    assert "name" in first
    assert "latitude" in first
    assert "longitude" in first
    assert "Shimla" in first["name"]


@pytest.mark.asyncio
async def test_plan_route_corridor_analysis(client):
    """Test planning a route between Shimla and Kufri and inspecting corridor conditions."""
    payload = {
        "origin_lat": 31.1048,
        "origin_lon": 77.1734,
        "origin_name": "Shimla Mall Road",
        "destination_lat": 31.0979,
        "destination_lon": 77.2674,
        "destination_name": "Kufri Adventure Park",
        "travel_mode": "driving",
    }
    response = await client.post("/api/v1/routes/plan", json=payload)
    assert response.status_code == 201
    data = response.json()

    assert "route_id" in data
    assert data["origin_name"] == "Shimla Mall Road"
    assert data["destination_name"] == "Kufri Adventure Park"
    assert data["distance_km"] > 0
    assert data["duration_minutes"] > 0
    assert "geometry_geojson" in data
    assert data["geometry_geojson"]["type"] == "LineString"

    # Verify corridor waypoints
    conditions = data["conditions"]
    assert len(conditions) >= 2
    first_wp = conditions[0]
    assert first_wp["name"].startswith("Start")
    assert "weather" in first_wp
    assert "temperature_c" in first_wp["weather"]
    assert "precipitation_probability_pct" in first_wp["weather"]
    assert "forest" in first_wp
    assert "wildfire" in first_wp
    assert "alerts" in first_wp

    # Alternatives check: legitimately returned from provider (empty if only 1 corridor exists)
    assert "alternatives" in data
    assert isinstance(data["alternatives"], list)

    # Verify retrieval by route_id
    route_id = data["route_id"]
    get_res = await client.get(f"/api/v1/routes/{route_id}")
    assert get_res.status_code == 200
    assert get_res.json()["route_id"] == route_id

    # Verify conditions endpoint
    cond_res = await client.get(f"/api/v1/routes/{route_id}/conditions")
    assert cond_res.status_code == 200
    assert len(cond_res.json()) == len(conditions)

    # Verify alternatives endpoint
    alt_res = await client.get(f"/api/v1/routes/{route_id}/alternatives")
    assert alt_res.status_code == 200
    assert isinstance(alt_res.json(), list)
    assert len(alt_res.json()) == len(data["alternatives"])


@pytest.mark.asyncio
async def test_plan_route_with_disaster_alert_intersection(client, test_db):
    """Seed an active landslide warning along the corridor and verify route hazard detection."""
    # Seed active disaster alert in Kufri region
    now = datetime.now(timezone.utc)
    alert = DisasterAlert(
        title="Landslide Warning on NH-5 Kufri Bypass",
        description="Active debris flow blocking single lane traffic near Kufri ridge.",
        hazard_category="landslide",
        severity="SEVERE",
        status="active",
        issuing_authority="State Disaster Management Authority",
        required_protective_instructions="Use designated valley detour and exercise caution.",
        center_latitude=31.0980,
        center_longitude=77.2600,
        affected_radius_meters=4000.0,
        published_at=now,
    )
    test_db.add(alert)
    await test_db.commit()

    payload = {
        "origin_lat": 31.1048,
        "origin_lon": 77.1734,
        "origin_name": "Shimla",
        "destination_lat": 31.0979,
        "destination_lon": 77.2674,
        "destination_name": "Kufri",
        "travel_mode": "driving",
    }
    response = await client.post("/api/v1/routes/plan", json=payload)
    assert response.status_code == 201
    data = response.json()

    # The overall status should be elevated due to the active landslide alert
    assert data["overall_safety_status"] in ["HIGH_RISK", "CAUTION"]
    assert data["overall_hazard_score"] >= 45.0

    # At least one waypoint should match the landslide alert
    has_matched_alert = False
    for wp in data["conditions"]:
        if wp["alerts"]:
            for a in wp["alerts"]:
                if a["hazard_category"] == "landslide":
                    has_matched_alert = True
                    assert "Landslide" in a["title"]
    assert has_matched_alert is True

    # Alternatives list is legitimately sourced from routing provider
    alts = data["alternatives"]
    assert isinstance(alts, list)
    if alts:
        assert alts[0]["safety_label"] is not None
        assert "hazard corridor" in alts[0]["safety_label"] or "detour" in alts[0]["safety_label"].lower()


@pytest.mark.asyncio
async def test_active_trip_monitoring_lifecycle(client):
    """Test starting a trip, submitting GPS progress, proximity alerting, and completing the trip."""
    # 1. Plan route
    plan_res = await client.post(
        "/api/v1/routes/plan",
        json={
            "origin_lat": 31.1048,
            "origin_lon": 77.1734,
            "origin_name": "Shimla",
            "destination_lat": 31.0979,
            "destination_lon": 77.2674,
            "destination_name": "Kufri",
        },
    )
    assert plan_res.status_code == 201
    route_id = plan_res.json()["route_id"]

    # 2. Start trip
    start_res = await client.post(
        "/api/v1/trips/start",
        json={"route_id": route_id, "device_id": "test-device-uuid-123"},
    )
    assert start_res.status_code == 201
    trip_data = start_res.json()
    assert trip_data["status"] == "ACTIVE"
    trip_id = trip_data["trip_id"]

    # 3. Post progress (near start point)
    prog_res = await client.post(
        f"/api/v1/trips/{trip_id}/progress",
        json={"latitude": 31.1050, "longitude": 77.1740, "speed_kmh": 32.0},
    )
    assert prog_res.status_code == 200
    prog_data = prog_res.json()
    assert prog_data["status"] == "ACTIVE"
    assert prog_data["is_off_route"] is False
    assert prog_data["distance_remaining_km"] > 0

    # 4. Check trip status
    stat_res = await client.get(f"/api/v1/trips/{trip_id}/status")
    assert stat_res.status_code == 200
    assert stat_res.json()["status"] == "ACTIVE"

    # 5. Complete trip (post location at destination)
    arr_res = await client.post(
        f"/api/v1/trips/{trip_id}/progress",
        json={"latitude": 31.0979, "longitude": 77.2674, "speed_kmh": 0.0},
    )
    assert arr_res.status_code == 200
    assert arr_res.json()["status"] == "COMPLETED"

    # 6. Explicit stop call
    stop_res = await client.post(f"/api/v1/trips/{trip_id}/stop")
    assert stop_res.status_code == 200
    assert stop_res.json()["status"] == "COMPLETED"

