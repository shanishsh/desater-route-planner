package com.touristsafety.app.feature.map

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.touristsafety.app.core.map.MapConfig
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var showOfflineSchematic by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selectedItinerary?.title ?: "Explore Route Map") },
                actions = {
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
                // Offline Route Schematic Canvas
                OfflineRouteSchematicView(
                    coordinates = state.routeCoordinates,
                    mapPoints = state.mapPoints,
                    onPointClicked = { viewModel.selectPoint(it) }
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

            // Map Attribution Pill (OpenFreeMap & OpenStreetMap)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = MapConfig.ATTRIBUTION,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray
                )
            }

            // Quick Assistance Overlay Chips
            if (state.mapPoints.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    state.mapPoints.take(3).forEach { pt ->
                        AssistChip(
                            onClick = { viewModel.selectPoint(pt) },
                            label = { Text(pt.name.take(16) + "...", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        // Marker / Point Detail BottomSheet
        state.selectedPoint?.let { pt ->
            PointDetailBottomSheet(
                point = pt,
                onDismiss = { viewModel.selectPoint(null) }
            )
        }
    }
}

@Composable
fun OfflineRouteSchematicView(
    coordinates: List<Pair<Double, Double>>,
    mapPoints: List<MapPoint>,
    onPointClicked: (MapPoint) -> Unit
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
                text = "Offline Route Vector Schematic (No Tiles Required)",
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
                .background(Color(0xFF1E272C), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            if (coordinates.size >= 2) {
                val minLat = coordinates.minOf { it.first }
                val maxLat = coordinates.maxOf { it.first }
                val minLon = coordinates.minOf { it.second }
                val maxLon = coordinates.maxOf { it.second }

                val latSpan = (maxLat - minLat).coerceAtLeast(0.001)
                val lonSpan = (maxLon - minLon).coerceAtLeast(0.001)

                val path = Path()
                coordinates.forEachIndexed { index, (lat, lon) ->
                    val x = ((lon - minLon) / lonSpan * (size.width - 60f) + 30f).toFloat()
                    val y = ((maxLat - lat) / latSpan * (size.height - 60f) + 30f).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                // Draw trail path line
                drawPath(
                    path = path,
                    color = Color(0xFF4FC3F7),
                    style = Stroke(width = 8f)
                )

                // Draw start and end points
                val startX = ((coordinates.first().second - minLon) / lonSpan * (size.width - 60f) + 30f).toFloat()
                val startY = ((maxLat - coordinates.first().first) / latSpan * (size.height - 60f) + 30f).toFloat()
                drawCircle(color = Color(0xFF66BB6A), radius = 12f, center = Offset(startX, startY))

                val endX = ((coordinates.last().second - minLon) / lonSpan * (size.width - 60f) + 30f).toFloat()
                val endY = ((maxLat - coordinates.last().first) / latSpan * (size.height - 60f) + 30f).toFloat()
                drawCircle(color = Color(0xFFEF5350), radius = 12f, center = Offset(endX, endY))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("Verified Assistance on Route:", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mapPoints.take(3).forEach { pt ->
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onPointClicked(pt) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF37474F))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(pt.name, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Text(pt.category.name, color = Color(0xFF81D4FA), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

