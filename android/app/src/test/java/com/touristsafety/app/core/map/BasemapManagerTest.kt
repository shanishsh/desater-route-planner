package com.touristsafety.app.core.map

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BasemapManagerTest {

    @Test
    fun `verify BasemapMode properties and satellite flags`() {
        assertFalse(BasemapMode.STREETS_TRAILS.isSatellite)
        assertTrue(BasemapMode.SATELLITE.isSatellite)
        assertTrue(BasemapMode.HYBRID.isSatellite)

        assertEquals("Streets & Trails", BasemapMode.STREETS_TRAILS.displayName)
        assertEquals("Satellite", BasemapMode.SATELLITE.displayName)
        assertEquals("Hybrid", BasemapMode.HYBRID.displayName)

        assertTrue(BasemapMode.STREETS_TRAILS.attribution.contains("OpenStreetMap"))
        assertTrue(BasemapMode.SATELLITE.attribution.contains("Esri"))
        assertTrue(BasemapMode.HYBRID.attribution.contains("Esri"))
    }

    @Test
    fun `verify satellite style JSON structure and raster source`() {
        val jsonStr = MapConfig.SATELLITE_STYLE_JSON
        assertNotNull(jsonStr)
        val styleObj = JSONObject(jsonStr)

        assertEquals(8, styleObj.getInt("version"))
        assertEquals("Uttarakhand Satellite Basemap", styleObj.getString("name"))

        val sources = styleObj.getJSONObject("sources")
        assertTrue(sources.has("satellite-tiles"))
        val esriSource = sources.getJSONObject("satellite-tiles")
        assertEquals("raster", esriSource.getString("type"))
        assertEquals(256, esriSource.getInt("tileSize"))

        val tiles = esriSource.getJSONArray("tiles")
        assertTrue(tiles.length() > 0)
        assertTrue(tiles.getString(0).contains("server.arcgisonline.com"))

        val layers = styleObj.getJSONArray("layers")
        assertTrue(layers.length() > 0)
        val basemapLayer = layers.getJSONObject(1)
        assertEquals("raster", basemapLayer.getString("type"))
        assertEquals("satellite-tiles", basemapLayer.getString("source"))
    }

    @Test
    fun `verify hybrid style JSON contains satellite raster plus reference transportation and places`() {
        val jsonStr = MapConfig.HYBRID_STYLE_JSON
        assertNotNull(jsonStr)
        val styleObj = JSONObject(jsonStr)

        assertEquals(8, styleObj.getInt("version"))
        assertEquals("Uttarakhand Hybrid Basemap", styleObj.getString("name"))

        val sources = styleObj.getJSONObject("sources")
        assertTrue(sources.has("satellite-tiles"))
        assertTrue(sources.has("reference-roads"))
        assertTrue(sources.has("reference-labels"))

        val roadsSource = sources.getJSONObject("reference-roads")
        assertEquals("raster", roadsSource.getString("type"))
        assertTrue(roadsSource.getJSONArray("tiles").getString(0).contains("World_Transportation"))

        val placesSource = sources.getJSONObject("reference-labels")
        assertEquals("raster", placesSource.getString("type"))
        assertTrue(placesSource.getJSONArray("tiles").getString(0).contains("World_Boundaries_and_Places"))

        val layers = styleObj.getJSONArray("layers")
        assertEquals(4, layers.length())
        assertEquals("satellite-tiles-layer", layers.getJSONObject(1).getString("id"))
        assertEquals("reference-roads-layer", layers.getJSONObject(2).getString("id"))
        assertEquals("reference-labels-layer", layers.getJSONObject(3).getString("id"))
    }

    @Test
    fun `verify MapConfig createStyleBuilder builds appropriate styles`() {
        val streetsBuilder = MapConfig.createStyleBuilder(BasemapMode.STREETS_TRAILS)
        assertNotNull(streetsBuilder)

        val satelliteBuilder = MapConfig.createStyleBuilder(BasemapMode.SATELLITE)
        assertNotNull(satelliteBuilder)

        val hybridBuilder = MapConfig.createStyleBuilder(BasemapMode.HYBRID)
        assertNotNull(hybridBuilder)
    }

    @Test
    fun `verify MapLibreLayerManager layer constants include route primary casing`() {
        assertEquals("layer-route-primary-casing", MapLibreLayerManager.LAYER_ROUTE_PRIMARY_CASING)
        assertEquals("layer-route-primary-line", MapLibreLayerManager.LAYER_ROUTE_PRIMARY_LINE)
        assertEquals("layer-uttarakhand-mask-fill", MapLibreLayerManager.LAYER_UTTARAKHAND_MASK_FILL)
    }
}
