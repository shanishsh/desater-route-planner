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
async def test_route_plan_rejects_out_of_state_origin_or_destination(client):
    """Verifies that routes originating outside Uttarakhand (e.g. Delhi) are strictly rejected with HTTP 422."""
    payload = {
        "origin_lat": 28.6139,
        "origin_lon": 77.2090,
        "origin_name": "New Delhi, Delhi",
        "destination_lat": 30.3347,
        "destination_lon": 77.9658,
        "destination_name": "Premnagar, Dehradun",
        "travel_mode": "driving",
    }
    resp = await client.post("/api/v1/routes/plan", json=payload)
    assert resp.status_code == 422
    assert "outside the state of Uttarakhand" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_route_plan_rejects_out_of_state_destination(client):
    """Verifies that routes with destination outside Uttarakhand (e.g. Chandigarh) are rejected with HTTP 422."""
    payload = {
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "origin_name": "Dehradun, Uttarakhand",
        "destination_lat": 30.7333,
        "destination_lon": 76.7794,
        "destination_name": "Chandigarh",
        "travel_mode": "driving",
    }
    resp = await client.post("/api/v1/routes/plan", json=payload)
    assert resp.status_code == 422
    assert "outside the state of Uttarakhand" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_route_plan_accepts_valid_in_state_route(client, monkeypatch):
    """Verifies that genuine in-state Uttarakhand routes plan successfully."""
    from app.services.routing.osrm_provider import osrm_routing_provider
    from app.services.routing.base import RouteResult, Waypoint

    async def mock_calculate(*args, **kwargs):
        return RouteResult(
            route_id="test-uk-in-state-1",
            title="Rishikesh to Gaurikund",
            origin_name="Rishikesh",
            origin_lat=30.0869,
            origin_lon=78.2676,
            destination_name="Gaurikund",
            destination_lat=30.6534,
            destination_lon=79.0268,
            travel_mode="driving",
            distance_km=210.0,
            duration_minutes=360.0,
            geometry_geojson={
                "type": "LineString",
                "coordinates": [
                    [78.2676, 30.0869],
                    [78.6000, 30.3000],
                    [79.0268, 30.6534],
                ],
            },
            waypoints=[
                Waypoint(0, "Start: Rishikesh", 30.0869, 78.2676, 0.0, 0.0),
                Waypoint(1, "Destination: Gaurikund", 30.6534, 79.0268, 210.0, 360.0),
            ],
            alternatives=[],
        )

    monkeypatch.setattr(osrm_routing_provider, "calculate_route", mock_calculate)

    payload = {
        "origin_lat": 30.0869,
        "origin_lon": 78.2676,
        "origin_name": "Rishikesh, Uttarakhand",
        "destination_lat": 30.6534,
        "destination_lon": 79.0268,
        "destination_name": "Gaurikund, Uttarakhand",
        "travel_mode": "driving",
    }
    resp = await client.post("/api/v1/routes/plan", json=payload)
    assert resp.status_code == 201
    data = resp.json()
    assert data["route_id"] == "test-uk-in-state-1"
    assert data["distance_km"] == 210.0


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


@pytest.mark.asyncio
async def test_region_boundary_and_mask_endpoints(client):
    """Verifies that the boundary GeoJSON and mask GeoJSON endpoints return valid geometry."""
    resp_b = await client.get("/api/v1/routes/region-boundary")
    assert resp_b.status_code == 200
    boundary = resp_b.json()
    assert boundary["type"] in ("Polygon", "Feature", "FeatureCollection")

    resp_m = await client.get("/api/v1/routes/region-mask")
    assert resp_m.status_code == 200
    mask = resp_m.json()
    assert mask["type"] in ("Polygon", "Feature", "FeatureCollection")


@pytest.mark.asyncio
async def test_trail_report_rejects_out_of_state_location(client):
    """Verifies that trail reports submitted outside Uttarakhand are rejected with HTTP 422."""
    payload = {
        "category": "landslide",
        "severity": "HIGH",
        "passability": "IMPASSABLE",
        "latitude": 28.6139,  # Delhi
        "longitude": 77.2090,
        "description": "Landslide blocking road in Delhi",
    }
    resp = await client.post("/api/v1/trail-reports", json=payload)
    assert resp.status_code == 422
    assert "outside Uttarakhand" in resp.json()["detail"]


@pytest.mark.asyncio
async def test_places_search_filters_out_of_state(client, monkeypatch):
    """Verifies that place search filters out locations not in Uttarakhand."""
    from app.clients.open_meteo import open_meteo_client

    async def mock_search(query, count):
        return [
            {"name": "Dehradun", "latitude": 30.3165, "longitude": 78.0322, "country": "India", "admin1": "Uttarakhand", "display_name": "Dehradun, Uttarakhand"},
            {"name": "Delhi", "latitude": 28.6139, "longitude": 77.2090, "country": "India", "admin1": "Delhi", "display_name": "New Delhi, Delhi"},
        ]

    monkeypatch.setattr(open_meteo_client, "search_settlements", mock_search)
    resp = await client.get("/api/v1/places/search?q=Deh")
    assert resp.status_code == 200
    items = resp.json()
    assert len(items) == 1
    assert items[0]["name"] == "Dehradun"


def test_inter_district_transit_corridor():
    """Validates that in-state routes transiting border highway plains (e.g. Dehradun to Nainital/Almora) pass."""
    coords = [
        (30.3165, 78.0322),  # Dehradun (in UK)
        (29.6100, 78.3400),  # Najibabad transit plain (outside UK polygon, in transit envelope)
        (29.2100, 78.9600),  # Kashipur transit plain
        (29.3919, 79.4542),  # Nainital (in UK)
    ]
    # Driving mode: allowed for in-state endpoints
    valid_driving, reason_driving = region_boundary_service.is_route_in_uttarakhand(coords, travel_mode="driving")
    assert valid_driving is True
    assert reason_driving is None

    # Hiking mode: allowed for in-state endpoints within regional corridor
    valid_hiking, reason_hiking = region_boundary_service.is_route_in_uttarakhand(coords, travel_mode="hiking")
    assert valid_hiking is True
    assert reason_hiking is None


def test_route_transit_envelope_rejection():
    """Verifies that routes with points deviating far outside regional transit bounds are rejected."""
    coords = [
        (30.3165, 78.0322),  # Dehradun
        (26.8467, 80.9462),  # Lucknow (far outside regional transit bbox lat < 28.0)
        (29.3919, 79.4542),  # Nainital
    ]
    valid, reason = region_boundary_service.is_route_in_uttarakhand(coords, travel_mode="driving")
    assert valid is False
    assert "deviate excessively outside the regional transit corridor" in reason

