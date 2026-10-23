"""Tests for NDMA SACHET CAP client, conditional caching, and alert ingestion."""
import pytest
from unittest.mock import AsyncMock, patch
import httpx
from app.clients.ndma_sachet import ndma_sachet_client
from app.models.disaster_alert import DisasterAlert
from sqlalchemy import select

SAMPLE_RSS_XML = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>All India: CAP Disaster Alert Feeds</title>
    <item>
      <title>Flash Flood Warning for Beas River Valley</title>
      <category>Met</category>
      <link>https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile?identifier=TEST_SACHET_001</link>
      <guid>TEST_SACHET_001</guid>
      <author>controlroom@ndma.gov.in (HP-SDMA)</author>
      <pubDate>Fri, 23 Oct 2026 12:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>
"""

SAMPLE_CAP_XML = """<cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
  <cap:identifier>TEST_SACHET_001</cap:identifier>
  <cap:sender>HP-SDMA</cap:sender>
  <cap:sent>2026-10-23T17:30:00+05:30</cap:sent>
  <cap:status>Actual</cap:status>
  <cap:msgType>Alert</cap:msgType>
  <cap:info>
    <cap:event>Flash Flood</cap:event>
    <cap:urgency>Immediate</cap:urgency>
    <cap:severity>Extreme</cap:severity>
    <cap:certainty>Observed</cap:certainty>
    <cap:headline>High water levels in Beas River. Immediate evacuation advised.</cap:headline>
    <cap:description>Continuous heavy precipitation has caused river levels to rise rapidly.</cap:description>
    <cap:instruction>Move away from riverbank and proceed to designated relief shelters.</cap:instruction>
    <cap:area>
      <cap:areaDesc>Kullu and Manali subdivisions, Himachal Pradesh</cap:areaDesc>
      <cap:circle>32.24,77.19 12.0</cap:circle>
    </cap:area>
  </cap:info>
</cap:alert>
"""

SAMPLE_CANCEL_CAP_XML = """<cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
  <cap:identifier>TEST_SACHET_001</cap:identifier>
  <cap:sender>HP-SDMA</cap:sender>
  <cap:sent>2026-10-23T20:00:00+05:30</cap:sent>
  <cap:status>Actual</cap:status>
  <cap:msgType>Cancel</cap:msgType>
  <cap:info>
    <cap:event>Flash Flood</cap:event>
    <cap:urgency>Past</cap:urgency>
    <cap:severity>Minor</cap:severity>
    <cap:certainty>Unlikely</cap:certainty>
    <cap:headline>Flash flood warning cancelled. Water levels normalized.</cap:headline>
    <cap:description>River flow returned to normal seasonal volumes.</cap:description>
    <cap:instruction>Resume regular activities while observing local advisories.</cap:instruction>
    <cap:area>
      <cap:areaDesc>Kullu and Manali subdivisions, Himachal Pradesh</cap:areaDesc>
    </cap:area>
  </cap:info>
</cap:alert>
"""


@pytest.mark.asyncio
async def test_parse_rss_items():
    items = ndma_sachet_client.parse_rss_items(SAMPLE_RSS_XML)
    assert len(items) == 1
    assert items[0]["guid"] == "TEST_SACHET_001"
    assert "Flash Flood" in items[0]["title"]
    assert items[0]["category"] == "Met"


@pytest.mark.asyncio
async def test_parse_cap_xml_fields():
    parsed = ndma_sachet_client.parse_cap_xml(SAMPLE_CAP_XML)
    assert parsed is not None
    assert parsed["identifier"] == "TEST_SACHET_001"
    assert parsed["sender"] == "HP-SDMA"
    assert parsed["event"] == "Flash Flood"
    assert parsed["severity"] == "CRITICAL"
    assert parsed["hazard_category"] == "flash_flood"
    assert parsed["center_latitude"] == 32.24
    assert parsed["center_longitude"] == 77.19
    assert parsed["affected_radius_meters"] == 12000.0
    assert "evacuation advised" in parsed["headline"]


@pytest.mark.asyncio
async def test_sync_sachet_alerts_ingestion_and_cancellation(test_db):
    # Mock fetching RSS and CAP XML
    with patch.object(ndma_sachet_client, "fetch_rss_feed", new_callable=AsyncMock) as mock_rss, \
         patch.object(ndma_sachet_client, "fetch_cap_xml", new_callable=AsyncMock) as mock_cap:

        mock_rss.return_value = (SAMPLE_RSS_XML, True)
        mock_cap.return_value = SAMPLE_CAP_XML

        # 1. Ingest initial alert
        count = await ndma_sachet_client.sync_sachet_alerts(test_db, max_fetch_details=5)
        assert count == 1

        stmt = select(DisasterAlert).where(DisasterAlert.id == "sachet-TEST_SACHET_001")
        res = await test_db.execute(stmt)
        alert = res.scalar_one()
        assert alert.status == "active"
        assert alert.severity == "CRITICAL"
        assert alert.is_simulated is False
        assert alert.center_latitude == 32.24

        # 2. Ingest cancellation
        mock_cap.return_value = SAMPLE_CANCEL_CAP_XML
        cancel_count = await ndma_sachet_client.sync_sachet_alerts(test_db, max_fetch_details=5)
        assert cancel_count == 1

        res_cancel = await test_db.execute(stmt)
        updated_alert = res_cancel.scalar_one()
        assert updated_alert.status == "cancelled"


SAMPLE_POLYGON_AND_CIRCLE_CAP_XML = """<cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
  <cap:identifier>TEST_SACHET_POLY_002</cap:identifier>
  <cap:sender>HP-SDMA</cap:sender>
  <cap:sent>2026-10-24T05:00:00+05:30</cap:sent>
  <cap:status>Actual</cap:status>
  <cap:msgType>Alert</cap:msgType>
  <cap:info>
    <cap:event>Landslide</cap:event>
    <cap:urgency>Immediate</cap:urgency>
    <cap:severity>Severe</cap:severity>
    <cap:certainty>Observed</cap:certainty>
    <cap:effective>2026-10-24T05:00:00+05:30</cap:effective>
    <cap:expires>2026-10-25T18:00:00+05:30</cap:expires>
    <cap:headline>Active Landslide along Rohtang Access Road.</cap:headline>
    <cap:description>Rockfall blocking corridor.</cap:description>
    <cap:instruction>Halt all ascent until clearance.</cap:instruction>
    <cap:area>
      <cap:areaDesc>Rohtang Pass Highway</cap:areaDesc>
      <cap:circle>32.35,77.20 5.0</cap:circle>
      <cap:polygon>32.34,77.19 32.36,77.19 32.36,77.21 32.34,77.21</cap:polygon>
    </cap:area>
  </cap:info>
