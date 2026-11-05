package com.touristsafety.app.feature.trip

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.touristsafety.app.core.designsystem.*
import com.touristsafety.app.core.designsystem.components.*
import com.touristsafety.app.core.network.dto.PlaceSearchResultDto

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

val defaultVerifiedCorridors = listOf(
    VerifiedCorridor(
        label = "Kedarnath",
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
        label = "Tungnath",
        originName = "Chopta Roadhead",
        originLat = 30.4870,
        originLon = 79.2170,
        destName = "Tungnath Temple",
        destLat = 30.4883,
        destLon = 79.2174,
        mode = "hiking",
        isPedestrianOnly = true
    ),
    VerifiedCorridor(
        label = "Rudranath",
        originName = "Sagar Village",
        originLat = 30.4432,
        originLon = 79.3304,
        destName = "Rudranath Temple",
        destLat = 30.5230,
        destLon = 79.3245,
        mode = "hiking",
        isPedestrianOnly = true
    ),
    VerifiedCorridor(
        label = "Hemkund",
        originName = "Ghangaria Camp",
        originLat = 30.7011,
        originLon = 79.5852,
        destName = "Hemkund Sahib Gurudwara",
        destLat = 30.7001,
        destLon = 79.6083,
        mode = "hiking",
        isPedestrianOnly = true
    ),
    VerifiedCorridor(
        label = "Valley of Flowers",
        originName = "Govindghat Base",
        originLat = 30.6247,
        originLon = 79.5606,
        destName = "Valley of Flowers Entry",
        destLat = 30.7258,
        destLon = 79.5936,
        mode = "hiking",
        isPedestrianOnly = true
    ),
    VerifiedCorridor(
        label = "Har Ki Dun",
        originName = "Sankri Base",
        originLat = 31.0772,
        originLon = 78.1802,
        destName = "Har Ki Dun Valley",
        destLat = 31.1444,
        destLon = 78.4116,
        mode = "hiking",
        isPedestrianOnly = true
    ),
    VerifiedCorridor(
        label = "Gaumukh – Tapovan",
        originName = "Gangotri Temple",
        originLat = 30.9947,
        originLon = 78.9398,
        destName = "Gaumukh Glacier",
        destLat = 30.9255,
        destLon = 79.0805,
        mode = "hiking",
        isPedestrianOnly = true
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
    )
)

/**
 * Stateful PlanTripScreen coordinating lifecycle flows, navigation events, and dialogs.
 */
