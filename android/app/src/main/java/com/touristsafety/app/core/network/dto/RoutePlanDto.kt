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
    val travel_mode: String? = "hiking",
    val departure_time: String? = null,
    val corridor_distance_km: Double = 5.0
)

@Serializable
data class RouteAnalysisRequestDto(
    val corridor_distance_km: Double = 5.0,
    val corridor_distance_meters: Double? = null,
    val route_version: Int? = null
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
    val acquisition_time: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val brightness: Double? = null
)

@Serializable
data class RouteDisasterAlertInfoDto(
    val alert_id: String,
    val title: String,
    val hazard_category: String,
    val severity: String,
    val distance_to_alert_km: Double,
    val required_protective_instructions: String,
    val is_simulated: Boolean = false,
    val center_latitude: Double? = null,
    val center_longitude: Double? = null,
    val affected_radius_meters: Double? = null,
    val polygon_geojson: JsonElement? = null,
    val area_description: String? = null,
    val issuing_authority: String? = null,
    val published_at: String? = null,
    val effective_at: String? = null,
    val expires_at: String? = null,
    val source_url: String? = null
)

@Serializable
data class ElevationProfilePointDto(
    val index: Int,
    val latitude: Double,
    val longitude: Double,
    val elevation_m: Double,
    val distance_from_start_km: Double,
    val slope_degrees: Double = 0.0,
    val hiking_speed_kmh: Double = 4.0,
    val cumulative_minutes: Double = 0.0,
    val estimated_arrival_iso: String = ""
)

@Serializable
data class SafeHavenInfoDto(
    val name: String,
    val category: String, // hospital, clinic, police, fuel, fire_station, shelter
    val latitude: Double,
    val longitude: Double,
    val distance_km: Double,
    val is_egress_point: Boolean = true,
    val opening_hours: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val brand: String? = null,
    val operator: String? = null,
    val address: String? = null,
    val emergency_verified: Boolean = false,
    val source: String? = null
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
    val elevation_m: Double = 0.0,
    val slope_degrees: Double = 0.0,
    val weather: RouteWeatherConditionDto,
    val forest: RouteForestInfoDto? = null,
    val wildfire: RouteWildfireInfoDto? = null,
    val alerts: List<RouteDisasterAlertInfoDto> = emptyList(),
    val contributing_factors: List<String> = emptyList()
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
    val geometry_geojson: JsonElement? = null,
    val corridor_geojson: JsonElement? = null,
    val is_recommended: Boolean = false,
    val direct_hazard_intersections_count: Int = 0,
    val corridor_hazard_count: Int = 0,
    val fires_within_2km_count: Int = 0,
    val recommendation_reason: String? = null,
    val exposure_summary: String? = null,
    val hospitals_count: Int = 0,
    val clinics_count: Int = 0,
    val police_stations_count: Int = 0,
    val fuel_stations_count: Int = 0,
    val fire_stations_count: Int = 0,
    val nearest_hospital_km: Double? = null,
    val nearest_police_km: Double? = null,
    val nearest_fuel_km: Double? = null,
    val facilities_geojson: JsonElement? = null,
    val preference_rankings: Map<String, JsonElement> = emptyMap(),
    val data_completeness: Boolean = true
)

@Serializable
data class ProviderStatusItemDto(
    val provider_name: String,
    val status: String,
    val last_request_time: String? = null,
    val last_success_time: String? = null,
    val http_status: Int? = null,
    val record_count: Int = 0,
    val is_stale: Boolean = false,
    val details: String? = null
)

