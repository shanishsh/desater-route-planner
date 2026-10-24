package com.touristsafety.app.feature.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanTripScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReview: (String) -> Unit,
    viewModel: PlanTripViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is PlanTripEvent.NavigateToSafetyReview -> onNavigateToReview(event.routeId)
            }
        }
    }

    data class VerifiedCorridor(
        val label: String,
        val originName: String,
        val originLat: Double,
        val originLon: Double,
        val destName: String,
        val destLat: Double,
        val destLon: Double,
        val mode: String,
        val isPedestrianOnly: Boolean
    )

    val verifiedCorridors = listOf(
        VerifiedCorridor(
            label = "Kedarnath Trek (Foot)",
            originName = "Gaurikund Roadhead",
            originLat = 30.6534,
            originLon = 79.0268,
            destName = "Kedarnath Temple",
            destLat = 30.7339,
            destLon = 79.0669,
            mode = "hiking",
            isPedestrianOnly = true
        ),
        VerifiedCorridor(
            label = "Gaurikund Base (NH 107)",
            originName = "Rudraprayag Junction",
            originLat = 30.2844,
            originLon = 78.9806,
            destName = "Gaurikund",
            destLat = 30.6534,
            destLon = 79.0268,
            mode = "driving",
            isPedestrianOnly = false
        ),
        VerifiedCorridor(
            label = "Mussoorie Hills",
            originName = "Dehradun Clock Tower",
            originLat = 30.3165,
            originLon = 78.0322,
            destName = "Mussoorie Mall Road",
            destLat = 30.4598,
            destLon = 78.0700,
            mode = "driving",
            isPedestrianOnly = false
        ),
        VerifiedCorridor(
            label = "Devprayag Confluence",
            originName = "Rishikesh",
            originLat = 30.0869,
            originLon = 78.2676,
            destName = "Devprayag Sangam",
            destLat = 30.1459,
            destLon = 78.5989,
            mode = "driving",
            isPedestrianOnly = false
        ),
        VerifiedCorridor(
            label = "Haridwar – Rishikesh",
            originName = "Haridwar",
            originLat = 29.9457,
            originLon = 78.1642,
            destName = "Rishikesh",
            destLat = 30.0869,
            destLon = 78.2676,
            mode = "driving",
            isPedestrianOnly = false
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uttarakhand Route & Trek Safety", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Intro banner
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Active Pilot Region: Uttarakhand, India",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Authoritative boundary active (7,129-vertex OSM border). Stage 1 pilot corridor: Gaurikund–Kedarnath alpine trek with genuine NDMA SACHET alerts, NASA FIRMS, and Open-Meteo weather.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Error message
            if (state.errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                state.errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Origin Selector
            item {
                Text("Start Location (Origin)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                OutlinedTextField(
                    value = state.originName,
                    onValueChange = { viewModel.onOriginQueryChanged(it) },
                    label = { Text("Origin settlement or coordinate") },
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.useCurrentLocation() }) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = "Use GPS Location",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Origin Suggestions
                if (state.originSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            state.originSuggestions.forEach { place ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectOrigin(place) }
                                        .padding(12.dp)
                                ) {
                                    Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(place.display_name, fontSize = 13.sp)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // Destination Selector
            item {
                Text("Destination", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                OutlinedTextField(
                    value = state.destinationName,
                    onValueChange = { viewModel.onDestinationQueryChanged(it) },
                    label = { Text("Search town, mountain pass, or landmark") },
                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                    trailingIcon = {
                        if (state.destinationName.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onDestinationQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Destination Suggestions
                if (state.destSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            state.destSuggestions.forEach { place ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectDestination(place) }
                                        .padding(12.dp)
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(place.display_name, fontSize = 13.sp)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // Verified Uttarakhand Pilot Corridors
            item {
                Text("Verified Uttarakhand Pilot Corridors", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(verifiedCorridors) { corridor ->
                        val isSelected = state.destinationName == corridor.destName && state.originName == corridor.originName
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.selectVerifiedCorridor(
                                    originName = corridor.originName,
                                    originLat = corridor.originLat,
                                    originLon = corridor.originLon,
                                    destName = corridor.destName,
                                    destLat = corridor.destLat,
                                    destLon = corridor.destLon,
                                    mode = corridor.mode,
                                    isPedestrianOnly = corridor.isPedestrianOnly
                                )
                            },
                            label = { Text(corridor.label, fontSize = 12.sp) },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        )
                    }
                }
            }

            // Travel Mode
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Travel Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (state.isPedestrianOnlySelected) {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Pedestrian Only Corridor",
                                color = Color(0xFFE65100),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Pair("driving", "Driving (Car/Taxi)"),
                        Pair("hiking", "Hiking / Trekking"),
                        Pair("walking", "Walking")
                    )
                    modes.forEach { (mode, label) ->
                        val isDrivingDisabled = mode == "driving" && state.isPedestrianOnlySelected
                        OutlinedButton(
                            onClick = { viewModel.setTravelMode(mode) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isDrivingDisabled,
                            colors = if (state.travelMode == mode) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            } else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text(
                                label.split(" ")[0],
                                fontSize = 12.sp,
                                fontWeight = if (state.travelMode == mode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                if (state.isPedestrianOnlySelected) {
                    Text(
                        "⚠️ Motor vehicles strictly prohibited beyond Gaurikund. Alpine foot trek only.",
                        fontSize = 11.sp,
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Progress / Action Area
            item {
                Spacer(modifier = Modifier.height(12.dp))
                if (state.isPlanning) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                state.planningPhase,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Multi-source sensor correlation in progress...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.planRoute() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = state.destinationName.isNotBlank() && state.originName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan & Evaluate Route Safety", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
