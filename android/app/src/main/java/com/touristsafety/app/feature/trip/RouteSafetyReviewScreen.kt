package com.touristsafety.app.feature.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.touristsafety.app.core.network.dto.RouteWaypointConditionDto
import com.touristsafety.app.core.network.dto.SafeHavenInfoDto
import com.touristsafety.app.core.offline.OfflineTrekPackDialog
import com.touristsafety.app.core.util.TrekShareHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouteSafetyReviewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToActiveTrip: (String) -> Unit,
    onNavigateToMap: () -> Unit = {},
    viewModel: RouteSafetyReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showExplanationDialog by remember { mutableStateOf(false) }
    var showOfflinePackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is RouteReviewEvent.NavigateToActiveTrip -> onNavigateToActiveTrip(event.tripId)
                is RouteReviewEvent.NavigateToMap -> onNavigateToMap()
            }
        }
    }

    val statusColor = when (state.overallSafetyStatus) {
        "NORMAL", "SAFE" -> Color(0xFF2E7D32)
        "CAUTION" -> Color(0xFFED6C02)
        else -> Color(0xFFD32F2F)
    }

    val statusBg = when (state.overallSafetyStatus) {
        "NORMAL", "SAFE" -> Color(0xFFE8F5E9)
        "CAUTION" -> Color(0xFFFFF3E0)
        else -> Color(0xFFFFEBEE)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TrailGuard Route Safety", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.viewOnMap() }) {
                        Icon(Icons.Default.Map, contentDescription = "View on Map")
                    }
                    IconButton(onClick = {
                        state.currentEntity?.let { entity ->
                            TrekShareHelper.shareTrekPlan(context, entity)
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Trek Plan")
                    }
                    IconButton(onClick = { showOfflinePackDialog = true }) {
                        Icon(
                            if (state.isOfflineSaved) Icons.Default.CloudDone else Icons.Default.SaveAlt,
                            contentDescription = "Offline Trek Pack",
                            tint = if (state.isOfflineSaved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = { viewModel.startTrip(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !state.isStartingTrip && !state.isLoading
                    ) {
                        if (state.isStartingTrip) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Monitored Trip", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Notification / Snackbar feedback
                if (state.message != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    state.message ?: "",
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.clearMessage() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Summary Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    state.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = statusBg,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    val badgeText = when (state.overallSafetyStatus) {
                                        "NORMAL", "SAFE" -> "NORMAL CONDITIONS"
                                        "CAUTION" -> "CAUTION ADVISED"
                                        "HIGH_RISK" -> "HIGH RISK"
                                        else -> state.overallSafetyStatus
                                    }
                                    Text(
                                        badgeText,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                StatItem(label = "Total Distance", value = "${state.distanceKm} km")
                                StatItem(label = "Est. Travel Time", value = "${state.durationMinutes.toInt()} mins")
                                StatItem(label = "Hazard Index", value = "${state.overallHazardScore.toInt()} / 100")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Open-Meteo Hourly • NASA FIRMS Hotspots • Copernicus DEM",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                                // Authoritative Grounded Route Briefing (REM-07)
                val summaryObj = state.groundedSummary
                if (summaryObj != null || state.decisionExplanation.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Authoritative Trail Briefing",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    summaryObj?.summary_text ?: state.decisionExplanation,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (summaryObj != null && summaryObj.direct_intersections.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Direct Trail Intersections:",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    summaryObj.direct_intersections.forEach { item ->
                                        Text("• " + item, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (summaryObj != null && summaryObj.nearby_facilities.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Shelters & Safe Havens:",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    summaryObj.nearby_facilities.forEach { item ->
                                        Text("• " + item, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    summaryObj?.disclaimer ?: "Advisory summary grounded strictly in retrieved database records. Mountain conditions can change rapidly.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                // Provider Status & Regional Coverage Card
                if (state.providerStatuses.isNotEmpty() || state.analysisLimitations.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Data Provider Coverage & Live Status",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.providerStatuses.forEach { (_, provider) ->
                                        val (statusBg, statusFg) = when (provider.status) {
                                            "LOADED" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
                                            "EMPTY" -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
                                            "UNSUPPORTED_REGION" -> Color(0xFFFFF3E0) to Color(0xFFE65100)
                                            "UNCONFIGURED" -> Color(0xFFEEEEEE) to Color(0xFF616161)
                                            else -> Color(0xFFFFEBEE) to Color(0xFFC62828)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = statusBg,
                                            border = BorderStroke(1.dp, statusFg.copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    provider.provider_name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    provider.status,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = statusFg
                                                )
                                            }
                                        }
                                    }
                                }

                                if (state.analysisLimitations.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Analysis Limitations & Regional Boundaries:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    state.analysisLimitations.forEach { limitation ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text("• ", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                            Text(
                                                limitation,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Offline Trek Pack Download CTA
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.isOfflineSaved) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (state.isOfflineSaved) Icons.Default.CloudDone else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (state.isOfflineSaved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (state.isOfflineSaved) "Offline Trek Pack Ready" else "Download Offline Trek Pack",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (state.isOfflineSaved) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    if (state.isOfflineSaved) "Route geometry, OpenFreeMap vector tiles & verified hazards saved locally."
                                    else "Preload corridor map tiles and disaster radar for network dead zones.",
                                    fontSize = 11.sp,
                                    color = if (state.isOfflineSaved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { showOfflinePackDialog = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (state.isOfflineSaved) "Manage" else "Download", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 2. Elevation Profile Canvas
                if (state.elevationProfile.isNotEmpty()) {
                    item {
                        ElevationProfileCard(
                            profilePoints = state.elevationProfile,
                            totalAscentM = state.totalAscentM,
                            maxElevationM = state.maxElevationM
                        )
                    }
                }

                // 3. TrailGuard Contrastive Decision Breakdown ("Why this route?")
                if (state.decisionExplanation.isNotBlank()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "TrailGuard Intelligence: Safety Explanation",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = state.decisionExplanation,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // 4. Safe Havens & Emergency Egress Points
                if (state.safeHavens.isNotEmpty()) {
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Emergency Safe Havens along Corridor", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            state.safeHavens.take(3).forEach { haven ->
                                SafeHavenCard(haven)
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }

                // Alternative detour banner
                if (state.alternatives.isNotEmpty() && state.overallSafetyStatus != "SAFE") {
                    item {
                        val alt = state.alternatives[0]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AltRoute,
                                    contentDescription = null,
                                    tint = Color(0xFF1565C0),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        alt.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF1565C0)
                                    )
                                    Text(
                                        alt.description,
                                        fontSize = 12.sp,
                                        color = Color(0xFF0D47A1)
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.selectAlternative(alt.id) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        if (state.selectedAlternativeId == alt.id) "Selected" else "Switch",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Waypoint Timeline Header
                item {
                    Text(
                        "Corridor Condition Timeline (${state.conditions.size} Waypoints)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                // Segment Cards
                items(state.conditions) { wp ->
                    WaypointConditionCard(wp)
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }

        if (showOfflinePackDialog && state.currentEntity != null) {
            OfflineTrekPackDialog(
                route = state.currentEntity!!,
                offlineManager = viewModel.offlineTrekPackManager,
                onDismiss = { showOfflinePackDialog = false },
                onDownloadComplete = {
                    showOfflinePackDialog = false
                    viewModel.onOfflineDownloadCompleted()
                }
            )
        }
    }
}

@Composable
fun SafeHavenCard(haven: SafeHavenInfoDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Cabin, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(haven.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1B5E20))
                Text("Safe Haven • ${haven.distance_km} km egress distance", fontSize = 11.sp, color = Color(0xFF388E3C))
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun WaypointConditionCard(wp: RouteWaypointConditionDto) {
    val wpColor = when (wp.hazard_level) {
        "CRITICAL" -> Color(0xFFD32F2F)
        "HIGH" -> Color(0xFFF57C00)
        "MEDIUM" -> Color(0xFFFBC02D)
        else -> Color(0xFF388E3C)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name & Arrival Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(wpColor, RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(wp.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                val timeStr = if (wp.estimated_arrival_at.length >= 16) {
                    wp.estimated_arrival_at.substring(11, 16)
                } else wp.estimated_arrival_at
                Text("ETA $timeStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Weather & Terrain row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("${wp.weather.temperature_c.toInt()}°C", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Thermostat, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${wp.weather.precipitation_probability_pct.toInt()}% Rain", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                if (wp.elevation_m > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text("${wp.elevation_m.toInt()}m (${wp.slope_degrees.toInt()}°)", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Landscape, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }

            Text(
                wp.weather.weather_description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Contributing factors callout
            if (wp.contributing_factors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                wp.contributing_factors.take(2).forEach { factor ->
                    Text("• $factor", fontSize = 11.sp, color = wpColor, fontWeight = FontWeight.Medium)
                }
            }

            // Forest badge
            if (wp.forest != null && wp.forest.in_forest) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Park, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${wp.forest.forest_name ?: "Woodland"} (${wp.forest.forest_type ?: "Forest"})",
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            // Wildfire alert
            if (wp.wildfire != null && wp.wildfire.has_fire_within_5km) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "NASA FIRMS Satellite Thermal Anomaly ${wp.wildfire.nearest_fire_distance_km} km away (FRP: ${wp.wildfire.fire_radiative_power ?: 0.0})",
                            fontSize = 11.sp,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Disaster Alerts
            if (wp.alerts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                wp.alerts.forEach { alert ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(alert.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                            }
                            if (!alert.issuing_authority.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Source: ${alert.issuing_authority}", fontSize = 10.sp, color = Color(0xFFBF360C), fontWeight = FontWeight.SemiBold)
                            }
                            if (!alert.area_description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Advisory Area: ${alert.area_description}", fontSize = 10.sp, color = Color(0xFFE65100))
                            }
                            if (!alert.published_at.isNullOrBlank()) {
                                val pubStr = if (alert.published_at.length >= 16) alert.published_at.substring(0, 16).replace("T", " ") else alert.published_at
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Issued: $pubStr UTC", fontSize = 10.sp, color = Color(0xFF795548))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(alert.required_protective_instructions, fontSize = 11.sp, color = Color(0xFFEF6C00))
                        }
                    }
                }
            }
        }
    }
}
