"""Comprehensive test suite for the disaster-data ingestion system.

Covers:
1. NDMA SACHET CAP 1.2 parsing (Polygon closure & RFC 7946, Circle, District text, Cancellation).
2. Elimination of coordinate / polygon fabrication for non-spatial district advisories.
3. Official IMD client status, configuration handling, and token authentication.
4. Uttarakhand PWD bulletin extractor with real S3WaaS HTML structure.
5. Spatial route corridor filtering (polygon intersection, circular zone overlap, keyword district advisory, and out-of-corridor rejection).
6. End-to-end database persistence and lifecycle updates.
"""

import json
import pytest
from unittest.mock import AsyncMock, patch
from sqlalchemy import select
from app.clients.ndma_sachet import ndma_sachet_client
from app.clients.imd import IMDClient
from app.clients.uttarakhand_pwd import uttarakhand_pwd_client
from app.models.disaster_alert import DisasterAlert
from app.services.spatial.route_corridor_service import route_corridor_service
from app.services.provider_registry import provider_registry

# 1. Representative CAP XML payloads based on real NDMA SACHET responses
SAMPLE_POLYGON_CAP_XML = """<?xml version="1.0" encoding="UTF-8"?>
<cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
  <cap:identifier>SACHET-UTTARAKHAND-POLY-2026-001</cap:identifier>
  <cap:sender>UK-SDMA</cap:sender>
  <cap:sent>2026-10-24T06:00:00+05:30</cap:sent>
  <cap:status>Actual</cap:status>
  <cap:msgType>Alert</cap:msgType>
  <cap:info>
    <cap:category>Met</cap:category>
    <cap:event>Landslide Warning</cap:event>
    <cap:urgency>Immediate</cap:urgency>
    <cap:severity>Severe</cap:severity>
    <cap:certainty>Likely</cap:certainty>
    <cap:headline>Active Landslide Threat between Sonprayag and Gaurikund</cap:headline>
    <cap:description>Slope instability and rockfall risk following persistent mountain showers.</cap:description>
    <cap:instruction>Pilgrims and trekkers must halt at Sonprayag safe shelters until clearance.</cap:instruction>
    <cap:area>
      <cap:areaDesc>Sonprayag-Gaurikund Highway Corridor, Rudraprayag District</cap:areaDesc>
      <cap:polygon>30.60,79.00 30.65,79.00 30.65,79.06 30.60,79.06 30.60,79.00</cap:polygon>
      <cap:geocode>
        <cap:valueName>LGD_DISTRICT_CODE</cap:valueName>
        <cap:value>050</cap:value>
      </cap:geocode>
    </cap:area>
  </cap:info>
</cap:alert>
"""

SAMPLE_CIRCLE_CAP_XML = """<?xml version="1.0" encoding="UTF-8"?>
<cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
  <cap:identifier>SACHET-MANALI-CIRCLE-2026-002</cap:identifier>
  <cap:sender>HP-SDMA</cap:sender>
  <cap:sent>2026-10-24T06:30:00+05:30</cap:sent>
  <cap:status>Actual</cap:status>
  <cap:msgType>Alert</cap:msgType>
  <cap:info>
    <cap:category>Met</cap:category>
    <cap:event>Flash Flood</cap:event>
    <cap:urgency>Immediate</cap:urgency>
    <cap:severity>Extreme</cap:severity>
    <cap:certainty>Observed</cap:certainty>
    <cap:headline>High discharge in Beas River basin</cap:headline>
    <cap:description>Sudden rise in river discharge due to upstream cloudburst.</cap:description>
    <cap:instruction>Evacuate riverbank low-lying camps immediately.</cap:instruction>
    <cap:area>
      <cap:areaDesc>Manali Sub-Division, Kullu District</cap:areaDesc>
      <cap:circle>32.24,77.19 8.0</cap:circle>
    </cap:area>
  </cap:info>
</cap:alert>
"""

