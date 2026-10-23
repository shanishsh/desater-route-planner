package com.touristsafety.app.feature.map

import androidx.compose.animation.AnimatedVisibility
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
                        text = if (isOffline) "Offline Cache" else "Live Data",
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

