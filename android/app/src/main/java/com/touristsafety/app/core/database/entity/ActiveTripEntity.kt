package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_trips")
data class ActiveTripEntity(
    @PrimaryKey val tripId: String,
    val routeId: String,
    val status: String,
    val startedAt: String,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val currentSegmentIndex: Int = 0,
    val progressPct: Double = 0.0,
    val isOffRoute: Boolean = false,
    val nearestHazardDistKm: Double? = null,
    val nearestHazardTitle: String? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

