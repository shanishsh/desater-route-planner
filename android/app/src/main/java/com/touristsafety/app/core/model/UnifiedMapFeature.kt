package com.touristsafety.app.core.model

/**
 * Unified representation of any geographic feature displayed on the map.
 * Ensures consistent rendering across MapLibre layers, markers, clustering,
 * nearby assistance lists, and compact detail cards.
 */
data class UnifiedMapFeature(
    val id: String,
    val category: MapFeatureCategory,
    val title: String,
    val subtitle: String? = null,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float? = null,
    val sourceName: String = "Verified Public Safety Directory",
    val sourceTimestampEpochMs: Long? = null,
    val isVerified: Boolean = true,
    val isSimulated: Boolean = false,
    val isCached: Boolean = false,
    val phone: String? = null,
    val address: String? = null,
    val details: Map<String, String> = emptyMap(),
    val geometryPolygon: List<Pair<Double, Double>>? = null // for area polygons (forests, hazard buffers)
) {
    /** Formatted human-readable distance */
    fun formattedDistance(): String? {
        val dist = distanceMeters ?: return null
        return if (dist < 1000f) {
            "${dist.toInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.1f km", dist / 1000f)
        }
    }
}

