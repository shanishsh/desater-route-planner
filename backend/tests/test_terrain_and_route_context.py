"""
Unit and integration tests for Terrain, Slope, Waterways, Historical Landslides,
and Cellular Coverage Intelligence in Uttarakhand.
"""

import pytest
from app.services.spatial.waterway_service import waterway_service
from app.services.spatial.historical_landslide_service import historical_landslide_service
from app.services.spatial.cellular_coverage_service import cellular_coverage_service
from app.services.risk.terrain_exposure_calculator import terrain_exposure_calculator
from app.services.terrain.elevation_service import elevation_service
from app.clients.nasa_lhasa import nasa_lhasa_client


def test_historical_landslide_detection():
    # Gaurikund to Kedarnath trail coords
    kedarnath_coords = [
        [79.0268, 30.6534],  # Gaurikund
        [79.0500, 30.7000],  # Mid-trail
        [79.0669, 30.7346],  # Kedarnath Temple
    ]
    geojson, summary = historical_landslide_service.get_corridor_landslides(
        kedarnath_coords, corridor_radius_m=3000.0
    )
    assert summary["count"] >= 1
    assert "Kedarnath" in summary["nearest_landslide_name"]
    assert summary["nearest_distance_m"] is not None
    assert summary["nearest_distance_m"] < 100.0  # Kedarnath terminus is at the historic event
    assert len(geojson["features"]) >= 1


def test_waterway_proximity_detection():
    # Mandakini valley trail
    kedarnath_coords = [
        [79.0268, 30.6534],  # Gaurikund
        [79.0669, 30.7346],  # Kedarnath
    ]
    geojson, summary = waterway_service.get_corridor_waterways(
        kedarnath_coords, corridor_radius_m=3000.0
    )
    assert len(summary["rivers_detected"]) >= 1
    assert "Mandakini River" in summary["rivers_detected"]
    assert summary["nearest_river_name"] == "Mandakini River"
    assert summary["nearest_distance_m"] < 500.0
    assert summary["is_river_gorge_route"] is True
    assert len(geojson["features"]) >= 1


def test_cellular_coverage_classification():
    trail_coords = [
        [79.0268, 30.6534],
        [79.0400, 30.6800],
        [79.0669, 30.7346],
    ]
    mock_cells = [
        {
            "id": "cell_405_854_101",
            "latitude": 30.6535,
            "longitude": 79.0269,
            "operator": "Reliance Jio",
            "radio": "LTE",
            "range_m": 1500,
        }
    ]
    sites_geojson = cellular_coverage_service.build_cellular_sites_geojson(mock_cells)
    assert len(sites_geojson["features"]) == 1
    assert sites_geojson["features"][0]["properties"]["operator"] == "Reliance Jio"

    coverage_geojson, summary = cellular_coverage_service.build_cellular_coverage_geojson(
        trail_coords, mock_cells
    )
    assert len(coverage_geojson["features"]) >= 1
    assert summary["total_km"] > 0
    # Must distinguish probable coverage from unknown coverage
    assert "Reliance Jio" in summary["operators_detected"]
    assert "disclaimer" in summary


def test_terrain_exposure_calculator():
    mock_elevation_profile = [
        {"elevation_m": 2000.0, "slope_deg": 12.0},
        {"elevation_m": 2500.0, "slope_deg": 35.0},
        {"elevation_m": 3580.0, "slope_deg": 52.0},  # Precipitous high altitude
    ]
    mock_waterway = {
        "nearest_distance_m": 150.0,
        "nearest_river_name": "Mandakini River",
    }
    mock_landslide = {
        "count": 1,
        "high_risk_zones_intersecting": 1,
        "nearest_distance_m": 80.0,
        "nearest_landslide_name": "Kedarnath Moraine Dam Failure",
    }
    exposure = terrain_exposure_calculator.compute_exposure(
        mock_elevation_profile,
        mock_waterway,
        mock_landslide,
        max_elevation_m=3580.0,
    )
    assert exposure["score"] > 50.0  # High exposure due to steep slope, riverbed, and landslide
    assert exposure["rating"] in ("HIGH_EXPOSURE", "SEVERE_EXPOSURE")
    assert len(exposure["factors"]) == 4


def test_elevation_slope_geojson_brackets():
    profile_points = [
        {"latitude": 30.65, "longitude": 79.02, "elevation_m": 1980.0, "slope_degrees": 8.0},
        {"latitude": 30.68, "longitude": 79.04, "elevation_m": 2500.0, "slope_degrees": 22.0},
        {"latitude": 30.71, "longitude": 79.06, "elevation_m": 3100.0, "slope_degrees": 38.0},
        {"latitude": 30.73, "longitude": 79.07, "elevation_m": 3580.0, "slope_degrees": 49.0},
    ]
    slope_geojson = elevation_service.build_slope_geojson(profile_points)
    assert len(slope_geojson["features"]) == 3
    categories = [f["properties"]["slope_category"] for f in slope_geojson["features"]]
    assert "MODERATE" in categories
    assert "STEEP" in categories
    assert "PRECIPITOUS" in categories


@pytest.mark.asyncio
async def test_nasa_lhasa_non_blocking_status():
    res = await nasa_lhasa_client.fetch_landslide_nowcast(28.0, 77.0, 32.0, 81.0)
    assert "status" in res
    assert "available" in res
    assert isinstance(res["nowcasts"], list)

