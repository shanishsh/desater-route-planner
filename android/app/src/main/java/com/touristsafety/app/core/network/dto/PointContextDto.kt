package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NearbyHazardDto(
    val id: String,
    val category: String,
    val title: String,
    val distance_km: Double,
    val severity: String? = null,
    val source: String = "Verified Observation",
    val is_intersecting: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class NearbyFacilityDto(
    val id: String,
    val name: String,
    val category: String,
    val distance_km: Double,
    val latitude: Double,
    val longitude: Double,
    val phone: String? = null,
    val address: String? = null,
    val is_verified: Boolean = true
)

@Serializable
data class PointContextDto(
    val latitude: Double,
    val longitude: Double,
    val in_uttarakhand: Boolean = true,
    val nearest_settlement: String? = null,
    val settlement_distance_km: Double? = null,
    val elevation_m: Double? = null,
    val weather: WeatherDto? = null,
    val active_warnings: List<RouteDisasterAlertInfoDto> = emptyList(),
    val nearby_hazards: List<NearbyHazardDto> = emptyList(),
    val nearby_facilities: List<NearbyFacilityDto> = emptyList(),
    val nearest_waterway: JsonElement? = null,
    val cellular_estimate: Map<String, String> = emptyMap(),
    val retrieved_at: String = "",
    val is_cached: Boolean = false,
    val is_stale: Boolean = false,
    val disclaimer: String = ""
)
