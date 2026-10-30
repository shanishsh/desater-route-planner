package com.touristsafety.app.feature.history

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.touristsafety.app.core.designsystem.ForestGreenDark
import com.touristsafety.app.core.designsystem.ForestGreenPrimary
import com.touristsafety.app.core.map.MapConfig
import com.touristsafety.app.core.map.MapLibreLayerManager
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedTrekDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: CompletedTrekDetailViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var maplibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var isMapStyleReady by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                maplibreMapInstance = map
                try {
                    map.setStyle(Style.Builder().fromUri(MapConfig.DEFAULT_STYLE_URL)) { style ->
                        MapLibreLayerManager.setupSourcesAndLayers(style, context)
                        isMapStyleReady = true
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("CompletedTrekDetail", "Failed loading map style: ${t.message}", t)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            try {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            } catch (_: Exception) {}
        }
    }

    // Render Traveled Track and Planned Route on MapLibre when ready
    LaunchedEffect(isMapStyleReady, state.trackCoordinates, state.plannedRouteCoordinates) {
        if (!isMapStyleReady) return@LaunchedEffect
        val map = maplibreMapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect

        if (state.plannedRouteCoordinates.isNotEmpty()) {
            MapLibreLayerManager.updatePrimaryRoute(style, state.plannedRouteCoordinates)
        }
        if (state.trackCoordinates.isNotEmpty()) {
            MapLibreLayerManager.updateTraveledTrack(style, state.trackCoordinates)
        }

        // Fit camera to track bounds
        val allPoints = if (state.trackCoordinates.isNotEmpty()) {
            state.trackCoordinates
        } else {
            state.plannedRouteCoordinates
        }

        if (allPoints.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            allPoints.forEach { (lat, lon) ->
                boundsBuilder.include(LatLng(lat, lon))
            }
            try {
                val bounds = boundsBuilder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120), 1200)
            } catch (_: Exception) {}
        }
    }

    fun shareTrekSummary() {
        val shareText = buildString {
            appendLine("🏔️ Uttarakhand Trek Completed!")
            appendLine("Trek: ${state.routeTitle}")
            if (state.originName.isNotBlank() && state.destinationName.isNotBlank()) {
                appendLine("Route: ${state.originName} → ${state.destinationName}")
            }
            appendLine("Distance: ${String.format(Locale.US, "%.2f km", state.traveledDistanceKm)}")
            appendLine("Duration: ${state.durationFormatted}")
            appendLine("Recorded GPS Breadcrumbs: ${state.recordedPointsCount} points")
            if (state.startedAt.isNotBlank()) {
                appendLine("Date: ${state.startedAt.take(10)}")
            }
            appendLine("Monitored with TrailGuard Uttarakhand Safety Platform.")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Trek Summary: ${state.routeTitle}")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Share Trek Summary"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.routeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Historical GPS Track Inspection",
                            style = MaterialTheme.typography.labelSmall,
                            color = ForestGreenPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareTrekSummary() }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Trek Summary")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Embedded MapLibre View
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            // Bottom Summary Card Overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ForestGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "COMPLETED & VERIFIED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenPrimary
                            )
                        }

                        if (state.startedAt.isNotBlank()) {
                            Text(
                                text = state.startedAt.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.routeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (state.originName.isNotBlank() && state.destinationName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${state.originName} → ${state.destinationName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(Locale.US, "%.2f km", state.traveledDistanceKm),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenDark
                            )
                            Text("Distance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.durationFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${state.recordedPointsCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text("GPS Points", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { shareTrekSummary() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Trek Certificate & Track", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
