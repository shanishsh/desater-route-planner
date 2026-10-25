package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TrailReportCreateDto(
    val category: String,
    val severity: String = "MEDIUM",
    val passability: String = "DIFFICULT_REQUIRES_CAUTION",
    val latitude: Double,
    val longitude: Double,
    val location_accuracy_meters: Double = 10.0,
    val location_source: String = "gps",
    val description: String? = null,
    val photo_url: String? = null,
    val route_id: String? = null,
    val route_version: Int? = 1,
    val idempotency_key: String? = null,
    val device_id: String? = null,
    val observation_time: String? = null
)

@Serializable
data class TrailReportResponseDto(
    val id: String,
    val category: String,
    val severity: String,
    val passability: String,
    val latitude: Double,
    val longitude: Double,
    val location_accuracy_meters: Double = 10.0,
    val location_source: String = "gps",
    val observation_time: String,
    val submission_time: String,
    val description: String? = null,
    val photo_url: String? = null,
    val route_id: String? = null,
    val route_version: Int? = null,
    val verification_status: String = "UNVERIFIED",
    val confirmations_count: Int = 0,
    val disputes_count: Int = 0,
    val last_confirmed_at: String? = null,
    val expires_at: String,
    val idempotency_key: String? = null,
    val is_expired: Boolean = false,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class TrailReportListResponseDto(
    val items: List<TrailReportResponseDto> = emptyList(),
    val total: Int = 0,
    val active_count: Int = 0,
    val corroborated_count: Int = 0
)

@Serializable
data class TrailReportInteractionCreateDto(
    val interaction_type: String, // CONFIRM, DISPUTE, RESOLVE
    val comment: String? = null,
    val device_id: String? = null
)

@Serializable
data class TrailReportUploadResponseDto(
    val success: Boolean,
    val photo_url: String,
    val filename: String,
    val size_bytes: Long,
    val content_type: String
)

