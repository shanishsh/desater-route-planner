# Provider & Environment Setup Checklist

This document is the operational setup and deployment guide for the **Tourist Safety & Assistance Platform (Uttarakhand Trek Safety)**.

Providers and dependencies are organized by priority tier so that engineers and DevOps operators can bootstrap the core application first without being blocked by optional external services or institutional approval delays.

---

## Priority Tier Overview

- **P0 (Critical Foundation)**: Application cannot start, render maps, or authenticate users without these.
- **P1 (Core Route, Alert & Safety Pack)**: Powers the trekker route planning, official disaster alerts, nearby emergency facilities, and offline safety pack.
- **P2 (Optional Infrastructure & Early Warning)**: Enhances early warnings (satellite fires, cell tower locations) and automated push/SMS messaging.
- **P3 (Institutional Data & AI Enhancements)**: Departmental weather nowcasts, raw satellite modeling, and optional LLM summaries.

---

## Tier P0 — Critical Foundation (Mandatory for Launch)

### 1. Database (PostgreSQL with PostGIS)
- **Required Env**: `DATABASE_URL`
- **Format**: `postgresql+asyncpg://<USER>:<PASSWORD>@<HOST>:<PORT>/<DATABASE>`
- **Where to configure**:
  - Local: `backend/.env` (defaults to `sqlite+aiosqlite:///./tourist.db` for zero-setup local dev)
  - Render: Secret Environment Variable in Render Dashboard.
- **Verification**:
  ```powershell
  python -c "import asyncio; from app.database.session import AsyncSessionLocal; from sqlalchemy import text; asyncio.run(AsyncSessionLocal().execute(text('SELECT 1')))"
  ```
- **Fallback**: The backend can run on local SQLite for offline testing, but PostGIS is required for full spatial corridor indexing.

### 2. Cache & Rate Limiting (Redis / Upstash)
- **Required Env**: `REDIS_URL` (Optional: `UPSTASH_REDIS_REST_URL`, `UPSTASH_REDIS_REST_TOKEN`)
- **Format**: `rediss://default:<TOKEN>@<HOST>:6379` (TLS) or `redis://localhost:6379/0`
- **Where to configure**: `backend/.env` and Render secrets.
- **Verification**:
  ```powershell
  python -c "import asyncio; from app.cache.redis_cache import cache_service; asyncio.run(cache_service.get_client().ping())"
  ```
- **Fallback**: If Redis is unreachable, the system automatically falls back to an internal thread-safe in-memory cache without crashing.

### 3. Application Security & Token Signing
- **Required Env**: `SECRET_KEY`, `ADMIN_PASSWORD`, `ADMIN_API_KEY`
- **Requirements**:
  - `SECRET_KEY`: Minimum 32-byte cryptographically secure random string. Generate with:
    ```powershell
    python -c "import secrets; print(secrets.token_hex(32))"
    ```
  - `ADMIN_PASSWORD`: Strong password for administrative dashboard access.
  - `ADMIN_API_KEY`: Key for triggering administrative alert resets or high-priority refreshes.
- **Where to configure**: `backend/.env` and Render secrets.

### 4. Base Map Vector Tiles (OpenFreeMap)
- **Configured Setting**: `MAP_STYLE_URL="https://tiles.openfreemap.org/styles/liberty"`
- **Authentication**: **None required**. Completely free, public, and open-source vector tile service.
- **Where to configure**: Defined in `android/app/build.gradle.kts` and `backend/app/config.py`.
- **Verification**:
  ```powershell
  curl -I https://tiles.openfreemap.org/styles/liberty
  ```
  Expected: `HTTP/2 200` with `content-type: application/json`.

---

## Tier P1 — Core Route, Official Alerts & Safety Pack

### 1. Mountain Weather & Place Geocoding (Open-Meteo)
- **Configured Setting**: `OPEN_METEO_BASE_URL="https://api.open-meteo.com/v1/forecast"`
- **Authentication**: **None required** (Free tier for non-commercial research, CC BY 4.0).
- **Features Powered**:
  - Trailhead & destination settlement search (`/api/v1/places/search`).
  - Real-time weather, temperature, rain risk, and freezing level (`/api/v1/weather/forecast`).
