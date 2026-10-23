package com.touristsafety.app.core.model

enum class AlertSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class AlertCategory {
    WEATHER, HAZARD, TRANSPORT, CROWD
}

data class Alert(
    val id: String,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val category: AlertCategory,
    val affectedItineraryId: String? = null,
    val affectedSegmentId: String? = null,
    val sourceName: String,
    val sourceUrl: String? = null,
    val sourcePublishedAtEpochMs: Long,
    val retrievedAtEpochMs: Long,
    val freshnessThresholdSeconds: Int = 3600,
    val isSimulation: Boolean = true
) {
    /**
     * Dynamically calculates whether this alert has exceeded its freshness threshold.
     */
    fun isStale(currentEpochMs: Long = System.currentTimeMillis()): Boolean {
        val ageMs = currentEpochMs - sourcePublishedAtEpochMs
        return ageMs > (freshnessThresholdSeconds * 1000L)
    }

    /**
     * Calculates the alert age in minutes.
     */
    fun ageMinutes(currentEpochMs: Long = System.currentTimeMillis()): Long {
        val ageMs = currentEpochMs - sourcePublishedAtEpochMs
        return (ageMs / 60000L).coerceAtLeast(0L)
    }
}

