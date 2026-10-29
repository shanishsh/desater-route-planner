package com.touristsafety.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.touristsafety.app.core.designsystem.AppIcons

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Welcome : Screen("welcome", "Welcome", Icons.Default.Terrain)
    data object Login : Screen("login", "Log In", Icons.Default.Lock)
    data object Register : Screen("register", "Register", Icons.Default.PersonAdd)
    data object PermissionOnboarding : Screen("permission_onboarding", "Permissions", Icons.Default.Security)

    // 5 Primary Navigation Pillars
    data object Home : Screen("home", "Home", AppIcons.Home)
    data object PlanTrip : Screen("plan_trip", "Plan & Routes", AppIcons.PlanTrip)
    data object Map : Screen("map", "Safety Map", AppIcons.Map) {
        fun createRoute(startDrawing: Boolean = false) = if (startDrawing) "map?startDrawing=true" else "map"
    }
    data object TrekHistory : Screen("trek_history", "Trek History", Icons.Default.History)
    data object Emergency : Screen("emergency", "Assistance", AppIcons.Emergency)

    // Secondary & Drill-Down Screens
    data object Alerts : Screen("alerts", "Advisories", AppIcons.Alerts)
    data object Saved : Screen("saved", "Saved", AppIcons.Saved)
    data object DisasterSafety : Screen("disaster_safety", "Disaster Safety", AppIcons.DisasterSafety)
    data object ProviderStatus : Screen("provider_status", "Data Providers", AppIcons.ProviderStatus)
    data object RouteSafetyReview : Screen("route_safety_review/{routeId}", "Safety Review", Icons.Default.Shield) {
        fun createRoute(routeId: String) = "route_safety_review/$routeId"
    }
    data object ActiveTrip : Screen("active_trip/{tripId}", "Active Trip", Icons.Default.Navigation) {
        fun createRoute(tripId: String) = "active_trip/$tripId"
    }
    data object CompletedTrekDetail : Screen("completed_trek_detail/{tripId}", "Trek Summary", Icons.Default.CheckCircle) {
        fun createRoute(tripId: String) = "completed_trek_detail/$tripId"
    }

    companion object {
        val bottomNavScreens: List<Screen>
            get() = listOf(Home, PlanTrip, Map, TrekHistory, Emergency)

        val fullScreenRoutes: Set<String>
            get() = setOf(
                Welcome.route,
                Login.route,
                Register.route,
                PermissionOnboarding.route,
                "route_safety_review/{routeId}",
                "active_trip/{tripId}",
                "completed_trek_detail/{tripId}"
            )
    }
}
