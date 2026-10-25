package com.touristsafety.app.feature.report

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.network.dto.TrailReportCreateDto
import com.touristsafety.app.core.repository.TrailReportRepository
import com.touristsafety.app.feature.trip.MapLocationPickerDialog
import kotlinx.coroutines.launch
import java.util.UUID

data class TrailCategoryOption(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

val TRAIL_CATEGORY_OPTIONS = listOf(
    TrailCategoryOption("fallen_tree", "Fallen Tree / Log", Icons.Default.NaturePeople),
    TrailCategoryOption("landslide", "Landslide / Rockfall", Icons.Default.Terrain),
    TrailCategoryOption("flash_flood", "Flood / Water Crossing", Icons.Default.Water),
    TrailCategoryOption("damaged_bridge", "Damaged Bridge / Trail", Icons.Default.Construction),
    TrailCategoryOption("blocked_path", "Blocked Path / Road", Icons.Default.Block),
    TrailCategoryOption("wildfire_smoke", "Fire / Smoke", Icons.Default.LocalFireDepartment),
    TrailCategoryOption("wildlife", "Wildlife Encounter", Icons.Default.Pets),
    TrailCategoryOption("dangerous_weather", "Dangerous Weather", Icons.Default.Thunderstorm),
    TrailCategoryOption("trail_emergency", "Injured Person / Emergency", Icons.Default.Emergency),
    TrailCategoryOption("other", "Other Trail Condition", Icons.Default.ReportProblem),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportTrailConditionBottomSheet(
    initialLat: Double?,
    initialLon: Double?,
    initialAccuracyM: Double? = 10.0,
    routeId: String? = null,
    trailReportRepository: TrailReportRepository,
    onDismiss: () -> Unit,
    onReportSubmitted: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("fallen_tree") }
    var selectedSeverity by remember { mutableStateOf("MEDIUM") }
    var selectedPassability by remember { mutableStateOf("DIFFICULT_REQUIRES_CAUTION") }
    var descriptionText by remember { mutableStateOf("") }

    var reportLat by remember { mutableStateOf(initialLat ?: 30.3165) }
    var reportLon by remember { mutableStateOf(initialLon ?: 78.0322) }
    var reportAccuracyM by remember { mutableStateOf(initialAccuracyM ?: 15.0) }
    var locationSource by remember { mutableStateOf(if (initialLat != null) "gps" else "manual_map") }

    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showLocationPicker by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitSuccess by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedPhotoUri = uri
    }

    if (showLocationPicker) {
        MapLocationPickerDialog(
            title = "Choose Obstacle Location",
            initialLat = reportLat,
            initialLon = reportLon,
            onConfirmLocation = { lat, lon, _ ->
                reportLat = lat
                reportLon = lon
                locationSource = "manual_map"
                reportAccuracyM = 5.0
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Report,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Report a Trail Condition",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Community report to alert trekkers behind you.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (submitSuccess) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Trail condition reported successfully! Thank you for keeping trails safe.",
                            color = Color(0xFF1B5E20),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Location Bar
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Report Location",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            TextButton(onClick = { showLocationPicker = true }) {
                                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Adjust on Map", fontSize = 12.sp)
                            }
                        }
                        Text(
                            "Coordinates: ${String.format(java.util.Locale.US, "%.5f", reportLat)}, ${String.format(java.util.Locale.US, "%.5f", reportLon)} (Accuracy ±${reportAccuracyM.toInt()}m, Source: ${locationSource.uppercase()})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Category Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What did you observe?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TRAIL_CATEGORY_OPTIONS.forEach { opt ->
                            FilterChip(
                                selected = selectedCategory == opt.id,
                                onClick = { selectedCategory = opt.id },
                                label = { Text(opt.label, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(opt.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }

                // Passability Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Can other trekkers pass?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("PASSABLE_ON_FOOT", "Passable", Color(0xFF2E7D32)),
                            Triple("DIFFICULT_REQUIRES_CAUTION", "Difficult", Color(0xFFF57C00)),
                            Triple("IMPASSABLE", "Impassable", Color(0xFFD32F2F))
                        ).forEach { (id, label, color) ->
                            FilterChip(
                                selected = selectedPassability == id,
                                onClick = { selectedPassability = id },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Severity Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Severity Level", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { sev ->
                            FilterChip(
                                selected = selectedSeverity == sev,
                                onClick = { selectedSeverity = sev },
                                label = { Text(sev, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Add Photo Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Add a Photo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                if (selectedPhotoUri != null) "Photo selected: ${selectedPhotoUri?.lastPathSegment}"
                                else "Capture obstruction or attach photo",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (selectedPhotoUri != null) "Change" else "Attach", fontSize = 12.sp)
                        }
                    }
                }

                // Description
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Observation details / advice to other hikers") },
                    placeholder = { Text("e.g. Large tree across trail at kilometer 4.2; requires scrambling over steep slope on right side.") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )

                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            isSubmitting = true
                            errorMessage = null
                            coroutineScope.launch {
                                val reportDto = TrailReportCreateDto(
                                    category = selectedCategory,
                                    severity = selectedSeverity,
                                    passability = selectedPassability,
                                    latitude = reportLat,
                                    longitude = reportLon,
                                    location_accuracy_meters = reportAccuracyM,
                                    location_source = locationSource,
                                    description = descriptionText.ifBlank { null },
                                    photo_url = selectedPhotoUri?.toString(),
                                    route_id = routeId,
                                    idempotency_key = "report-${UUID.randomUUID().toString().take(16)}"
                                )
                                val res = trailReportRepository.submitReport(reportDto)
                                isSubmitting = false
                                res.fold(
                                    onSuccess = {
                                        submitSuccess = true
                                        onReportSubmitted()
                                    },
                                    onFailure = { err ->
                                        errorMessage = err.message ?: "Failed to submit report. Please check your network or try again."
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Submit Report", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
