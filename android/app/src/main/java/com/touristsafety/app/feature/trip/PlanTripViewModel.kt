package com.touristsafety.app.feature.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.spatial.UttarakhandBoundaryValidator
import com.touristsafety.app.core.location.LocationFixResult
import com.touristsafety.app.core.location.LocationHelper
import com.touristsafety.app.core.network.dto.PlaceSearchResultDto
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MapPickerTarget {
    ORIGIN,
    DESTINATION
}

data class PlanTripUiState(
    val originName: String = "",
    val originLat: Double? = null,
    val originLon: Double? = null,
    val destinationName: String = "",
    val destinationLat: Double? = null,
    val destinationLon: Double? = null,
    val travelMode: String = "hiking",
    val corridorDistanceKm: Double = 5.0,
    val isPedestrianOnlySelected: Boolean = false,
    val regionNotice: String = "Uttarakhand Alpine Safety Radar Active",
    val isLocating: Boolean = false,
    val isSearchingOrigin: Boolean = false,
    val isSearchingDest: Boolean = false,
    val originSuggestions: List<PlaceSearchResultDto> = emptyList(),
    val destSuggestions: List<PlaceSearchResultDto> = emptyList(),
    val recentLocations: List<PlaceSearchResultDto> = emptyList(),
    val isMapPickerOpen: Boolean = false,
    val mapPickerTarget: MapPickerTarget = MapPickerTarget.ORIGIN,
    val isPlanning: Boolean = false,
    val planningPhase: String = "",
    val errorMessage: String? = null
)

sealed interface PlanTripEvent {
    data class NavigateToSafetyReview(val routeId: String) : PlanTripEvent
}

