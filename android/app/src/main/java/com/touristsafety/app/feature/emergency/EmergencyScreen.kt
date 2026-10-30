package com.touristsafety.app.feature.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.designsystem.components.*
import com.touristsafety.app.core.model.MapPoint
import com.touristsafety.app.core.model.PointCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    viewModel: EmergencyViewModel,
    onNavigateToDisasterSafety: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assistance & Emergency", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. National Emergency 112 Hero Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:112")
                            }
                            context.startActivity(intent)
                        },
                    color = EmergencyRed,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            AppIcons.DialEmergency,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Dial National Emergency (112)",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "India ERSS: Police, Fire & Medical 24x7",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:112")
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("CALL", color = EmergencyRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 2. Handset GPS Location for Emergency Dispatch
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SageContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.MyLocation, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Your Current GPS Coordinates",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (state.userLatitude != null && state.userLongitude != null) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.5f° N,  %.5f° E", state.userLatitude, state.userLongitude),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryLight
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Read these exact coordinates to 112 / SDRF dispatchers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryLight
                            )
                        } else {
                            Text(
                                "Acquiring satellite GPS fix... Clear sky view required.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryLight
                            )
                        }
                    }
                }
            }

            // 3. Search & Rescue (SAR) Distress Field Beacon
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (state.isSosTriggered) EmergencyRed else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.SosBeacon,
                                contentDescription = null,
                                tint = if (state.isSosTriggered) Color.White else EmergencyRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (state.isSosTriggered) "SOS BEACON TRANSMITTED" else "SAR Distress Beacon",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (state.isSosTriggered) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Broadcasts emergency GPS coordinates & telemetry",
                                    fontSize = 12.sp,
                                    color = if (state.isSosTriggered) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        AppPrimaryButton(
                            text = if (state.isSosTriggered) "RE-TRANSMIT BEACON" else "TRANSMIT DISTRESS BEACON",
                            containerColor = if (state.isSosTriggered) Color.White else EmergencyRed,
                            contentColor = if (state.isSosTriggered) EmergencyRed else Color.White,
                            onClick = { viewModel.triggerFieldSos() }
                        )
                    }
                }
            }

            // SOS Feedback message
            state.sosMessage?.let { msg ->
                item {
                    AppErrorBanner(
                        message = msg,
                        isCritical = false,
                        icon = AppIcons.Safe,
                        onDismiss = { viewModel.dismissSosMessage() }
                    )
                }
            }

            // 4. Uttarakhand Verified Emergency Helplines
            item {
                AppSectionHeader(
                    title = "Uttarakhand Rescue Directory",
                    subtitle = "Verified Government of Uttarakhand helplines"
                )
            }

            val officialContacts = listOf(
                Triple("SDRF Mountain Rescue", "1077", "State Disaster Response Force 24x7"),
                Triple("State Disaster Op Centre (SEOC)", "1070", "USDMA Emergency Operations"),
                Triple("Forest Fire Helpline (Van Agni)", "18001804141", "Forest Department MoEFCC"),
                Triple("Trekker & Pilgrim Helpline", "1364", "Uttarakhand Tourism & Char Dham"),
                Triple("Ambulance & Medical Emergency", "108", "State Health Emergency Service")
            )

            items(officialContacts) { (title, phone, desc) ->
                AppListItem(
                    title = title,
                    subtitle = desc,
                    leadingIcon = AppIcons.Phone,
                    leadingIconTint = ForestGreenPrimary,
                    leadingIconBg = SageContainer,
                    trailingContent = {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:$phone")
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SageContainer),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(phone, color = ForestGreenPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    },
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$phone")
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 5. Offline Safety Center & SMS Geofence Link
            item {
                Spacer(modifier = Modifier.height(4.dp))
                AppListItem(
                    title = "Disaster Safety & Offline SMS Registration",
                    subtitle = "Manage emergency SMS contacts and offline corridor geofences",
                    leadingIcon = AppIcons.DisasterSafety,
                    leadingIconTint = ForestGreenPrimary,
                    leadingIconBg = SageContainer,
                    showChevron = true,
                    showDivider = true,
                    onClick = onNavigateToDisasterSafety
                )
            }

            // 6. Nearby Facilities Header & Category Filter
            item {
                AppSectionHeader(
                    title = "Nearby Assistance Facilities",
                    subtitle = "Verified hospitals, police stations, shelters, and medical posts along trail corridors"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SageContainer,
                            selectedLabelColor = ForestGreenPrimary
                        )
                    )
                    PointCategory.values().forEach { cat ->
                        FilterChip(
                            selected = state.selectedCategory == cat,
                            onClick = { viewModel.selectCategory(cat) },
                            label = { Text(cat.name.replace('_', ' ')) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SageContainer,
                                selectedLabelColor = ForestGreenPrimary
                            )
                        )
                    }
                }
            }

            // 7. Facilities List
            if (state.filteredPoints.isEmpty()) {
                item {
                    AppEmptyState(
                        title = "No Facilities in Cached Corridor",
                        description = "Expand your corridor search buffer in Plan Route or download an offline trek pack.",
                        icon = AppIcons.Hospital
                    )
                }
            } else {
                items(state.filteredPoints, key = { it.id }) { pt ->
                    val facilityIcon = when (pt.category) {
                        PointCategory.POLICE -> AppIcons.Police
                        PointCategory.MEDICAL, PointCategory.CLINIC -> AppIcons.Hospital
                        PointCategory.SHELTER -> AppIcons.Shelter
                        else -> AppIcons.Pharmacy
                    }
                    AppListItem(
                        title = pt.name,
                        subtitle = "${pt.category.name.replace('_', ' ')}${if (pt.address != null) " • ${pt.address}" else ""}",
                        caption = "Source: ${pt.provenance}",
                        leadingIcon = facilityIcon,
                        leadingIconTint = ForestGreenPrimary,
                        leadingIconBg = SageContainer,
                        trailingContent = pt.phone?.let { phoneNum ->
                            {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:$phoneNum")
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SageContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(AppIcons.Phone, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Call", color = ForestGreenPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
