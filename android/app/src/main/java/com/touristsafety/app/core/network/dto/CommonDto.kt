package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponseDto<T>(
    val items: List<T>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val has_more: Boolean
)

