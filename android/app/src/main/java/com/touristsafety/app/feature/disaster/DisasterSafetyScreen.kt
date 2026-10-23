package com.touristsafety.app.feature.disaster

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.touristsafety.app.core.database.entity.HazardZoneEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisasterSafetyScreen(
    viewModel: DisasterSafetyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hazardZones by viewModel.hazardZones.collectAsState()
    val subscriber by viewModel.subscriber.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val lastLocation by viewModel.lastLocation.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var phoneInput by remember { mutableStateOf("+919876543210") }
    var otpInput by remember { mutableStateOf("123456") }
    var consentChecked by remember { mutableStateOf(true) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            viewModel.toggleHazardMonitoring(true)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.infoMessage, uiState.errorMessage) {
        val msg = uiState.infoMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Disaster Alert & Offline Safety") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync Alerts")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Section 1: Offline Hazard Geofencing Opt-In Card
                HazardMonitoringCard(
                    isMonitoring = isMonitoring,
                    cachedZonesCount = hazardZones.size,
                    hasLocation = lastLocation != null,
                    onToggle = { enable ->
                        if (enable) {
                            val fineCheck = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                            if (fineCheck == PackageManager.PERMISSION_GRANTED) {
                                viewModel.toggleHazardMonitoring(true)
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        } else {
                            viewModel.toggleHazardMonitoring(false)
                        }
                    }
                )
            }

            item {
                // Section 2: Emergency SMS Fallback Registration Card
                SmsSubscriptionCard(
                    subscriber = subscriber,
                    phoneInput = phoneInput,
                    onPhoneChange = { phoneInput = it },
                    onRequestOtp = { viewModel.requestOtp(phoneInput) },
                    onUnsubscribe = { viewModel.unsubscribe() }
                )
            }

            item {
                // Section 3: Diagnostic Test Alarm & Dummy Alert Button
                val isSirenPlaying by viewModel.isSirenPlaying.collectAsState()
                TestAlarmCard(
                    isSirenPlaying = isSirenPlaying,
                    onTriggerTest = { viewModel.triggerDummyAlert() },
                    onStopSiren = { viewModel.stopDisasterSiren() }
                )
            }

            item {
                // Section 4: Header for Cached & Active Hazard Zones
                Text(
                    text = "Active & Offline Cached Hazard Zones (${hazardZones.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Stored in local Room database. Protected by on-device GPS geofencing even without internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hazardZones.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "No active disaster hazard zones found.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Tap sync above to fetch the latest disaster advisories from the backend.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(hazardZones, key = { it.alertId }) { zone ->
                    HazardZoneCard(zone = zone)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // OTP Verification Dialog
    if (uiState.isOtpDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissOtpDialog() },
            title = { Text("Verify Mobile Number") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter the 6-digit verification code sent to ${uiState.pendingPhone}.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Demo / Test Mock Code: 123456",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { otpInput = it },
                        label = { Text("6-Digit OTP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = consentChecked,
                            onCheckedChange = { consentChecked = it }
                        )
                        Text(
                            "I explicitly consent to emergency disaster warnings via SMS for my subscribed area.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.verifyOtp(
                            otp = otpInput,
                            consent = consentChecked,
                            areas = listOf("Kullu", "Rohtang", "all")
                        )
                    },
                    enabled = otpInput.isNotBlank() && consentChecked
                ) {
                    Text("Verify & Subscribe")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissOtpDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HazardMonitoringCard(
    isMonitoring: Boolean,
    cachedZonesCount: Int,
    hasLocation: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMonitoring) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (isMonitoring) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Monitor Saved Route for Hazards",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Switch(
                    checked = isMonitoring,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (isMonitoring) {
                Text(
                    text = "STATUS: ACTIVE on-device GPS geofencing. Monitoring $cachedZonesCount cached hazard zones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1B5E20),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "If you enter a danger zone while offline without internet, you will receive an immediate local warning siren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32)
                )
            } else {
                Text(
                    text = "Enable to continuously monitor your position against downloaded hazard zones without uploading your location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SmsSubscriptionCard(
    subscriber: com.touristsafety.app.core.database.entity.SubscriberEntity?,
    phoneInput: String,
    onPhoneChange: (String) -> Unit,
    onRequestOtp: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (subscriber?.consentGiven == true) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = if (subscriber?.consentGiven == true) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Emergency Cellular SMS Fallback",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (subscriber != null && subscriber.consentGiven) {
                Text(
                    text = "SUBSCRIPTION ACTIVE: ${subscriber.phone}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF0D47A1),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Area: ${subscriber.subscribedAreas}. If mobile data is lost in remote valleys, verified SEVERE / CRITICAL disaster warnings will arrive via cellular SMS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1565C0)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onUnsubscribe,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Unsubscribe from SMS")
                }
            } else {
                Text(
                    text = "Register your mobile number to receive emergency disaster warnings by SMS when data coverage is unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = onPhoneChange,
                    label = { Text("E.164 Mobile Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRequestOtp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Verification OTP")
                }
            }
        }
    }
}

@Composable
private fun TestAlarmCard(
    isSirenPlaying: Boolean,
    onTriggerTest: () -> Unit,
    onStopSiren: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Dummy Alert & Emergency Ring Sound",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Simulates a full disaster warning: fires high-priority Android notification, sounds the emergency alarm siren ring tone, and executes pulse vibration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTriggerTest,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trigger Dummy Alert", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (isSirenPlaying) {
                    Button(
                        onClick = onStopSiren,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Silence Siren", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HazardZoneCard(zone: HazardZoneEntity) {
    val severityColor = when (zone.severity.uppercase()) {
        "CRITICAL" -> Color(0xFFB71C1C)
        "SEVERE" -> Color(0xFFE65100)
        "WARNING" -> Color(0xFFF57F17)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = zone.severity.uppercase(),
                    color = severityColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                if (zone.isSimulated) {
                    Surface(
                        color = Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "SIMULATED",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF424242),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = zone.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = zone.description,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))
            // Instructions callout box
            Surface(
                color = severityColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "PROTECTIVE ACTION REQUIRED:",
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = zone.requiredProtectiveInstructions,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Issuing Authority: ${zone.issuingAuthority}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Radius: ${zone.radiusMeters.toInt()}m around (${String.format("%.4f", zone.centerLatitude)}, ${String.format("%.4f", zone.centerLongitude)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "[OFFLINE ROOM DB]: Cached for disconnected geofence matching.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

