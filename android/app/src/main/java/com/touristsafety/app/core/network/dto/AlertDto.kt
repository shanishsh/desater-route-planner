package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AlertDto(
    val id: String,
    val title: String,
    val description: String,
    val severity: String,
    val category: String,
    val affected_itinerary_id: String? = null,
    val affected_segment_id: String? = null,
    val source_name: String,
    val source_url: String? = null,
    val source_published_at: String,
    val retrieved_at: String,
    val freshness_threshold_seconds: Int = 3600,
    val is_simulation: Boolean = true,
    val is_stale: Boolean = false,
    val age_minutes: Long = 0
)

