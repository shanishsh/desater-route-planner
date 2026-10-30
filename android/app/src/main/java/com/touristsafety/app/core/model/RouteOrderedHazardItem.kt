package com.touristsafety.app.core.model

/**
 * Direct route intersection vs nearby within the monitored corridor buffer.
 */
enum class RouteIntersectionType {
    DIRECT_INTERSECTION,
    CORRIDOR_NEARBY,
    UNCERTAIN
}

/**
 * Status of a route hazard relative to the trekker's live progress along the route.
 */
enum class HazardTrekStatus {
    AHEAD,      // Hazard lies ahead on the route
    NEARBY,     // Hazard is currently at or within immediate proximity (< 250m) of trekker
    PASSED,     // Trekker has verified passed the hazard's route position (> 150m past)
    UNCERTAIN   // Position cannot be reliably matched (e.g. GPS off-route or inaccurate)
}

/**
 * A real hazard or safety facility positioned along a selected trek route.
 * Strictly ordered by its genuine distance along the route polyline from origin to destination.
 */
data class RouteOrderedHazardItem(
    val sequenceNumber: Int,
    val sequenceLabel: String,
    val feature: UnifiedMapFeature,
    val distanceAlongRouteMeters: Double,
    val crossTrackDistanceMeters: Double,
    val intersectionType: RouteIntersectionType,
    val trekStatus: HazardTrekStatus = HazardTrekStatus.AHEAD,
    val distanceAheadMeters: Double? = null,
    val distanceCurrentLocationMeters: Double? = null,
    val estimatedMinutesAhead: Int? = null,
    val categoryType: HazardCategoryType,
    val severity: String = "WARNING",
    val observationTimeText: String? = null,
    val isStale: Boolean = false
) {
    /** Formatted distance along the route from the starting trailhead */
    fun formattedDistanceAlongRoute(): String {
        return if (distanceAlongRouteMeters < 1000.0) {
            "${distanceAlongRouteMeters.toInt()} m from start"
        } else {
            String.format(java.util.Locale.US, "%.1f km from start", distanceAlongRouteMeters / 1000.0)
        }
    }

    /** Formatted relative distance ahead of trekker */
    fun formattedDistanceAhead(): String? {
        val ahead = distanceAheadMeters ?: return null
        return if (ahead < 1000.0) {
            "${ahead.toInt()} m ahead"
        } else {
            String.format(java.util.Locale.US, "%.1f km ahead", ahead / 1000.0)
        }
    }
}
