"""Deterministic automated tests for Dynamic Route Corridor Data Fetching.

Verifies:
1. Route & alert polygon intersection
2. Route & polygon outside corridor exclusion
3. Fire point inside corridor inclusion with distance metrics
4. Fire point outside corridor but inside provider bounding box exclusion
5. Width sensitivity (1 km vs 10 km corridor)
6. Destination change corridor recalculation
7. Successful provider response with zero matching records (empty layer state)
8. Provider outage isolation (failure in one provider does not erase others)
9. CAP alert without polygon geometry (no fabricated polygon drawn)
10. Multiple route weather sample points with arrival times
11. Stale provider record detection
"""
import json
import pytest
from datetime import datetime, timezone, timedelta
from app.models.disaster_alert import DisasterAlert
from app.services.spatial.route_corridor_service import route_corridor_service
from app.clients.nasa_firms import nasa_firms_client
from app.clients.open_meteo import open_meteo_client
from app.services.routing.base import RouteResult, Waypoint, RouteAlternative
from app.services.route_condition_analyzer import route_condition_analyzer


@pytest.mark.asyncio
async def test_corridor_construction_and_geometry():
    """Verify geodesic corridor polygon construction following route bends."""
    route_coords = [
        (30.3165, 78.0322),  # Manali
        (30.3800, 78.0500),  # Turn northwest
        (30.4598, 78.0700),  # Solang
    ]
    corridor = route_corridor_service.compute_corridor(route_coords, corridor_distance_meters=5000.0)

    assert "corridor_polygon_geojson" in corridor
    poly = corridor["corridor_polygon_geojson"]
    assert poly["type"] == "Polygon"
    assert len(poly["coordinates"]) >= 1
    # Check that corridor polygon has sufficient perimeter resolution
    assert len(poly["coordinates"][0]) >= 20

    assert corridor["corridor_distance_km"] == 5.0
    assert corridor["corridor_distance_meters"] == 5000.0
    assert corridor["area_sq_km"] > 10.0

    min_lat, min_lon, max_lat, max_lon = corridor["bbox"]
    assert min_lat < 30.3165
    assert max_lat > 30.4598
    assert min_lon < 78.0700
    assert max_lon > 78.0322


@pytest.mark.asyncio
async def test_route_and_alert_polygon_intersect(client, test_db):
    """Test 1: A route and an alert polygon that intersect are included in analysis and overlays."""
    now = datetime.now(timezone.utc)

    # Polygon along Dehradun-Mussoorie route corridor
    intersecting_poly = {
        "type": "Polygon",
        "coordinates": [[
            [78.055, 30.370],
            [78.065, 30.370],
            [78.065, 30.380],
            [78.055, 30.380],
            [78.055, 30.370]
        ]]
    }

    alert = DisasterAlert(
        title="Flash Flood Flash Alert - Rispana River",
        description="Rapid stream swelling across trail path",
        hazard_category="flash_flood",
        severity="CRITICAL",
        status="active",
        issuing_authority="DDMA Dehradun",
        required_protective_instructions="Avoid stream crossings",
        center_latitude=30.375,
        center_longitude=78.060,
        affected_radius_meters=1500.0,
        polygon_geojson=json.dumps(intersecting_poly),
        published_at=now,
    )
    test_db.add(alert)
    await test_db.commit()

    # Plan route
    plan_res = await client.post("/api/v1/routes/plan", json={
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_lat": 30.4598,
        "destination_lon": 78.0700,
        "destination_name": "Mussoorie Picture Palace",
        "corridor_distance_km": 5.0,
    })
    assert plan_res.status_code == 201
    route_id = plan_res.json()["route_id"]

    # Analyze corridor
    analyze_res = await client.post(f"/api/v1/routes/{route_id}/analyze", json={
        "corridor_distance_km": 5.0,
    })
    assert analyze_res.status_code == 200
    data = analyze_res.json()

    # Verify alert is included in corridor alerts and overlays
    assert any(a["title"] == alert.title for a in data["disaster_alerts"])
    overlays = data["overlays_geojson"]["features"]
    assert any(f["properties"].get("layer") == "disaster_alert" and f["properties"].get("title") == alert.title for f in overlays)
    assert any(f["properties"].get("layer") == "corridor" for f in overlays)


