package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trek_track_points",
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["tripId", "isSynced"])
    ]
)
data class TrekTrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val accuracyMeters: Float,
    val speedKmh: Double,
    val recordedAtEpochMs: Long,
    val isAcceptedForDistance: Boolean,
    val isSynced: Boolean = false
)

