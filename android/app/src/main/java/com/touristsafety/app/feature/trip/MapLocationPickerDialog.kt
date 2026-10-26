package com.touristsafety.app.feature.trip

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.touristsafety.app.core.spatial.UttarakhandBoundaryValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

@Composable
fun MapLocationPickerDialog(
    title: String,
    initialLat: Double?,
    initialLon: Double?,
    boundaryValidator: UttarakhandBoundaryValidator? = null,
    onConfirmLocation: (lat: Double, lon: Double, displayName: String) -> Unit,
    onDismiss: () -> Unit,
    reverseGeocode: (suspend (Double, Double) -> Result<PlaceSearchResultDto>)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var reverseGeocodeJob by remember { mutableStateOf<Job?>(null) }

    val defaultLat = initialLat?.takeIf { it != 0.0 && it in -90.0..90.0 } ?: 30.1500
    val defaultLon = initialLon?.takeIf { it != 0.0 && it in -180.0..180.0 } ?: 79.2500

    var currentLat by remember { mutableDoubleStateOf(defaultLat) }
    var currentLon by remember { mutableDoubleStateOf(defaultLon) }
    var resolvedName by remember { mutableStateOf("Locating selected point...") }
    var isReverseGeocoding by remember { mutableStateOf(false) }

    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    val isInUttarakhand = remember(currentLat, currentLon) {
        boundaryValidator?.isPointInUttarakhand(currentLat, currentLon) ?: true
    }

    val quickTowns = remember {
        listOf(
            "Almora" to (29.5971 to 79.6591),
            "Nainital" to (29.3919 to 79.4542),
            "Kedarnath" to (30.7346 to 79.0669),
            "Badrinath" to (30.7433 to 79.4938),
            "Rishikesh" to (30.0869 to 78.2676),
            "Dehradun" to (30.3165 to 78.0322),
            "Mussoorie" to (30.4598 to 78.0700),
            "Haridwar" to (29.9457 to 78.1642),
            "Ranikhet" to (29.6434 to 79.4322),
            "Haldwani" to (29.2183 to 79.5130),
            "Joshimath / Auli" to (30.5564 to 79.5662),
            "Uttarkashi" to (30.7268 to 78.4354),
            "Pithoragarh" to (29.5829 to 80.2182),
            "Munsyari" to (30.0673 to 80.2372),
            "Chopta" to (30.4853 to 79.1764),
            "Kausani" to (29.8542 to 79.5967)
        )
    }

    fun triggerReverseGeocode(lat: Double, lon: Double) {
        reverseGeocodeJob?.cancel()
        if (reverseGeocode == null) {
            resolvedName = String.format(java.util.Locale.US, "Selected Pin (%.4f, %.4f)", lat, lon)
            return
        }
        reverseGeocodeJob = coroutineScope.launch {
            isReverseGeocoding = true
            delay(350) // debounce while dragging
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
                        MapLibreMapOptions.createFromAttributes(ctx)
                    } catch (_: Throwable) {
                        MapLibreMapOptions()
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
                                            val bounds = LatLngBounds.from(
                                                MapConfig.UTTARAKHAND_MAX_LAT + 0.35,
                                                MapConfig.UTTARAKHAND_MAX_LON + 0.35,
                                                MapConfig.UTTARAKHAND_MIN_LAT - 0.35,
                                                MapConfig.UTTARAKHAND_MIN_LON - 0.35
                                            )
                                            map.setLatLngBoundsForCameraTarget(bounds)
                                            map.setMinZoomPreference(6.5)
                                            map.setMaxZoomPreference(18.0)
                                            val target = LatLng(defaultLat, defaultLon)
                                            val pos = CameraPosition.Builder()
                                                .target(target)
                                                .zoom(10.5)
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
                                ) { loadedStyle ->
                                    try {
                                        maplibreMap = map

                                        // 1. Outside State Mask (dimming outside regions so Uttarakhand is crystal clear)
                                        try {
                                            ctx.assets.open("uttarakhand_mask.geojson").use { stream ->
                                                val maskText = stream.reader().readText()
                                                if (loadedStyle.getSource("picker-mask-src") == null) {
                                                    loadedStyle.addSource(GeoJsonSource("picker-mask-src", maskText))
                                                    val maskFill = FillLayer("picker-mask-layer", "picker-mask-src").apply {
                                                        setProperties(
                                                            PropertyFactory.fillColor("#0D1117"),
                                                            PropertyFactory.fillOpacity(0.40f),
                                                            PropertyFactory.fillAntialias(true)
                                                        )
                                                    }
                                                    loadedStyle.addLayer(maskFill)
                                                }
                                            }
                                        } catch (e: Throwable) {
                                            android.util.Log.w("MapLocationPicker", "Could not add boundary mask: ${e.message}")
                                        }

                                        // 2. Authoritative Boundary Perimeter Line (Vivid Emerald Green)
                                        try {
                                            ctx.assets.open("uttarakhand_boundary.geojson").use { stream ->
                                                val bndText = stream.reader().readText()
                                                if (loadedStyle.getSource("picker-bnd-src") == null) {
                                                    loadedStyle.addSource(GeoJsonSource("picker-bnd-src", bndText))
                                                    val bndLine = LineLayer("picker-bnd-layer", "picker-bnd-src").apply {
                                                        setProperties(
                                                            PropertyFactory.lineColor("#2E7D32"),
                                                            PropertyFactory.lineWidth(3.5f),
                                                            PropertyFactory.lineOpacity(0.95f)
                                                        )
                                                    }
                                                    loadedStyle.addLayer(bndLine)
                                                }
                                            }
                                        } catch (e: Throwable) {
                                            android.util.Log.w("MapLocationPicker", "Could not add boundary perimeter: ${e.message}")
                                        }

                                        val bounds = LatLngBounds.from(
                                            MapConfig.UTTARAKHAND_MAX_LAT + 0.35,
                                            MapConfig.UTTARAKHAND_MAX_LON + 0.35,
                                            MapConfig.UTTARAKHAND_MIN_LAT - 0.35,
                                            MapConfig.UTTARAKHAND_MIN_LON - 0.35
                                        )
                                        map.setLatLngBoundsForCameraTarget(bounds)
                                        map.setMinZoomPreference(6.5)
                                        map.setMaxZoomPreference(18.0)
                                        val target = LatLng(defaultLat, defaultLon)
                                        val pos = CameraPosition.Builder()
                                            .target(target)
                                            .zoom(10.5)
                                            .build()
                                        map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
                                    } catch (_: Throwable) {}
                                }

                                // Tap to select position directly on map
                                map.addOnMapClickListener { latLng ->
                                    currentLat = latLng.latitude
                                    currentLon = latLng.longitude
                                    map.animateCamera(CameraUpdateFactory.newLatLng(latLng), 300)
                                    triggerReverseGeocode(latLng.latitude, latLng.longitude)
                                    true
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
                        tint = if (isInUttarakhand) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(44.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape)
                    )
                }
            }

            // 3. Top Header Bar with Quick Town Jump Chips
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Tap anywhere on the map or drag to place the pin",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Quick Jump Town Chips
                    Text(
                        text = "Quick jump to destination:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        items(quickTowns) { (name, coords) ->
                            SuggestionChip(
                                onClick = {
                                    val pt = LatLng(coords.first, coords.second)
                                    maplibreMap?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(pt, 12.5),
                                        350
                                    )
                                    currentLat = coords.first
                                    currentLon = coords.second
                                    triggerReverseGeocode(coords.first, coords.second)
                                },
                                label = { Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
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
                    // Boundary Status Banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isInUttarakhand) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isInUttarakhand) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isInUttarakhand) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isInUttarakhand) "Valid Point inside Uttarakhand" else "Outside Uttarakhand Boundary",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isInUttarakhand) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                modifier = Modifier.weight(1f)
                            )
                            if (!isInUttarakhand) {
                                TextButton(
                                    onClick = {
                                        val centerUK = LatLng(30.1500, 79.2500)
                                        maplibreMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(centerUK, 8.5), 400)
                                        currentLat = centerUK.latitude
                                        currentLon = centerUK.longitude
                                        triggerReverseGeocode(centerUK.latitude, centerUK.longitude)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Center on UK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

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
                            tint = if (isInUttarakhand) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
                                if (isInUttarakhand) {
                                    onConfirmLocation(currentLat, currentLon, resolvedName)
                                }
                            },
                            enabled = isInUttarakhand,
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