@Composable
fun PlanTripScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReview: (String) -> Unit,
    onNavigateToDrawRoute: () -> Unit = {},
    onNavigateToChooseOnMap: () -> Unit = {},
    viewModel: PlanTripViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is PlanTripEvent.NavigateToSafetyReview -> onNavigateToReview(event.routeId)
                is PlanTripEvent.NavigateToDrawRoute -> onNavigateToDrawRoute()
            }
        }
    }

    PlanTripContent(
        state = state,
        verifiedCorridors = defaultVerifiedCorridors,
        onNavigateBack = onNavigateBack,
        onNavigateToDrawRoute = onNavigateToDrawRoute,
        onNavigateToChooseOnMap = onNavigateToChooseOnMap,
        onClearError = { viewModel.clearError() },
        onRetryWithTravelMode = { mode -> viewModel.retryWithTravelMode(mode) },
        onSwitchToRenderAndRetry = { viewModel.switchToRenderAndRetry() },
        onSelectCorridor = { corridor ->
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
        onOriginQueryChanged = { viewModel.onOriginQueryChanged(it) },
        onSelectOrigin = { viewModel.selectOrigin(it) },
        onUseCurrentLocation = { viewModel.useCurrentLocation() },
        onOpenOriginMapPicker = { viewModel.openMapPicker(MapPickerTarget.ORIGIN) },
        onSelectRecentOriginLocation = { viewModel.selectRecentLocation(it, isOrigin = true) },
        onDestinationQueryChanged = { viewModel.onDestinationQueryChanged(it) },
        onSelectDestination = { viewModel.selectDestination(it) },
        onOpenDestMapPicker = { viewModel.openMapPicker(MapPickerTarget.DESTINATION) },
        onSelectRecentDestLocation = { viewModel.selectRecentLocation(it, isOrigin = false) },
        onSetTravelMode = { viewModel.setTravelMode(it) },
        onSetCorridorDistanceKm = { viewModel.setCorridorDistanceKm(it) },
        onPlanRoute = { viewModel.planRoute() }
    )

    // Map Location Picker Dialog
    if (state.isMapPickerOpen) {
        val isOrigin = state.mapPickerTarget == MapPickerTarget.ORIGIN
        MapLocationPickerDialog(
            title = if (isOrigin) "Select Starting Point on Map" else "Select Destination on Map",
            initialLat = if (isOrigin) (state.originLat ?: state.deviceGpsLat) else state.destinationLat,
            initialLon = if (isOrigin) (state.originLon ?: state.deviceGpsLon) else state.destinationLon,
            currentGpsLat = state.deviceGpsLat,
            currentGpsLon = state.deviceGpsLon,
            onRefreshGpsLocation = { viewModel.useCurrentLocation() },
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

/**
 * Pure stateless PlanTripContent implementing the Dark Outdoor Reference Design (Screen 3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanTripContent(
    state: PlanTripUiState,
    verifiedCorridors: List<VerifiedCorridor>,
    onNavigateBack: () -> Unit,
    onNavigateToDrawRoute: () -> Unit,
    onNavigateToChooseOnMap: () -> Unit = {},
    onClearError: () -> Unit,
    onRetryWithTravelMode: (String) -> Unit,
    onSwitchToRenderAndRetry: () -> Unit,
    onSelectCorridor: (VerifiedCorridor) -> Unit,
    onOriginQueryChanged: (String) -> Unit,
    onSelectOrigin: (PlaceSearchResultDto) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onOpenOriginMapPicker: () -> Unit,
    onSelectRecentOriginLocation: (PlaceSearchResultDto) -> Unit,
    onDestinationQueryChanged: (String) -> Unit,
    onSelectDestination: (PlaceSearchResultDto) -> Unit,
    onOpenDestMapPicker: () -> Unit,
    onSelectRecentDestLocation: (PlaceSearchResultDto) -> Unit,
    onSetTravelMode: (String) -> Unit,
    onSetCorridorDistanceKm: (Double) -> Unit,
    onPlanRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRecentOriginMenu by remember { mutableStateOf(false) }
    var showRecentDestMenu by remember { mutableStateOf(false) }
    var showSearchFields by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Plan Route & Safety",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(AppIcons.Back, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
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
                item(key = "error_banner") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppErrorBanner(
                            message = state.errorMessage ?: "",
                            onDismiss = onClearError
                        )
                        val err = state.errorMessage ?: ""
                        val isTrailFailure = err.contains("hiking trail", ignoreCase = true) ||
                                err.contains("Draw my route", ignoreCase = true) ||
                                err.contains("alpine terrain", ignoreCase = true) ||
                                err.contains("unmapped", ignoreCase = true)

                        if (isTrailFailure) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = onNavigateToDrawRoute,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MintPrimary,
                                        contentColor = DarkBackground
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(AppIcons.DrawRoute, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Switch to 'Draw my route' on Map", fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onRetryWithTravelMode("walking") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, DarkDivider)
                                    ) {
                                        Text("Walking / Road", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                                    }
                                    if (!state.isPedestrianOnlySelected) {
                                        OutlinedButton(
                                            onClick = { onRetryWithTravelMode("driving") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(1.dp, DarkDivider)
                                        ) {
                                            Text("Driving Route", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                                        }
                                    }
                                }
                            }
                        }

                        if (state.errorMessage?.contains("10.0.2.2") == true ||
                            state.errorMessage?.contains("failed to connect") == true ||
                            state.errorMessage?.contains("Failed to connect") == true ||
                            state.errorMessage?.contains("Connection refused") == true) {
                            Button(
                                onClick = onSwitchToRenderAndRetry,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintPrimary,
                                    contentColor = DarkBackground
                                )
                            ) {
                                Text("Switch to Live Cloud & Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 1: Presets (Screen 3 Reference Design)
            item(key = "section_presets") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = verifiedCorridors,
                            key = { it.label }
                        ) { corridor ->
                            val isSelected = state.destinationName == corridor.destName && state.originName == corridor.originName
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectCorridor(corridor) },
                                label = {
                                    Text(
                                        corridor.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = if (isSelected) null else BorderStroke(1.dp, DarkDivider),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = DarkSurfacePrimary,
                                    labelColor = TextSecondary,
                                    selectedContainerColor = MintPrimary,
                                    selectedLabelColor = DarkBackground
                                )
                            )
                        }
                    }
                }
            }

            // Section 2: Actions (Screen 3 Reference Design)
            item(key = "section_actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Action Card 1: Choose Directly on Live Map
                    PlanActionCard(
                        title = "Choose Directly on Live Map",
                        subtitle = "Choose directly on Live Map",
                        buttonText1 = "Open Map",
                        onButtonClick1 = onNavigateToChooseOnMap,
                        buttonText2 = "Draw",
                        onButtonClick2 = onNavigateToDrawRoute,
                        rightGraphicIcon = Icons.Default.LocationOn
                    )

                    // Action Card 2: Draw Custom Route with Elevation Graph
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurfacePrimary,
                        border = BorderStroke(1.dp, DarkDivider)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Draw Custom Route",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Elevation graph route",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))

                                // Decorative Elevation Curve Vector
                                Box(
                                    modifier = Modifier
                                        .size(64.dp, 36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x33263332))
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val wavePath = Path().apply {
                                            moveTo(0f, h * 0.8f)
                                            cubicTo(w * 0.3f, h * 0.8f, w * 0.4f, h * 0.2f, w * 0.7f, h * 0.3f)
                                            cubicTo(w * 0.85f, h * 0.35f, w * 0.9f, h * 0.1f, w, h * 0.2f)
                                        }
                                        drawPath(wavePath, color = MintSecondary, style = Stroke(width = 4f))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = onNavigateToDrawRoute,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintPrimary,
                                    contentColor = DarkBackground
                                )
                            ) {
                                Icon(AppIcons.DrawRoute, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Draw", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 3: Inputs (Screen 3 Reference Design)
            item(key = "section_inputs") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Inputs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        TextButton(onClick = { showSearchFields = !showSearchFields }) {
                            Text(
                                if (showSearchFields) "Hide Search" else "Search / GPS",
                                color = MintPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Connected Timeline Container (Green dot, dashed line, red pin)
                    RouteInputsContainer(
                        originText = state.originName,
                        destinationText = state.destinationName,
                        onOriginClick = {
                            if (!showSearchFields) showSearchFields = true
                            onOpenOriginMapPicker()
                        },
                        onDestinationClick = {
                            if (!showSearchFields) showSearchFields = true
                            onOpenDestMapPicker()
                        },
                        onClearOrigin = { onOriginQueryChanged("") },
                        onClearDestination = { onDestinationQueryChanged("") }
                    )

                    // Expandable Detailed Search & GPS Fields
                    if (showSearchFields) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurfaceSecondary,
                            border = BorderStroke(1.dp, DarkDivider)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Origin Search Box
                                OutlinedTextField(
                                    value = state.originName,
                                    onValueChange = onOriginQueryChanged,
                                    placeholder = { Text("Search starting point or village...", color = TextSecondary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = SafeGreen)
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = onUseCurrentLocation,
                                                enabled = !state.isLocating
                                            ) {
                                                if (state.isLocating) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MintPrimary)
                                                } else {
                                                    Icon(AppIcons.MyLocation, contentDescription = "Use GPS", tint = MintPrimary)
                                                }
                                            }
                                            IconButton(onClick = onOpenOriginMapPicker) {
                                                Icon(AppIcons.Map, contentDescription = "Pick on Map", tint = MintPrimary)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkSurfacePrimary,
                                        unfocusedContainerColor = DarkSurfacePrimary,
                                        focusedBorderColor = MintPrimary,
                                        unfocusedBorderColor = DarkDivider,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    singleLine = true
                                )

                                // Suggestions for Origin
                                if (state.originSuggestions.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = DarkSurfaceElevated,
                                        border = BorderStroke(1.dp, DarkDivider)
                                    ) {
                                        Column {
                                            state.originSuggestions.forEach { place ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { onSelectOrigin(place) }
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(16.dp), tint = MintPrimary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(place.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextPrimary)
                                                        Text(place.display_name, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                                                    }
                                                }
                                                HorizontalDivider(thickness = 0.5.dp, color = DarkDivider)
                                            }
                                        }
                                    }
                                }

                                // Destination Search Box
                                OutlinedTextField(
                                    value = state.destinationName,
                                    onValueChange = onDestinationQueryChanged,
                                    placeholder = { Text("Search destination, summit or temple...", color = TextSecondary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = CriticalRed)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = onOpenDestMapPicker) {
                                            Icon(AppIcons.Map, contentDescription = "Pick on Map", tint = MintPrimary)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkSurfacePrimary,
                                        unfocusedContainerColor = DarkSurfacePrimary,
                                        focusedBorderColor = MintPrimary,
                                        unfocusedBorderColor = DarkDivider,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    singleLine = true
                                )

                                // Suggestions for Destination
                                if (state.destSuggestions.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = DarkSurfaceElevated,
                                        border = BorderStroke(1.dp, DarkDivider)
                                    ) {
                                        Column {
                                            state.destSuggestions.forEach { place ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { onSelectDestination(place) }
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp), tint = CriticalRed)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(place.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextPrimary)
                                                        Text(place.display_name, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                                                    }
                                                }
                                                HorizontalDivider(thickness = 0.5.dp, color = DarkDivider)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Travel Mode & Safety Corridor Buffer
            item(key = "section_mode_and_buffer") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfacePrimary,
                    border = BorderStroke(1.dp, DarkDivider)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Travel Mode",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modes = listOf(
                                Triple("hiking", "Hiking", AppIcons.Hiking),
                                Triple("walking", "Walking", Icons.Default.DirectionsWalk),
                                Triple("driving", "Driving", AppIcons.Driving)
                            )
                            modes.forEach { (mode, label, icon) ->
                                val isDrivingDisabled = mode == "driving" && state.isPedestrianOnlySelected
                                val isSelected = state.travelMode == mode
                                Button(
                                    onClick = { onSetTravelMode(mode) },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(19.dp),
                                    enabled = !isDrivingDisabled,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) MintPrimary else DarkSurfaceElevated,
                                        contentColor = if (isSelected) DarkBackground else TextPrimary
                                    ),
                                    border = if (isSelected) null else BorderStroke(1.dp, DarkDivider),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DarkDivider, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Safety Corridor Buffer",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${state.corridorDistanceKm.toInt()} km radius",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val buffers = listOf(1.0, 2.0, 5.0, 10.0)
                            buffers.forEach { km ->
                                val isSelected = state.corridorDistanceKm == km
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSetCorridorDistanceKm(km) },
                                    shape = RoundedCornerShape(16.dp),
                                    label = { Text("${km.toInt()} km", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    border = if (isSelected) null else BorderStroke(1.dp, DarkDivider),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = DarkSurfaceElevated,
                                        labelColor = TextSecondary,
                                        selectedContainerColor = MintPrimary,
                                        selectedLabelColor = DarkBackground
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Section 5: Calculate / Action Area
            item(key = "section_plan_action") {
                if (state.isPlanning) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurfacePrimary,
                        border = BorderStroke(1.dp, DarkDivider)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MintPrimary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                state.planningPhase.ifBlank { "Scanning Route & Safety Radar..." },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MintPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Correlating weather, fire and disaster hazards along trail...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onPlanRoute,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MintPrimary,
                            contentColor = DarkBackground
                        ),
                        enabled = state.destinationName.isNotBlank() && state.originName.isNotBlank()
                    ) {
                        Icon(AppIcons.StartTrek, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan & Evaluate Route Safety",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Preview(name = "Phone - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun PlanTripPreviewDark() {
    TouristSafetyTheme(darkTheme = true) {
        PlanTripContent(
            state = PlanTripUiState(
                originName = "Gaurikund Base",
                destinationName = "Kedarnath Temple",
                travelMode = "hiking",
                corridorDistanceKm = 5.0
            ),
            verifiedCorridors = defaultVerifiedCorridors,
            onNavigateBack = {},
            onNavigateToDrawRoute = {},
            onClearError = {},
            onRetryWithTravelMode = {},
            onSwitchToRenderAndRetry = {},
            onSelectCorridor = {},
            onOriginQueryChanged = {},
            onSelectOrigin = {},
            onUseCurrentLocation = {},
            onOpenOriginMapPicker = {},
            onSelectRecentOriginLocation = {},
            onDestinationQueryChanged = {},
            onSelectDestination = {},
            onOpenDestMapPicker = {},
            onSelectRecentDestLocation = {},
            onSetTravelMode = {},
            onSetCorridorDistanceKm = {},
            onPlanRoute = {}
        )
    }
}
