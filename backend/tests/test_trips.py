"""Tests for Active Trip Tracking, Progress Monitoring, and Batch Sync API."""
import pytest
from datetime import datetime, timezone
from app.models.planned_route import PlannedRoute, ActiveTrip


@pytest.fixture
async def planned_test_route(test_db):
    """Inserts a verified Uttarakhand trekking route for active tracking tests."""
    route = PlannedRoute(
        id="test-trek-route-kedarnath",
        title="Gaurikund to Kedarnath Trail",
        origin_name="Gaurikund Roadhead",
        origin_lat=30.6534,
        origin_lon=79.0268,
        destination_name="Kedarnath Temple",
        dest_lat=30.7339,
        dest_lon=79.0669,
        travel_mode="hiking",
        distance_km=16.0,
        duration_minutes=360.0,
        geometry_geojson={
            "type": "LineString",
            "coordinates": [
                [79.0268, 30.6534],
                [79.0400, 30.6800],
                [79.0550, 30.7100],
                [79.0669, 30.7339],
            ]
        },
        overall_hazard_score=15.0,
        overall_safety_status="SAFE",
        route_version=1,
        conditions_data=[
            {
                "waypoint_index": 0,
                "name": "Gaurikund Roadhead",
                "latitude": 30.6534,
                "longitude": 79.0268,
                "elevation_m": 1982.0,
                "hazard_level": "LOW",
                "alerts": [],
                "weather": {"temperature_c": 14.0, "weather_description": "Partly Cloudy"},
            },
            {
                "waypoint_index": 1,
                "name": "Jungle Chatti",
                "latitude": 30.6800,
                "longitude": 79.0400,
                "elevation_m": 2350.0,
                "hazard_level": "LOW",
                "alerts": [],
                "weather": {"temperature_c": 11.0, "weather_description": "Clear"},
            },
            {
                "waypoint_index": 2,
                "name": "Bheembali Cliff",
                "latitude": 30.7100,
                "longitude": 79.0550,
                "elevation_m": 2780.0,
                "hazard_level": "HIGH",
                "alerts": [{"id": "alert-landslide-1", "title": "Rockfall Risk Area"}],
                "weather": {"temperature_c": 8.0, "weather_description": "Windy"},
            },
            {
                "waypoint_index": 3,
                "name": "Kedarnath Temple",
                "latitude": 30.7339,
                "longitude": 79.0669,
                "elevation_m": 3583.0,
                "hazard_level": "LOW",
                "alerts": [],
                "weather": {"temperature_c": 4.0, "weather_description": "Cold"},
            },
        ],
    )
    test_db.add(route)
    await test_db.commit()
    await test_db.refresh(route)
    return route


@pytest.mark.asyncio
async def test_start_active_trip(client, planned_test_route):
    """Test starting an active trip monitoring session."""
    res = await client.post("/api/v1/trips/start", json={
        "route_id": planned_test_route.id,
        "device_id": "device-test-123",
        "user_id": "user-test-456"
    })
    assert res.status_code == 201
    data = res.json()
    assert "trip_id" in data
    assert data["route_id"] == planned_test_route.id
    assert data["status"] == "ACTIVE"


@pytest.mark.asyncio
async def test_single_point_trip_progress(client, planned_test_route):
    """Test updating trip progress with a single GPS point."""
    start_res = await client.post("/api/v1/trips/start", json={
        "route_id": planned_test_route.id,
        "device_id": "device-test-123"
    })
    trip_id = start_res.json()["trip_id"]

    # Post progress at Jungle Chatti
    prog_res = await client.post(f"/api/v1/trips/{trip_id}/progress", json={
        "latitude": 30.6800,
        "longitude": 79.0400,
        "speed_kmh": 3.5
    })
    assert prog_res.status_code == 200
    prog_data = prog_res.json()
    assert prog_data["trip_id"] == trip_id
    assert prog_data["current_segment_index"] == 1
    assert prog_data["is_off_route"] is False
    assert prog_data["distance_to_nearest_hazard_km"] is not None
    assert "Rockfall" in (prog_data["nearest_hazard_title"] or "")


@pytest.mark.asyncio
async def test_batch_track_points_sync(client, planned_test_route):
    """Test synchronizing a queued batch of offline GPS track points."""
    start_res = await client.post("/api/v1/trips/start", json={
        "route_id": planned_test_route.id,
        "device_id": "device-test-offline"
    })
    trip_id = start_res.json()["trip_id"]

    # Batch of 4 sequential points moving from Gaurikund up the trail
    batch_payload = {
        "points": [
            {
                "latitude": 30.6534,
                "longitude": 79.0268,
                "altitude_m": 1982.0,
                "accuracy_m": 4.5,
                "speed_kmh": 3.0,
                "recorded_at": "2026-10-26T06:00:00Z"
            },
            {
                "latitude": 30.6600,
                "longitude": 79.0310,
                "altitude_m": 2100.0,
                "accuracy_m": 5.0,
                "speed_kmh": 3.2,
                "recorded_at": "2026-10-26T06:15:00Z"
            },
            {
                "latitude": 30.6700,
                "longitude": 79.0360,
                "altitude_m": 2220.0,
                "accuracy_m": 6.0,
                "speed_kmh": 2.8,
                "recorded_at": "2026-10-26T06:30:00Z"
            },
            {
                "latitude": 30.6800,
                "longitude": 79.0400,
                "altitude_m": 2350.0,
                "accuracy_m": 4.0,
                "speed_kmh": 3.1,
                "recorded_at": "2026-10-26T06:45:00Z"
            }
        ]
    }

    sync_res = await client.post(f"/api/v1/trips/{trip_id}/sync-points", json=batch_payload)
    assert sync_res.status_code == 200
    sync_data = sync_res.json()
    assert sync_data["trip_id"] == trip_id
    assert sync_data["synced_count"] == 4
    assert sync_data["traveled_distance_km"] > 2.0
    assert sync_data["progress_pct"] > 10.0
    assert sync_data["is_off_route"] is False
    assert "synchronized 4" in sync_data["message"]

    # Verify status reflects batch sync updates
    status_res = await client.get(f"/api/v1/trips/{trip_id}/status")
    assert status_res.status_code == 200
    status_data = status_res.json()
    assert status_data["last_latitude"] == 30.6800
    assert status_data["last_longitude"] == 79.0400
    assert status_data["current_segment_index"] == 1


@pytest.mark.asyncio
async def test_stop_active_trip(client, planned_test_route):
    """Test stopping and completing an active trek session."""
    start_res = await client.post("/api/v1/trips/start", json={
        "route_id": planned_test_route.id,
    })
    trip_id = start_res.json()["trip_id"]

    stop_res = await client.post(f"/api/v1/trips/{trip_id}/stop")
    assert stop_res.status_code == 200
    assert stop_res.json()["status"] == "COMPLETED"

    status_res = await client.get(f"/api/v1/trips/{trip_id}/status")
    assert status_res.json()["status"] == "COMPLETED"
    assert status_res.json()["completed_at"] is not None

