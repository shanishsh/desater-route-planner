package com.touristsafety.app.core.model

import com.touristsafety.app.core.location.ActiveHazardAlert
import com.touristsafety.app.core.network.dto.NearbyFacilityDto
import com.touristsafety.app.core.network.dto.WeatherDto

/**
 * Status of an active trek session.
 */
enum class TrekSessionStatus {
    IDLE,
    ACTIVE,
    PAUSED,
    OFF_ROUTE,
    COMPLETED
}

/**
 * Spatial hazard classification relative to live trek progress.
 */
enum class HazardCategoryClassification {
    AHEAD_ON_ROUTE,        // Hazard lies on or intersects remaining route ahead of trekker
    NEAR_CURRENT_LOCATION, // Hazard is in immediate proximity to current GPS (on or off trail)
    PASSED,                // Hazard is behind verified route progress
    NEARBY_OFF_ROUTE,      // Within corridor but off-trail without direct path intersection
    UNCERTAIN              // Poor GPS accuracy or significant off-route deviation
}

/**
 * Disambiguated trek distance, chainage, and route progress metrics.
 */
data class TrekProgressMetrics(
    val traveledDistanceMeters: Double = 0.0,            // Real odometer distance from accepted GPS breadcrumbs
    val routeProgressMeters: Double = 0.0,               // Projected progress / chainage along planned trail polyline
    val totalRouteDistanceMeters: Double = 0.0,          // Total planned distance of the selected trail
    val remainingDistanceMeters: Double = 0.0,           // Planned trail distance still ahead
    val straightLineToDestMeters: Double = 0.0,          // Haversine direct distance to destination
    val progressPct: Float = 0f,                         // Percentage of route completed (0..100)
    val currentSegmentIndex: Int = 0,                    // Index of nearest polyline vertex/segment
    val crossTrackDistanceMeters: Double = 0.0,          // Lateral deviation from nearest trail segment
    val isOffRoute: Boolean = false,                     // True if lateral deviation exceeds off-route threshold (>80m)
    val lastAcceptedLat: Double? = null,
    val lastAcceptedLon: Double? = null,
    val lastAcceptedAltitudeM: Double? = null,
    val currentGpsAccuracyM: Float? = null,
    val currentSpeedKmh: Double = 0.0,
    val elapsedDurationSeconds: Long = 0L
) {
    fun formattedTraveledDistance(): String {
        return if (traveledDistanceMeters < 1000.0) {
            "${traveledDistanceMeters.toInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.2f km", traveledDistanceMeters / 1000.0)
        }
    }

    fun formattedRemainingDistance(): String {
        return if (remainingDistanceMeters < 1000.0) {
            "${remainingDistanceMeters.toInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.1f km", remainingDistanceMeters / 1000.0)
        }
    }
}

/**
 * Categorized real-time hazard classification collections.
 */
data class CategorizedHazards(
    val upcomingHazards: List<RouteOrderedHazardItem> = emptyList(),   // Ahead on route (ordered with sequence numbers)
    val nearbyHazards: List<RouteOrderedHazardItem> = emptyList(),     // Within physical proximity radius of GPS
    val passedHazards: List<RouteOrderedHazardItem> = emptyList(),     // Traversed and behind current progress
    val offRouteHazards: List<RouteOrderedHazardItem> = emptyList(),   // Within corridor but off-route
    val uncertainHazards: List<RouteOrderedHazardItem> = emptyList()    // GPS uncertain / unclassified
)

/**
 * Live device network and cellular infrastructure state.
 */
data class LiveNetworkStatus(
    val isConnected: Boolean = true,
    val connectionType: String = "CELLULAR", // "WIFI", "CELLULAR", "OFFLINE"
    val nearestTowerDistanceM: Double? = null,
    val nearestTowerOperator: String? = null,
    val estimatedCoverage: String = "GOOD",  // "GOOD", "MARGINAL", "DEAD_ZONE_LIKELY", "UNKNOWN"
    val isOffline: Boolean = false,
    val lastSyncedEpochMs: Long = System.currentTimeMillis()
)

/**
 * Live weather context for current position and route ahead.
 */
data class LiveWeatherContext(
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val currentTemperatureC: Double? = null,
    val weatherDescription: String? = null,
    val precipitationProbabilityPct: Int? = null,
    val windSpeedKmh: Double? = null,
    val thunderstormProbabilityPct: Int? = null,
    val retrievedAtEpochMs: Long = 0L,
    val isStale: Boolean = false,
    val isCached: Boolean = false,
    val upcomingForecastSamples: List<SelectedMapTarget.RouteWeather> = emptyList()
)

/**
 * Top-level immutable state snapshot produced by LiveTrekContextEngine.
 */
data class LiveTrekContext(
    val tripId: String? = null,
    val routeId: String? = null,
    val destinationName: String = "Destination",
    val sessionStatus: TrekSessionStatus = TrekSessionStatus.IDLE,
    val progress: TrekProgressMetrics = TrekProgressMetrics(),
    val hazards: CategorizedHazards = CategorizedHazards(),
    val weather: LiveWeatherContext = LiveWeatherContext(),
    val nearbyFacilities: List<NearbyFacilityDto> = emptyList(),
    val networkStatus: LiveNetworkStatus = LiveNetworkStatus(),
    val activeCriticalAlert: ActiveHazardAlert? = null,
    val lastEngineUpdateEpochMs: Long = System.currentTimeMillis()
)
