package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class RouteSegmentDto(
    val id: String,
    val sequence_order: Int,
    val name: String,
    val elevation_gain_m: Double = 0.0,
    val hazard_level: String = "low"
)

@Serializable
data class ItineraryDto(
    val id: String,
    val title: String,
    val description: String,
    val region: String,
    val difficulty: String,
    val distance_km: Double,
    val estimated_duration_hours: Double,
    val start_latitude: Double,
    val start_longitude: Double,
    val end_latitude: Double,
    val end_longitude: Double,
    val route_geojson: JsonObject,
    val segments: List<RouteSegmentDto> = emptyList()
)

