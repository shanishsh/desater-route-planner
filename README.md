# TrailGuard: AI-Powered Mountain Safety, Dynamic Risk-Aware Navigation & Offline Rescue Intelligence

TrailGuard is an offline-first, map-centric mountain safety and intelligent evacuation system built for alpine trekkers, search & rescue teams, and wilderness expeditions.

Rather than acting as a generic tourist tracking dashboard, TrailGuard solves the difficult, life-critical challenges of high-mountain environments:
1. **Dynamic Risk-Aware Route Planning**: Replaces blind shortest-path optimization with multi-factor risk scoring factoring in slope steepness, elevation, freezing hypothermia thresholds, and real-time weather compounding.
2. **Terrain-Aware Hazard Prediction**: Uses 30m digital elevation models (DEM) and Tobler's Hiking Velocity Function to forecast conditions when the trekker will actually arrive at high waypoints.
3. **Intelligent Offline Safety**: Executes 100% on-device cross-track deviation detection ($d_\perp > 75\text{m}$) and 2D ray-casting point-in-polygon hazard checks with zero cellular network access.
4. **Search & Rescue (SAR) & Check-In Escalation**: Tracks periodic check-ins, calculates dead-reckoning uncertainty search ellipses ($R = R_{\text{GPS}} + v_{\max} \cdot \Delta t$), and exports standardized RFC 7946 SAR GeoJSON incident packages.

---

## Production Audit & Verification Documentation

Complete, verified engineering documentation produced during the full audit is available in `docs/audit/`:

- [Feature Inventory & Status](docs/audit/FEATURE_INVENTORY.md) — 27 feature areas classified and verified.
- [Defect Log & Remediation Report](docs/audit/DEFECTS.md) — Identified defects, root causes, and verified fixes.
- [External API Provider Matrix](docs/audit/API_PROVIDER_MATRIX.md) — Endpoints, live HTTP statuses, and licensing.
- [Test Execution Record](docs/audit/TEST_RESULTS.md) — Automated test results (42 backend tests passed, Android unit tests passed).
- [Deployment Checklist](docs/audit/DEPLOYMENT_CHECKLIST.md) — Environment variables, Docker, and Render configuration.
- [Known Limitations & Operational Boundaries](docs/audit/KNOWN_LIMITATIONS.md) — External provider and hardware constraints.

---

## Architectural Highlights

- **Field Unit (Kotlin Native Android)**:
  - Jetpack Compose Material 3 map-first UI.
  - MapLibre Native Android SDK with OpenFreeMap Liberty tiles and offline vector canvas fallback.
  - Room database (version 6) as the offline source of truth with complete TrailPack storage.
  - FusedLocationProviderClient with real GPS acquisition, accuracy reporting, and stale-fix detection.
  - Background WorkManager synchronization with network constraints.
- **Geospatial Intelligence Hub (Python FastAPI + PostGIS + Redis)**:
  - Asynchronous FastAPI monolith with typed Pydantic v2 schemas.
  - PostgreSQL with PostGIS for spatial queries, corridor buffers, and alert intersection.
  - Upstash Redis TLS for cache-aside storage with source-specific TTLs.
  - Live API integration: Open-Meteo (hourly weather & Copernicus DEM elevation), NASA FIRMS (VIIRS/MODIS thermal hotspots), OpenStreetMap Overpass (forest polygons and emergency facilities), and NDMA SACHET (CAP XML advisories).
  - Search & Rescue API (`/api/v1/sar`) for field check-ins and emergency incident export.

---

## Quick Start Runbook

### 1. Backend Local Execution
```bash
cd backend
python -m venv .venv
source .venv/bin/activate  # Or .venv\Scripts\Activate.ps1 on Windows
pip install -r requirements.txt

# Run migrations
python -m alembic upgrade head

# Start FastAPI server
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 2. Running Automated Tests
```bash
# Run full backend test suite (42 tests)
cd backend
python -m pytest tests/ -v

# Run Android unit tests
gradle.bat -p android testDebugUnitTest
```

### 3. Compiling Android Debug APK
```bash
gradle.bat -p android assembleDebug
# Produced APK: android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Verified Live Endpoints

- **Liveness Probe**: `GET /api/v1/health/live`
- **Readiness Probe**: `GET /api/v1/health/ready` (Verifies DB and Redis connectivity)
- **Provider Status**: `GET /api/v1/providers/status` (Returns live statuses of 7 providers)
- **TrailGuard Route Plan**: `POST /api/v1/routes/plan` (Returns 30m DEM elevation profile, Tobler speeds, multi-factor risk, and contrastive explanation)
- **Trekker Check-In**: `POST /api/v1/sar/check-in`
- **Emergency SOS**: `POST /api/v1/sar/sos`
- **SAR GeoJSON Export**: `GET /api/v1/sar/incidents/{incident_id}/export` (RFC 7946 standardized search perimeter)
