package com.touristsafety.app.feature.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.touristsafety.app.core.model.MapPoint
import com.touristsafety.app.core.model.PointCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    viewModel: EmergencyViewModel,
    onNavigateToDisasterSafety: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency & Assistance") }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. National Emergency 112 Main Action Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:112")
                            }
                            context.startActivity(intent)
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PhoneInTalk,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Dial National Emergency (112)",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "India ERSS: Police, Fire & Medical 24x7",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // 1b. Uttarakhand Verified Emergency Directory
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Uttarakhand Emergency Directory",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Verified state disaster response helplines (Government of Uttarakhand)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val officialContacts = listOf(
                            Triple("SDRF Mountain Rescue", "1077", "State Disaster Response Force 24x7"),
                            Triple("State Disaster Op Centre (SEOC)", "1070", "USDMA Emergency Operations"),
                            Triple("Forest Fire Helpline (Van Agni)", "18001804141", "Forest Department MoEFCC"),
                            Triple("Trekker & Pilgrim Helpline", "1364", "Uttarakhand Tourism & Char Dham"),
                            Triple("Ambulance & Medical", "108", "State Health Emergency Service")
                        )

                        officialContacts.forEach { (title, phone, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:$phone")
                                        }
                                        context.startActivity(intent)
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(desc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:$phone")
                                        }
                                        context.startActivity(intent)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(phone, fontWeight = FontWeight.Bold)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // 1c. Handset GPS Location Card for Emergency Dispatch
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Your Current GPS Location",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (state.userLatitude != null && state.userLongitude != null) {
                            Text(
                                "Latitude: " + String.format("%.5f", state.userLatitude) + "° N\nLongitude: " + String.format("%.5f", state.userLongitude) + "° E",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Read these coordinates aloud when contacting 112 / 1077 / 1070 dispatchers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        } else {
                            Text(
                                "Acquiring satellite GPS fix... Ensure device has clear view of sky.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // 1a. Search & Rescue (SAR) SOS Field Beacon
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isSosTriggered) Color(0xFFB71C1C) else MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Emergency,
                                contentDescription = null,
                                tint = if (state.isSosTriggered) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (state.isSosTriggered) "SOS BEACON ACTIVE" else "Transmit SAR Emergency Beacon",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.isSosTriggered) Color.White else MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Broadcasts your live GPS coordinates, altitude, and SAR distress package to emergency responders.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.isSosTriggered) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.triggerFieldSos() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isSosTriggered) Color.White else MaterialTheme.colorScheme.error,
                                contentColor = if (state.isSosTriggered) Color(0xFFB71C1C) else MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CrisisAlert, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (state.isSosTriggered) "RE-TRANSMIT BEACON" else "SEND DISTRESS BEACON",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // SOS Alert Message Feedback
            state.sosMessage?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.dismissSosMessage() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // 1b. Disaster Safety & Offline Geofence Shortcut
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDisasterSafety() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Disaster Alert & Offline Safety Center",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "SMS registration, offline route hazard geofencing & siren test.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // 2. Privacy & On-Device GPS Notice
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Privacy Guarantee: Your GPS location is computed strictly on-device and is never tracked or stored on backend servers.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Category Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All") }
                    )
                    PointCategory.values().forEach { cat ->
                        FilterChip(
                            selected = state.selectedCategory == cat,
                            onClick = { viewModel.selectCategory(cat) },
                            label = { Text(cat.name.replace('_', ' ')) }
                        )
                    }
                }
            }

            // 4. Assistance Points Directory
            items(state.filteredPoints, key = { it.id }) { pt ->
                AssistancePointCard(
                    point = pt,
                    onCall = { phone ->
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$phone")
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun AssistancePointCard(point: MapPoint, onCall: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = point.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = point.category.name.replace('_', ' '),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            point.description?.let { desc ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(desc, style = MaterialTheme.typography.bodyMedium)
            }

            point.address?.let { addr ->
                Spacer(modifier = Modifier.height(4.dp))
                Text("Address: $addr", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Provenance: ${point.provenance}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )

            point.phone?.let { phoneNum ->
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onCall(phoneNum) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Call $phoneNum")
                }
            }
        }
    }
}

