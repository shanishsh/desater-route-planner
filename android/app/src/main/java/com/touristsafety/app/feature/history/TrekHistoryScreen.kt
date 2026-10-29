package com.touristsafety.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.touristsafety.app.core.database.entity.ActiveTripEntity
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import com.touristsafety.app.core.designsystem.ForestGreenDark
import com.touristsafety.app.core.designsystem.ForestGreenPrimary
import com.touristsafety.app.core.designsystem.SageContainer
import com.touristsafety.app.core.designsystem.components.AppEmptyState
import com.touristsafety.app.core.designsystem.components.AppPrimaryButton
import com.touristsafety.app.core.designsystem.components.AppSectionHeader
import com.touristsafety.app.core.model.Itinerary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekHistoryScreen(
    viewModel: TrekHistoryViewModel,
    onNavigateToCompletedTrekDetail: (String) -> Unit = {},
    onNavigateToPlanTrip: () -> Unit = {},
    onNavigateToReview: (String) -> Unit = {},
    onNavigateToMap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Trek History & Offline",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Tab Row (0: Completed Treks, 1: Saved Packs & Routes)
            TabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ForestGreenPrimary
            ) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Completed (${state.completedTrips.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Saved & Offline (${state.plannedRoutes.size + state.savedItineraries.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            if (state.selectedTab == 0) {
                // Completed Treks List
                if (state.completedTrips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Hiking,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                "No Completed Treks Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "When you finish an active trek, your full recorded GPS path, distance, duration, and safety milestones will be archived here for inspection.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AppPrimaryButton(
                                text = "Plan Your First Trek",
                                onClick = onNavigateToPlanTrip
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.completedTrips, key = { it.tripId }) { trip ->
                            CompletedTripCard(
                                trip = trip,
                                onClick = { onNavigateToCompletedTrekDetail(trip.tripId) }
                            )
                        }
                    }
                }
            } else {
                // Saved Expeditions & Offline Packs (Merged from SavedTripsScreen)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Offline Reliability Guarantee",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "All route GeoJSON, waypoints, and hazard layers are saved locally in Room storage. You can access maps and safety guidance even with 0% cellular connectivity in the Himalayas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Custom Planned Routes
                    item {
                        AppSectionHeader(
                            title = "Planned Expeditions (${state.plannedRoutes.size})",
                            subtitle = "Cached routes ready for offline navigation"
                        )
                    }

                    if (state.plannedRoutes.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = "No custom expedition routes planned yet. Plan or draw a route to cache offline.",
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(state.plannedRoutes, key = { it.routeId }) { route ->
                            PlannedRouteHistoryItem(
                                route = route,
                                onReview = {
                                    viewModel.activateRoute(route)
                                    onNavigateToReview(route.routeId)
                                },
                                onViewOnMap = {
                                    viewModel.activateRoute(route)
                                    onNavigateToMap()
                                },
                                onDelete = { viewModel.deletePlannedRoute(route.routeId) }
                            )
                        }
                    }

                    // Saved Offline Itineraries
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        AppSectionHeader(
                            title = "Saved Offline Itineraries (${state.savedItineraries.size})",
                            subtitle = "Pre-packaged bounded route packs"
                        )
                    }

                    if (state.savedItineraries.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = "No pre-packaged itineraries saved offline.",
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(state.savedItineraries, key = { "saved_${it.id}" }) { itin ->
                            SavedItineraryHistoryItem(
                                itinerary = itin,
                                onRemove = { viewModel.toggleOfflineSave(itin.id, false) }
                            )
                        }
                    }

                    // All Available Itineraries
                    if (state.allItineraries.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            AppSectionHeader(
                                title = "Available Packs to Download",
                                subtitle = "Explore and cache verified pilgrim and trail packs"
                            )
                        }

                        items(state.allItineraries, key = { "avail_${it.id}" }) { itin ->
                            AvailableItineraryHistoryItem(
                                itinerary = itin,
                                onSave = { viewModel.toggleOfflineSave(itin.id, true) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedTripCard(
    trip: ActiveTripEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ForestGreenPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "COMPLETED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary
                    )
                }

                Text(
                    text = trip.startedAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = trip.routeTitle.ifBlank { "Completed Trek" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (trip.originName.isNotBlank() && trip.destinationName.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${trip.originName} → ${trip.destinationName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val distKm = trip.traveledDistanceMeters / 1000.0
                val hrs = trip.elapsedDurationSeconds / 3600
                val mins = (trip.elapsedDurationSeconds % 3600) / 60
                val timeStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins} min"

                Column {
                    Text(
                        text = "${String.format(Locale.US, "%.2f km", distKm)} traveled",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenDark
                    )
                    Text(
                        text = "Duration: $timeStr • ${trip.recordedPointsCount} GPS points",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Inspect Track", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PlannedRouteHistoryItem(
    route: PlannedRouteEntity,
    onReview: () -> Unit,
    onViewOnMap: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(route.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text("${route.originName} → ${route.destinationName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("${String.format(Locale.US, "%.1f", route.distanceKm)} km", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text("Ascent: ${route.totalAscentM.toInt()} m", style = MaterialTheme.typography.labelMedium)
                Text(route.overallSafetyStatus, style = MaterialTheme.typography.labelMedium, color = ForestGreenPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onReview,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Safety Review", fontSize = 12.sp)
                }
                FilledTonalButton(
                    onClick = onViewOnMap,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Map", fontSize = 12.sp)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SavedItineraryHistoryItem(itinerary: Itinerary, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(itinerary.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Saved Offline", color = ForestGreenPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("${itinerary.distanceKm} km • ${itinerary.region}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Remove from Offline Storage", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AvailableItineraryHistoryItem(itinerary: Itinerary, onSave: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(itinerary.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(itinerary.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download Offline Pack", fontSize = 12.sp)
            }
        }
    }
}

