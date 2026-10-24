"""
Unit and integration tests for Uttarakhand Regional Scoping and Verified Corridors.
"""

import pytest
from app.services.spatial.region_boundary_service import region_boundary_service
from app.services.routing.verified_corridors_registry import (
    get_verified_corridors,
    find_verified_corridor,
    is_pedestrian_only_corridor,
)


def test_points_inside_and_outside_uttarakhand():
    """Validates authoritative administrative boundary containment."""
    # Inside Uttarakhand
    assert region_boundary_service.is_point_in_uttarakhand(30.3165, 78.0322) is True  # Dehradun
    assert region_boundary_service.is_point_in_uttarakhand(30.6534, 79.0268) is True  # Gaurikund
    assert region_boundary_service.is_point_in_uttarakhand(30.7339, 79.0669) is True  # Kedarnath
    assert region_boundary_service.is_point_in_uttarakhand(30.0869, 78.2676) is True  # Rishikesh
    assert region_boundary_service.is_point_in_uttarakhand(30.4598, 78.0700) is True  # Mussoorie

    # Outside Uttarakhand
    assert region_boundary_service.is_point_in_uttarakhand(28.6139, 77.2090) is False  # Delhi
    assert region_boundary_service.is_point_in_uttarakhand(30.7333, 76.7794) is False  # Chandigarh
    assert region_boundary_service.is_point_in_uttarakhand(31.1048, 77.1734) is False  # Shimla
    assert region_boundary_service.is_point_in_uttarakhand(19.0760, 72.8777) is False  # Mumbai


def test_verified_corridors_registry():
    """Validates Stage 1 pilot corridors and allowed modes."""
    corridors = get_verified_corridors()
    assert len(corridors) >= 5

    # Check Kedarnath Trek
    kedar = find_verified_corridor("uk-corridor-gaurikund-kedarnath-trek")
    assert kedar is not None
    assert kedar["corridor_type"] == "ALPINE_TREK"
    assert "hiking" in kedar["allowed_modes"]
    assert "driving" not in kedar["allowed_modes"]

    # Check Gaurikund Road Approach
    gauri_road = find_verified_corridor("uk-corridor-rudraprayag-gaurikund")
    assert gauri_road is not None
    assert gauri_road["corridor_type"] == "ROAD_APPROACH"
    assert "driving" in gauri_road["allowed_modes"]


def test_pedestrian_only_corridor_rule():
    """Validates that motor driving is forbidden on pedestrian alpine tracks."""
    is_ped, reason = is_pedestrian_only_corridor(
        origin_lat=30.6534,
        origin_lon=79.0268,
        dest_lat=30.7339,
        dest_lon=79.0669,
    )
    assert is_ped is True
    assert "Gaurikund" in reason
    assert "hiking" in reason

    # Road corridor should not be flagged as pedestrian only
    is_ped_road, _ = is_pedestrian_only_corridor(
        origin_lat=30.2844,
        origin_lon=78.9806,
        dest_lat=30.6534,
        dest_lon=79.0268,
    )
    assert is_ped_road is False


@pytest.mark.asyncio
async def test_route_plan_rejects_invalid_wgs84_coordinates(client):
    """Rejects route planning requests when coordinates exceed valid WGS84 bounds."""
    payload = {
        "origin_lat": 95.0,  # Invalid latitude > 90
        "origin_lon": 77.2090,
        "origin_name": "Invalid Origin",
        "destination_lat": 30.33474,
        "destination_lon": 77.96589,
        "destination_name": "Valid Destination",
        "travel_mode": "driving",
    }
    resp = await client.post("/api/v1/routes/plan", json=payload)
    assert resp.status_code == 422
    assert "outside valid WGS84 bounds" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_route_plan_dynamic_any_location(client, monkeypatch):
    """Verifies that routes outside Uttarakhand (e.g. Himachal or Delhi) calculate dynamically."""
    from app.services.routing.osrm_provider import osrm_routing_provider
    from app.services.routing.base import RouteResult, Waypoint

    async def mock_calculate(*args, **kwargs):
        return RouteResult(
            route_id="test-dynamic-any-loc-1",
            title="Delhi to Premnagar",
            origin_name="New Delhi",
            origin_lat=28.6139,
            origin_lon=77.2090,
            destination_name="Premnagar",
            destination_lat=30.3347,
            destination_lon=77.9658,
            travel_mode="driving",
            distance_km=240.5,
            duration_minutes=270.0,
            geometry_geojson={
                "type": "LineString",
                "coordinates": [
                    [77.2090, 28.6139],
                    [77.5000, 29.5000],
                    [77.9658, 30.3347],
                ],
            },
            waypoints=[
                Waypoint(0, "Start: New Delhi", 28.6139, 77.2090, 0.0, 0.0),
                Waypoint(1, "Destination: Premnagar", 30.3347, 77.9658, 240.5, 270.0),
            ],
            alternatives=[],
        )

    monkeypatch.setattr(osrm_routing_provider, "calculate_route", mock_calculate)

    payload = {
        "origin_lat": 28.6139,
        "origin_lon": 77.2090,
        "origin_name": "New Delhi, Delhi",
        "destination_lat": 30.3347,
        "destination_lon": 77.9658,
        "destination_name": "Premnagar",
        "travel_mode": "driving",
    }
    resp = await client.post("/api/v1/routes/plan", json=payload)
    assert resp.status_code == 201
    data = resp.json()
    assert data["route_id"] == "test-dynamic-any-loc-1"
    assert data["distance_km"] == 240.5


@pytest.mark.asyncio
async def test_route_plan_rejects_driving_on_kedarnath_trek(client):
    """Rejects driving mode requests on the Gaurikund-Kedarnath trail."""
    payload = {
        "origin_lat": 30.6534,
        "origin_lon": 79.0268,
        "origin_name": "Gaurikund, Uttarakhand",
        "destination_lat": 30.7339,
        "destination_lon": 79.0669,
        "destination_name": "Kedarnath Temple, Uttarakhand",
        "travel_mode": "driving",
    }
    resp = await client.post("/api/v1/routes/plan", json=payload)
    assert resp.status_code == 400
    assert "strictly prohibited" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_verified_corridors_api_endpoint(client):
    """Verifies that the /verified-corridors endpoint returns the pilot catalog."""
    resp = await client.get("/api/v1/routes/verified-corridors")
    assert resp.status_code == 200
    data = resp.json()
    assert data["region"] == "Uttarakhand"
    assert len(data["corridors"]) >= 5


@pytest.mark.asyncio
async def test_region_info_api_endpoint(client):
    """Verifies that the /region-info endpoint returns Uttarakhand metadata."""
    resp = await client.get("/api/v1/routes/region-info")
    assert resp.status_code == 200
    data = resp.json()
    assert data["region_name"] == "Uttarakhand"
    assert data["boundary_verified"] is True

