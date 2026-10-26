package com.touristsafety.app

import com.touristsafety.app.core.model.*
import com.touristsafety.app.core.spatial.RouteHazardSequenceCalculator
import org.junit.Assert.*
import org.junit.Test

class RouteHazardSequenceCalculatorTest {

    // A sample trail from Gaurikund (30.6534, 79.0268) up through Jungle Chatti (30.6800, 79.0400),
    // Bheembali (30.7100, 79.0550), to Kedarnath Temple (30.7339, 79.0669)
    private val kedarnathTrail = listOf(
        30.6534 to 79.0268, // WP 0: Gaurikund
        30.6800 to 79.0400, // WP 1: Jungle Chatti (~3.5 km)
        30.7100 to 79.0550, // WP 2: Bheembali (~7.5 km)
        30.7339 to 79.0669  // WP 3: Kedarnath Temple (~11.5 km)
    )

    @Test
    fun testEmptyRouteOrFeaturesReturnsEmptySequence() {
        val emptyRoute = emptyList<Pair<Double, Double>>()
        val singlePointRoute = listOf(30.6534 to 79.0268)
        val dummyFeature = UnifiedMapFeature(
            id = "f1",
            category = MapFeatureCategory.HAZARD_ZONE,
            title = "Test",
            latitude = 30.6800,
            longitude = 79.0400
        )

        assertTrue(RouteHazardSequenceCalculator.calculateSequence(emptyRoute, listOf(dummyFeature)).isEmpty())
        assertTrue(RouteHazardSequenceCalculator.calculateSequence(singlePointRoute, listOf(dummyFeature)).isEmpty())
        assertTrue(RouteHazardSequenceCalculator.calculateSequence(kedarnathTrail, emptyList()).isEmpty())
    }

    @Test
    fun testSequenceOrdersHazardsStrictlyAlongRoutePolyline() {
        // Create 3 hazards in reverse order of encounter:
        // Hazard 3 is near Kedarnath (end)
        // Hazard 2 is near Bheembali (middle)
        // Hazard 1 is near Jungle Chatti (start)
        val hKedarnath = UnifiedMapFeature(
            id = "h_kedarnath",
            category = MapFeatureCategory.HAZARD_ZONE,
            title = "Heavy Snow and Freezing Rain",
            latitude = 30.7330,
            longitude = 79.0660
        )
        val hBheembali = UnifiedMapFeature(
            id = "h_bheembali",
            category = MapFeatureCategory.HAZARD_ZONE,
            title = "Active Rockfall and Landslide Cliff",
            latitude = 30.7105,
            longitude = 79.0552
        )
        val hJungleChatti = UnifiedMapFeature(
            id = "h_jungle_chatti",
            category = MapFeatureCategory.HAZARD_ZONE,
            title = "Severe Thunderstorm Alert",
            latitude = 30.6802,
            longitude = 79.0405
        )

        // Pass in jumbled order: Kedarnath, Jungle Chatti, Bheembali
        val features = listOf(hKedarnath, hJungleChatti, hBheembali)

        val sequence = RouteHazardSequenceCalculator.calculateSequence(
            routePoints = kedarnathTrail,
            features = features,
            corridorDistanceMeters = 5000.0
        )

        assertEquals("Should contain all 3 corridor hazards", 3, sequence.size)

        // First encountered along trail must be Jungle Chatti
        assertEquals("01", sequence[0].sequenceLabel)
        assertEquals("h_jungle_chatti", sequence[0].feature.id)
        assertEquals(HazardCategoryType.THUNDERSTORM, sequence[0].categoryType)

        // Second encountered must be Bheembali
        assertEquals("02", sequence[1].sequenceLabel)
        assertEquals("h_bheembali", sequence[1].feature.id)
        assertEquals(HazardCategoryType.LANDSLIDE, sequence[1].categoryType)

        // Third encountered must be Kedarnath
        assertEquals("03", sequence[2].sequenceLabel)
        assertEquals("h_kedarnath", sequence[2].feature.id)
        assertEquals(HazardCategoryType.HEAVY_RAIN, sequence[2].categoryType)

        // Distances along route must be strictly monotonically increasing
        assertTrue(sequence[0].distanceAlongRouteMeters < sequence[1].distanceAlongRouteMeters)
        assertTrue(sequence[1].distanceAlongRouteMeters < sequence[2].distanceAlongRouteMeters)
    }

    @Test
    fun testPolygonRouteIntersectionDistinguishesDirectVsCorridor() {
        // Direct intersecting polygon: crosses trail near Jungle Chatti
        val intersectingPolygon = listOf(
            30.6780 to 79.0380,
            30.6820 to 79.0380,
            30.6820 to 79.0420,
            30.6780 to 79.0420
        )
        val directHazard = UnifiedMapFeature(
            id = "poly_direct",
            category = MapFeatureCategory.HAZARD_ZONE,
            title = "Flash Flooding River Surge",
            latitude = 30.6800,
            longitude = 79.0400,
            geometryPolygon = intersectingPolygon
        )

        // Nearby corridor polygon: 2.5 km west of trail (does not intersect route line)
        val nearbyPolygon = listOf(
            30.6800 to 79.0100,
            30.6850 to 79.0100,
            30.6850 to 79.0150,
            30.6800 to 79.0150
        )
        val corridorHazard = UnifiedMapFeature(
            id = "poly_corridor",
            category = MapFeatureCategory.HAZARD_ZONE,
            title = "Forest Wildfire Buffer",
            latitude = 30.6825,
            longitude = 79.0125,
            geometryPolygon = nearbyPolygon
        )

        val sequence = RouteHazardSequenceCalculator.calculateSequence(
            routePoints = kedarnathTrail,
            features = listOf(directHazard, corridorHazard),
            corridorDistanceMeters = 5000.0
        )

        assertEquals(2, sequence.size)
        val directItem = sequence.first { it.feature.id == "poly_direct" }
        val corridorItem = sequence.first { it.feature.id == "poly_corridor" }

        assertEquals(RouteIntersectionType.DIRECT_INTERSECTION, directItem.intersectionType)
        assertEquals(RouteIntersectionType.CORRIDOR_NEARBY, corridorItem.intersectionType)
        assertTrue("Corridor hazard cross-track distance should be > 1000m", corridorItem.crossTrackDistanceMeters > 1000.0)
    }

