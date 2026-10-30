package com.touristsafety.app.core.map

import com.touristsafety.app.BuildConfig
import org.maplibre.android.maps.Style

/**
 * Free basemap configuration supporting Streets & Trails (OpenFreeMap),
 * Satellite (ArcGIS World Imagery / MapTiler), and Hybrid imagery styles.
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

    val FALLBACK_STYLE_JSON = OSM_RASTER_STYLE_JSON

    /**
     * Genuine high-resolution aerial and satellite imagery basemap.
     * Powered by ArcGIS World Imagery (Esri, Maxar, Earthstar Geographics).
     */
    val SATELLITE_STYLE_JSON = """
    {
      "version": 8,
      "name": "Uttarakhand Satellite Basemap",
      "sources": {
        "satellite-tiles": {
          "type": "raster",
          "tiles": [
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
          ],
          "tileSize": 256,
          "attribution": "Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
        }
      },
      "layers": [
        {
          "id": "background",
          "type": "background",
          "paint": {
            "background-color": "#0B131E"
          }
        },
        {
          "id": "satellite-tiles-layer",
          "type": "raster",
          "source": "satellite-tiles",
          "minzoom": 0,
          "maxzoom": 19
        }
      ]
    }
    """.trimIndent()

    /**
     * Hybrid basemap compositing high-resolution satellite imagery with
     * reference transportation roads and administrative place labels.
     */
    val HYBRID_STYLE_JSON = """
    {
      "version": 8,
      "name": "Uttarakhand Hybrid Basemap",
      "sources": {
        "satellite-tiles": {
          "type": "raster",
          "tiles": [
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
          ],
          "tileSize": 256,
          "attribution": "Imagery: © Esri, Maxar, Earthstar Geographics"
        },
        "reference-roads": {
          "type": "raster",
          "tiles": [
            "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}"
          ],
          "tileSize": 256
        },
        "reference-labels": {
          "type": "raster",
          "tiles": [
            "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
          ],
          "tileSize": 256,
          "attribution": "Boundaries & Places: © Esri"
        }
      },
      "layers": [
        {
          "id": "background",
          "type": "background",
          "paint": {
            "background-color": "#0B131E"
          }
        },
        {
          "id": "satellite-tiles-layer",
          "type": "raster",
          "source": "satellite-tiles",
          "minzoom": 0,
          "maxzoom": 19
        },
        {
          "id": "reference-roads-layer",
          "type": "raster",
          "source": "reference-roads",
          "minzoom": 0,
          "maxzoom": 19
        },
        {
          "id": "reference-labels-layer",
          "type": "raster",
          "source": "reference-labels",
          "minzoom": 0,
          "maxzoom": 19
        }
      ]
    }
    """.trimIndent()

    /**
     * Generates a MapLibre Style.Builder for the requested BasemapMode.
     */
    fun createStyleBuilder(mode: BasemapMode): Style.Builder {
        return when (mode) {
            BasemapMode.STREETS_TRAILS -> {
                Style.Builder().fromUri(DEFAULT_STYLE_URL)
            }
            BasemapMode.SATELLITE -> {
                Style.Builder().fromJson(SATELLITE_STYLE_JSON)
            }
            BasemapMode.HYBRID -> {
                Style.Builder().fromJson(HYBRID_STYLE_JSON)
            }
        }
    }
}
