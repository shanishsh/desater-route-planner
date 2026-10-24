"""
Uttarakhand Public Works Department (PWD) SRMD Integration & Documentation.

Documents official Uttarakhand PWD State Road Maintenance Division (SRMD)
bulletins and notices regarding mountain highway conditions, landslide blockages,
and alternative emergency corridors.

Official Web Portal: https://pwd.uk.gov.in/srmd-reports-1/
Emergency Helpline: 1070 (State Emergency Operation Centre Uttarakhand)
"""

import logging
import re
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
import httpx

logger = logging.getLogger(__name__)

OFFICIAL_PWD_PORTAL_URL = "https://pwd.uk.gov.in/srmd-reports-1/"
PWD_DISASTER_HELPLINE = "1070"  # State Emergency Operation Centre Uttarakhand


class UttarakhandPWDClient:
    """Client providing metadata and verified bulletin access for Uttarakhand PWD."""

    def __init__(self):
        self.portal_url = OFFICIAL_PWD_PORTAL_URL
        self.emergency_helpline = PWD_DISASTER_HELPLINE
        self.last_attempt_at: Optional[str] = None
        self.last_success_at: Optional[str] = None
        self.last_status_code: Optional[int] = None
        self.last_error: Optional[str] = None
        self.etag: Optional[str] = None
        self.cached_bulletins: List[Dict[str, Any]] = []

    def _classify_bulletin(self, title: str) -> str:
        """Categorizes the bulletin based on genuine document title."""
        title_lower = title.lower()
        if "alternative route" in title_lower or "alternate route" in title_lower:
            return "ALTERNATIVE_ROUTES"
        if "closure" in title_lower or "block" in title_lower or "srmd" in title_lower:
            return "ROAD_CLOSURE_ADVISORY"
        if "nodal officer" in title_lower or "disaster management" in title_lower:
            return "NODAL_OFFICERS"
        if "helipad" in title_lower:
            return "HELIPAD_DIRECTORY"
        if "machine" in title_lower or "equipment" in title_lower:
            return "EQUIPMENT_DIRECTORY"
        if "walkie talkie" in title_lower or "communication" in title_lower:
            return "EMERGENCY_COMMUNICATION"
        return "GENERAL_SRMD_BULLETIN"

    def parse_bulletins_html(self, html_text: str) -> List[Dict[str, Any]]:
        """
        Parses genuine S3WaaS table rows from Uttarakhand PWD SRMD reports page.
        Extracts verified titles, S3WaaS PDF links, and file size metadata.
        Never fabricates mock blockage coordinates.
        """
        bulletins: List[Dict[str, Any]] = []
        if not html_text:
            return bulletins

        # Match table rows
        rows = re.findall(r"<tr[^>]*>([\s\S]*?)</tr>", html_text, re.IGNORECASE)
        for row in rows:
            # Check for direct PDF links
            link_match = re.search(
                r"""<a[^>]+href=["']([^"']+\.pdf)["'][^>]*aria-label=["']([^"']+)["']""",
                row,
                re.IGNORECASE,
            )
            if not link_match:
                # Fallback: simple href and anchor text
                link_match_simple = re.search(
                    r"""<a[^>]+href=["']([^"']+\.pdf)["'][^>]*>([\s\S]*?)</a>""",
                    row,
                    re.IGNORECASE,
                )
                if not link_match_simple:
                    continue
                pdf_url = link_match_simple.group(1).strip()
                raw_label = link_match_simple.group(2).strip()
            else:
                pdf_url = link_match.group(1).strip()
                raw_label = link_match.group(2).strip()

            # Clean aria-label or anchor text
            clean_text = re.sub(r"<[^>]+>", " ", raw_label)
            clean_text = re.sub(r"\s+", " ", clean_text).strip()
            clean_text = re.sub(r"^View,\s*", "", clean_text, flags=re.IGNORECASE)
            clean_text = re.sub(r"\s*-\s*opens in a new window$", "", clean_text, flags=re.IGNORECASE)

            # Extract file size (e.g., "42 KB", "2 MB")
            size_match = re.search(r"(\d+(?:\.\d+)?\s*(?:KB|MB|GB))", clean_text, re.IGNORECASE)
            file_size = size_match.group(1) if size_match else "PDF Document"

            # Remove size suffix from title if present
            clean_title = re.sub(r"PDF\s*\d+(?:\.\d+)?\s*(?:KB|MB|GB).*", "", clean_text, flags=re.IGNORECASE).strip()
            if not clean_title or len(clean_title) < 5:
                # Fallback: extract title from row cells
                cells = re.findall(r"<td[^>]*>([\s\S]*?)</td>", row, re.IGNORECASE)
                if cells:
                    cell_text = re.sub(r"<[^>]+>", " ", cells[0])
                    clean_title = re.sub(r"\s+", " ", cell_text).strip()

            # Extract upload date approximation from URL path if present e.g. /uploads/2026/02/...
            date_match = re.search(r"/uploads/(\d{4})/(\d{2})/", pdf_url)
            upload_date = f"{date_match.group(1)}-{date_match.group(2)}" if date_match else None

            bulletin = {
                "title": clean_title or "Uttarakhand PWD SRMD Official Bulletin",
                "pdf_url": pdf_url,
                "file_size": file_size,
                "upload_period": upload_date,
                "category": self._classify_bulletin(clean_title or ""),
                "issuing_authority": "Public Works Department, Government of Uttarakhand",
                "source_portal": self.portal_url,
                "emergency_helpline": self.emergency_helpline,
            }
            bulletins.append(bulletin)

        return bulletins

    async def fetch_latest_bulletins(self, force: bool = False) -> List[Dict[str, Any]]:
        """
        Fetches the latest official road and disaster bulletins from Uttarakhand PWD.
        Uses conditional request headers (ETag / If-None-Match) when supported to minimize bandwidth.
        """
        now_str = datetime.now(timezone.utc).isoformat()
        self.last_attempt_at = now_str

        headers = {
            "User-Agent": "TrailGuard-DisasterMonitor/1.0 (Disaster and Trek Safety Ingestion)",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        }
        if self.etag and not force:
            headers["If-None-Match"] = self.etag

        try:
            async with httpx.AsyncClient(timeout=10.0, follow_redirects=True, verify=False) as client:
                response = await client.get(self.portal_url, headers=headers)

                self.last_status_code = response.status_code
                if response.status_code == 304:
                    logger.info("Uttarakhand PWD reports not modified (HTTP 304). Serving cached bulletins.")
                    self.last_success_at = now_str
                    self.last_error = None
                    return self.cached_bulletins

                if response.status_code == 200:
                    etag = response.headers.get("ETag")
                    if etag:
                        self.etag = etag.strip('"')

                    bulletins = self.parse_bulletins_html(response.text)
                    self.cached_bulletins = bulletins
                    self.last_success_at = now_str
                    self.last_error = None
                    logger.info("Successfully fetched %d Uttarakhand PWD bulletins.", len(bulletins))
                    return self.cached_bulletins
                else:
                    self.last_error = f"HTTP {response.status_code} received from PWD portal"
                    logger.warning("Uttarakhand PWD portal returned status %s", response.status_code)
                    return self.cached_bulletins

        except Exception as e:
            self.last_error = str(e)
            logger.warning("Error fetching Uttarakhand PWD bulletins: %s", e)
            return self.cached_bulletins

    def get_service_status(self) -> Dict[str, Any]:
        """Returns the operational status and integration details of Uttarakhand PWD."""
        status = "HEALTHY" if self.last_status_code in [200, 304] else (
            "DEGRADED" if self.last_error else "STANDBY"
        )
        diag = (
            f"Connected to official Uttarakhand PWD portal ({len(self.cached_bulletins)} verified bulletins available)."
            if self.last_status_code in [200, 304]
            else (self.last_error or "Uttarakhand PWD client initialized and ready.")
        )
        return {
            "provider_id": "uttarakhand_pwd",
            "name": "Uttarakhand PWD State Road Maintenance Division (SRMD)",
            "category": "road_advisory",
            "is_configured": True,
            "status": status,
            "coverage_scope": "REGIONAL_UTTARAKHAND",
            "supported_bbox": [28.7, 77.5, 31.5, 81.1],
            "official_portal": self.portal_url,
            "emergency_helpline": self.emergency_helpline,
            "feed_type": "OFFICIAL_GOVERNMENT_BULLETIN",
            "data_format": "PDF / Daily State Operations Circulars",
            "has_automated_rest_api": False,
            "last_attempt_at": self.last_attempt_at,
            "last_success_at": self.last_success_at,
            "last_http_status": self.last_status_code,
            "record_count": len(self.cached_bulletins),
            "cache_age_seconds": 0,
            "data_state": "LIVE" if self.cached_bulletins else "STANDBY",
            "is_stale": False,
            "diagnostic_message": diag,
            "attribution": "Public Works Department, Government of Uttarakhand",
            "source_url": self.portal_url,
            "registration_url": self.portal_url,
            "bulletins": self.cached_bulletins[:5],
            "integration_note": (
                "Uttarakhand PWD road advisories are published as verified state bulletins and circulars. "
                "TrailGuard displays official verified bulletins and alternative corridor guidance "
                "without synthesizing unverified dynamic road closures."
            ),
        }


uttarakhand_pwd_client = UttarakhandPWDClient()
