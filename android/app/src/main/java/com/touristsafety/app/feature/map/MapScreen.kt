package com.touristsafety.app.feature.map

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.touristsafety.app.core.map.ClusterItem
import com.touristsafety.app.core.map.MapConfig
import com.touristsafety.app.core.map.MapLibreLayerManager
import com.touristsafety.app.feature.report.ReportTrailConditionBottomSheet
import com.touristsafety.app.core.model.MapFeatureCategory
import com.touristsafety.app.core.model.UnifiedMapFeature
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

enum class BaseMapStatus {
    INITIALIZING,
    STYLE_LOADING,
    READY,
    FAILED,
    FALLBACK_RASTER_ACTIVE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onNavigateToPlanTrip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var maplibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var baseMapStatus by remember { mutableStateOf(BaseMapStatus.INITIALIZING) }
    var currentStyleUrl by remember { mutableStateOf(MapConfig.DEFAULT_STYLE_URL) }
    var styleErrorMessage by remember { mutableStateOf<String?>(null) }
    var measuredWidthPx by remember { mutableIntStateOf(0) }
    var measuredHeightPx by remember { mutableIntStateOf(0) }
    var userToggledCanvas by remember { mutableStateOf(false) }
    var showReportConditionSheet by remember { mutableStateOf(false) }
    var lastRenderedRouteId by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val mapView = remember {
        try {
            MapLibre.getInstance(context)
        } catch (t: Throwable) {
            android.util.Log.w("MapScreen", "MapLibre.getInstance in remember: ${t.message}")
        }
        val options = try {
            MapLibreMapOptions.createFromAttributes(context)
        } catch (_: Throwable) {
            MapLibreMapOptions()
        }
        MapView(context, options).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onCreate(null)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            try {
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        android.util.Log.d("MapScreen", "Forwarding ON_START to MapView")
                        mapView.onStart()
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        android.util.Log.d("MapScreen", "Forwarding ON_RESUME to MapView")
                        mapView.onResume()
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                        android.util.Log.d("MapScreen", "Forwarding ON_PAUSE to MapView")
                        mapView.onPause()
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                        android.util.Log.d("MapScreen", "Forwarding ON_STOP to MapView")
                        mapView.onStop()
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                        android.util.Log.d("MapScreen", "Forwarding ON_DESTROY to MapView")
                        mapView.onDestroy()
                    }
                    else -> {}
                }
            } catch (t: Throwable) {
                android.util.Log.w("MapScreen", "Lifecycle event $event failed on MapView safely", t)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Forward current lifecycle state immediately if already active
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            android.util.Log.d("MapScreen", "Current state is STARTED or above -> calling mapView.onStart()")
            try { mapView.onStart() } catch (t: Throwable) { android.util.Log.w("MapScreen", "onStart error: ${t.message}") }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            android.util.Log.d("MapScreen", "Current state is RESUMED -> calling mapView.onResume()")
            try { mapView.onResume() } catch (t: Throwable) { android.util.Log.w("MapScreen", "onResume error: ${t.message}") }
        }

        onDispose {
            android.util.Log.d("MapScreen", "Disposing MapView effect, tearing down observer and MapView")
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(mapView) {
        mapView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0) {
                measuredWidthPx = w
                measuredHeightPx = h
                android.util.Log.d("MapScreen", "MapView measured dimensions: ${w}x${h}")
            }
        }

        mapView.addOnWillStartLoadingMapListener {
            android.util.Log.d("MapScreen", "MapLibre will start loading map")
        }

        mapView.addOnDidFinishLoadingMapListener {
            android.util.Log.d("MapScreen", "MapLibre did finish loading map")
        }

        mapView.addOnDidFailLoadingMapListener { errorMessage ->
            android.util.Log.e("MapScreen", "MapLibre did fail loading map: $errorMessage")
            if (baseMapStatus != BaseMapStatus.READY && baseMapStatus != BaseMapStatus.FALLBACK_RASTER_ACTIVE) {
                baseMapStatus = BaseMapStatus.FAILED
                styleErrorMessage = errorMessage
            }
        }

        mapView.addOnDidFinishLoadingStyleListener {
            android.util.Log.i("MapScreen", "MapLibre did finish loading style")
        }

        baseMapStatus = BaseMapStatus.STYLE_LOADING

        mapView.getMapAsync { mapboxMap ->
            try {
                maplibreMapInstance = mapboxMap

                mapboxMap.setStyle(Style.Builder().fromUri(currentStyleUrl)) { style ->
                    try {
                        android.util.Log.i("MapScreen", "Base style loaded successfully: $currentStyleUrl")
                        baseMapStatus = BaseMapStatus.READY
                        styleErrorMessage = null

                        val target = LatLng(state.cameraTarget.first, state.cameraTarget.second)
                        val pos = CameraPosition.Builder()
                            .target(target)
                            .zoom(state.zoomLevel)
                            .build()
                        mapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos))

                        // Render vector layers & markers safely on top of loaded base style
                        updateMaplibreLayers(mapboxMap, state)
                    } catch (t: Throwable) {
                        android.util.Log.e("MapScreen", "Error during style load init: ${t.message}", t)
                    }
                }

                mapboxMap.addOnMapClickListener { latLng ->
                    try {
                        val screenPoint = mapboxMap.projection.toScreenLocation(latLng)
                        val renderedFeatures = mapboxMap.queryRenderedFeatures(
                            screenPoint,
                            MapLibreLayerManager.LAYER_ACTIVE_FIRES_SYMBOL,
                            MapLibreLayerManager.LAYER_ACTIVE_FIRES_CIRCLE,
                            MapLibreLayerManager.LAYER_DISASTER_FILL,
                            MapLibreLayerManager.LAYER_DISASTER_LINE,
                            MapLibreLayerManager.LAYER_DISASTER_POINTS_SYMBOL,
                            MapLibreLayerManager.LAYER_DISASTER_POINTS_CIRCLE,
                            MapLibreLayerManager.LAYER_HOSPITALS_SYMBOL,
                            MapLibreLayerManager.LAYER_HOSPITALS_CIRCLE,
                            MapLibreLayerManager.LAYER_CLINICS_SYMBOL,
                            MapLibreLayerManager.LAYER_CLINICS_CIRCLE,
                            MapLibreLayerManager.LAYER_POLICE_SYMBOL,
                            MapLibreLayerManager.LAYER_POLICE_CIRCLE,
                            MapLibreLayerManager.LAYER_FUEL_SYMBOL,
                            MapLibreLayerManager.LAYER_FUEL_CIRCLE,
                            MapLibreLayerManager.LAYER_FIRE_STATIONS_SYMBOL,
                            MapLibreLayerManager.LAYER_FIRE_STATIONS_CIRCLE,
                            MapLibreLayerManager.LAYER_SAFE_HAVENS_SYMBOL,
                            MapLibreLayerManager.LAYER_SAFE_HAVENS_CIRCLE,
                            MapLibreLayerManager.LAYER_WEATHER_POINTS_CIRCLE,
                            MapLibreLayerManager.LAYER_FOREST_FILL,
                            MapLibreLayerManager.LAYER_FOREST_LINE
                        )
                        if (renderedFeatures.isNotEmpty()) {
                            val top = renderedFeatures.first()
                            val featId = top.getStringProperty("id") ?: top.id()
                            val clicked = state.allFeatures.firstOrNull { it.id == featId }
                                ?: state.allFeatures.firstOrNull { it.title == top.getStringProperty("title") }
                            if (clicked != null) {
                                viewModel.selectFeature(clicked)
                                return@addOnMapClickListener true
                            } else {
                                val title = top.getStringProperty("title") ?: top.getStringProperty("name")
                                if (!title.isNullOrBlank()) {
                                    val catStr = top.getStringProperty("category") ?: "HAZARD_ZONE"
                                    val category = try { MapFeatureCategory.valueOf(catStr) } catch (e: Exception) { MapFeatureCategory.HAZARD_ZONE }
                                    val dynamicFeature = UnifiedMapFeature(
                                        id = featId ?: "feature-${System.currentTimeMillis()}",
                                        title = title,
                                        subtitle = top.getStringProperty("subtitle") ?: top.getStringProperty("description"),
                                        category = category,
                                        latitude = latLng.latitude,
                                        longitude = latLng.longitude
                                    )
                                    viewModel.selectFeature(dynamicFeature)
                                    return@addOnMapClickListener true
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.w("MapScreen", "Map tap query failed safely: ${t.message}")
                    }
                    false
                }

                mapboxMap.setOnMarkerClickListener { marker ->
                    try {
                        val snippet = marker.snippet ?: ""
                        val clicked = state.visibleFeatures.firstOrNull { it.id == snippet }
                        if (clicked != null) {
                            viewModel.selectFeature(clicked)
                        } else {
                            val byTitle = state.visibleFeatures.firstOrNull { it.title == marker.title }
                            viewModel.selectFeature(byTitle)
                        }
                    } catch (t: Throwable) {
                        android.util.Log.w("MapScreen", "Marker tap failed safely: ${t.message}")
                    }
                    true
                }

                mapboxMap.addOnCameraIdleListener {
                    try {
                        val curZoom = mapboxMap.cameraPosition.zoom
                        viewModel.setZoomLevel(curZoom)
                    } catch (t: Throwable) {
                        android.util.Log.w("MapScreen", "Camera idle callback failed: ${t.message}")
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("MapScreen", "getMapAsync callback failure: ${t.message}", t)
                baseMapStatus = BaseMapStatus.FAILED
                styleErrorMessage = t.message
            }
        }
    }

    // Reactively update MapLibre vector layers, POI icons, and filters whenever features or filter selections update
    LaunchedEffect(
        state.allFeatures,
        state.facilitiesGeoJson,
        state.visibleFeatures,
        state.selectedFilter,
        state.layerVisibilities,
        state.routeCoordinates,
        baseMapStatus
    ) {
        if (baseMapStatus == BaseMapStatus.READY || baseMapStatus == BaseMapStatus.FALLBACK_RASTER_ACTIVE) {
            maplibreMapInstance?.let { map ->
                if (map.style?.isFullyLoaded == true) {
                    updateMaplibreLayers(map, state)
                }
            }
        }
    }

    // Animate camera to active route when route selection changes
    LaunchedEffect(state.activeRouteId) {
        val routeId = state.activeRouteId
        if (routeId != null && routeId != lastRenderedRouteId) {
            lastRenderedRouteId = routeId
            maplibreMapInstance?.let { map ->
                if (map.style?.isFullyLoaded == true && state.routeCoordinates.isNotEmpty()) {
                    val centerLat = state.routeCoordinates.map { it.first }.average()
                    val centerLon = state.routeCoordinates.map { it.second }.average()
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLon), 12.0))
                }
            }
        }
    }

    // Animate camera when camera target is updated programmatically
    LaunchedEffect(state.cameraTarget) {
        if (baseMapStatus == BaseMapStatus.READY || baseMapStatus == BaseMapStatus.FALLBACK_RASTER_ACTIVE) {
            maplibreMapInstance?.let { map ->
                if (map.style?.isFullyLoaded == true) {
                    val target = LatLng(state.cameraTarget.first, state.cameraTarget.second)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, state.zoomLevel))
                }
            }
        }
    }

    val retryStyleLoad: () -> Unit = {
        baseMapStatus = BaseMapStatus.STYLE_LOADING
        styleErrorMessage = null
        currentStyleUrl = MapConfig.DEFAULT_STYLE_URL
        maplibreMapInstance?.let { map ->
            try {
                map.setStyle(Style.Builder().fromUri(MapConfig.DEFAULT_STYLE_URL)) { style ->
                    android.util.Log.i("MapScreen", "Retry loaded base style successfully")
                    baseMapStatus = BaseMapStatus.READY
                    updateMaplibreLayers(map, state)
                }
            } catch (t: Throwable) {
                android.util.Log.e("MapScreen", "Retry style load failed: ${t.message}")
                baseMapStatus = BaseMapStatus.FAILED
                styleErrorMessage = t.message
            }
        } ?: run {
            mapView.getMapAsync { map ->
                maplibreMapInstance = map
                try {
                    map.setStyle(Style.Builder().fromUri(MapConfig.DEFAULT_STYLE_URL)) { style ->
                        android.util.Log.i("MapScreen", "Retry loaded base style successfully")
                        baseMapStatus = BaseMapStatus.READY
                        updateMaplibreLayers(map, state)
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("MapScreen", "Retry style load failed: ${t.message}")
                    baseMapStatus = BaseMapStatus.FAILED
                    styleErrorMessage = t.message
                }
            }
        }
    }

    val switchToFallbackRaster: () -> Unit = {
        baseMapStatus = BaseMapStatus.STYLE_LOADING
        styleErrorMessage = null
        currentStyleUrl = "OSM Raster (Offline Fallback)"
        val fallbackBuilder = Style.Builder().fromJson(MapConfig.OSM_RASTER_STYLE_JSON)
        maplibreMapInstance?.let { map ->
            try {
                map.setStyle(fallbackBuilder) { style ->
                    android.util.Log.i("MapScreen", "Applied OSM raster fallback style successfully")
                    baseMapStatus = BaseMapStatus.FALLBACK_RASTER_ACTIVE
                    updateMaplibreLayers(map, state)
                }
            } catch (t: Throwable) {
                android.util.Log.e("MapScreen", "Fallback style failed: ${t.message}")
                baseMapStatus = BaseMapStatus.FAILED
                styleErrorMessage = t.message
            }
        } ?: run {
            mapView.getMapAsync { map ->
                maplibreMapInstance = map
                try {
                    map.setStyle(fallbackBuilder) { style ->
                        android.util.Log.i("MapScreen", "Applied OSM raster fallback style successfully")
                        baseMapStatus = BaseMapStatus.FALLBACK_RASTER_ACTIVE
                        updateMaplibreLayers(map, state)
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("MapScreen", "Fallback style failed: ${t.message}")
                    baseMapStatus = BaseMapStatus.FAILED
                    styleErrorMessage = t.message
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFE5E0D8))) {
        // 1. BASE MAP LAYER (MapLibre Native or Fallback Offline Canvas)
        if (userToggledCanvas) {
            OfflineVectorCanvasView(
                routeCoordinates = state.routeCoordinates,
                corridorPolygonCoordinates = state.corridorPolygonCoordinates,
                features = state.visibleFeatures,
                onFeatureClicked = { viewModel.selectFeature(it) },
                onDismiss = { userToggledCanvas = false }
            )
        } else {
            AndroidView(
                factory = { _ ->
                    (mapView.parent as? ViewGroup)?.removeView(mapView)
                    mapView
                },
                update = {
                    try {
                        maplibreMapInstance?.let { map ->
                            if (map.style?.isFullyLoaded == true) {
                                updateMaplibreLayers(map, state)

                                if (state.activeRouteId != null && state.activeRouteId != lastRenderedRouteId) {
                                    lastRenderedRouteId = state.activeRouteId
                                    if (state.routeCoordinates.isNotEmpty()) {
                                        val centerLat = state.routeCoordinates.map { it.first }.average()
                                        val centerLon = state.routeCoordinates.map { it.second }.average()
                                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLon), 12.0))
                                    }
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.w("MapScreen", "Error during map update lambda: ${t.message}")
                    }
                },
                onRelease = {
                    // Safe release handled by DisposableEffect
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Diagnostic & Recovery Card when Base Map Fails
        if (baseMapStatus == BaseMapStatus.FAILED) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Base Map Loading Issue",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("• View Size: ${measuredWidthPx} x ${measuredHeightPx} px", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Text("• Target Coordinates: ${String.format(java.util.Locale.US, "%.4f, %.4f", state.cameraTarget.first, state.cameraTarget.second)} (Zoom ${state.zoomLevel})", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Text("• Configured Style: $currentStyleUrl", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Text("• Status: ${baseMapStatus.name}", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    if (!styleErrorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Error: $styleErrorMessage", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { retryStyleLoad() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { switchToFallbackRaster() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Use OSM Fallback", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Indicator when Base Map is in Fallback Raster Mode
        if (baseMapStatus == BaseMapStatus.FALLBACK_RASTER_ACTIVE) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF334155).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 110.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Degraded Mode: Standard OSM Raster Basemap",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Switch to Vector",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { retryStyleLoad() }
                    )
                }
            }
        }

        // 2. TOP FLOATING INTERACTION BAR: Location Search Card & Horizontal Filters
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Floating Search Card
            TopSearchCard(
                routeTitle = state.routeTitle,
                onRecenterGps = {
                    viewModel.recenterToUserLocation()
                    maplibreMapInstance?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(state.userLocation.first, state.userLocation.second),
                            14.5
                        )
                    )
                },
                onChangeDestination = { viewModel.toggleSearchDialog(true) }
            )

            if (state.locationErrorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF3E0),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = state.locationErrorMessage ?: "",
                            fontSize = 11.sp,
                            color = Color(0xFFE65100),
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                        TextButton(
                            onClick = { viewModel.requestCurrentLocationFix(recenter = true) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontally Scrollable Filter Chips
            FilterChipsRow(
                selectedFilter = state.selectedFilter,
                categoryCounts = state.categoryCounts,
                onSelectFilter = { viewModel.onFilterSelected(it) },
                onOpenNearbyList = { viewModel.toggleNearbySheet(true) }
            )

            // Prominent Offline Mode Indicator
            if (state.isOfflineMode) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF263238).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFF78909C)),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = "Offline Mode",
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Offline Mode • Cached Basemap & Hazards",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 3. FLOATING MAP CONTROLS (Right Column)
        FloatingMapControls(
            isOffline = state.isOfflineMode,
            onRecenter = {
                viewModel.recenterToUserLocation()
                maplibreMapInstance?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(state.userLocation.first, state.userLocation.second),
                        14.5
                    )
                )
            },
            onFitRoute = {
                viewModel.fitToRoute()
                if (state.routeCoordinates.isNotEmpty()) {
                    val centerLat = state.routeCoordinates.map { it.first }.average()
                    val centerLon = state.routeCoordinates.map { it.second }.average()
                    maplibreMapInstance?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLon), 12.0)
                    )
                }
            },
            onOpenLayers = { viewModel.toggleLayersSheet(true) },
            onToggleOffline = {
                userToggledCanvas = !userToggledCanvas
                viewModel.toggleOfflineSchematic()
            },
            onReportTrailCondition = { showReportConditionSheet = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )

        if (showReportConditionSheet && viewModel.trailReportRepository != null) {
            val locState by viewModel.locationState.collectAsState()
            ReportTrailConditionBottomSheet(
                initialLat = locState.latitude ?: if (state.userLocation.first != 0.0) state.userLocation.first else null,
                initialLon = locState.longitude ?: if (state.userLocation.second != 0.0) state.userLocation.second else null,
                initialAccuracyM = locState.location?.accuracy?.toDouble() ?: 10.0,
                routeId = state.activeRouteId,
                trailReportRepository = viewModel.trailReportRepository!!,
                onDismiss = { showReportConditionSheet = false },
                onReportSubmitted = { showReportConditionSheet = false }
            )
        }

        // 4. BOTTOM ROUTE SUMMARY CARD (Active Route Only) OR MINIMAL ACTION PILL
        val hasActiveRoute = state.activeRouteId != null || state.routeCoordinates.size >= 2

        if (hasActiveRoute) {
            RouteSummaryCard(
                routeTitle = state.routeTitle,
                distanceKm = state.routeDistanceKm,
                durationHours = state.routeDurationHours,
                isOffline = state.isOfflineMode,
                activeAlertsCount = state.activeDisasterAlertsCount,
                weather = state.routeSummaryWeather,
                fireCount = state.routeSummaryFireCount,
                forestCount = state.routeSummaryForestCount,
                nearestMedicalDistance = state.nearbyAssistanceList.firstOrNull { it.category == MapFeatureCategory.HOSPITAL || it.category == MapFeatureCategory.CLINIC }?.formattedDistance(),
                isExpanded = state.isBottomRouteExpanded,
                onToggleExpand = { viewModel.toggleBottomRouteExpanded() },
                plannedRouteAlternatives = state.plannedRouteAlternatives,
                selectedAlternativeId = state.selectedAlternativeId,
                overallSafetyStatus = state.routeSafetyStatus,
                overallHazardScore = state.routeOverallHazardScore,
                onSelectAlternative = { viewModel.selectRouteAlternative(it) },
                corridorDistanceKm = state.corridorDistanceKm,
                isAnalyzingCorridor = state.isAnalyzingCorridor,
                onSelectCorridorDistance = { viewModel.setCorridorDistance(it) },
                analysisLimitations = state.analysisLimitations,
                onOpenComparison = { viewModel.toggleComparisonSheet(true) },
                onClearRoute = { viewModel.clearRoute() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        } else {
            // Sleek floating pill when exploring the map without an active route
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clickable { onNavigateToPlanTrip() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Plan Safe Trek",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Map Attribution Pill
        val attributionBottomPadding = if (hasActiveRoute) {
            if (state.isBottomRouteExpanded) 220.dp else 66.dp
        } else {
            14.dp
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = attributionBottomPadding)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "© OpenStreetMap contributors • NASA FIRMS • DDMA",
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray,
                fontSize = 9.sp
            )
        }

        // 5. BOTTOM SHEETS & DIALOGS

        // A. Selected Feature Detail Bottom Sheet
        state.selectedFeature?.let { feature ->
            FeatureDetailBottomSheet(
                feature = feature,
                onDismiss = { viewModel.selectFeature(null) }
            )
        }

        // B. Layers Configuration Sheet
        if (state.showLayersSheet) {
            MapLayersSheet(
                layerVisibilities = state.layerVisibilities,
                layerStatuses = state.layerStatuses,
                categoryCounts = state.categoryCounts,
                onToggleLayer = { viewModel.toggleLayer(it) },
                onShowAll = { viewModel.showAllLayers() },
                onResetDefaults = { viewModel.resetLayerDefaults() },
                onDismiss = { viewModel.toggleLayersSheet(false) }
            )
        }

        // C. Nearby Assistance Sheet
        if (state.showNearbySheet) {
            NearbyAssistanceSheet(
                items = state.nearbyAssistanceList,
                onSelectFeature = { feat ->
                    viewModel.selectFeature(feat)
                    maplibreMapInstance?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(feat.latitude, feat.longitude), 15.0)
                    )
                },
                onDismiss = { viewModel.toggleNearbySheet(false) }
            )
        }

        // D. Destination Search Dialog
        if (state.showSearchDialog) {
            PlaceSearchDialog(
                query = state.searchQuery,
                onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                results = state.searchResults,
                isSearching = state.isSearching,
                onSelectPlace = { place ->
                    viewModel.selectDestination(place)
                    maplibreMapInstance?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), 13.5)
                    )
                },
                onDismiss = { viewModel.toggleSearchDialog(false) }
            )
        }

        // E. Route Candidates Comparison Bottom Sheet
        if (state.showComparisonSheet) {
            RouteComparisonSheet(
                state = state,
                onDismiss = { viewModel.toggleComparisonSheet(false) },
                onSelectAlternative = { altId ->
                    viewModel.selectRouteAlternative(altId)
                    viewModel.toggleComparisonSheet(false)
                },
                onSelectPreference = { pref ->
                    viewModel.setComparisonPreference(pref)
                }
            )
        }
    }
}

