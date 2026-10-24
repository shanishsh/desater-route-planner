"""
Comprehensive tests for Dynamic Any-Location Route Planning, Corridor Safety Analysis,
Provider Geographic Coverage Engine, and Place Geocoding.
"""

import pytest
from app.services.provider_registry import provider_registry
from app.services.routing.base import RouteResult, Waypoint, RouteAlternative
from app.services.routing.osrm_provider import osrm_routing_provider


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
    """Verifies that places search operates globally and reverse geocoding returns structured pin data."""
    from app.clients.open_meteo import open_meteo_client

    async def mock_search(query: str, count: int = 8):
        return [
            {
                "name": "Manali",
                "latitude": 32.2396,
                "longitude": 77.1887,
                "country": "India",
                "admin1": "Himachal Pradesh",
                "display_name": "Manali, Himachal Pradesh (India)",
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

    # 1. Forward search
    resp = await client.get("/api/v1/places/search?q=manali")
    assert resp.status_code == 200
    results = resp.json()
    assert len(results) == 2
    assert results[0]["name"] == "Manali"
    assert results[1]["name"] == "Zermatt"

    # 2. Reverse geocode pin
    rev_resp = await client.get("/api/v1/places/reverse?lat=32.2396&lon=77.1887")
    assert rev_resp.status_code == 200
    rev_data = rev_resp.json()
    assert "Map Pin" in rev_data["name"]
    assert rev_data["latitude"] == 32.2396
    assert rev_data["longitude"] == 77.1887


@pytest.mark.asyncio
async def test_dynamic_plan_and_coverage_reporting_international(client, monkeypatch):
    """
    Verifies that an international route (e.g. Alps trek) plans dynamically,
    and NDMA SACHET reports UNSUPPORTED_REGION without breaking global analysis.
    """
    async def mock_calculate(*args, **kwargs):
        return RouteResult(
            route_id="intl-route-alps-1",
            title="Chamonix to Zermatt Alpine Route",
            origin_name="Chamonix",
            origin_lat=45.9237,
            origin_lon=6.8694,
            destination_name="Zermatt",
            destination_lat=45.9765,
            destination_lon=7.7491,
            travel_mode="hiking",
            distance_km=72.0,
            duration_minutes=1440.0,
            geometry_geojson={
                "type": "LineString",
                "coordinates": [
                    [6.8694, 45.9237],
                    [7.3000, 45.9500],
                    [7.7491, 45.9765],
                ],
            },
            waypoints=[
                Waypoint(0, "Start: Chamonix", 45.9237, 6.8694, 0.0, 0.0),
                Waypoint(1, "Destination: Zermatt", 45.9765, 7.7491, 72.0, 1440.0),
            ],
            alternatives=[
                RouteAlternative(
                    id="alt-intl-1",
                    title="High Route Glacier Variant",
                    description="Glacial alternative path.",
                    distance_km=78.5,
                    duration_minutes=1600.0,
                    geometry_geojson={
                        "type": "LineString",
                        "coordinates": [
                            [6.8694, 45.9237],
                            [7.2500, 45.9800],
                            [7.7491, 45.9765],
                        ],
                    },
                )
            ],
        )

    monkeypatch.setattr(osrm_routing_provider, "calculate_route", mock_calculate)

    payload = {
        "origin_lat": 45.9237,
        "origin_lon": 6.8694,
        "origin_name": "Chamonix",
        "destination_lat": 45.9765,
        "destination_lon": 7.7491,
        "destination_name": "Zermatt",
        "travel_mode": "hiking",
        "corridor_distance_km": 5.0,
    }

    resp = await client.post("/api/v1/routes/plan", json=payload)
    assert resp.status_code == 201
    data = resp.json()

    assert data["route_id"] == "intl-route-alps-1"
    assert data["travel_mode"] == "hiking"
    assert data["distance_km"] == 72.0
    assert len(data["alternatives"]) == 1

    # Verify provider statuses: SACHET should report UNSUPPORTED_REGION
    prov_statuses = data.get("provider_statuses", {})
    assert "ndma_sachet" in prov_statuses
    assert prov_statuses["ndma_sachet"]["status"] == "UNSUPPORTED_REGION"

    # Verify analysis limitations note SACHET India-only constraint
    limitations = data.get("analysis_limitations", [])
    assert any("NDMA SACHET" in lim for lim in limitations)


@pytest.mark.asyncio
async def test_dynamic_plan_unroutable_coordinates_returns_404(client, monkeypatch):
    """
    Verifies that when OSRM reports no route between disconnected coordinates
    (e.g. island or across impassable water), an informative 404 is returned.
    """
    import httpx
    from fastapi import HTTPException

    async def mock_calculate_noroute(*args, **kwargs):
        raise HTTPException(
            status_code=404,
            detail="No viable route found between coordinates (0.0000, 0.0000) and (10.0000, 10.0000) for travel mode 'walking'. (NoRoute: Impossible path)",
        )

    monkeypatch.setattr(osrm_routing_provider, "calculate_route", mock_calculate_noroute)

    payload = {
        "origin_lat": 0.0,
        "origin_lon": 0.0,
        "origin_name": "Ocean Point A",
        "destination_lat": 10.0,
        "destination_lon": 10.0,
        "destination_name": "Ocean Point B",
        "travel_mode": "walking",
    }

    resp = await client.post("/api/v1/routes/plan", json=payload)
    assert resp.status_code == 404
    assert "No viable route found" in resp.json()["detail"]