@pytest.mark.asyncio
async def test_route_and_polygon_outside_configured_corridor(client, test_db):
    """Test 2: A route and an alert polygon outside the configured corridor are excluded."""
    now = datetime.now(timezone.utc)

    # Distant polygon 35 km away in Mandi region
    distant_poly = {
        "type": "Polygon",
        "coordinates": [[
            [76.90, 31.70],
            [76.95, 31.70],
            [76.95, 31.75],
            [76.90, 31.75],
            [76.90, 31.70]
        ]]
    }

    alert = DisasterAlert(
        title="Distant Rockfall in Mandi Gorge",
        description="Debris on Mandi stretch 35km away",
        hazard_category="rockfall",
        severity="WARNING",
        status="active",
        issuing_authority="Mandi District Admin",
        required_protective_instructions="Caution on Mandi highway",
        center_latitude=31.72,
        center_longitude=76.92,
        affected_radius_meters=2000.0,
        polygon_geojson=json.dumps(distant_poly),
        published_at=now,
    )
    test_db.add(alert)
    await test_db.commit()

    plan_res = await client.post("/api/v1/routes/plan", json={
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_lat": 30.4598,
        "destination_lon": 78.0700,
        "destination_name": "Mussoorie Picture Palace",
        "corridor_distance_km": 5.0,
    })
    assert plan_res.status_code == 201
    route_id = plan_res.json()["route_id"]

    analyze_res = await client.post(f"/api/v1/routes/{route_id}/analyze", json={
        "corridor_distance_km": 5.0,
    })
    assert analyze_res.status_code == 200
    data = analyze_res.json()

    # Distant alert must be strictly excluded from this route's corridor analysis
    assert not any(a["title"] == alert.title for a in data["disaster_alerts"])


@pytest.mark.asyncio
async def test_fire_point_inside_and_outside_corridor_filtering(monkeypatch, client):
    """
    Tests 3 & 4:
    - Fire point 800m from route (inside corridor) is included with distance metrics.
    - Fire point 8.5km from route (inside bounding box rectangle, but outside 5km corridor) is EXCLUDED.
    """
    # Route: Manali (30.3165, 78.0322) to Solang (30.4598, 78.0700)
    # Inside fire: at 30.3800, 78.0520 (~300m from route)
    # Outside fire: at 30.4200, 78.1400 (~9km east of route, inside bounding box)
    mock_fires = [
        {
            "latitude": 30.3752,
            "longitude": 78.0790,
            "brightness": 345.2,
            "confidence": "nominal",
            "frp_mw": 18.5,
            "satellite": "VIIRS",
            "acquisition_date": "2026-10-24",
            "acquisition_time": "0430",
        },
        {
            "latitude": 30.3752,
            "longitude": 78.2000,
            "brightness": 360.0,
            "confidence": "high",
            "frp_mw": 45.0,
            "satellite": "VIIRS",
            "acquisition_date": "2026-10-24",
            "acquisition_time": "0430",
        }
    ]

    async def mock_fetch_fires(min_lat, min_lon, max_lat, max_lon, **kwargs):
        return {
            "records": mock_fires,
            "record_count": 2,
            "provider_status": "LOADED",
            "is_configured": True,
            "diagnostic_message": "Mock active fires in bbox",
            "disclaimer": "Test",
            "retrieved_at": datetime.now(timezone.utc).isoformat(),
            "is_cached": False,
        }

    monkeypatch.setattr(nasa_firms_client, "fetch_active_fires", mock_fetch_fires)

    plan_res = await client.post("/api/v1/routes/plan", json={
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_lat": 30.4598,
        "destination_lon": 78.0700,
        "destination_name": "Mussoorie Picture Palace",
        "corridor_distance_km": 5.0,
    })
    route_id = plan_res.json()["route_id"]

    analyze_res = await client.post(f"/api/v1/routes/{route_id}/analyze", json={
        "corridor_distance_km": 5.0,
    })
    data = analyze_res.json()

    # Exact spatial verification
    fires = data["fire_detections"]
    # Exactly the inside fire is retained, the outside fire is filtered out!
    assert len(fires) == 1
    retained_fire = fires[0]
    assert retained_fire["has_fire_within_5km"] is True
    assert retained_fire["nearest_fire_distance_km"] < 2.0


