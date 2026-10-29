package com.touristsafety.app.feature.trip

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.designsystem.components.*
import com.touristsafety.app.core.map.MapConfig
import com.touristsafety.app.core.map.MapLibreLayerManager
import com.touristsafety.app.core.model.MapFeatureCategory
import com.touristsafety.app.core.model.UnifiedMapFeature
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTripScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit = onNavigateBack,
    onNavigateToCompletedTrekDetail: (String) -> Unit = {},
    viewModel: ActiveTripViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var maplibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isMapStyleReady by remember { mutableStateOf(false) }

    var showEndTrekDialog by remember { mutableStateOf(false) }
    var showTrekSummaryDialog by remember { mutableStateOf(false) }
    var showLayersSheet by remember { mutableStateOf(false) }

    // MapView instance lifecycle
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                maplibreMapInstance = map
                try {
                    // Restrict camera to Uttarakhand territory
                    val bounds = LatLngBounds.Builder()
                        .include(LatLng(MapConfig.UTTARAKHAND_MIN_LAT, MapConfig.UTTARAKHAND_MIN_LON))
                        .include(LatLng(MapConfig.UTTARAKHAND_MAX_LAT, MapConfig.UTTARAKHAND_MAX_LON))
                        .build()
                    map.setLatLngBoundsForCameraTarget(bounds)
                    map.setMinZoomPreference(MapConfig.MIN_ZOOM_LEVEL)
                    map.setMaxZoomPreference(MapConfig.MAX_ZOOM_LEVEL)

                    map.setStyle(Style.Builder().fromUri(MapConfig.DEFAULT_STYLE_URL)) { style ->
                        MapLibreLayerManager.setupSourcesAndLayers(style, context)
                        isMapStyleReady = true
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("ActiveTripScreen", "Error initializing map style: ${t.message}", t)
                }

                // Handle map feature tap inspection
                map.addOnMapClickListener { latLng ->
                    try {
                        val screenPoint = map.projection.toScreenLocation(latLng)
                        val renderedFeatures = map.queryRenderedFeatures(
                            screenPoint,
                            MapLibreLayerManager.LAYER_ROUTE_HAZARD_SEQUENCE_SYMBOL,
                            MapLibreLayerManager.LAYER_ACTIVE_FIRES_SYMBOL,
                            MapLibreLayerManager.LAYER_HOSPITALS_SYMBOL,
                            MapLibreLayerManager.LAYER_CLINICS_SYMBOL,
                            MapLibreLayerManager.LAYER_POLICE_SYMBOL,
                            MapLibreLayerManager.LAYER_SAFE_HAVENS_SYMBOL
                        )
                        if (renderedFeatures.isNotEmpty()) {
                            val top = renderedFeatures.first()
                            val title = top.getStringProperty("title") ?: top.getStringProperty("name") ?: "Trail Feature"
                            val catStr = top.getStringProperty("category") ?: "HAZARD_ZONE"
                            val category = try {
                                MapFeatureCategory.valueOf(catStr)
                            } catch (e: Exception) {
                                MapFeatureCategory.HAZARD_ZONE
                            }
                            val feature = UnifiedMapFeature(
                                id = top.getStringProperty("id") ?: top.id() ?: "feature",
                                title = title,
                                subtitle = top.getStringProperty("subtitle") ?: top.getStringProperty("description"),
                                category = category,
                                latitude = latLng.latitude,
                                longitude = latLng.longitude
                            )
                            viewModel.selectFeatureDetail(feature)
                            return@addOnMapClickListener true
                        }
                    } catch (t: Throwable) {
                        android.util.Log.d("ActiveTripScreen", "Map tap query: ${t.message}")
                    }
                    false
                }
            }
        }
    }

    // Reactively update traveled track, user puck, and layer visibility
    LaunchedEffect(
        state.traveledTrackCoordinates,
        state.currentLatitude,
        state.currentLongitude,
        state.gpsAccuracyMeters,
        state.layerVisibility,
        isMapStyleReady
    ) {
        if (isMapStyleReady) {
            maplibreMapInstance?.let { map ->
                val style = map.style
                if (style?.isFullyLoaded == true) {
                    // 1. Update Traveled Track Line
                    MapLibreLayerManager.updateTraveledTrack(style, state.traveledTrackCoordinates)

                    // 2. Update Current Trekker Puck & Accuracy Halo
                    val lat = state.currentLatitude
                    val lon = state.currentLongitude
                    val acc = state.gpsAccuracyMeters ?: 15.0f
                    if (lat != null && lon != null) {
                        MapLibreLayerManager.updateTrekCurrentPosition(style, lat, lon, acc)
                    }

                    // 3. Update Category Layer Visibilities
                    state.layerVisibility.forEach { (category, visible) ->
                        MapLibreLayerManager.setLayerCategoryVisibility(style, category, visible)
                    }
                }
            }
        }
    }

    // Recenter camera when first location arrives or on request
    LaunchedEffect(state.currentLatitude, state.currentLongitude) {
        val lat = state.currentLatitude
        val lon = state.currentLongitude
        if (lat != null && lon != null && isMapStyleReady) {
            maplibreMapInstance?.let { map ->
                if (map.style?.isFullyLoaded == true) {
                    val target = LatLng(lat, lon)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 14.5), 1000)
                }
            }
        }
    }

    // Confirmation dialog for ending the trek
    if (showEndTrekDialog) {
        AlertDialog(
            onDismissRequest = { showEndTrekDialog = false },
            icon = { Icon(AppIcons.StopTrek, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(32.dp)) },
            title = { Text("End Active Trek?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will finalize your monitored trek session, stop background GPS tracking, and archive your route stats to Room storage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndTrekDialog = false
                        viewModel.endTrip(context)
                        showTrekSummaryDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("End & Archive Trek", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndTrekDialog = false }) {
                    Text("Continue Trekking")
                }
            }
        )
    }

    // Post-Trek Summary Dialog
    if (showTrekSummaryDialog) {
        AlertDialog(
            onDismissRequest = {
                showTrekSummaryDialog = false
                onNavigateBack()
            },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(36.dp)) },
            title = { Text("Trek Completed!", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = state.destinationName.ifBlank { "Expedition Route" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your trek tracking session is successfully finished and saved to your local database.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format(java.util.Locale.US, "%.2f km", state.traveledDistanceKm), fontWeight = FontWeight.Bold, color = ForestGreenPrimary, fontSize = 16.sp)
                            Text("Distance", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.elapsedDurationFormatted, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            Text("Time", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${state.traveledTrackCoordinates.size}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                            Text("GPS Points", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTrekSummaryDialog = false
                        if (state.tripId.isNotBlank()) {
                            onNavigateToCompletedTrekDetail(state.tripId)
                        } else {
                            onNavigateToHistory()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View in Trek History", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTrekSummaryDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Return Home")
                }
            }
        )
    }

    // Layers Visibility Bottom Sheet
    if (showLayersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLayersSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "Active Map Layers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                val categories = listOf(
                    Triple("hazards", "Severe Hazard Zones", AppIcons.HazardGeneric),
                    Triple("fires", "Active Wildfire Hotspots", AppIcons.Wildfire),
                    Triple("hospitals", "Hospitals & Medical Clinics", AppIcons.Hospital),
                    Triple("police", "Police & Security Posts", AppIcons.Police),
                    Triple("shelters", "Emergency Safe Havens", AppIcons.Shelter)
                )

                categories.forEach { (catKey, label, icon) ->
                    val isChecked = state.layerVisibility[catKey] ?: true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleLayer(catKey) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { viewModel.toggleLayer(catKey) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.destinationName.isNotBlank()) "En Route to ${state.destinationName}" else "Active Trek Tracking",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val accStr = state.gpsAccuracyMeters?.let { "±${it.toInt()}m" } ?: "Searching"
                        val gpsLabel = when (state.gpsStatus) {
                            "GPS_ACCURATE" -> "GPS Lock ($accStr)"
                            "GPS_MEDIUM" -> "GPS Fair ($accStr)"
                            else -> "Acquiring GPS ($accStr)"
                        }
                        Text(
                            text = if (state.isPaused) "PAUSED • $gpsLabel" else "MONITORED • $gpsLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.isPaused) WarningAmber else ForestGreenPrimary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Sound Alert Toggle
                    IconButton(onClick = { viewModel.toggleSoundAlerts() }) {
                        Icon(
                            imageVector = if (state.soundAlertsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Toggle Audio Alert",
                            tint = if (state.soundAlertsEnabled) ForestGreenPrimary else Color.Gray
                        )
                    }
                    // Vibration Toggle
                    IconButton(onClick = { viewModel.toggleVibrationAlerts() }) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Toggle Vibration Alert",
                            tint = if (state.vibrationAlertsEnabled) ForestGreenPrimary else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Embedded MapLibre Map
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Live Metrics HUD Overlay (Top-Center)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppMetricDisplay(
                        value = String.format(java.util.Locale.US, "%.2f km", state.traveledDistanceKm),
                        label = "Distance",
                        valueColor = ForestGreenPrimary
                    )
                    VerticalDivider(modifier = Modifier.height(32.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    AppMetricDisplay(
                        value = state.elapsedDurationFormatted,
                        label = "Elapsed",
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    VerticalDivider(modifier = Modifier.height(32.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    AppMetricDisplay(
                        value = "${state.progressPct.toInt()}%",
                        label = "Route Done",
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 3. Floating Loud Hazard Alert Banner (Appears when hazard engine triggers)
            AnimatedVisibility(
                visible = state.activeAlertBanner != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp)
            ) {
                state.activeAlertBanner?.let { alert ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
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
                                    text = "${alert.distanceKm} km away • ${alert.instructions}",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.dismissActiveAlert() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("MUTE", color = EmergencyRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 4. Floating Action Controls (Right-Side FABs)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Recenter Camera FAB
                FloatingActionButton(
                    onClick = {
                        val lat = state.currentLatitude
                        val lon = state.currentLongitude
                        if (lat != null && lon != null) {
                            maplibreMapInstance?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15.0),
                                800
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = ForestGreenPrimary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(AppIcons.MyLocation, contentDescription = "Recenter Map")
                }

                // Layers Toggle FAB
                FloatingActionButton(
                    onClick = { showLayersSheet = true },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = ForestGreenPrimary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(AppIcons.Layers, contentDescription = "Toggle Layers")
                }
            }

            // 5. Bottom Trek Controls Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
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
                        if (state.isPaused) {
                            Button(
                                onClick = { viewModel.resumeTrek(context) },
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
                                onClick = { viewModel.pauseTrek(context) },
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
                            onClick = { showEndTrekDialog = true },
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
                        // Check-in Button
                        FilledTonalButton(
                            onClick = { viewModel.submitCheckIn() },
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
                            onClick = { viewModel.triggerEmergencySos() },
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

            // 6. Selected Feature Detail Bottom Sheet
            state.selectedFeatureDetail?.let { feature ->
                ModalBottomSheet(
                    onDismissRequest = { viewModel.selectFeatureDetail(null) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (feature.category) {
                                    MapFeatureCategory.HOSPITAL -> AppIcons.Hospital
                                    MapFeatureCategory.POLICE_STATION -> AppIcons.Police
                                    MapFeatureCategory.SHELTER -> AppIcons.Shelter
                                    MapFeatureCategory.ACTIVE_FIRE_DETECTION -> AppIcons.Wildfire
                                    else -> AppIcons.HazardGeneric
                                },
                                contentDescription = null,
                                tint = ForestGreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = feature.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!feature.subtitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = feature.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Category: ${feature.category.name.replace('_', ' ')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        AppSecondaryButton(
                            text = "Dismiss",
                            onClick = { viewModel.selectFeatureDetail(null) }
                        )
                    }
                }
            }
        }
    }
}