- **Verification**:
  ```powershell
  curl -s "https://api.open-meteo.com/v1/forecast?latitude=30.3&longitude=78.0&current=temperature_2m"
  ```

### 2. OSRM Multimodal Routing Engine
- **Endpoints**: `https://router.project-osrm.org/route/v1/`
- **Authentication**: **None required** (Public OSM routing engine).
- **Features Powered**: Dynamic routing for driving, walking, and trekking with distance and elevation duration calculation.
- **Offline Fallback**: In offline or poor network scenarios, the backend and Android client automatically fall back to the built-in `VerifiedCorridorsRegistry`, containing pre-verified geometries for high-altitude Char Dham trails and Himalayan passes.

### 3. NDMA SACHET Disaster Alert Feeds (CAP 1.2)
- **Feed URL**: `https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml`
- **Authentication**: **None required**. Public CAP feed maintained by the National Disaster Management Authority.
- **Features Powered**: Ingestion of official flash flood, heavy rainfall, and landslide alerts directly mapped into trail corridors.
- **Verification**:
  ```powershell
  curl -sI https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml
  ```

### 4. USGS Himalayan Real-time Earthquakes
- **Feed URL**: `https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson`
- **Authentication**: **None required**. Public USGS seismic feed.
- **Features Powered**: Real-time earthquake filtering within the Himalayan geographic envelope (`28.7°N–31.5°N, 77.5°E–81.1°E`).

### 5. Uttarakhand PWD SRMD Highway Advisories
- **Portal URL**: `https://pwd.uk.gov.in/srmd-reports-1/`
- **Authentication**: **None required**.
- **Features Powered**: Ingests mountain road blockages, landslide closures, and official alternative emergency corridors.

### 6. Emergency Facilities & Forest Cover (OpenStreetMap Overpass)
- **Configured Setting**: `OVERPASS_API_URL="https://overpass-api.de/api/interpreter"`
- **Authentication**: None required (ODbL).
- **Reliability Note**: Public Overpass servers frequently experience heavy load or rate limiting.
- **Offline Protection**: If Overpass times out, `OSMAmenitiesClient` and `OSMForestClient` automatically fall back to the bundled offline datasets:
  - `backend/app/clients/fixtures/bundled_amenities.json` (Hospitals, clinics, police stations, fuel stations, shelters).
  - `backend/app/clients/fixtures/bundled_forest.json` (Himalayan forest and woodland polygons).

### 7. Community Trail Condition Reporting & Photo Uploads
- **Storage**: Local filesystem directory `uploads/` (ignored in git).
- **Sanitization**: All uploaded JPEG/PNG photos automatically have EXIF and GPS metadata stripped, are re-encoded to JPEG quality 85, and assigned a random UUID filename for trekker privacy.
- **Features Powered**: Community reports for fallen trees, landslides, washouts, and wildlife encounters.

---

## Tier P2 — Optional Infrastructure & Push Alerts

