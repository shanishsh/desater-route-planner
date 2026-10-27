"""Automated tests for IMD multi-endpoint client and USGS Himalayan earthquake client."""
import pytest
from unittest.mock import AsyncMock, patch, MagicMock
from app.clients.imd import IMDClient, UTTARAKHAND_DISTRICTS
from app.clients.usgs_earthquake import USGSEarthquakeClient


@pytest.mark.asyncio
async def test_imd_unconfigured_behavior():
    client = IMDClient()
    with patch("app.clients.imd.settings.IMD_API_KEY", ""):
        assert client.is_configured() is False
        status = client.get_service_status()
        assert status["status"] == "UNCONFIGURED"
        assert "IP whitelisting" in status["diagnostic_message"]
        warnings = await client.fetch_district_warnings()
        assert warnings == []
        nowcasts = await client.fetch_district_nowcast("Dehradun")
        assert nowcasts == []


@pytest.mark.asyncio
async def test_imd_multi_endpoint_calls():
    client = IMDClient()
    with patch("app.clients.imd.settings.IMD_API_KEY", "test_key"), \
         patch("app.clients.imd.settings.IMD_PASSWORD", "test_pass"):

        assert client.is_configured() is True

        # Mock token fetch
        client.fetch_token = AsyncMock(return_value="mock_jwt_token")

        # Mock _make_authenticated_request
        mock_warning_data = [{"district": "DEHRADUN", "colour": "ORANGE", "warning": "Heavy Rainfall"}]
        mock_nowcast_data = [{"district": "UTTARKASHI", "hazard": "Thunderstorm with Squall"}]
        mock_qpf_data = [{"basin": "Ganga", "qpf_mm": "25-50"}]

        async def mock_request(endpoint, params=None):
            if "districtwarning" in endpoint:
                return mock_warning_data, 200, None
            elif "districtnowcast" in endpoint:
                return mock_nowcast_data, 200, None
            elif "basinqpf" in endpoint:
                return mock_qpf_data, 200, None
            return [], 200, None

        client._make_authenticated_request = AsyncMock(side_effect=mock_request)

        warnings = await client.fetch_district_warnings("UTTARAKHAND")
        assert len(warnings) == 1
        assert warnings[0]["district"] == "DEHRADUN"

        nowcasts = await client.fetch_district_nowcast("UTTARKASHI")
        assert len(nowcasts) == 1
        assert nowcasts[0]["hazard"] == "Thunderstorm with Squall"

        qpf = await client.fetch_basin_qpf("Ganga")
        assert len(qpf) == 1
        assert qpf[0]["basin"] == "Ganga"


@pytest.mark.asyncio
async def test_usgs_earthquake_filtering():
    client = USGSEarthquakeClient()

    mock_geojson = {
        "features": [
            {
                "id": "nc75001",
                "geometry": {
                    "type": "Point",
                    "coordinates": [78.5, 30.2, 12.5],  # Inside Uttarakhand (78.5E, 30.2N)
                },
                "properties": {
                    "title": "M 3.8 - 14 km NW of Tehri, India",
                    "mag": 3.8,
                    "place": "14 km NW of Tehri, India",
                    "time": 1729850000000,
                    "alert": "green",
                    "url": "https://earthquake.usgs.gov/earthquakes/eventpage/nc75001",
                },
            },
            {
                "id": "us99999",
                "geometry": {
                    "type": "Point",
                    "coordinates": [-122.4, 37.7, 5.0],  # Outside (California)
                },
                "properties": {
                    "title": "M 2.1 - San Francisco, CA",
                    "mag": 2.1,
                },
            },
        ]
    }

    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = mock_geojson

    with patch("httpx.AsyncClient.get", AsyncMock(return_value=mock_response)):
        events = await client.fetch_regional_earthquakes()
        assert len(events) == 1
        assert events[0]["id"] == "nc75001"
        assert events[0]["magnitude"] == 3.8
        assert events[0]["latitude"] == 30.2
        assert events[0]["longitude"] == 78.5
        assert events[0]["depth_km"] == 12.5

    status = client.get_service_status()
    assert status["status"] == "HEALTHY"
    assert status["record_count"] == 1
    assert "USGS" in status["attribution"]
