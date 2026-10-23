package com.touristsafety.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_statuses")
data class ProviderStatusEntity(
    @PrimaryKey
    val providerId: String,
    val name: String,
    val category: String,
    val isConfigured: Boolean,
    val status: String,
    val lastAttemptAt: String?,
    val lastSuccessAt: String?,
    val lastHttpStatus: Int?,
    val recordCount: Int,
    val dataState: String,
    val isStale: Boolean,
    val diagnosticMessage: String,
    val attribution: String,
    val sourceUrl: String,
    val registrationUrl: String,
    val retrievedAtEpochMs: Long
)

