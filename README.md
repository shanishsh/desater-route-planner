# Tourist Safety & Assistance Platform

A production-grade, offline-first mobile safety and navigation platform designed for wilderness, trek, and alpine tourists. The system combines a native **Android application** (Kotlin, Jetpack Compose Material 3, MapLibre Native SDK, Room offline database) and a high-performance **FastAPI modular monolith backend** (Python 3.11, PostgreSQL with PostGIS, Redis, Alembic, ARQ background worker, Open-Meteo weather integration, and a dynamic alert freshness engine).

---

## 1. Architectural Highlights

- **Native Android Client**: Written in 100% Kotlin using Jetpack Compose Material 3, MVVM architecture, Hilt dependency injection, Coroutines and Flow, Room as local offline source of truth, Retrofit/OkHttp, and WorkManager background sync.
- **Map Engine**: MapLibre Native Android SDK utilizing the free **OpenFreeMap Liberty vector tile style** (`https://tiles.openfreemap.org/styles/liberty`). Features route polylines, clickable waypoint clusters, assistance markers, and an offline vector schematic fallback.
- **FastAPI Modular Monolith**: Typed Pydantic v2 schemas, SQLAlchemy 2.0 async sessions, PostgreSQL with PostGIS for spatial bounding box filtering, Redis cache-aside with stampede protection, and graceful degraded fallback.
- **Live Weather Integration**: Direct backend proxy to **Open-Meteo API** (free for non-commercial safety use) with circuit-breaker isolation, Redis caching, and PostgreSQL snapshot fallback.
- **Simulated Alerts Engine**: Seeded repeatable demonstration alerts with dynamically computed freshness flags (`is_stale`), ensuring deterministic presentation at demo reset.
- **Offline-First Resilience**: Room local database is the single source of truth for the mobile UI. All routes, waypoints, and cached alerts remain 100% accessible offline without network connectivity.

---

## 2. Seeded Simulated Alerts Scenarios

At demo reset ($T_0$), three simulated alert scenarios are generated with publication timestamps relative to current time:

| Alert Scenario | Category | Source Age | Freshness Limit | Status ($T_0$) | Simulation Flag |
|---|---|---:|---:|:---:|:---:|
| **High Rain-Risk Advisory on Upper Ridge** | Weather | 5 minutes | 60 minutes | **FRESH** | `[SIMULATED]` |
| **Temporary Trail Obstruction (Fallen Timber)** | Hazard | 75 minutes | 60 minutes | **STALE** | `[SIMULATED]` |
| **Shuttle Bus Delay Near Trailhead Exit** | Transport | 180 minutes | 60 minutes | **STALE** | `[SIMULATED]` |

> Freshness is dynamically computed via `(now - source_published_at) > freshness_threshold_seconds`. A previously stored boolean is never treated as permanently valid.

---

## 3. Quick Start & Execution Runbook

### Prerequisites
- Python 3.11+
- Docker & Docker Compose (or local PostgreSQL & Redis)
- Android Studio with Android SDK 35/36 installed

### Option A: Running with Docker Compose (Recommended)

1. **Start Infrastructure & Services**:
   ```bash
   docker compose up -d --build
   ```
   This spins up:
   - `tourist_safety_db` (PostgreSQL 16 + PostGIS) on port `5432`
   - `tourist_safety_redis` (Redis 7) on port `6379`
   - `tourist_safety_api` (FastAPI backend) on port `8000`
   - `tourist_safety_worker` (ARQ background worker)

2. **Seed Initial Demo Data**:
   ```bash
   docker compose exec api python scripts/seed_demo_data.py
   ```

3. **Reset Alerts to $T_0$ Anytime**:
   ```bash
   docker compose exec api python scripts/reset_demo_alerts.py
   ```

---

### Option B: Local Standalone Execution (Without Docker)

1. **Install Python Dependencies**:
   ```bash
   cd backend
   python -m pip install -r requirements.txt
   ```

2. **Seed Demonstration Data**:
   ```bash
   $env:DATABASE_URL="sqlite+aiosqlite:///./tourist_safety.db"
   python scripts/seed_demo_data.py
   ```

3. **Run Backend API Server**:
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```

4. **Verify Health Endpoints**:
   - Liveness: `http://localhost:8000/api/v1/health/live`
   - Readiness: `http://localhost:8000/api/v1/health/ready`
   - Interactive Swagger API Docs: `http://localhost:8000/docs`
   - Prometheus Metrics: `http://localhost:8000/metrics`

---

## 4. Running the Android Mobile Application