@Serializable
data class PlannedRouteResponseDto(
    val route_id: String,
    val route_version: Int = 1,
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
    val distance_meters: Double = 0.0,
    val duration_seconds: Double = 0.0,
    val routing_provider: String = "OSRM",
    val overall_hazard_score: Double,
    val overall_safety_status: String,
    val high_risk_segments_count: Int = 0,
    val total_ascent_m: Double = 0.0,
    val total_descent_m: Double = 0.0,
    val min_elevation_m: Double = 0.0,
    val max_elevation_m: Double = 0.0,
    val decision_explanation: String = "",
    val conditions: List<RouteWaypointConditionDto> = emptyList(),
    val elevation_profile: List<ElevationProfilePointDto> = emptyList(),
    val safe_havens: List<SafeHavenInfoDto> = emptyList(),
    val alternatives: List<RouteAlternativeDto> = emptyList(),
    val geometry_geojson: JsonElement? = null,
    val corridor_distance_km: Double = 5.0,
    val corridor_distance_meters: Double = 5000.0,
    val corridor_geojson: JsonElement? = null,
    val hazards_geojson: JsonElement? = null,
    val fires_geojson: JsonElement? = null,
    val facilities_geojson: JsonElement? = null,
    val forests_geojson: JsonElement? = null,
    val weather_geojson: JsonElement? = null,
    val terrain_slope_geojson: JsonElement? = null,
    val cellular_sites_geojson: JsonElement? = null,
    val cellular_coverage_geojson: JsonElement? = null,
    val waterways_geojson: JsonElement? = null,
    val historical_landslides_geojson: JsonElement? = null,
    val terrain_exposure: JsonElement? = null,
    val cellular_summary: JsonElement? = null,
    val waterway_summary: JsonElement? = null,
    val landslide_summary: JsonElement? = null,
    val nasa_lhasa_status: JsonElement? = null,
    val geometry_diagnostics: Map<String, JsonElement> = emptyMap(),
    val provider_statuses: Map<String, ProviderStatusItemDto> = emptyMap(),
    val analysis_limitations: List<String> = emptyList(),
    val created_at: String = "",
    val grounded_summary: GroundedRouteSummaryDto? = null
)

