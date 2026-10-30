package com.touristsafety.app.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.map.BasemapMode
import com.touristsafety.app.core.model.LayerDataStatus
import com.touristsafety.app.core.model.MapFeatureCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayersSheet(
    layerVisibilities: Map<MapFeatureCategory, Boolean>,
    layerStatuses: Map<MapFeatureCategory, LayerDataStatus>,
    categoryCounts: Map<MapFeatureCategory, Int>,
    onToggleLayer: (MapFeatureCategory) -> Unit,
    onShowAll: () -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit,
    currentBasemapMode: BasemapMode = BasemapMode.STREETS_TRAILS,
    onSelectBasemapMode: ((BasemapMode) -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Map Layers & Providers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Customize visible map features and real data status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Basemap Style Selector
            Text(
                text = "Basemap Style",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasemapMode.entries.forEach { mode ->
                    val isSelected = mode == currentBasemapMode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectBasemapMode?.invoke(mode) },
                        label = {
                            Text(
                                text = mode.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (mode) {
                                    BasemapMode.STREETS_TRAILS -> Icons.Default.Terrain
                                    BasemapMode.SATELLITE -> Icons.Default.SatelliteAlt
                                    BasemapMode.HYBRID -> Icons.Default.Map
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons (Show All / Reset Defaults)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShowAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Show All", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Defaults", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Layer rows list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(MapFeatureCategory.entries.filter { it != MapFeatureCategory.ROUTE_WAYPOINT }) { category ->
                    val isVisible = layerVisibilities[category] ?: true
                    val status = layerStatuses[category] ?: LayerDataStatus.LOADED
                    val count = categoryCounts[category] ?: 0

                    LayerRowItem(
                        category = category,
                        isVisible = isVisible,
                        status = status,
                        count = count,
                        onToggle = { onToggleLayer(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Map Legend Accordion / Section
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Map Legend",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendBadge(color = Color(0xFFE53935), label = "Medical / Clinic")
                        LegendBadge(color = Color(0xFF1E88E5), label = "Police Post")
                        LegendBadge(color = Color(0xFFE65100), label = "Fire Station")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendBadge(color = Color(0xFF2E7D32), label = "Forest Cover (OSM)")
                        LegendBadge(color = Color(0xFFD32F2F), label = "Active Fire (FIRMS)")
                        LegendBadge(color = Color(0xFFC62828), label = "Hazard Zone")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendBadge(color = Color(0xFF651FFF), label = "Cell Tower & Range")
                        LegendBadge(color = Color(0xFF0288D1), label = "Rivers & Waterways")
                        LegendBadge(color = Color(0xFF0097A7), label = "Flood Inundation")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LayerRowItem(
    category: MapFeatureCategory,
    isVisible: Boolean,
    status: LayerDataStatus,
    count: Int,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(category.primaryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = category.primaryColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.height(18.dp)
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Data Provenance & Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(status.color)
                    )
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = getProviderNameForCategory(category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Visibility Toggle Switch
            Switch(
                checked = isVisible,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(0.85f)
            )
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.size((52 * scale).dp, (32 * scale).dp)
)

@Composable
private fun LegendBadge(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getProviderNameForCategory(category: MapFeatureCategory): String {
    return when (category) {
        MapFeatureCategory.HOSPITAL -> "UK Health Directory"
        MapFeatureCategory.CLINIC -> "UK Health Directory"
        MapFeatureCategory.POLICE_STATION -> "Uttarakhand Police"
        MapFeatureCategory.FIRE_STATION -> "Uttarakhand Fire Service"
        MapFeatureCategory.SHELTER -> "DDMA Safe Havens"
        MapFeatureCategory.PETROL_PUMP -> "HPCL / IOCL / BPCL"
        MapFeatureCategory.TOURIST_ASSISTANCE -> "Uttarakhand Tourism"
        MapFeatureCategory.FOREST_AREA -> "OpenStreetMap (ODbL)"
        MapFeatureCategory.ACTIVE_FIRE_DETECTION -> "NASA FIRMS (VIIRS/MODIS)"
        MapFeatureCategory.WEATHER_POINT -> "Open-Meteo API"
        MapFeatureCategory.HAZARD_ZONE -> "USDMA & NDMA CAP"
        MapFeatureCategory.COMMUNITY_HAZARD -> "Community Trekker Reports"
        MapFeatureCategory.ROUTE_WAYPOINT -> "Offline Route Engine"
        MapFeatureCategory.CELL_TOWER -> "OpenCelliD / TARANG Sanchar"
        MapFeatureCategory.WATERWAY -> "OpenStreetMap / Glacial Lake Atlas"
    }
}
