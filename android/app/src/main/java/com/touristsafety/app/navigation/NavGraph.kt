package com.touristsafety.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.touristsafety.app.feature.alerts.AlertsScreen
import com.touristsafety.app.feature.alerts.AlertsViewModel
import com.touristsafety.app.feature.disaster.DisasterSafetyScreen
import com.touristsafety.app.feature.disaster.DisasterSafetyViewModel
import com.touristsafety.app.feature.emergency.EmergencyScreen
import com.touristsafety.app.feature.emergency.EmergencyViewModel
import com.touristsafety.app.feature.home.HomeScreen
import com.touristsafety.app.feature.home.HomeViewModel
import com.touristsafety.app.feature.map.MapScreen
import com.touristsafety.app.feature.map.MapViewModel
import com.touristsafety.app.feature.offline.SavedTripsScreen
import com.touristsafety.app.feature.offline.SavedTripsViewModel
import com.touristsafety.app.feature.providers.ProviderStatusScreen
import com.touristsafety.app.feature.providers.ProviderStatusViewModel

@Composable
fun AppNavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier.padding(paddingValues)
    ) {
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onNavigateToMap = { navController.navigate(Screen.Map.route) },
                onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) },
                onNavigateToEmergency = { navController.navigate(Screen.Emergency.route) },
                onNavigateToDisasterSafety = { navController.navigate(Screen.DisasterSafety.route) },
                onNavigateToProviders = { navController.navigate(Screen.ProviderStatus.route) }
            )
        }
        composable(Screen.Map.route) {
            val viewModel: MapViewModel = hiltViewModel()
            MapScreen(viewModel = viewModel)
        }
        composable(Screen.Alerts.route) {
            val viewModel: AlertsViewModel = hiltViewModel()
            AlertsScreen(viewModel = viewModel)
        }
        composable(Screen.Emergency.route) {
            val viewModel: EmergencyViewModel = hiltViewModel()
            EmergencyScreen(
                viewModel = viewModel,
                onNavigateToDisasterSafety = { navController.navigate(Screen.DisasterSafety.route) }
            )
        }
        composable(Screen.Saved.route) {
            val viewModel: SavedTripsViewModel = hiltViewModel()
            SavedTripsScreen(viewModel = viewModel)
        }
        composable(Screen.DisasterSafety.route) {
            val viewModel: DisasterSafetyViewModel = hiltViewModel()
            DisasterSafetyScreen(viewModel = viewModel)
        }
        composable(Screen.ProviderStatus.route) {
            val viewModel: ProviderStatusViewModel = hiltViewModel()
            ProviderStatusScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

