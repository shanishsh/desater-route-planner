package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planned_routes")
data class PlannedRouteEntity(
    @PrimaryKey val routeId: String,
    val title: String,
    val originName: String,
    val originLat: Double,
    val originLon: Double,
    val destinationName: String,
    val destLat: Double,
    val destLon: Double,
    val travelMode: String,
    val distanceKm: Double,
    val durationMinutes: Double,
    val overallHazardScore: Double,
    val overallSafetyStatus: String,
    val highRiskSegmentsCount: Int,
    val conditionsJson: String,
    val alternativesJson: String,
    val isSavedOffline: Boolean = true,
    val cachedAt: Long = System.currentTimeMillis()
)

