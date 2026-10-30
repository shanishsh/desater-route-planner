package com.touristsafety.app.feature.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.model.MapFeatureCategory
import com.touristsafety.app.core.model.SelectedMapTarget
import com.touristsafety.app.core.model.UnifiedMapFeature
import com.touristsafety.app.core.network.dto.NearbyFacilityDto
import com.touristsafety.app.core.network.dto.NearbyHazardDto
import com.touristsafety.app.core.network.dto.PointContextDto
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointDetailsBottomSheet(
    uiState: MapUiState,
    onDismiss: () -> Unit,
    onSelectHazard: ((UnifiedMapFeature) -> Unit)? = null
) {
    val context = LocalContext.current
    val target = uiState.selectedTarget ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Header Bar: Coordinate Readout & Title
            PointDetailsHeader(
                target = target,
                pointContext = uiState.pointContextData,
                onCopyCoordinates = { lat, lon ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Coordinates", "$lat, $lon"))
                    Toast.makeText(context, "Coordinates copied: $lat, $lon", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Content depending on Target Type
            when (target) {
                is SelectedMapTarget.RouteWeather -> {
                    RouteWeatherDetailSection(target = target)
                }
                is SelectedMapTarget.Hazard -> {
                    HazardDetailSection(feature = target.feature)
                }
                is SelectedMapTarget.HazardPolygon -> {
                    HazardDetailSection(feature = target.feature)
                }
                is SelectedMapTarget.Facility -> {
                    FacilityDetailSection(feature = target.feature)
                }
                is SelectedMapTarget.ArbitraryPoint -> {
                    ArbitraryPointContextSection(
                        isLoading = uiState.pointContextLoading,
                        error = uiState.pointContextError,
                        pointData = uiState.pointContextData,
                        onSelectHazard = onSelectHazard
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Action Buttons (Google Maps Navigation / Dismiss)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }

                Button(
                    onClick = {
                        val lat = target.latitude
                        val lon = target.longitude
                        val label = when (target) {
                            is SelectedMapTarget.RouteWeather -> target.name
                            is SelectedMapTarget.Hazard -> target.feature.title
                            is SelectedMapTarget.HazardPolygon -> target.feature.title
                            is SelectedMapTarget.Facility -> target.feature.title
                            is SelectedMapTarget.ArbitraryPoint -> uiState.pointContextData?.nearest_settlement ?: "Point Inspection"
                        }
                        val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri)
                            context.startActivity(fallbackIntent)
                        }
                    },
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Directions")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PointDetailsHeader(
    target: SelectedMapTarget,
    pointContext: PointContextDto?,
    onCopyCoordinates: (Double, Double) -> Unit
) {
    val (title, categoryBadge, icon, iconColor) = when (target) {
        is SelectedMapTarget.RouteWeather -> Quadruple(
            target.name,
            "Route Weather Sample",
            Icons.Default.WbSunny,
            Color(0xFF0288D1)
        )
        is SelectedMapTarget.Hazard -> Quadruple(
            target.feature.title,
            target.feature.category.displayName,
            target.feature.category.icon,
            target.feature.category.primaryColor
        )
        is SelectedMapTarget.HazardPolygon -> Quadruple(
            target.feature.title,
            target.feature.category.displayName,
            target.feature.category.icon,
            target.feature.category.primaryColor
        )
        is SelectedMapTarget.Facility -> Quadruple(
            target.feature.title,
            target.feature.category.displayName,
            target.feature.category.icon,
            target.feature.category.primaryColor
        )
        is SelectedMapTarget.ArbitraryPoint -> Quadruple(
            pointContext?.nearest_settlement ?: "Point Inspection",
            if (pointContext?.in_uttarakhand == false) "Outside Region" else "Uttarakhand Terrain",
            Icons.Default.LocationSearching,
            Color(0xFFF57C00)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = iconColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = categoryBadge.uppercase(Locale.US),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )

            // Coordinate Readout
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onCopyCoordinates(target.latitude, target.longitude) }
            ) {
                Text(
                    text = String.format(Locale.US, "%.5f°N, %.5f°E", target.latitude, target.longitude),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy Coordinates",
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RouteWeatherDetailSection(target: SelectedMapTarget.RouteWeather) {
    val w = target.weather
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Atmospheric Forecast for Waypoint",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailMetric(
                    label = "Temperature",
                    value = "${w.temperature_c}°C",
                    icon = Icons.Default.Thermostat,
                    color = Color(0xFFD32F2F)
                )
                DetailMetric(
                    label = "Conditions",
                    value = w.weather_description,
                    icon = Icons.Default.Cloud,
                    color = Color(0xFF0288D1)
                )
                DetailMetric(
                    label = "Rainfall",
                    value = "${w.precipitation_mm} mm",
                    icon = Icons.Default.WaterDrop,
                    color = Color(0xFF00796B)
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailMetric(
                    label = "Wind Speed",
                    value = "${w.wind_speed_kmh} km/h",
                    icon = Icons.Default.Air,
                    color = Color(0xFF5D4037)
                )
                DetailMetric(
                    label = "Rain Probability",
                    value = "${w.precipitation_probability_pct.toInt()}%",
                    icon = Icons.Default.Umbrella,
                    color = Color(0xFF1976D2)
                )
                DetailMetric(
                    label = "Along Route",
                    value = String.format(Locale.US, "%.1f km", target.distanceAlongRouteKm),
                    icon = Icons.Default.Straighten,
                    color = Color(0xFF7B1FA2)
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Source: ${w.source} • Valid: ${w.forecast_valid_at}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ArbitraryPointContextSection(
    isLoading: Boolean,
    error: String?,
    pointData: PointContextDto?,
    onSelectHazard: ((UnifiedMapFeature) -> Unit)?
) {
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Analyzing weather, elevation & hazards for this coordinate...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (error != null) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFEBEE),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFD32F2F))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = Color(0xFFC62828)
                )
            }
        }
        return
    }

    if (pointData == null) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // A. Weather Forecast Card
        val w = pointData.weather
        if (w != null) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Weather Forecast",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (pointData.is_cached || pointData.is_stale) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (pointData.is_stale) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = if (pointData.is_stale) "Stale Cache" else "Cached",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pointData.is_stale) Color(0xFFC62828) else Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DetailMetric(
                            label = "Temperature",
                            value = "${w.temperature_c}°C",
                            icon = Icons.Default.Thermostat,
                            color = Color(0xFFD32F2F)
                        )
                        DetailMetric(
                            label = "Sky Condition",
                            value = w.weather_description,
                            icon = Icons.Default.Cloud,
                            color = Color(0xFF0288D1)
                        )
                        DetailMetric(
                            label = "Precipitation",
                            value = "${w.precipitation_mm} mm",
                            icon = Icons.Default.WaterDrop,
                            color = Color(0xFF00796B)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        DetailMetric(
                            label = "Wind Speed",
                            value = "${w.wind_speed_kmh} km/h",
                            icon = Icons.Default.Air,
                            color = Color(0xFF5D4037)
                        )
                        DetailMetric(
                            label = "Humidity",
                            value = "${w.relative_humidity_pct.toInt()}%",
                            icon = Icons.Default.Water,
                            color = Color(0xFF1976D2)
                        )
                        pointData.elevation_m?.let { elev ->
                            DetailMetric(
                                label = "Elevation",
                                value = "${elev.toInt()} m",
                                icon = Icons.Default.Terrain,
                                color = Color(0xFF388E3C)
                            )
                        }
                    }
                }
            }
        }

        // B. Active Warnings & Hazards Section
        if (pointData.active_warnings.isNotEmpty() || pointData.nearby_hazards.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Official Warnings & Known Hazards",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFBF360C)
                        )
                    }

                    pointData.nearby_hazards.forEach { h ->
                        NearbyHazardRow(hazard = h)
                    }
                }
            }
        }

        // C. Nearby Facilities Section
        if (pointData.nearby_facilities.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Nearest Emergency Facilities",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    pointData.nearby_facilities.take(4).forEach { fac ->
                        NearbyFacilityRow(facility = fac)
                    }
                }
            }
        }

        // D. Cellular Connectivity
        if (pointData.cellular_estimate.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CellTower, contentDescription = null, tint = Color(0xFF651FFF), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Cellular Coverage: ${pointData.cellular_estimate["status"] ?: "Variable"}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = pointData.cellular_estimate["description"] ?: "",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // E. Disclaimer
        Text(
            text = pointData.disclaimer,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun NearbyHazardRow(hazard: NearbyHazardDto) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hazard.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(0xFFBF360C)
                )
                Text(
                    text = "Source: ${hazard.source} • Severity: ${hazard.severity ?: "Active"}",
                    fontSize = 10.sp,
                    color = Color(0xFF757575)
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (hazard.is_intersecting) Color(0xFFFFEBEE) else Color(0xFFFFF3E0)
            ) {
                Text(
                    text = if (hazard.is_intersecting) "Intersecting" else "${hazard.distance_km} km",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = if (hazard.is_intersecting) Color(0xFFC62828) else Color(0xFFE65100),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun NearbyFacilityRow(facility: NearbyFacilityDto) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = facility.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Text(
                    text = "${facility.category.replace("_", " ").uppercase(Locale.US)} • ${facility.distance_km} km away",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            facility.phone?.takeIf { it.isNotBlank() }?.let { phoneNum ->
                IconButton(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNum"))
                        context.startActivity(dialIntent)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HazardDetailSection(feature: UnifiedMapFeature) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Protective Action Required:",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFFC62828)
            )
            Text(
                text = feature.details["Instructions"] ?: feature.subtitle ?: "Exercise caution and avoid hazardous zones.",
                fontSize = 12.sp,
                color = Color(0xFFB71C1C)
            )
            Divider(color = Color(0xFFFFCDD2))
            DetailRow(icon = Icons.Default.Warning, label = "Hazard Type", value = feature.category.displayName)
            DetailRow(icon = Icons.Default.Source, label = "Issuing Authority", value = feature.sourceName)
            feature.formattedDistance()?.let {
                DetailRow(icon = Icons.Default.Straighten, label = "Distance from Location", value = it)
            }
        }
    }
}

@Composable
private fun FacilityDetailSection(feature: UnifiedMapFeature) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailRow(icon = Icons.Default.Category, label = "Facility Type", value = feature.category.displayName)
            feature.address?.let {
                DetailRow(icon = Icons.Default.Home, label = "Address", value = it)
            }
            feature.formattedDistance()?.let {
                DetailRow(icon = Icons.Default.Straighten, label = "Distance", value = it)
            }
            feature.phone?.takeIf { it.isNotBlank() }?.let { phoneNum ->
                Button(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNum"))
                        context.startActivity(dialIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call $phoneNum")
                }
            }
        }
    }
}

@Composable
private fun DetailMetric(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