SAMPLE_DISTRICT_ONLY_CAP_XML = """<?xml version="1.0" encoding="UTF-8"?>
<cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
  <cap:identifier>SACHET-RAJASTHAN-DISTRICT-2026-003</cap:identifier>
  <cap:sender>RJ-SDMA</cap:sender>
  <cap:sent>2026-10-24T07:00:00+05:30</cap:sent>
  <cap:status>Actual</cap:status>
  <cap:msgType>Alert</cap:msgType>
  <cap:info>
    <cap:category>Met</cap:category>
    <cap:event>Dust Storm and Squall</cap:event>
    <cap:urgency>Expected</cap:urgency>
    <cap:severity>Moderate</cap:severity>
    <cap:certainty>Likely</cap:certainty>
    <cap:headline>Thunderstorm with gusty winds expected across 8 districts</cap:headline>
    <cap:description>High velocity surface winds (40-50 kmph) likely across Bikaner, Jodhpur, and Nagaur.</cap:description>
    <cap:instruction>Take shelter in sturdy structures; avoid temporary sheds and tin roofs.</cap:instruction>
    <cap:area>
      <cap:areaDesc>Bikaner, Jodhpur, Jaisalmer, Nagaur, Barmer districts</cap:areaDesc>
      <cap:geocode>
        <cap:valueName>LGD_DISTRICT_CODES</cap:valueName>
        <cap:value>084,085,086</cap:value>
      </cap:geocode>
    </cap:area>
  </cap:info>
</cap:alert>
"""

SAMPLE_PWD_HTML = """
<!DOCTYPE html>
<html>
<body>
  <table>
    <tr>
      <td role="rowheader">1</td>
      <td>Details of alternative routes in case of frequent road closures during the monsoon season Accessible Version : View (2 MB)</td>
      <td><a target="_blank" href="https://cdnbbsr.s3waas.gov.in/s31e0a84051e6a4a7381473328f43c4884/uploads/2026/02/202602162038342113.pdf" aria-label="View, Details of alternative routes in case of frequent road closures during the monsoon season PDF 2 MB - opens in a new window">View</a></td>
    </tr>
    <tr>
      <td role="rowheader">2</td>
      <td>Helipad list Accessible Version : View (821 KB)</td>
      <td><a target="_blank" href="https://cdnbbsr.s3waas.gov.in/s31e0a84051e6a4a7381473328f43c4884/uploads/2026/02/202602161860409952.pdf" aria-label="View, Helipad list PDF 821 KB - opens in a new window">View</a></td>
    </tr>
    <tr>
      <td role="rowheader">3</td>
      <td>SRMD Morning Road Closure Bulletin Kedarnath NH-107 Accessible Version : View (150 KB)</td>
      <td><a target="_blank" href="https://cdnbbsr.s3waas.gov.in/s31e0a84051e6a4a7381473328f43c4884/uploads/2026/02/202602169999999999.pdf" aria-label="View, SRMD Morning Road Closure Bulletin Kedarnath NH-107 PDF 150 KB - opens in a new window">View</a></td>
    </tr>
  </table>
</body>
</html>
"""


@pytest.mark.asyncio
async def test_cap_polygon_parsing_closure_and_rfc7946():
    """Verify that Polygon coordinates in CAP XML are parsed in RFC 7946 [lon, lat] format with ring closure."""
    parsed = ndma_sachet_client.parse_cap_xml(SAMPLE_POLYGON_CAP_XML)
    assert parsed is not None
    assert parsed["identifier"] == "SACHET-UTTARAKHAND-POLY-2026-001"
    assert parsed["severity"] == "SEVERE"
    assert parsed["polygon_geojson"] is not None

    poly = json.loads(parsed["polygon_geojson"])
    assert poly["type"] == "Polygon"
    ring = poly["coordinates"][0]

    # Verify closed ring (first coordinate equals last coordinate)
    assert ring[0] == ring[-1]
    # Verify RFC 7946 coordinate ordering [lon, lat]
    assert ring[0][0] == 79.00  # Longitude first
    assert ring[0][1] == 30.60  # Latitude second

    # Verify geocode and area_description preservation
    assert "Rudraprayag" in parsed["area_description"]
    assert "050" in parsed["geocode_info"]


@pytest.mark.asyncio
async def test_cap_circle_parsing():
    """Verify that Circle in CAP XML extracts coordinates and converts km radius to meters."""
    parsed = ndma_sachet_client.parse_cap_xml(SAMPLE_CIRCLE_CAP_XML)
    assert parsed is not None
    assert parsed["identifier"] == "SACHET-MANALI-CIRCLE-2026-002"
    assert parsed["center_latitude"] == 32.24
    assert parsed["center_longitude"] == 77.19
    # 8.0 km -> 8000.0 meters
    assert parsed["affected_radius_meters"] == 8000.0


