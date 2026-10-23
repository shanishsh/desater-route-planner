package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class WildfireRecordDto(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val brightness: Double = 0.0,
    val confidence: String = "nominal",
    val frp_mw: Double = 0.0,
    val acquisition_date: String = "",
    val acquisition_time: String = "",
    val satellite: String = "VIIRS/MODIS",
    val instrument: String = "NRT",
    val is_simulated: Boolean = false
)

@Serializable
data class WildfiresResponseDto(
    val records: List<WildfireRecordDto> = emptyList(),
    val record_count: Int = 0,
    val provider_status: String = "HEALTHY",
    val is_configured: Boolean = true,
    val diagnostic_message: String? = null,
    val disclaimer: String = "",
    val is_cached: Boolean = false
)

