"""Tests for OpenStreetMap facilities pipeline, Overpass resilience, and corridor querying."""
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch, MagicMock
import pytest
from app.models.map_point import MapPoint
from app.models.planned_route import PlannedRoute
from app.clients.osm_amenities import OSMAmenitiesClient, osm_amenities_client
from app.services.emergency_service import EmergencyService


@pytest.mark.asyncio
async def test_overpass_deduplication_and_categorization():
    """Verifies that OSM Overpass parser deduplicates near-identical records and tags categories properly."""
    client = OSMAmenitiesClient()
    mock_elements = [
        {
            "type": "node",
            "id": 1001,
            "lat": 30.1234,
            "lon": 78.1234,
            "tags": {"amenity": "hospital", "name": "City General Hospital", "phone": "108"},
        },
        # Duplicate within 10 meters with same name
        {
            "type": "way",
            "id": 2002,
            "center": {"lat": 30.12345, "lon": 78.12345},
            "tags": {"amenity": "hospital", "name": "City General Hospital", "emergency": "yes"},
        },
        # Distinct clinic
        {
            "type": "node",
            "id": 1003,
            "lat": 30.1300,
            "lon": 78.1300,
            "tags": {"amenity": "clinic", "name": "Community Health Clinic"},
        },
        # Police station
        {
            "type": "node",
            "id": 1004,
            "lat": 30.1400,
            "lon": 78.1400,
            "tags": {"amenity": "police", "name": "Kotwali Police Station"},
        },
        # Fuel station
        {
            "type": "node",
            "id": 1005,
            "lat": 30.1500,
            "lon": 78.1500,
            "tags": {"amenity": "fuel", "name": "HP Petrol Pump"},
        },
        # Fire station
        {
            "type": "node",
            "id": 1006,
            "lat": 30.1600,
            "lon": 78.1600,
            "tags": {"amenity": "fire_station", "name": "City Fire Service"},
        },
    ]

    mock_resp = MagicMock()
    mock_resp.status_code = 200
    mock_resp.json.return_value = {"elements": mock_elements}

    with patch.object(client, "get_client") as mock_get_client:
        mock_http = AsyncMock()
        mock_http.post.return_value = mock_resp
        mock_get_client.return_value = mock_http

        results = await client.fetch_amenities(30.10, 78.10, 30.20, 78.20)

    # 6 elements with 1 duplicate -> 5 deduped facilities
    assert len(results) == 5
    categories = [r["category"] for r in results]
    assert "hospital" in categories
    assert "clinic" in categories
    assert "police" in categories
    assert "fuel" in categories
    assert "fire_station" in categories

    # Verify duplicate was merged and phone preserved
    hosp = next(r for r in results if r["category"] == "hospital")
    assert hosp["phone"] == "108"
    assert hosp["emergency_verified"] is True


@pytest.mark.asyncio
async def test_overpass_timeout_with_cached_records(client, test_db):
    """When Overpass times out but DB has cached facilities, returns cached with provider_status='stale'."""
    now = datetime.now(timezone.utc)
    p = MapPoint(
        name="Cached District Hospital",
        category="hospital",
        latitude=30.30,
        longitude=78.30,
        verification_status="verified",
        verification_timestamp=now,
        provenance="OpenStreetMap",
    )
    test_db.add(p)
    await test_db.commit()

    with patch.object(osm_amenities_client, "fetch_amenities", new_callable=AsyncMock) as mock_fetch:
        mock_fetch.return_value = []
        osm_amenities_client.last_error = "Gateway Timeout on Overpass mirror"

        res = await client.get("/api/v1/map-points?bbox=78.20,30.20,78.40,30.40")
        assert res.status_code == 200
        data = res.json()
        assert data["total"] >= 1
        assert any(item["name"] == "Cached District Hospital" for item in data["items"])
        assert data["provider_status"] == "success" or data["is_stale"] is False

        # When queried in an uncached area where DB has 0 records but regional records exist:
        res_uncached = await client.get("/api/v1/map-points?bbox=79.80,28.20,79.90,28.30")
        assert res_uncached.status_code == 200
        data_uncached = res_uncached.json()
        # Fallback to cached regional records with stale marker
        assert data_uncached["provider_status"] == "stale"
        assert data_uncached["is_stale"] is True
        assert data_uncached["total"] >= 1


