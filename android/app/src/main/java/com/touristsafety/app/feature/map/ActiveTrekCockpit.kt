package com.touristsafety.app.feature.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.designsystem.components.*
import com.touristsafety.app.core.location.ActiveHazardAlert
import com.touristsafety.app.core.model.RouteOrderedHazardItem
import com.touristsafety.app.core.network.dto.NearbyFacilityDto

/**
 * Top HUD displaying live trek odometer, elapsed duration, progress, live weather, network connectivity,
 * sound/voice toggles, and safety deviation alerts.
 */
@Composable
fun ActiveTrekTopHud(
    state: MapUiState,
    onToggleVoice: (Boolean) -> Unit,
    onToggleSound: () -> Unit,
    onToggleVibration: () -> Unit,
    onDismissAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Row 1: Odometer, Elapsed, Remaining, and Progress %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppMetricDisplay(
                    value = String.format(java.util.Locale.US, "%.2f km", state.traveledDistanceKm),
                    label = "Odometer",
                    valueColor = ForestGreenPrimary
                )
                VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
                AppMetricDisplay(
                    value = state.elapsedDurationFormatted,
                    label = "Elapsed",
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
                AppMetricDisplay(
                    value = String.format(java.util.Locale.US, "%.2f km", state.remainingDistanceKm),
                    label = "Remaining",
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
                AppMetricDisplay(
                    value = "${state.trekProgressPct.toInt()}%",
                    label = "Progress",
                    valueColor = ForestGreenPrimary
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Row 2: Live Conditions (Weather, Cellular Network, Audio Alerts, Voice Guidance)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Weather Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = InfoBlue,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val tempStr = state.liveWeather.currentTemperatureC?.let { "${it.toInt()}°C" } ?: "--°C"
                    val descStr = state.liveWeather.weatherDescription ?: "Fair"
                    Text(
                        text = "$tempStr • $descStr",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Network Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state.liveNetwork.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (state.liveNetwork.isConnected) ForestGreenPrimary else WarningAmber,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val netLabel = if (state.liveNetwork.isConnected) {
                        val towerKm = state.liveNetwork.nearestTowerDistanceM?.let { String.format(java.util.Locale.US, " (%.1fkm)", it / 1000.0) } ?: ""
                        "${state.liveNetwork.connectionType}$towerKm"
                    } else {
                        "Offline"
                    }
                    Text(
                        text = netLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.liveNetwork.isConnected) MaterialTheme.colorScheme.onSurface else WarningAmber
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Sound Toggle
                    IconButton(
                        onClick = onToggleSound,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = if (state.soundAlertsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Sound Alerts",
                            tint = if (state.soundAlertsEnabled) ForestGreenPrimary else Color.Gray,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Voice Guidance Toggle
                    IconButton(
                        onClick = { onToggleVoice(!state.isVoiceGuidanceEnabled) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isVoiceGuidanceEnabled) Icons.Default.RecordVoiceOver else Icons.Default.VoiceOverOff,
                            contentDescription = "Toggle Voice Guidance",
                            tint = if (state.isVoiceGuidanceEnabled) ForestGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Deterministic Safety Event Banner (if triggered)
            if (state.activeSafetyEvent != null) {
                val ev = state.activeSafetyEvent
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (ev.requiresImmediateAlarm) EmergencyRed.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (ev.requiresImmediateAlarm) EmergencyRed else WarningAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (ev.requiresImmediateAlarm) EmergencyRed else WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${ev.severity}: ${ev.hazardTitle}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (ev.requiresImmediateAlarm) EmergencyRed else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                ev.deterministicActionText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onDismissAlert,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Off-Route Warning (if active)
            if (state.isOffRoute) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = WarningAmber.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "OFF TRAIL: Cross-track deviation >80m. Rejoin route.",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Loud critical alert banner triggered when approaching severe hazards.
 */
@Composable
fun ActiveHazardAlertBanner(
    alert: ActiveHazardAlert,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = EmergencyRed,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                AppIcons.Siren,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HAZARD AHEAD: ${alert.title}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${String.format(java.util.Locale.US, "%.2f km", alert.distanceKm)} away • ${alert.instructions}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("MUTE", color = EmergencyRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Bottom interactive cockpit combining AI Safety Advisor recommendations,
 * question input bar, ahead-on-route hazard timeline, nearby emergency facilities,
 * and trek lifecycle action buttons (Pause, Resume, End, Check-in, SOS).
 */
@Composable
fun ActiveTrekAiSafetyPanel(
    state: MapUiState,
    onToggleExpanded: () -> Unit,
    onAskAssistant: (String) -> Unit,
    onReplayVoice: () -> Unit,
    onHazardClick: (RouteOrderedHazardItem) -> Unit,
    onPauseTrek: () -> Unit,
    onResumeTrek: () -> Unit,
    onEndTrek: () -> Unit,
    onCheckIn: () -> Unit,
    onTriggerSos: () -> Unit,
    modifier: Modifier = Modifier
) {
    var assistantInputText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // AI Safety Advisor Card (Docked above bottom controls)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Header row (always visible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpanded() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val upcomingCount = state.upcomingHazards.size
                        val indicatorColor = if (upcomingCount > 0) EmergencyRed else ForestGreenPrimary
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(indicatorColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (upcomingCount > 0) "Upcoming Hazards ($upcomingCount Ahead)" else "Trail Status: All Clear Ahead",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isSafetyAssistantExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Toggle Safety Assistant"
                        )
                    }
                }

                // Collapsed Summary Line
                if (!state.isSafetyAssistantExpanded) {
                    val firstAhead = state.upcomingHazards.firstOrNull()
                    if (firstAhead != null) {
                        val distStr = firstAhead.formattedDistanceAhead() ?: ""
                        val timeStr = firstAhead.estimatedMinutesAhead?.let { " • ~$it min" } ?: ""
                        Text(
                            text = "Next: ${firstAhead.sequenceLabel} ${firstAhead.feature.title} ($distStr$timeStr)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            text = "No active hazards detected within remaining planned route.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ForestGreenPrimary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Expanded Details
                AnimatedVisibility(visible = state.isSafetyAssistantExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .padding(top = 8.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 1. AI Safety Assistant Guidance Card
                            if (state.assistantGuidance != null) {
                                val g = state.assistantGuidance
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, if (g.is_ai_generated) ForestGreenPrimary.copy(alpha = 0.4f) else InfoBlue.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.RecordVoiceOver,
                                                        contentDescription = null,
                                                        tint = if (g.is_ai_generated) ForestGreenPrimary else InfoBlue,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        if (g.is_ai_generated) "GROQ AI SAFETY ADVISOR" else "OFFLINE PRE-APPROVED GUIDANCE",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (g.is_ai_generated) ForestGreenPrimary else InfoBlue
                                                    )
                                                }

                                                TextButton(
                                                    onClick = onReplayVoice,
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(24.dp)
                                                ) {
                                                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Replay", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }

                                            Text(
                                                g.summary,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )

                                            if (g.recommended_action.isNotBlank()) {
                                                Text(
                                                    "Action: ${g.recommended_action}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = ForestGreenPrimary,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }

                                            if (g.what_to_avoid.isNotBlank()) {
                                                Text(
                                                    "Avoid: ${g.what_to_avoid}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = EmergencyRed,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Quick-Action Question Chips
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val chips = listOf(
                                        "What is the next hazard?",
                                        "What's the weather ahead?",
                                        "Nearest medical clinic?",
                                        "Is there an alternative trail?"
                                    )
                                    items(chips.size) { idx ->
                                        val chipText = chips[idx]
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.clickable { onAskAssistant(chipText) }
                                        ) {
                                            Text(
                                                chipText,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Interactive Query Input Bar
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = assistantInputText,
                                        onValueChange = { assistantInputText = it },
                                        placeholder = { Text("Ask trek safety assistant...", style = MaterialTheme.typography.bodySmall) },
                                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            val query = assistantInputText
                                            assistantInputText = ""
                                            onAskAssistant(query)
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        if (state.isAssistantQueryLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Send, contentDescription = "Send Query", tint = ForestGreenPrimary)
                                        }
                                    }
                                }
                            }

                            // Ahead on Route Section
                            item {
                                Text(
                                    "AHEAD ON ROUTE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (state.upcomingHazards.isEmpty()) {
                                item {
                                    Text(
                                        "No upcoming hazards along remaining route.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(state.upcomingHazards.size) { idx ->
                                    val item = state.upcomingHazards[idx]
                                    HazardListItemRow(
                                        item = item,
                                        onClick = { onHazardClick(item) }
                                    )
                                }
                            }

                            // Near Current Location Section
                            if (state.nearbyHazards.isNotEmpty() || state.nearbyFacilities.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "NEAR CURRENT LOCATION",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmergencyRed
                                    )
                                }
                                items(state.nearbyHazards.size) { idx ->
                                    val item = state.nearbyHazards[idx]
                                    HazardListItemRow(
                                        item = item,
                                        onClick = { onHazardClick(item) }
                                    )
                                }
                                items(state.nearbyFacilities.size) { idx ->
                                    val facility = state.nearbyFacilities[idx]
                                    FacilityListItemRow(facility = facility)
                                }
                            }

                            // Passed Section
                            if (state.passedHazards.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "PASSED HAZARDS (${state.passedHazards.size} CLEARED)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestGreenPrimary
                                    )
                                }
                                items(state.passedHazards.size) { idx ->
                                    val passed = state.passedHazards[idx]
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "${passed.feature.title} • Cleared",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Trek Primary Controls Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pause / Resume Button
                    if (state.isTrekPaused) {
                        Button(
                            onClick = onResumeTrek,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume Trek", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onPauseTrek,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(AppIcons.PauseTrek, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause Trek", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // End Trek Button
                    Button(
                        onClick = onEndTrek,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                    ) {
                        Icon(AppIcons.StopTrek, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("End Trek", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Emergency Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Safety Check-In Button
                    FilledTonalButton(
                        onClick = onCheckIn,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !state.isCheckingIn
                    ) {
                        if (state.isCheckingIn) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Safety Check-In", fontSize = 12.sp)
                        }
                    }

                    // SOS Emergency Beacon Button
                    Button(
                        onClick = onTriggerSos,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isSosTriggered) Color.White else EmergencyRedContainer,
                            contentColor = EmergencyRed
                        )
                    ) {
                        Icon(AppIcons.SosBeacon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (state.isSosTriggered) "SOS ACTIVE" else "SOS Beacon",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HazardListItemRow(
    item: RouteOrderedHazardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (item.severity == "CRITICAL" || item.severity == "SEVERE") EmergencyRed else WarningAmber
            ) {
                Text(
                    text = item.sequenceLabel,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.feature.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                val aheadStr = item.formattedDistanceAhead() ?: "${(item.distanceAlongRouteMeters / 1000.0).toInt()} km"
                val etaStr = item.estimatedMinutesAhead?.let { " • ~$it min walk" } ?: ""
                Text(
                    text = "$aheadStr$etaStr • ${item.categoryType.name.replace('_', ' ')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun FacilityListItemRow(
    facility: NearbyFacilityDto,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ForestGreenPrimary.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (facility.category.lowercase()) {
                    "hospital" -> AppIcons.Hospital
                    "police" -> AppIcons.Police
                    else -> AppIcons.Shelter
                },
                contentDescription = null,
                tint = ForestGreenPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = facility.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${facility.category.replace('_', ' ')} • ${String.format(java.util.Locale.US, "%.2f km away", facility.distance_km)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
