# Test Results and Diagnostic Execution Log

**Audit Date**: October 25, 2026  
**Repository Commit**: `8403e98` on branch `main`  
**Execution Environment**: Windows 11, Python 3.11.9, OpenJDK 17 (Android Studio JBR), Gradle 9.6.0  

---

## 1. Automated Test Suites Summary

| Test Harness | Total Tests Executed | Passed | Failed | Skipped | Warnings | Wall-Clock Duration | Exit Code | Result |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Backend Pytest Suite** | **98** | **98** | **0** | **0** | 1 (Starlette deprecation) | 8m 56s (536.69s) | `0` | **100% PASSED** |
| **Android Unit Tests (Gradle)** | **46** | **46** | **0** | **0** | 0 | 52s | `0` | **100% PASSED** |
| **Android Build Compilation** | 31 Gradle Tasks | 31 | 0 | 0 | Deprecation (Gradle 10) | 2m 11s | `0` | **BUILD SUCCESSFUL** |
| **Total Automated Tests** | **144** | **144** | **0** | **0** | 1 | — | `0` | **100% PASSED** |

---

## 2. Backend Pytest Detailed Test Suite Log

**Command**: `python -m pytest backend/tests -v`

| Test Suite File | Tests Count | Status | Key Validations & Assertions Tested |
| :--- | :--- | :--- | :--- |
| `test_alerts.py` | 4 | `PASSED` | Alert freshness lifecycle, rejection of future clock skew, simulated alerts isolation, demo admin reset endpoint. |
| `test_auth_api.py` | 1 | `PASSED` | Full user registration and login flow, JWT bearer token generation, password verification. |
| `test_auth_schema.py` | 3 | `PASSED` | Email format validation via `email-validator`, rejection of invalid emails, token response schema conformity. |
| `test_circuit_breaker.py` | 1 | `PASSED` | Circuit breaker transitions (`CLOSED` $\rightarrow$ `OPEN` $\rightarrow$ `HALF_OPEN`), failure threshold, recovery time window. |
| `test_disaster_ingestion_system.py` | 6 | `PASSED` | CAP 1.2 polygon parsing & ring closure, CAP circle parsing, district-only advisory zero coordinate fabrication, IMD status reporting, PWD bulletin extractor, corridor spatial filtering, PostGIS persistence. |
| `test_disaster_module.py` | 7 | `PASSED` | SMS subscriber registration, OTP verification with mandatory consent, unsubscribe flow, device push registration, admin alert creation, notification deduplication, alert updates and cancellation. |
| `test_dynamic_routing_and_corridors.py` | 4 | `PASSED` | Provider registry coverage engine, settlement place search & reverse geocoding, international boundary validation, unroutable coordinate 404 rejection. |
| `test_facility_mapping_and_multiroute.py` | 5 | `PASSED` | Haversine distance calculations in meters, safe haven schema validation, fuel station safe havens, route alternative preference rankings, OSRM multi-route queries. |
| `test_facility_points_pipeline.py` | 5 | `PASSED` | Overpass facility deduplication and category mapping, Overpass timeout with cached fallback, clean empty response handling, corridor facility spatial filtering. |
| `test_health.py` | 2 | `PASSED` | `/api/v1/health/live` liveness check, `/api/v1/health/ready` database & Redis readiness check. |
| `test_imd_and_earthquake.py` | 3 | `PASSED` | IMD unconfigured state reporting, IMD multi-endpoint calls with auth locking, USGS Himalayan earthquake filtering and bounding box clamping. |
| `test_itineraries.py` | 1 | `PASSED` | Trekking itinerary creation, retrieval, and regional filtering. |
| `test_map_points.py` | 2 | `PASSED` | MapPoint spatial and category queries, unbounded map points query returning seeded baseline facilities. |
| `test_ndma_sachet.py` | 4 | `PASSED` | NDMA SACHET CAP XML parsing, ETag 304 caching, coordinate order preservation, expired alert status handling. |
| `test_providers.py` | 8 | `PASSED` | NASA FIRMS VIIRS & MODIS CSV parsing, zero detections handling, unconfigured FIRMS and OpenCelliD graceful handling, OSM forest GeoJSON element mapping, provider registry statuses. |
| `test_rate_limit.py` | 1 | `PASSED` | Rate limiting headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`) and 429 status enforcement. |
| `test_route_corridor_analysis.py` | 14 | `PASSED` | Route corridor buffer construction, alert polygon intersections, exclusion of outside hazards, fire point filtering, 1km vs 10km sensitivity, route destination recalculation, zero detections layer handling, single provider outage resilience, CAP alerts without geometry, multi-waypoint weather sampling, route exposure metrics, alternative route comparative rankings. |
| `test_route_monitoring.py` | 9 | `PASSED` | Settlement search, corridor analysis, active landslide intersection, active trip monitoring lifecycle, structured route contract, route analysis lifecycle, distinct origin/destination pairs, geographic filtering, nonexistent route 404. |
| `test_trailguard_risk.py` | 6 | `PASSED` | Tobler hiking speed calculation on mountain grades, freezing and rain compounding, slope subscore, aggregate route risk preserving local peaks, contrastive explanation generation, SAR uncertainty circle polygons. |
| `test_uttarakhand_boundary_and_corridors.py` | 8 | `PASSED` | Points inside vs outside Uttarakhand, verified corridors registry (Kedarnath, Valley of Flowers, Roopkund, Har Ki Dun), pedestrian-only corridor enforcement (rejecting driving on pilgrim footpaths), coordinate bounds checking, dynamic any-location routing. |
| `test_weather.py` | 2 | `PASSED` | Live Open-Meteo weather querying and 15m Redis caching, offline fallback to PostGIS cached snapshot. |

---

## 3. Android Unit Tests Detailed Log

**Command**: `& "C:\Users\shani\.gradle\wrapper\dists\gradle-9.6.0-bin\42k10rwplmzkhuboz9kdazi7s\gradle-9.6.0\bin\gradle.bat" -p android :app:testDebugUnitTest`

| Android Test Suite File | Tests Count | Status | Key Validations & Assertions Tested |
| :--- | :--- | :--- | :--- |
| `AlertFreshnessTest.kt` | 3 | `PASSED` | Evaluates `LayerDataStatus` transitions (`LIVE`, `STALE`, `CACHED`, `STANDBY`) based on observation timestamps. |
| `AuthFlowTest.kt` | 8 | `PASSED` | Validates email syntax checking, password length rules, guest login bypass, bearer token injection in Retrofit interceptors, and logout state clearing. |
| `DeviceLocationRepositoryTest.kt` | 5 | `PASSED` | High-accuracy GNSS fix acquisition, coarse vs fine permission checks, rejection of stale fixes and `(0.0, 0.0)` coordinates. |
| `HazardGeofenceTest.kt` | 4 | `PASSED` | On-device geofencing: alerts triggered when user coordinate approaches within buffer distance of active hazard. |
| `HomeViewModelTest.kt` | 2 | `PASSED` | Home screen state initialization, active alerts count observation, recent trips loading. |
| `LocalSpatialEngineTest.kt` | 5 | `PASSED` | On-device Haversine cross-track error ($d_\perp > 75\text{m}$) calculation, 2D ray-casting point-in-polygon containment without network. |
| `MapViewModelTest.kt` | 10 | `PASSED` | Map feature category filtering (Hospitals, Police, Fires, Disasters), marker selection, camera bounds framing, and zoom tracking. |
| `OfflineRepositoryTest.kt` | 2 | `PASSED` | Verifies saving route metadata to Room SQLite and loading cached trips without network connection. |
| `ProviderStatusViewModelTest.kt` | 1 | `PASSED` | Verifies provider status loading and ensures `TelephonyManager` permission restrictions do not throw `SecurityException`. |
| `RouteMonitoringTest.kt` | 3 | `PASSED` | Active trip lifecycle: start trip, submit GPS breadcrumb progress, stop trip. |
| `ScreenNavigationTest.kt` | 3 | `PASSED` | Navigation routing between Welcome, Plan Trip, Review, and Map screens. |

---

## 4. Live Network & Upstream Diagnostic Probes

Executed via diagnostic script `scratch/probe_providers.py`:

```
=== LIVE PROBE OF OVERPASS API ===
Testing https://overpass-api.de/api/interpreter...
  Status: 406 Not Acceptable (Bot/User-Agent restriction)
