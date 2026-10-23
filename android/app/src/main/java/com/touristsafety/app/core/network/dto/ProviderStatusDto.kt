package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProviderStatusDto(
    val provider_id: String,
    val name: String,
    val category: String,
    val is_configured: Boolean,
    val status: String,
    val last_attempt_at: String? = null,
    val last_success_at: String? = null,
    val last_http_status: Int? = null,
    val record_count: Int = 0,
    val cache_age_seconds: Int = 0,
    val data_state: String = "LIVE",
    val is_stale: Boolean = false,
    val diagnostic_message: String = "",
    val attribution: String = "",
    val source_url: String = "",
    val registration_url: String = ""
)

