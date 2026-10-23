package com.touristsafety.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Map : Screen("map", "Explore Map", Icons.Default.Map)
    data object Alerts : Screen("alerts", "Alerts", Icons.Default.Warning)
    data object Emergency : Screen("emergency", "Emergency", Icons.Default.Emergency)
    data object Saved : Screen("saved", "Saved", Icons.Default.CloudDownload)
    data object DisasterSafety : Screen("disaster_safety", "Disaster Safety", Icons.Default.Shield)
    data object ProviderStatus : Screen("provider_status", "Data Providers", Icons.Default.CloudQueue)

    companion object {
        val bottomNavScreens = listOf(Home, Map, Alerts, Emergency, Saved)
    }
}

