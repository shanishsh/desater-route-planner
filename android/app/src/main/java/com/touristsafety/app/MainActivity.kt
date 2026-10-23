package com.touristsafety.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize scheduled background synchronization with network constraints
        try {
            syncManager.schedulePeriodicSync()
            syncManager.triggerOneTimeSync()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule background sync work", e)
        }

        setContent {
            TouristSafetyTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            Screen.bottomNavScreens.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) },
                                    selected = currentRoute == screen.route,
                                    onClick = {
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
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
                ) { paddingValues ->
                    AppNavGraph(
                        navController = navController,
                        paddingValues = paddingValues
                    )
                }
            }
        }
    }
}

