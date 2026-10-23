package com.touristsafety.app.core.model

enum class PointCategory {
    EMERGENCY_SERVICE,
    MEDICAL,
    CLINIC,
    POLICE,
    FIRE_STATION,
    SHELTER,
    VISITOR_INFO,
    TRANSPORT,
    VERIFIED_PUBLIC
}

data class MapPoint(
    val id: String,
    val name: String,
    val category: PointCategory,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val phone: String? = null,
    val description: String? = null,
    val verificationStatus: String = "verified",
    val verificationTimestampEpochMs: Long = System.currentTimeMillis(),
    val provenance: String = "Official Public Safety Directory"
)