@Serializable
data class RouteAnalysisResponseDto(
    val route_id: String,
    val route_version: Int = 1,
    val corridor_distance_km: Double = 5.0,
    val corridor_distance_meters: Double = 5000.0,
    val analysis_timestamp: String = "",
    val overall_hazard_score: Double = 0.0,
    val overall_safety_status: String = "SAFE",
    val decision_explanation: String = "",
    val provider_statuses: Map<String, ProviderStatusItemDto> = emptyMap(),
    val weather_by_waypoint: List<RouteWeatherConditionDto> = emptyList(),
    val disaster_alerts: List<RouteDisasterAlertInfoDto> = emptyList(),
    val fire_detections: List<RouteWildfireInfoDto> = emptyList(),
    val forest_features: List<RouteForestInfoDto> = emptyList(),
    val assistance_points: List<SafeHavenInfoDto> = emptyList(),
    val affected_segments: List<RouteWaypointConditionDto> = emptyList(),
    val data_quality_summary: Map<String, JsonElement> = emptyMap(),
    val corridor_geojson: JsonElement? = null,
    val hazards_geojson: JsonElement? = null,
    val fires_geojson: JsonElement? = null,
    val facilities_geojson: JsonElement? = null,
    val forests_geojson: JsonElement? = null,
    val weather_geojson: JsonElement? = null,
    val terrain_slope_geojson: JsonElement? = null,
    val cellular_sites_geojson: JsonElement? = null,
    val cellular_coverage_geojson: JsonElement? = null,
    val waterways_geojson: JsonElement? = null,
    val historical_landslides_geojson: JsonElement? = null,
    val terrain_exposure: JsonElement? = null,
    val cellular_summary: JsonElement? = null,
    val waterway_summary: JsonElement? = null,
    val landslide_summary: JsonElement? = null,
    val nasa_lhasa_status: JsonElement? = null,
    val alternatives: List<RouteAlternativeDto> = emptyList(),
    val analysis_limitations: List<String> = emptyList(),
    val overlays_geojson: JsonElement? = null,
    val geometry_diagnostics: Map<String, JsonElement> = emptyMap(),
    val grounded_summary: GroundedRouteSummaryDto? = null
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

@Serializable
data class TrekkerCheckInRequestDto(
    val trip_id: String,
    val device_id: String? = null,
    val latitude: Double,
    val longitude: Double,
    val elevation_m: Double? = 0.0,
    val gps_accuracy_m: Double? = 10.0,
    val battery_pct: Int? = 100,
    val status: String = "OK",
    val notes: String? = null,
    val timestamp: String? = null
)

@Serializable
data class TrekkerCheckInResponseDto(
    val checkin_id: String,
    val trip_id: String,
    val status: String,
    val received_at: String,
    val next_check_in_due: String,
    val message: String
)

@Serializable
data class EmergencySOSRequestDto(
    val trip_id: String,
    val device_id: String? = null,
    val latitude: Double,
    val longitude: Double,
    val elevation_m: Double? = 0.0,
    val battery_pct: Int? = 100,
    val emergency_type: String = "MEDICAL",
    val details: String? = null,
    val route_id: String? = null
)

@Serializable
data class EmergencySOSResponseDto(
    val incident_id: String,
    val trip_id: String,
    val status: String,
    val escalation_level: String,
    val broadcast_timestamp: String,
    val sar_export_url: String,
    val message: String
)


@Serializable
data class GroundedRouteSummaryDto(
    val route_id: String = "",
    val route_version: Int = 1,
    val summary_text: String = "",
    val direct_intersections: List<String> = emptyList(),
    val regional_context: List<String> = emptyList(),
    val nearby_facilities: List<String> = emptyList(),
    val stale_or_missing_sources: List<String> = emptyList(),
    val alternative_tradeoffs: List<String> = emptyList(),
    val is_ai_generated: Boolean = false,
    val disclaimer: String = ""
)

@Serializable
data class LatLonDto(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class RouteOfflinePackDto(
    val pack_id: String,
    val route_id: String,
    val route_version: Int = 1,
    val title: String,
    val origin_name: String,
    val destination_name: String,
    val origin: LatLonDto,
    val destination: LatLonDto,
    val bbox: List<Double> = emptyList(),
    val created_at: String,
    val expires_at: String,
    val is_stale: Boolean = false,
    val route_geometry: JsonElement? = null,
    val corridor_geojson: JsonElement? = null,
    val hazards_geojson: JsonElement? = null,
    val fires_geojson: JsonElement? = null,
    val facilities_geojson: JsonElement? = null,
    val weather_snapshot: List<JsonElement> = emptyList(),
    val conditions: List<JsonElement> = emptyList(),
    val alternatives: List<JsonElement> = emptyList(),
    val data_provenance: Map<String, String> = emptyMap(),
    val offline_usage_disclaimer: String = ""
)


@Serializable
data class TrackPointBatchItemDto(
    val latitude: Double,
    val longitude: Double,
    val altitude_m: Double? = null,
    val accuracy_m: Double? = null,
    val speed_kmh: Double? = null,
    val recorded_at: String? = null
)

@Serializable
data class TrackPointsSyncRequestDto(
    val points: List<TrackPointBatchItemDto>
)

@Serializable
data class TrackPointsSyncResponseDto(
    val trip_id: String,
    val points_received: Int = 0,
    val points_stored: Int = 0,
    val latest_latitude: Double,
    val latest_longitude: Double,
    val current_segment_index: Int = 0,
    val total_distance_km: Double = 0.0,
    val progress_pct: Double = 0.0,
    val is_off_route: Boolean = false,
    val distance_to_nearest_hazard_km: Double? = null,
    val nearest_hazard_title: String? = null,
    val hazard_warning_level: String = "LOW",
    val reroute_suggested: Boolean = false,
    val suggested_alternative_id: String? = null,
    val status: String = "ACTIVE",
    val message: String = ""
)
