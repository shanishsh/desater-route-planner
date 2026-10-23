package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class DisasterAlertDto(
    val id: String,
    val title: String,
    val description: String,
    val hazard_category: String,
    val severity: String,
    val status: String,
    val version: Int,
    val is_simulated: Boolean,
    val issuing_authority: String,
    val source_url: String? = null,
    val source_verification_status: String,
    val required_protective_instructions: String,
    val center_latitude: Double,
    val center_longitude: Double,
    val affected_radius_meters: Float = 5000f,
    val polygon_geojson: String? = null,
    val affected_route_id: String? = null,
    val published_at: String,
    val received_at: String,
    val effective_at: String? = null,
    val expires_at: String? = null
)

@Serializable
data class SubscriberRegisterRequestDto(
    val phone_e164: String
)

@Serializable
data class SubscriberVerifyOtpRequestDto(
    val phone_e164: String,
    val otp: String,
    val consent_given: Boolean,
    val consent_version: String = "v1.0",
    val subscribed_areas: List<String> = emptyList(),
    val subscribed_itinerary_id: String? = null
)

@Serializable
data class SubscriberResponseDto(
    val id: String,
    val phone_masked: String,
    val status: String,
    val consent_given: Boolean,
    val subscribed_areas: List<String> = emptyList(),
    val subscribed_itinerary_id: String? = null
)

@Serializable
data class DeviceRegisterRequestDto(
    val installation_id: String,
    val fcm_token: String? = null,
    val platform: String = "android",
    val app_version: String = "1.0.0"
)

@Serializable
data class TestAlarmRequestDto(
    val hazard_category: String = "severe_weather",
    val severity: String = "WARNING"
)

