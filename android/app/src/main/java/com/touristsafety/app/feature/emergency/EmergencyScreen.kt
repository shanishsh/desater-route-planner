package com.touristsafety.app.feature.emergency

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.designsystem.components.*

private val OFFICIAL_UTTARAKHAND_CONTACTS = listOf(
    Triple("SDRF", "Rescue helpline", "1077"),
    Triple("SEOC", "Rescue helpline", "1070"),
    Triple("Van Agni", "Rescue helpline", "1800-180-4141"),
    Triple("Trekker & Pilgrim", "Char Dham helpline", "1364"),
    Triple("Ambulance & Medical", "State medical emergency", "108")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    viewModel: EmergencyViewModel,
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToDisasterSafety: () -> Unit = {},
    onNavigateToProviders: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Safety Hub & Emergency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Dial National Emergency (112) Hero Banner (Screen 4 Reference)
            item(key = "emergency_hero_banner") {
                EmergencyHeroCard(
                    onCallClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:112")
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 2. Section: Location (Screen 4 Reference)
            item(key = "section_location") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    LocationCard(
                        latitude = state.userLatitude,
                        longitude = state.userLongitude,
                        accuracyMeters = null,
                        onCopyCoordinates = {
                            if (state.userLatitude != null && state.userLongitude != null) {
                                val coordsStr = String.format(java.util.Locale.US, "%.5f, %.5f", state.userLatitude, state.userLongitude)
                                clipboardManager.setText(AnnotatedString(coordsStr))
                                Toast.makeText(context, "Coordinates copied: $coordsStr", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "GPS location still acquiring...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenMap = {}
                    )
                }
            }

            // 3. Section: Beacon (Screen 4 Reference)
            item(key = "section_beacon") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Beacon",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    DistressBeaconCard(
                        onTransmitBeacon = {
                            viewModel.triggerFieldSos()
                            Toast.makeText(context, "Distress beacon transmitted to local network & SMS cache", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }

            // SOS Feedback Banner (if active)
            state.sosMessage?.let { msg ->
                item(key = "sos_feedback") {
                    AppErrorBanner(
                        message = msg,
                        isCritical = false,
                        icon = AppIcons.Safe,
                        onDismiss = { viewModel.dismissSosMessage() }
                    )
                }
            }

            // 4. Section: Uttarakhand Rescue Directory (Screen 4 Reference)
            item(key = "section_rescue_directory") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Uttarakhand Rescue Directory",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OFFICIAL_UTTARAKHAND_CONTACTS.forEach { (name, desc, phone) ->
                            RescueDirectoryRow(
                                authorityName = name,
                                roleDescription = desc,
                                phoneNumber = phone,
                                onCallClick = {
                                    val cleanNum = phone.replace("-", "")
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:$cleanNum")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }

            // 5. Section: Safety & Operational Modules
            item(key = "section_safety_modules") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Safety & Operational Modules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurfacePrimary,
                        border = BorderStroke(1.dp, DarkDivider)
                    ) {
                        Column {
                            // Live Weather & Disaster Advisories
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAlerts() }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x33F2A477)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(AppIcons.Alerts, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Live Weather & Disaster Advisories", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                    Text("IMD rain, flood warnings, avalanche bulletins", fontSize = 11.sp, color = TextSecondary)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }

                            HorizontalDivider(color = DarkDivider, thickness = 0.5.dp)

                            // Offline Survival & SMS Beacon Hub
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToDisasterSafety() }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x3388CDB2)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(AppIcons.DisasterSafety, contentDescription = null, tint = MintSecondary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Offline Survival & SMS Emergency Hub", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                    Text("Trusted SMS contacts & offline shelter maps", fontSize = 11.sp, color = TextSecondary)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }

                            HorizontalDivider(color = DarkDivider, thickness = 0.5.dp)

                            // Data Providers & System Diagnostics
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToProviders() }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x33303D3A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(AppIcons.ProviderStatus, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Data Providers & System Diagnostics", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                                    Text("Inspect real-time feeds, offline sync & cache", fontSize = 11.sp, color = TextSecondary)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