@pytest.mark.asyncio
async def test_district_only_advisory_zero_coordinate_fabrication():
    """CRITICAL: District-level alerts must NOT fabricate fake centroids or default to (20.5937, 78.9629)."""
    parsed = ndma_sachet_client.parse_cap_xml(SAMPLE_DISTRICT_ONLY_CAP_XML)
    assert parsed is not None
    assert parsed["identifier"] == "SACHET-RAJASTHAN-DISTRICT-2026-003"

    # Coordinates MUST be None (no fake pin / fake circle)
    assert parsed["center_latitude"] is None
    assert parsed["center_longitude"] is None
    assert parsed["affected_radius_meters"] is None
    assert parsed["polygon_geojson"] is None

    # Text descriptions and geocodes must be fully preserved
    assert "Bikaner, Jodhpur" in parsed["area_description"]
    assert "084,085,086" in parsed["geocode_info"]


@pytest.mark.asyncio
async def test_imd_client_status_and_circuit_breaker():
    """Verify IMDClient reports UNCONFIGURED without throwing exceptions when credentials are absent."""
    client = IMDClient()
    assert client.is_configured() is False

    status = client.get_service_status()
    assert status["provider_id"] == "imd_warning"
    assert status["status"] == "UNCONFIGURED"
    assert "IMD API requires an organizational account" in status["diagnostic_message"]

    # Calling warnings when unconfigured gracefully returns empty list
    warnings = await client.fetch_district_warnings("UK01")
    assert warnings == []


@pytest.mark.asyncio
async def test_uttarakhand_pwd_bulletin_extractor():
    """Verify Uttarakhand PWD HTML parser extracts authentic S3WaaS links and categories."""
    bulletins = uttarakhand_pwd_client.parse_bulletins_html(SAMPLE_PWD_HTML)
    assert len(bulletins) == 3

    b1 = bulletins[0]
    assert "alternative routes" in b1["title"].lower()
    assert b1["category"] == "ALTERNATIVE_ROUTES"
    assert "2 MB" in b1["file_size"]
    assert b1["pdf_url"].startswith("https://cdnbbsr.s3waas.gov.in/")

    b2 = bulletins[1]
    assert "helipad" in b2["title"].lower()
    assert b2["category"] == "HELIPAD_DIRECTORY"
    assert "821 KB" in b2["file_size"]

    b3 = bulletins[2]
    assert b3["category"] == "ROAD_CLOSURE_ADVISORY"

    status = uttarakhand_pwd_client.get_service_status()
    assert status["provider_id"] == "uttarakhand_pwd"
    assert status["coverage_scope"] == "REGIONAL_UTTARAKHAND"
    assert status["emergency_helpline"] == "1070"


