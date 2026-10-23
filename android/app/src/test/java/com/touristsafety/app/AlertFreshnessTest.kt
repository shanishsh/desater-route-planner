package com.touristsafety.app

import com.touristsafety.app.core.model.Alert
import com.touristsafety.app.core.model.AlertCategory
import com.touristsafety.app.core.model.AlertSeverity
import org.junit.Assert.*
import org.junit.Test

class AlertFreshnessTest {

    @Test
    fun `alert 5 minutes old with 60 minute threshold is fresh`() {
        val currentEpoch = 1700000000000L
        val fiveMinutesAgo = currentEpoch - (5 * 60 * 1000L)

        val alert = Alert(
            id = "alert-1",
            title = "[SIMULATED] Rain Risk Advisory",
            description = "High rain risk on ridge",
            severity = AlertSeverity.MEDIUM,
            category = AlertCategory.WEATHER,
            sourceName = "Meteo Center",
            sourcePublishedAtEpochMs = fiveMinutesAgo,
            retrievedAtEpochMs = currentEpoch,
            freshnessThresholdSeconds = 3600,
            isSimulation = true
        )

        assertFalse("Alert 5 min old should NOT be stale", alert.isStale(currentEpoch))
        assertEquals(5L, alert.ageMinutes(currentEpoch))
    }

    @Test
    fun `alert 75 minutes old with 60 minute threshold is stale`() {
        val currentEpoch = 1700000000000L
        val seventyFiveMinutesAgo = currentEpoch - (75 * 60 * 1000L)

        val alert = Alert(
            id = "alert-2",
            title = "[SIMULATED] Temporary Trail Obstruction",
            description = "Fallen timber at km 4.2",
            severity = AlertSeverity.HIGH,
            category = AlertCategory.HAZARD,
            sourceName = "Forestry Post",
            sourcePublishedAtEpochMs = seventyFiveMinutesAgo,
            retrievedAtEpochMs = currentEpoch,
            freshnessThresholdSeconds = 3600,
            isSimulation = true
        )

        assertTrue("Alert 75 min old should BE stale", alert.isStale(currentEpoch))
        assertEquals(75L, alert.ageMinutes(currentEpoch))
    }

    @Test
    fun `alert 180 minutes old with 60 minute threshold is stale`() {
        val currentEpoch = 1700000000000L
        val oneEightyMinutesAgo = currentEpoch - (180 * 60 * 1000L)

        val alert = Alert(
            id = "alert-3",
            title = "[SIMULATED] Transport Disruption",
            description = "Shuttle bus delay at exit",
            severity = AlertSeverity.LOW,
            category = AlertCategory.TRANSPORT,
            sourceName = "Valley Transit",
            sourcePublishedAtEpochMs = oneEightyMinutesAgo,
            retrievedAtEpochMs = currentEpoch,
            freshnessThresholdSeconds = 3600,
            isSimulation = true
        )

        assertTrue("Alert 180 min old should BE stale", alert.isStale(currentEpoch))
        assertEquals(180L, alert.ageMinutes(currentEpoch))
    }
}

