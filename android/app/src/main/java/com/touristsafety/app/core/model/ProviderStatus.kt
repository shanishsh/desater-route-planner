package com.touristsafety.app.core.model

data class ProviderStatus(
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
    val registrationUrl: String
)

