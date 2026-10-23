package com.touristsafety.app.feature.map

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.touristsafety.app.core.map.MapConfig
import com.touristsafety.app.core.model.FireHotspot
import com.touristsafety.app.core.model.ForestFeature
import com.touristsafety.app.core.model.MapPoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showOfflineSchematic by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selectedItinerary?.title ?: "Explore Route Map", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refreshLayers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Layers")
                    }
                    IconButton(onClick = { showOfflineSchematic = !showOfflineSchematic }) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Toggle Schematic Mode",
                            tint = if (showOfflineSchematic) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (showOfflineSchematic || state.isOfflineMode) {
                // Offline Route Schematic Canvas with Forest Polygons & Fire Hotspots
                OfflineRouteSchematicView(
                    coordinates = state.routeCoordinates,
                    mapPoints = if (state.showEmergencyLayer) state.mapPoints else emptyList(),
                    forests = if (state.showForestLayer) state.forestFeatures else emptyList(),
                    fires = if (state.showFireLayer) state.fireHotspots else emptyList(),
                    onPointClicked = { viewModel.selectPoint(it) },
                    onForestClicked = { viewModel.selectForest(it) },
                    onFireClicked = { viewModel.selectFire(it) }
                )
            } else {
                // MapLibre Native Vector Map with OpenFreeMap Liberty Style
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            getMapAsync { mapboxMap ->
                                mapboxMap.setStyle(
                                    Style.Builder().fromUri(MapConfig.DEFAULT_STYLE_URL)
                                ) {
                                    val startLat = state.selectedItinerary?.startLatitude ?: MapConfig.DEFAULT_LATITUDE
                                    val startLon = state.selectedItinerary?.startLongitude ?: MapConfig.DEFAULT_LONGITUDE
                                    val cameraPosition = CameraPosition.Builder()
                                        .target(LatLng(startLat, startLon))
                                        .zoom(MapConfig.DEFAULT_ZOOM)
                                        .build()
                                    mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Layer Visibility Chips Header
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.showForestLayer,
                    onClick = { viewModel.toggleForestLayer() },
                    label = { Text("Forest Cover (${state.forestFeatures.size})") },
                    leadingIcon = { Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = state.showFireLayer,
                    onClick = { viewModel.toggleFireLayer() },
                    label = { Text("Active Fires (${state.fireHotspots.size})") },
                    leadingIcon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = state.showEmergencyLayer,
                    onClick = { viewModel.toggleEmergencyLayer() },
                    label = { Text("Assistance (${state.mapPoints.size})") },
                    leadingIcon = { Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            // Map Attribution Pill (OpenFreeMap, OpenStreetMap, NASA FIRMS)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.88f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "© OpenFreeMap | © OpenStreetMap | NASA FIRMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray
                )
            }
        }

        // 1. Map Point Detail Bottom Sheet
        state.selectedPoint?.let { pt ->
            PointDetailBottomSheet(
                point = pt,
                onDismiss = { viewModel.selectPoint(null) }
            )
        }

        // 2. Forest Feature Detail Bottom Sheet
        state.selectedForest?.let { forest ->
            ForestDetailBottomSheet(
                forest = forest,
                onDismiss = { viewModel.selectForest(null) }
            )
        }

        // 3. NASA FIRMS Fire Hotspot Detail Bottom Sheet
        state.selectedFire?.let { fire ->
            FireDetailBottomSheet(
                fire = fire,
                onDismiss = { viewModel.selectFire(null) }
            )
        }
    }
}

