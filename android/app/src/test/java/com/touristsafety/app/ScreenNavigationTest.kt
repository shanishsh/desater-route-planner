package com.touristsafety.app

import com.touristsafety.app.navigation.Screen
import org.junit.Assert.*
import org.junit.Test

class ScreenNavigationTest {

    @Test
    fun `bottom nav screens are non-null and have non-empty routes and titles`() {
        val bottomNavScreens = Screen.bottomNavScreens
        assertNotNull("bottomNavScreens list must not be null", bottomNavScreens)
        assertEquals("Expected 5 bottom nav items", 5, bottomNavScreens.size)

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
}

