package com.touristsafety.app.feature.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.touristsafety.app.core.model.Weather
import com.touristsafety.app.core.network.dto.RouteAlternativeDto
import java.util.Locale

@Composable
fun RouteSummaryCard(
    routeTitle: String,
    distanceKm: Double?,
    durationHours: Double?,
    isOffline: Boolean,
    activeAlertsCount: Int,
    weather: Weather?,
    fireCount: Int,
    forestCount: Int,
    nearestMedicalDistance: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    plannedRouteAlternatives: List<RouteAlternativeDto> = emptyList(),
    selectedAlternativeId: String? = null,
    overallSafetyStatus: String? = null,
    overallHazardScore: Double? = null,
    onSelectAlternative: (String?) -> Unit = {},
    corridorDistanceKm: Double = 5.0,
    isAnalyzingCorridor: Boolean = false,
    onSelectCorridorDistance: (Double) -> Unit = {},
    analysisLimitations: List<String> = emptyList(),
    onOpenComparison: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .clickable { onToggleExpand() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Collapsed Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Route Icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routeTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (distanceKm != null && durationHours != null) {
                            Text(
                                text = "${distanceKm} km • ${durationHours} hrs",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(text = " • ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Alerts Status Tag
                        if (activeAlertsCount > 0) {
                            Text(
                                text = "$activeAlertsCount active alert${if (activeAlertsCount > 1) "s" else ""}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                        } else {
                            Text(
                                text = "0 active hazards",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                // Data freshness badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOffline) Color(0xFFE3F2FD) else Color(0xFFE8F5E9)
                ) {
                    Text(
                        text = if (isOffline) "Uttarakhand Offline Mode" else "Live Data",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOffline) Color(0xFF1565C0) else Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            // Corridor Width Selector (Safety scan: X km each side)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Safety scan (each side):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                listOf(1.0, 2.0, 5.0, 10.0).forEach { dist ->
                    val isSelected = (corridorDistanceKm == dist)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCorridorDistance(dist) },
                        label = {
                            Text(
                                text = "${dist.toInt()} km",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(26.dp)
                    )
                }

                if (isAnalyzingCorridor) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Route Alternatives / Comparison Selector (Shortest vs Safest)
            if (plannedRouteAlternatives.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isPrimarySelected = selectedAlternativeId == null
                    val primaryHoursStr = String.format(Locale.US, "%.1f", durationHours ?: 0.0)
                    val primaryDistStr = String.format(Locale.US, "%.1f", distanceKm ?: 0.0)

                    RouteOptionChip(
                        title = "Shortest Path",
                        subtitle = "$primaryDistStr km • $primaryHoursStr hrs",
                        badge = overallSafetyStatus ?: "Standard",
                        isSelected = isPrimarySelected,
                        onClick = { onSelectAlternative(null) }
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { onOpenComparison() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compare (${1 + plannedRouteAlternatives.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    plannedRouteAlternatives.forEach { alt ->
                        val isAltSelected = selectedAlternativeId == alt.id
                        val altHoursStr = String.format(Locale.US, "%.1f", alt.duration_minutes / 60.0)
                        val altDistStr = String.format(Locale.US, "%.1f", alt.distance_km)

                        RouteOptionChip(
                            title = alt.title,
                            subtitle = "$altDistStr km • $altHoursStr hrs",
                            badge = "${alt.safety_label} (Risk ${alt.hazard_score.toInt()})",
                            isSelected = isAltSelected,
                            isAlternative = true,
                            onClick = { onSelectAlternative(alt.id) }
                        )
                    }
                }
            }

            // Expanded Route Condition Cards Carousel
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Corridor Safety Assessment",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Weather Card
                        ConditionCard(
                            title = "Weather",
                            icon = Icons.Default.WbSunny,
                            iconColor = Color(0xFF0288D1),
                            primaryText = if (weather != null) "${weather.temperatureC}°C, ${weather.weatherDescription}" else "Checking...",
                            secondaryText = if (weather != null) "Rain: ${weather.precipitationMm} mm • Wind: ${weather.windSpeedKmh} km/h" else "Open-Meteo"
                        )

                        // 2. Forest & Fire Card
                        ConditionCard(
                            title = "Forest & Wildfire",
                            icon = Icons.Default.Whatshot,
                            iconColor = Color(0xFFD32F2F),
                            primaryText = if (fireCount > 0) "$fireCount Satellite Hotspot${if (fireCount > 1) "s" else ""}" else "No Detections in Corridor",
                            secondaryText = "$forestCount mapped forest area${if (forestCount != 1) "s" else ""} (OSM)"
                        )

                        // 3. Road & Trail Disruptions
                        ConditionCard(
                            title = "Hazard Zones",
                            icon = Icons.Default.Warning,
                            iconColor = if (activeAlertsCount > 0) Color(0xFFC62828) else Color(0xFF2E7D32),
                            primaryText = if (activeAlertsCount > 0) "$activeAlertsCount Official Advisory Active" else "Trail Open & Passable",
                            secondaryText = "District Disaster Authority (DDMA)"
                        )

                        // 4. Nearby Assistance Card
                        ConditionCard(
                            title = "Nearby Assistance",
                            icon = Icons.Default.LocalHospital,
                            iconColor = Color(0xFFE53935),
                            primaryText = nearestMedicalDistance?.let { "Nearest Clinic: $it" } ?: "Medical Aid Nearby",
                            secondaryText = "Alpine Emergency Support Post"
                        )
                    }

                    if (analysisLimitations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Data Limitations & Coverage:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            analysisLimitations.forEach { limitation ->
                                Text(
                                    text = "• $limitation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    primaryText: String,
    secondaryText: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .width(180.dp)
            .height(90.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                text = primaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Text(
                text = secondaryText,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RouteOptionChip(
    title: String,
    subtitle: String,
    badge: String,
    isSelected: Boolean,
    isAlternative: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.AltRoute,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "• $badge",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (badge.contains("Safe", ignoreCase = true) || badge.contains("PASSABLE", ignoreCase = true)) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFC62828)
                        }
                    )
                }
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

