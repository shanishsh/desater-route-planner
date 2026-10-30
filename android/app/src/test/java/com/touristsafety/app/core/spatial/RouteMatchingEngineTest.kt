package com.touristsafety.app.core.spatial

import com.touristsafety.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class RouteMatchingEngineTest {

    // Gaurikund to Kedarnath realistic trail coordinates (4 vertices)
    private val kedarnathTrail = listOf(
        30.6534 to 79.0268, // WP 0: Gaurikund (Origin)
        30.6800 to 79.0400, // WP 1: Jungle Chatti (~3.2 km)
        30.7100 to 79.0550, // WP 2: Bheembali (~7.0 km)
        30.7339 to 79.0669  // WP 3: Kedarnath Temple (~10.0 km)
    )

    private val cumDistances = RouteMatchingEngine.buildCumulativeDistances(kedarnathTrail)

    @Test
    fun testCumulativeDistancesMonotonic() {
        assertEquals(4, cumDistances.size)
        assertEquals(0.0, cumDistances[0], 0.001)
        assertTrue(cumDistances[1] > 3000.0)
        assertTrue(cumDistances[2] > cumDistances[1])
        assertTrue(cumDistances[3] > cumDistances[2])
    }

    @Test
    fun testProjectPointDirectlyOnTrail() {
        // Test projecting WP 1 directly
        val projection = RouteMatchingEngine.projectPointToRoute(
            lat = 30.6800,
            lon = 79.0400,
            routePoints = kedarnathTrail,
            cumDistances = cumDistances,
            previousChainageMeters = 0.0
        )

        assertEquals("Cross track distance should be zero or negligible", 0.0, projection.crossTrackDistanceMeters, 1.0)
        assertEquals("Segment index should match segment 0-1", 0, projection.segmentIndex)
        assertEquals("Chainage should closely match cumulative distance to WP 1", cumDistances[1], projection.chainageMeters, 2.0)
        assertFalse("Should not be off route", projection.isOffRoute)
    }

    @Test
    fun testCrossTrackOffRouteThreshold() {
        // Point on trail segment 1
        val onTrail = 30.6800 to 79.0400

        // Point deviated slightly by ~25 meters
        val slightDeviation = RouteMatchingEngine.projectPointToRoute(
            lat = onTrail.first + 0.0002, // ~22m
            lon = onTrail.second,
            routePoints = kedarnathTrail,
            cumDistances = cumDistances,
            previousChainageMeters = cumDistances[0]
        )
        assertFalse("Deviation of ~22m must NOT trigger off-route (>80m)", slightDeviation.isOffRoute)
        assertTrue("Cross track should be approx 22m", slightDeviation.crossTrackDistanceMeters < 50.0)

        // Point deviated significantly by ~200 meters perpendicularly
        val majorDeviation = RouteMatchingEngine.projectPointToRoute(
            lat = onTrail.first,
            lon = onTrail.second + 0.0025, // ~240m east perpendicular to trail
            routePoints = kedarnathTrail,
            cumDistances = cumDistances,
            previousChainageMeters = cumDistances[0]
        )
        assertTrue("Deviation of ~240m MUST trigger isOffRoute", majorDeviation.isOffRoute)
        assertTrue("Cross track should exceed 80m", majorDeviation.crossTrackDistanceMeters > 80.0)
    }

    @Test
    fun testSwitchbackMonotonicProtection() {
        // Mountain switchback: a zigzag trail where leg A (0..500m) and leg B (500..1000m) run parallel 30m apart
        val switchbackTrail = listOf(
            30.6500 to 79.0200, // Start of lower leg
            30.6500 to 79.0250, // Hairpin turn (~480m)
            30.6503 to 79.0200  // Upper leg running back parallel ~33m north (~960m)
        )
        val sbCumDist = RouteMatchingEngine.buildCumulativeDistances(switchbackTrail)

        // Trekker is currently walking the lower leg at chainage 150m
        val currentChainage = 150.0
        val currentLoc = 30.6500 to 79.0215 // on lower leg

        val match = RouteMatchingEngine.projectPointToRoute(
            lat = currentLoc.first,
            lon = currentLoc.second,
            routePoints = switchbackTrail,
            cumDistances = sbCumDist,
            previousChainageMeters = currentChainage
        )

        // Even though upper leg is only 33m away at 30.6503, the trekker must NOT jump to 800m chainage
        assertEquals(0, match.segmentIndex)
        assertTrue("Chainage must stay near 140-160m on lower leg", match.chainageMeters < 250.0)
    }

    @Test
    fun testHazardClassificationAheadVsPassed() {
        val totalDist = cumDistances.last()
        val currentChainage = cumDistances[1] // Trekker is at Jungle Chatti (~3.2 km)

        // 1. Hazard at Bheembali (~7.0 km) -> Ahead on route
        val hBheembali = UnifiedMapFeature(
            id = "h_bheembali",
            title = "Active Landslide",
            category = MapFeatureCategory.HAZARD_ZONE,
            latitude = 30.7100,
            longitude = 79.0550
        )
        val classAhead = RouteMatchingEngine.classifyHazard(
            feature = hBheembali,
            routePoints = kedarnathTrail,
            cumDistances = cumDistances,
            currentChainageMeters = currentChainage,
            currentLat = 30.6800,
            currentLon = 79.0400,
            gpsAccuracyMeters = 10.0f,
            isOffRoute = false
        )
        assertEquals(HazardCategoryClassification.AHEAD_ON_ROUTE, classAhead.classification)
        val distAhead = classAhead.distanceAheadMeters ?: 0.0
        assertTrue("Distance ahead should be around 3.8 km", distAhead > 3000.0)

        // 2. Hazard at Gaurikund (Origin, 0 km) -> Passed (since trekker is at 3.2 km)
        val hGaurikund = UnifiedMapFeature(
            id = "h_gaurikund",
            title = "Past Flood Debris",
            category = MapFeatureCategory.HAZARD_ZONE,
            latitude = 30.6534,
            longitude = 79.0268
        )
        val classPassed = RouteMatchingEngine.classifyHazard(
            feature = hGaurikund,
            routePoints = kedarnathTrail,
            cumDistances = cumDistances,
            currentChainageMeters = currentChainage,
            currentLat = 30.6800,
            currentLon = 79.0400,
            gpsAccuracyMeters = 10.0f,
            isOffRoute = false
        )
        assertEquals(HazardCategoryClassification.PASSED, classPassed.classification)

        // 3. Hazard in immediate physical radius (120m away from current GPS) -> Near Current Location
        val hNearby = UnifiedMapFeature(
            id = "h_nearby",
            title = "Immediate Rockfall",
            category = MapFeatureCategory.HAZARD_ZONE,
            latitude = 30.6800 + 0.0008, // ~90m
            longitude = 79.0400
        )
        val classNearby = RouteMatchingEngine.classifyHazard(
            feature = hNearby,
            routePoints = kedarnathTrail,
            cumDistances = cumDistances,
            currentChainageMeters = currentChainage,
            currentLat = 30.6800,
            currentLon = 79.0400,
            gpsAccuracyMeters = 10.0f,
            isOffRoute = false
        )
        assertEquals(HazardCategoryClassification.NEAR_CURRENT_LOCATION, classNearby.classification)

        // 4. Hazard far off to side (3.5 km off trail) -> Nearby Off Route
        val hOffRoute = UnifiedMapFeature(
            id = "h_off",
            title = "Distant Valley Fire",
            category = MapFeatureCategory.ACTIVE_FIRE_DETECTION,
            latitude = 30.6800 + 0.032, // ~3.5 km
            longitude = 79.0400
        )
        val classOff = RouteMatchingEngine.classifyHazard(
            feature = hOffRoute,
            routePoints = kedarnathTrail,
            cumDistances = cumDistances,
            currentChainageMeters = currentChainage,
            currentLat = 30.6800,
            currentLon = 79.0400,
            gpsAccuracyMeters = 10.0f,
            isOffRoute = false
        )
        assertEquals(HazardCategoryClassification.NEARBY_OFF_ROUTE, classOff.classification)
    }

    @Test
    fun testPolygonHazardEntryExitChainage() {
        // Define a rectangular polygon hazard centered at Jungle Chatti (WP 1: 30.6800, 79.0400)
        // Polygon spans from 30.6780 to 30.6820 lat, 79.0380 to 79.0420 lon
        val polygon = listOf(
            30.6780 to 79.0380,
            30.6820 to 79.0380,
            30.6820 to 79.0420,
            30.6780 to 79.0420,
            30.6780 to 79.0380
        )

        val entryExit = RouteMatchingEngine.calculatePolygonEntryExit(
            polygon = polygon,
            routePoints = kedarnathTrail,
            cumDistances = cumDistances
        )

        assertNotNull("Should detect intersection with polygon hazard", entryExit)
        val entryChainage = entryExit!!.first
        val exitChainage = entryExit.second
        assertTrue("Entry chainage must be less than exit chainage", entryChainage < exitChainage)
        assertTrue("Entry chainage should be just before WP 1 (~3.2 km)", entryChainage < cumDistances[1])
        assertTrue("Exit chainage should be just after WP 1 (~3.2 km)", exitChainage > cumDistances[1])
    }

    @Test
    fun testDisambiguateDistanceMetrics() {
        val traveledOdometer = 3400.0 // 3.4 km walked on GPS
        val routeProgress = 3200.0   // 3.2 km projected along trail
        val totalRoute = cumDistances.last()
        val remaining = maxOf(0.0, totalRoute - routeProgress)
        val dest = kedarnathTrail.last()
        val straightLineToDest = RouteMatchingEngine.haversineDistanceMeters(30.6800, 79.0400, dest.first, dest.second)

        val metrics = TrekProgressMetrics(
            traveledDistanceMeters = traveledOdometer,
            routeProgressMeters = routeProgress,
            totalRouteDistanceMeters = totalRoute,
            remainingDistanceMeters = remaining,
            straightLineToDestMeters = straightLineToDest,
            progressPct = ((routeProgress / totalRoute) * 100).toFloat(),
            isOffRoute = false
        )

        assertEquals("3.40 km", metrics.formattedTraveledDistance())
        assertTrue("Remaining distance should be positive", metrics.remainingDistanceMeters > 5000.0)
        assertTrue("Straight line distance to destination should be shorter than winding trail", straightLineToDest < remaining)
    }
}

