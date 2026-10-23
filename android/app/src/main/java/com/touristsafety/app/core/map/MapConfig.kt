package com.touristsafety.app.core.map

import com.touristsafety.app.BuildConfig

object MapConfig {
    const val DEFAULT_STYLE_URL = BuildConfig.MAP_STYLE_URL
    const val ATTRIBUTION = "© OpenStreetMap contributors, OpenFreeMap"

    // OpenFreeMap Community Offline Policy
    const val MAX_OFFLINE_TILES = 3000
    const val MAX_OFFLINE_SIZE_MB = 50
    const val MAX_CONCURRENT_DOWNLOADS = 4

    // Default trail initial camera coordinates (Rohtang Valley Trailhead)
    const val DEFAULT_LATITUDE = 32.2396
    const val DEFAULT_LONGITUDE = 77.1887
    const val DEFAULT_ZOOM = 13.0
}

