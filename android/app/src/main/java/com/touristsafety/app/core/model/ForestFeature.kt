package com.touristsafety.app.core.model

data class ForestFeature(
    val id: String,
    val osmId: Long,
    val name: String,
    val woodType: String,
    val coordinates: List<Pair<Double, Double>>, // List of (lat, lon)
    val isPolygon: Boolean,
    val isCached: Boolean = true
)

