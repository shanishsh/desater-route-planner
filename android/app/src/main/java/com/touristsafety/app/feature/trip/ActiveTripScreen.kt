package com.touristsafety.app.feature.trip

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTripScreen(
    onNavigateBack: () -> Unit,
    viewModel: ActiveTripViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val animatedProgress by animateFloatAsState(targetValue = (state.progressPct / 100f).toFloat())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Trip Monitoring", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("En Route To", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(state.destinationName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Surface(
                            color = if (state.isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (state.isCompleted) "COMPLETED" else "LIVE GPS TRACKING",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Route Progress: ${state.progressPct.toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    )
                }
            }

            // Route Deviation or Hazard Radar Alert
            if (state.isOffRoute) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, tint = Color(0xFFD32F2F))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Off planned route by > 600m. Please return to corridor or recalculate.",
                            color = Color(0xFFB71C1C),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (state.nearestHazardDistKm != null && state.nearestHazardDistKm!! <= 5.0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Hazard Ahead (${state.nearestHazardDistKm} km)",
                                color = Color(0xFFE65100),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                state.nearestHazardTitle ?: "Hazard detected along upcoming segment",
                                color = Color(0xFFEF6C00),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Next Waypoint Arrival Forecast
            val next = state.nextWaypoint
            if (next != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Next Route Waypoint", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(next.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text("${next.weather.temperature_c.toInt()}°C", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Thermostat, contentDescription = null) }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("${next.weather.precipitation_probability_pct.toInt()}% Rain", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.WaterDrop, contentDescription = null) }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("${next.weather.wind_speed_kmh.toInt()} km/h", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Air, contentDescription = null) }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Expected: ${next.weather.weather_description}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // End Trip Action
            Button(
                onClick = {
                    viewModel.endTrip(context)
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("End Trip Monitoring", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

