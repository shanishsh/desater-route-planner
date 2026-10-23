package com.touristsafety.app

import com.touristsafety.app.core.location.LocalSpatialEngine
import org.junit.Assert.*
import org.junit.Test

class LocalSpatialEngineTest {

    @Test
    fun testHaversineDistanceAccuracy() {
        // Distance between Shimla (31.1048, 77.1734) and Kufri (31.0979, 77.2674) is ~9.0 km
        val distance = LocalSpatialEngine.haversineDistanceMeters(
            lat1 = 31.1048, lon1 = 77.1734,
            lat2 = 31.0979, lon2 = 77.2674
        )
        assertTrue(distance in 8500.0..9500.0)
    }

    @Test
    fun testCrossTrackDistanceOnLine() {
        val startLat = 32.2400
        val startLon = 77.1800
        val endLat = 32.2500
        val endLon = 77.1800

        // Point directly in the middle of line segment
        val midLat = 32.2450
        val midLon = 77.1800

        val crossTrack = LocalSpatialEngine.crossTrackDistanceMeters(
            pointLat = midLat, pointLon = midLon,
            startLat = startLat, startLon = startLon,
            endLat = endLat, endLon = endLon
        )
        assertEquals(0.0, crossTrack, 1.0)
    }

    @Test
    fun testCrossTrackDistanceOffRoute() {
        val startLat = 32.2400
        val startLon = 77.1800
        val endLat = 32.2500
        val endLon = 77.1800

        // Point displaced east by ~0.002 deg (~180 meters)
        val offLat = 32.2450
        val offLon = 77.1820

        val crossTrack = LocalSpatialEngine.crossTrackDistanceMeters(
            pointLat = offLat, pointLon = offLon,
            startLat = startLat, startLon = startLon,
            endLat = endLat, endLon = endLon
        )
        assertTrue(crossTrack in 150.0..220.0)
    }

    @Test
    fun testPointInPolygonRayCasting() {
        // Simple rectangular polygon around Rohtang Pass region
        val polygon = listOf(
            Pair(32.3000, 77.2000),
            Pair(32.4000, 77.2000),
            Pair(32.4000, 77.3000),
            Pair(32.3000, 77.3000),
            Pair(32.3000, 77.2000)
        )

        // Point inside
        val inside = LocalSpatialEngine.isPointInPolygon(32.3500, 77.2500, polygon)
        assertTrue("Point should be inside polygon", inside)

        // Point clearly outside
        val outside = LocalSpatialEngine.isPointInPolygon(32.5000, 77.5000, polygon)
        assertFalse("Point should be outside polygon", outside)
    }

    @Test
    fun testSearchUncertaintyExpansion() {
        val initialAccuracy = 15.0 // 15 meters
        val elapsedMinutes = 60.0 // 1 hour elapsed

        // With 3.5 km/h max speed, 1 hour adds 3500 meters
        val radius = LocalSpatialEngine.computeUncertaintyRadiusMeters(
            lastGpsAccuracyM = initialAccuracy,
            elapsedMinutes = elapsedMinutes,
            maxSpeedKmh = 3.5
        )
        assertEquals(3515.0, radius, 5.0)
    }
}

