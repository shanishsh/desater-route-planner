"""USGS Himalayan Earthquake Client.

Queries official USGS GeoJSON feeds for real-time seismic events.
Filters to Uttarakhand / Himalayan geographic envelope (28.7°N–31.5°N, 77.5°E–81.1°E).
Preserves actual magnitude, depth, coordinates, and origin time without fabricating event impact.
"""
import logging
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
import httpx
from app.clients.circuit_breaker import CircuitBreaker

logger = logging.getLogger(__name__)

ATTRIBUTION_NOTICE = "Seismic data courtesy of U.S. Geological Survey (USGS) Earthquake Hazards Program."


class USGSEarthquakeClient:
    """Client for retrieving genuine seismic events from USGS real-time GeoJSON feeds."""

    FEED_URL = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson"

    # Uttarakhand regional bounding box
    UTTARAKHAND_BBOX = (28.7, 77.5, 31.5, 81.1)

    def __init__(self):
        self.circuit_breaker = CircuitBreaker("USGS_EARTHQUAKE", failure_threshold=3, recovery_time_seconds=60.0)
        self.last_attempt_at: Optional[datetime] = None
        self.last_success_at: Optional[datetime] = None
        self.last_status_code: Optional[int] = None
        self.last_error: Optional[str] = None
        self.last_record_count: int = 0

    def get_service_status(self) -> Dict[str, Any]:
        """Returns the operational status and provenance for the USGS earthquake provider."""
        status = (
            "HEALTHY"
            if self.last_status_code == 200
            else ("ERROR" if self.last_error else "STANDBY")
        )
        diag = (
            f"Retrieved {self.last_record_count} seismic event(s) in regional Himalayan envelope."
            if self.last_status_code == 200
            else (self.last_error or "USGS GeoJSON client ready for real-time earthquake feeds.")
        )
        return {
            "provider_id": "usgs_earthquake",
            "name": "USGS Himalayan Seismic Events",
            "category": "seismic",
            "is_configured": True,
            "status": status,
            "coverage_scope": "GLOBAL",
            "supported_bbox": list(self.UTTARAKHAND_BBOX),
            "last_attempt_at": self.last_attempt_at.isoformat() if self.last_attempt_at else None,
            "last_success_at": self.last_success_at.isoformat() if self.last_success_at else None,
            "last_http_status": self.last_status_code,
            "record_count": self.last_record_count,
            "cache_age_seconds": 0,
            "data_state": "LIVE" if self.last_success_at else "STANDBY",
            "is_stale": False,
            "diagnostic_message": diag,
            "attribution": ATTRIBUTION_NOTICE,
            "source_url": "https://earthquake.usgs.gov/earthquakes/feed/v1.0/",
            "registration_url": "https://earthquake.usgs.gov/",
        }

    async def fetch_regional_earthquakes(
        self,
        min_lat: float = 28.7,
        min_lon: float = 77.5,
        max_lat: float = 31.5,
        max_lon: float = 81.1,
    ) -> List[Dict[str, Any]]:
        """
        Fetches seismic events from USGS and filters strictly to the provided bounding box.
        Coordinates are [lon, lat, depth_km] in USGS GeoJSON.
        """
        now = datetime.now(timezone.utc)
        self.last_attempt_at = now

        if not self.circuit_breaker.can_execute():
            logger.warning("USGS Earthquake circuit breaker is OPEN; skipping upstream fetch.")
            return []

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                res = await client.get(self.FEED_URL, headers={"User-Agent": "TouristSafetyPlatform/1.0"})
                self.last_status_code = res.status_code

                if res.status_code == 200:
                    self.circuit_breaker.record_success()
                    self.last_success_at = now
                    self.last_error = None
                    payload = res.json()
                    features = payload.get("features", [])

                    regional_events = []
                    for feat in features:
                        geom = feat.get("geometry", {})
                        coords = geom.get("coordinates", [])
                        if len(coords) >= 2:
                            ev_lon = float(coords[0])
                            ev_lat = float(coords[1])
                            depth_km = float(coords[2]) if len(coords) >= 3 else 0.0

                            # Geographic bounding check
                            if min_lat <= ev_lat <= max_lat and min_lon <= ev_lon <= max_lon:
                                props = feat.get("properties", {})
                                time_ms = props.get("time")
                                origin_dt = (
                                    datetime.fromtimestamp(time_ms / 1000.0, tz=timezone.utc)
                                    if time_ms
                                    else now
                                )
                                regional_events.append({
                                    "id": feat.get("id"),
                                    "title": props.get("title", "Earthquake Event"),
                                    "magnitude": props.get("mag"),
                                    "depth_km": depth_km,
                                    "latitude": ev_lat,
                                    "longitude": ev_lon,
                                    "place": props.get("place"),
                                    "time": origin_dt.isoformat(),
                                    "alert_level": props.get("alert"),
                                    "url": props.get("url"),
                                    "source": "USGS Earthquake Hazards Program",
                                })

                    self.last_record_count = len(regional_events)
                    return regional_events
                else:
                    self.last_error = f"USGS HTTP {res.status_code}: {res.text[:150]}"
                    self.circuit_breaker.record_failure()
                    return []
        except Exception as e:
            self.last_error = str(e)
            self.circuit_breaker.record_failure()
            logger.warning(f"Error fetching USGS earthquakes: {e}")
            return []


usgs_earthquake_client = USGSEarthquakeClient()
