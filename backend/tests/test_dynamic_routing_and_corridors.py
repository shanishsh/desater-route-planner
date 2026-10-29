"""
Comprehensive tests for Dynamic Any-Location Route Planning, Corridor Safety Analysis,
Provider Geographic Coverage Engine, and Place Geocoding.
"""

import pytest
from app.services.provider_registry import provider_registry
from app.services.routing.base import RouteResult, Waypoint, RouteAlternative
from app.services.routing.osrm_provider import osrm_routing_provider
from app.services.routing.trail_routing_engine import trail_routing_engine


def test_provider_registry_coverage_engine():
    """Validates that provider coverage checks accurately reflect documented capability bounds."""
    # India territory checks (SACHET is India-only)
    assert provider_registry.is_point_in_india(32.2396, 77.1887) is True   # Manali, Himachal
    assert provider_registry.is_point_in_india(28.6139, 77.2090) is True   # Delhi
    assert provider_registry.is_point_in_india(19.0760, 72.8777) is True   # Mumbai
    assert provider_registry.is_point_in_india(30.6534, 79.0268) is True   # Gaurikund, Uttarakhand

    # Outside India territory checks
    assert provider_registry.is_point_in_india(45.9237, 6.8694) is False   # Chamonix, France
    assert provider_registry.is_point_in_india(37.7749, -122.4194) is False # San Francisco, USA
    assert provider_registry.is_point_in_india(-33.8688, 151.2093) is False # Sydney, Australia

    # Check provider coverage method
    in_cov, in_err = provider_registry.check_provider_coverage("ndma_sachet", 32.2396, 77.1887)
    assert in_cov is True
    assert in_err is None

    out_cov, out_err = provider_registry.check_provider_coverage("ndma_sachet", 45.9237, 6.8694)
    assert out_cov is False
    assert "India only" in out_err

    # Global providers should cover all coordinates
    glob_cov, _ = provider_registry.check_provider_coverage("open_meteo", 45.9237, 6.8694)
    assert glob_cov is True

    osm_cov, _ = provider_registry.check_provider_coverage("osm_overpass", 45.9237, 6.8694)
    assert osm_cov is True


@pytest.mark.asyncio
async def test_places_search_and_reverse_geocoding(client, monkeypatch):
    """Verifies that places search filters to Uttarakhand settlements and reverse geocoding returns structured pin data."""
    from app.clients.open_meteo import open_meteo_client

    async def mock_search(query: str, count: int = 8):
        return [
            {
                "name": "Rishikesh",
                "latitude": 30.0869,
                "longitude": 78.2676,
                "country": "India",
                "admin1": "Uttarakhand",
                "display_name": "Rishikesh, Uttarakhand (India)",
            },
            {
                "name": "Kedarnath",
                "latitude": 30.7346,
                "longitude": 79.0669,
                "country": "India",
                "admin1": "Uttarakhand",
                "display_name": "Kedarnath, Uttarakhand (India)",
            },
            {
                "name": "Zermatt",
                "latitude": 45.9765,
                "longitude": 7.7491,
                "country": "Switzerland",
                "admin1": "Valais",
                "display_name": "Zermatt, Valais (Switzerland)",
            },
        ]

    monkeypatch.setattr(open_meteo_client, "search_settlements", mock_search)

    # 1. Forward search filters out Zermatt (outside Uttarakhand) and keeps Rishikesh & Kedarnath
    resp = await client.get("/api/v1/places/search?q=rishikesh")
    assert resp.status_code == 200
    results = resp.json()
    assert len(results) == 2
    names = [r["name"] for r in results]
    assert "Rishikesh" in names
    assert "Kedarnath" in names
    assert "Zermatt" not in names

    # 2. Reverse geocode pin inside Uttarakhand
    rev_resp = await client.get("/api/v1/places/reverse?lat=30.0869&lon=78.2676")
    assert rev_resp.status_code == 200
    rev_data = rev_resp.json()
    assert "Map Pin" in rev_data["name"]
    assert rev_data["latitude"] == 30.0869
    assert rev_data["longitude"] == 78.2676
    assert rev_data["admin1"] == "Uttarakhand"

    # 3. Reverse geocode pin outside Uttarakhand
    out_resp = await client.get("/api/v1/places/reverse?lat=45.9765&lon=7.7491")
    assert out_resp.status_code == 200
    out_data = out_resp.json()
    assert "Outside Uttarakhand" in out_data["display_name"]
    assert out_data["admin1"] == "Unsupported"


