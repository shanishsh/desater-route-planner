package com.touristsafety.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touristsafety.app.core.auth.TokenManager
import com.touristsafety.app.core.designsystem.ExpressiveShapes
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
        enableEdgeToEdge()
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
                val activeTrip by routeRepository.activeTripFlow.collectAsStateWithLifecycle(initialValue = null)
                var isBannerDismissed by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(activeTrip?.tripId) {
                    isBannerDismissed = false
                }

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

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isExpanded = maxWidth >= 600.dp

                    if (isExpanded && !isFullScreen) {
                        // Adaptive layout for Tablets, Foldables, and Landscape: Navigation Rail on Start Edge
                        Row(modifier = Modifier.fillMaxSize()) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                header = {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "TrailGuard",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestGreenPrimary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            ) {
                                Screen.bottomNavScreens.filterNotNull().forEach { screen ->
                                    val isSelected = currentRoute?.substringBefore('?') == screen.route.substringBefore('?')
                                    NavigationRailItem(
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title) },
                                        selected = isSelected,
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = ForestGreenPrimary,
                                            selectedTextColor = ForestGreenPrimary,
                                            indicatorColor = SageContainer
                                        ),
                                        onClick = {
                                            val targetRoute = screen.route
                                            if (currentRoute?.substringBefore('?') != targetRoute.substringBefore('?')) {
                                                navController.navigate(targetRoute) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                // Docked active trek HUD banner if active
                                activeTrip?.let { trip ->
                                    if (!isBannerDismissed && currentRoute?.startsWith("active_trip") != true) {
                                        ActiveTrekHudBanner(
                                            routeTitle = trip.routeTitle,
                                            distanceMeters = trip.traveledDistanceMeters,
                                            onResumeClick = {
                                                navController.navigate(Screen.ActiveTrip.createRoute(trip.tripId)) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            onDismissClick = { isBannerDismissed = true },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                AppNavGraph(
                                    navController = navController,
                                    paddingValues = PaddingValues(0.dp),
                                    startDestination = startDestination
                                )
                            }
                        }
                    } else {
                        // Standard Compact layout: Scaffold with Bottom Navigation Bar
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                if (!isFullScreen) {
                                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                        activeTrip?.let { trip ->
                                            if (!isBannerDismissed && currentRoute?.startsWith("active_trip") != true) {
                                                ActiveTrekHudBanner(
                                                    routeTitle = trip.routeTitle,
                                                    distanceMeters = trip.traveledDistanceMeters,
                                                    onResumeClick = {
                                                        navController.navigate(Screen.ActiveTrip.createRoute(trip.tripId)) {
                                                            launchSingleTop = true
                                                        }
                                                    },
                                                    onDismissClick = { isBannerDismissed = true },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                                )
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
                                                                popUpTo(navController.graph.findStartDestination().id) {
                                                                    saveState = true
                                                                }
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
    }
}

/**
 * Compact, clamped 44dp Active Trek HUD banner shaped as an Expressive pill capsule.
 */
@Composable
private fun ActiveTrekHudBanner(
    routeTitle: String,
    distanceMeters: Double,
    onResumeClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onResumeClick),
        shape = ExpressiveShapes.pill,
        color = ForestGreenPrimary,
        contentColor = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("●", color = Color(0xFF69F0AE), fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = routeTitle.ifBlank { "Trek in Progress" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000.0)} • Resume",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onDismissClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