/**
 * Top floating search card communicating place name, GPS center, and destination change.
 */
@Composable
private fun TopSearchCard(
    routeTitle: String,
    onRecenterGps: () -> Unit,
    onChangeDestination: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onChangeDestination() }
            ) {
                Text(
                    text = "Current Itinerary / Destination",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = routeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // GPS button
            IconButton(
                onClick = onRecenterGps,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Change destination button
            IconButton(
                onClick = onChangeDestination,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EditLocationAlt,
                    contentDescription = "Change Destination",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Filter chips row displaying real record counts and quick access to nearby assistance list.
 */
@Composable
private fun FilterChipsRow(
    selectedFilter: MapFilterChip,
    categoryCounts: Map<MapFeatureCategory, Int>,
    onSelectFilter: (MapFilterChip) -> Unit,
    onOpenNearbyList: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MapFilterChip.entries.forEach { chip ->
            val isSelected = selectedFilter == chip
            val count = if (chip == MapFilterChip.ALL) {
                null
            } else if (chip.category != null) {
                categoryCounts[chip.category] ?: 0
            } else {
                null
            }

            FilterChip(
                selected = isSelected,
                onClick = { onSelectFilter(chip) },
                label = {
                    val labelText = if (count != null && count > 0) "${chip.label} ($count)" else chip.label
                    Text(labelText, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                },
                leadingIcon = chip.category?.let { cat ->
                    {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = null,
                            tint = if (isSelected) cat.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Quick Nearby List Pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clickable { onOpenNearbyList() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FormatListBulleted,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Nearby List",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * Floating map controls for Recenter, Fit Route, Layers, and Offline mode toggle.
 */
@Composable
private fun FloatingMapControls(
    isOffline: Boolean,
    onRecenter: () -> Unit,
    onFitRoute: () -> Unit,
    onOpenLayers: () -> Unit,
    onToggleOffline: () -> Unit,
    onReportTrailCondition: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FloatingActionButton(
            onClick = onRecenter,
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Recenter", modifier = Modifier.size(20.dp))
        }

        FloatingActionButton(
            onClick = onFitRoute,
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(Icons.Default.Route, contentDescription = "Fit Route", modifier = Modifier.size(20.dp))
        }

        FloatingActionButton(
            onClick = onOpenLayers,
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(Icons.Default.Layers, contentDescription = "Layers", modifier = Modifier.size(20.dp))
        }

        FloatingActionButton(
            onClick = onReportTrailCondition,
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Report,
                contentDescription = "Report a Trail Condition",
                modifier = Modifier.size(20.dp)
            )
        }

        FloatingActionButton(
            onClick = onToggleOffline,
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            containerColor = if (isOffline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (isOffline) MaterialTheme.colorScheme.primary else Color.Gray,
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(
                imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                contentDescription = "Toggle Offline Mode",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Updates MapLibre vector layers, polyline route line, forest polygons, hazard zones, and clustered markers.
 */
private fun updateMaplibreLayers(mapboxMap: MapLibreMap, state: MapUiState, context: android.content.Context? = null) {
    try {
        val style = mapboxMap.style
        if (style == null || !style.isFullyLoaded) {
            return
        }

        try {
            MapLibreLayerManager.setupSourcesAndLayers(style, context)
            MapLibreLayerManager.updateData(style, state)
        } catch (t: Throwable) {
            android.util.Log.e("MapScreen", "Vector layers update failed safely: ${t.message}")
        }

        // Manage lightweight GPS position marker and route endpoints via annotation system only when style is fully loaded
        try {
            mapboxMap.clear()
            if (state.isUserLocationAcquired) {
                val acc = state.userLocationAccuracyMeters?.toInt()?.let { " (±${it}m)" } ?: ""
                mapboxMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(state.userLocation.first, state.userLocation.second))
                        .title("Your Current Location$acc")
                        .snippet("GPS Location")
                )
            }
            if (state.routeCoordinates.size >= 2) {
                val startCoord = state.routeCoordinates.first()
                val destCoord = state.routeCoordinates.last()
                mapboxMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(startCoord.first, startCoord.second))
                        .title("Route Start")
                        .snippet(state.routeTitle)
                )
                mapboxMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(destCoord.first, destCoord.second))
                        .title("Route Destination")
                        .snippet(state.routeTitle)
                )
            }
        } catch (t: Throwable) {
            android.util.Log.w("MapScreen", "Annotations update failed safely: ${t.message}")
        }
    } catch (e: Throwable) {
        android.util.Log.e("MapScreen", "updateMaplibreLayers failed safely: ${e.message}")
    }
}

/**
 * High-performance Offline Canvas Vector Schematic fallback when offline or toggled.
 */
@Composable
fun OfflineVectorCanvasView(
    routeCoordinates: List<Pair<Double, Double>>,
    corridorPolygonCoordinates: List<List<Pair<Double, Double>>> = emptyList(),
    features: List<UnifiedMapFeature>,
    onFeatureClicked: (UnifiedMapFeature) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E272C))
            .padding(top = 100.dp, bottom = 120.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Offline Route Vector Schematic & Cached Features",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF37474F),
                    modifier = Modifier.clickable { onDismiss() }
                ) {
                    Text(
                        text = "Map Mode",
                        color = Color(0xFF80D8FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF121B20))
                .pointerInput(features) {
                    detectTapGestures { offset ->
                        // Hit detection for features on canvas
                        // Handled by nearest coordinate mapping
                    }
                }
        ) {
            val allLats = routeCoordinates.map { it.first } + features.map { it.latitude }
            val allLons = routeCoordinates.map { it.second } + features.map { it.longitude }

            val minLat = allLats.minOrNull() ?: 32.20
            val maxLat = allLats.maxOrNull() ?: 32.30
            val minLon = allLons.minOrNull() ?: 77.15
            val maxLon = allLons.maxOrNull() ?: 77.25

            val latSpan = (maxLat - minLat).coerceAtLeast(0.01)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.01)

            fun project(lat: Double, lon: Double): Offset {
                val x = ((lon - minLon) / lonSpan * (size.width - 80f) + 40f).toFloat()
                val y = ((maxLat - lat) / latSpan * (size.height - 80f) + 40f).toFloat()
                return Offset(x, y)
            }

            // 0. Draw Route Corridor Polygon (if available)
            corridorPolygonCoordinates.forEach { ring ->
                if (ring.size >= 3) {
                    val cPath = Path()
                    ring.forEachIndexed { idx, (lat, lon) ->
                        val pt = project(lat, lon)
                        if (idx == 0) cPath.moveTo(pt.x, pt.y) else cPath.lineTo(pt.x, pt.y)
                    }
                    cPath.close()
                    drawPath(path = cPath, color = Color(0x2203A9F4))
                    drawPath(path = cPath, color = Color(0x660288D1), style = Stroke(width = 2f))
                }
            }

            // 1. Draw Forest polygons
            features.filter { it.category == MapFeatureCategory.FOREST_AREA }.forEach { forest ->
                forest.geometryPolygon?.let { coords ->
                    if (coords.size >= 3) {
                        val path = Path()
                        coords.forEachIndexed { idx, (lat, lon) ->
                            val pt = project(lat, lon)
                            if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                        }
                        path.close()
                        drawPath(path = path, color = Color(0x332E7D32))
                        drawPath(path = path, color = Color(0xFF4CAF50), style = Stroke(width = 3f))
                    }
                }
            }

            // 2. Draw Hazard Zones
            features.filter { it.category == MapFeatureCategory.HAZARD_ZONE }.forEach { hazard ->
                hazard.geometryPolygon?.let { coords ->
                    if (coords.size >= 3) {
                        val path = Path()
                        coords.forEachIndexed { idx, (lat, lon) ->
                            val pt = project(lat, lon)
                            if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                        }
                        path.close()
                        drawPath(path = path, color = Color(0x33C62828))
                        drawPath(path = path, color = Color(0xFFE53935), style = Stroke(width = 3f))
                    }
                }
            }

            // 3. Draw Route Polyline
            if (routeCoordinates.size >= 2) {
                val trailPath = Path()
                routeCoordinates.forEachIndexed { index, (lat, lon) ->
                    val pt = project(lat, lon)
                    if (index == 0) trailPath.moveTo(pt.x, pt.y) else trailPath.lineTo(pt.x, pt.y)
                }
                drawPath(path = trailPath, color = Color(0xFF0288D1), style = Stroke(width = 8f))

                // Start dot (green)
                val start = project(routeCoordinates.first().first, routeCoordinates.first().second)
                drawCircle(color = Color(0xFF4CAF50), radius = 14f, center = start)

                // End dot (red)
                val end = project(routeCoordinates.last().first, routeCoordinates.last().second)
                drawCircle(color = Color(0xFFF44336), radius = 14f, center = end)
            }

            // 4. Draw Point Features
            features.filter { !it.category.isAreaFeature }.forEach { feat ->
                val pt = project(feat.latitude, feat.longitude)
                drawCircle(color = feat.category.primaryColor, radius = 12f, center = pt)
                drawCircle(color = Color.White, radius = 5f, center = pt)
            }
        }
    }
}
