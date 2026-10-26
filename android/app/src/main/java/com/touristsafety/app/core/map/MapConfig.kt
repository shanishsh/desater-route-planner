package com.touristsafety.app.core.map

import com.touristsafety.app.BuildConfig

/**
 * Free basemap configuration using OpenFreeMap (https://openfreemap.org).
 * OpenFreeMap provides completely free, open-source vector map tiles powered by OpenStreetMap data
 * with no API keys, billing accounts, or proprietary tracking tokens required.
 *
 * OpenFreeMap Fair Use & Offline Terms:
 * - Direct vector downloads capped at 3,000 tiles or 50 MB per offline pack.
 * - Maximum of 4 parallel requests (enforced via OkHttp Dispatcher).
 * - Custom descriptive User-Agent header identifying client.
 *
 * Statewide Uttarakhand Architecture (Large-Area Offline Alternative):
 * For statewide coverage exceeding 3,000 tiles / 50 MB, OpenFreeMap provides free raw PMTiles
 * extracts (or MBTiles) available at https://openfreemap.org/downloads. These can be:
 * 1. Self-hosted locally on a regional server (e.g., using Martin, Tileserver-GL, or Caddy)
 * 2. Bundled as a local SQLite/PMTiles asset within the Android client or external SD card
 * 3. Pointed to via local file URI: "file:///android_asset/uttarakhand.mbtiles" or self-hosted endpoint.
 */
object MapConfig {
    const val DEFAULT_STYLE_URL = BuildConfig.MAP_STYLE_URL
    const val ATTRIBUTION = "© OpenStreetMap contributors, OpenFreeMap"

    // OpenFreeMap Community Offline Policy Caps
    const val MAX_OFFLINE_TILES = 3000
    const val MAX_OFFLINE_SIZE_MB = 50
    const val MAX_CONCURRENT_DOWNLOADS = 4

    // Default Uttarakhand initial camera coordinates (Centroid of Uttarakhand)
    const val DEFAULT_LATITUDE = 30.1500
    const val DEFAULT_LONGITUDE = 79.2500
    const val DEFAULT_ZOOM = 7.5

    // Geographic Camera Target Bounds for Uttarakhand
    const val UTTARAKHAND_MIN_LAT = 28.70
    const val UTTARAKHAND_MIN_LON = 77.55
    const val UTTARAKHAND_MAX_LAT = 31.48
    const val UTTARAKHAND_MAX_LON = 81.08

    // Zoom Constraints: strictly prevents zooming out to continental/world scale
    const val MIN_ZOOM_LEVEL = 6.5
    const val MAX_ZOOM_LEVEL = 18.0

    // High-visibility, rich topographic basemap using OpenStreetMap standard raster tiles
    val OSM_RASTER_STYLE_JSON = """
    {
      "version": 8,
      "name": "Uttarakhand Basemap",
      "sources": {
        "osm-tiles": {
          "type": "raster",
          "tiles": [
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          ],
          "tileSize": 256,
          "attribution": "© OpenStreetMap contributors"
        }
      },
      "layers": [
        {
          "id": "background",
          "type": "background",
          "paint": {
            "background-color": "#E5E0D8"
          }
        },
        {
          "id": "osm-tiles-layer",
          "type": "raster",
          "source": "osm-tiles",
          "minzoom": 0,
          "maxzoom": 19
        }
      ]
    }
    """.trimIndent()

    // Offline-ready fallback style JSON pointing to the rich OSM raster basemap
    val FALLBACK_STYLE_JSON = OSM_RASTER_STYLE_JSON
}

