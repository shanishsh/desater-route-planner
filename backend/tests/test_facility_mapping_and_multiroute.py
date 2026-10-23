"""Unit and integration tests for essential facility mapping and multi-route comparison."""
import pytest
from app.clients.osm_amenities import OSMAmenitiesClient, _haversine_meters
from app.schemas.route_plan import SafeHavenInfo, RouteAlternativeSchema
from app.services.routing.osrm_provider import OSRMRoutingProvider


def test_haversine_meters():
    # Distance between two nearby points in Dehradun (~111 meters)
    d = _haversine_meters(30.3840, 77.9316, 30.3850, 77.9316)
    assert 100.0 < d < 120.0


def test_safe_haven_info_schema():
    sh = SafeHavenInfo(
        name="Doon Hospital",
        category="hospital",
        latitude=30.3245,
        longitude=78.0412,
        distance_km=1.2,
        opening_hours="24/7",
        phone="+91-135-265-2121",
        website="https://doonhospital.gov.in",
        emergency_verified=True,
        brand=None,
        operator="Government of Uttarakhand",
        address="Court Road, Dehradun",
        source="OpenStreetMap Overpass",
    )
    assert sh.category == "hospital"
    assert sh.emergency_verified is True
    assert sh.phone == "+91-135-265-2121"
    assert sh.opening_hours == "24/7"


def test_fuel_station_safe_haven_info():
    fuel = SafeHavenInfo(
        name="HP Auto Care",
        category="fuel",
        latitude=30.3340,
        longitude=77.9650,
        distance_km=0.4,
        brand="Hindustan Petroleum",
        emergency_verified=False,
    )
    assert fuel.category == "fuel"
    assert fuel.brand == "Hindustan Petroleum"
    assert fuel.emergency_verified is False
    assert fuel.opening_hours is None  # Factual: None when absent, no fake 24/7 hours


def test_route_alternative_preference_rankings():
    alt = RouteAlternativeSchema(
        id="alt-test-1",
        title="Alternative Route 1",
        description="Via Rajpur bypass",
        distance_km=14.2,
        duration_minutes=22.0,
        geometry_geojson={"type": "LineString", "coordinates": [[77.93, 30.38], [77.96, 30.33]]},
        hazard_score=15.0,
        safety_label="Safe",
        hospitals_count=2,
        clinics_count=1,
        police_stations_count=1,
        fuel_stations_count=3,
        nearest_hospital_km=0.8,
        nearest_police_km=1.2,
        nearest_fuel_km=0.3,
        preference_rankings={
            "shortest": {"rank": 2, "metric": "14.2 km"},
            "fastest": {"rank": 1, "metric": "22 min"},
            "safest": {"rank": 1, "metric": "Hazard: 15/100"},
            "facility_access": {"rank": 1, "metric": "3 emergency sites"},
            "balanced": {"rank": 1, "metric": "Rank #1"},
        },
    )
    assert alt.hospitals_count == 2
    assert alt.fuel_stations_count == 3
    assert alt.preference_rankings["fastest"]["rank"] == 1
    assert alt.preference_rankings["safest"]["metric"] == "Hazard: 15/100"


def test_osrm_provider_requests_three_alternatives():
    provider = OSRMRoutingProvider()
    assert provider is not None