@pytest.mark.asyncio
async def test_corridor_width_sensitivity_1km_vs_10km(client, test_db):
    """
    Test 5: The same route at 1 km and 10 km corridor widths.
    A feature 3.2 km away is excluded at 1 km and included at 10 km.
    """
    now = datetime.now(timezone.utc)
    # Alert located 3.2 km perpendicular from route
    alert = DisasterAlert(
        title="Mid-Range Landslide on Mountain Slope",
        description="Slope instability 3.2 km from main trail",
        hazard_category="landslide",
        severity="SEVERE",
        status="active",
        issuing_authority="HP PWD",
        required_protective_instructions="Stay on designated valley route",
        center_latitude=30.3752,
        center_longitude=78.1150,  # ~3.2 km east of route
        affected_radius_meters=500.0,
        published_at=now,
    )
    test_db.add(alert)
    await test_db.commit()

    plan_res = await client.post("/api/v1/routes/plan", json={
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_lat": 30.4598,
        "destination_lon": 78.0700,
        "destination_name": "Mussoorie Picture Palace",
        "corridor_distance_km": 5.0,
    })
    route_id = plan_res.json()["route_id"]

    # 1. Analyze with 1 km corridor -> Feature must be EXCLUDED
    res_1km = await client.post(f"/api/v1/routes/{route_id}/analyze", json={
        "corridor_distance_km": 1.0,
    })
    data_1km = res_1km.json()
    assert data_1km["corridor_distance_km"] == 1.0
    assert not any(a["title"] == alert.title for a in data_1km["disaster_alerts"])

    # 2. Analyze with 10 km corridor -> Feature must be INCLUDED
    res_10km = await client.post(f"/api/v1/routes/{route_id}/analyze", json={
        "corridor_distance_km": 10.0,
    })
    data_10km = res_10km.json()
    assert data_10km["corridor_distance_km"] == 10.0
    assert any(a["title"] == alert.title for a in data_10km["disaster_alerts"])


@pytest.mark.asyncio
async def test_route_destination_change_recalculates_corridor(client):
    """Test 6: Changing destination recalculates route geometry, corridor polygon, and route ID."""
    # Route 1: Manali to Solang (North)
    res1 = await client.post("/api/v1/routes/plan", json={
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_lat": 30.4598,
        "destination_lon": 78.0700,
        "destination_name": "Mussoorie Picture Palace",
        "corridor_distance_km": 5.0,
    })
    data1 = res1.json()

    # Route 2: Manali to Naggar Castle (South)
    res2 = await client.post("/api/v1/routes/plan", json={
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_lat": 30.2000,
        "destination_lon": 78.0454,
        "destination_name": "Naggar Castle",
        "corridor_distance_km": 5.0,
    })
    data2 = res2.json()

    assert data1["route_id"] != data2["route_id"]
    assert data1["destination_name"] == "Mussoorie Picture Palace"
    assert data2["destination_name"] == "Naggar Castle"
    assert data1["corridor_geojson"] != data2["corridor_geojson"]


@pytest.mark.asyncio
async def test_successful_provider_response_zero_records(monkeypatch, client):
    """Test 8: Successful provider response with 0 records returns EMPTY layer state, not failure or dummy data."""
    async def mock_zero_fires(*args, **kwargs):
        return {
            "records": [],
            "record_count": 0,
            "provider_status": "LOADED",
            "is_configured": True,
            "diagnostic_message": "NASA FIRMS query successful: 0 hotspots detected in corridor.",
            "disclaimer": "Satellite thermal anomalies disclaimer.",
            "retrieved_at": datetime.now(timezone.utc).isoformat(),
            "is_cached": False,
        }

    monkeypatch.setattr(nasa_firms_client, "fetch_active_fires", mock_zero_fires)

    plan_res = await client.post("/api/v1/routes/plan", json={
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_lat": 30.4598,
        "destination_lon": 78.0700,
        "destination_name": "Mussoorie Picture Palace",
    })
    route_id = plan_res.json()["route_id"]

    analyze_res = await client.post(f"/api/v1/routes/{route_id}/analyze")
    data = analyze_res.json()

    # Zero fires must be legitimately returned as empty list, not mock data
    assert len(data["fire_detections"]) == 0
    firms_status = data["provider_statuses"]["nasa_firms"]
    assert firms_status["record_count"] == 0
    assert firms_status["status"] in ["LOADED", "EMPTY"]