@pytest.mark.asyncio
async def test_dynamic_plan_and_coverage_reporting_in_uttarakhand(client, monkeypatch):
    """
    Verifies that route planning within Uttarakhand succeeds,
    and out-of-boundary international routes are strictly rejected with 422.
    """
    async def mock_calculate(*args, **kwargs):
        return RouteResult(
            route_id="uk-route-kedarnath-1",
            title="Gaurikund to Kedarnath Trek",
            origin_name="Gaurikund",
            origin_lat=30.6534,
            origin_lon=79.0268,
            destination_name="Kedarnath",
            destination_lat=30.7339,
            destination_lon=79.0669,
            travel_mode="hiking",
            distance_km=16.0,
            duration_minutes=360.0,
            geometry_geojson={
                "type": "LineString",
                "coordinates": [
                    [79.0268, 30.6534],
                    [79.0450, 30.6900],
                    [79.0669, 30.7339],
                ],
            },
            waypoints=[
                Waypoint(0, "Start: Gaurikund", 30.6534, 79.0268, 0.0, 0.0),
                Waypoint(1, "Destination: Kedarnath", 30.7339, 79.0669, 16.0, 360.0),
            ],
            alternatives=[
                RouteAlternative(
                    id="alt-uk-1",
                    title="Jungle Chatti Alternative",
                    description="Secondary trail.",
                    distance_km=17.5,
                    duration_minutes=390.0,
                    geometry_geojson={
                        "type": "LineString",
                        "coordinates": [
                            [79.0268, 30.6534],
                            [79.0500, 30.6800],
                            [79.0669, 30.7339],
                        ],
                    },
                )
            ],
        )

    monkeypatch.setattr(osrm_routing_provider, "calculate_route", mock_calculate)
    monkeypatch.setattr(trail_routing_engine, "calculate_hiking_route", mock_calculate)

    # 1. Valid route within Uttarakhand
    payload = {
        "origin_lat": 30.6534,
        "origin_lon": 79.0268,
        "origin_name": "Gaurikund",
        "destination_lat": 30.7339,
        "destination_lon": 79.0669,
        "destination_name": "Kedarnath",
        "travel_mode": "hiking",
        "corridor_distance_km": 5.0,
    }

    resp = await client.post("/api/v1/routes/plan", json=payload)
    assert resp.status_code == 201
    data = resp.json()

    assert data["route_id"] == "uk-route-kedarnath-1"
    assert data["travel_mode"] == "hiking"
    assert data["distance_km"] == 16.0
    assert len(data["alternatives"]) == 1

    # Verify provider statuses include active providers
    prov_statuses = data.get("provider_statuses", {})
    assert "ndma_sachet" in prov_statuses

    # 2. International / out-of-state route must be rejected with 422
    intl_payload = {
        "origin_lat": 45.9237,
        "origin_lon": 6.8694,
        "origin_name": "Chamonix",
        "destination_lat": 45.9765,
        "destination_lon": 7.7491,
        "destination_name": "Zermatt",
        "travel_mode": "hiking",
    }
    intl_resp = await client.post("/api/v1/routes/plan", json=intl_payload)
    assert intl_resp.status_code == 422
    assert "outside the state of Uttarakhand" in intl_resp.json()["detail"]


@pytest.mark.asyncio
async def test_dynamic_plan_unroutable_coordinates_returns_404(client, monkeypatch):
    """
    Verifies that when OSRM reports no route between disconnected coordinates in Uttarakhand,
    an informative 404 is returned.
    """
    import httpx
    from fastapi import HTTPException

    async def mock_calculate_noroute(*args, **kwargs):
        raise HTTPException(
            status_code=404,
            detail="No viable route found between coordinates (30.6534, 79.0268) and (30.7339, 79.0669) for travel mode 'walking'. (NoRoute: Impossible path)",
        )

    monkeypatch.setattr(osrm_routing_provider, "calculate_route", mock_calculate_noroute)
    monkeypatch.setattr(trail_routing_engine, "calculate_hiking_route", mock_calculate_noroute)

    payload = {
        "origin_lat": 30.6534,
        "origin_lon": 79.0268,
        "origin_name": "Gaurikund",
        "destination_lat": 30.7339,
        "destination_lon": 79.0669,
        "destination_name": "Kedarnath",
        "travel_mode": "walking",
    }

    resp = await client.post("/api/v1/routes/plan", json=payload)
    assert resp.status_code == 404
    assert "No viable route found" in resp.json()["detail"]

