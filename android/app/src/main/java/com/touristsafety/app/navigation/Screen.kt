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

    // 5 Permanent Navigation Pillars (Clean, Predictable, Never Shifting)
    data object Home : Screen("home", "Explore", AppIcons.Home)
    data object PlanTrip : Screen("plan_trip", "Plan", AppIcons.PlanTrip)
    data object Map : Screen("map?startDrawing={startDrawing}&activeTripId={activeTripId}", "Live Map", AppIcons.Map) {
        fun createRoute(startDrawing: Boolean = false, activeTripId: String? = null): String {
            val params = mutableListOf<String>()
            if (startDrawing) params.add("startDrawing=true")
            if (!activeTripId.isNullOrBlank()) params.add("activeTripId=$activeTripId")
            return if (params.isEmpty()) "map" else "map?" + params.joinToString("&")
        }
    }
    data object TrekHistory : Screen("trek_history", "History & Saved", Icons.Default.History)
    data object Emergency : Screen("emergency", "Safety Hub", AppIcons.Emergency)

    // Secondary & Drill-Down Screens (Centralized via Safety Hub / Route Planning)
    data object Alerts : Screen("alerts", "Advisories", AppIcons.Alerts)
    data object DisasterSafety : Screen("disaster_safety", "Disaster Safety", AppIcons.DisasterSafety)
    data object ProviderStatus : Screen("provider_status", "Data Providers", AppIcons.ProviderStatus)
    data object RouteSafetyReview : Screen("route_safety_review/{routeId}", "Safety Review", Icons.Default.Shield) {
        fun createRoute(routeId: String) = "route_safety_review/$routeId"
    }
    data object CompletedTrekDetail : Screen("completed_trek_detail/{tripId}", "Trek Summary", Icons.Default.CheckCircle) {
        fun createRoute(tripId: String) = "completed_trek_detail/$tripId"
    }

    // Legacy Pass-Through Routes (Redirected seamlessly in NavGraph for backward compatibility)
    data object Saved : Screen("saved", "Saved", AppIcons.Saved)
    data object ActiveTrip : Screen("active_trip/{tripId}", "Active Trip", Icons.Default.Navigation) {
        fun createRoute(tripId: String) = "active_trip/$tripId"
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
                "completed_trek_detail/{tripId}",
                Alerts.route,
                DisasterSafety.route,
                ProviderStatus.route
            )
    }
}
