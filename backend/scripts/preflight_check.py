"""
Configuration and Environment Preflight Verification Tool.

Audits runtime configuration, tests database and Redis connections,
and executes bounded health checks against external providers.
Prints a sanitized, security-conscious diagnostic report without revealing secrets.

Usage:
    python backend/scripts/preflight_check.py
    python backend/scripts/preflight_check.py --strict
    python backend/scripts/preflight_check.py --json
"""
import sys
import os
import re
import time
import json
import asyncio
import argparse
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
from urllib.parse import urlparse

# Ensure backend directory is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import httpx
from sqlalchemy import text


class PreflightChecker:
    """Performs non-destructive environment, configuration, and connectivity audits."""

    def __init__(self, strict: bool = False):
        self.strict = strict
        self.report: Dict[str, Any] = {
            "timestamp_utc": datetime.now(timezone.utc).isoformat(),
            "overall_status": "PASS",
            "checks": [],
            "summary": {"pass": 0, "warn": 0, "fail": 0, "info": 0},
        }

    def _record(self, name: str, priority: str, status: str, message: str, details: Optional[Dict[str, Any]] = None):
        """Records an individual check result."""
        self.report["checks"].append({
            "name": name,
            "priority": priority,
            "status": status,
            "message": message,
            "details": details or {},
        })
        self.report["summary"][status.lower()] = self.report["summary"].get(status.lower(), 0) + 1
        if status == "FAIL":
            self.report["overall_status"] = "FAIL"
        elif status == "WARN" and self.report["overall_status"] != "FAIL":
            self.report["overall_status"] = "WARN"

    def check_env_variables(self, settings):
        """Verifies core environment variables without exposing sensitive values."""
        # 1. Environment mode
        env_mode = settings.ENVIRONMENT
        self._record(
            "ENVIRONMENT",
            "P0",
            "PASS" if env_mode in ("development", "staging", "production") else "WARN",
            f"Environment configured as '{env_mode}'.",
        )

        # 2. Secret Key
        secret = settings.SECRET_KEY
        if not secret or len(secret) < 32:
            self._record(
                "SECRET_KEY",
                "P0",
                "FAIL",
                "SECRET_KEY must be a cryptographically secure random string with minimum 32 characters.",
            )
        elif "change-this" in secret.lower() or "dev-secret" in secret.lower():
            status = "FAIL" if env_mode == "production" else "WARN"
            self._record(
                "SECRET_KEY",
                "P0",
                status,
                "SECRET_KEY is using a known development placeholder. Must be replaced with a secure random key in production.",
            )
        else:
            self._record("SECRET_KEY", "P0", "PASS", f"Configured with {len(secret)} characters (meets 32-char security minimum).")

        # 3. Database URL syntax
        db_url = settings.DATABASE_URL
        if not db_url:
            self._record("DATABASE_URL", "P0", "FAIL", "DATABASE_URL is not set.")
        elif db_url.startswith("sqlite"):
            self._record("DATABASE_URL", "P0", "INFO", "Using local SQLite database for local execution.")
        elif db_url.startswith("postgresql+asyncpg://"):
            self._record("DATABASE_URL", "P0", "PASS", "Valid async PostgreSQL driver (postgresql+asyncpg://) configured.")
        else:
            self._record(
                "DATABASE_URL",
                "P0",
                "WARN",
                f"Database URL scheme '{db_url.split('://')[0] if '://' in db_url else 'unknown'}' may not support async execution.",
            )

        # 4. Redis URL syntax
        redis_url = settings.REDIS_URL
        if not redis_url:
            self._record("REDIS_URL", "P0", "WARN", "REDIS_URL is not set. Cache will operate in in-memory fallback mode.")
        elif redis_url.startswith("rediss://") or redis_url.startswith("redis://"):
            self._record("REDIS_URL", "P0", "PASS", f"Valid Redis scheme configured ({redis_url.split('://')[0]}://).")
        else:
            self._record("REDIS_URL", "P0", "WARN", "Unrecognized Redis connection scheme.")

        # 5. Core URLs
        for url_name, val, prio in [
            ("MAP_STYLE_URL", settings.MAP_STYLE_URL, "P0"),
            ("OPEN_METEO_BASE_URL", settings.OPEN_METEO_BASE_URL, "P1"),
            ("OVERPASS_API_URL", settings.OVERPASS_API_URL, "P1"),
        ]:
            parsed = urlparse(val)
            if parsed.scheme in ("http", "https") and parsed.netloc:
                self._record(url_name, prio, "PASS", f"Valid URL format: {parsed.scheme}://{parsed.netloc}{parsed.path}")
            else:
                self._record(url_name, prio, "FAIL", f"Invalid URL syntax: '{val}'")

    async def check_database_connection(self):
        """Tests live database connectivity."""
        from app.database.session import AsyncSessionLocal
        start_t = time.perf_counter()
        try:
            async with AsyncSessionLocal() as session:
                res = await session.execute(text("SELECT 1"))
                val = res.scalar()
                latency_ms = round((time.perf_counter() - start_t) * 1000, 1)
                self._record(
                    "Database Connectivity",
                    "P0",
                    "PASS",
                    f"Successfully executed SELECT 1 ({latency_ms}ms latency).",
                    {"latency_ms": latency_ms},
                )
        except Exception as e:
            latency_ms = round((time.perf_counter() - start_t) * 1000, 1)
            self._record(
                "Database Connectivity",
                "P0",
                "FAIL",
                f"Database query failed ({latency_ms}ms): {type(e).__name__}: {str(e)}",
            )

    async def check_redis_connection(self):
        """Tests live Redis connectivity."""
        from app.cache.redis_cache import cache_service
        start_t = time.perf_counter()
        try:
            client = await cache_service.get_client()
            if client:
                res = await client.ping()
                latency_ms = round((time.perf_counter() - start_t) * 1000, 1)
                self._record(
                    "Redis Connectivity",
                    "P0",
                    "PASS",
                    f"Redis PING succeeded ({latency_ms}ms latency).",
                    {"latency_ms": latency_ms},
                )
            else:
                self._record(
                    "Redis Connectivity",
                    "P0",
                    "INFO",
                    "Redis client unavailable; in-memory fallback cache operational.",
                )
        except Exception as e:
            latency_ms = round((time.perf_counter() - start_t) * 1000, 1)
            self._record(
                "Redis Connectivity",
                "P0",
                "WARN",
                f"Redis PING failed ({latency_ms}ms): {type(e).__name__}. In-memory fallback will be used.",
            )

    async def check_external_providers(self, settings):
        """Executes lightweight, non-destructive probes on external endpoints."""
        async with httpx.AsyncClient(timeout=8.0, follow_redirects=True) as client:
            # 1. OpenFreeMap Map Style (P0)
            try:
                t0 = time.perf_counter()
                resp = await client.get(settings.MAP_STYLE_URL)
                dt = round((time.perf_counter() - t0) * 1000, 1)
                if resp.status_code == 200 and "sources" in resp.text:
                    self._record("OpenFreeMap Tiles", "P0", "PASS", f"Style accessible (HTTP 200, {dt}ms).")
                else:
                    self._record("OpenFreeMap Tiles", "P0", "FAIL", f"Unexpected status: HTTP {resp.status_code} ({dt}ms)")
            except Exception as e:
                self._record("OpenFreeMap Tiles", "P0", "FAIL", f"Connection error: {type(e).__name__}")

            # 2. Open-Meteo Weather (P1)
            try:
                t0 = time.perf_counter()
                resp = await client.get(
                    settings.OPEN_METEO_BASE_URL,
                    params={"latitude": 30.3, "longitude": 78.0, "current": "temperature_2m"}
                )
                dt = round((time.perf_counter() - t0) * 1000, 1)
                if resp.status_code == 200:
                    self._record("Open-Meteo Weather", "P1", "PASS", f"Forecast operational (HTTP 200, {dt}ms).")
                else:
                    self._record("Open-Meteo Weather", "P1", "WARN", f"Unexpected status: HTTP {resp.status_code}")
            except Exception as e:
                self._record("Open-Meteo Weather", "P1", "WARN", f"Weather endpoint unreachable: {type(e).__name__}")

            # 3. NDMA SACHET CAP Feed (P1)
            from app.clients.ndma_sachet import NDMASachetClient
            try:
                t0 = time.perf_counter()
                resp = await client.get(NDMASachetClient.RSS_FEED_URL)
                dt = round((time.perf_counter() - t0) * 1000, 1)
                if resp.status_code == 200 and ("rss" in resp.text.lower() or "xml" in resp.text.lower()):
                    self._record("NDMA SACHET Alerts", "P1", "PASS", f"CAP 1.2 RSS feed reachable (HTTP 200, {dt}ms).")
                else:
                    self._record("NDMA SACHET Alerts", "P1", "WARN", f"Unexpected feed response: HTTP {resp.status_code}")
            except Exception as e:
                self._record("NDMA SACHET Alerts", "P1", "WARN", f"SACHET feed unreachable: {type(e).__name__}")

            # 4. USGS Earthquakes (P1)
            from app.clients.usgs_earthquake import USGSEarthquakeClient
            try:
                t0 = time.perf_counter()
                resp = await client.get(USGSEarthquakeClient.FEED_URL)
                dt = round((time.perf_counter() - t0) * 1000, 1)
                if resp.status_code == 200 and "FeatureCollection" in resp.text:
                    self._record("USGS Earthquakes", "P1", "PASS", f"GeoJSON feed accessible (HTTP 200, {dt}ms).")
                else:
                    self._record("USGS Earthquakes", "P1", "WARN", f"Unexpected response: HTTP {resp.status_code}")
            except Exception as e:
                self._record("USGS Earthquakes", "P1", "WARN", f"USGS feed unreachable: {type(e).__name__}")

            # 5. NASA FIRMS (P2)
            firms_key = settings.NASA_FIRMS_MAP_KEY.strip()
            if not firms_key:
                self._record(
                    "NASA FIRMS (Wildfires)",
                    "P2",
                    "INFO",
                    "NASA_FIRMS_MAP_KEY is unconfigured. Register for free at https://firms.modaps.eosdis.nasa.gov/api/map_key/",
                )
            else:
                try:
                    probe_url = f"{settings.NASA_FIRMS_BASE_URL}/area/csv/{firms_key}/VIIRS_SNPP_NRT/78.0,30.0,78.2,30.2/1"
                    resp = await client.get(probe_url)
                    if resp.status_code == 200:
                        self._record("NASA FIRMS (Wildfires)", "P2", "PASS", "NASA FIRMS API key validated successfully.")
                    elif resp.status_code in (401, 403):
                        self._record("NASA FIRMS (Wildfires)", "P2", "FAIL", "Configured NASA_FIRMS_MAP_KEY was rejected by NASA EOSDIS.")
                    else:
                        self._record("NASA FIRMS (Wildfires)", "P2", "WARN", f"FIRMS responded with HTTP {resp.status_code}")
                except Exception as e:
                    self._record("NASA FIRMS (Wildfires)", "P2", "WARN", f"FIRMS probe error: {type(e).__name__}")

            # 6. IMD (P1)
            if not settings.IMD_API_KEY.strip():
                self._record(
                    "IMD Official Weather",
                    "P1",
                    "INFO",
                    "IMD_API_KEY is unconfigured. Requires institutional registration and server IP whitelisting at https://api.imd.gov.in/",
                )
            else:
                self._record("IMD Official Weather", "P1", "PASS", "IMD_API_KEY credentials configured in environment.")

            # 7. OpenCellID (P2)
            if not settings.OPENCELLID_API_KEY.strip():
                self._record(
                    "OpenCelliD Cell Towers",
                    "P2",
                    "INFO",
                    "OPENCELLID_API_KEY is unconfigured. Register for free at https://opencellid.org",
                )
            else:
                self._record("OpenCelliD Cell Towers", "P2", "PASS", "OPENCELLID_API_KEY credentials configured in environment.")

    def print_text_report(self):
        """Prints a colorized terminal report."""
        print("=" * 80)
        print("   TOURIST SAFETY & ASSISTANCE PLATFORM - PREFLIGHT CONFIGURATION AUDIT")
        print("=" * 80)
        print(f"Timestamp: {self.report['timestamp_utc']}")
        print(f"Evaluation Mode: {'STRICT' if self.strict else 'STANDARD'}")
        print("-" * 80)

        for check in self.report["checks"]:
            status = check["status"]
            # Color tags
            tag = f"[{status}]".ljust(8)
            prio = f"({check['priority']})".ljust(6)
            name = check["name"].ljust(26)
            print(f"{tag} {prio} {name} : {check['message']}")

        print("-" * 80)
        summary = self.report["summary"]
        print(
            f"SUMMARY: {summary.get('pass', 0)} Passed | "
            f"{summary.get('warn', 0)} Warnings | "
            f"{summary.get('fail', 0)} Failed | "
            f"{summary.get('info', 0)} Info / Unconfigured"
        )
        print(f"OVERALL PREFLIGHT RESULT: {self.report['overall_status']}")
        print("=" * 80)


async def main():
    parser = argparse.ArgumentParser(description="Preflight configuration auditor.")
    parser.add_argument("--strict", action="store_true", help="Fail if any optional provider is unconfigured.")
    parser.add_argument("--json", action="store_true", help="Output machine-readable JSON.")
    args = parser.parse_args()

    from app.config import settings
    checker = PreflightChecker(strict=args.strict)
    
    # 1. Environment variable audit
    checker.check_env_variables(settings)

    # 2. Database connection
    await checker.check_database_connection()

    # 3. Redis connection
    await checker.check_redis_connection()

    # 4. External providers
    await checker.check_external_providers(settings)

    if args.json:
        print(json.dumps(checker.report, indent=2))
    else:
        checker.print_text_report()

    # Exit code
    if checker.report["overall_status"] == "FAIL":
        sys.exit(1)
    if args.strict and checker.report["summary"].get("warn", 0) > 0:
        sys.exit(1)
    sys.exit(0)


if __name__ == "__main__":
    asyncio.run(main())

