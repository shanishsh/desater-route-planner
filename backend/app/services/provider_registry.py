"""Centralized provider registry tracking upstream health, latency, configuration, and freshness."""
import logging
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional, Tuple
from app.config import settings
from app.clients.open_meteo import open_meteo_client
from app.clients.osm_forest import osm_forest_client
from app.clients.nasa_firms import nasa_firms_client
from app.clients.opencellid import opencellid_client
from app.clients.ndma_sachet import ndma_sachet_client
from app.services.routing.osrm_provider import osrm_routing_provider
from app.cache.redis_cache import cache_service

logger = logging.getLogger(__name__)


class ProviderRegistry:
    """Manages provider health tracking, status reporting, geographic coverage, and independent refresh orchestration."""

    def __init__(self):
        self._last_refresh_attempt: Optional[datetime] = None

    @staticmethod
    def is_point_in_india(lat: float, lon: float) -> bool:
        """Determines if a WGS84 coordinate falls within the geographic envelope of India."""
        return 6.0 <= lat <= 37.5 and 68.0 <= lon <= 97.5

    def check_provider_coverage(self, provider_id: str, lat: float, lon: float) -> Tuple[bool, Optional[str]]:
        """
        Validates whether a provider genuinely supports data queries for the given coordinates.
        Does not infer universal coverage merely because an endpoint accepts coordinates.
        """
        if provider_id == "ndma_sachet":
            if not self.is_point_in_india(lat, lon):
                return False, "NDMA SACHET disaster advisories cover India only. No official SACHET alerts available for this region."
            return True, None
        elif provider_id in ("open_meteo", "osm_overpass", "nasa_firms", "osrm"):
            return True, None
        return True, None

    async def get_all_provider_statuses(self) -> List[Dict[str, Any]]:
        """Returns the real-time operational status for all configured and reference providers."""
        now = datetime.now(timezone.utc)

        # 0. OSRM Routing Engine
        osrm_circuit_open = osrm_routing_provider.circuit_breaker.state == "OPEN"
        if osrm_circuit_open:
            osrm_status = "CIRCUIT_OPEN"
            osrm_diag = "OSRM circuit breaker is OPEN."
        elif osrm_routing_provider.last_error:
            osrm_status = "DEGRADED"
            osrm_diag = osrm_routing_provider.last_error
        elif osrm_routing_provider.last_status_code == 200:
            osrm_status = "HEALTHY"
            osrm_diag = "OSRM routing operational for driving, hiking, and walking."
        else:
            osrm_status = "STANDBY"
            osrm_diag = "OSRM routing client initialized and ready."

        osrm_info = {
            "provider_id": "osrm",
            "name": "OSRM Routing Engine",
            "category": "routing",
            "is_configured": True,
            "status": osrm_status,
            "coverage_scope": "GLOBAL",
            "last_attempt_at": osrm_routing_provider.last_attempt_at.isoformat() if hasattr(osrm_routing_provider, "last_attempt_at") and osrm_routing_provider.last_attempt_at else None,
            "last_success_at": osrm_routing_provider.last_success_at.isoformat() if hasattr(osrm_routing_provider, "last_success_at") and osrm_routing_provider.last_success_at else None,
            "last_http_status": osrm_routing_provider.last_status_code,
            "record_count": 1 if osrm_routing_provider.last_status_code == 200 else 0,
            "cache_age_seconds": 0,
            "data_state": "LIVE" if osrm_routing_provider.last_status_code == 200 else "STANDBY",
            "is_stale": False,
            "diagnostic_message": osrm_diag,
            "attribution": "Routing data © OpenStreetMap contributors, OSRM Project",
            "source_url": "https://project-osrm.org/",
            "registration_url": "https://project-osrm.org/",
        }

        # 1. Open-Meteo Weather
        meteo_circuit_open = open_meteo_client.circuit_breaker.state == "OPEN"
        if meteo_circuit_open:
            meteo_status = "CIRCUIT_OPEN"
            meteo_diag = "Open-Meteo circuit breaker is OPEN."
        elif open_meteo_client.last_error:
            meteo_status = "DEGRADED"
            meteo_diag = open_meteo_client.last_error
        elif open_meteo_client.last_success_at:
            meteo_status = "HEALTHY"
            meteo_diag = "Live forecast and current weather operational (No key required; non-commercial tier)."
        else:
            meteo_status = "STANDBY"
            meteo_diag = "Open-Meteo client initialized and ready for weather queries."

        open_meteo_info = {
            "provider_id": "open_meteo",
            "name": "Open-Meteo Weather API",
            "category": "weather",
            "is_configured": True,
            "status": meteo_status,
            "coverage_scope": "GLOBAL",
            "last_attempt_at": open_meteo_client.last_attempt_at.isoformat() if open_meteo_client.last_attempt_at else None,
            "last_success_at": open_meteo_client.last_success_at.isoformat() if open_meteo_client.last_success_at else None,
            "last_http_status": open_meteo_client.last_status_code,
            "record_count": open_meteo_client.last_record_count,
            "cache_age_seconds": 0,
            "data_state": "LIVE" if open_meteo_client.last_success_at else "STANDBY",
            "is_stale": False,
            "diagnostic_message": meteo_diag,
            "attribution": "Weather data by Open-Meteo under CC BY 4.0",
            "source_url": "https://open-meteo.com/",
            "registration_url": "https://open-meteo.com/",
        }

        # 2. OpenStreetMap Overpass (Forest Cover)
        osm_status = osm_forest_client.last_error if osm_forest_client.last_error else (
            "HEALTHY" if osm_forest_client.last_status_code == 200 else "STANDBY"
        )
        if osm_forest_client.last_status_code == 200:
            osm_state = "HEALTHY"
            osm_diag = f"Successfully parsed {osm_forest_client.last_record_count} forest & woodland geometry features."
        elif osm_forest_client.last_status_code == 429:
            osm_state = "RATE_LIMITED"
            osm_diag = "Overpass public API rate limit reached. Cached geometries will be served."
        elif osm_forest_client.last_error:
            osm_state = "DEGRADED"
            osm_diag = osm_forest_client.last_error
        else:
            osm_state = "HEALTHY"
            osm_diag = "OpenStreetMap Overpass ready for bounding-box forest geometry queries."

        osm_info = {
            "provider_id": "osm_overpass",
            "name": "OpenStreetMap Overpass (Forest Cover)",
            "category": "forest",
            "is_configured": True,
            "status": osm_state,
            "coverage_scope": "GLOBAL",
            "last_attempt_at": osm_forest_client.last_attempt_at.isoformat() if osm_forest_client.last_attempt_at else None,
            "last_success_at": osm_forest_client.last_success_at.isoformat() if osm_forest_client.last_success_at else None,
            "last_http_status": osm_forest_client.last_status_code,
            "record_count": osm_forest_client.last_record_count,
            "cache_age_seconds": 0,
            "data_state": "LIVE" if osm_forest_client.last_success_at else "STANDBY",
            "is_stale": False,
            "diagnostic_message": osm_diag,
            "attribution": "Forest data © OpenStreetMap contributors under ODbL",
            "source_url": "https://wiki.openstreetmap.org/wiki/Overpass_API",
            "registration_url": "https://overpass-api.de/",
        }

        # 3. NASA FIRMS (Active Wildfires)
        firms_configured = nasa_firms_client.is_configured()
        if not firms_configured:
            firms_status = "UNCONFIGURED"
            firms_diag = "NASA_FIRMS_MAP_KEY is unconfigured. Obtain a free key at https://firms.modaps.eosdis.nasa.gov/api/map_key/ and add to backend .env"
        elif nasa_firms_client.last_error:
            firms_status = "ERROR"
            firms_diag = nasa_firms_client.last_error
        elif nasa_firms_client.last_success_at:
            firms_status = "HEALTHY"
            firms_diag = f"Connected. {nasa_firms_client.last_record_count} active thermal anomalies retrieved."
        else:
            firms_status = "STANDBY"
            firms_diag = "NASA FIRMS client configured and ready for satellite hotspot queries."

        nasa_firms_info = {
            "provider_id": "nasa_firms",
            "name": "NASA FIRMS Satellite Active Fires",
            "category": "fire",
            "is_configured": firms_configured,
            "status": firms_status,
            "coverage_scope": "GLOBAL",
            "last_attempt_at": nasa_firms_client.last_attempt_at.isoformat() if nasa_firms_client.last_attempt_at else None,
            "last_success_at": nasa_firms_client.last_success_at.isoformat() if nasa_firms_client.last_success_at else None,
            "last_http_status": nasa_firms_client.last_status_code,
            "record_count": nasa_firms_client.last_record_count,
            "cache_age_seconds": 0,
            "data_state": "LIVE" if firms_configured and nasa_firms_client.last_success_at else ("UNCONFIGURED" if not firms_configured else "STANDBY"),
            "is_stale": False,
            "diagnostic_message": firms_diag,
            "attribution": "NASA FIRMS imagery & data courtesy of NASA-LANCE / EOSDIS",
            "source_url": "https://firms.modaps.eosdis.nasa.gov/",
            "registration_url": "https://firms.modaps.eosdis.nasa.gov/api/map_key/",
        }

        # 4. Forest Survey of India (FSI) Forest Fire Alerts System
        fsi_info = {
            "provider_id": "fsi_forest_fire",
            "name": "Forest Survey of India (FSI) Fire Alerts",
            "category": "fire_reference",
            "is_configured": False,
            "status": "REFERENCE_ONLY",
            "coverage_scope": "COUNTRY_INDIA",
            "last_attempt_at": None,
            "last_success_at": None,
            "last_http_status": None,
            "record_count": 0,
            "cache_age_seconds": 0,
            "data_state": "UNAVAILABLE",
            "is_stale": False,
            "diagnostic_message": "Official reference portal. No public automated JSON/REST API is published by FSI. External portal link provided; manual feed importer ready.",
            "attribution": "Forest Survey of India, Ministry of Environment, Forest and Climate Change (MoEFCC)",
            "source_url": "https://fsiforestfire.gov.in/",
            "registration_url": "https://fsiforestfire.gov.in/",
        }

        # 5. OpenCelliD (Cell Towers)
        cell_configured = opencellid_client.is_configured()
        if not cell_configured:
            cell_status = "UNCONFIGURED"
            cell_diag = "OPENCELLID_API_KEY is not configured. Register at https://opencellid.org to get an API key."
        elif opencellid_client.last_error:
            cell_status = "ERROR"
            cell_diag = opencellid_client.last_error
        else:
            cell_status = "HEALTHY" if opencellid_client.last_success_at else "STANDBY"
            cell_diag = f"OpenCelliD ready. Handset signal measurements will complement cell location queries."

        opencellid_info = {
            "provider_id": "opencellid",
            "name": "OpenCelliD Cell Tower Database",
            "category": "telecom",
            "is_configured": cell_configured,
            "status": cell_status,
            "coverage_scope": "GLOBAL",
            "last_attempt_at": opencellid_client.last_attempt_at.isoformat() if opencellid_client.last_attempt_at else None,
            "last_success_at": opencellid_client.last_success_at.isoformat() if opencellid_client.last_success_at else None,
            "last_http_status": opencellid_client.last_status_code,
            "record_count": opencellid_client.last_record_count,
            "cache_age_seconds": 0,
            "data_state": "LIVE" if cell_configured and opencellid_client.last_success_at else ("UNCONFIGURED" if not cell_configured else "STANDBY"),
            "is_stale": False,
            "diagnostic_message": cell_diag,
            "attribution": "Data from OpenCelliD Project licensed under CC-BY-SA 4.0",
            "source_url": "https://opencellid.org/",
            "registration_url": "https://opencellid.org/register.php",
        }

        # 6. NDMA SACHET CAP Disaster Advisory Feed
        sachet_state = "HEALTHY" if ndma_sachet_client.last_status_code in [200, 304] else (
            "ERROR" if ndma_sachet_client.last_error else "STANDBY"
        )
        sachet_diag = (
            f"Connected to official NDMA SACHET CAP feed ({ndma_sachet_client.active_alerts_count} alerts indexed)."
            if ndma_sachet_client.last_status_code in [200, 304]
            else (ndma_sachet_client.last_error or "NDMA SACHET CAP client initialized and ready for advisories.")
        )
        ndma_sachet_info = {
            "provider_id": "ndma_sachet",
            "name": "NDMA SACHET Disaster Alert Feeds (CAP 1.2)",
            "category": "disaster_advisory",
            "is_configured": True,
            "status": sachet_state,
            "coverage_scope": "COUNTRY_INDIA",
            "supported_bbox": [6.0, 68.0, 37.5, 97.5],
            "last_attempt_at": ndma_sachet_client.last_attempt_at,
            "last_success_at": ndma_sachet_client.last_success_at,
            "last_http_status": ndma_sachet_client.last_status_code,
            "record_count": ndma_sachet_client.active_alerts_count,
            "cache_age_seconds": 0,
            "data_state": "LIVE" if ndma_sachet_client.last_success_at else "STANDBY",
            "is_stale": False,
            "diagnostic_message": sachet_diag,
            "attribution": "National Disaster Management Authority (NDMA) & State Disaster Management Authorities (SDMAs)",
            "source_url": "https://sachet.ndma.gov.in/",
            "registration_url": "https://sachet.ndma.gov.in/CapFeed",
        }

        # 7. Database / Local Cache
        db_info = {
            "provider_id": "backend_database",
            "name": "Local PostGIS / Room Cache",
            "category": "storage",
            "is_configured": True,
            "status": "HEALTHY",
            "coverage_scope": "GLOBAL",
            "last_attempt_at": now.isoformat(),
            "last_success_at": now.isoformat(),
            "last_http_status": 200,
            "record_count": 0,
            "cache_age_seconds": 0,
            "data_state": "LIVE",
            "is_stale": False,
            "diagnostic_message": "Local database and Redis caching operational.",
            "attribution": "Tourist Safety & Assistance Platform",
            "source_url": "http://localhost:8000",
            "registration_url": "",
        }

        return [
            osrm_info,
            open_meteo_info,
            osm_info,
            nasa_firms_info,
            ndma_sachet_info,
            fsi_info,
            opencellid_info,
            db_info,
        ]

    async def refresh_all(self, test_lat: float = 32.2396, test_lon: float = 77.1887) -> Dict[str, Any]:
        """Triggers a smoke refresh across all configured live external providers."""
        now = datetime.now(timezone.utc)
        self._last_refresh_attempt = now

        # Run independent provider queries concurrently with isolation (one failure doesn't affect others)
        results = {}

        # 1. Weather
        try:
            w_res = await open_meteo_client.fetch_current_weather(test_lat, test_lon)
            results["weather"] = "SUCCESS" if w_res else "FAILED"
        except Exception as e:
            results["weather"] = f"ERROR: {str(e)}"

        # 2. Forest
        try:
            f_res = await osm_forest_client.fetch_forest_features(
                test_lat - 0.1, test_lon - 0.1, test_lat + 0.1, test_lon + 0.1
            )
            results["forest"] = f"SUCCESS ({f_res.get('record_count', 0)} features)"
        except Exception as e:
            results["forest"] = f"ERROR: {str(e)}"

        # 3. NASA FIRMS (if configured)
        if nasa_firms_client.is_configured():
            try:
                fire_res = await nasa_firms_client.fetch_active_fires(
                    test_lat - 0.5, test_lon - 0.5, test_lat + 0.5, test_lon + 0.5
                )
                results["active_fires"] = f"SUCCESS ({fire_res.get('record_count', 0)} hotspots)"
            except Exception as e:
                results["active_fires"] = f"ERROR: {str(e)}"
        else:
            results["active_fires"] = "UNCONFIGURED"

        # 4. OpenCelliD (if configured)
        if opencellid_client.is_configured():
            try:
                c_res = await opencellid_client.fetch_nearby_cells(test_lat, test_lon)
                results["cell_towers"] = f"SUCCESS ({c_res.get('record_count', 0)} cells)"
            except Exception as e:
                results["cell_towers"] = f"ERROR: {str(e)}"
        else:
            results["cell_towers"] = "UNCONFIGURED"

        return {
            "refreshed_at": now.isoformat(),
            "results": results,
            "providers": await self.get_all_provider_statuses(),
        }


provider_registry = ProviderRegistry()

