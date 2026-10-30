package com.touristsafety.app.feature.offline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touristsafety.app.core.model.Itinerary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedTripsScreen(
    viewModel: SavedTripsViewModel,
    onNavigateToReview: (String) -> Unit = {},
    onNavigateToMap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Trips & Offline Packs") }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Offline Policy Information Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Offline Data Strategy & OpenFreeMap Terms",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Route GeoJSON, waypoints, and safety advisories are 100% saved in local Room storage.\n" +
                                    "• OpenFreeMap vector downloads enforce a strict 3,000 tile / 50MB ceiling with 4 concurrent connections.\n" +
                                    "• If online vector basemaps fail, the built-in offline vector schematic displays the exact trail path safely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            state.downloadStatusMessage?.let { msg ->
                item {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 2. Custom Planned Routes (From Plan Route Screen)
            item {
                Text(
                    text = "Planned Expedition Routes (${state.plannedRoutes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.plannedRoutes.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No custom expedition routes planned yet. Plan a route to cache topographies and corridor hazards offline.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(state.plannedRoutes, key = { it.routeId }) { route ->
                    PlannedRouteItem(
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

            // 3. Saved Offline Itineraries Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Saved Offline Itineraries (${state.savedItineraries.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.savedItineraries.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No pre-packaged itineraries saved offline. Download a bounded pack below.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(state.savedItineraries, key = { it.id }) { itin ->
                    SavedTripItem(
                        itinerary = itin,
                        onRemove = { viewModel.toggleOfflineSave(itin.id, false) }
                    )
                }
            }

            // 4. Available Itineraries to Save
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "All Available Itineraries",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(state.allItineraries, key = { "all_${it.id}" }) { itin ->
                AvailableTripItem(
                    itinerary = itin,
                    onSave = { viewModel.downloadBoundedOfflinePack(itin.id) }
                )
            }
        }
    }
}

@Composable
fun PlannedRouteItem(
    route: com.touristsafety.app.core.database.entity.PlannedRouteEntity,
    onReview: () -> Unit,
    onViewOnMap: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(route.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = "Cached", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Offline Ready", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${route.originName} → ${route.destinationName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Distance: ${String.format(java.util.Locale.US, "%.1f", route.distanceKm)} km", style = MaterialTheme.typography.bodySmall)
                Text("Ascent: ${route.totalAscentM.toInt()} m", style = MaterialTheme.typography.bodySmall)
                Text("Status: ${route.overallSafetyStatus}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onReview,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Review Safety")
                }
                FilledTonalButton(
                    onClick = onViewOnMap
                ) {
                    Icon(Icons.Default.Map, contentDescription = "View on Map", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Map")
                }
                OutlinedButton(
                    onClick = onDelete
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun SavedTripItem(itinerary: Itinerary, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(itinerary.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = "Saved", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Saved Offline", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Distance: ${itinerary.distanceKm} km • ${itinerary.region}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Remove from Offline Storage")
            }
        }
    }
}

@Composable
fun AvailableTripItem(itinerary: Itinerary, onSave: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(itinerary.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(itinerary.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Bounded Offline Pack")
            }
        }
    }
}

