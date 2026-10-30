package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AssistantLocationDto(
    val latitude: Double,
    val longitude: Double,
    val altitude_meters: Double? = null,
    val accuracy_meters: Float? = null,
    val speed_kmh: Double? = null
)

@Serializable
data class AssistantRouteProgressDto(
    val traveled_km: Double = 0.0,
    val chainage_km: Double = 0.0,
    val remaining_km: Double = 0.0,
    val progress_pct: Double = 0.0,
    val is_off_route: Boolean = false,
    val cross_track_m: Double = 0.0
)

@Serializable
data class AssistantHazardContextDto(
    val id: String,
    val title: String,
    val category: String,
    val severity: String,
    val distance_ahead_m: Double? = null,
    val distance_current_m: Double? = null,
    val sequence_number: Int? = null,
    val is_direct_intersection: Boolean = false,
    val source_name: String? = null,
    val valid_until: String? = null,
    val instructions: String? = null
)

@Serializable
data class AssistantWeatherContextDto(
    val temperature_c: Double? = null,
    val weather_description: String? = null,
    val precipitation_probability_pct: Int? = null,
    val wind_speed_kmh: Double? = null,
    val thunderstorm_probability_pct: Int? = null,
    val retrieved_at: String? = null,
    val is_stale: Boolean = false
)

@Serializable
data class AssistantFacilityContextDto(
    val id: String,
    val name: String,
    val category: String,
    val distance_km: Double,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class AssistantVerifiedAlternativeDto(
    val route_id: String,
    val title: String,
    val distance_km: Double,
    val duration_minutes: Double,
    val safety_advantage: String? = null
)

@Serializable
data class AssistantSafetyEventDto(
    val event_id: String,
    val event_type: String,
    val hazard_id: String? = null,
    val hazard_title: String,
    val severity: String,
    val distance_ahead_m: Double? = null,
    val distance_current_m: Double = 0.0,
    val is_direct_route_obstruction: Boolean = false,
    val has_verified_alternative: Boolean = false,
    val deterministic_spoken_text: String? = null,
    val deterministic_action_text: String? = null
)

@Serializable
data class LiveSafetyAssistantRequestDto(
    val trip_id: String,
    val route_id: String,
    val context_generation_id: Int = 1,
    val trigger_event: AssistantSafetyEventDto? = null,
    val current_location: AssistantLocationDto,
    val route_progress: AssistantRouteProgressDto,
    val upcoming_hazards: List<AssistantHazardContextDto> = emptyList(),
    val nearby_hazards: List<AssistantHazardContextDto> = emptyList(),
    val current_weather: AssistantWeatherContextDto? = null,
    val nearby_facilities: List<AssistantFacilityContextDto> = emptyList(),
    val verified_alternatives: List<AssistantVerifiedAlternativeDto> = emptyList(),
    val connectivity_status: String = "ONLINE",
    val stale_sources: List<String> = emptyList()
)

@Serializable
data class AssistantInteractiveQueryRequestDto(
    val trip_id: String,
    val route_id: String,
    val context_generation_id: Int = 1,
    val query: String,
    val current_location: AssistantLocationDto,
    val route_progress: AssistantRouteProgressDto,
    val upcoming_hazards: List<AssistantHazardContextDto> = emptyList(),
    val nearby_hazards: List<AssistantHazardContextDto> = emptyList(),
    val current_weather: AssistantWeatherContextDto? = null,
    val nearby_facilities: List<AssistantFacilityContextDto> = emptyList(),
    val verified_alternatives: List<AssistantVerifiedAlternativeDto> = emptyList(),
    val connectivity_status: String = "ONLINE"
)

@Serializable
data class LiveSafetyAssistantResponseDto(
    val event_id: String? = null,
    val context_generation_id: Int = 1,
    val summary: String = "",
    val what_happened: String = "",
    val why_relevant: String = "",
    val recommended_action: String = "",
    val what_to_avoid: String = "",
    val verified_alternatives: List<String> = emptyList(),
    val referenced_hazard_ids: List<String> = emptyList(),
    val referenced_weather_ids: List<String> = emptyList(),
    val data_limitations: List<String> = emptyList(),
    val spoken_message: String = "",
    val additional_explanation: String? = null,
    val is_ai_generated: Boolean = false,
    val fallback_used: Boolean = false,
    val disclaimer: String = ""
)

