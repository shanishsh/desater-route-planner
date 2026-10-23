package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fire_hotspots")
data class FireHotspotEntity(
    @PrimaryKey
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val brightness: Double,
    val confidence: String,
    val frpMw: Double,
    val acquisitionDate: String,
    val acquisitionTime: String,
    val satellite: String,
    val instrument: String,
    val isSimulated: Boolean,
    val retrievedAtEpochMs: Long
)

