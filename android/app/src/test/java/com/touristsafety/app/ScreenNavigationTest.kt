package com.touristsafety.app

import com.touristsafety.app.navigation.Screen
import org.maplibre.android.maps.MapLibreMapOptions
import org.junit.Assert.*
import org.junit.Test

class ScreenNavigationTest {

    @Test
    fun `bottom nav screens are non-null and have non-empty routes and titles`() {
        val bottomNavScreens = Screen.bottomNavScreens
        assertNotNull("bottomNavScreens list must not be null", bottomNavScreens)
        assertEquals("Expected 5 bottom nav items for 5-pillar architecture", 5, bottomNavScreens.size)

        for (screen in bottomNavScreens) {
            assertNotNull("Individual bottom nav screen must not be null", screen)
            assertTrue("Screen route must not be blank", screen.route.isNotBlank())
            assertTrue("Screen title must not be blank", screen.title.isNotBlank())
            assertNotNull("Screen icon must not be null", screen.icon)
        }
    }

    @Test
    fun `full screen routes are non-null and valid`() {
        val fullScreenRoutes = Screen.fullScreenRoutes
        assertNotNull("fullScreenRoutes must not be null", fullScreenRoutes)
        assertTrue("fullScreenRoutes must contain welcome", fullScreenRoutes.contains("welcome"))
        assertTrue("fullScreenRoutes must contain login", fullScreenRoutes.contains("login"))
        assertTrue("fullScreenRoutes must contain register", fullScreenRoutes.contains("register"))
    }

    @Test
    fun `maplibre layer manager escapes json strings safely`() {
        val escapedQuotes = com.touristsafety.app.core.map.MapLibreLayerManager.escapeJson("Advisory: \"Heavy Rain\"")
        assertEquals("Advisory: \\\"Heavy Rain\\\"", escapedQuotes)

        val escapedNewlines = com.touristsafety.app.core.map.MapLibreLayerManager.escapeJson("Line 1\nLine 2\r\nLine 3")
        assertEquals("Line 1\\nLine 2\\r\\nLine 3", escapedNewlines)

        val escapedBackslashes = com.touristsafety.app.core.map.MapLibreLayerManager.escapeJson("C:\\folder\\file")
        assertEquals("C:\\\\folder\\\\file", escapedBackslashes)

        val escapedNull = com.touristsafety.app.core.map.MapLibreLayerManager.escapeJson(null)
        assertEquals("", escapedNull)
    }
}

