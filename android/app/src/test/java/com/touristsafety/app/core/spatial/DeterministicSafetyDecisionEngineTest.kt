package com.touristsafety.app.core.spatial

import com.touristsafety.app.core.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeterministicSafetyDecisionEngineTest {

    private lateinit var guidanceProvider: OfflineSafetyGuidanceProvider
    private lateinit var engine: DeterministicSafetyDecisionEngine

    @Before
    fun setup() {
        guidanceProvider = OfflineSafetyGuidanceProvider()
        engine = DeterministicSafetyDecisionEngine(guidanceProvider)
    }

    private fun createHazardItem(
        id: String,
        title: String,
        severity: String,
        distanceAhead: Double?,
        distanceCurrent: Double? = null
    ): RouteOrderedHazardItem {
        return RouteOrderedHazardItem(
            sequenceNumber = 1,
            sequenceLabel = "#01",
            feature = UnifiedMapFeature(
                id = id,
                title = title,
                subtitle = "Active hazard zone",
                category = MapFeatureCategory.HAZARD_ZONE,
                latitude = 30.5,
                longitude = 79.1
            ),
            distanceAlongRouteMeters = 1000.0,
            crossTrackDistanceMeters = 0.0,
            intersectionType = RouteIntersectionType.DIRECT_INTERSECTION,
            trekStatus = HazardTrekStatus.AHEAD,
            distanceAheadMeters = distanceAhead,
            distanceCurrentLocationMeters = distanceCurrent,
            estimatedMinutesAhead = 20,
            categoryType = HazardCategoryType.LANDSLIDE,
            severity = severity
        )
    }

    @Test
    fun testCriticalHazardAheadTriggersImmediateAlarm() {
        val criticalAhead = listOf(
            createHazardItem(
                id = "hz_landslide_01",
                title = "Massive Rockfall and Landslide",
                severity = "CRITICAL",
                distanceAhead = 1200.0
            )
        )

        val progress = TrekProgressMetrics(traveledDistanceMeters = 2000.0, remainingDistanceMeters = 5000.0)
        val weather = LiveWeatherContext()

        val event = engine.evaluate(
            upcomingHazards = criticalAhead,
            nearbyHazards = emptyList(),
            progress = progress,
            weather = weather,
            isOffline = false
        )

        assertNotNull("Critical hazard ahead must produce a SafetyEvent", event)
        assertEquals("hz_landslide_01", event!!.hazardId)
        assertTrue("Critical hazard ahead must require immediate alarm", event.requiresImmediateAlarm)
        assertTrue("Critical hazard ahead must require voice alert", event.requiresVoiceAlert)
        assertTrue("Must detect route obstruction", event.isDirectRouteObstruction)
        assertTrue("Spoken message must not be empty", event.deterministicSpokenText.isNotBlank())
    }

    @Test
    fun testNearbyHazardImmediateProximity() {
        val nearbyHazard = listOf(
            createHazardItem(
                id = "hz_flash_flood_01",
                title = "Flash Flood Torrent",
                severity = "CRITICAL",
                distanceAhead = null,
                distanceCurrent = 350.0
            )
        )

        val progress = TrekProgressMetrics(traveledDistanceMeters = 1000.0)
        val event = engine.evaluate(
            upcomingHazards = emptyList(),
            nearbyHazards = nearbyHazard,
            progress = progress,
            weather = LiveWeatherContext(),
            isOffline = false
        )

        assertNotNull("Nearby critical hazard within 600m must produce an event", event)
        assertEquals(SafetyEventType.HIGH_PRIORITY_HAZARD_WARNING, event!!.eventType)
        assertTrue(event.requiresImmediateAlarm)
    }

    @Test
    fun testSignificantRouteDeviationTriggersWarning() {
        val progress = TrekProgressMetrics(
            isOffRoute = true,
            crossTrackDistanceMeters = 135.0,
            traveledDistanceMeters = 1500.0
        )

        val event = engine.evaluate(
            upcomingHazards = emptyList(),
            nearbyHazards = emptyList(),
            progress = progress,
            weather = LiveWeatherContext(),
            isOffline = false
        )

        assertNotNull("Off-route deviation > 80m must trigger deviation event", event)
        assertEquals(SafetyEventType.SIGNIFICANT_ROUTE_DEVIATION, event!!.eventType)
        assertFalse("Deviation warning should not trigger loud siren", event.requiresImmediateAlarm)
        assertTrue("Deviation warning must trigger voice alert", event.requiresVoiceAlert)
        assertTrue(event.deterministicSpokenText.contains("off planned trail"))
    }

    @Test
    fun testSevereWeatherWarningEvaluation() {
        val weather = LiveWeatherContext(
            thunderstormProbabilityPct = 80,
            weatherDescription = "Severe Thunderstorms and Gale"
        )

        val event = engine.evaluate(
            upcomingHazards = emptyList(),
            nearbyHazards = emptyList(),
            progress = TrekProgressMetrics(),
            weather = weather,
            isOffline = false
        )

        assertNotNull("Thunderstorm weather must trigger warning event", event)
        assertEquals(SafetyEventType.NEWLY_RELEVANT_WEATHER_WARNING, event!!.eventType)
        assertTrue(event.requiresVoiceAlert)
        assertTrue(event.deterministicActionText.contains("Descend", ignoreCase = true))
    }

    @Test
    fun testEventDeduplicationAndReset() {
        val hazard = listOf(
            createHazardItem(
                id = "hz_dedup_01",
                title = "Active Landslide Chute",
                severity = "SEVERE",
                distanceAhead = 1500.0
            )
        )

        val progress = TrekProgressMetrics()
        val event1 = engine.evaluate(
            upcomingHazards = hazard,
            nearbyHazards = emptyList(),
            progress = progress,
            weather = LiveWeatherContext(),
            isOffline = false
        )
        assertNotNull(event1)

        // Immediate subsequent evaluation must be suppressed by cooldown
        val event2 = engine.evaluate(
            upcomingHazards = hazard,
            nearbyHazards = emptyList(),
            progress = progress,
            weather = LiveWeatherContext(),
            isOffline = false
        )
        assertNull("Subsequent identical alert within cooldown must be suppressed", event2)

        // Reset clears cooldowns
        engine.reset()
        val event3 = engine.evaluate(
            upcomingHazards = hazard,
            nearbyHazards = emptyList(),
            progress = progress,
            weather = LiveWeatherContext(),
            isOffline = false
        )
        assertNotNull("Alert must fire again after engine reset", event3)
    }

    @Test
    fun testOfflineSafetyGuidanceProviderConciseSpeech() {
        val event = SafetyEvent(
            eventType = SafetyEventType.ROUTE_CLOSURE_OBSTRUCTION,
            hazardId = "hz_slide_99",
            hazardTitle = "Landslide Obstruction",
            severity = "CRITICAL",
            distanceAheadMeters = 800.0,
            deterministicSpokenText = "",
            deterministicActionText = ""
        )

        val guidance = guidanceProvider.getGuidanceForEvent(event)
        assertNotNull(guidance)
        assertTrue("Must provide non-empty recommended action", guidance.recommendedAction.isNotBlank())
        assertTrue("Must provide what to avoid", guidance.whatToAvoid.isNotBlank())

        val wordCount = guidance.spokenMessage.split("\\s+".toRegex()).size
        assertTrue("Spoken message must be <= 30 words for walking safety (was $wordCount)", wordCount <= 30)
    }
}
