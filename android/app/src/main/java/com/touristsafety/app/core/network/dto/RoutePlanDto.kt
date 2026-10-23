package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PlaceSearchResultDto(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
    val display_name: String
)

@Serializable
data class PlanRouteRequestDto(
    val origin_lat: Double,
    val origin_lon: Double,
    val origin_name: String? = "Current Location",
    val destination_lat: Double,
    val destination_lon: Double,
    val destination_name: String,
    val travel_mode: String? = "driving",
    val departure_time: String? = null
)

@Serializable
data class RouteWeatherConditionDto(
    val temperature_c: Double,
    val relative_humidity_pct: Double,
    val precipitation_probability_pct: Double,
    val precipitation_mm: Double,
    val wind_speed_kmh: Double,
    val weather_code: Int,
    val weather_description: String,
    val warning_level: String,
    val forecast_valid_at: String,
    val source: String,
    val is_simulated: Boolean = false
)

@Serializable
data class RouteForestInfoDto(
    val in_forest: Boolean = false,
    val forest_name: String? = null,
    val forest_type: String? = null,
    val distance_to_edge_m: Double? = null
)

@Serializable
data class RouteWildfireInfoDto(
    val has_fire_within_5km: Boolean = false,
    val nearest_fire_distance_km: Double? = null,
    val fire_radiative_power: Double? = null,
    val confidence: String? = null,
    val acquisition_time: String? = null
)

@Serializable
data class RouteDisasterAlertInfoDto(
    val alert_id: String,
    val title: String,
    val hazard_category: String,
    val severity: String,
    val distance_to_alert_km: Double,
    val required_protective_instructions: String,
    val is_simulated: Boolean = false
)

@Serializable
data class RouteWaypointConditionDto(
    val index: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distance_from_start_km: Double,
    val estimated_arrival_at: String,
    val hazard_level: String,
    val weather: RouteWeatherConditionDto,
    val forest: RouteForestInfoDto? = null,
    val wildfire: RouteWildfireInfoDto? = null,
    val alerts: List<RouteDisasterAlertInfoDto> = emptyList()
)

@Serializable
data class RouteAlternativeDto(
    val id: String,
    val title: String,
    val description: String,
    val distance_km: Double,
    val duration_minutes: Double,
    val hazard_score: Double = 0.0,
    val safety_label: String = "Safe",
    val geometry_geojson: JsonElement? = null
)

@Serializable
data class PlannedRouteResponseDto(
    val route_id: String,
    val title: String,
    val origin_name: String,
    val origin_lat: Double,
    val origin_lon: Double,
    val destination_name: String,
    val dest_lat: Double,
    val dest_lon: Double,
    val travel_mode: String,
    val distance_km: Double,
    val duration_minutes: Double,
    val overall_hazard_score: Double,
    val overall_safety_status: String,
    val high_risk_segments_count: Int = 0,
    val conditions: List<RouteWaypointConditionDto> = emptyList(),
    val alternatives: List<RouteAlternativeDto> = emptyList(),
    val geometry_geojson: JsonElement? = null,
    val created_at: String
)

@Serializable
data class StartTripRequestDto(
    val route_id: String,
    val device_id: String? = null,
    val user_id: String? = null
)

@Serializable
data class StartTripResponseDto(
    val trip_id: String,
    val route_id: String,
    val status: String,
    val started_at: String,
    val message: String
)

@Serializable
data class TripProgressRequestDto(
    val latitude: Double,
    val longitude: Double,
    val speed_kmh: Double? = 0.0,
    val timestamp: String? = null
)

@Serializable
data class TripProgressResponseDto(
    val trip_id: String,
    val status: String,
    val current_segment_index: Int,
    val progress_pct: Double,
    val distance_remaining_km: Double,
    val distance_to_nearest_hazard_km: Double? = null,
    val nearest_hazard_title: String? = null,
    val hazard_warning_level: String = "LOW",
    val is_off_route: Boolean = false,
    val reroute_suggested: Boolean = false,
    val suggested_alternative_id: String? = null,
    val next_waypoint: RouteWaypointConditionDto? = null,
    val message: String
)

@Serializable
data class TripStatusResponseDto(
    val trip_id: String,
    val route_id: String,
    val status: String,
    val started_at: String,
    val last_latitude: Double? = null,
    val last_longitude: Double? = null,
    val progress_pct: Double = 0.0,
    val current_segment_index: Int = 0,
    val overall_safety_status: String = "SAFE",
    val completed_at: String? = null
)

