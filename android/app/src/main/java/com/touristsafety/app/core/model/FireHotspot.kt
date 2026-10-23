package com.touristsafety.app.core.model

data class FireHotspot(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val brightness: Double,
    val confidence: String,
    val frpMw: Double,
    val acquisitionDate: String,
    val acquisitionTime: String,
    val satellite: String,
    val isSimulated: Boolean
)

