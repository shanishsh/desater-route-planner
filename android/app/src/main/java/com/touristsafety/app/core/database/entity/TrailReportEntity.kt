package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trail_reports")
data class TrailReportEntity(
    @PrimaryKey val id: String,
    val category: String,
    val severity: String,
    val passability: String,
    val latitude: Double,
    val longitude: Double,
    val locationAccuracyMeters: Double,
    val locationSource: String,
    val description: String?,
    val localPhotoUri: String?,
    val serverPhotoUrl: String?,
    val routeId: String?,
    val routeVersion: Int?,
    val idempotencyKey: String,
    val verificationStatus: String = "UNVERIFIED",
    val confirmationsCount: Int = 0,
    val disputesCount: Int = 0,
    val observationTimeEpochMs: Long,
    val syncStatus: String = "PENDING", // PENDING, SYNCED, FAILED
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

