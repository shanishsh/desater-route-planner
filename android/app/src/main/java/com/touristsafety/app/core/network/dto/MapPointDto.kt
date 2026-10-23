package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class MapPointDto(
    val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val phone: String? = null,
    val description: String? = null,
    val verification_status: String = "verified",
    val verification_timestamp: String,
    val provenance: String = "Official Registry"
)

