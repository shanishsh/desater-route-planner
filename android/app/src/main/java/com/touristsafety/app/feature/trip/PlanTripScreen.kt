package com.touristsafety.app.feature.trip

import androidx.compose.foundation.background
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.designsystem.components.*

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
                title = { Text("Plan Route & Safety", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(AppIcons.Back, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dismissible error banner
            if (state.errorMessage != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppErrorBanner(
                            message = state.errorMessage ?: "",
                            onDismiss = { viewModel.clearError() }
                        )
                        if (state.errorMessage?.contains("10.0.2.2") == true ||
                            state.errorMessage?.contains("failed to connect") == true ||
                            state.errorMessage?.contains("Failed to connect") == true ||
                            state.errorMessage?.contains("Connection refused") == true) {
                            Button(
                                onClick = { viewModel.switchToRenderAndRetry() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("☁️ Switch to Live Cloud (Render) & Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Presets row
            item {
                AppSectionHeader(
                    title = "Verified Trail Presets",
                    subtitle = "Tap a route to automatically load coordinates and recommended travel mode"
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                            label = { Text(corridor.label, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SageContainer,
                                selectedLabelColor = ForestGreenPrimary
                            )
                        )
                    }
                }
            }

            // Route Input Card (Origin & Destination)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Origin Field
                        Text(
                            text = "Starting Point (Origin)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.originName,
                            onValueChange = { viewModel.onOriginQueryChanged(it) },
                            placeholder = { Text("Search location or enter coordinates") },
                            leadingIcon = {
                                Icon(Icons.Default.Place, contentDescription = null, tint = ForestGreenPrimary)
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (state.originName.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onOriginQueryChanged("") }) {
                                            Icon(AppIcons.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.useCurrentLocation() },
                                        enabled = !state.isLocating
                                    ) {
                                        if (state.isLocating) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(AppIcons.MyLocation, contentDescription = "Use GPS", tint = ForestGreenPrimary)
                                        }
                                    }
                                    IconButton(onClick = { viewModel.openMapPicker(MapPickerTarget.ORIGIN) }) {
                                        Icon(AppIcons.Map, contentDescription = "Pick on Map", tint = ForestGreenPrimary)
                                    }
                                    if (state.recentLocations.isNotEmpty()) {
                                        IconButton(onClick = { showRecentOriginMenu = true }) {
                                            Icon(Icons.Default.History, contentDescription = "Recent Locations")
                                        }
                                        DropdownMenu(
                                            expanded = showRecentOriginMenu,
                                            onDismissRequest = { showRecentOriginMenu = false }
                                        ) {
                                            state.recentLocations.take(5).forEach { recent ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(recent.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                            Text(
                                                                String.format(java.util.Locale.US, "%.4f, %.4f", recent.latitude, recent.longitude),
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.outline
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
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        // Autocomplete Suggestions for Origin
                        if (state.originSuggestions.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 3.dp
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
                                            Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(18.dp), tint = ForestGreenPrimary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(place.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                Text(place.display_name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        HorizontalDivider(thickness = 0.5.dp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Destination Field
                        Text(
                            text = "Destination",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmergencyRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = state.destinationName,
                            onValueChange = { viewModel.onDestinationQueryChanged(it) },
                            placeholder = { Text("Search summit, temple, town or landmark") },
                            leadingIcon = {
                                Icon(Icons.Default.Flag, contentDescription = null, tint = EmergencyRed)
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (state.destinationName.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onDestinationQueryChanged("") }) {
                                            Icon(AppIcons.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(onClick = { viewModel.openMapPicker(MapPickerTarget.DESTINATION) }) {
                                        Icon(AppIcons.Map, contentDescription = "Pick on Map", tint = ForestGreenPrimary)
                                    }
                                    if (state.recentLocations.isNotEmpty()) {
                                        IconButton(onClick = { showRecentDestMenu = true }) {
                                            Icon(Icons.Default.History, contentDescription = "Recent Destinations")
                                        }
                                        DropdownMenu(
                                            expanded = showRecentDestMenu,
                                            onDismissRequest = { showRecentDestMenu = false }
                                        ) {
                                            state.recentLocations.take(5).forEach { recent ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(recent.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                            Text(
                                                                String.format(java.util.Locale.US, "%.4f, %.4f", recent.latitude, recent.longitude),
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.outline
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
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        // Autocomplete Suggestions for Destination
                        if (state.destSuggestions.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 3.dp
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
                                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp), tint = EmergencyRed)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(place.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                Text(place.display_name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        HorizontalDivider(thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Travel Mode Segmented Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Travel Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (state.isPedestrianOnlySelected) {
                        AppStatusBadge(label = "Foot Trail Only", isWarning = true)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Triple("hiking", "Hiking / Trekking", AppIcons.Hiking),
                        Triple("walking", "Walking", Icons.Default.DirectionsWalk),
                        Triple("driving", "Driving", AppIcons.Driving)
                    )
                    modes.forEach { (mode, label, icon) ->
                        val isDrivingDisabled = mode == "driving" && state.isPedestrianOnlySelected
                        val isSelected = state.travelMode == mode
                        OutlinedButton(
                            onClick = { viewModel.setTravelMode(mode) },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isDrivingDisabled,
                            colors = if (isSelected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = SageContainer,
                                    contentColor = ForestGreenPrimary
                                )
                            } else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                label.split(" ")[0],
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                if (state.isPedestrianOnlySelected) {
                    Text(
                        "Motor vehicles strictly prohibited on pedestrian mountain trails.",
                        fontSize = 11.sp,
                        color = WarningAmber,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Corridor Buffer Width Selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Safety Corridor Buffer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${state.corridorDistanceKm.toInt()} km search radius",
                        style = MaterialTheme.typography.labelSmall,
                        color = ForestGreenPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
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
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SageContainer,
                                selectedLabelColor = ForestGreenPrimary
                            )
                        )
                    }
                }
            }

            // Calculate Action Area
            item {
                Spacer(modifier = Modifier.height(8.dp))
                if (state.isPlanning) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = ForestGreenPrimary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                state.planningPhase,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Correlating weather, fire and disaster hazards along trail...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    AppPrimaryButton(
                        text = "Scan & Evaluate Route Safety",
                        icon = AppIcons.StartTrek,
                        onClick = { viewModel.planRoute() },
                        enabled = state.destinationName.isNotBlank() && state.originName.isNotBlank()
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
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
            boundaryValidator = viewModel.boundaryValidator,
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
