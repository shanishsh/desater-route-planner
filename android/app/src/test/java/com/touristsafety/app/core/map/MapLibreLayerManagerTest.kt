package com.touristsafety.app.core.map

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MapLibreLayerManagerTest {

    @Test
    fun `ensureFeatureCollection preserves multiline FeatureCollection without nesting geometry`() {
        val multilineFc = """
            {
              "type": "FeatureCollection",
              "name": "Uttarakhand Test Reserves",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "name": "Kedarnath" },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[79.0, 30.5], [79.1, 30.5], [79.1, 30.6], [79.0, 30.5]]]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = MapLibreLayerManager.ensureFeatureCollection(multilineFc)
        val json = JSONObject(result)

        assertEquals("FeatureCollection", json.optString("type"))
        val features = json.getJSONArray("features")
        assertEquals(1, features.length())

        val feat = features.getJSONObject(0)
        assertEquals("Feature", feat.optString("type"))
        val geom = feat.getJSONObject("geometry")
        assertEquals("Polygon", geom.optString("type"))
        assertNotEquals("Geometry type must NOT be FeatureCollection", "FeatureCollection", geom.optString("type"))
    }

    @Test
    fun `ensureFeatureCollection wraps single Feature correctly`() {
        val singleFeature = """
            {
              "type": "Feature",
              "properties": { "name": "Single Trail" },
              "geometry": {
                "type": "LineString",
                "coordinates": [[79.0, 30.0], [79.1, 30.1]]
              }
            }
        """.trimIndent()

        val result = MapLibreLayerManager.ensureFeatureCollection(singleFeature)
        val json = JSONObject(result)

        assertEquals("FeatureCollection", json.optString("type"))
        val features = json.getJSONArray("features")
        assertEquals(1, features.length())
        assertEquals("Single Trail", features.getJSONObject(0).getJSONObject("properties").optString("name"))
    }

    @Test
    fun `ensureFeatureCollection wraps bare Polygon geometry correctly`() {
        val barePolygon = """
            {
              "type": "Polygon",
              "coordinates": [[[78.0, 30.0], [78.5, 30.0], [78.5, 30.5], [78.0, 30.0]]]
            }
        """.trimIndent()

        val result = MapLibreLayerManager.ensureFeatureCollection(barePolygon)
        val json = JSONObject(result)

        assertEquals("FeatureCollection", json.optString("type"))
        val features = json.getJSONArray("features")
        assertEquals(1, features.length())
        val geom = features.getJSONObject(0).getJSONObject("geometry")
        assertEquals("Polygon", geom.optString("type"))
    }

    @Test
    fun `ensureFeatureCollection handles empty and null inputs safely`() {
        assertEquals("{\"type\":\"FeatureCollection\",\"features\":[]}", MapLibreLayerManager.ensureFeatureCollection(""))
        assertEquals("{\"type\":\"FeatureCollection\",\"features\":[]}", MapLibreLayerManager.ensureFeatureCollection("   "))
        assertEquals("{\"type\":\"FeatureCollection\",\"features\":[]}", MapLibreLayerManager.ensureFeatureCollection("{}"))
        assertEquals("{\"type\":\"FeatureCollection\",\"features\":[]}", MapLibreLayerManager.ensureFeatureCollection("null"))
    }

    @Test
    fun `verify authoritative assets parse as valid FeatureCollections with correct geometries`() {
        val candidates = listOf(
            File("src/main/assets"),
            File("android/app/src/main/assets"),
            File("app/src/main/assets")
        )
        val assetsDir = candidates.firstOrNull { it.exists() }
        assertNotNull("Assets directory must exist in candidate paths", assetsDir)

        // 1. Waterways asset
        val waterFile = File(assetsDir, "uttarakhand_major_waterways.geojson")
        assertTrue("uttarakhand_major_waterways.geojson must exist", waterFile.exists())
        val waterJson = MapLibreLayerManager.ensureFeatureCollection(waterFile.readText())
        val waterParsed = JSONObject(waterJson)
        assertEquals("FeatureCollection", waterParsed.optString("type"))
        val waterFeatures = waterParsed.getJSONArray("features")
        assertEquals("Expected 29 authoritative waterways", 29, waterFeatures.length())

        // 2. Forest reserves asset
        val forestFile = File(assetsDir, "uttarakhand_forest_reserves.geojson")
        assertTrue("uttarakhand_forest_reserves.geojson must exist", forestFile.exists())
        val forestJson = MapLibreLayerManager.ensureFeatureCollection(forestFile.readText())
        val forestParsed = JSONObject(forestJson)
        assertEquals("FeatureCollection", forestParsed.optString("type"))
        val forestFeatures = forestParsed.getJSONArray("features")
        assertEquals("Expected 15 authoritative forest reserves", 15, forestFeatures.length())

        // 3. Historical landslides asset
        val lsFile = File(assetsDir, "uttarakhand_historical_landslides.geojson")
        assertTrue("uttarakhand_historical_landslides.geojson must exist", lsFile.exists())
        val lsJson = MapLibreLayerManager.ensureFeatureCollection(lsFile.readText())
        val lsParsed = JSONObject(lsJson)
        assertEquals("FeatureCollection", lsParsed.optString("type"))
        val lsFeatures = lsParsed.getJSONArray("features")
        assertEquals("Expected 10 historical landslide zones", 10, lsFeatures.length())

        // 4. Verified trails asset
        val trailsFile = File(assetsDir, "uttarakhand_verified_trails.geojson")
        assertTrue("uttarakhand_verified_trails.geojson must exist", trailsFile.exists())
        val trailsJson = MapLibreLayerManager.ensureFeatureCollection(trailsFile.readText())
        val trailsParsed = JSONObject(trailsJson)
        assertEquals("FeatureCollection", trailsParsed.optString("type"))
        val trailsFeatures = trailsParsed.getJSONArray("features")
        assertEquals("Expected 12 verified trails", 12, trailsFeatures.length())

        // 5. Baseline hazard zones asset
        val hazardFile = File(assetsDir, "uttarakhand_hazard_zones.geojson")
        assertTrue("uttarakhand_hazard_zones.geojson must exist", hazardFile.exists())
        val hazardJson = MapLibreLayerManager.ensureFeatureCollection(hazardFile.readText())
        val hazardParsed = JSONObject(hazardJson)
        assertEquals("FeatureCollection", hazardParsed.optString("type"))
        val hazardFeatures = hazardParsed.getJSONArray("features")
        assertEquals("Expected 18 hazard features (9 polygons + 9 centroid markers)", 18, hazardFeatures.length())
    }

    @Test
    fun `hasFeatures accurately identifies non-empty features and rejects empty collections`() {
        assertFalse(MapLibreLayerManager.hasFeatures(null))
        assertFalse(MapLibreLayerManager.hasFeatures(""))
        assertFalse(MapLibreLayerManager.hasFeatures("   "))
        assertFalse(MapLibreLayerManager.hasFeatures("{}"))
        assertFalse(MapLibreLayerManager.hasFeatures("{\"type\":\"FeatureCollection\",\"features\":[]}"))
        assertFalse(MapLibreLayerManager.hasFeatures("""
            {
               "type": "FeatureCollection",
               "features": []
            }
        """.trimIndent()))

        val validFc = """
            {
               "type": "FeatureCollection",
               "features": [
                 {
                   "type": "Feature",
                   "geometry": { "type": "Point", "coordinates": [79.0, 30.0] }
                 }
               ]
            }
        """.trimIndent()
        assertTrue(MapLibreLayerManager.hasFeatures(validFc))

        val singleFeature = """
            {
               "type": "Feature",
               "geometry": { "type": "Point", "coordinates": [79.0, 30.0] }
            }
        """.trimIndent()
        assertTrue(MapLibreLayerManager.hasFeatures(singleFeature))
    }

    @Test
    fun `buildHazardSequenceGeoJson produces syntactically valid GeoJSON with icon and sequence properties`() {
        val dummyFeature = com.touristsafety.app.core.model.UnifiedMapFeature(
            id = "hazard_101",
            category = com.touristsafety.app.core.model.MapFeatureCategory.HAZARD_ZONE,
            title = "Kedarnath Outburst",
            subtitle = "Active Warning",
            latitude = 30.7346,
            longitude = 79.0669
        )
        val dummyItem = com.touristsafety.app.core.model.RouteOrderedHazardItem(
            sequenceNumber = 1,
            sequenceLabel = "#1",
            feature = dummyFeature,
            distanceAlongRouteMeters = 1200.0,
            crossTrackDistanceMeters = 15.0,
            intersectionType = com.touristsafety.app.core.model.RouteIntersectionType.DIRECT_INTERSECTION,
            categoryType = com.touristsafety.app.core.model.HazardCategoryType.FLOODING,
            severity = "CRITICAL"
        )

        val jsonString = MapLibreLayerManager.buildHazardSequenceGeoJson(listOf(dummyItem))
        val parsed = JSONObject(jsonString)

        assertEquals("FeatureCollection", parsed.optString("type"))
        val features = parsed.getJSONArray("features")
        assertEquals(1, features.length())

        val feat = features.getJSONObject(0)
        assertEquals("Feature", feat.optString("type"))
        assertEquals("hazard_101", feat.optString("id"))

        val geom = feat.getJSONObject("geometry")
        assertEquals("Point", geom.optString("type"))
        val coords = geom.getJSONArray("coordinates")
        assertEquals(79.0669, coords.getDouble(0), 0.0001)
        assertEquals(30.7346, coords.getDouble(1), 0.0001)

        val props = feat.getJSONObject("properties")
        assertEquals("#1", props.optString("sequence_label"))
        assertEquals(1, props.optInt("sequence_number"))
        assertEquals("icon-anim-flooding", props.optString("icon_id"))
        assertEquals("flooding", props.optString("hazard_category"))
    }
}

