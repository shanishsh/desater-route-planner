package com.touristsafety.app.core.map

/**
 * Supported basemap display modes for the Uttarakhand Trek Safety mapping canvas.
 */
enum class BasemapMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val attribution: String,
    val isSatellite: Boolean
) {
    STREETS_TRAILS(
        id = "streets_trails",
        title = "Streets & Trails",
        subtitle = "OpenFreeMap vector style with topographic contours & trail network",
        attribution = "© OpenStreetMap contributors, OpenFreeMap",
        isSatellite = false
    ),
    SATELLITE(
        id = "satellite",
        title = "Satellite",
        subtitle = "High-resolution aerial imagery for terrain, ridge & valley inspection",
        attribution = "Imagery: © Esri, Maxar, Earthstar Geographics, GIS User Community",
        isSatellite = true
    ),
    HYBRID(
        id = "hybrid",
        title = "Hybrid",
        subtitle = "Satellite imagery with geographic reference roads, labels & settlements",
        attribution = "Imagery: © Esri, Maxar | Reference: © OpenStreetMap, Esri",
        isSatellite = true
    );

    val displayName: String get() = title
}