### 1. NASA FIRMS Active Wildfires (Satellite Thermal Anomalies)
- **Required Env**: `NASA_FIRMS_MAP_KEY`
- **Registration**:
  1. Go to: [https://firms.modaps.eosdis.nasa.gov/api/map_key/](https://firms.modaps.eosdis.nasa.gov/api/map_key/)
  2. Enter an email address and click "Get MAP Key".
  3. Copy the 32-character hex key sent to your email.
  4. Set `NASA_FIRMS_MAP_KEY=<your_key>` in `backend/.env` and Render secrets.
- **Verification**:
  ```powershell
  python -c "import asyncio, os; from app.clients.nasa_firms import nasa_firms_client; print(asyncio.run(nasa_firms_client.fetch_active_fires(30.0, 78.0, 30.5, 78.5)))"
  ```
- **Fallback**: If unconfigured, the app logs an informative diagnostic state and does not block route planning or other hazard checks.

### 2. OpenCellID Cell Tower Coordinates
- **Required Env**: `OPENCELLID_API_KEY`
- **Registration**:
  1. Register for free at: [https://opencellid.org/register.php](https://opencellid.org/register.php)
  2. Copy your API token from the account dashboard.
  3. Set `OPENCELLID_API_KEY=<your_token>` in `backend/.env`.
- **Fallback**: Trekker handsets measure live carrier cellular bars natively; missing OpenCellID tokens do not impair handset operation.

### 3. Firebase Cloud Messaging (FCM) Production Push
- **Required Env**: `FCM_PROVIDER=firebase`, `FIREBASE_CREDENTIALS_PATH=/path/to/service-account.json`
- **Setup**:
  1. Open Firebase Console: [https://console.firebase.google.com/](https://console.firebase.google.com/)
  2. Create/select project: Project Settings -> Service Accounts -> Generate New Private Key.
  3. Store JSON securely on backend and reference in `FIREBASE_CREDENTIALS_PATH`.
  4. Ensure `google-services.json` and service account files are in `.gitignore` (already configured).
- **Development Fallback**: `FCM_PROVIDER=mock` is the active default. Android devices receive disaster alerts through local foreground notifications, `DisasterAlarmReceiver`, and polling.

### 4. Automated SMS Gateway (Twilio)
- **Required Env**: `SMS_PROVIDER=twilio`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`
- **Setup**: Configure Twilio console with Indian SMS registration (DLT compliance) if sending automated SMS to Indian handsets.
- **Offline Trekker SOS Fallback**: In the Android app, tapping the SOS Emergency action directly opens the native device SMS messenger (`Intent.ACTION_SENDTO`) addressed to Uttarakhand Emergency Helpline **1070** with pre-filled GPS coordinates. This works 100% offline via basic 2G voice/SMS carrier towers without requiring Twilio backend credentials.

---

## Tier P3 — Institutional Feeds & Advanced Satellite/AI

### 1. India Meteorological Department (IMD) Warning & Nowcast
- **Required Env**: `IMD_API_KEY`, `IMD_API_SECRET`, `IMD_BASE_URL="https://api.imd.gov.in"`
- **Access Conditions**:
  - IMD requires an institutional account and a formal request through `https://api.imd.gov.in/`.
  - **Mandatory Static IP Whitelist**: IMD firewall blocks requests from dynamic cloud IPs. A static egress proxy (e.g. QuotaGuard on Render or dedicated VPS IP) must be provided to IMD during registration.
- **Fallback**: When IMD credentials are absent, Open-Meteo provides temperature, precipitation, and storm forecasts, while NDMA SACHET delivers official CAP disaster advisories.

### 2. Geological Survey of India (GSI BhuSanket) & DRDO DGRE Avalanche
- **Current Integration**: Handled through `backend/app/services/ai/bulletin_extractor.py`.
- **Process**: Official bulletin PDFs and text summaries published by GSI and DGRE are parsed into structured advisories with cited source snippets.
- **No API Key Required**: No public REST APIs exist; do not attempt to register API keys for these sources.

### 3. Optional LLM AI Provider (OpenAI / Gemini)
- **Configured Setting**: `AI_PROVIDER=deterministic` (Default)
- **Optional Env**: `GEMINI_API_KEY` or `OPENAI_API_KEY`
- **Behavior**: The Grounded Route Safety Assistant uses a deterministic template engine that requires **no API key**, guaranteeing zero hallucinations, instant response times, and compliance with mountain safety standards.

---

## Pre-Deployment Verification Runbook

Before deploying changes to staging or production, execute these automated checks:

### Step 1: Run Preflight Configuration Tool
```powershell
python backend/scripts/preflight_check.py
```
*Expected*: `OVERALL PREFLIGHT RESULT: PASS` with 0 failures.

### Step 2: Query Real-time Integration Health
```powershell
curl -s http://localhost:8000/api/v1/health/integrations | jq .summary
```
*Expected Output*:
```json
{
  "total_providers": 16,
  "healthy": 11,
  "configured_mock": 2,
  "degraded": 1,
  "not_configured": 2,
  "unavailable": 0
}
```

### Step 3: Run Backend Test Suite
```powershell
python -m pytest backend/tests -v
```
*Expected*: All 108 tests pass.

### Step 4: Verify Android Compilation
```powershell
& "C:\Users\shani\.gradle\wrapper\dists\gradle-9.6.0-bin\42k10rwplmzkhuboz9kdazi7s\gradle-9.6.0\bin\gradle.bat" -p android assembleDebug
```
*Expected*: `BUILD SUCCESSFUL`.

