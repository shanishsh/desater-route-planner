"""Tests for Itinerary endpoints."""
import pytest
from app.models.itinerary import Itinerary, RouteSegment


@pytest.mark.asyncio
async def test_itinerary_lifecycle(client, test_db):
    route_geojson = {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "geometry": {
                    "type": "LineString",
                    "coordinates": [[77.18, 32.23], [77.20, 32.25]],
                },
            }
        ],
    }

    itinerary = Itinerary(
        title="Test Summit Route",
        description="Alpine route for testing.",
        region="Northern Mountains",
        difficulty="hard",
        distance_km=12.0,
        estimated_duration_hours=4.5,
        start_latitude=32.23,
        start_longitude=77.18,
        end_latitude=32.25,
        end_longitude=77.20,
        route_geojson=route_geojson,
        is_active=True,
    )
    test_db.add(itinerary)
    await test_db.flush()

    segment = RouteSegment(
        itinerary_id=itinerary.id,
        sequence_order=1,
        name="Climb Segment 1",
        elevation_gain_m=300.0,
        hazard_level="low",
    )
    test_db.add(segment)
    await test_db.commit()

    # 1. List itineraries
    res_list = await client.get("/api/v1/itineraries")
    assert res_list.status_code == 200
    data_list = res_list.json()
    assert data_list["total"] == 1
    assert data_list["items"][0]["title"] == "Test Summit Route"
    assert len(data_list["items"][0]["segments"]) == 1

    # 2. Get itinerary by ID
    res_get = await client.get(f"/api/v1/itineraries/{itinerary.id}")
    assert res_get.status_code == 200
    assert res_get.json()["title"] == "Test Summit Route"

    # 3. Get route GeoJSON
    res_route = await client.get(f"/api/v1/itineraries/{itinerary.id}/route")
    assert res_route.status_code == 200
    assert "route_geojson" in res_route.json()

    # 4. Non-existent itinerary
    res_not_found = await client.get("/api/v1/itineraries/nonexistent-id-123")
    assert res_not_found.status_code == 404

