# TrailGuard: Known Limitations & Operational Constraints

This document explicitly details known limitations, external provider constraints, and boundaries of the current implementation.

---

## 1. External Provider Constraints

1. **NASA FIRMS Fire Hotspots**:
   - **Nature**: Detections reflect satellite radiometric thermal anomalies (MODIS and VIIRS NOAA-20/Suomi-NPP), not ground-confirmed forest fires.
   - **Latency**: Near Real-Time (NRT) satellite passes occur every 3–6 hours. Detections represent recent observations, not continuous real-time video surveillance.
   - **Resolution**: Pixel footprint is 375m (VIIRS) or 1km (MODIS). A thermal anomaly indicates excessive heat within that bounding pixel.

2. **OpenStreetMap Overpass Rate Limits**:
   - The public Overpass interpreter (`overpass-api.de`) enforces query rate limits based on server load.
   - To protect app availability, TrailGuard employs a Circuit Breaker (failure threshold: 2, 30s recovery) and 24-hour cache TTL for forest polygons and emergency amenities.

3. **Forest Survey of India (FSI) Machine-Readable Feeds**:
   - FSI currently publishes web map view portals and periodic PDF/bulletin reports rather than a high-availability open public REST/WMS feed for programmatic integration.
   - TrailGuard relies on NASA FIRMS (VIIRS/MODIS) for live, machine-readable satellite hotspot coordinates, as officially documented.

4. **NDMA / SACHET RSS Feed**:
   - Feeds publish disaster warnings with CAP XML metadata across India. Where authoritative agency entries do not supply a full spatial polygon, TrailGuard falls back to the declared district centroid and declared affected radius without inventing fake geometry.

---

## 2. Offline Mode Boundaries

1. **Dynamic Rerouting Without Downloaded Graph**:
   - While offline, the app executes local cross-track corridor deviation checks ($d_\perp > 75\text{m}$) and point-in-polygon checks against cached hazard polygons in Room.
   - Dynamic arbitrary rerouting between arbitrary new coordinates while offline requires a downloaded road/trail graph (e.g., local OSRM/Valhalla graph bundle), which is not yet bundled inside the mobile client. When off-route, the app instructs the trekker to return to the active trail corridor or switch to a cached pre-planned alternative.

2. **Stale Hazard Expiry & Zero-Connectivity Limitations**:
   - When offline, weather forecasts and fire detections preserve their last retrieval timestamps.
   - The UI clearly displays `[CACHED WEATHER (X hours ago)]`. Stored hazard boundaries remain visible, but the application explicitly states that newly published disaster closures cannot be retrieved until cellular connectivity is re-established.

3. **Emergency Check-Ins & SOS Transmission**:
   - In airplane mode or deep wilderness valleys with zero cellular reception, check-in pings and SOS beacons are stored in the local SQLite queue (`STORED_LOCALLY`).
   - They transmit automatically upon opportunistic re-connection to a cellular tower or Wi-Fi network. Device-to-device Bluetooth Low Energy (BLE) multi-hop mesh relays remain an experimental extension subject to hardware density.

---

## 3. Physical Hardware Constraints

1. **GNSS Accuracy in Alpine Canyons**:
   - In steep mountain valleys or heavy cloud cover, GPS horizontal accuracy may degrade from $\pm 5\text{m}$ to $\pm 30\text{m}$.
   - The app's `LocalSpatialEngine` accounts for this by computing dead-reckoning uncertainty ellipses ($R = R_{\text{GPS}} + v_{\max} \cdot \Delta t$) rather than projecting false pinpoint confidence.

