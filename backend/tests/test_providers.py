"""Unit tests for external data providers and the centralized Provider Registry."""
import pytest
from app.config import settings
from app.clients.nasa_firms import nasa_firms_client
from app.clients.osm_forest import osm_forest_client
from app.clients.opencellid import opencellid_client
from app.services.provider_registry import provider_registry


SAMPLE_VIIRS_CSV = """latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
32.2450,77.1950,335.4,0.4,0.4,2026-10-23,0645,N,VIIRS,nominal,2.0NRT,285.2,12.8,D
32.2610,77.2100,342.1,0.5,0.4,2026-10-23,0645,N,VIIRS,high,2.0NRT,290.0,18.4,D
"""

SAMPLE_EMPTY_CSV = """latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
"""

SAMPLE_MODIS_CSV = """latitude,longitude,brightness,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_t31,frp,daynight
32.1800,77.1500,318.5,1.2,1.1,2026-10-23,0530,T,MODIS,85,6.1NRT,291.4,9.2,D
"""


def test_parse_firms_viirs_csv():
    records = nasa_firms_client.parse_firms_csv(SAMPLE_VIIRS_CSV, "VIIRS_SNPP_NRT")
    assert len(records) == 2

    first = records[0]
    assert first["latitude"] == 32.2450
    assert first["longitude"] == 77.1950
    assert first["brightness"] == 335.4
    assert first["satellite"] == "N"
    assert first["confidence"] == "nominal"
    assert first["frp_mw"] == 12.8
    assert first["daynight"] == "D"
    assert first["is_simulated"] is False


def test_parse_firms_empty_zero_detections():
    records = nasa_firms_client.parse_firms_csv(SAMPLE_EMPTY_CSV, "VIIRS_SNPP_NRT")
    assert len(records) == 0  # Zero detections must be an empty list, NOT an error!


def test_parse_firms_modis_csv():
    records = nasa_firms_client.parse_firms_csv(SAMPLE_MODIS_CSV, "MODIS_NRT")
    assert len(records) == 1
    assert records[0]["latitude"] == 32.1800
    assert records[0]["brightness"] == 318.5
    assert records[0]["confidence"] == "85"


@pytest.mark.asyncio
async def test_unconfigured_firms_graceful_handling(monkeypatch):
    # When MAP_KEY is unconfigured, it must report UNCONFIGURED and not raise an exception
    monkeypatch.setattr(settings, "NASA_FIRMS_MAP_KEY", "")
    result = await nasa_firms_client.fetch_active_fires(32.0, 77.0, 32.5, 77.5)
    assert result["record_count"] == 0
    assert result["provider_status"] == "UNCONFIGURED"
    assert "NASA_FIRMS_MAP_KEY" in result["diagnostic_message"]
    assert result["is_configured"] is False


@pytest.mark.asyncio
async def test_unconfigured_opencellid_graceful_handling(monkeypatch):
    monkeypatch.setattr(settings, "OPENCELLID_API_KEY", "")
    result = await opencellid_client.fetch_nearby_cells(32.2396, 77.1887)
    assert result["record_count"] == 0
    assert result["provider_status"] == "UNCONFIGURED"
    assert "OPENCELLID_API_KEY" in result["diagnostic_message"]


@pytest.mark.asyncio
async def test_configured_opencellid_live_or_parsing():
    # If configured with a valid key, verify structure
    if opencellid_client.is_configured():
        result = await opencellid_client.fetch_nearby_cells(30.3165, 78.0322, radius_m=1000)
        assert result["is_configured"] is True
        assert result["provider_status"] in ("HEALTHY", "ZERO_RECORDS")
        assert "cells" in result
        assert isinstance(result["cells"], list)


def test_osm_forest_element_to_geojson():
    raw_elements = [
        {
            "type": "way",
            "id": 12345,
            "tags": {"name": "Rohtang Pine Forest", "landuse": "forest", "wood": "conifer"},
            "geometry": [
                {"lat": 32.24, "lon": 77.19},
                {"lat": 32.25, "lon": 77.20},
                {"lat": 32.24, "lon": 77.21},
                {"lat": 32.24, "lon": 77.19},
            ],
        }
    ]
    features = osm_forest_client._elements_to_geojson_features(raw_elements)
    assert len(features) == 1
    feat = features[0]
    assert feat["type"] == "Feature"
    assert feat["geometry"]["type"] == "Polygon"
    assert feat["properties"]["name"] == "Rohtang Pine Forest"
    assert feat["properties"]["landuse"] == "forest"


@pytest.mark.asyncio
async def test_provider_registry_statuses():
    statuses = await provider_registry.get_all_provider_statuses()
    provider_ids = [p["provider_id"] for p in statuses]

    assert "open_meteo" in provider_ids
    assert "osm_overpass" in provider_ids
    assert "nasa_firms" in provider_ids
    assert "fsi_forest_fire" in provider_ids
    assert "opencellid" in provider_ids
    assert "backend_database" in provider_ids

    # Verify no secret credentials leaked in diagnostics
    for p in statuses:
        assert "key=" not in p["diagnostic_message"].lower()
        assert "secret" not in p["diagnostic_message"].lower()
        assert "token" not in p["diagnostic_message"].lower()

