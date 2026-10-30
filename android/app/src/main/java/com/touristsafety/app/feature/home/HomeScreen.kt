package com.touristsafety.app.feature.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.designsystem.components.*
import com.touristsafety.app.core.model.Alert
import com.touristsafety.app.core.model.AlertSeverity
import com.touristsafety.app.core.model.Itinerary
import com.touristsafety.app.core.model.Weather
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToMap: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToDisasterSafety: () -> Unit = {},
    onNavigateToProviders: () -> Unit = {},
    onNavigateToPlanTrip: () -> Unit = {},
    onNavigateToWelcome: () -> Unit = {},
    onNavigateToActiveTrip: (String) -> Unit = {},
    onNavigateToCompletedTrekDetail: (String) -> Unit = {},
    onNavigateToDrawRoute: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSirenPlaying by viewModel.isSirenPlaying.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showProfileDialog by remember { mutableStateOf(false) }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            icon = { Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(36.dp), tint = ForestGreenPrimary) },
            title = { Text("Trekker Account", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Logged in as:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        viewModel.getUserEmail() ?: "Field Explorer (Guest)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "TrailGuard session active. Offline safety caches and planned routes are synchronized with on-device Room storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showProfileDialog = false
                        onNavigateToWelcome()
                    }
                ) {
                    Text("Sign Out", color = EmergencyRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Uttarakhand Trek Safety",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val syncTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.lastSyncTimestamp))
                        Text(
                            if (state.isOffline) "Offline Cache Active" else "Last Synced at $syncTime",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isOffline) WarningAmber else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToProviders) {
                        Icon(AppIcons.ProviderStatus, contentDescription = "Data Providers & Diagnostics")
                    }
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(AppIcons.Refresh, contentDescription = "Refresh Data")
                    }
                    IconButton(onClick = { showProfileDialog = true }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Account Profile")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            OfflineBanner(isOffline = state.isOffline)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. High-Priority Disaster Siren Alarm (Only shown if actively ringing)
                if (isSirenPlaying) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(EmergencyRed)
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    AppIcons.Siren,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "DISASTER ALARM ACTIVE",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "Emergency alert tone sounding.",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp
                                    )
                                }
                                Button(
                                    onClick = { viewModel.stopDisasterSiren() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("SILENCE", color = EmergencyRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Active Ongoing Trek HUD Banner (High Priority)
                state.activeTrip?.let { active ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToActiveTrip(active.tripId) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = ForestGreenPrimary.copy(alpha = 0.12f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, ForestGreenPrimary)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(ForestGreenPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "LIVE TREK IN PROGRESS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = ForestGreenPrimary
                                        )
                                    }
                                    if (active.isPaused) {
                                        Text(
                                            "PAUSED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningAmber
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = active.routeTitle.ifBlank { "Active Monitored Trek" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (active.originName.isNotBlank() && active.destinationName.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${active.originName} → ${active.destinationName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val distKm = active.traveledDistanceMeters / 1000.0
                                    val hrs = active.elapsedDurationSeconds / 3600
                                    val mins = (active.elapsedDurationSeconds % 3600) / 60
                                    val timeStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins} min"

                                    Text(
                                        text = "${String.format(Locale.US, "%.2f km", distKm)} traveled • $timeStr",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Button(
                                        onClick = { onNavigateToActiveTrip(active.tripId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(AppIcons.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Resume HUD", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Recently Completed Treks (if available)
                if (state.recentCompletedTrips.isNotEmpty()) {
                    item {
                        AppSectionHeader(
                            title = "Recent Completed Treks (${state.recentCompletedTrips.size})",
                            subtitle = "Review recorded GPS tracks, distance, and duration",
                            actionLabel = "View All",
                            onActionClick = onNavigateToHistory
                        )
                    }
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = state.recentCompletedTrips,
                                key = { it.tripId },
                                contentType = { "recent_completed_trek" }
                            ) { trip ->
                                Surface(
                                    modifier = Modifier
                                        .width(220.dp)
                                        .clickable { onNavigateToCompletedTrekDetail(trip.tripId) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("COMPLETED", style = MaterialTheme.typography.labelSmall, color = ForestGreenPrimary, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = trip.routeTitle.ifBlank { "Completed Trek" },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val distKm = trip.traveledDistanceMeters / 1000.0
                                        val mins = trip.elapsedDurationSeconds / 60
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f km", distKm)} • $mins mins",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Alpine Weather Glance
                item {
                    AlpineWeatherGlance(
                        weather = state.weather,
                        isLoading = state.isLoading
                    )
                }

                // 4. Trail Quick-Starts (Verified Corridors)
                item {
                    AppSectionHeader(
                        title = "Verified Trail Routes",
                        subtitle = "Select a monitored corridor to inspect hazards and safety facilities",
                        actionLabel = "Plan Custom",
                        onActionClick = onNavigateToPlanTrip
                    )
                    VerifiedTrailQuickStarts(onSelectTrail = onNavigateToPlanTrip)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateToDrawRoute,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(AppIcons.DrawRoute, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Draw Route", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onNavigateToHistory,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trek History", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // 5. Live Safety Advisories
                item {
                    AppSectionHeader(
                        title = "Live Safety Advisories (${state.alerts.size})",
                        actionLabel = "View All",
                        onActionClick = onNavigateToAlerts
                    )
                }

                if (state.alerts.isEmpty() && !state.isLoading) {
                    item {
                        AppEmptyState(
                            title = "All Clear on Monitored Trails",
                            description = "No active NDMA/SACHET weather alerts or severe hazard closures detected.",
                            icon = AppIcons.Safe
                        )
                    }
                } else {
                    items(state.alerts.take(5)) { alert ->
                        val stripeColor = when (alert.severity) {
                            AlertSeverity.CRITICAL -> EmergencyRed
                            AlertSeverity.HIGH -> WarningAmber
                            AlertSeverity.MEDIUM -> Color(0xFFF59E0B)
                            AlertSeverity.LOW -> InfoBlue
                        }
                        AppListItem(
                            title = alert.title,
                            subtitle = alert.description,
                            caption = "Source: ${alert.sourceName} • ${alert.category.name}",
                            stripeColor = stripeColor,
                            trailingContent = {
                                AppSeverityChip(severity = alert.severity)
                            },
                            onClick = onNavigateToAlerts
                        )
                    }
                }

                // 6. Direct Emergency & Assistance Card
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppListItem(
                        title = "National Emergency Assistance (112)",
                        subtitle = "Instant dial for SDRF Mountain Rescue, Police & Medical",
                        leadingIcon = AppIcons.DialEmergency,
                        leadingIconTint = Color.White,
                        leadingIconBg = EmergencyRed,
                        trailingContent = {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:112")
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("DIAL 112", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        },
                        showDivider = false,
                        onClick = onNavigateToEmergency
                    )
                }

                // 7. Offline Geofence & Safety Center Shortcut
                item {
                    AppListItem(
                        title = "Offline Safety & SMS Beacon",
                        subtitle = "Register emergency SMS contacts and manage route hazard geofences",
                        leadingIcon = AppIcons.DisasterSafety,
                        leadingIconTint = ForestGreenPrimary,
                        leadingIconBg = SageContainer,
                        showChevron = true,
                        showDivider = false,
                        onClick = onNavigateToDisasterSafety
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Compact Alpine Weather Glance component.
 */
@Composable
private fun AlpineWeatherGlance(weather: Weather?, isLoading: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        if (weather == null) {
            if (isLoading) {
                LoadingSkeletonCard(height = 80.dp)
            } else {
                Text(
                    "Weather forecast unavailable",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${weather.temperatureC}°C",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = weather.weatherDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Wind: ${weather.windSpeedKmh} km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Humidity: ${weather.relativeHumidityPct}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Precip: ${weather.precipitationMm} mm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (weather.isFallback || weather.isStale) {
                        Text(
                            text = "• Cached Forecast",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Active Trek Resumption card.
 */
@Composable
private fun ActiveTrekResumptionCard(itinerary: Itinerary, onExploreMap: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SageContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        AppIcons.Hiking,
                        contentDescription = null,
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACTIVE MONITORED TREK",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ForestGreenPrimary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = itinerary.difficulty.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = itinerary.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ForestGreenDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${itinerary.distanceKm} km • ~${itinerary.estimatedDurationHours} hrs • ${itinerary.region}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryLight
            )

            Spacer(modifier = Modifier.height(12.dp))
            AppPrimaryButton(
                text = "Resume Trek on Map",
                icon = AppIcons.Map,
                onClick = onExploreMap
            )
        }
    }
}

/**
 * Horizontal row of popular verified corridors for fast start.
 */
@Composable
private fun VerifiedTrailQuickStarts(onSelectTrail: () -> Unit) {
    val quickTrails = listOf(
        Pair("Kedarnath Trail", "Gaurikund to Temple • 16 km"),
        Pair("Mussoorie Hills", "Dehradun to Mall Road • 32 km"),
        Pair("Devprayag Confluence", "Rishikesh to Sangam • 70 km"),
        Pair("Haridwar Highway", "Pilgrim Road corridor • 24 km")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        quickTrails.forEach { (title, subtitle) ->
            Surface(
                modifier = Modifier
                    .width(180.dp)
                    .clickable { onSelectTrail() },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
