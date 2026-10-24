package com.touristsafety.app.feature.trip

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.touristsafety.app.core.map.MapConfig
import com.touristsafety.app.core.network.dto.PlaceSearchResultDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapLocationPickerDialog(
    title: String,
    initialLat: Double?,
    initialLon: Double?,
    onConfirmLocation: (lat: Double, lon: Double, displayName: String) -> Unit,
    onDismiss: () -> Unit,
    reverseGeocode: (suspend (Double, Double) -> Result<PlaceSearchResultDto>)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var reverseGeocodeJob by remember { mutableStateOf<Job?>(null) }

    val defaultLat = initialLat?.takeIf { it != 0.0 && it in -90.0..90.0 } ?: 30.3165
    val defaultLon = initialLon?.takeIf { it != 0.0 && it in -180.0..180.0 } ?: 78.0322

    var currentLat by remember { mutableDoubleStateOf(defaultLat) }
    var currentLon by remember { mutableDoubleStateOf(defaultLon) }
    var resolvedName by remember { mutableStateOf("Locating selected point...") }
    var isReverseGeocoding by remember { mutableStateOf(false) }

    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    fun triggerReverseGeocode(lat: Double, lon: Double) {
        reverseGeocodeJob?.cancel()
        if (reverseGeocode == null) {
            resolvedName = String.format(java.util.Locale.US, "Selected Pin (%.4f, %.4f)", lat, lon)
            return
        }
        reverseGeocodeJob = coroutineScope.launch {
            isReverseGeocoding = true
            delay(400) // debounce while dragging
            val result = reverseGeocode(lat, lon)
            result.fold(
                onSuccess = { dto ->
                    resolvedName = dto.display_name.ifBlank {
                        String.format(java.util.Locale.US, "Point (%.4f, %.4f)", lat, lon)
                    }
                    isReverseGeocoding = false
                },
                onFailure = {
                    resolvedName = String.format(java.util.Locale.US, "Selected Point (%.4f, %.4f)", lat, lon)
                    isReverseGeocoding = false
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        triggerReverseGeocode(defaultLat, defaultLon)
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mapViewInstance?.onPause()
                mapViewInstance?.onStop()
                mapViewInstance?.onDestroy()
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. MapLibre Native MapView
            AndroidView(
                factory = { ctx ->
                    try {
                        MapLibre.getInstance(ctx)
                    } catch (_: Throwable) {}

                    val options = try {
                        MapLibreMapOptions.createFromAttributes(ctx).apply {
                            textureMode(true)
                        }
                    } catch (_: Throwable) {
                        MapLibreMapOptions().apply {
                            textureMode(true)
                        }
                    }

                    MapView(ctx, options).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        onCreate(null)
                        onStart()
                        onResume()
                        mapViewInstance = this

                        addOnDidFailLoadingMapListener { errorMessage ->
                            android.util.Log.w("MapLocationPicker", "Map loading failed: $errorMessage; applying fallback style")
                            try {
                                getMapAsync { map ->
                                    if (map.style?.isFullyLoaded != true) {
                                        map.setStyle(Style.Builder().fromJson(MapConfig.FALLBACK_STYLE_JSON)) { fallbackStyle ->
                                            maplibreMap = map
                                            val target = LatLng(defaultLat, defaultLon)
                                            val pos = CameraPosition.Builder()
                                                .target(target)
                                                .zoom(11.0)
                                                .build()
                                            map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }

                        getMapAsync { map ->
                            try {
                                map.setStyle(
                                    Style.Builder().fromJson(MapConfig.OSM_RASTER_STYLE_JSON)
                                ) {
                                    try {
                                        maplibreMap = map
                                        val target = LatLng(defaultLat, defaultLon)
                                        val pos = CameraPosition.Builder()
                                            .target(target)
                                            .zoom(11.0)
                                            .build()
                                        map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
                                    } catch (_: Throwable) {}
                                }

                                map.addOnCameraMoveListener {
                                    try {
                                        val target = map.cameraPosition?.target ?: return@addOnCameraMoveListener
                                        currentLat = target.latitude
                                        currentLon = target.longitude
                                    } catch (_: Throwable) {}
                                }

                                map.addOnCameraIdleListener {
                                    try {
                                        val target = map.cameraPosition?.target ?: return@addOnCameraIdleListener
                                        currentLat = target.latitude
                                        currentLon = target.longitude
                                        triggerReverseGeocode(target.latitude, target.longitude)
                                    } catch (_: Throwable) {}
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Center Pin Crosshair
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 36.dp), // offset pin tip to dead center
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Target Pin",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(42.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                    )
                }
            }

            // 3. Top Header Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Pan and zoom the map to position the center pin",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 4. Bottom Selection Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected Coordinates",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.5f, %.5f", currentLat, currentLon),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = resolvedName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        if (isReverseGeocoding) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                onConfirmLocation(currentLat, currentLon, resolvedName)
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirm Pin", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

