package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hazard_zones")
data class HazardZoneEntity(
    @PrimaryKey val alertId: String,
    val title: String,
    val description: String,
    val hazardCategory: String, // flash_flood, landslide, severe_weather, trail_closure, etc.
    val severity: String,       // CRITICAL, SEVERE, WARNING, INFO
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusMeters: Float,
    val requiredProtectiveInstructions: String,
    val issuingAuthority: String,
    val publishedAtEpochMs: Long,
    val receivedAtEpochMs: Long,
    val downloadedAtEpochMs: Long,
    val isSimulated: Boolean,
    val status: String          // active, updated, cancelled
)