</cap:alert>
"""

SAMPLE_EXPIRED_CAP_XML = """<cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2">
  <cap:identifier>TEST_SACHET_EXPIRED_003</cap:identifier>
  <cap:sender>HP-SDMA</cap:sender>
  <cap:sent>2026-10-20T05:00:00+05:30</cap:sent>
  <cap:status>Actual</cap:status>
  <cap:msgType>Alert</cap:msgType>
  <cap:info>
    <cap:event>Heavy Rainfall</cap:event>
    <cap:urgency>Past</cap:urgency>
    <cap:severity>Moderate</cap:severity>
    <cap:certainty>Observed</cap:certainty>
    <cap:effective>2026-10-20T05:00:00+05:30</cap:effective>
    <cap:expires>2026-10-21T12:00:00+05:30</cap:expires>
    <cap:headline>Past heavy rain bulletin.</cap:headline>
    <cap:description>Rain event concluded.</cap:description>
    <cap:instruction>None.</cap:instruction>
    <cap:area>
      <cap:areaDesc>Kullu</cap:areaDesc>
      <cap:circle>31.96,77.11 10.0</cap:circle>
    </cap:area>
  </cap:info>
</cap:alert>
"""


@pytest.mark.asyncio
async def test_parse_cap_xml_polygon_preservation_and_closure():
    """Verify that <cap:polygon> is not dropped when <cap:circle> is present, and polygon is closed."""
    import json
    parsed = ndma_sachet_client.parse_cap_xml(SAMPLE_POLYGON_AND_CIRCLE_CAP_XML)
    assert parsed is not None
    assert parsed["polygon_geojson"] is not None
    poly = json.loads(parsed["polygon_geojson"])
    assert poly["type"] == "Polygon"
    ring = poly["coordinates"][0]
    # 4 input coordinates + 1 closure point = 5 points
    assert len(ring) == 5
    # Verify RFC 7946 GeoJSON [longitude, latitude] coordinate ordering
    assert ring[0][0] == 77.19
    assert ring[0][1] == 32.34
    # Closed ring: first point == last point
    assert ring[0] == ring[-1]
    # Verify effective and expires datetime parsing
    assert parsed["effective_at"] is not None
    assert parsed["expires_at"] is not None
    assert parsed["expires_at"].year == 2026


@pytest.mark.asyncio
async def test_sync_sachet_expired_alert_status(test_db):
    """Verify that an already-expired CAP alert is marked status='expired' upon ingestion."""
    expired_rss = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>All India: CAP Disaster Alert Feeds</title>
    <item>
      <title>Past heavy rain bulletin</title>
      <link>https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile?identifier=TEST_SACHET_EXPIRED_003</link>
      <guid>TEST_SACHET_EXPIRED_003</guid>
    </item>
  </channel>
</rss>
"""
    with patch.object(ndma_sachet_client, "fetch_rss_feed", new_callable=AsyncMock) as mock_rss, \
         patch.object(ndma_sachet_client, "fetch_cap_xml", new_callable=AsyncMock) as mock_cap:

        mock_rss.return_value = (expired_rss, True)
        mock_cap.return_value = SAMPLE_EXPIRED_CAP_XML

        count = await ndma_sachet_client.sync_sachet_alerts(test_db, max_fetch_details=5)
        assert count == 1

        stmt = select(DisasterAlert).where(DisasterAlert.id == "sachet-TEST_SACHET_EXPIRED_003")
        res = await test_db.execute(stmt)
        alert = res.scalar_one()
        assert alert.status == "expired"
        assert alert.expires_at is not None