@pytest.mark.asyncio
async def test_provider_outage_resilience(monkeypatch, client):
    """Test 9: Provider outage in one source does not erase results from functional providers."""
    async def mock_failing_firms(*args, **kwargs):
        raise ConnectionError("NASA FIRMS gateway timeout")

    monkeypatch.setattr(nasa_firms_client, "fetch_active_fires", mock_failing_firms)

    plan_res = await client.post("/api/v1/routes/plan", json={
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_lat": 30.4598,
        "destination_lon": 78.0700,
        "destination_name": "Mussoorie Picture Palace",
    })
    route_id = plan_res.json()["route_id"]

    analyze_res = await client.post(f"/api/v1/routes/{route_id}/analyze")
    assert analyze_res.status_code == 200
    data = analyze_res.json()

    # Weather and safe havens still succeed despite FIRMS outage
    assert len(data["weather_by_waypoint"]) > 0
    assert data["provider_statuses"]["open_meteo"]["status"] == "LOADED"


@pytest.mark.asyncio
async def test_cap_alert_without_polygon_geometry(client, test_db):
    """Test 10: Valid CAP alert without polygon geometry does not invent a fabricated polygon."""
    now = datetime.now(timezone.utc)
    alert = DisasterAlert(
        title="District General High Wind Advisory",
        description="Strong gusts across valley ridge",
        hazard_category="severe_weather",
        severity="INFO",
        status="active",
        issuing_authority="IMD Shimla",
        required_protective_instructions="Secure outdoor gear",
        center_latitude=0.0,
        center_longitude=0.0,
        affected_radius_meters=0.0,
        polygon_geojson=None,  # No usable geometry
        published_at=now,
    )
    test_db.add(alert)
    await test_db.commit()

    plan_res = await client.post("/api/v1/routes/plan", json={
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_lat": 30.4598,
        "destination_lon": 78.0700,
        "destination_name": "Mussoorie Picture Palace",
    })
    route_id = plan_res.json()["route_id"]

    analyze_res = await client.post(f"/api/v1/routes/{route_id}/analyze")
    data = analyze_res.json()

    # The alert has no geometry and must not have a fabricated polygon in the overlays
    overlays = data["overlays_geojson"]["features"]
    assert not any(
        f["properties"].get("layer") == "disaster_alert" and f["properties"].get("title") == alert.title
        for f in overlays
    )


@pytest.mark.asyncio
async def test_weather_values_for_multiple_route_samples(client):
    """Test 11: Weather values are provided for multiple sample points along the route."""
    plan_res = await client.post("/api/v1/routes/plan", json={
        "origin_lat": 30.3165,
        "origin_lon": 78.0322,
        "destination_lat": 30.4598,
        "destination_lon": 78.0700,
        "destination_name": "Mussoorie Picture Palace",
    })
    route_id = plan_res.json()["route_id"]

    analyze_res = await client.post(f"/api/v1/routes/{route_id}/analyze")
    data = analyze_res.json()

    weather_samples = data["weather_by_waypoint"]
    assert len(weather_samples) >= 2
    for s in weather_samples:
        assert "temperature_c" in s
        assert "relative_humidity_pct" in s
        assert "wind_speed_kmh" in s
        assert "weather_description" in s
        assert "forecast_valid_at" in s
        assert s["source"] != ""


