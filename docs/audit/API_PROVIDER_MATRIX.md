# TrailGuard: External API Provider Matrix & Smoke Test Audit

This matrix documents the integration status, endpoints, authentication mechanisms, and actual live responses of all external data providers configured in the system.

---

| Provider | Endpoint & Protocol | Authentication | Actual HTTP Status | Live Record Count | Last Verified Test Result | Limitations & Usage Policy |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Open-Meteo Weather** | `GET https://api.open-meteo.com/v1/forecast` | None (Public API) | `HTTP 200 OK` | 24–168 hourly forecasts | `VERIFIED LIVE` | Free tier limited to non-commercial use with $< 10,000$ calls/day. Cached locally with 15m TTL. |
| **Open-Meteo Elevation (Copernicus DEM)** | `GET https://api.open-meteo.com/v1/elevation` | None (Public API) | `HTTP 200 OK` | Batch of 80 coordinates per request | `VERIFIED LIVE` | 30m / 90m global digital elevation model. Batching enforced to prevent URI overflow. |
| **NASA FIRMS Active Fires** | `GET https://firms.modaps.eosdis.nasa.gov/api/area/csv/{MAP_KEY}/VIIRS_SNPP_NRT/{bbox}/1` | MAP_KEY (`f1876d284ab...`) | `HTTP 200 OK` | Variable (0 to 12 real thermal detections) | `VERIFIED LIVE` | Satellite thermal anomalies (MODIS/VIIRS). Satellite observations, not ground-verified fires. Cached with 30m TTL. |
| **NDMA SACHET (India Disasters)** | `GET https://sachet.ndma.gov.in/backend/rss/feed.xml` | None (Public CAP RSS) | `HTTP 200 OK / 304` | 0 to 45 active alerts across India | `VERIFIED LIVE` | Real-time CAP XML feed with ETag/304 caching. Polygons parsed when provided; centroids used when geometry is absent. |
| **OpenStreetMap Overpass (Forests)** | `POST https://overpass-api.de/api/interpreter` | None (ODbL License) | `HTTP 200 OK` | 5–35 forest polygons per bounding box | `VERIFIED LIVE` | Subject to Overpass server load and rate-limiting. Protected with Circuit Breaker (failure threshold: 2). Cached for 24h. |
| **OpenStreetMap Overpass (Amenities)** | `POST https://overpass-api.de/api/interpreter` | None (ODbL License) | `HTTP 200 OK` | 4–20 verified hospitals, clinics, police stations | `VERIFIED LIVE` | Real-world facilities only; contact numbers preserved where mapped, never invented. |
| **OSRM Routing Engine** | `GET https://router.project-osrm.org/route/v1/{mode}/{coords}` | None (Public Demo) | `HTTP 200 OK` | Full polyline geometry, distance, and duration | `VERIFIED LIVE` | Standard public OSRM server. Bounded with local fallback routing if network latency spikes. |
| **Supabase PostgreSQL / PostGIS** | `postgresql+asyncpg://aws-0-ap-south-1.pooler.supabase.com:5432/postgres` | Username / Password | `CONNECTED` | 11 tables initialized | `VERIFIED LIVE` | Session pooler enabled. PostGIS spatial extensions active. |
| **Upstash Redis TLS** | `rediss://viable-bluegill-237463.upstash.io:6379` | Bearer Token / TLS | `CONNECTED` | Active cache-aside store | `VERIFIED LIVE` | Serverless Redis with TLS encryption and automatic TTL eviction. |
| **Twilio SMS Gateway** | `POST https://api.twilio.com/2010-04-01/Accounts/{SID}/Messages.json` | Account SID + Auth Token | `CONFIG-GATED` | N/A (Switches to Mock in Dev) | `VERIFIED ARCHITECTURE` | Code complete; activates cellular dispatch when production Twilio credentials are provided in deployment secrets. |