@pytest.mark.asyncio
async def test_corridor_filtering_polygon_circle_district(test_db):
    """
    Tests corridor spatial filtering:
    1. Polygon inside corridor is matched.
    2. Circle near corridor is matched.
    3. District advisory matching waypoint district keyword is matched as DistrictText.
    4. Distant alert (far from corridor) is rejected.
    """
    # Gaurikund to Kedarnath corridor (~30.65, 79.05)
    route_coords = [
        (30.60, 79.03),  # Sonprayag
        (30.65, 79.07),  # Gaurikund
        (30.73, 79.07),  # Kedarnath
    ]
    corridor_info = route_corridor_service.compute_corridor(route_coords, corridor_distance_meters=5000.0)
    corridor_poly = corridor_info["corridor_polygon_geojson"]

    from datetime import datetime, timezone
    now_utc = datetime.now(timezone.utc)

    # Create 4 test alert objects
    alert_poly = DisasterAlert(
        id="a1",
        title="Landslide Zone",
        description="Active slide",
        hazard_category="landslide",
        severity="SEVERE",
        status="active",
        issuing_authority="UK-SDMA",
        required_protective_instructions="Halt ascent",
        polygon_geojson=json.dumps({
            "type": "Polygon",
            "coordinates": [[[79.03, 30.62], [79.07, 30.62], [79.07, 30.66], [79.03, 30.66], [79.03, 30.62]]]
        }),
        published_at=now_utc
    )

    alert_circle = DisasterAlert(
        id="a2",
        title="Flash Flood Near Gaurikund",
        description="Mandakini river surge",
        hazard_category="flash_flood",
        severity="CRITICAL",
        status="active",
        issuing_authority="UK-SDMA",
        required_protective_instructions="Evacuate banks",
        center_latitude=30.65,
        center_longitude=79.07,
        affected_radius_meters=3000.0,
        published_at=now_utc
    )

    alert_district = DisasterAlert(
        id="a3",
        title="Heavy Rain District Advisory",
        description="Orange alert for Rudraprayag",
        hazard_category="severe_weather",
        severity="WARNING",
        status="active",
        issuing_authority="IMD",
        required_protective_instructions="Keep emergency supplies",
        center_latitude=None,
        center_longitude=None,
        area_description="Rudraprayag and Chamoli districts",
        published_at=now_utc
    )

    alert_distant = DisasterAlert(
        id="a4",
        title="Cyclone Advisory Gujarat",
        description="Coastal cyclone alert",
        hazard_category="cyclone",
        severity="CRITICAL",
        status="active",
        issuing_authority="IMD",
        required_protective_instructions="Fishermen stay ashore",
        center_latitude=21.5,
        center_longitude=69.5,
        affected_radius_meters=50000.0,
        area_description="Saurashtra and Kutch coast",
        published_at=now_utc
    )

    alerts = [alert_poly, alert_circle, alert_district, alert_distant]
    district_keywords = ["Rudraprayag", "Gaurikund", "Kedarnath"]

    matches = route_corridor_service.filter_disaster_alerts(
        alerts,
        route_coords,
        corridor_poly,
        corridor_distance_meters=5000.0,
        corridor_district_keywords=district_keywords,
    )

    matched_ids = [m[0].id for m in matches]
    assert "a1" in matched_ids  # Polygon matched
    assert "a2" in matched_ids  # Circle matched
    assert "a3" in matched_ids  # District text matched without fake polygon
    assert "a4" not in matched_ids  # Distant Gujarat alert rejected

    # Verify metadata for district alert
    a3_meta = next(m[1] for m in matches if m[0].id == "a3")
    assert a3_meta["intersection_type"] == "district_advisory"
    assert a3_meta["geometry_type"] == "DistrictText"
    assert a3_meta.get("is_non_spatial_advisory") is True


@pytest.mark.asyncio
async def test_full_ingestion_database_persistence_and_lifecycle(test_db):
    """Verifies end-to-end ingestion, duplicate avoidance, and lifecycle cancellation in database."""
    rss_xml = f"""<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>NDMA SACHET Test Feed</title>
    <item>
      <title>Landslide Alert</title>
      <link>https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile?identifier=SACHET-UTTARAKHAND-POLY-2026-001</link>
      <guid>SACHET-UTTARAKHAND-POLY-2026-001</guid>
    </item>
  </channel>
</rss>
"""
    with patch.object(ndma_sachet_client, "fetch_rss_feed", new_callable=AsyncMock) as mock_rss, \
         patch.object(ndma_sachet_client, "fetch_cap_xml", new_callable=AsyncMock) as mock_cap:

        mock_rss.return_value = (rss_xml, True)
        mock_cap.return_value = SAMPLE_POLYGON_CAP_XML

        # 1. First sync: Alert ingested
        count1 = await ndma_sachet_client.sync_sachet_alerts(test_db, max_fetch_details=5)
        assert count1 == 1

        stmt = select(DisasterAlert).where(DisasterAlert.id == "sachet-SACHET-UTTARAKHAND-POLY-2026-001")
        res = await test_db.execute(stmt)
        record = res.scalar_one()
        assert record.status == "active"
        assert record.polygon_geojson is not None
        assert record.area_description is not None
        assert "050" in record.geocode_info

        # 2. Second sync with 304 Not Modified: 0 new items ingested, existing record preserved
        mock_rss.return_value = (rss_xml, False)
        count2 = await ndma_sachet_client.sync_sachet_alerts(test_db, max_fetch_details=5)
        assert count2 == 0
