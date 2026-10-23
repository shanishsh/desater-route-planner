"""Unit tests for TrailGuard Risk Engine, Tobler Elevation, and SAR Service."""
import pytest
import math
from datetime import datetime, timezone
from app.services.terrain.elevation_service import (
    tobler_hiking_speed_kmh,
    haversine_distance_m,
    elevation_service,
)
from app.services.risk.trailguard_risk_engine import trailguard_risk_engine
from app.api.v1.sar import create_uncertainty_circle_polygon


def test_tobler_hiking_speed():
    """Verify Tobler velocity behaves according to physical hiking biomechanics."""
    # Flat terrain (0.0 slope)
    speed_flat = tobler_hiking_speed_kmh(0.0)
    assert 4.0 <= speed_flat <= 5.5

    # Gentle downhill (-0.05 slope is optimal hiking efficiency)
    speed_gentle_downhill = tobler_hiking_speed_kmh(-0.05)
    assert speed_gentle_downhill >= speed_flat

    # Steep uphill (+0.35 slope = ~19.3 degrees)
    speed_steep_up = tobler_hiking_speed_kmh(0.35)
    assert speed_steep_up < speed_flat
    assert 1.0 <= speed_steep_up <= 2.5

    # Extreme vertical scramble (+0.80 slope)
    speed_extreme = tobler_hiking_speed_kmh(0.80)
    assert speed_extreme == 0.8  # Lower bound clamp


def test_weather_subscore_freezing_and_rain():
    """Verify freezing elevation and torrential rain escalate weather subscore."""
    # Mild clear weather
    w_mild = trailguard_risk_engine.compute_weather_subscore(
        precipitation_mm=0.0,
        precipitation_prob_pct=10.0,
        wind_speed_kmh=12.0,
        temperature_c=22.0,
        elevation_m=1200.0,
        warning_level="NORMAL",
    )
    assert w_mild["subscore"] <= 15.0

    # High elevation freezing snow gale
    w_freezing_gale = trailguard_risk_engine.compute_weather_subscore(
        precipitation_mm=12.0,
        precipitation_prob_pct=95.0,
        wind_speed_kmh=72.0,
        temperature_c=-1.0,
        elevation_m=3400.0,
        warning_level="SEVERE",
    )
    assert w_freezing_gale["subscore"] >= 80.0
    assert any("Freezing snow" in f for f in w_freezing_gale["factors"])
    assert any("Dangerous gale" in f for f in w_freezing_gale["factors"])


def test_slope_subscore_compounding():
    """Verify non-linear compounding multiplier when steep slope intersects heavy precipitation."""
    # Steep slope without rain
    s_dry = trailguard_risk_engine.compute_slope_subscore(
        slope_deg=30.0,
        precipitation_mm=0.0,
        elevation_m=2000.0,
    )
    # Steep slope WITH heavy rain (> 5mm)
    s_wet = trailguard_risk_engine.compute_slope_subscore(
        slope_deg=30.0,
        precipitation_mm=10.0,
        elevation_m=2000.0,
    )
    assert s_wet["subscore"] > s_dry["subscore"]
    assert any("Landslide & rockfall vulnerability" in f for f in s_wet["factors"])


def test_aggregate_route_risk_preserves_peaks():
    """Verify 0.65 * max + 0.35 * mean formula preserves extreme local danger."""
    # 9 safe segments (score = 10) and 1 deadly segment (score = 90)
    segments = [{"risk_score": 10.0, "risk_level": "SAFE", "contributing_factors": []}] * 9
    segments.append(
        {
            "risk_score": 90.0,
            "risk_level": "CRITICAL",
            "contributing_factors": ["Active rockfall chute"],
        }
    )

    agg = trailguard_risk_engine.aggregate_route_risk(segments)
    # 0.65 * 90 + 0.35 * 18 = 58.5 + 6.3 = 64.8
    assert agg["overall_score"] >= 60.0
    assert agg["overall_status"] == "HIGH_RISK"
    assert agg["critical_segments_count"] == 1


def test_contrastive_explanation_generation():
    """Verify human-readable contrastive explanation highlights alternatives when high risk."""
    primary_agg = {
        "overall_score": 68.5,
        "overall_status": "HIGH_RISK",
        "critical_segments_count": 2,
    }
    primary_segments = [
        {
            "contributing_factors": [
                "Torrential rain (18.0 mm/h)",
                "Landslide & rockfall vulnerability (steep terrain under heavy rain)",
            ]
        }
    ]
    alternatives = [
        {
            "title": "Low Valley Spur B",
            "hazard_score": 22.0,
            "safety_label": "Recommended: Bypasses hazard corridor",
        }
    ]

    explanation = trailguard_risk_engine.generate_contrastive_explanation(
        primary_agg, primary_segments, alternatives
    )
    assert "HIGH RISK DETECTED" in explanation
    assert "Torrential rain" in explanation
    assert "Low Valley Spur B" in explanation
    assert "reduces corridor hazard score" in explanation


def test_sar_uncertainty_circle_polygon():
    """Verify Search and Rescue uncertainty perimeter generates valid GeoJSON polygon."""
    poly = create_uncertainty_circle_polygon(
        center_lat=32.2415, center_lon=77.1724, radius_meters=1500.0, num_points=36
    )
    assert poly["type"] == "Polygon"
    coords = poly["coordinates"][0]
    assert len(coords) == 37  # 36 points + 1 closing coordinate
    # Verify closed polygon (first == last)
    assert coords[0] == coords[-1]
    # Coordinates should be [lon, lat]
    assert 76.5 <= coords[0][0] <= 78.0
    assert 31.5 <= coords[0][1] <= 33.0

