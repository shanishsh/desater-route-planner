package com.touristsafety.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.touristsafety.app.core.auth.TokenManager
import com.touristsafety.app.core.designsystem.TouristSafetyTheme
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

                val isFullScreen = currentRoute == null ||
                        currentRoute in Screen.fullScreenRoutes ||
                        currentRoute.startsWith("route_safety_review") ||
                        currentRoute.startsWith("active_trip")

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
                            NavigationBar {
                                Screen.bottomNavScreens.filterNotNull().forEach { screen ->
                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title) },
                                        selected = currentRoute == screen.route,
                                        onClick = {
                                            val targetRoute = screen.route
                                            if (currentRoute != targetRoute) {
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
