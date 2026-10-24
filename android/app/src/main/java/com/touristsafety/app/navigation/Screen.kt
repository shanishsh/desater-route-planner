package com.touristsafety.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Welcome : Screen("welcome", "Welcome", Icons.Default.Terrain)
    data object Login : Screen("login", "Log In", Icons.Default.Lock)
    data object Register : Screen("register", "Register", Icons.Default.PersonAdd)
    data object PermissionOnboarding : Screen("permission_onboarding", "Permissions", Icons.Default.Security)

    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Map : Screen("map", "Explore Map", Icons.Default.Map)
    data object Alerts : Screen("alerts", "Alerts", Icons.Default.Warning)
    data object Emergency : Screen("emergency", "Emergency", Icons.Default.Emergency)
    data object Saved : Screen("saved", "Saved", Icons.Default.CloudDownload)
    data object DisasterSafety : Screen("disaster_safety", "Disaster Safety", Icons.Default.Shield)
    data object ProviderStatus : Screen("provider_status", "Data Providers", Icons.Default.CloudQueue)
    data object PlanTrip : Screen("plan_trip", "Plan Route", Icons.Default.Directions)
    data object RouteSafetyReview : Screen("route_safety_review/{routeId}", "Safety Review", Icons.Default.Shield) {
        fun createRoute(routeId: String) = "route_safety_review/$routeId"
    }
    data object ActiveTrip : Screen("active_trip/{tripId}", "Active Trip", Icons.Default.Navigation) {
        fun createRoute(tripId: String) = "active_trip/$tripId"
    }

    companion object {
        val bottomNavScreens: List<Screen>
            get() = listOf(Home, PlanTrip, Map, Alerts, Emergency)

        val fullScreenRoutes: Set<String>
            get() = setOf(
                Welcome.route,
                Login.route,
                Register.route,
                PermissionOnboarding.route,
                "route_safety_review/{routeId}",
                "active_trip/{tripId}"
            )
    }
}

