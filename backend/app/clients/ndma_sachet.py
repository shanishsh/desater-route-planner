"""NDMA SACHET CAP (Common Alerting Protocol) RSS/XML Client with conditional ETag caching."""
import logging
import math
import uuid
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional, Tuple
import httpx
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession
from app.clients.circuit_breaker import CircuitBreaker
from app.cache.redis_cache import cache_service
from app.models.disaster_alert import DisasterAlert

logger = logging.getLogger(__name__)

# State / Region centroids for geographic scoping when explicit circle/polygon is absent
INDIA_REGION_CENTROIDS: Dict[str, Tuple[float, float]] = {
    "himachal": (31.95, 77.25),
    "shimla": (31.10, 77.17),
    "kullu": (31.96, 77.11),
    "manali": (32.24, 77.19),
    "mandi": (31.71, 76.93),
    "kangra": (32.09, 76.27),
    "dharamshala": (32.22, 76.32),
    "uttarakhand": (30.06, 79.02),
    "dehradun": (30.32, 78.03),
    "delhi": (28.61, 77.21),
    "chennai": (13.08, 80.27),
    "puducherry": (11.94, 79.81),
    "tamil nadu": (11.13, 78.65),
    "kerala": (10.85, 76.27),
    "maharashtra": (19.75, 75.71),
    "mumbai": (19.07, 72.88),
    "karnataka": (15.31, 75.71),
    "bengaluru": (12.97, 77.59),
    "jammu": (32.73, 74.86),
    "kashmir": (34.08, 74.80),
    "ladakh": (34.15, 77.58),
    "punjab": (31.15, 75.34),
    "haryana": (29.06, 76.09),
    "rajasthan": (27.02, 74.22),
    "gujarat": (22.26, 71.19),
    "west bengal": (22.98, 87.85),
    "odisha": (20.95, 85.10),
    "assam": (26.20, 92.94),
}


def _parse_iso_dt(dt_str: Optional[str]) -> Optional[datetime]:
    """Parses ISO 8601 datetimes with timezone offsets (e.g., CAP 1.2 format)."""
    if not dt_str:
        return None
    try:
        cleaned = dt_str.strip()
        if cleaned.endswith("Z"):
            cleaned = cleaned[:-1] + "+00:00"
        dt = datetime.fromisoformat(cleaned)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(timezone.utc)
    except Exception:
        return None


