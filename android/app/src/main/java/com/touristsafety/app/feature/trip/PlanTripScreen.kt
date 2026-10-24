package com.touristsafety.app.feature.trip

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
    var showRecentOriginMenu by remember { mutableStateOf(false) }
    var showRecentDestMenu by remember { mutableStateOf(false) }

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
                title = { Text("Plan Route & Trek Safety", fontWeight = FontWeight.Bold) },
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
            // Capabilities and Coverage Banner
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
                            Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Dynamic Any-Location Routing & Safety Radar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Plan routes anywhere via GPS, on-map pin selection, or place search. Real-time correlation with Open-Meteo alpine weather, Overpass facilities, NASA FIRMS active fires, and NDMA SACHET India disaster alerts.",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Start Location (Origin)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = state.originName,
                        onValueChange = { viewModel.onOriginQueryChanged(it) },
                        label = { Text("Search place or enter coordinates") },
                        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                        trailingIcon = {
                            if (state.originName.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onOriginQueryChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Origin Action Row: GPS, Map Picker, Recent
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.useCurrentLocation() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !state.isLocating
                        ) {
                            if (state.isLocating) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("My GPS", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { viewModel.openMapPicker(MapPickerTarget.ORIGIN) },
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick on Map", fontSize = 12.sp)
                        }

                        if (state.recentLocations.isNotEmpty()) {
                            Box {
                                OutlinedButton(
                                    onClick = { showRecentOriginMenu = true },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Recent", fontSize = 12.sp)
                                }
                                DropdownMenu(
                                    expanded = showRecentOriginMenu,
                                    onDismissRequest = { showRecentOriginMenu = false }
                                ) {
                                    state.recentLocations.take(6).forEach { recent ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(recent.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                    Text(
                                                        String.format(java.util.Locale.US, "%.4f, %.4f", recent.latitude, recent.longitude),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.selectRecentLocation(recent, isOrigin = true)
                                                showRecentOriginMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Origin Autocomplete Suggestions
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
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(place.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                            Text(place.display_name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            // Destination Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Destination", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = state.destinationName,
                        onValueChange = { viewModel.onDestinationQueryChanged(it) },
                        label = { Text("Search summit, landmark, town, or peak") },
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

                    // Destination Action Row: Pick on Map, Recent
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.openMapPicker(MapPickerTarget.DESTINATION) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick on Map", fontSize = 12.sp)
                        }

                        if (state.recentLocations.isNotEmpty()) {
                            Box {
                                OutlinedButton(
                                    onClick = { showRecentDestMenu = true },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Recent Dest", fontSize = 12.sp)
                                }
                                DropdownMenu(
                                    expanded = showRecentDestMenu,
                                    onDismissRequest = { showRecentDestMenu = false }
                                ) {
                                    state.recentLocations.take(6).forEach { recent ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(recent.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                    Text(
                                                        String.format(java.util.Locale.US, "%.4f, %.4f", recent.latitude, recent.longitude),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                viewModel.selectRecentLocation(recent, isOrigin = false)
                                                showRecentDestMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

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
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(place.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                            Text(place.display_name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            // Corridor Buffer Width Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hazard & Facility Corridor Buffer", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${state.corridorDistanceKm.toInt()} km per side", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Defines the spatial search polygon along the route for hospitals, police, fires, and alerts.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val buffers = listOf(1.0, 2.0, 5.0, 10.0)
                        buffers.forEach { km ->
                            val isSelected = state.corridorDistanceKm == km
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setCorridorDistanceKm(km) },
                                label = { Text("${km.toInt()} km${if (km == 5.0) " (Def)" else ""}", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                        Pair("hiking", "Hiking / Trekking"),
                        Pair("walking", "Walking"),
                        Pair("driving", "Driving")
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
                        "⚠️ Motor vehicles strictly prohibited on pedestrian mountain trails. Foot travel only.",
                        fontSize = 11.sp,
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Quick Alpine Corridors (Popular Suggestions)
            item {
                Text("Popular Trek & Pilgrim Corridors", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
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

            // Progress / Action Area
            item {
                Spacer(modifier = Modifier.height(8.dp))
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

    // Map Location Picker Dialog
    if (state.isMapPickerOpen) {
        val isOrigin = state.mapPickerTarget == MapPickerTarget.ORIGIN
        MapLocationPickerDialog(
            title = if (isOrigin) "Select Starting Point on Map" else "Select Destination on Map",
            initialLat = if (isOrigin) state.originLat else state.destinationLat,
            initialLon = if (isOrigin) state.originLon else state.destinationLon,
            onConfirmLocation = { lat, lon, name ->
                viewModel.onLocationConfirmedFromMap(lat, lon, name)
            },
            onDismiss = {
                viewModel.closeMapPicker()
            },
            reverseGeocode = { lat, lon ->
                viewModel.reverseGeocode(lat, lon)
            }
        )
    }
}
