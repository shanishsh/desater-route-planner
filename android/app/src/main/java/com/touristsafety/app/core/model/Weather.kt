package com.touristsafety.app.core.model

data class Weather(
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
    val isCached: Boolean = false,
    val isFallback: Boolean = false,
    val isStale: Boolean = false,
    val providerNotice: String = "Open-Meteo Weather"
)