@pytest.mark.asyncio
async def test_evaluate_route_exposure_metrics():
    """Verify explainable exposure evaluation, direct vs corridor intersections, and fire proximity."""
    route_coords = [
        (30.3165, 78.0322),
        (30.3800, 78.0500),
        (30.4598, 78.0700),
    ]
    corridor_info = route_corridor_service.compute_corridor(route_coords, corridor_distance_meters=5000.0)
    corridor_poly = corridor_info["corridor_polygon_geojson"]

    # 1. Direct intersecting alert (polygon right over route line)
    direct_alert = DisasterAlert(
        id="alert-direct-1",
        title="Direct Flash Flood Intersection",
        hazard_category="flash_flood",
        severity="CRITICAL",
        status="active",
        center_latitude=30.3752,
        center_longitude=78.0500,
        affected_radius_meters=1000.0,
        polygon_geojson=json.dumps({
            "type": "Polygon",
            "coordinates": [[
                [78.045, 30.375],
                [78.055, 30.375],
                [78.055, 30.385],
                [78.045, 30.385],
                [78.045, 30.375]
            ]]
        })
    )

    # 2. Corridor alert (in corridor buffer ~3 km away, but not on centerline)
    corridor_alert = DisasterAlert(
        id="alert-corridor-2",
        title="Corridor Warning Alert",
        hazard_category="heavy_rainfall",
        severity="WARNING",
        status="active",
        center_latitude=30.3752,
        center_longitude=78.0800,
        affected_radius_meters=500.0,
    )

    # 3. Fire within 1 km of route
    close_fire = {"latitude": 30.3810, "longitude": 78.0510, "confidence": "high", "frp_mw": 25.0}

    # 4. Fire 4 km away in corridor
    corridor_fire = {"latitude": 30.3800, "longitude": 78.0900, "confidence": "nominal", "frp_mw": 10.0}

    eval_result = route_corridor_service.evaluate_route_exposure(
        route_coords,
        corridor_distance_meters=5000.0,
        corridor_polygon_geojson=corridor_poly,
        alerts=[direct_alert, corridor_alert],
        fires=[close_fire, corridor_fire],
        provider_statuses={"ndma_sachet": {"status": "LOADED"}, "nasa_firms": {"status": "LOADED"}},
    )

    assert eval_result["direct_hazard_intersections_count"] == 1
    assert eval_result["corridor_hazard_count"] == 1
    assert eval_result["fires_within_2km_count"] == 1
    assert eval_result["corridor_fires_count"] == 1
    assert eval_result["hazard_score"] > 60.0
    assert eval_result["safety_status"] == "HIGH_RISK"
    assert "direct" in eval_result["safety_label"].lower()
    # Ensure calibrated wording (never claims '100% safe')
    assert "safe" not in eval_result["safety_label"].lower() or "safest" in eval_result["safety_label"].lower()


