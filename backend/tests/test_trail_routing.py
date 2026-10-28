"""
Unit and integration tests for Trail-First Routing & Custom User-Drawn Routes.
"""
import pytest
from fastapi import HTTPException
from app.services.routing.trail_routing_engine import trail_routing_engine, point_to_segment_distance_km
from app.services.routing.base import haversine_distance_km


def test_point_to_segment_distance():
    # Segment from (30.0, 79.0) to (30.0, 79.1)
    # Point at (30.05, 79.05)
    dist_km, proj_lat, proj_lon = point_to_segment_distance_km(
        30.05, 79.05,
        30.0, 79.0,
        30.0, 79.1
    )
    assert 5.0 < dist_km < 6.0
    assert abs(proj_lat - 30.0) < 0.001
    assert abs(proj_lon - 79.05) < 0.001


@pytest.mark.asyncio
async def test_kedarnath_verified_trail_routing():
    # Gaurikund (30.6534, 79.0268) to Kedarnath Temple (30.7339, 79.0669)
    route = await trail_routing_engine.calculate_hiking_route(
        origin_lat=30.6534,
        origin_lon=79.0268,
        origin_name="Gaurikund Roadhead",
        dest_lat=30.7339,
        dest_lon=79.0669,
        dest_name="Kedarnath Temple",
    )

    assert route is not None
    assert route.travel_mode == "hiking"
    assert 9.0 <= route.distance_km <= 18.0
    assert route.verification_status == "MAPPED_TRAIL"
    assert "Kedarnath" in route.title
    coords = route.geometry_geojson.get("coordinates", [])
    assert len(coords) >= 10
    # First coordinate near Gaurikund, last coordinate near Kedarnath
    assert abs(coords[0][1] - 30.6534) < 0.01
    assert abs(coords[-1][1] - 30.7339) < 0.01


@pytest.mark.asyncio
async def test_partially_verified_with_proposed_connector():
    # Off-trail meadow point ~450m from Auli Kuari Pass trailhead
    origin_lat = 30.5310 + 0.003
    origin_lon = 79.5710 + 0.003
    route = await trail_routing_engine.calculate_hiking_route(
        origin_lat=origin_lat,
        origin_lon=origin_lon,
        origin_name="Near Auli Off-Trail",
        dest_lat=30.4780,
        dest_lon=79.6100,
        dest_name="Kuari Pass",
    )
    assert route.verification_status == "PARTIALLY_VERIFIED"
    assert route.unverified_reason is not None
    assert "off-trail approach" in route.unverified_reason.lower()
    assert route.proposed_connector_geojson is not None


@pytest.mark.asyncio
async def test_remote_wilderness_no_verified_route_rejection():
    # Remote high-altitude peak with no mapped trail (> 5 km from any trail)
    with pytest.raises(HTTPException) as exc_info:
        await trail_routing_engine.calculate_hiking_route(
            origin_lat=30.8500,
            origin_lon=79.4000,
            origin_name="Remote Ridge A",
            dest_lat=30.9500,
            dest_lon=79.5000,
            dest_name="Remote Peak B",
        )
    assert exc_info.value.status_code == 404
    assert "no verified connected hiking trail" in exc_info.value.detail.lower()
    assert "draw my route" in exc_info.value.detail.lower()


@pytest.mark.asyncio
async def test_valley_of_flowers_trail_routing():
    # Govindghat (30.6250, 79.5590) to Valley of Flowers (30.7280, 79.6050)
    route = await trail_routing_engine.calculate_hiking_route(
        origin_lat=30.6250,
        origin_lon=79.5590,
        origin_name="Govindghat",
        dest_lat=30.7280,
        dest_lon=79.6050,
        dest_name="Valley of Flowers Core",
    )
    assert route.verification_status == "MAPPED_TRAIL"
    assert 11.0 <= route.distance_km <= 18.0

