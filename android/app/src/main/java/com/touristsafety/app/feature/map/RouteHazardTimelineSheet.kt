package com.touristsafety.app.feature.map

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.touristsafety.app.core.model.*

/**
 * Interactive, animated route hazard timeline components.
 * Prioritizes the most relevant upcoming hazard and displays the complete ordered
 * route sequence in a collapsible, interactive bottom sheet.
 */
@Composable
fun RouteHazardTimelineBar(
    hazardSequence: List<RouteOrderedHazardItem>,
    selectedItem: RouteOrderedHazardItem?,
    isAnimationEnabled: Boolean,
    onToggleAnimation: () -> Unit,
    onExpandTimeline: () -> Unit,
    onSelectItem: (RouteOrderedHazardItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (hazardSequence.isEmpty()) return

    // Find the primary upcoming hazard (first non-passed hazard, or selected item)
    val activeUpcoming = selectedItem
        ?: hazardSequence.firstOrNull { it.trekStatus == HazardTrekStatus.AHEAD || it.trekStatus == HazardTrekStatus.NEARBY }
        ?: hazardSequence.first()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(
            1.5.dp,
            if (activeUpcoming.intersectionType == RouteIntersectionType.DIRECT_INTERSECTION) {
                Color(0xFFEF4444) // Direct route alert red
            } else {
                Color(0xFFF59E0B) // Corridor warning amber
            }
        ),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onExpandTimeline() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sequence Badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(activeUpcoming.categoryType.badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = activeUpcoming.sequenceLabel,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Next: ${activeUpcoming.feature.title}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildProgressSubtitle(activeUpcoming),
                            fontSize = 11.sp,
                            color = if (activeUpcoming.intersectionType == RouteIntersectionType.DIRECT_INTERSECTION) {
                                Color(0xFFDC2626)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Animation toggle chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAnimationEnabled) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (isAnimationEnabled) Color(0xFF10B981) else Color.LightGray),
                        modifier = Modifier.clickable { onToggleAnimation() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isAnimationEnabled) "🎬 Anim" else "⏸ Static",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isAnimationEnabled) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Expand Route Hazard Sequence",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteHazardTimelineModalSheet(
    hazardSequence: List<RouteOrderedHazardItem>,
    selectedItem: RouteOrderedHazardItem?,
    isAnimationEnabled: Boolean,
    onToggleAnimation: () -> Unit,
    onSelectItem: (RouteOrderedHazardItem) -> Unit,
    onRecenterTrek: () -> Unit,
    onDismiss: () -> Unit
) {
    var activeFilter by remember { mutableStateOf("ALL") }

    val filteredItems = remember(hazardSequence, activeFilter) {
        when (activeFilter) {
            "DIRECT" -> hazardSequence.filter { it.intersectionType == RouteIntersectionType.DIRECT_INTERSECTION }
            "CORRIDOR" -> hazardSequence.filter { it.intersectionType == RouteIntersectionType.CORRIDOR_NEARBY }
            "UPCOMING" -> hazardSequence.filter { it.trekStatus == HazardTrekStatus.AHEAD || it.trekStatus == HazardTrekStatus.NEARBY }
            else -> hazardSequence
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Route Hazard Sequence",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${hazardSequence.size} items ordered by trail position",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Recenter Trek Button
                    OutlinedButton(
                        onClick = onRecenterTrek,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recenter", fontSize = 11.sp)
                    }

                    // Animation toggle
                    FilledTonalButton(
                        onClick = onToggleAnimation,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(if (isAnimationEnabled) "🎬 Active" else "⏸ Static", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeFilter == "ALL",
                    onClick = { activeFilter = "ALL" },
                    label = { Text("All (${hazardSequence.size})", fontSize = 11.sp) }
                )
                val directCount = hazardSequence.count { it.intersectionType == RouteIntersectionType.DIRECT_INTERSECTION }
                FilterChip(
                    selected = activeFilter == "DIRECT",
                    onClick = { activeFilter = "DIRECT" },
                    label = { Text("On Route ($directCount)", fontSize = 11.sp) }
                )
                val upcomingCount = hazardSequence.count { it.trekStatus == HazardTrekStatus.AHEAD || it.trekStatus == HazardTrekStatus.NEARBY }
                FilterChip(
                    selected = activeFilter == "UPCOMING",
                    onClick = { activeFilter = "UPCOMING" },
                    label = { Text("Upcoming ($upcomingCount)", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sequence List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredItems, key = { it.feature.id }) { item ->
                    RouteHazardItemCard(
                        item = item,
                        isSelected = selectedItem?.feature?.id == item.feature.id,
                        onClick = { onSelectItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun RouteHazardItemCard(
    item: RouteOrderedHazardItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var expandedInstructions by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else if (item.intersectionType == RouteIntersectionType.DIRECT_INTERSECTION) {
                Color(0xFFFCA5A5) // Light red
            } else {
                Color(0xFFE2E8F0) // Slate light border
            }
        ),
        shadowElevation = if (isSelected) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Sequence Number + Title + Severity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Number Badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(item.categoryType.badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.sequenceLabel,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.feature.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        SeverityBadge(item.severity)
                    }
                    Text(
                        text = item.categoryType.displayName,
                        fontSize = 11.sp,
                        color = item.categoryType.badgeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Geospatial metrics: Distance along route & relative progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Route Position
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "📍 ${item.formattedDistanceAlongRoute()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Direct intersection vs corridor
                if (item.intersectionType == RouteIntersectionType.DIRECT_INTERSECTION) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFEE2E2),
                        border = BorderStroke(0.5.dp, Color(0xFFEF4444))
                    ) {
                        Text(
                            text = "⚠️ Direct Route Intersection",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFEF3C7)
                    ) {
                        Text(
                            text = "↔ ${(item.crossTrackDistanceMeters / 1000.0).format(1)} km in Corridor",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Live Trekker Relative Status
            if (item.distanceAheadMeters != null || item.trekStatus == HazardTrekStatus.PASSED) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (statusColor, statusBg, statusText) = when (item.trekStatus) {
                        HazardTrekStatus.AHEAD -> Triple(
                            Color(0xFF0369A1),
                            Color(0xFFE0F2FE),
                            "Approaching: ${item.formattedDistanceAhead()} (${item.estimatedMinutesAhead ?: 0} min)"
                        )
                        HazardTrekStatus.NEARBY -> Triple(
                            Color(0xFFDC2626),
                            Color(0xFFFEE2E2),
                            "Immediate Proximity • Active Caution"
                        )
                        HazardTrekStatus.PASSED -> Triple(
                            Color(0xFF4B5563),
                            Color(0xFFF3F4F6),
                            "Passed • Behind Trekker"
                        )
                        HazardTrekStatus.UNCERTAIN -> Triple(
                            Color(0xFFD97706),
                            Color(0xFFFEF3C7),
                            "Off-Route • Re-acquire Path"
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Provenance & Freshness
            item.observationTimeText?.let { obsText ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = obsText,
                    fontSize = 10.sp,
                    color = if (item.isStale) Color(0xFFD97706) else MaterialTheme.colorScheme.outline
                )
            }

            // Expandable Instructions
            val instructions = item.feature.details["Instructions"] ?: item.feature.details["Protective Instructions"]
            if (!instructions.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (expandedInstructions) "▲ Hide Instructions" else "▼ View Safety Instructions",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expandedInstructions = !expandedInstructions }
                )
                if (expandedInstructions) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = instructions,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityBadge(severity: String) {
    val (bgColor, fgColor) = when (severity.uppercase()) {
        "CRITICAL" -> Color(0xFFDC2626) to Color.White
        "SEVERE", "HIGH" -> Color(0xFFEA580C) to Color.White
        "WARNING", "MODERATE" -> Color(0xFFF59E0B) to Color.Black
        else -> Color(0xFF10B981) to Color.White
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Text(
            text = severity.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = fgColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

private fun buildProgressSubtitle(item: RouteOrderedHazardItem): String {
    val directText = if (item.intersectionType == RouteIntersectionType.DIRECT_INTERSECTION) {
        "Directly on route"
    } else {
        "${(item.crossTrackDistanceMeters / 1000.0).format(1)} km right of trail"
    }
    val aheadText = item.distanceAheadMeters?.let {
        if (it < 1000.0) "${it.toInt()}m ahead" else "${(it / 1000.0).format(1)}km ahead"
    } ?: item.formattedDistanceAlongRoute()

    return "$aheadText • $directText"
}

private fun Double.format(decimals: Int): String {
    return String.format(java.util.Locale.US, "%.${decimals}f", this)
}