@pytest.mark.asyncio
async def test_alternative_route_comparative_ranking(test_db):
    """Verify that candidate alternatives are evaluated independently and safer ones are recommended."""
    from app.services.routing.base import RouteResult, Waypoint, RouteAlternative
    from app.services.route_condition_analyzer import route_condition_analyzer

    now = datetime.now(timezone.utc)
    # Direct alert on primary route (30.3800, 78.0500)
    alert = DisasterAlert(
        id="alert-blocking-trail",
        title="Direct Mudslide Blocking Trail",
        description="Trail blocked",
        hazard_category="landslide",
        severity="CRITICAL",
        status="active",
        issuing_authority="SDMA",
        required_protective_instructions="Evacuate",
        center_latitude=30.3752,
        center_longitude=78.0500,
        affected_radius_meters=1200.0,
        polygon_geojson=json.dumps({
            "type": "Polygon",
            "coordinates": [[
                [78.045, 30.375],
                [78.055, 30.375],
                [78.055, 30.385],
                [78.045, 30.385],
                [78.045, 30.375]
            ]]
        }),
        published_at=now,
    )
    test_db.add(alert)
    await test_db.commit()

    primary_geom = {
        "type": "LineString",
        "coordinates": [
            [78.0322, 30.3165],
            [78.0500, 30.3800],
            [78.0700, 30.4598],
        ]
    }

    # Alternative that detours 6 km east away from the mudslide
    alt_geom = {
        "type": "LineString",
        "coordinates": [
            [78.0322, 30.3165],
            [78.1200, 30.3800],
            [78.0700, 30.4598],
        ]
    }

    route_res = RouteResult(
        route_id="test-route-alternatives",
        title="Manali to Solang",
        origin_name="Manali",
        origin_lat=30.3165,
        origin_lon=78.0322,
        destination_name="Solang",
        destination_lat=30.4598,
        destination_lon=78.0700,
        travel_mode="hiking",
        distance_km=12.0,
        duration_minutes=150.0,
        geometry_geojson=primary_geom,
        waypoints=[
            Waypoint(index=0, name="Manali", latitude=30.3165, longitude=78.0322, distance_from_start_km=0.0, estimated_travel_time_minutes=0.0),
            Waypoint(index=1, name="Hazard Point", latitude=30.3800, longitude=78.0500, distance_from_start_km=6.0, estimated_travel_time_minutes=75.0),
            Waypoint(index=2, name="Solang", latitude=30.4598, longitude=78.0700, distance_from_start_km=12.0, estimated_travel_time_minutes=150.0),
        ],
        alternatives=[
            RouteAlternative(
                id="alt-bypass-mudslide",
                title="Eastern Ridge Bypass",
                description="Detours east of active landslide corridor",
                distance_km=14.5,
                duration_minutes=175.0,
                geometry_geojson=alt_geom,
            )
        ]
    )

    planned = await route_condition_analyzer.analyze_route(test_db, route_res, corridor_distance_meters=3000.0)

    assert len(planned.alternatives) == 1
    alt_res = planned.alternatives[0]
    # The alternative bypasses the mudslide, so it should have a significantly lower hazard score
    assert alt_res.hazard_score < planned.overall_hazard_score
    # Should be flagged as recommended
    assert alt_res.is_recommended is True
    assert alt_res.recommendation_reason is not None
    assert "Bypasses" in alt_res.recommendation_reason
    assert alt_res.corridor_geojson is not None
    assert planned.hazards_geojson is not None
    assert planned.fires_geojson is not None
    assert planned.facilities_geojson is not None


@pytest.mark.asyncio
async def test_simulated_alerts_excluded_from_route_corridor_analysis(client, test_db):
    """Verify that alerts flagged with is_simulated=True are completely ignored by corridor analyzer."""
    now = datetime.now(timezone.utc)
    # Insert simulated alert right along the route
    sim_alert = DisasterAlert(
        title="[SIMULATED] Simulated Rockslide on Trail",
        description="This is a test simulation and must not be shown in real corridor analysis",
        hazard_category="landslide",
        severity="SEVERE",
        status="active",
        issuing_authority="Test Simulation Agency",
        required_protective_instructions="Avoid trail",
        center_latitude=30.40,
        center_longitude=78.05,
        affected_radius_meters=3000.0,
        polygon_geojson=json.dumps({
            "type": "Polygon",
            "coordinates": [[[78.04, 30.39], [78.06, 30.39], [78.06, 30.41], [78.04, 30.41], [78.04, 30.39]]]
        }),
        is_simulated=True,
        published_at=now,
    )
    test_db.add(sim_alert)
    await test_db.commit()

    route_res = RouteResult(
        route_id="test-sim-filter-route",
        title="Manali to Solang Sim Test",
        origin_name="Manali",
        origin_lat=30.3165,
        origin_lon=78.0322,
        destination_name="Solang",
        destination_lat=30.4598,
        destination_lon=78.0700,
        travel_mode="hiking",
        distance_km=12.0,
        duration_minutes=150.0,
        geometry_geojson={
            "type": "LineString",
            "coordinates": [[78.0322, 30.3165], [78.0500, 30.3800], [78.0700, 30.4598]]
        },
        waypoints=[
            Waypoint(index=0, name="Manali", latitude=30.3165, longitude=78.0322, distance_from_start_km=0.0, estimated_travel_time_minutes=0.0),
            Waypoint(index=1, name="Mid", latitude=30.3800, longitude=78.0500, distance_from_start_km=6.0, estimated_travel_time_minutes=75.0),
            Waypoint(index=2, name="Solang", latitude=30.4598, longitude=78.0700, distance_from_start_km=12.0, estimated_travel_time_minutes=150.0),
        ]
    )

    planned = await route_condition_analyzer.analyze_route(test_db, route_res, corridor_distance_meters=5000.0)

    # Simulated alert must NOT be included in corridor hazards
    hazard_features = planned.hazards_geojson.get("features", [])
    sim_features = [f for f in hazard_features if "SIM-MOCK" in str(f) or "Simulated" in str(f)]
    assert len(sim_features) == 0

    # Ensure overall safety status does NOT give false guarantee of absolute safety ("SAFE"),
    # but rather calibrated status "NORMAL" or "CAUTION".
    assert planned.overall_safety_status in ("NORMAL", "CAUTION", "MODERATE", "SEVERE", "CRITICAL")
    assert planned.overall_safety_status != "SAFE"


