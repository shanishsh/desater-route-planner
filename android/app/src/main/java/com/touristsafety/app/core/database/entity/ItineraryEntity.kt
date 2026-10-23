package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "itineraries")
data class ItineraryEntity(
    @PrimaryKey val id: String,
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
    val isSavedOffline: Boolean = false,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "route_segments",
    foreignKeys = [
        ForeignKey(
            entity = ItineraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["itineraryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itineraryId")]
)
data class RouteSegmentEntity(
    @PrimaryKey val id: String,
    val itineraryId: String,
    val sequenceOrder: Int,
    val name: String,
    val elevationGainM: Double,
    val hazardLevel: String
)

