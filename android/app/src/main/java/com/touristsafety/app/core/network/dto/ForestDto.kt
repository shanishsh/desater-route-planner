package com.touristsafety.app.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ForestPropertiesDto(
    val osm_id: Long = 0,
    val name: String = "Unnamed Woodland",
    val landuse: String? = null,
    val natural: String? = null,
    val wood_type: String = "mixed",
    val source: String = "OpenStreetMap"
)

@Serializable
data class ForestFeatureDto(
    val id: String,
    val type: String = "Feature",
    val geometry: JsonObject,
    val properties: ForestPropertiesDto = ForestPropertiesDto()
)

@Serializable
data class ForestGeoJsonResponseDto(
    val type: String = "FeatureCollection",
    val features: List<ForestFeatureDto> = emptyList(),
    val record_count: Int = 0,
    val provider_status: String = "HEALTHY",
    val attribution: String = "",
    val is_cached: Boolean = false,
    val diagnostic_message: String? = null
)