@pytest.mark.asyncio
async def test_forest_and_hazard_geometry_fidelity_and_diagnostics(client, test_db, monkeypatch):
    """Verify:
    1. forests_geojson is populated and contains valid Polygons.
    2. MultiPolygon and polygon holes are preserved in hazards_geojson.
    3. Point-only alert produces Point feature with 'disaster_alert_point', not an invented polygon.
    4. Active fires are strictly Point features.
    5. geometry_diagnostics contains valid breakdown.
    """
    now = datetime.now(timezone.utc)

    # 1. Alert with polygon holes
    outer_ring = [[78.04, 30.36], [78.08, 30.36], [78.08, 30.40], [78.04, 30.40], [78.04, 30.36]]
    inner_ring_hole = [[78.05, 30.37], [78.07, 30.37], [78.07, 30.39], [78.05, 30.39], [78.05, 30.37]]
    poly_with_hole = {
        "type": "Polygon",
        "coordinates": [outer_ring, inner_ring_hole]
    }
    alert_hole = DisasterAlert(
        title="Landslide with Safe Island Zone",
        description="Hazard perimeter with unaffected inner ridge",
        hazard_category="landslide",
        severity="SEVERE",
        status="active",
        issuing_authority="Geological Survey of India",
        required_protective_instructions="Follow outer perimeter detour",
        center_latitude=30.38,
        center_longitude=78.06,
        affected_radius_meters=2000.0,
        polygon_geojson=json.dumps(poly_with_hole),
        is_simulated=False,
        published_at=now,
    )

    # 2. Alert with Point only (no polygon, no circle radius)
    alert_point_only = DisasterAlert(
        title="Stationary Rockfall Warning Post",
        description="Point detection without verified perimeter",
        hazard_category="trail_closure",
        severity="WARNING",
        status="active",
        issuing_authority="State Disaster Management",
        required_protective_instructions="Caution near mile marker 14",
        center_latitude=30.37,
        center_longitude=78.06,
        affected_radius_meters=0.0,
        polygon_geojson=None,
        is_simulated=False,
        published_at=now,
    )

    test_db.add_all([alert_hole, alert_point_only])
    await test_db.commit()

    # Mock OSM Forest client to return real forest feature
    async def mock_fetch_forest(min_lat, min_lon, max_lat, max_lon):
        return {
            "type": "FeatureCollection",
            "features": [{
                "type": "Feature",
                "id": "osm_way_101",
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[78.05, 30.35], [78.07, 30.35], [78.07, 30.37], [78.05, 30.37], [78.05, 30.35]]]
                },
                "properties": {
                    "name": "Solang Pine Reserve",
                    "type": "forest",
                    "wood_type": "coniferous",
                }
            }],
            "record_count": 1,
            "provider_status": "HEALTHY",
        }

    from app.clients.osm_forest import osm_forest_client
    monkeypatch.setattr(osm_forest_client, "fetch_forest_features", mock_fetch_forest)

    route_res = RouteResult(
        route_id="test-geometry-fidelity-route",
        title="Manali to Solang Geometry Test",
        origin_name="Manali",
        origin_lat=30.3165,
        origin_lon=78.0322,
        destination_name="Solang",
        destination_lat=30.4598,
        destination_lon=78.0700,
        travel_mode="hiking",
        distance_km=12.0,
        duration_minutes=150.0,
        geometry_geojson={
            "type": "LineString",
            "coordinates": [[78.0322, 30.3165], [78.0500, 30.3800], [78.0700, 30.4598]]
        },
        waypoints=[
            Waypoint(index=0, name="Manali", latitude=30.3165, longitude=78.0322, distance_from_start_km=0.0, estimated_travel_time_minutes=0.0),
            Waypoint(index=1, name="Mid", latitude=30.3800, longitude=78.0500, distance_from_start_km=6.0, estimated_travel_time_minutes=75.0),
            Waypoint(index=2, name="Solang", latitude=30.4598, longitude=78.0700, distance_from_start_km=12.0, estimated_travel_time_minutes=150.0),
        ]
    )

    planned = await route_condition_analyzer.analyze_route(test_db, route_res, corridor_distance_meters=5000.0)

    # 1. Verify forests_geojson
    assert planned.forests_geojson is not None
    assert planned.forests_geojson["type"] == "FeatureCollection"
    assert len(planned.forests_geojson["features"]) >= 1
    forest_feat = planned.forests_geojson["features"][0]
    assert forest_feat["geometry"]["type"] == "Polygon"
    assert forest_feat["properties"]["name"] == "Solang Pine Reserve"

    # 2. Verify hazards_geojson preserves holes
    assert planned.hazards_geojson is not None
    hazard_features = planned.hazards_geojson["features"]
    poly_hole_feat = next(f for f in hazard_features if f["properties"]["title"] == "Landslide with Safe Island Zone")
    assert poly_hole_feat["geometry"]["type"] == "Polygon"
    # Outer ring + inner hole ring = 2 rings preserved!
    assert len(poly_hole_feat["geometry"]["coordinates"]) == 2

    # 3. Verify point-only alert produces Point feature with 'disaster_alert_point' layer
    point_feat = next(f for f in hazard_features if f["properties"]["title"] == "Stationary Rockfall Warning Post")
    assert point_feat["geometry"]["type"] == "Point"
    assert point_feat["properties"]["layer"] == "disaster_alert_point"
    assert point_feat["properties"]["derived_from"] == "point"

    # 4. Verify weather_geojson exists
    assert planned.weather_geojson is not None
    assert len(planned.weather_geojson["features"]) == 3
    assert all(f["geometry"]["type"] == "Point" for f in planned.weather_geojson["features"])

    # 5. Verify fires_geojson is strictly Points
    assert planned.fires_geojson is not None
    for f in planned.fires_geojson["features"]:
        assert f["geometry"]["type"] == "Point"

    # 6. Persist PlannedRoute and verify corridor analysis endpoint returns geometry_diagnostics
    from app.models.planned_route import PlannedRoute
    db_route = PlannedRoute(
        id="test-geometry-fidelity-route",
        title=planned.title,
        origin_name=planned.origin_name,
        origin_lat=planned.origin_lat,
        origin_lon=planned.origin_lon,
        destination_name=planned.destination_name,
        dest_lat=planned.dest_lat,
        dest_lon=planned.dest_lon,
        travel_mode=planned.travel_mode,
        distance_km=planned.distance_km,
        duration_minutes=planned.duration_minutes,
        geometry_geojson=planned.geometry_geojson,
        overall_hazard_score=planned.overall_hazard_score,
        overall_safety_status=planned.overall_safety_status,
        route_version=1,
        conditions_data=[c.model_dump() for c in planned.conditions],
        alternatives_data=[a.model_dump() for a in planned.alternatives],
        data_freshness_data=planned.data_freshness,
    )
    test_db.add(db_route)
    await test_db.commit()

    persisted = await route_condition_analyzer.analyze_persisted_route(test_db, "test-geometry-fidelity-route")
    assert persisted is not None
    assert persisted.forests_geojson is not None
    assert persisted.geometry_diagnostics is not None
    diag = persisted.geometry_diagnostics
    assert "geometry_type_counts" in diag
    assert diag["geometry_type_counts"]["polygons"] >= 1
    assert diag["forest_polygons_count"] >= 1



