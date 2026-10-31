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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.crash.CrashReporter
import com.touristsafety.app.core.map.BasemapManager
import com.touristsafety.app.core.map.BasemapMode
import com.touristsafety.app.core.map.ClusterItem
import com.touristsafety.app.core.map.MapConfig
import com.touristsafety.app.core.map.MapLibreLayerManager
import com.touristsafety.app.core.map.HazardAnimationController
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var maplibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var baseMapStatus by remember { mutableStateOf(BaseMapStatus.INITIALIZING) }
    var currentStyleUrl by remember { mutableStateOf(MapConfig.DEFAULT_STYLE_URL) }
    var styleErrorMessage by remember { mutableStateOf<String?>(null) }
    var measuredWidthPx by remember { mutableIntStateOf(0) }
    var measuredHeightPx by remember { mutableIntStateOf(0) }
    var userToggledCanvas by remember { mutableStateOf(false) }
    var showReportConditionSheet by remember { mutableStateOf(false) }
    var lastRenderedRouteId by remember { mutableStateOf<String?>(null) }
    val animationController = remember { HazardAnimationController() }
    val basemapManager = remember {
        BasemapManager(
            onStyleLoadStarted = {
                baseMapStatus = BaseMapStatus.STYLE_LOADING
            },
            onStyleLoadSuccess = { _, _ ->
                baseMapStatus = BaseMapStatus.READY
                styleErrorMessage = null
            },
            onStyleLoadFailed = { _, error ->
                baseMapStatus = BaseMapStatus.FAILED
                styleErrorMessage = error
            }
        )
    }

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
            animationController.stop()
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
            CrashReporter.recordNonFatal(RuntimeException("MapLibre map load failure: $errorMessage"), "MAPLIBRE_STYLE_LOAD")
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

                val initialStyleBuilder = MapConfig.createStyleBuilder(state.basemapMode)
                mapboxMap.setStyle(initialStyleBuilder) { style ->
                    try {
                        android.util.Log.i("MapScreen", "Base style loaded successfully: $currentStyleUrl")
                        baseMapStatus = BaseMapStatus.READY
                        styleErrorMessage = null

                        // Restrict camera navigation strictly to Uttarakhand administrative territory
                        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                            .include(LatLng(MapConfig.UTTARAKHAND_MIN_LAT, MapConfig.UTTARAKHAND_MIN_LON))
                            .include(LatLng(MapConfig.UTTARAKHAND_MAX_LAT, MapConfig.UTTARAKHAND_MAX_LON))
                            .build()
                        mapboxMap.setLatLngBoundsForCameraTarget(bounds)
                        mapboxMap.setMinZoomPreference(MapConfig.MIN_ZOOM_LEVEL)
                        mapboxMap.setMaxZoomPreference(MapConfig.MAX_ZOOM_LEVEL)

                        val target = LatLng(state.cameraTarget.first, state.cameraTarget.second)
                        if (state.cameraTarget.first == MapConfig.DEFAULT_LATITUDE && state.cameraTarget.second == MapConfig.DEFAULT_LONGITUDE) {
                            // Frame complete Uttarakhand state boundary with clean padding
                            mapboxMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 32))
                        } else {
                            val pos = CameraPosition.Builder()
                                .target(target)
                                .zoom(state.zoomLevel)
                                .bearing(state.cameraBearing)
                                .build()
                            mapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
                        }

                        // Render vector layers & markers safely on top of loaded base style
                        updateMaplibreLayers(mapboxMap, state, context)
                    } catch (t: Throwable) {
                        android.util.Log.e("MapScreen", "Error during style load init: ${t.message}", t)
                    }
                }

                mapboxMap.addOnMapClickListener { latLng ->
                    if (viewModel.uiState.value.isDrawingMode) {
                        viewModel.addDraftWaypoint(latLng.latitude, latLng.longitude)
                        return@addOnMapClickListener true
                    }
                    try {
                        val screenPoint = mapboxMap.projection.toScreenLocation(latLng)

                        // 1. Priority 1: Interactive Point Markers & Weather Circles
                        val pointFeatures = mapboxMap.queryRenderedFeatures(
                            screenPoint,
                            MapLibreLayerManager.LAYER_WEATHER_POINTS_CIRCLE,
                            MapLibreLayerManager.LAYER_ROUTE_HAZARD_SEQUENCE_SYMBOL,
                            MapLibreLayerManager.LAYER_COMMUNITY_HAZARDS_SYMBOL,
                            MapLibreLayerManager.LAYER_COMMUNITY_HAZARDS_CIRCLE,
                            MapLibreLayerManager.LAYER_ACTIVE_FIRES_SYMBOL,
                            MapLibreLayerManager.LAYER_ACTIVE_FIRES_CIRCLE,
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
                            MapLibreLayerManager.LAYER_HISTORICAL_LANDSLIDES_SYMBOL,
                            MapLibreLayerManager.LAYER_HISTORICAL_LANDSLIDES_CIRCLE,
                            MapLibreLayerManager.LAYER_CELLULAR_SITES_SYMBOL,
                            MapLibreLayerManager.LAYER_CELLULAR_SITES_CIRCLE
                        )

                        if (pointFeatures.isNotEmpty()) {
                            val top = pointFeatures.first()
                            val featId = top.getStringProperty("id") ?: top.id()
                            val layerId = top.getStringProperty("layer")
                            val catProp = top.getStringProperty("category") ?: top.getStringProperty("category_enum")

                            // Weather Point Sample tapped
                            if (layerId == "weather_point" || catProp == "WEATHER_POINT") {
                                val sampleId = top.getStringProperty("sample_id") ?: featId
                                val sampleIdx = top.getNumberProperty("sample_index")?.toInt() ?: top.getNumberProperty("index")?.toInt() ?: 0
                                val targetSample = state.routeWeatherSamples.firstOrNull { it.sampleId == sampleId || it.sampleIndex == sampleIdx }
                                if (targetSample != null) {
                                    viewModel.selectTarget(targetSample)
                                    return@addOnMapClickListener true
                                } else {
                                    val name = top.getStringProperty("name") ?: top.getStringProperty("title") ?: "Route Weather Sample"
                                    val tempC = top.getNumberProperty("temperature_c")?.toDouble() ?: 18.0
                                    val windKmh = top.getNumberProperty("wind_speed_kmh")?.toDouble() ?: 10.0
                                    val precipMm = top.getNumberProperty("precipitation_mm")?.toDouble() ?: 0.0
                                    val precipProb = top.getNumberProperty("precipitation_probability_pct")?.toDouble() ?: 0.0
                                    val desc = top.getStringProperty("description") ?: "Weather Forecast"
                                    val distKm = top.getNumberProperty("distance_km")?.toDouble() ?: 0.0
                                    val dynamicSample = com.touristsafety.app.core.model.SelectedMapTarget.RouteWeather(
                                        sampleId = sampleId ?: "weather-sample-$sampleIdx",
                                        sampleIndex = sampleIdx,
                                        name = name,
                                        latitude = latLng.latitude,
                                        longitude = latLng.longitude,
                                        distanceAlongRouteKm = distKm,
                                        weather = com.touristsafety.app.core.network.dto.RouteWeatherConditionDto(
                                            temperature_c = tempC,
                                            relative_humidity_pct = top.getNumberProperty("relative_humidity_pct")?.toDouble() ?: 60.0,
                                            precipitation_probability_pct = precipProb,
                                            precipitation_mm = precipMm,
                                            wind_speed_kmh = windKmh,
                                            weather_code = top.getNumberProperty("weather_code")?.toInt() ?: 1,
                                            weather_description = desc,
                                            warning_level = top.getStringProperty("warning_level") ?: "NONE",
                                            forecast_valid_at = top.getStringProperty("forecast_valid_at") ?: "Current",
                                            source = top.getStringProperty("source") ?: "Open-Meteo"
                                        )
                                    )
                                    viewModel.selectTarget(dynamicSample)
                                    return@addOnMapClickListener true
                                }
                            }

                            // Hazard sequence item tapped
                            val seqLabel = top.getStringProperty("sequence_label")
                            val seqItem = state.routeHazardSequence.firstOrNull { it.feature.id == featId || it.sequenceLabel == seqLabel }
                            if (seqItem != null) {
                                viewModel.selectHazardSequenceItem(seqItem)
                                return@addOnMapClickListener true
                            }

                            // Known mapped feature tapped
                            val clicked = state.allFeatures.firstOrNull { it.id == featId }
                                ?: state.allFeatures.firstOrNull { it.title == top.getStringProperty("title") }
                            if (clicked != null) {
                                viewModel.selectFeature(clicked)
                                return@addOnMapClickListener true
                            }
                        }

                        // 2. Priority 2: Line Features (Route)
                        val lineFeatures = mapboxMap.queryRenderedFeatures(
                            screenPoint,
                            MapLibreLayerManager.LAYER_ROUTE_PRIMARY_LINE,
                            MapLibreLayerManager.LAYER_ROUTE_SECONDARY_LINE
                        )
                        if (lineFeatures.isNotEmpty()) {
                            viewModel.inspectPoint(latLng.latitude, latLng.longitude)
                            return@addOnMapClickListener true
                        }

                        // 3. Priority 3: Polygon Features (Disasters, Forests, Waterways)
                        val polyFeatures = mapboxMap.queryRenderedFeatures(
                            screenPoint,
                            MapLibreLayerManager.LAYER_DISASTER_FILL,
                            MapLibreLayerManager.LAYER_DISASTER_LINE,
                            MapLibreLayerManager.LAYER_FOREST_FILL,
                            MapLibreLayerManager.LAYER_FOREST_LINE,
                            MapLibreLayerManager.LAYER_WATERWAYS_FILL,
                            MapLibreLayerManager.LAYER_WATERWAYS_LINE
                        )
                        if (polyFeatures.isNotEmpty()) {
                            val topPoly = polyFeatures.first()
                            val polyId = topPoly.getStringProperty("id") ?: topPoly.id()
                            val clickedPoly = state.allFeatures.firstOrNull { it.id == polyId }
                                ?: state.allFeatures.firstOrNull { it.title == topPoly.getStringProperty("title") }
                            if (clickedPoly != null) {
                                viewModel.selectFeature(clickedPoly)
                                return@addOnMapClickListener true
                            }
                            val polyName = topPoly.getStringProperty("name") ?: topPoly.getStringProperty("title")
                            if (!polyName.isNullOrBlank()) {
                                val catStr = topPoly.getStringProperty("category") ?: ""
                                val isWaterway = catStr.contains("water") || catStr.contains("river") || catStr.contains("flood") || catStr.contains("lake")
                                val desc = topPoly.getStringProperty("description")
                                    ?: topPoly.getStringProperty("wood_type")
                                    ?: topPoly.getStringProperty("basin")
                                    ?: topPoly.getStringProperty("source")
                                    ?: "Environmental Map Feature"
                                val syntheticFeature = com.touristsafety.app.core.model.UnifiedMapFeature(
                                    id = polyId ?: "poly_${System.currentTimeMillis()}",
                                    category = if (isWaterway) com.touristsafety.app.core.model.MapFeatureCategory.WATERWAY else com.touristsafety.app.core.model.MapFeatureCategory.FOREST_AREA,
                                    title = polyName,
                                    subtitle = desc,
                                    latitude = latLng.latitude,
                                    longitude = latLng.longitude,
                                    details = mapOf(
                                        "description" to desc,
                                        "severity" to (topPoly.getStringProperty("flood_risk") ?: "INFO")
                                    )
                                )
                                viewModel.selectFeature(syntheticFeature)
                                return@addOnMapClickListener true
                            }
                        }

                        // 4. Priority 4: Empty Map Area -> Universal Map Tap Point Inspection
                        viewModel.inspectPoint(latLng.latitude, latLng.longitude)
                        return@addOnMapClickListener true

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
                        val curPos = mapboxMap.cameraPosition
                        val target = curPos.target
                        if (target != null) {
                            viewModel.saveCameraState(
                                lat = target.latitude,
                                lon = target.longitude,
                                zoom = curPos.zoom,
                                bearing = curPos.bearing
                            )
                        }
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
        state.draftPolyline,
        state.draftWaypoints,
        state.proposedConnectorCoordinates,
        state.liveRecordedTrackCoordinates,
        baseMapStatus
    ) {
        if (baseMapStatus == BaseMapStatus.READY || baseMapStatus == BaseMapStatus.FALLBACK_RASTER_ACTIVE) {
            maplibreMapInstance?.let { map ->
                if (map.style?.isFullyLoaded == true) {
                    updateMaplibreLayers(map, state, context)
                }
            }
        }
    }

    // Control hazard icon & polygon animation loop
    LaunchedEffect(state.isAnimationEnabled, baseMapStatus) {
        if (baseMapStatus == BaseMapStatus.READY || baseMapStatus == BaseMapStatus.FALLBACK_RASTER_ACTIVE) {
            maplibreMapInstance?.let { map ->
                if (map.style?.isFullyLoaded == true) {
                    animationController.start(
                        coroutineScope = this,
                        style = map.style,
                        isAnimationEnabled = state.isAnimationEnabled
                    )
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

    // Seamlessly transition basemap mode while preserving camera, trek recording, and drawing state
    LaunchedEffect(state.basemapMode) {
        val map = maplibreMapInstance ?: return@LaunchedEffect
        if (basemapManager.currentMode != state.basemapMode) {
            basemapManager.switchBasemap(map, state.basemapMode, state, context)
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
                    updateMaplibreLayers(map, state, context)
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
                        updateMaplibreLayers(map, state, context)
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
                    updateMaplibreLayers(map, state, context)
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
                        updateMaplibreLayers(map, state, context)
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
                                updateMaplibreLayers(map, state, context)

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
                    shape = ExpressiveShapes.pill,
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

                if (state.basemapMode.isSatellite) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Satellite imagery requires internet. Cached trails are on Streets & Trails.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Revert",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable {
                                    viewModel.setBasemapMode(BasemapMode.STREETS_TRAILS)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. FLOATING MAP CONTROLS (Right Column)
        FloatingMapControls(
            isOffline = state.isOfflineMode,
            currentBasemapMode = state.basemapMode,
            isDrawing = state.isDrawingMode,
            isRecording = state.isRecordingTrek,
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
            onOpenBasemapSelector = { viewModel.toggleBasemapSelector(true) },
            onOpenLayers = { viewModel.toggleLayersSheet(true) },
            onToggleOffline = {
                userToggledCanvas = !userToggledCanvas
                viewModel.toggleOfflineSchematic()
            },
            onReportTrailCondition = { showReportConditionSheet = true },
            onStartDrawing = { viewModel.enterDrawingMode("TRAIL_FOLLOWING") },
            onStartRecording = { viewModel.startLiveTrekRecording() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )

        if (showReportConditionSheet) {
            val locState by viewModel.locationState.collectAsStateWithLifecycle()
            ReportTrailConditionBottomSheet(
                initialLat = locState.latitude ?: if (state.userLocation.first != 0.0) state.userLocation.first else null,
                initialLon = locState.longitude ?: if (state.userLocation.second != 0.0) state.userLocation.second else null,
                initialAccuracyM = locState.location?.accuracy?.toDouble() ?: 10.0,
                routeId = state.activeRouteId,
                trailReportRepository = viewModel.trailReportRepository,
                onDismiss = { showReportConditionSheet = false },
                onReportSubmitted = {
                    showReportConditionSheet = false
                    viewModel.refreshAllLayers()
                }
            )
        }

        // 4. BOTTOM ROUTE SUMMARY CARD (Active Route Only) OR MINIMAL ACTION PILL
        val hasActiveRoute = state.activeRouteId != null || state.routeCoordinates.size >= 2

        if (hasActiveRoute) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            ) {
                // Route Hazard Timeline Bar (Priority 2: Most relevant upcoming hazard)
                if (state.routeHazardSequence.isNotEmpty()) {
                    RouteHazardTimelineBar(
                        hazardSequence = state.routeHazardSequence,
                        selectedItem = state.selectedHazardSequenceItem,
                        isAnimationEnabled = state.isAnimationEnabled,
                        onToggleAnimation = { viewModel.toggleAnimation() },
                        onExpandTimeline = { viewModel.toggleHazardTimelineExpanded(true) },
                        onSelectItem = { item -> viewModel.selectHazardSequenceItem(item) }
                    )
                }

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
                    verificationStatus = state.routeVerificationStatus,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (!state.isDrawingMode && !state.isRecordingTrek) {
            // 3-Mode Entry Card when exploring the map without active route
            Surface(
                shape = ExpressiveShapes.largeIncreased,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode 1: Find Trail Route
                    Surface(
                        shape = ExpressiveShapes.mediumIncreased,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f).clickable { onNavigateToPlanTrip() }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Find Trail", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    // Mode 2: Draw My Route
                    Surface(
                        shape = ExpressiveShapes.mediumIncreased,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(1f).clickable { viewModel.enterDrawingMode("TRAIL_FOLLOWING") }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Gesture, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Draw Route", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }

                    // Mode 3: Record My Trek
                    Surface(
                        shape = ExpressiveShapes.mediumIncreased,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.weight(1f).clickable { viewModel.startLiveTrekRecording() }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Record Trek", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }
        }

        // DRAWING MODE HUD (Top banner & bottom controls)
        if (state.isDrawingMode) {
            // Top banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Gesture, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Draw Route", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFF3E0)
                        ) {
                            Text(
                                "🟠 Walkability Unverified",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val isTrailFollowing = state.drawingType == "TRAIL_FOLLOWING"
                            FilterChip(
                                selected = isTrailFollowing,
                                onClick = { viewModel.setDrawingType("TRAIL_FOLLOWING") },
                                label = { Text("Trail-Following", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            FilterChip(
                                selected = !isTrailFollowing,
                                onClick = { viewModel.setDrawingType("FREEHAND") },
                                label = { Text("Freehand", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        Text(
                            text = "${state.draftWaypoints.size} pts • ${state.draftDistanceKm} km",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            // Bottom editing toolbar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFF475569)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { viewModel.undoDraftWaypoint() },
                        enabled = state.draftUndoStack.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (state.draftUndoStack.isNotEmpty()) Color.White else Color.Gray
                        )
                    }

                    IconButton(
                        onClick = { viewModel.clearDraft() },
                        enabled = state.draftWaypoints.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Clear",
                            tint = if (state.draftWaypoints.isNotEmpty()) Color(0xFFFCA5A5) else Color.Gray
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.exitDrawingMode() },
                        border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.openSaveDraftDialog() },
                        enabled = state.draftPolyline.size >= 2,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Finish Route", fontSize = 12.sp)
                    }
                }
            }
        }

        // SAVE DRAFT ROUTE DIALOG
        if (state.showSaveDraftDialog) {
            var routeName by remember { mutableStateOf("Custom Trek ${java.text.SimpleDateFormat("dd MMM", java.util.Locale.US).format(java.util.Date())}") }
            var routeDesc by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { viewModel.dismissSaveDraftDialog() },
                title = { Text("Save Custom Route", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Drawn Path: ${state.draftWaypoints.size} waypoints • ${state.draftDistanceKm} km",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = routeName,
                            onValueChange = { routeName = it },
                            label = { Text("Route Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = routeDesc,
                            onValueChange = { routeDesc = it },
                            label = { Text("Description / Notes (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF3E0),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Walkability unverified. Never assume a manually drawn line across forests, cliffs, or rivers is safe or accessible.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.saveDraftRoute(routeName, routeDesc.ifBlank { null }) },
                        enabled = routeName.isNotBlank() && !state.isSavingDraftRoute
                    ) {
                        if (state.isSavingDraftRoute) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Save & Analyze")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissSaveDraftDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // LIVE TREK RECORDING HUD OVERLAY
        if (state.isRecordingTrek) {
            var showStopTrekDialog by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, if (state.isTrekPaused) Color(0xFFF59E0B) else Color(0xFFEF4444)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isTrekPaused) Color(0xFFF59E0B) else Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.isTrekPaused) "TREK PAUSED" else "LIVE TREK RECORDING",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        val hours = state.recordedTrekDurationSeconds / 3600
                        val mins = (state.recordedTrekDurationSeconds % 3600) / 60
                        val secs = state.recordedTrekDurationSeconds % 60
                        val timeStr = if (hours > 0) {
                            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)
                        } else {
                            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
                        }
                        Text(
                            text = timeStr,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Distance: ${state.recordedTrekDistanceKm} km",
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "Points: ${state.liveRecordedTrackCoordinates.size}",
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "GPS: ${state.gpsSignalQuality}",
                            fontSize = 12.sp,
                            color = if (state.gpsSignalQuality.contains("POOR")) Color(0xFFFCA5A5) else Color(0xFF86EFAC)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.isTrekPaused) {
                            Button(
                                onClick = { viewModel.resumeLiveTrekRecording() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.pauseLiveTrekRecording() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause")
                            }
                        }

                        Button(
                            onClick = { showStopTrekDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Finish & Save")
                        }
                    }
                }
            }

            if (showStopTrekDialog) {
                var trekTitle by remember { mutableStateOf("Recorded Trek ${java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US).format(java.util.Date())}") }
                AlertDialog(
                    onDismissRequest = { showStopTrekDialog = false },
                    title = { Text("Save Recorded Trek", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Total distance: ${state.recordedTrekDistanceKm} km with ${state.liveRecordedTrackCoordinates.size} GPS points.")
                            OutlinedTextField(
                                value = trekTitle,
                                onValueChange = { trekTitle = it },
                                label = { Text("Trek Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            showStopTrekDialog = false
                            viewModel.stopAndSaveLiveTrek(trekTitle)
                        }) {
                            Text("Save Trek")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStopTrekDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
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
                .background(Color.White.copy(alpha = 0.88f))
                .clickable { viewModel.toggleBasemapSelector(true) }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${state.basemapMode.displayName} • ${state.basemapMode.attribution} • NASA FIRMS • DDMA",
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray,
                fontSize = 9.sp
            )
        }

        // 5. BOTTOM SHEETS & DIALOGS

        // Basemap Style Selector Dialog
        if (state.showBasemapSelector) {
            BasemapSelectorDialog(
                currentMode = state.basemapMode,
                isOffline = state.isOfflineMode,
                onSelectMode = { viewModel.setBasemapMode(it) },
                onDismiss = { viewModel.toggleBasemapSelector(false) }
            )
        }

        // A. Selected Target / Feature Detail Bottom Sheet
        if (state.selectedTarget != null) {
            PointDetailsBottomSheet(
                uiState = state,
                onDismiss = { viewModel.clearSelection() },
                onSelectHazard = { viewModel.selectFeature(it) }
            )
        } else if (state.selectedFeature != null) {
            FeatureDetailBottomSheet(
                feature = state.selectedFeature!!,
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
                onDismiss = { viewModel.toggleLayersSheet(false) },
                currentBasemapMode = state.basemapMode,
                onSelectBasemapMode = { viewModel.setBasemapMode(it) }
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
        shape = ExpressiveShapes.largeIncreased,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

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
                    style = MaterialTheme.typography.titleMediumEmphasized,
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
    currentBasemapMode: BasemapMode = BasemapMode.STREETS_TRAILS,
    isDrawing: Boolean = false,
    isRecording: Boolean = false,
    onRecenter: () -> Unit,
    onFitRoute: () -> Unit,
    onOpenBasemapSelector: () -> Unit = {},
    onOpenLayers: () -> Unit,
    onToggleOffline: () -> Unit,
    onReportTrailCondition: () -> Unit = {},
    onStartDrawing: () -> Unit = {},
    onStartRecording: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isDrawing && !isRecording) {
            FloatingActionButton(
                onClick = onStartDrawing,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.Gesture, contentDescription = "Draw Route", modifier = Modifier.size(20.dp))
            }

            FloatingActionButton(
                onClick = onStartRecording,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = Color(0xFFD32F2F),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = "Record Trek", modifier = Modifier.size(20.dp))
            }
        }
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
            onClick = onOpenBasemapSelector,
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = when (currentBasemapMode) {
                BasemapMode.STREETS_TRAILS -> Color(0xFF2E7D32)
                BasemapMode.SATELLITE -> Color(0xFF1565C0)
                BasemapMode.HYBRID -> Color(0xFFF57C00)
            },
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(
                imageVector = when (currentBasemapMode) {
                    BasemapMode.STREETS_TRAILS -> Icons.Default.Terrain
                    BasemapMode.SATELLITE -> Icons.Default.SatelliteAlt
                    BasemapMode.HYBRID -> Icons.Default.Map
                },
                contentDescription = "Basemap Mode (${currentBasemapMode.displayName})",
                modifier = Modifier.size(20.dp)
            )
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
            MapLibreLayerManager.setupSourcesAndLayers(style, context, isSatellite = state.basemapMode.isSatellite)
            MapLibreLayerManager.updateData(style, state, context, isSatellite = state.basemapMode.isSatellite)
        } catch (t: Throwable) {
            android.util.Log.e("MapScreen", "Vector layers update failed safely: ${t.message}")
        }

        // High-performance selective marker update: avoids clearing all annotations and reallocating bitmaps
        try {
            if (state.isUserLocationAcquired) {
                val userLatLng = LatLng(state.userLocation.first, state.userLocation.second)
                val acc = state.userLocationAccuracyMeters?.toInt()?.let { " (±${it}m)" } ?: ""
                val existing = mapboxMap.markers.firstOrNull { it.snippet == "GPS Location" }
                if (existing != null) {
                    existing.position = userLatLng
                    existing.title = "Your Current Location$acc"
                } else {
                    mapboxMap.addMarker(
                        MarkerOptions()
                            .position(userLatLng)
                            .title("Your Current Location$acc")
                            .snippet("GPS Location")
                    )
                }
            }

            if (state.routeCoordinates.size >= 2) {
                val startCoord = state.routeCoordinates.first()
                val destCoord = state.routeCoordinates.last()
                val startLatLng = LatLng(startCoord.first, startCoord.second)
                val destLatLng = LatLng(destCoord.first, destCoord.second)

                val existingStart = mapboxMap.markers.firstOrNull { it.title == "Route Start" }
                if (existingStart != null) {
                    if (existingStart.position != startLatLng) existingStart.position = startLatLng
                } else {
                    mapboxMap.addMarker(
                        MarkerOptions()
                            .position(startLatLng)
                            .title("Route Start")
                            .snippet(state.routeTitle)
                    )
                }

                val existingDest = mapboxMap.markers.firstOrNull { it.title == "Route Destination" }
                if (existingDest != null) {
                    if (existingDest.position != destLatLng) existingDest.position = destLatLng
                } else {
                    mapboxMap.addMarker(
                        MarkerOptions()
                            .position(destLatLng)
                            .title("Route Destination")
                            .snippet(state.routeTitle)
                    )
                }
            } else {
                val toRemove = mapboxMap.markers.filter { it.title == "Route Start" || it.title == "Route Destination" }
                toRemove.forEach { mapboxMap.removeMarker(it) }
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

            // 1b. Draw Waterways, River Channels & Flood Inundation polygons
            features.filter { it.category == MapFeatureCategory.WATERWAY }.forEach { water ->
                water.geometryPolygon?.let { coords ->
                    if (coords.size >= 3) {
                        val path = Path()
                        coords.forEachIndexed { idx, (lat, lon) ->
                            val pt = project(lat, lon)
                            if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                        }
                        path.close()
                        val isFlood = water.details["category"] == "flood_inundation_zone" || water.title.contains("Flood", ignoreCase = true)
                        val fillColor = if (isFlood) Color(0x440097A7) else Color(0x440288D1)
                        val strokeColor = if (isFlood) Color(0xFF00796B) else Color(0xFF01579B)
                        drawPath(path = path, color = fillColor)
                        drawPath(path = path, color = strokeColor, style = Stroke(width = 2.5f))
                    }
                }
            }

            // 2. Draw Hazard Zones (distinguish Flood Inundation from general hazards)
            features.filter { it.category == MapFeatureCategory.HAZARD_ZONE }.forEach { hazard ->
                hazard.geometryPolygon?.let { coords ->
                    if (coords.size >= 3) {
                        val path = Path()
                        coords.forEachIndexed { idx, (lat, lon) ->
                            val pt = project(lat, lon)
                            if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                        }
                        path.close()
                        val isFloodHazard = hazard.details["Category"]?.contains("FLOOD", ignoreCase = true) == true ||
                            hazard.details["hazard_category"]?.contains("flood", ignoreCase = true) == true ||
                            hazard.title.contains("Flood", ignoreCase = true)
                        val hFillColor = if (isFloodHazard) Color(0x440097A7) else Color(0x33C62828)
                        val hStrokeColor = if (isFloodHazard) Color(0xFF00796B) else Color(0xFFE53935)
                        drawPath(path = path, color = hFillColor)
                        drawPath(path = path, color = hStrokeColor, style = Stroke(width = 3f))
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
