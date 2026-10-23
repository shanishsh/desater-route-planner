package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_cache")
data class WeatherEntity(
    @PrimaryKey val locationKey: String, // e.g. "32.24:77.19"
    val latitude: Double,
    val longitude: Double,
    val temperatureC: Double,
    val relativeHumidityPct: Double,
    val windSpeedKmh: Double,
    val precipitationMm: Double,
    val weatherCode: Int,
    val weatherDescription: String,
    val forecastValidAtEpochMs: Long,
    val retrievedAtEpochMs: Long,
    val isFallback: Boolean = false
)

@Entity(tableName = "map_points")
data class MapPointEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val phone: String?,
    val description: String?,
    val verificationStatus: String,
    val verificationTimestampEpochMs: Long,
    val provenance: String
)

