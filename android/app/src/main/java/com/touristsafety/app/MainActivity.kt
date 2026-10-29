package com.touristsafety.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.touristsafety.app.core.auth.TokenManager
import com.touristsafety.app.core.designsystem.ForestGreenPrimary
import com.touristsafety.app.core.designsystem.SageContainer
import com.touristsafety.app.core.designsystem.TouristSafetyTheme
import com.touristsafety.app.core.repository.RouteRepository
import com.touristsafety.app.core.sync.SyncManager
import com.touristsafety.app.navigation.AppNavGraph
import com.touristsafety.app.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var routeRepository: RouteRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize scheduled background synchronization with network constraints safely
        try {
            syncManager.schedulePeriodicSync()
            syncManager.triggerOneTimeSync()
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Failed to schedule background sync work safely", t)
        }

        // Initialize Firebase Messaging token fetch safely
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    android.util.Log.i("MainActivity", "Firebase FCM Registration Token: $token")
                } else {
                    android.util.Log.d("MainActivity", "Firebase FCM token fetch pending or unconfigured")
                }
            }
        } catch (t: Throwable) {
            android.util.Log.d("MainActivity", "Firebase Messaging not active: ${t.message}")
        }

        setContent {
            TouristSafetyTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val activeTrip by routeRepository.activeTripFlow.collectAsState(initial = null)

                val isFullScreen = currentRoute == null ||
                        currentRoute in Screen.fullScreenRoutes ||
                        currentRoute.startsWith("route_safety_review") ||
                        currentRoute.startsWith("active_trip") ||
                        currentRoute.startsWith("completed_trek_detail")

                val startDestination = remember {
                    try {
                        if (tokenManager.isLoggedIn()) {
                            if (tokenManager.hasCompletedPermissionsOnboarding()) {
                                Screen.Home.route
                            } else {
                                Screen.PermissionOnboarding.route
                            }
                        } else {
                            Screen.Welcome.route
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("MainActivity", "Error reading auth state, defaulting to Welcome", t)
                        Screen.Welcome.route
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!isFullScreen) {
                            Column {
                                activeTrip?.let { trip ->
                                    if (currentRoute?.startsWith("active_trip") != true) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    navController.navigate(Screen.ActiveTrip.createRoute(trip.tripId))
                                                },
                                            color = ForestGreenPrimary,
                                            contentColor = Color.White
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("●", color = Color(0xFF69F0AE), fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = trip.routeTitle.ifBlank { "Trek in Progress" },
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1
                                                    )
                                                }
                                                Text(
                                                    text = "${String.format(java.util.Locale.US, "%.1f km", trip.traveledDistanceMeters / 1000.0)} • Tap to resume HUD",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }

                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 6.dp
                                ) {
                                Screen.bottomNavScreens.filterNotNull().forEach { screen ->
                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = {
                                            Text(
                                                screen.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (currentRoute == screen.route) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        selected = currentRoute?.substringBefore('?') == screen.route.substringBefore('?'),
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = ForestGreenPrimary,
                                            selectedTextColor = ForestGreenPrimary,
                                            indicatorColor = SageContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        onClick = {
                                            val targetRoute = screen.route
                                            if (currentRoute?.substringBefore('?') != targetRoute.substringBefore('?')) {
                                                navController.navigate(targetRoute) {
                                                    popUpTo(Screen.Home.route) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                ) { paddingValues ->
                    AppNavGraph(
                        navController = navController,
                        paddingValues = paddingValues,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
