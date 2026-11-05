package com.touristsafety.app.feature.home

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touristsafety.app.core.database.entity.ActiveTripEntity
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.designsystem.components.*
import com.touristsafety.app.core.model.Alert
import com.touristsafety.app.core.model.AlertCategory
import com.touristsafety.app.core.model.AlertSeverity
import com.touristsafety.app.core.model.Weather
import java.text.SimpleDateFormat
import java.util.*

/**
 * Stateful Route Composable: Collects flows, injects ViewModel, manages local UI dialogs.
 */
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
    var showProfileDialog by remember { mutableStateOf(false) }

    HomeContent(
        state = state,
        isSirenPlaying = isSirenPlaying,
        userEmail = viewModel.getUserEmail(),
        isGuest = viewModel.isGuest(),
        showProfileDialog = showProfileDialog,
        onShowProfileDialogChange = { showProfileDialog = it },
        onRefresh = { viewModel.refreshAll() },
        onStopDisasterSiren = { viewModel.stopDisasterSiren() },
        onLogout = {
            viewModel.logout()
            onNavigateToWelcome()
        },
        onSignInOrRegister = {
            viewModel.logout()
            onNavigateToWelcome()
        },
        onNavigateToMap = onNavigateToMap,
        onNavigateToAlerts = onNavigateToAlerts,
        onNavigateToEmergency = onNavigateToEmergency,
        onNavigateToDisasterSafety = onNavigateToDisasterSafety,
        onNavigateToProviders = onNavigateToProviders,
        onNavigateToPlanTrip = onNavigateToPlanTrip,
        onNavigateToActiveTrip = onNavigateToActiveTrip,
        onNavigateToCompletedTrekDetail = onNavigateToCompletedTrekDetail,
        onNavigateToDrawRoute = onNavigateToDrawRoute,
        onNavigateToHistory = onNavigateToHistory,
        modifier = modifier
    )
}