@pytest.mark.asyncio
async def test_overpass_timeout_with_no_cached_records(test_db):
    """When Overpass times out and database is completely empty, returns provider_error (never empty success)."""
    service = EmergencyService(test_db)
    # Mock point repo to return 0 records
    with patch.object(service.point_repo, "count_all", new_callable=AsyncMock) as mock_count:
        mock_count.return_value = 0
        with patch.object(service.point_repo, "list_points", new_callable=AsyncMock) as mock_list:
            mock_list.return_value = ([], 0)
            with patch.object(osm_amenities_client, "fetch_amenities", new_callable=AsyncMock) as mock_fetch:
                mock_fetch.return_value = []
                osm_amenities_client.last_error = "Connection refused to all mirrors"

                items, total, meta = await service.list_points(
                    min_lat=30.0, min_lon=78.0, max_lat=30.1, max_lon=78.1
                )

                assert items == []
                assert total == 0
                assert meta["provider_status"] == "provider_error"
                assert "Connection refused" in meta["last_error"]


@pytest.mark.asyncio
async def test_clean_empty_overpass_response(test_db):
    """When Overpass responds 200 with genuinely 0 elements, returns provider_status='empty'."""
    service = EmergencyService(test_db)
    with patch.object(service.point_repo, "list_points", new_callable=AsyncMock) as mock_list:
        mock_list.return_value = ([], 0)
        with patch.object(osm_amenities_client, "fetch_amenities", new_callable=AsyncMock) as mock_fetch:
            mock_fetch.return_value = []
            osm_amenities_client.last_error = None  # Clean 200 OK empty response

            items, total, meta = await service.list_points(
                min_lat=30.0, min_lon=78.0, max_lat=30.1, max_lon=78.1
            )

            assert items == []
            assert total == 0
            assert meta["provider_status"] == "empty"


@pytest.mark.asyncio
async def test_route_corridor_facilities_filtering(client, test_db):
    """Verifies route_id corridor query returns only facilities within the corridor distance."""
    now = datetime.now(timezone.utc)
    # Create route along 30.0, 78.0 -> 30.1, 78.0
    route = PlannedRoute(
        id="test-corridor-route-1",
        title="Rishikesh Corridor Trek",
        origin_name="Rishikesh Start",
        origin_lat=30.0,
        origin_lon=78.0,
        destination_name="Corridor Camp",
        dest_lat=30.1,
        dest_lon=78.0,
        travel_mode="walking",
        distance_km=11.1,
        duration_minutes=120,
        geometry_geojson={
            "type": "LineString",
            "coordinates": [[78.0, 30.0], [78.0, 30.05], [78.0, 30.1]],
        },
        overall_safety_status="SAFE",
        overall_hazard_score=10.0,
    )
    # Point close to route (< 1 km)
    p_close = MapPoint(
        id="poi-close-1",
        name="Corridor Emergency Clinic",
        category="clinic",
        latitude=30.05,
        longitude=78.005,  # ~500m east of route
        verification_status="verified",
        verification_timestamp=now,
        provenance="OpenStreetMap",
    )
    # Point far from route (> 15 km)
    p_far = MapPoint(
        id="poi-far-1",
        name="Far Distant Clinic",
        category="clinic",
        latitude=30.05,
        longitude=78.200,  # ~20km east of route
        verification_status="verified",
        verification_timestamp=now,
        provenance="OpenStreetMap",
    )
    test_db.add_all([route, p_close, p_far])
    await test_db.commit()

    res = await client.get("/api/v1/map-points?route_id=test-corridor-route-1&corridor_km=5.0")
    assert res.status_code == 200
    data = res.json()
    assert data["total"] >= 1
    names = [item["name"] for item in data["items"]]
    assert "Corridor Emergency Clinic" in names
    assert "Far Distant Clinic" not in names
