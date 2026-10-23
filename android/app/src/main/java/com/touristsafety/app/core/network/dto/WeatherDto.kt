package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class WeatherDto(
    val latitude: Double,
    val longitude: Double,
    val temperature_c: Double,
    val relative_humidity_pct: Double,
    val wind_speed_kmh: Double,
    val precipitation_mm: Double,
    val precipitation_probability_pct: Double = 0.0,
    val weather_code: Int,
    val weather_description: String,
    val forecast_valid_at: String,
    val retrieved_at: String,
    val expires_at: String,
    val is_cached: Boolean = false,
    val is_fallback: Boolean = false,
    val is_stale: Boolean = false,
    val provider_notice: String = "Open-Meteo Weather"
)