@HiltViewModel
class PlanTripViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val locationHelper: LocationHelper,
    private val boundaryValidator: UttarakhandBoundaryValidator
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanTripUiState())
    val uiState: StateFlow<PlanTripUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<PlanTripEvent>()
    val eventFlow: SharedFlow<PlanTripEvent> = _eventFlow.asSharedFlow()

    private var searchDestJob: Job? = null
    private var searchOriginJob: Job? = null

    init {
        // Load recent endpoints from room database
        viewModelScope.launch {
            routeRepository.getRecentEndpoints().collect { recents ->
                _uiState.update { it.copy(recentLocations = recents) }
            }
        }
        // Attempt current location fix on launch if permission is granted
        useCurrentLocation()
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, errorMessage = null) }
            val fixResult = locationHelper.getCurrentOrLastLocation(timeoutMs = 8000L)
            when (fixResult) {
                is LocationFixResult.Success -> {
                    val loc = fixResult.location
                    if (!boundaryValidator.isPointInUttarakhand(loc.latitude, loc.longitude)) {
                        _uiState.update {
                            it.copy(
                                isLocating = false,
                                originName = "",
                                originLat = null,
                                originLon = null,
                                errorMessage = String.format(
                                    java.util.Locale.US,
                                    "Current GPS (%.4f, %.4f) is outside Uttarakhand. Route planning is strictly scoped to Uttarakhand. Please choose a starting trailhead within the state.",
                                    loc.latitude,
                                    loc.longitude
                                )
                            )
                        }
                    } else {
                        val fallbackName = String.format(java.util.Locale.US, "Current GPS (%.4f, %.4f)", loc.latitude, loc.longitude)
                        _uiState.update {
                            it.copy(
                                originName = fallbackName,
                                originLat = loc.latitude,
                                originLon = loc.longitude,
                                isLocating = false,
                                errorMessage = null
                            )
                        }
                        // Reverse geocode in background to provide readable place name
                        viewModelScope.launch {
                            val rev = routeRepository.reverseGeocode(loc.latitude, loc.longitude)
                            rev.getOrNull()?.let { place ->
                                if (place.display_name.isNotBlank()) {
                                    _uiState.update { it.copy(originName = place.display_name) }
                                }
                            }
                        }
                    }
                }
                is LocationFixResult.PermissionDenied -> {
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            errorMessage = "Location permission denied. Please search or pick a starting point on the map."
                        )
                    }
                }
                is LocationFixResult.LocationServicesDisabled -> {
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            errorMessage = "Location services (GPS) are disabled on your device. Please turn on GPS or pick a point on the map."
                        )
                    }
                }
                is LocationFixResult.Timeout -> {
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            errorMessage = "GPS satellite fix timed out. Please enter origin or pick a starting point on the map."
                        )
                    }
                }
                is LocationFixResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            errorMessage = "GPS fix unavailable: ${fixResult.message}. Please enter origin or pick on map."
                        )
                    }
                }
            }
        }
    }

    fun openMapPicker(target: MapPickerTarget) {
        _uiState.update {
            it.copy(
                isMapPickerOpen = true,
                mapPickerTarget = target,
                errorMessage = null
            )
        }
    }

    fun closeMapPicker() {
        _uiState.update { it.copy(isMapPickerOpen = false) }
    }

    fun onLocationConfirmedFromMap(lat: Double, lon: Double, displayName: String) {
        if (!boundaryValidator.isPointInUttarakhand(lat, lon)) {
            _uiState.update {
                it.copy(
                    errorMessage = String.format(
                        java.util.Locale.US,
                        "Selected point (%.4f, %.4f) is outside Uttarakhand. Please choose a location within the state.",
                        lat,
                        lon
                    )
                )
            }
            return
        }
        val target = _uiState.value.mapPickerTarget
        if (target == MapPickerTarget.ORIGIN) {
            _uiState.update {
                it.copy(
                    originName = displayName,
                    originLat = lat,
                    originLon = lon,
                    originSuggestions = emptyList(),
                    isMapPickerOpen = false,
                    errorMessage = null
                )
            }
        } else {
            val isKedarnath = displayName.contains("Kedarnath", ignoreCase = true)
            _uiState.update {
                it.copy(
                    destinationName = displayName,
                    destinationLat = lat,
                    destinationLon = lon,
                    destSuggestions = emptyList(),
                    travelMode = if (isKedarnath) "hiking" else it.travelMode,
                    isPedestrianOnlySelected = isKedarnath,
                    isMapPickerOpen = false,
                    errorMessage = null
                )
            }
        }
    }

    fun selectRecentLocation(place: PlaceSearchResultDto, isOrigin: Boolean) {
        if (isOrigin) {
            _uiState.update {
                it.copy(
                    originName = place.display_name,
                    originLat = place.latitude,
                    originLon = place.longitude,
                    originSuggestions = emptyList()
                )
            }
        } else {
            val isKedarnath = place.display_name.contains("Kedarnath", ignoreCase = true)
            _uiState.update {
                it.copy(
                    destinationName = place.display_name,
                    destinationLat = place.latitude,
                    destinationLon = place.longitude,
                    destSuggestions = emptyList(),
                    travelMode = if (isKedarnath) "hiking" else it.travelMode,
                    isPedestrianOnlySelected = isKedarnath
                )
            }
        }
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): Result<PlaceSearchResultDto> {
        return routeRepository.reverseGeocode(lat, lon)
    }

    fun setCorridorDistanceKm(km: Double) {
        _uiState.update { it.copy(corridorDistanceKm = km) }
    }

    fun onOriginQueryChanged(query: String) {
        _uiState.update { it.copy(originName = query) }
        searchOriginJob?.cancel()
        if (query.trim().length >= 2 && !query.startsWith("Current GPS")) {
            searchOriginJob = viewModelScope.launch {
                delay(350)
                _uiState.update { it.copy(isSearchingOrigin = true) }
                val res = routeRepository.searchPlaces(query)
                _uiState.update {
                    it.copy(
                        isSearchingOrigin = false,
                        originSuggestions = res.getOrDefault(emptyList())
                    )
                }
            }
        } else {
            _uiState.update { it.copy(originSuggestions = emptyList()) }
        }
    }

    fun selectOrigin(place: PlaceSearchResultDto) {
        if (!boundaryValidator.isPointInUttarakhand(place.latitude, place.longitude)) {
            _uiState.update {
                it.copy(
                    errorMessage = "${place.display_name} is outside Uttarakhand. Please select a starting point within the state."
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                originName = place.display_name,
                originLat = place.latitude,
                originLon = place.longitude,
                originSuggestions = emptyList(),
                errorMessage = null
            )
        }
    }

    fun onDestinationQueryChanged(query: String) {
        _uiState.update { it.copy(destinationName = query) }
        searchDestJob?.cancel()
        if (query.trim().length >= 2) {
            searchDestJob = viewModelScope.launch {
                delay(350)
                _uiState.update { it.copy(isSearchingDest = true) }
                val res = routeRepository.searchPlaces(query)
                _uiState.update {
                    it.copy(
                        isSearchingDest = false,
                        destSuggestions = res.getOrDefault(emptyList())
                    )
                }
            }
        } else {
            _uiState.update { it.copy(destSuggestions = emptyList()) }
        }
    }

    fun selectDestination(place: PlaceSearchResultDto) {
        if (!boundaryValidator.isPointInUttarakhand(place.latitude, place.longitude)) {
            _uiState.update {
                it.copy(
                    errorMessage = "${place.display_name} is outside Uttarakhand. Please select a destination within the state."
                )
            }
            return
        }
        val isKedarnath = place.display_name.contains("Kedarnath", ignoreCase = true)
        _uiState.update {
            it.copy(
                destinationName = place.display_name,
                destinationLat = place.latitude,
                destinationLon = place.longitude,
                destSuggestions = emptyList(),
                travelMode = if (isKedarnath) "hiking" else it.travelMode,
                isPedestrianOnlySelected = isKedarnath,
                errorMessage = null
            )
        }
    }

    fun selectVerifiedCorridor(
        originName: String,
        originLat: Double,
        originLon: Double,
        destName: String,
        destLat: Double,
        destLon: Double,
        mode: String,
        isPedestrianOnly: Boolean
    ) {
        _uiState.update {
            it.copy(
                originName = originName,
                originLat = originLat,
                originLon = originLon,
                destinationName = destName,
                destinationLat = destLat,
                destinationLon = destLon,
                travelMode = mode,
                isPedestrianOnlySelected = isPedestrianOnly,
                originSuggestions = emptyList(),
                destSuggestions = emptyList(),
                errorMessage = null
            )
        }
    }

    fun selectQuickDestination(name: String, lat: Double, lon: Double) {
        val isKedarnath = name.contains("Kedarnath", ignoreCase = true)
        _uiState.update {
            it.copy(
                destinationName = name,
                destinationLat = lat,
                destinationLon = lon,
                destSuggestions = emptyList(),
                travelMode = if (isKedarnath) "hiking" else it.travelMode,
                isPedestrianOnlySelected = isKedarnath,
                errorMessage = null
            )
        }
    }

    fun setTravelMode(mode: String) {
        val state = _uiState.value
        if (state.isPedestrianOnlySelected && mode == "driving") {
            _uiState.update {
                it.copy(
                    errorMessage = "Motor vehicles are strictly prohibited on pedestrian trek corridors (e.g. Kedarnath trail). Please select Hiking or Walking."
                )
            }
            return
        }
        _uiState.update { it.copy(travelMode = mode, errorMessage = null) }
    }

    fun planRoute() {
        val state = _uiState.value
        val originLat = state.originLat
        val originLon = state.originLon
        if (originLat == null || originLon == null || state.originName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please specify a start location via GPS, map picker, or place search.") }
            return
        }

        val destLat = state.destinationLat
        val destLon = state.destinationLon
        if (destLat == null || destLon == null || state.destinationName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please choose a destination via map picker or search.") }
            return
        }

        if (!boundaryValidator.isPointInUttarakhand(originLat, originLon)) {
            _uiState.update {
                it.copy(
                    errorMessage = "Route origin is outside Uttarakhand. Please select a trailhead within Uttarakhand."
                )
            }
            return
        }

        if (!boundaryValidator.isPointInUttarakhand(destLat, destLon)) {
            _uiState.update {
                it.copy(
                    errorMessage = "Route destination is outside Uttarakhand. Please select a destination within Uttarakhand."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPlanning = true,
                    planningPhase = "Calculating polyline and ${state.corridorDistanceKm} km spatial corridor...",
                    errorMessage = null
                )
            }
            delay(350)
            _uiState.update { it.copy(planningPhase = "Correlating Open-Meteo hourly weather with estimated arrival times...") }
            delay(350)
            _uiState.update { it.copy(planningPhase = "Scanning NASA FIRMS fire anomalies, OSM facilities, and alerts...") }

            val result = routeRepository.planRoute(
                originLat = originLat,
                originLon = originLon,
                originName = state.originName,
                destLat = destLat,
                destLon = destLon,
                destName = state.destinationName,
                travelMode = state.travelMode,
                corridorDistanceKm = state.corridorDistanceKm
            )

            result.fold(
                onSuccess = { dto ->
                    _uiState.update { it.copy(isPlanning = false) }
                    _eventFlow.emit(PlanTripEvent.NavigateToSafetyReview(dto.route_id))
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isPlanning = false,
                            errorMessage = "Route scan failed: ${err.message}. Please check connection or retry."
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
