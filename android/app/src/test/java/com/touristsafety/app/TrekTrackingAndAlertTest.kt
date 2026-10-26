package com.touristsafety.app

import com.touristsafety.app.core.location.GpsFilter
import com.touristsafety.app.core.location.GpsFilterStatus
import com.touristsafety.app.core.location.TrekDistanceCalculator
import org.junit.Assert.*
import org.junit.Test

class TrekTrackingAndAlertTest {

    @Test
    fun `testGpsFilterRejectsLowAccuracyAndStationaryDrift`() {
        val baseLat = 30.7339
        val baseLon = 79.0669
        val now = 1700000000000L

        // 1. Initial point should be accepted
        val initialResult = GpsFilter.filter(
            lastAcceptedLat = null,
            lastAcceptedLon = null,
            lastAcceptedEpochMs = null,
            lat = baseLat,
            lon = baseLon,
            accuracyMeters = 10.0f,
            speedKmh = 3.5,
            timestampEpochMs = now
        )
        assertEquals(GpsFilterStatus.ACCEPTED, initialResult.status)
        assertTrue(initialResult.isAcceptedForDistance)
        assertEquals(0.0, initialResult.deltaMeters, 0.001)

        // 2. Low accuracy reading (> 35m) must be rejected
        val lowAccResult = GpsFilter.filter(
            lastAcceptedLat = baseLat,
            lastAcceptedLon = baseLon,
            lastAcceptedEpochMs = now,
            lat = baseLat + 0.0001,
            lon = baseLon + 0.0001,
            accuracyMeters = 42.0f,
            speedKmh = 3.0,
            timestampEpochMs = now + 5000L
        )
        assertEquals(GpsFilterStatus.REJECTED_LOW_ACCURACY, lowAccResult.status)
        assertFalse(lowAccResult.isAcceptedForDistance)

        // 3. Stationary GPS drift (< 2.5m and speed < 1.8 km/h) must be rejected
        val driftResult = GpsFilter.filter(
            lastAcceptedLat = baseLat,
            lastAcceptedLon = baseLon,
            lastAcceptedEpochMs = now,
            lat = baseLat + 0.00001, // ~1.1 meters
            lon = baseLon,
            accuracyMeters = 8.0f,
            speedKmh = 0.4,
            timestampEpochMs = now + 5000L
        )
        assertEquals(GpsFilterStatus.REJECTED_STATIONARY_DRIFT, driftResult.status)
        assertFalse(driftResult.isAcceptedForDistance)

        // 4. Legitimate walking step (~15m at 4 km/h) must be accepted
        val walkingResult = GpsFilter.filter(
            lastAcceptedLat = baseLat,
            lastAcceptedLon = baseLon,
            lastAcceptedEpochMs = now,
            lat = baseLat + 0.00015, // ~16.6 meters
            lon = baseLon,
            accuracyMeters = 10.0f,
            speedKmh = 4.2,
            timestampEpochMs = now + 5000L
        )
        assertEquals(GpsFilterStatus.ACCEPTED, walkingResult.status)
        assertTrue(walkingResult.isAcceptedForDistance)
        assertTrue("Distance delta should be positive", walkingResult.deltaMeters > 10.0)
    }

    @Test
    fun `testGpsFilterRejectsImpossibleSpeedJumps`() {
        val baseLat = 30.7339
        val baseLon = 79.0669
        val now = 1700000000000L

        // Jump 500 meters in 5 seconds = 100 m/s (~360 km/h) -> Multipath error
        val teleportResult = GpsFilter.filter(
            lastAcceptedLat = baseLat,
            lastAcceptedLon = baseLon,
            lastAcceptedEpochMs = now,
            lat = baseLat + 0.0045, // ~500 meters
            lon = baseLon,
            accuracyMeters = 12.0f,
            speedKmh = 12.0,
            timestampEpochMs = now + 5000L
        )
        assertEquals(GpsFilterStatus.REJECTED_IMPOSSIBLE_JUMP, teleportResult.status)
        assertFalse(teleportResult.isAcceptedForDistance)
    }

    @Test
    fun `testTrekDistanceCalculatorSequentialHaversine`() {
        // Gaurikund to Kedarnath known trail coordinates
        val gaurikundLat = 30.6534
        val gaurikundLon = 79.0268
        val kedarnathLat = 30.7339
        val kedarnathLon = 79.0669

        val straightLineMeters = TrekDistanceCalculator.computeDistanceMeters(
            gaurikundLat, gaurikundLon,
            kedarnathLat, kedarnathLon
        )

        // Direct displacement is approx 9.7 km
        assertTrue("Displacement should be between 9km and 11km", straightLineMeters in 9000.0..11000.0)

        val km = TrekDistanceCalculator.metersToKm(straightLineMeters)
        assertTrue("Km representation should match", km in 9.0..11.0)
    }

    @Test
    fun `testTrekDistanceZeroForSameCoordinates`() {
        val dist = TrekDistanceCalculator.computeDistanceMeters(
            30.3165, 78.0322,
            30.3165, 78.0322
        )
        assertEquals(0.0, dist, 0.0001)
    }
}