class NDMASachetClient:
    """Official NDMA SACHET Disaster Alerts Client implementing RSS and CAP 1.2 XML parsing."""

    RSS_FEED_URL = "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml"
    FETCH_XML_URL = "https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile"

    def __init__(self):
        self.circuit_breaker = CircuitBreaker("NDMA_SACHET", failure_threshold=3, recovery_time_seconds=60.0)
        self._last_etag: Optional[str] = None
        self._last_feed_xml: Optional[str] = None
        self._client: Optional[httpx.AsyncClient] = None
        self._loop = None
        self.last_attempt_at: Optional[str] = None
        self.last_success_at: Optional[str] = None
        self.last_status_code: Optional[int] = None
        self.last_error: Optional[str] = None
        self.active_alerts_count: int = 0

    async def get_client(self) -> httpx.AsyncClient:
        import asyncio
        loop = asyncio.get_running_loop()
        if self._client is None or self._client.is_closed or self._loop != loop:
            self._loop = loop
            self._client = httpx.AsyncClient(
                timeout=httpx.Timeout(connect=5.0, read=12.0, write=5.0, pool=5.0),
                headers={
                    "User-Agent": "TouristSafetyPlatform/1.0 (Non-Commercial; NDMA-CAP-Reader)",
                    "Accept": "application/xml, text/xml",
                },
                verify=False,  # Government portal SSL chain compatibility
            )
        return self._client

    async def fetch_rss_feed(self) -> Tuple[Optional[str], bool]:
        """
        Fetches the NDMA SACHET public CAP RSS feed with conditional ETag validation.
        Returns (xml_content, is_modified).
        """
        now = datetime.now(timezone.utc)
        self.last_attempt_at = now.isoformat()

        if not self.circuit_breaker.can_execute():
            logger.warning("NDMA SACHET circuit breaker is OPEN; skipping upstream fetch.")
            return self._last_feed_xml, False

        client = await self.get_client()
        headers = {}
        if self._last_etag:
            headers["If-None-Match"] = self._last_etag

        try:
            response = await client.get(self.RSS_FEED_URL, headers=headers)
            self.last_status_code = response.status_code

            if response.status_code == 304:
                # Unmodified: reuse cached XML
                logger.info("NDMA SACHET RSS feed unchanged (HTTP 304 Not Modified).")
                self.circuit_breaker.record_success()
                return self._last_feed_xml, False

            if response.status_code == 200:
                self._last_etag = response.headers.get("ETag")
                self._last_feed_xml = response.text
                self.last_success_at = now.isoformat()
                self.last_error = None
                self.circuit_breaker.record_success()
                return response.text, True

            self.last_error = f"HTTP {response.status_code}: {response.text[:200]}"
            self.circuit_breaker.record_failure()
            return self._last_feed_xml, False

        except Exception as e:
            self.last_error = str(e)
            self.circuit_breaker.record_failure()
            logger.warning(f"Error fetching NDMA SACHET feed: {e}")
            return self._last_feed_xml, False

    def parse_rss_items(self, feed_xml: str) -> List[Dict[str, Any]]:
        """Parses individual items from the RSS feed."""
        if not feed_xml:
            return []

        items = []
        try:
            root = ET.fromstring(feed_xml)
            channel = root.find("channel")
            if channel is None:
                return []

            for item in channel.findall("item"):
                title = item.findtext("title", default="").strip()
                category = item.findtext("category", default="").strip()
                link = item.findtext("link", default="").strip()
                guid = item.findtext("guid", default="").strip()
                author = item.findtext("author", default="").strip()
                pub_date = item.findtext("pubDate", default="").strip()

                if not guid and link:
                    # Extract identifier from URL
                    if "identifier=" in link:
                        guid = link.split("identifier=")[-1]

                items.append({
                    "title": title,
                    "category": category,
                    "link": link,
                    "guid": guid or str(uuid.uuid4()),
                    "author": author,
                    "pubDate": pub_date,
                })
        except Exception as e:
            logger.warning(f"Error parsing NDMA SACHET RSS XML: {e}")

        return items

    async def fetch_cap_xml(self, identifier: str) -> Optional[str]:
        """Fetches detailed OASIS CAP 1.2 XML document for an alert identifier."""
        client = await self.get_client()
        try:
            url = f"{self.FETCH_XML_URL}?identifier={identifier}"
            res = await client.get(url)
            if res.status_code == 200:
                return res.text
        except Exception as e:
            logger.warning(f"Error fetching CAP XML for {identifier}: {e}")
        return None

    def parse_cap_xml(self, cap_xml_str: str) -> Optional[Dict[str, Any]]:
        """Parses OASIS CAP 1.2 XML fields."""
        if not cap_xml_str:
            return None

        try:
            root = ET.fromstring(cap_xml_str)
            ns = {"cap": "urn:oasis:names:tc:emergency:cap:1.2"}

            identifier = root.findtext("cap:identifier", default="", namespaces=ns)
            sender = root.findtext("cap:sender", default="", namespaces=ns)
            sent_str = root.findtext("cap:sent", default="", namespaces=ns)
            status = root.findtext("cap:status", default="Actual", namespaces=ns)
            msg_type = root.findtext("cap:msgType", default="Alert", namespaces=ns)

            info = root.find("cap:info", ns)
            if info is None:
                return None

            event = info.findtext("cap:event", default="Disaster Alert", namespaces=ns)
            urgency = info.findtext("cap:urgency", default="Unknown", namespaces=ns)
            severity = info.findtext("cap:severity", default="Moderate", namespaces=ns)
            certainty = info.findtext("cap:certainty", default="Observed", namespaces=ns)
            headline = info.findtext("cap:headline", default="", namespaces=ns)
            description = info.findtext("cap:description", default="", namespaces=ns)
            instruction = info.findtext("cap:instruction", default="", namespaces=ns)
            effective_str = info.findtext("cap:effective", default="", namespaces=ns)
            onset_str = info.findtext("cap:onset", default="", namespaces=ns)
            expires_str = info.findtext("cap:expires", default="", namespaces=ns)
            effective_at = _parse_iso_dt(effective_str) or _parse_iso_dt(sent_str)
            expires_at = _parse_iso_dt(expires_str)

            # Area information
            area = info.find("cap:area", ns)
            area_desc = ""
            circle_str = ""
            polygon_str = ""
            center_lat = 0.0
            center_lon = 0.0
            radius_m = 5000.0

            if area is not None:
                area_desc = area.findtext("cap:areaDesc", default="", namespaces=ns)
                circle_str = area.findtext("cap:circle", default="", namespaces=ns)
                polygon_str = area.findtext("cap:polygon", default="", namespaces=ns)

            # Resolve coordinates from circle if provided: "lat,lon radius_km"
            if circle_str and "," in circle_str:
                parts = circle_str.strip().split()
                coords = parts[0].split(",")
                try:
                    center_lat = float(coords[0])
                    center_lon = float(coords[1])
                    if len(parts) > 1:
                        radius_m = float(parts[1]) * 1000.0
                except ValueError:
                    pass

            # Resolve coordinates from polygon if provided (always parse regardless of circle)
            polygon_geojson = None
            if polygon_str:
                pts = []
                for token in polygon_str.strip().split():
                    if "," in token:
                        sp = token.split(",")
                        try:
                            # CAP 1.2 polygon format is "latitude,longitude"
                            pts.append((float(sp[0]), float(sp[1])))
                        except ValueError:
                            pass
                if len(pts) >= 3:
                    if not (center_lat and center_lon):
                        center_lat = sum(p[0] for p in pts) / len(pts)
                        center_lon = sum(p[1] for p in pts) / len(pts)
                    
                    # RFC 7946 coordinates: [longitude, latitude]
                    ring = [[p[1], p[0]] for p in pts]
                    # Ensure linear ring is closed: first coordinate equals last coordinate
                    if ring[0] != ring[-1]:
                        ring.append(list(ring[0]))
                    import json
                    polygon_geojson = json.dumps({
                        "type": "Polygon",
                        "coordinates": [ring]
                    })

            # Geographic fallback by matching named district/state in area_desc or headline
            if not (center_lat and center_lon):
                search_text = f"{area_desc} {headline} {sender}".lower()
                for region_key, (r_lat, r_lon) in INDIA_REGION_CENTROIDS.items():
                    if region_key in search_text:
                        center_lat = r_lat
                        center_lon = r_lon
                        radius_m = 25000.0  # District-level radius
                        break

            # Map CAP severity to internal enum
            sev_mapped = "WARNING"
            sev_upper = severity.upper()
            if sev_upper in ["EXTREME"]:
                sev_mapped = "CRITICAL"
            elif sev_upper in ["SEVERE"]:
                sev_mapped = "SEVERE"
            elif sev_upper in ["MODERATE"]:
                sev_mapped = "WARNING"
            else:
                sev_mapped = "INFO"

            # Map category
            cat_lower = event.lower()
            hazard_cat = "severe_weather"
            if "flood" in cat_lower:
                hazard_cat = "flash_flood"
            elif "rain" in cat_lower:
                hazard_cat = "heavy_rainfall"
            elif "landslide" in cat_lower:
                hazard_cat = "landslide"
            elif "fire" in cat_lower:
                hazard_cat = "wildfire"
            elif "cyclone" in cat_lower or "storm" in cat_lower:
                hazard_cat = "cyclone"

            return {
                "identifier": identifier,
                "sender": sender,
                "sent_str": sent_str,
                "status": status,
                "msg_type": msg_type,
                "event": event,
                "urgency": urgency,
                "severity": sev_mapped,
                "headline": headline or event,
                "description": description or headline or event,
                "instruction": instruction or "Follow State Disaster Management Authority guidelines.",
                "area_desc": area_desc,
                "center_latitude": center_lat,
                "center_longitude": center_lon,
                "affected_radius_meters": radius_m,
                "polygon_geojson": polygon_geojson,
                "hazard_category": hazard_cat,
                "effective_at": effective_at,
                "expires_at": expires_at,
            }

        except Exception as e:
            logger.warning(f"Error parsing CAP XML: {e}")
            return None

    async def sync_sachet_alerts(self, db: AsyncSession, max_fetch_details: int = 5) -> int:
        """
        Synchronizes live NDMA SACHET alerts into the database:
        - Parses RSS feed items.
        - Fetches full CAP XML for latest items.
        - Reconciles updates, cancellations, and new alerts.
        """
        feed_xml, is_modified = await self.fetch_rss_feed()
        if not feed_xml:
            return 0

        rss_items = self.parse_rss_items(feed_xml)
        if not rss_items:
            return 0

        now = datetime.now(timezone.utc)
        synced_count = 0

        for item in rss_items[:max_fetch_details]:
            guid = item.get("guid")
            if not guid:
                continue

            cap_xml = await self.fetch_cap_xml(guid)
            if not cap_xml:
                continue

            cap_data = self.parse_cap_xml(cap_xml)
            if not cap_data:
                continue

            alert_id = f"sachet-{guid}"
            msg_type = cap_data["msg_type"].upper()

            # Check if alert already exists in database
            stmt = select(DisasterAlert).where(DisasterAlert.id == alert_id)
            res = await db.execute(stmt)
            existing: Optional[DisasterAlert] = res.scalar_one_or_none()

            if msg_type in ["CANCEL", "CANCELLED"]:
                if existing:
                    existing.status = "cancelled"
                    synced_count += 1
                continue

            # Determine lifecycle status
            expires_at = cap_data.get("expires_at")
            status_val = "expired" if (expires_at and expires_at < now) else "active"

            if existing:
                # Update existing alert
                existing.version += 1
                existing.status = "updated" if status_val == "active" else status_val
                existing.title = cap_data["headline"][:200]
                existing.description = cap_data["description"]
                existing.required_protective_instructions = cap_data["instruction"]
                existing.severity = cap_data["severity"]
                if cap_data["center_latitude"] and cap_data["center_longitude"]:
                    existing.center_latitude = cap_data["center_latitude"]
                    existing.center_longitude = cap_data["center_longitude"]
                    existing.affected_radius_meters = cap_data["affected_radius_meters"]
                if cap_data.get("polygon_geojson"):
                    existing.polygon_geojson = cap_data["polygon_geojson"]
                if cap_data.get("effective_at"):
                    existing.effective_at = cap_data["effective_at"]
                if cap_data.get("expires_at"):
                    existing.expires_at = cap_data["expires_at"]
                synced_count += 1
            else:
                published_dt = _parse_iso_dt(cap_data.get("sent_str")) or now
                new_alert = DisasterAlert(
                    id=alert_id,
                    title=cap_data["headline"][:200],
                    description=cap_data["description"],
                    hazard_category=cap_data["hazard_category"],
                    severity=cap_data["severity"],
                    status=status_val,
                    version=1,
                    is_simulated=False,
                    issuing_authority=f"NDMA SACHET / {cap_data['sender']}"[:150],
                    source_url=f"https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile?identifier={guid}",
                    source_verification_status="GOVERNMENT_OFFICIAL_VERIFIED",
                    required_protective_instructions=cap_data["instruction"],
                    center_latitude=cap_data["center_latitude"] or 20.5937, # Default to India geographic center if unpositioned
                    center_longitude=cap_data["center_longitude"] or 78.9629,
                    affected_radius_meters=cap_data["affected_radius_meters"],
                    polygon_geojson=cap_data.get("polygon_geojson"),
                    published_at=published_dt,
                    received_at=now,
                    effective_at=cap_data.get("effective_at"),
                    expires_at=cap_data.get("expires_at"),
                )
                db.add(new_alert)
                synced_count += 1

        if synced_count > 0:
            await db.commit()
            logger.info(f"Synchronized {synced_count} live NDMA SACHET disaster alerts into database.")

        self.active_alerts_count = synced_count
        return synced_count


ndma_sachet_client = NDMASachetClient()

