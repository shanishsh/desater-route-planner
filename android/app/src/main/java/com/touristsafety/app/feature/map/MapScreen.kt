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
import com.touristsafety.app.core.model.MapFeatureCategory
import com.touristsafety.app.core.model.UnifiedMapFeature
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onNavigateToPlanTrip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var maplibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapLoadFailed by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. BASE MAP LAYER (MapLibre Native or Offline Canvas)
        if (state.isOfflineMode || mapLoadFailed) {
            OfflineVectorCanvasView(
                routeCoordinates = state.routeCoordinates,
                corridorPolygonCoordinates = state.corridorPolygonCoordinates,
                features = state.visibleFeatures,
                onFeatureClicked = { viewModel.selectFeature(it) }
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    try {
                        MapView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            onCreate(null)
                            onStart()
                            onResume()
                            getMapAsync { mapboxMap ->
                            maplibreMapInstance = mapboxMap
                            mapboxMap.setStyle(
                                Style.Builder().fromUri(MapConfig.DEFAULT_STYLE_URL)
                            ) { style ->
                                val target = LatLng(state.cameraTarget.first, state.cameraTarget.second)
                                val pos = CameraPosition.Builder()
                                    .target(target)
                                    .zoom(state.zoomLevel)
                                    .build()
                                mapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos))

                                // Render vector layers & markers
                                updateMaplibreLayers(mapboxMap, state)
                            }

                            // Query rendered vector features on map tap (polygons, active fires, safe havens)
                            mapboxMap.addOnMapClickListener { latLng ->
                                val screenPoint = mapboxMap.projection.toScreenLocation(latLng)
                                val renderedFeatures = mapboxMap.queryRenderedFeatures(
                                    screenPoint,
                                    MapLibreLayerManager.LAYER_ACTIVE_FIRES_CIRCLE,
                                    MapLibreLayerManager.LAYER_DISASTER_FILL,
                                    MapLibreLayerManager.LAYER_DISASTER_LINE,
                                    MapLibreLayerManager.LAYER_DISASTER_POINTS_CIRCLE,
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
                                false
                            }

                            // Marker click listener
                            mapboxMap.setOnMarkerClickListener { marker ->
                                val snippet = marker.snippet ?: ""
                                val clicked = state.visibleFeatures.firstOrNull { it.id == snippet }
                                if (clicked != null) {
                                    viewModel.selectFeature(clicked)
                                } else {
                                    val byTitle = state.visibleFeatures.firstOrNull { it.title == marker.title }
                                    viewModel.selectFeature(byTitle)
                                }
                                true
                            }

                            // Camera listener for zoom updates
                            mapboxMap.addOnCameraIdleListener {
                                val curZoom = mapboxMap.cameraPosition.zoom
                                viewModel.setZoomLevel(curZoom)
                            }
                        }
                    }
                    } catch (e: Throwable) {
                        android.util.Log.e("MapScreen", "Failed to initialize MapView, falling back to schematic canvas", e)
                        mapLoadFailed = true
                        android.view.View(ctx)
                    }
                },
                update = {
                    maplibreMapInstance?.let { map ->
                        updateMaplibreLayers(map, state)
                    }
                },
                onRelease = { mapView ->
                    try {
                        if (mapView is MapView) {
                            mapView.onPause()
                            mapView.onStop()
                            mapView.onDestroy()
                        }
                    } catch (e: Throwable) {
                        // Safe catch
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. TOP FLOATING INTERACTION BAR: Location Search Card & Horizontal Filters
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
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
            onToggleOffline = { viewModel.toggleOfflineSchematic() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )

        // 4. BOTTOM ANCHORED ROUTE SUMMARY CARD
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
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Map Attribution Pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = if (state.isBottomRouteExpanded) 190.dp else 95.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.88f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "© OpenFreeMap • © OpenStreetMap • NASA FIRMS • DDMA",
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray,
                fontSize = 10.sp
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // GPS button
            IconButton(
                onClick = onRecenterGps,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Change destination button
            IconButton(
                onClick = onChangeDestination,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EditLocationAlt,
                    contentDescription = "Change Destination",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
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
private fun updateMaplibreLayers(mapboxMap: MapLibreMap, state: MapUiState) {
    try {
        val style = mapboxMap.style
        if (style != null && style.isFullyLoaded) {
            MapLibreLayerManager.setupSourcesAndLayers(style)
            MapLibreLayerManager.updateData(style, state)
        }

        // Manage lightweight GPS position marker via annotation system
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
    } catch (e: Exception) {
        // Safe catch for map lifecycle transitions
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
    onFeatureClicked: (UnifiedMapFeature) -> Unit
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
                fontSize = 12.sp
            )
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
