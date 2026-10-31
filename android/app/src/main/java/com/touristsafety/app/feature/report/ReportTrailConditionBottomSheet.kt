package com.touristsafety.app.feature.report

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touristsafety.app.core.designsystem.ExpressiveShapes
import com.touristsafety.app.core.designsystem.ForestGreenPrimary
import com.touristsafety.app.core.designsystem.SageContainer
import com.touristsafety.app.core.repository.TrailReportRepository
import com.touristsafety.app.feature.trip.MapLocationPickerDialog

data class TrailCategoryOption(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val isCritical: Boolean = false
)

val TRAIL_CATEGORY_OPTIONS = listOf(
    TrailCategoryOption("landslide", "Landslide / Rockfall", Icons.Default.Terrain, isCritical = true),
    TrailCategoryOption("fallen_tree", "Fallen Tree / Obstacle", Icons.Default.NaturePeople),
    TrailCategoryOption("flash_flood", "River Flood / Crossing", Icons.Default.Water, isCritical = true),
    TrailCategoryOption("damaged_bridge", "Damaged Trail / Bridge", Icons.Default.Construction),
    TrailCategoryOption("blocked_path", "Blocked Path / Snow", Icons.Default.Block),
    TrailCategoryOption("wildfire_smoke", "Fire / Smoke Hazard", Icons.Default.LocalFireDepartment, isCritical = true),
    TrailCategoryOption("wildlife", "Wildlife Encounter", Icons.Default.Pets),
    TrailCategoryOption("dangerous_weather", "Severe Fog / Blizzard", Icons.Default.Thunderstorm),
    TrailCategoryOption("trail_emergency", "Injured Trekker Emergency", Icons.Default.Emergency, isCritical = true),
    TrailCategoryOption("other", "Other Hazard Condition", Icons.Default.ReportProblem)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportTrailConditionBottomSheet(
    initialLat: Double?,
    initialLon: Double?,
    initialAccuracyM: Double? = 10.0,
    routeId: String? = null,
    trailReportRepository: TrailReportRepository? = null,
    onDismiss: () -> Unit,
    onReportSubmitted: () -> Unit,
    viewModel: ReportTrailConditionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialLat, initialLon, routeId) {
        viewModel.initialize(initialLat, initialLon, initialAccuracyM, routeId)
    }

    ReportTrailConditionContent(
        state = state,
        onDismiss = onDismiss,
        onSelectCategory = { viewModel.selectCategory(it) },
        onSetLocation = { lat, lon -> viewModel.setLocation(lat, lon) },
        onSetSeverity = { viewModel.setSeverity(it) },
        onSetPassability = { viewModel.setPassability(it) },
        onSetDescription = { viewModel.setDescription(it) },
        onSetPhotoUri = { viewModel.setPhotoUri(it) },
        onCreateCameraUri = { viewModel.createCameraUri() },
        onCameraPhotoCaptured = { viewModel.onCameraPhotoCaptured(it) },
        onNextStep = { viewModel.nextStep() },
        onPrevStep = { viewModel.prevStep() },
        onSubmit = { viewModel.submitReport(onReportSubmitted) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportTrailConditionContent(
    state: ReportTrailConditionUiState,
    onDismiss: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSetLocation: (Double, Double) -> Unit,
    onSetSeverity: (String) -> Unit,
    onSetPassability: (String) -> Unit,
    onSetDescription: (String) -> Unit,
    onSetPhotoUri: (Uri?) -> Unit,
    onCreateCameraUri: () -> Uri?,
    onCameraPhotoCaptured: (Boolean) -> Unit,
    onNextStep: () -> Unit,
    onPrevStep: () -> Unit,
    onSubmit: () -> Unit
) {
    var showLocationPicker by remember { mutableStateOf(false) }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onSetPhotoUri(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        onCameraPhotoCaptured(success)
    }

    if (showLocationPicker) {
        MapLocationPickerDialog(
            title = "Confirm Obstacle Location on Map",
            initialLat = state.latitude,
            initialLon = state.longitude,
            onConfirmLocation = { lat, lon, _ ->
                onSetLocation(lat, lon)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ExpressiveShapes.largeIncreased,
        containerColor = MaterialTheme.colorScheme.surface
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = ExpressiveShapes.pill,
                        color = ForestGreenPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ReportProblem,
                                contentDescription = null,
                                tint = ForestGreenPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Report Trail Condition",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Step ${state.currentStep.ordinal + 1} of 5 • Community Alert",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Step Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ReportWizardStep.entries.forEachIndexed { index, step ->
                    val isCompleted = state.currentStep.ordinal >= index
                    val isCurrent = state.currentStep == step
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(ExpressiveShapes.pill)
                            .background(
                                when {
                                    isCurrent -> ForestGreenPrimary
                                    isCompleted -> ForestGreenPrimary.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    )
                }
            }

            // Error Banner
            if (state.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = ExpressiveShapes.mediumIncreased,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        state.errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Success State
            if (state.submitSuccess) {
                Surface(
                    color = if (state.isOfflineQueued) Color(0xFFFFF3E0) else Color(0xFFE8F5E9),
                    shape = ExpressiveShapes.largeIncreased,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            if (state.isOfflineQueued) Icons.Default.CloudQueue else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (state.isOfflineQueued) Color(0xFFE65100) else Color(0xFF2E7D32),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            if (state.isOfflineQueued) "Saved to Offline Sync Queue" else "Report Published Live!",
                            fontWeight = FontWeight.Bold,
                            color = if (state.isOfflineQueued) Color(0xFFE65100) else Color(0xFF1B5E20),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (state.isOfflineQueued)
                                "Your trail report is safely stored in local database. It will automatically upload to the backend when cellular signal is restored."
                            else
                                "Your hazard observation has been shared with other trekkers in this mountain corridor. Thank you for keeping trails safe!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                            shape = ExpressiveShapes.pill
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Step Content
                when (state.currentStep) {
                    ReportWizardStep.CATEGORY -> {
                        Text(
                            "What trail obstacle or hazard did you observe?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TRAIL_CATEGORY_OPTIONS.forEach { opt ->
                                val isSelected = state.category == opt.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectCategory(opt.id) },
                                    label = { Text(opt.label, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            opt.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (opt.isCritical) Color(0xFFD32F2F) else ForestGreenPrimary
                                        )
                                    },
                                    shape = ExpressiveShapes.pill,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SageContainer,
                                        selectedLabelColor = ForestGreenPrimary
                                    )
                                )
                            }
                        }
                    }

                    ReportWizardStep.LOCATION -> {
                        Text(
                            "Confirm Obstacle Coordinates",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = ExpressiveShapes.largeIncreased
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Current Coordinates", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            "${String.format(java.util.Locale.US, "%.5f", state.latitude)}, ${String.format(java.util.Locale.US, "%.5f", state.longitude)}",
                                            fontSize = 12.sp,
                                            color = ForestGreenPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { showLocationPicker = true },
                                        shape = ExpressiveShapes.pill
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Adjust on Map", fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Location Source: ${state.locationSource.uppercase()} (Accuracy: ±${state.accuracyMeters.toInt()}m)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    ReportWizardStep.EVIDENCE -> {
                        Text(
                            "Attach Photo Evidence (Optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = ExpressiveShapes.largeIncreased
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (state.photoUri != null) {
                                    val context = LocalContext.current
                                    val bitmap = remember(state.photoUri) {
                                        try {
                                            context.contentResolver.openInputStream(state.photoUri)?.use { stream ->
                                                android.graphics.BitmapFactory.decodeStream(stream)
                                            }?.asImageBitmap()
                                        } catch (t: Throwable) {
                                            null
                                        }
                                    }
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap,
                                            contentDescription = "Report Photo",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(ExpressiveShapes.largeIncreased),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp),
                                            shape = ExpressiveShapes.largeIncreased,
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("Photo selected: ${state.photoUri.lastPathSegment}", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onSetPhotoUri(null) },
                                            shape = ExpressiveShapes.pill
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Remove")
                                        }
                                        Button(
                                            onClick = {
                                                val uri = onCreateCameraUri()
                                                if (uri != null) cameraLauncher.launch(uri)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                                            shape = ExpressiveShapes.pill
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retake")
                                        }
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.AddAPhoto,
                                        contentDescription = null,
                                        tint = ForestGreenPrimary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Visual evidence helps rescuers and other hikers verify severity",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                val uri = onCreateCameraUri()
                                                if (uri != null) cameraLauncher.launch(uri)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                                            shape = ExpressiveShapes.pill
                                        ) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open Camera", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { galleryPickerLauncher.launch("image/*") },
                                            shape = ExpressiveShapes.pill
                                        ) {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Pick Gallery", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ReportWizardStep.DETAILS -> {
                        Text(
                            "Severity & Trail Passability",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        // Passability
                        Text("Can other trekkers get past?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple("PASSABLE_ON_FOOT", "Passable", Color(0xFF2E7D32)),
                                Triple("DIFFICULT_REQUIRES_CAUTION", "Difficult", Color(0xFFF57C00)),
                                Triple("IMPASSABLE", "Impassable", Color(0xFFD32F2F))
                            ).forEach { (id, label, _) ->
                                val isSelected = state.passability == id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSetPassability(id) },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                    shape = ExpressiveShapes.pill,
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SageContainer,
                                        selectedLabelColor = ForestGreenPrimary
                                    )
                                )
                            }
                        }

                        // Severity
                        Text("Hazard Severity", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { sev ->
                                val isSelected = state.severity == sev
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSetSeverity(sev) },
                                    label = { Text(sev, fontSize = 11.sp) },
                                    shape = ExpressiveShapes.pill,
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SageContainer,
                                        selectedLabelColor = ForestGreenPrimary
                                    )
                                )
                            }
                        }

                        // Description
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = onSetDescription,
                            label = { Text("Details & guidance for other hikers") },
                            placeholder = { Text("e.g. Scramble over rocks on the left side to bypass the mudslide.") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            shape = ExpressiveShapes.mediumIncreased
                        )
                    }

                    ReportWizardStep.REVIEW -> {
                        Text(
                            "Review & Submit",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = ExpressiveShapes.largeIncreased
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val categoryLabel = TRAIL_CATEGORY_OPTIONS.firstOrNull { it.id == state.category }?.label ?: state.category
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Hazard Type:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(categoryLabel, color = ForestGreenPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Coordinates:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        "${String.format(java.util.Locale.US, "%.4f", state.latitude)}, ${String.format(java.util.Locale.US, "%.4f", state.longitude)}",
                                        fontSize = 12.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Passability:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(state.passability.replace('_', ' '), fontSize = 12.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Severity:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(state.severity, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                if (state.photoUri != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Photo:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Attached ✓", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (state.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Description:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(state.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Wizard Navigation Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.currentStep != ReportWizardStep.CATEGORY) {
                        OutlinedButton(
                            onClick = onPrevStep,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = ExpressiveShapes.pill
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = ExpressiveShapes.pill
                        ) {
                            Text("Cancel")
                        }
                    }

                    if (state.currentStep != ReportWizardStep.REVIEW) {
                        Button(
                            onClick = onNextStep,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                            shape = ExpressiveShapes.pill
                        ) {
                            Text("Next", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = onSubmit,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                            shape = ExpressiveShapes.pill,
                            enabled = !state.isSubmitting
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Publish Report", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
