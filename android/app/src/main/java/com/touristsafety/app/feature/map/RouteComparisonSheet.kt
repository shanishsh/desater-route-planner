package com.touristsafety.app.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.network.dto.RouteAlternativeDto

enum class ComparisonPreference(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String
) {
    BALANCED(
        id = "balanced",
        title = "Balanced",
        icon = Icons.Default.Balance,
        description = "Multi-criteria trade-off (40% Safety, 30% Speed, 30% Facilities)"
    ),
    SAFEST(
        id = "safest",
        title = "Safest",
        icon = Icons.Default.Shield,
        description = "Minimizes hazard score and active disaster/wildfire exposure"
    ),
    FASTEST(
        id = "fastest",
        title = "Fastest",
        icon = Icons.Default.Speed,
        description = "Shortest estimated travel time on the road network"
    ),
    SHORTEST(
        id = "shortest",
        title = "Shortest",
        icon = Icons.Default.Straighten,
        description = "Shortest physical travel distance in kilometers"
    ),
    FACILITY_ACCESS(
        id = "facility_access",
        title = "Emergency Access",
        icon = Icons.Default.LocalHospital,
        description = "Maximizes access to nearby hospitals and police stations"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteComparisonSheet(
    state: MapUiState,
    onDismiss: () -> Unit,
    onSelectAlternative: (String?) -> Unit,
    onSelectPreference: (String) -> Unit
) {
    val selectedPref = ComparisonPreference.entries.find { it.id == state.selectedComparisonPreference }
        ?: ComparisonPreference.BALANCED

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Compare Route Candidates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${1 + state.plannedRouteAlternatives.size} genuine routes analyzed independently",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5 Selectable Preference Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ComparisonPreference.entries) { pref ->
                    val isSelected = pref == selectedPref
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPreference(pref.id) },
                        label = { Text(pref.title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = {
                            Icon(
                                imageVector = pref.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Preference explanation banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedPref.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Route Candidate Cards
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(bottom = 16.dp)
            ) {
                // 1. Primary Route Card
                item {
                    val isPrimaryActive = state.selectedAlternativeId == null
                    PrimaryRouteComparisonCard(
                        state = state,
                        isActive = isPrimaryActive,
                        preference = selectedPref,
                        onSelect = { onSelectAlternative(null) }
                    )
                }

                // 2. Alternative Route Cards
                items(state.plannedRouteAlternatives) { alt ->
                    val isAltActive = state.selectedAlternativeId == alt.id
                    AlternativeRouteComparisonCard(
                        alt = alt,
                        isActive = isAltActive,
                        preference = selectedPref,
                        primaryDistanceKm = state.routeDistanceKm ?: 0.0,
                        primaryDurationMinutes = (state.routeDurationHours ?: 0.0) * 60.0,
                        onSelect = { onSelectAlternative(alt.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryRouteComparisonCard(
    state: MapUiState,
    isActive: Boolean,
    preference: ComparisonPreference,
    onSelect: () -> Unit
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val cardBackground = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isActive) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0288D1))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Primary Route (Direct)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "ACTIVE ROUTE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "Distance",
                    value = String.format(java.util.Locale.US, "%.1f km", state.routeDistanceKm ?: 0.0)
                )
                MetricItem(
                    label = "Duration",
                    value = "${((state.routeDurationHours ?: 0.0) * 60).toInt()} min"
                )
                MetricItem(
                    label = "Hazard Score",
                    value = "${(state.primaryRouteOverallHazardScore ?: state.routeOverallHazardScore ?: 0.0).toInt()}/100",
                    valueColor = if ((state.routeOverallHazardScore ?: 0.0) >= 25.0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Facility counts summary
            val hospCount = state.categoryCounts[com.touristsafety.app.core.model.MapFeatureCategory.HOSPITAL] ?: 0
            val clinicCount = state.categoryCounts[com.touristsafety.app.core.model.MapFeatureCategory.CLINIC] ?: 0
            val polCount = state.categoryCounts[com.touristsafety.app.core.model.MapFeatureCategory.POLICE_STATION] ?: 0
            val fuelCount = state.categoryCounts[com.touristsafety.app.core.model.MapFeatureCategory.PETROL_PUMP] ?: 0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FacilityBadge(icon = Icons.Default.LocalHospital, count = hospCount + clinicCount, label = "Medical", color = Color(0xFFE53935))
                FacilityBadge(icon = Icons.Default.LocalPolice, count = polCount, label = "Police", color = Color(0xFF1E88E5))
                if (fuelCount > 0) {
                    FacilityBadge(icon = Icons.Default.LocalGasStation, count = fuelCount, label = "Fuel", color = Color(0xFFF57C00))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isActive) {
                OutlinedButton(
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Select Primary Route")
                }
            }
        }
    }
}

@Composable
private fun AlternativeRouteComparisonCard(
    alt: RouteAlternativeDto,
    isActive: Boolean,
    preference: ComparisonPreference,
    primaryDistanceKm: Double,
    primaryDurationMinutes: Double,
    onSelect: () -> Unit
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val cardBackground = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isActive) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (alt.is_recommended) Color(0xFF2E7D32) else Color(0xFF90A4AE))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alt.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "ACTIVE ALTERNATIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (alt.is_recommended) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Text(
                            text = "RECOMMENDED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "Distance",
                    value = String.format(java.util.Locale.US, "%.1f km", alt.distance_km),
                    subtext = if (primaryDistanceKm > 0) String.format(java.util.Locale.US, "%+g km", alt.distance_km - primaryDistanceKm) else null
                )
                MetricItem(
                    label = "Duration",
                    value = "${alt.duration_minutes.toInt()} min",
                    subtext = if (primaryDurationMinutes > 0) String.format(java.util.Locale.US, "%+d min", (alt.duration_minutes - primaryDurationMinutes).toInt()) else null
                )
                MetricItem(
                    label = "Hazard Score",
                    value = "${alt.hazard_score.toInt()}/100",
                    valueColor = if (alt.hazard_score >= 25.0) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Facility counts & nearest distances
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FacilityBadge(
                    icon = Icons.Default.LocalHospital,
                    count = alt.hospitals_count + alt.clinics_count,
                    label = "Medical",
                    color = Color(0xFFE53935)
                )
                FacilityBadge(
                    icon = Icons.Default.LocalPolice,
                    count = alt.police_stations_count,
                    label = "Police",
                    color = Color(0xFF1E88E5)
                )
                if (alt.fuel_stations_count > 0) {
                    FacilityBadge(
                        icon = Icons.Default.LocalGasStation,
                        count = alt.fuel_stations_count,
                        label = "Fuel",
                        color = Color(0xFFF57C00)
                    )
                }
            }

            alt.nearest_hospital_km?.let { nearHosp ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Nearest hospital is $nearHosp km from route corridor",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Contrastive trade-off explanation
            alt.exposure_summary?.let { exp ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = exp,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onSelect,
                enabled = !isActive,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isActive) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Currently Selected Alternative")
                } else {
                    Text("Select This Route Candidate")
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    subtext: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
        subtext?.let {
            Text(text = it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FacilityBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "$count $label", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

