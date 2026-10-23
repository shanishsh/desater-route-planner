package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forest_features")
data class ForestEntity(
    @PrimaryKey
    val id: String,
    val osmId: Long,
    val name: String,
    val woodType: String,
    val coordinatesJson: String, // GeoJSON coordinates string
    val isPolygon: Boolean,
    val retrievedAtEpochMs: Long,
    val isCached: Boolean = true
)