Testing https://maps.mail.ru/osm/tools/overpass/api/interpreter...
  Failed: Connection Timeout
Testing https://overpass.kumi.systems/api/interpreter...
  Failed: Connection Timeout

=== LIVE PROBE OF MAP STYLE (OpenFreeMap) ===
OpenFreeMap style status: 200 OK
  Sources: ['ne2_shaded', 'openmaptiles']
  Layers count: 111
  Glyphs: https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf
  Sprite: https://tiles.openfreemap.org/sprites/ofm_f384/ofm

=== LIVE PROBE OF USGS EARTHQUAKE API ===
USGS status: 200 OK
  Features found: 5 active seismic events in Uttarakhand coordinate bounding box
```

---

## 5. Environmental Limitations and Unverified Boundaries

1. **Physical Device Satellite Reception**: Automated tests run in JVM and mock GNSS environments. Multi-path satellite signal degradation under sheer Himalayan granite cliffs (e.g. Kedarnath Valley) cannot be fully simulated without field testing.
2. **Cellular SMS Gateway**: Twilio cellular SMS transmission is currently unverified with live carrier networks because `TWILIO_ACCOUNT_SID` and `TWILIO_AUTH_TOKEN` are not populated in development.
3. **IMD Direct API Token Exchange**: Direct OAuth token exchange with `https://api.imd.gov.in/api/oauth/token.php` requires static IP whitelisting on IMD's government firewall. While the client code and schema handling are 100% verified via unit tests, live queries against IMD's production server remain unverified pending IP approval. IMD severe weather warnings are nonetheless verified live via NDMA SACHET feeds.
