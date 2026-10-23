package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val severity: String,
    val category: String,
    val affectedItineraryId: String?,
    val affectedSegmentId: String?,
    val sourceName: String,
    val sourceUrl: String?,
    val sourcePublishedAtEpochMs: Long,
    val retrievedAtEpochMs: Long,
    val freshnessThresholdSeconds: Int,
    val isSimulation: Boolean = true
)