@Composable
fun OfflineRouteSchematicView(
    coordinates: List<Pair<Double, Double>>,
    mapPoints: List<MapPoint>,
    forests: List<ForestFeature>,
    fires: List<FireHotspot>,
    onPointClicked: (MapPoint) -> Unit,
    onForestClicked: (ForestFeature) -> Unit,
    onFireClicked: (FireHotspot) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF263238))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFFB300))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Offline Route Vector Schematic & Layers",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
        }

        // Draw Route Geometry Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1E272C), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            val allLats = coordinates.map { it.first } + forests.flatMap { f -> f.coordinates.map { it.first } } + fires.map { it.latitude }
            val allLons = coordinates.map { it.second } + forests.flatMap { f -> f.coordinates.map { it.second } } + fires.map { it.longitude }

            val minLat = allLats.minOrNull() ?: 32.20
            val maxLat = allLats.maxOrNull() ?: 32.30
            val minLon = allLons.minOrNull() ?: 77.15
            val maxLon = allLons.maxOrNull() ?: 77.25

            val latSpan = (maxLat - minLat).coerceAtLeast(0.01)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.01)

            fun project(lat: Double, lon: Double): Offset {
                val x = ((lon - minLon) / lonSpan * (size.width - 60f) + 30f).toFloat()
                val y = ((maxLat - lat) / latSpan * (size.height - 60f) + 30f).toFloat()
                return Offset(x, y)
            }

            // 1. Draw Forest polygons
            forests.forEach { forest ->
                if (forest.coordinates.size >= 3) {
                    val forestPath = Path()
                    forest.coordinates.forEachIndexed { idx, (lat, lon) ->
                        val pt = project(lat, lon)
                        if (idx == 0) forestPath.moveTo(pt.x, pt.y) else forestPath.lineTo(pt.x, pt.y)
                    }
                    forestPath.close()
                    drawPath(path = forestPath, color = Color(0x332E7D32)) // translucent green
                    drawPath(path = forestPath, color = Color(0xFF4CAF50), style = Stroke(width = 3f))
                }
            }

            // 2. Draw Trail line
            if (coordinates.size >= 2) {
                val trailPath = Path()
                coordinates.forEachIndexed { index, (lat, lon) ->
                    val pt = project(lat, lon)
                    if (index == 0) trailPath.moveTo(pt.x, pt.y) else trailPath.lineTo(pt.x, pt.y)
                }
                drawPath(path = trailPath, color = Color(0xFF4FC3F7), style = Stroke(width = 8f))

                val startPt = project(coordinates.first().first, coordinates.first().second)
                drawCircle(color = Color(0xFF66BB6A), radius = 12f, center = startPt)

                val endPt = project(coordinates.last().first, coordinates.last().second)
                drawCircle(color = Color(0xFFEF5350), radius = 12f, center = endPt)
            }

            // 3. Draw Active Fire Hotspots
            fires.forEach { fire ->
                val firePt = project(fire.latitude, fire.longitude)
                drawCircle(color = Color(0xFFD32F2F), radius = 14f, center = firePt)
                drawCircle(color = Color(0xFFFF9800), radius = 8f, center = firePt)
            }

            // 4. Draw Assistance Points
            mapPoints.forEach { pt ->
                val ptOffset = project(pt.latitude, pt.longitude)
                drawCircle(color = Color(0xFF0288D1), radius = 10f, center = ptOffset)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Layer Pills at Bottom
        if (forests.isNotEmpty()) {
            Text("Mapped Forest Covers (OSM):", color = Color(0xFF81C784), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                forests.forEach { f ->
                    Surface(
                        color = Color(0xFF1B5E20),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onForestClicked(f) }
                    ) {
                        Text(f.name, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }

        if (fires.isNotEmpty()) {
            Text("Active Wildfire Hotspots (NASA FIRMS):", color = Color(0xFFFF8A65), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                fires.forEach { fire ->
                    Surface(
                        color = Color(0xFFB71C1C),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onFireClicked(fire) }
                    ) {
                        Text("Hotspot: ${fire.brightness}K (${fire.satellite})", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestDetailBottomSheet(
    forest: ForestFeature,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Park, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(forest.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Type: ${forest.woodType.capitalize()} woodland", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Source: OpenStreetMap Contributors under Open Database License (ODbL).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Notice: Mapped forest boundaries provide geographic context. They do not prove current ground conditions, accessibility, or absence of hazards.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireDetailBottomSheet(
    fire: FireHotspot,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Satellite Thermal Anomaly", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Sensor: ${fire.satellite} (${fire.confidence} confidence)", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Brightness Temperature: ${fire.brightness} Kelvin", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Fire Radiative Power: ${fire.frpMw} MW", fontSize = 13.sp)
            Text("Acquisition: ${fire.acquisitionDate} ${fire.acquisitionTime} UTC", fontSize = 13.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Disclaimer: NASA FIRMS thermal anomaly detections reflect heat signatures observed from orbit. They do not confirm actively spreading ground fires and may be obscured by cloud cover.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

private fun String.capitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
