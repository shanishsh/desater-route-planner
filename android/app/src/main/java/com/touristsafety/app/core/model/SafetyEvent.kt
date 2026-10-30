package com.touristsafety.app.core.model

import java.util.UUID

enum class SafetyEventType {
    CRITICAL_HAZARD_WARNING,
    HIGH_PRIORITY_HAZARD_WARNING,
    APPROACHING_HAZARD,
    NEWLY_RELEVANT_WEATHER_WARNING,
    ROUTE_CLOSURE_OBSTRUCTION,
    COMMUNITY_HAZARD_REPORT,
    SIGNIFICANT_ROUTE_DEVIATION,
    CONNECTIVITY_LOSS,
    STALE_SAFETY_INFORMATION,
    NO_VERIFIED_ALTERNATIVE_AVAILABLE
}

data class SafetyEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: SafetyEventType,
    val hazardId: String? = null,
    val hazardTitle: String,
    val severity: String,
    val distanceAheadMeters: Double? = null,
    val distanceCurrentMeters: Double = 0.0,
    val isDirectRouteObstruction: Boolean = false,
    val hasVerifiedAlternative: Boolean = false,
    val requiresImmediateAlarm: Boolean = false,
    val requiresVoiceAlert: Boolean = true,
    val deterministicSpokenText: String,
    val deterministicActionText: String,
    val triggeredAtEpochMs: Long = System.currentTimeMillis()
)

