package com.touristsafety.app.feature.providers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.touristsafety.app.core.model.ProviderStatus
import com.touristsafety.app.core.network.NetworkStatus
import com.touristsafety.app.core.network.ServerConfigManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderStatusScreen(
    viewModel: ProviderStatusViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var editingUrl by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Provider Health & Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Central Connectivity & Hardware Telemetry Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Central Network State",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val (badgeText, badgeColor) = when (uiState.connectivity.status) {
                                NetworkStatus.BACKEND_REACHABLE -> "BACKEND ONLINE" to Color(0xFF2E7D32)
                                NetworkStatus.INTERNET_AVAILABLE -> "INTERNET READY" to Color(0xFF1976D2)
                                NetworkStatus.CONNECTED_NO_INTERNET -> "NO INTERNET" to Color(0xFFF57C00)
                                NetworkStatus.NO_NETWORK -> "OFFLINE" to Color(0xFFD32F2F)
                            }
                            Surface(
                                color = badgeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    badgeText,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Transport", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val transport = when {
                                    uiState.connectivity.isWifi -> "Wi-Fi"
                                    uiState.connectivity.isCellular -> "Cellular Data"
                                    else -> "Disconnected"
                                }
                                Text(transport, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Column {
                                Text("Latency", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val latency = uiState.connectivity.backendPingMs?.let { "${it} ms" } ?: "--"
                                Text(latency, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Column {
                                Text("Mobile Radio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val radio = uiState.telephony?.networkType ?: "Unknown"
                                Text(radio, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // 2. Active Backend Base URL Configuration Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("FastAPI Backend Base URL", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            uiState.currentBaseUrl,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                                .fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("One-Tap Presets:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.setServerUrl(ServerConfigManager.PRESET_RENDER) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.currentBaseUrl.contains("onrender.com")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text("☁️ Live Render Cloud (Production)", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.setServerUrl(ServerConfigManager.PRESET_WIFI_LAN) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text("Wi-Fi LAN", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.setServerUrl(ServerConfigManager.PRESET_USB_REVERSE) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text("USB Reverse", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.setServerUrl(ServerConfigManager.PRESET_EMULATOR) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text("Emulator", fontSize = 11.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                editingUrl = uiState.currentBaseUrl
                                showUrlDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enter Custom URL")
                        }
                    }
                }
            }

            // 2b. Map & Route Geometry Diagnostics Card
            if (uiState.geometryDiagnostics.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Route Geometry Diagnostics",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Surface(
                                    color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "STREAM READY",
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (!uiState.activeRouteTitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    uiState.activeRouteTitle ?: "",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(10.dp))

                            uiState.geometryDiagnostics.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val formattedKey = key.replace("_", " ")
                                        .replaceFirstChar { it.uppercase() }
                                    Text(
                                        formattedKey,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        value.replace("\"", ""),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Provider List Header & Live Smoke Trigger
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Upstream Data Providers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { viewModel.triggerUpstreamSmokeRefresh() }) {
                        Text("Test All Providers")
                    }
                }
            }

            // 4. Provider Status Items
            items(uiState.providers) { provider ->
                ProviderCard(provider = provider) { url ->
                    if (url.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Configure Backend Base URL") },
            text = {
                Column {
                    Text(
                        "Enter the full base URL including /api/v1/ (e.g. http://192.168.1.100:8000/api/v1/):",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingUrl,
                        onValueChange = { editingUrl = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editingUrl.isNotBlank()) {
                        viewModel.setServerUrl(editingUrl)
                    }
                    showUrlDialog = false
                }) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProviderCard(
    provider: ProviderStatus,
    onOpenUrl: (String) -> Unit
) {
    val (statusColor, statusLabel) = when (provider.status.uppercase()) {
        "HEALTHY" -> Color(0xFF2E7D32) to "HEALTHY"
        "LIVE" -> Color(0xFF2E7D32) to "LIVE"
        "STANDBY" -> Color(0xFF1976D2) to "STANDBY"
        "CIRCUIT_OPEN", "RATE_LIMITED", "DEGRADED" -> Color(0xFFF57C00) to provider.status
        "UNCONFIGURED" -> Color(0xFF757575) to "UNCONFIGURED"
        "REFERENCE_ONLY" -> Color(0xFF5E35B1) to "REFERENCE ONLY"
        else -> Color(0xFFD32F2F) to provider.status
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        provider.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Category: ${provider.category.replace('_', ' ').capitalize()}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        statusLabel,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                provider.diagnosticMessage,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (provider.attribution.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    provider.attribution,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (provider.registrationUrl.isNotBlank() && !provider.isConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onOpenUrl(provider.registrationUrl) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Get Free API Key", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun String.capitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

