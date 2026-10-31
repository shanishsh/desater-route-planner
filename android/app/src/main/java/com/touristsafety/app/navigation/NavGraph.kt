package com.touristsafety.app.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.touristsafety.app.feature.alerts.AlertsScreen
import com.touristsafety.app.feature.alerts.AlertsViewModel
import com.touristsafety.app.feature.auth.AuthViewModel
import com.touristsafety.app.feature.auth.LoginScreen
import com.touristsafety.app.feature.auth.RegisterScreen
import com.touristsafety.app.feature.auth.WelcomeScreen
import com.touristsafety.app.feature.disaster.DisasterSafetyScreen
import com.touristsafety.app.feature.disaster.DisasterSafetyViewModel
import com.touristsafety.app.feature.emergency.EmergencyScreen
import com.touristsafety.app.feature.emergency.EmergencyViewModel
import com.touristsafety.app.feature.history.CompletedTrekDetailScreen
import com.touristsafety.app.feature.history.TrekHistoryScreen
import com.touristsafety.app.feature.history.TrekHistoryViewModel
import com.touristsafety.app.feature.home.HomeScreen
import com.touristsafety.app.feature.home.HomeViewModel
import com.touristsafety.app.feature.map.MapScreen
import com.touristsafety.app.feature.map.MapViewModel
import com.touristsafety.app.feature.offline.SavedTripsScreen
import com.touristsafety.app.feature.offline.SavedTripsViewModel
import com.touristsafety.app.feature.onboarding.PermissionOnboardingScreen
import com.touristsafety.app.feature.providers.ProviderStatusScreen
import com.touristsafety.app.feature.providers.ProviderStatusViewModel
import com.touristsafety.app.feature.trip.ActiveTripScreen
import com.touristsafety.app.feature.trip.PlanTripScreen
import com.touristsafety.app.feature.trip.RouteSafetyReviewScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues,
    startDestination: String = Screen.Home.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.padding(paddingValues)
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onNavigateToLogin = { navController.navigate(Screen.Login.route) },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onContinueAsGuest = {
                    navController.navigate(Screen.PermissionOnboarding.route)
                }
            )
        }

        composable(Screen.Login.route) {
            val authViewModel: AuthViewModel = hiltViewModel()
            LoginScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route) {
                        popUpTo(Screen.Welcome.route)
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.PermissionOnboarding.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            val authViewModel: AuthViewModel = hiltViewModel()
            RegisterScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Welcome.route)
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.PermissionOnboarding.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PermissionOnboarding.route) {
            val authViewModel: AuthViewModel = hiltViewModel()
            PermissionOnboardingScreen(
                onPermissionsConfigured = {
                    authViewModel.completeOnboarding()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onNavigateToMap = {
                    navController.navigate(Screen.Map.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToAlerts = { navController.navigate(Screen.Alerts.route) },
                onNavigateToEmergency = {
                    navController.navigate(Screen.Emergency.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToDisasterSafety = { navController.navigate(Screen.DisasterSafety.route) },
                onNavigateToProviders = { navController.navigate(Screen.ProviderStatus.route) },
                onNavigateToPlanTrip = {
                    navController.navigate(Screen.PlanTrip.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToWelcome = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToActiveTrip = { tripId ->
                    navController.navigate(Screen.ActiveTrip.createRoute(tripId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToCompletedTrekDetail = { tripId ->
                    navController.navigate(Screen.CompletedTrekDetail.createRoute(tripId))
                },
                onNavigateToDrawRoute = {
                    navController.navigate(Screen.Map.createRoute(startDrawing = true)) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.TrekHistory.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(Screen.PlanTrip.route) {
            PlanTripScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReview = { routeId ->
                    navController.navigate(Screen.RouteSafetyReview.createRoute(routeId))
                },
                onNavigateToDrawRoute = {
                    navController.navigate(Screen.Map.createRoute(startDrawing = true)) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(
            route = Screen.RouteSafetyReview.route,
            arguments = listOf(navArgument("routeId") { type = NavType.StringType })
        ) {
            RouteSafetyReviewScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToActiveTrip = { tripId ->
                    navController.navigate(Screen.ActiveTrip.createRoute(tripId)) {
                        popUpTo(Screen.RouteSafetyReview.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToMap = {
                    navController.navigate(Screen.Map.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(
            route = Screen.ActiveTrip.route,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) {
            ActiveTripScreen(
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.TrekHistory.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToCompletedTrekDetail = { tripId ->
                    navController.navigate(Screen.CompletedTrekDetail.createRoute(tripId)) {
                        popUpTo(Screen.ActiveTrip.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.TrekHistory.route) {
            val viewModel: TrekHistoryViewModel = hiltViewModel()
            TrekHistoryScreen(
                viewModel = viewModel,
                onNavigateToCompletedTrekDetail = { tripId ->
                    navController.navigate(Screen.CompletedTrekDetail.createRoute(tripId))
                },
                onNavigateToPlanTrip = {
                    navController.navigate(Screen.PlanTrip.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToReview = { routeId ->
                    navController.navigate(Screen.RouteSafetyReview.createRoute(routeId))
                },
                onNavigateToMap = {
                    navController.navigate(Screen.Map.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(
            route = Screen.CompletedTrekDetail.route,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) {
            CompletedTrekDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "map?startDrawing={startDrawing}",
            arguments = listOf(
                navArgument("startDrawing") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val startDrawing = backStackEntry.arguments?.getBoolean("startDrawing") ?: false
            val viewModel: MapViewModel = hiltViewModel()
            LaunchedEffect(startDrawing) {
                if (startDrawing && !viewModel.uiState.value.isDrawingMode) {
                    viewModel.enterDrawingMode("TRAIL_FOLLOWING")
                }
            }
            MapScreen(
                viewModel = viewModel,
                onNavigateToPlanTrip = {
                    navController.navigate(Screen.PlanTrip.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
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
            SavedTripsScreen(
                viewModel = viewModel,
                onNavigateToReview = { routeId ->
                    navController.navigate(Screen.RouteSafetyReview.createRoute(routeId))
                },
                onNavigateToMap = {
                    navController.navigate(Screen.Map.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
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
