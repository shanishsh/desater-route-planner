package com.touristsafety.app.feature.trip

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import com.touristsafety.app.core.model.RouteOrderedHazardItem
import com.touristsafety.app.core.model.UnifiedMapFeature
import com.touristsafety.app.core.network.dto.NearbyFacilityDto
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
    var isSafetyAssistantExpanded by remember { mutableStateOf(false) }
    var assistantInputText by remember { mutableStateOf("") }
    var isFollowModeEnabled by remember { mutableStateOf(true) }

    // MapView instance lifecycle
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                maplibreMapInstance = map
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        isFollowModeEnabled = false
                    }
                }
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

    // 1. Update Current Trekker Puck & Accuracy Halo
    LaunchedEffect(state.currentLatitude, state.currentLongitude, state.gpsAccuracyMeters, isMapStyleReady) {
        if (isMapStyleReady) {
            maplibreMapInstance?.let { map ->
                val style = map.style
                if (style?.isFullyLoaded == true) {
                    val lat = state.currentLatitude
                    val lon = state.currentLongitude
                    val acc = state.gpsAccuracyMeters ?: 15.0f
                    if (lat != null && lon != null) {
                        MapLibreLayerManager.updateTrekCurrentPosition(style, lat, lon, acc)
                    }
                }
            }
        }
    }

    // 2. Incrementally Update Traveled Track Line (only on new coordinate appended)
    LaunchedEffect(state.traveledTrackCoordinates.size, isMapStyleReady) {
        if (isMapStyleReady) {
            maplibreMapInstance?.let { map ->
                val style = map.style
                if (style?.isFullyLoaded == true) {
                    MapLibreLayerManager.updateTraveledTrack(style, state.traveledTrackCoordinates)
                }
            }
        }
    }

    // 3. Update Route Hazard Sequence (only when hazard order or identity changes)
    val upcomingHazardKeys = remember(state.upcomingHazards) {
        state.upcomingHazards.map { "${it.feature.id}_${it.sequenceNumber}_${it.distanceAheadMeters}" }
    }
    LaunchedEffect(upcomingHazardKeys, isMapStyleReady) {
        if (isMapStyleReady) {
            maplibreMapInstance?.let { map ->
                val style = map.style
                if (style?.isFullyLoaded == true) {
                    MapLibreLayerManager.updateRouteHazardSequence(style, state.upcomingHazards)
                }
            }
        }
    }

    // 4. Update Category Layer Visibilities
    LaunchedEffect(state.layerVisibility, isMapStyleReady) {
        if (isMapStyleReady) {
            maplibreMapInstance?.let { map ->
                val style = map.style
                if (style?.isFullyLoaded == true) {
                    state.layerVisibility.forEach { (category, visible) ->
                        MapLibreLayerManager.setLayerCategoryVisibility(style, category, visible)
                    }
                }
            }
        }
    }

    // 5. Camera follow mode: smoothly center on user position without hijacking user zoom or pan
    LaunchedEffect(state.currentLatitude, state.currentLongitude, isFollowModeEnabled, isMapStyleReady) {
        val lat = state.currentLatitude
        val lon = state.currentLongitude
        if (isFollowModeEnabled && lat != null && lon != null && isMapStyleReady) {
            maplibreMapInstance?.let { map ->
                if (map.style?.isFullyLoaded == true) {
                    val target = LatLng(lat, lon)
                    map.animateCamera(CameraUpdateFactory.newLatLng(target), 800)
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
                    .padding(top = 8.dp, start = 14.dp, end = 14.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Row 1: Distance & Progress Disambiguation
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
                            value = String.format(java.util.Locale.US, "%.1f km", state.remainingDistanceKm),
                            label = "Remaining",
                            valueColor = MaterialTheme.colorScheme.onSurface
                        )
                        VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        AppMetricDisplay(
                            value = "${state.progressPct.toInt()}%",
                            label = "Progress",
                            valueColor = ForestGreenPrimary
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Row 2: Live Conditions (Weather & Network)
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

                        // Network Connectivity Status
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
                            // Voice Guidance Toggle
                            IconButton(
                                onClick = { viewModel.toggleVoiceGuidance(!state.isVoiceGuidanceEnabled) },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.isVoiceGuidanceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "Toggle Voice Guidance",
                                    tint = if (state.isVoiceGuidanceEnabled) ForestGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // Deterministic Safety Event Banner (if triggered)
                    if (state.activeSafetyEvent != null) {
                        val ev = state.activeSafetyEvent!!
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
                                    onClick = { viewModel.dismissActiveAlert() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // Row 3: Off-Route Warning (if active)
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
                        isFollowModeEnabled = true
                        val lat = state.currentLatitude
                        val lon = state.currentLongitude
                        if (lat != null && lon != null) {
                            maplibreMapInstance?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15.0),
                                800
                            )
                        }
                    },
                    containerColor = if (isFollowModeEnabled) ForestGreenPrimary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isFollowModeEnabled) Color.White else ForestGreenPrimary,
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

            // 5. Bottom Interactive Area: Safety Assistant + Trek Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Live Route Safety Assistant Card
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
                                .clickable { isSafetyAssistantExpanded = !isSafetyAssistantExpanded },
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
                                onClick = { isSafetyAssistantExpanded = !isSafetyAssistantExpanded },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSafetyAssistantExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Toggle Safety Assistant"
                                )
                            }
                        }

                        // Collapsed Summary line
                        if (!isSafetyAssistantExpanded) {
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
                        AnimatedVisibility(visible = isSafetyAssistantExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 350.dp)
                                    .padding(top = 8.dp)
                            ) {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // 0. AI Safety Assistant Guidance Card
                                    if (state.assistantGuidance != null) {
                                        val g = state.assistantGuidance!!
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
                                                            onClick = { viewModel.replayVoiceGuidance() },
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
                                        androidx.compose.foundation.lazy.LazyRow(
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
                                                    modifier = Modifier.clickable {
                                                        viewModel.askAssistant(chipText)
                                                    }
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
                                                modifier = Modifier.weight(1f).height(46.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = {
                                                    val query = assistantInputText
                                                    assistantInputText = ""
                                                    viewModel.askAssistant(query)
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

                                    // 1. Ahead on Route Section
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
                                                onClick = {
                                                    viewModel.selectFeatureDetail(item.feature)
                                                    maplibreMapInstance?.animateCamera(
                                                        CameraUpdateFactory.newLatLngZoom(
                                                            LatLng(item.feature.latitude, item.feature.longitude),
                                                            15.0
                                                        ),
                                                        600
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    // 2. Near Current Location Section
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
                                                onClick = {
                                                    viewModel.selectFeatureDetail(item.feature)
                                                }
                                            )
                                        }
                                        items(state.nearbyFacilities.size) { idx ->
                                            val facility = state.nearbyFacilities[idx]
                                            FacilityListItemRow(facility = facility)
                                        }
                                    }

                                    // 3. Passed Section
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

                // Controls Bar Surface
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

@Composable
private fun HazardListItemRow(
    item: RouteOrderedHazardItem,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
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
private fun FacilityListItemRow(facility: NearbyFacilityDto) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ForestGreenPrimary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
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
                    text = "${facility.category.replace('_', ' ')} • ${String.format(java.util.Locale.US, "%.1f km away", facility.distance_km)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
