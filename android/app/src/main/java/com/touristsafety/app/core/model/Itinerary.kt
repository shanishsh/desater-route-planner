package com.touristsafety.app.core.model

data class RouteSegment(
    val id: String,
    val sequenceOrder: Int,
    val name: String,
    val elevationGainM: Double,
    val hazardLevel: String,
    val segmentGeoJson: String? = null
)

data class Itinerary(
    val id: String,
    val title: String,
    val description: String,
    val region: String,
    val difficulty: String,
    val distanceKm: Double,
    val estimatedDurationHours: Double,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val routeGeoJson: String,
    val segments: List<RouteSegment> = emptyList(),
    val isSavedOffline: Boolean = false,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)

