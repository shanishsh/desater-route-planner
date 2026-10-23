package com.touristsafety.app.core.map

import com.touristsafety.app.core.model.UnifiedMapFeature
import kotlin.math.*

/**
 * Cluster item representing either an individual map feature or a clustered group of nearby points.
 */
data class ClusterItem(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val features: List<UnifiedMapFeature>,
    val isCluster: Boolean = features.size > 1
) {
    val singleFeature: UnifiedMapFeature? get() = if (features.size == 1) features.first() else null
    val count: Int get() = features.size
}

/**
 * High-performance grid-based spatial clustering utility for MapLibre and Offline Canvas rendering.
 */
object MapClusterHelper {

    /**
     * Clusters point features based on current map zoom level.
     * Zoom < 12.5: clusters within ~1.5 - 3 km
     * Zoom >= 13.0: individual markers displayed
     */
    fun clusterFeatures(
        features: List<UnifiedMapFeature>,
        zoom: Double
    ): List<ClusterItem> {
        if (features.isEmpty()) return emptyList()
        // Close-up: individual markers
        if (zoom >= 13.0) {
            return features.map {
                ClusterItem(
                    id = it.id,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    features = listOf(it),
                    isCluster = false
                )
            }
        }

        // Calculate grid bucket size based on zoom
        val gridSize = when {
            zoom < 9.0 -> 0.08  // ~9 km wide-area
            zoom < 11.0 -> 0.04 // ~4 km
            else -> 0.018       // ~2 km
        }

        val buckets = mutableMapOf<Pair<Long, Long>, MutableList<UnifiedMapFeature>>()
        for (feat in features) {
            val gridX = (feat.longitude / gridSize).toLong()
            val gridY = (feat.latitude / gridSize).toLong()
            val key = gridX to gridY
            buckets.getOrPut(key) { mutableListOf() }.add(feat)
        }

        return buckets.map { (key, group) ->
            val avgLat = group.sumOf { it.latitude } / group.size
            val avgLon = group.sumOf { it.longitude } / group.size
            val id = if (group.size == 1) group.first().id else "cluster_${key.first}_${key.second}_${group.size}"
            ClusterItem(
                id = id,
                latitude = avgLat,
                longitude = avgLon,
                features = group,
                isCluster = group.size > 1
            )
        }
    }

    /**
     * Computes spherical distance in meters between two lat/lon coordinates (Haversine).
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }
}

