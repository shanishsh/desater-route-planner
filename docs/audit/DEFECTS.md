# TrailGuard: Defect Log & Remediation Report

This document records the defects identified during the production audit, detailing their severity, root cause, affected files, and implemented remediation.

---

### DEFECT-001: Room Database Schema Mismatch Crash on App Startup
- **Severity**: `CRITICAL`
- **Root Cause**: `PlannedRouteEntity` had been modified to include the `routeGeoJson` column, but `AppDatabase.kt` was left at `version = 4`. When the Android app initialized on launch, Room compared its compiled entity identity hash with the existing SQLite metadata table. Finding a mismatch, Room threw an unhandled fatal `IllegalStateException: Room cannot verify the data integrity...`, terminating the application process immediately.
- **Affected Files**: `android/app/src/main/java/com/touristsafety/app/core/database/AppDatabase.kt`
- **Remediation**: Incremented database version to `5` (and subsequently `6` for TrailGuard elevation and safe haven fields), triggering `.fallbackToDestructiveMigration()` to safely recreate and verify the schema on launch.
- **Verification**: App cold startup verified with `./gradlew assembleDebug` and test execution without crash.

---

### DEFECT-002: WorkManager Hilt Dual-Initialization Collision
- **Severity**: `CRITICAL`
- **Root Cause**: `TouristSafetyApp.kt` implemented `Configuration.Provider` to provide custom `@HiltWorker` factory injection. However, Android's default `InitializationProvider` with `WorkManagerInitializer` remained active in the merged manifest, causing WorkManager to auto-initialize before Hilt injected the worker factory, causing crashes when `syncManager.schedulePeriodicSync()` was called in `MainActivity.onCreate`.
- **Affected Files**: 
  - `android/app/src/main/AndroidManifest.xml`
  - `android/app/src/main/java/com/touristsafety/app/TouristSafetyApp.kt`
  - `android/app/src/main/java/com/touristsafety/app/MainActivity.kt`
- **Remediation**:
  1. Disabled default `WorkManagerInitializer` using `tools:node="remove"` under `InitializationProvider` in `AndroidManifest.xml`.
  2. Guarded `TouristSafetyApp.workManagerConfiguration` with `if (::workerFactory.isInitialized)`.
  3. Wrapped background sync scheduling in `MainActivity.kt` in a safe try-catch logging block.
- **Verification**: WorkManager runs reliably with zero startup exceptions.

---

### DEFECT-003: Render Deployment Failure (`ModuleNotFoundError: No module named 'email_validator'`)
- **Severity**: `CRITICAL`
- **Root Cause**: Render deployment was targeting commit `b811adb`, which predated the addition of `email-validator` to `requirements.txt`. Pydantic v2 raises an `ImportError` when loading `app/schemas/auth.py` if `email-validator` is missing.
- **Affected Files**: 
  - `backend/requirements.txt`
  - `backend/Dockerfile`
- **Remediation**:
  1. Explicitly pinned `email-validator>=2.0.0,<3.0.0` in `requirements.txt`.
  2. Verified import with `from app.schemas.auth import UserLogin` in clean Python environment.
  3. Ensured Dockerfile runs `pip install --no-cache-dir -r requirements.txt` before code copy.
- **Verification**: `python -c "import email_validator; from app.main import app; print('SUCCESS')"` returns exit code 0.

---

### DEFECT-004: Live Credentials Tracked in `.env.example`
- **Severity**: `HIGH (SECURITY)`
- **Root Cause**: An active Upstash Redis TLS connection string and REST token had been inadvertently committed to the root `.env.example` file.
- **Affected Files**: `.env.example`
- **Remediation**:
  1. Immediately sanitized `.env.example` replacing live URLs and tokens with generic documentation placeholders (`your-upstash-token`, `your-redis-url`).
  2. Verified that actual runtime secrets reside exclusively in untracked local `.env` and deployment environment variables.
- **Verification**: `git diff` and `git ls-files` confirm clean placeholders in tracked files.

---

### DEFECT-005: Hazard Score Dilution in Corridor Risk Analysis
- **Severity**: `HIGH`
- **Root Cause**: When an official disaster alert (such as a severe landslide) was detected on a route segment with calm weather, the weighted aggregation formula ($0.30 \text{weather} + 0.25 \text{slope} + 0.25 \text{hazard} + 0.20 \text{fire}$) resulted in $0.25 \times 75.0 = 18.75$, diluting the localized threat into a generic `SAFE` status.
- **Affected Files**: `backend/app/services/risk/trailguard_risk_engine.py`
- **Remediation**: Implemented a **Dominant Hazard Override** ensuring that active official disaster alerts ($\ge 70.0$) and proximate wildfires ($\le 1.2\text{ km}$) dominate local segment risk and are never masked by sunny weather.
- **Verification**: `test_plan_route_with_disaster_alert_intersection` in `tests/test_route_monitoring.py` passed with `overall_safety_status: HIGH_RISK`.

---

### DEFECT-006: MapLibre Native Lifecycle Uninitialized Context
- **Severity**: `MEDIUM`
- **Root Cause**: Jetpack Compose `AndroidView` wrapping MapLibre's `MapView` lacked explicit forwarding of `onCreate(null)`, `onStart()`, and `onResume()`, causing native OpenGL ES context crashes on devices with strict surface lifecycles.
- **Affected Files**: `android/app/src/main/java/com/touristsafety/app/feature/map/MapScreen.kt`
- **Remediation**:
  1. Forwarded explicit native lifecycle methods in `factory` and `onRelease`.
  2. Wrapped native `MapView` initialization in try-catch with graceful automatic fallback to `OfflineVectorCanvasView` if native GL context allocation fails.
- **Verification**: Map renders smoothly in both vector surface and canvas fallback modes.