1. Open the `android/` directory in **Android Studio**.
2. **Network Base URL Configuration**:
   - If running in the standard Android Emulator, `http://10.0.2.2:8000/api/v1/` (configured in `app/build.gradle.kts`) automatically reaches `localhost:8000` on the developer host machine.
   - If deploying to a physical Android device over Wi-Fi, update `BACKEND_BASE_URL` in `app/build.gradle.kts` to your developer machine's LAN IP (e.g. `http://192.168.1.50:8000/api/v1/`).
3. Build and run on emulator or connected device:
   ```powershell
   cd android
   .\gradlew assembleDebug
   ```

### Step-by-Step Demonstration Workflow

1. **Dashboard Overview (Home)**:
   - Notice the selected **Rohtang Valley Ridge Trail** itinerary with distance (14.5 km), duration (~5.5 hrs), and difficulty.
   - Observe the live weather card fetched from Open-Meteo.
   - Observe the active safety advisories with their computed freshness pills (`FRESH • 5m ago`, `STALE • 75m ago`, `STALE • 180m ago`).
2. **Interactive Map Exploration (Map)**:
   - Switch to the **Explore Map** tab.
   - The map loads the vector basemap using OpenFreeMap Liberty style.
   - Route polyline is overlaid across the trail segments.
   - Tap any assistance marker (Emergency 112, District Civil Hospital, Tourist Police Post) to open the interactive `PointDetailBottomSheet`.
3. **Emergency Assistance (Emergency)**:
   - Switch to the **Emergency** tab.
   - Tap **Dial National Emergency (112)** to open the phone dialler with official ERSS contact.
   - Notice the on-device privacy guarantee disclosure.
4. **Offline Capability Testing (Saved / Airplane Mode)**:
   - Navigate to **Saved Offline** and save the active trail.
   - Put your Android emulator or device into **Airplane Mode** (disable network).
   - Return to Home and Map:
     - Notice the prominent dark **Offline Banner** at the top.
     - The trail route geometry, waypoints, and cached alerts remain 100% available without network connection.
     - Switch to the vector schematic view on the Map tab to view the trail coordinates even without online basemap tiles.
   - Turn Airplane Mode back off: The app seamlessly synchronizes updates.

---

## 5. Automated Tests

### Backend Test Suite (Pytest)
```bash
cd backend
python -m pytest -v
```
**Test Coverage Includes:**
- Liveness and readiness dependency health checks (`test_health.py`)
- Alert dynamic freshness engine & future clock skew validation (`test_alerts.py`)
- Verification of all 3 seeded simulated alerts (`test_alerts.py`)
- Weather API retrieval, Redis caching, and PostgreSQL snapshot fallback (`test_weather.py`)
- Itinerary retrieval, GeoJSON, and segment relationships (`test_itineraries.py`)
- MapPoint spatial bounding box (`bbox`) and category filtering (`test_map_points.py`)
- Rate limiting middleware headers and limits (`test_rate_limit.py`)
- Circuit breaker state machine transitions (`test_circuit_breaker.py`)

### Android Unit Test Suite (JUnit & MockK)
```powershell
cd android
.\gradlew testDebugUnitTest
```
**Test Coverage Includes:**
- `AlertFreshnessTest`: Injected clock verification of Fresh vs Stale determination.
- `OfflineRepositoryTest`: Room offline-first persistence and network error fallback.
- `HomeViewModelTest`: StateFlow UI state emission and itinerary/alert bindings.

---

## 6. Troubleshooting Guide

- **Android Emulator Cannot Reach Backend**:
  - Inside the Android emulator, `localhost` refers to the emulator itself. Always use `http://10.0.2.2:8000/api/v1/`. Cleartext HTTP is permitted for `10.0.2.2` via `res/xml/network_security_config.xml`.
- **PostGIS or Docker Daemon Stopped**:
  - If Docker Desktop is not running, the backend seamlessly supports local development with `DATABASE_URL=sqlite+aiosqlite:///./tourist.db` and operates Redis in degraded memory cache mode without crashing.
- **Port 5432 or 8000 In Use**:
  - Stop conflicting local services or configure custom ports in `.env` and `docker-compose.yml`.
- **OpenFreeMap Tile Rate Limits**:
  - OpenFreeMap enforces a maximum of 3,000 tiles (or 50 MB) per offline download with a limit of 4 concurrent requests. The app adheres to these limits and uses the built-in offline vector schematic fallback when tiles are not cached.

---

## 7. Data Attribution and Licenses

- **OpenFreeMap**: Vector tile rendering powered by OpenStreetMap data under the Open Database License (ODbL).
- **Open-Meteo**: Weather forecast data provided under Creative Commons Attribution 4.0 International (CC BY 4.0) for non-commercial public safety use.
- **Emergency Directory**: Sourced from the official National Emergency Response Support System (ERSS 112) guidelines.

