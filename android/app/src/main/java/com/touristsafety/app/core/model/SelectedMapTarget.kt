package com.touristsafety.app.core.model

import com.touristsafety.app.core.network.dto.RouteWeatherConditionDto
import java.util.Locale

/**
 * Unified, strictly-typed selection target model for MapLibre map interactions.
 * Prevents selection-mapping bugs by distinguishing route weather samples,
 * hazard markers/polygons, verified facilities, and arbitrary map taps.
 */
sealed interface SelectedMapTarget {
    val targetId: String
    val latitude: Double
    val longitude: Double

    /**
     * Tapped route weather waypoint (deterministic sample along active route).
     */
    data class RouteWeather(
        val sampleId: String,
        val sampleIndex: Int,
        val name: String,
        override val latitude: Double,
        override val longitude: Double,
        val distanceAlongRouteKm: Double,
        val weather: RouteWeatherConditionDto
    ) : SelectedMapTarget {
        override val targetId: String get() = sampleId
    }

    /**
     * Tapped hazard marker or active fire hotspot.
     */
    data class Hazard(
        val hazardId: String,
        val feature: UnifiedMapFeature
    ) : SelectedMapTarget {
        override val targetId: String get() = hazardId
        override val latitude: Double get() = feature.latitude
        override val longitude: Double get() = feature.longitude
    }

    /**
     * Tapped disaster warning or forest boundary polygon.
     */
    data class HazardPolygon(
        val polygonId: String,
        val feature: UnifiedMapFeature,
        val polygonCoordinates: List<Pair<Double, Double>> = emptyList()
    ) : SelectedMapTarget {
        override val targetId: String get() = polygonId
        override val latitude: Double get() = feature.latitude
        override val longitude: Double get() = feature.longitude
    }

    /**
     * Tapped verified assistance facility (hospital, clinic, police, fuel, shelter).
     */
    data class Facility(
        val facilityId: String,
        val feature: UnifiedMapFeature
    ) : SelectedMapTarget {
        override val targetId: String get() = facilityId
        override val latitude: Double get() = feature.latitude
        override val longitude: Double get() = feature.longitude
    }

    /**
     * Tapped arbitrary point anywhere on the map outside mapped features.
     */
    data class ArbitraryPoint(
        override val latitude: Double,
        override val longitude: Double,
        val generationToken: Long = 0L,
        val tappedAtEpochMs: Long = System.currentTimeMillis()
    ) : SelectedMapTarget {
        override val targetId: String
            get() = String.format(Locale.US, "point-%.5f,%.5f", latitude, longitude)
    }
}
