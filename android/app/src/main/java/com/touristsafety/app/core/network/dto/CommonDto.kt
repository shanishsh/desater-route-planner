package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponseDto<T>(
    val items: List<T>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val has_more: Boolean,
    val provider_status: String? = "success",
    val retrieved_at: String? = null,
    val is_stale: Boolean = false,
    val source_provenance: String? = null
)