    @Test
    fun testActiveTrekProgressClassifiesAheadNearbyAndPassed() {
        val h1 = UnifiedMapFeature(
            id = "h1",
            category = MapFeatureCategory.HAZARD_ZONE,
            title = "Obstruction at Jungle Chatti",
            latitude = 30.6800,
            longitude = 79.0400 // ~3.5 km from start
        )
        val h2 = UnifiedMapFeature(
            id = "h2",
            category = MapFeatureCategory.HAZARD_ZONE,
            title = "Landslide at Bheembali",
            latitude = 30.7100,
            longitude = 79.0550 // ~7.5 km from start
        )

        // Trekker is currently at 30.6900, 79.0450 (past Jungle Chatti, before Bheembali, ~5.0 km)
        val trekkerLocation = 30.6900 to 79.0450

        val sequence = RouteHazardSequenceCalculator.calculateSequence(
            routePoints = kedarnathTrail,
            features = listOf(h1, h2),
            userLocation = trekkerLocation,
            corridorDistanceMeters = 5000.0
        )

        assertEquals(2, sequence.size)
        val item1 = sequence.first { it.feature.id == "h1" }
        val item2 = sequence.first { it.feature.id == "h2" }

        // h1 is behind the trekker -> PASSED
        assertEquals(HazardTrekStatus.PASSED, item1.trekStatus)
        assertNull("Passed item should not have distance ahead", item1.distanceAheadMeters)

        // h2 is ahead on the trail -> AHEAD
        assertEquals(HazardTrekStatus.AHEAD, item2.trekStatus)
        assertNotNull("Upcoming item must have distance ahead", item2.distanceAheadMeters)
        assertTrue(item2.distanceAheadMeters!! > 1000.0)
        assertNotNull(item2.estimatedMinutesAhead)
    }

    @Test
    fun testOffRouteTrekkerSetsUncertainStatus() {
        val hazard = UnifiedMapFeature(
            id = "h1",
            category = MapFeatureCategory.HAZARD_ZONE,
            title = "Thunderstorm Alert",
            latitude = 30.6800,
            longitude = 79.0400
        )

        // Trekker is 5 km away from trail (off-route)
        val offRouteLocation = 30.6800 to 78.9500

        val sequence = RouteHazardSequenceCalculator.calculateSequence(
            routePoints = kedarnathTrail,
            features = listOf(hazard),
            userLocation = offRouteLocation,
            corridorDistanceMeters = 10000.0
        )

        assertEquals(1, sequence.size)
        assertEquals(HazardTrekStatus.UNCERTAIN, sequence[0].trekStatus)
    }

    @Test
    fun testHazardCategoryKeywordDetection() {
        val tStormFeat = UnifiedMapFeature("t1", MapFeatureCategory.HAZARD_ZONE, "Severe Thunderstorm Warning", latitude = 0.0, longitude = 0.0)
        assertEquals(HazardCategoryType.THUNDERSTORM, HazardCategoryType.fromFeature(tStormFeat))

        val rainFeat = UnifiedMapFeature("r1", MapFeatureCategory.HAZARD_ZONE, "Intense Rain and Cloudburst", latitude = 0.0, longitude = 0.0)
        assertEquals(HazardCategoryType.HEAVY_RAIN, HazardCategoryType.fromFeature(rainFeat))

        val floodFeat = UnifiedMapFeature("f1", MapFeatureCategory.HAZARD_ZONE, "River Flooding Flash Inundation", latitude = 0.0, longitude = 0.0)
        assertEquals(HazardCategoryType.FLOODING, HazardCategoryType.fromFeature(floodFeat))

        val slideFeat = UnifiedMapFeature("s1", MapFeatureCategory.HAZARD_ZONE, "Rockfall and Debris Slope", latitude = 0.0, longitude = 0.0)
        assertEquals(HazardCategoryType.LANDSLIDE, HazardCategoryType.fromFeature(slideFeat))

        val fireFeat = UnifiedMapFeature("w1", MapFeatureCategory.ACTIVE_FIRE_DETECTION, "NASA Thermal Hotspot", latitude = 0.0, longitude = 0.0)
        assertEquals(HazardCategoryType.WILDFIRE, HazardCategoryType.fromFeature(fireFeat))

        val quakeFeat = UnifiedMapFeature("q1", MapFeatureCategory.HAZARD_ZONE, "Seismic Tremor M4.2", latitude = 0.0, longitude = 0.0)
        assertEquals(HazardCategoryType.EARTHQUAKE, HazardCategoryType.fromFeature(quakeFeat))

        val closureFeat = UnifiedMapFeature("c1", MapFeatureCategory.HAZARD_ZONE, "Road Damaged and Trail Closure", latitude = 0.0, longitude = 0.0)
        assertEquals(HazardCategoryType.TRAIL_CLOSURE, HazardCategoryType.fromFeature(closureFeat))

        val hospFeat = UnifiedMapFeature("h1", MapFeatureCategory.HOSPITAL, "Kedarnath Base Hospital", latitude = 0.0, longitude = 0.0)
        assertEquals(HazardCategoryType.SAFETY_FACILITY, HazardCategoryType.fromFeature(hospFeat))
    }
}