/**
 * Stateless Content Composable: Pure rendering matching the reference dark outdoor design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    isSirenPlaying: Boolean,
    userEmail: String?,
    isGuest: Boolean = false,
    showProfileDialog: Boolean,
    onShowProfileDialogChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onStopDisasterSiren: () -> Unit,
    onLogout: () -> Unit = {},
    onSignInOrRegister: () -> Unit = {},
    onNavigateToMap: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    onNavigateToDisasterSafety: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToPlanTrip: () -> Unit,
    onNavigateToActiveTrip: (String) -> Unit,
    onNavigateToCompletedTrekDetail: (String) -> Unit,
    onNavigateToDrawRoute: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (showProfileDialog) {
        AccountProfileDialog(
            userEmail = userEmail,
            isGuest = isGuest,
            isOffline = state.isOffline,
            lastSyncTimestamp = state.lastSyncTimestamp,
            onDismiss = { onShowProfileDialogChange(false) },
            onLogout = onLogout,
            onSignInOrRegister = onSignInOrRegister,
            onViewDiagnostics = onNavigateToProviders
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
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        val syncText = remember(state.lastSyncTimestamp, state.isOffline) {
                            if (state.isOffline) {
                                "Offline Cache Active"
                            } else if (state.lastSyncTimestamp <= 0L) {
                                "Synchronizing..."
                            } else {
                                val diffMs = System.currentTimeMillis() - state.lastSyncTimestamp
                                val diffHours = diffMs / 3_600_000.0
                                if (diffHours < 0.1) {
                                    "Last-synced just now"
                                } else if (diffHours < 24.0) {
                                    String.format(Locale.US, "Last-synced %.1f hours ago", diffHours)
                                } else {
                                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    "Last-synced at ${sdf.format(Date(state.lastSyncTimestamp))}"
                                }
                            }
                        }
                        Text(
                            syncText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isOffline) WarningAmber else TextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Data",
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToAlerts) {
                        BadgedBox(
                            badge = {
                                if (state.alerts.isNotEmpty()) {
                                    Badge(
                                        containerColor = CriticalRed,
                                        contentColor = Color.White
                                    ) {
                                        Text("${state.alerts.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Active Advisories",
                                tint = TextPrimary
                            )
                        }
                    }
                    IconButton(onClick = { onShowProfileDialogChange(true) }) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = "Account Profile",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            OfflineBanner(isOffline = state.isOffline)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. High-Priority Disaster Siren Alarm
                if (isSirenPlaying) {
                    item(key = "disaster_siren_alarm", contentType = "alarm_banner") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(CriticalRed)
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
                                    onClick = onStopDisasterSiren,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("SILENCE", color = CriticalRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Active Ongoing Trek HUD Banner (High Priority)
                state.activeTrip?.let { active ->
                    item(key = "active_ongoing_trek_card", contentType = "active_trek_card") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToActiveTrip(active.tripId) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = DarkSurfacePrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MintPrimary)
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
                                                .background(MintPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "LIVE TREK IN PROGRESS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MintPrimary
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
                                    color = TextPrimary
                                )

                                if (active.originName.isNotBlank() && active.destinationName.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${active.originName} → ${active.destinationName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
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
                                        text = "${String.format(Locale.US, "%.2f km", distKm)} • $timeStr",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )

                                    Button(
                                        onClick = { onNavigateToActiveTrip(active.tripId) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MintPrimary,
                                            contentColor = DarkBackground
                                        ),
                                        shape = RoundedCornerShape(20.dp),
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

                // 3. Section: Weather
                item(key = "section_weather") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Weather",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        val weather = state.weather
                        val temp = weather?.temperatureC?.toInt()
                        val cond = weather?.weatherDescription ?: if (state.isLoading) "Fetching weather..." else "Weather forecast unavailable"
                        val loc = "Kedarnath Valley"
                        val alertNote = if (state.alerts.isEmpty()) "No alerts" else "${state.alerts.size} active alerts"

                        WeatherSummarySurface(
                            temperatureC = temp,
                            conditionText = cond,
                            locationName = loc,
                            alertsNote = alertNote,
                            onClick = onNavigateToMap
                        )
                    }
                }

                // 4. Section: Verified Trail Routes
                item(key = "section_verified_trails") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Verified Trail Routes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Plan Custom",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MintPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onNavigateToPlanTrip() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(VERIFIED_CORRIDORS_PREVIEW, key = { it.title }) { corridor ->
                                TrailRouteCard(
                                    title = corridor.title,
                                    subtitle = corridor.subtitle,
                                    distanceKm = corridor.distanceKm,
                                    onClick = onNavigateToPlanTrip
                                )
                            }
                        }
                    }
                }

                // 5. Section: Live Safety Advisories
                item(key = "section_live_safety_advisories") {
                    val criticalCount = state.alerts.count { it.severity == AlertSeverity.CRITICAL }
                    val warningCount = state.alerts.count { it.severity == AlertSeverity.HIGH || it.severity == AlertSeverity.MEDIUM }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Safety Advisories",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (state.alerts.isNotEmpty()) {
                                Text(
                                    text = "View All (${state.alerts.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onNavigateToAlerts() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Prominent Circular Status Card ("All Clear" or Warning/Critical)
                        SafetyStatusCard(
                            criticalCount = criticalCount,
                            warningCount = warningCount,
                            isOffline = state.isOffline,
                            onClick = onNavigateToAlerts
                        )

                        // If active alerts exist, show concise rows matching the reference design
                        if (state.alerts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            state.alerts.take(3).forEach { alert ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNavigateToAlerts() },
                                    shape = RoundedCornerShape(12.dp),
                                    color = DarkSurfacePrimary,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkDivider)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val iconColor = when (alert.severity) {
                                            AlertSeverity.CRITICAL -> CriticalRed
                                            AlertSeverity.HIGH -> WarningAmber
                                            else -> WarningAmber
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = alert.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = alert.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Section: Live Map Preview
                item(key = "section_live_map_preview") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Map Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Open Map",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MintPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onNavigateToMap() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        // Lightweight Corridor Preview Card (zero duplicate MapView OpenGL allocation)
                        LiveMapPreviewCard(onOpenMap = onNavigateToMap)
                    }
                }

                // 7. Section: Critical Emergency Block
                item(key = "section_critical_emergency") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Critical Emergency Block",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        CriticalEmergencyBlock(
                            onDial112 = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:112")
                                }
                                context.startActivity(intent)
                            },
                            onOfflineSafety = onNavigateToEmergency
                        )
                    }
                }

                // 8. Section: Recently Completed Treks (if available)
                if (state.recentCompletedTrips.isNotEmpty()) {
                    item(key = "section_recent_treks") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Treks (${state.recentCompletedTrips.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "View All",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MintPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onNavigateToHistory() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = state.recentCompletedTrips,
                                    key = { it.tripId }
                                ) { trip ->
                                    Surface(
                                        modifier = Modifier
                                            .width(220.dp)
                                            .clickable { onNavigateToCompletedTrekDetail(trip.tripId) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = DarkSurfacePrimary,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkDivider)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MintPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "COMPLETED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MintPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = trip.routeTitle.ifBlank { "Completed Trek" },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val distKm = trip.traveledDistanceMeters / 1000.0
                                            val mins = trip.elapsedDurationSeconds / 60
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f km", distKm)} • $mins mins",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Padding
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private data class TrailPreviewItem(
    val title: String,
    val subtitle: String,
    val distanceKm: Double
)

private val VERIFIED_CORRIDORS_PREVIEW = listOf(
    TrailPreviewItem("Kedarnath Trail", "Gaurikund to Temple", 16.0),
    TrailPreviewItem("Mussoorie Hills", "Dehradun to Mall Road", 32.0),
    TrailPreviewItem("Devprayag Confluence", "Rishikesh to Sangam", 70.0),
    TrailPreviewItem("Haridwar Highway", "Pilgrim Road corridor", 24.0)
)

// Multi-Device @Preview Composables
@Preview(name = "Phone - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun HomeScreenPreviewDark() {
    TouristSafetyTheme(darkTheme = true) {
        HomeContent(
            state = HomeUiState(
                activeTrip = null,
                alerts = listOf(
                    Alert(
                        id = "a1",
                        title = "Kedarnath Trail • recent alerts",
                        description = "Loose scree and active falling stones detected along corridor.",
                        severity = AlertSeverity.HIGH,
                        category = AlertCategory.HAZARD,
                        sourceName = "DDMA Rudraprayag",
                        sourcePublishedAtEpochMs = System.currentTimeMillis() - 600000L,
                        retrievedAtEpochMs = System.currentTimeMillis()
                    )
                )
            ),
            isSirenPlaying = false,
            userEmail = "explorer@field.in",
            showProfileDialog = false,
            onShowProfileDialogChange = {},
            onRefresh = {},
            onStopDisasterSiren = {},
            onNavigateToMap = {},
            onNavigateToAlerts = {},
            onNavigateToEmergency = {},
            onNavigateToDisasterSafety = {},
            onNavigateToProviders = {},
            onNavigateToPlanTrip = {},
            onNavigateToActiveTrip = {},
            onNavigateToCompletedTrekDetail = {},
            onNavigateToDrawRoute = {},
            onNavigateToHistory = {}
        )
    }
}
