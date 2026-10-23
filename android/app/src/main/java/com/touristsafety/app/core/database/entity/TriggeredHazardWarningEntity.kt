package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "triggered_hazard_warnings")
data class TriggeredHazardWarningEntity(
    @PrimaryKey val alertId: String,
    val triggeredTimestampEpochMs: Long,
    val dismissed: Boolean = false
)

