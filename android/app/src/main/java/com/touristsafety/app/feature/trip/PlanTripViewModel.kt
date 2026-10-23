package com.touristsafety.app.feature.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class PlanTripUiState(
    val originName: String = "",
    val originLat: Double? = null,
    val originLon: Double? = null,
    val destinationName: String = "",
    val destinationLat: Double? = null,
    val destinationLon: Double? = null,
    val travelMode: String = "driving",
    val isLocating: Boolean = false,
    val isSearchingOrigin: Boolean = false,
    val isSearchingDest: Boolean = false,
    val originSuggestions: List<PlaceSearchResultDto> = emptyList(),
    val destSuggestions: List<PlaceSearchResultDto> = emptyList(),
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
    private val locationHelper: LocationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanTripUiState())
    val uiState: StateFlow<PlanTripUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<PlanTripEvent>()
    val eventFlow: SharedFlow<PlanTripEvent> = _eventFlow.asSharedFlow()

    private var searchDestJob: Job? = null
    private var searchOriginJob: Job? = null

    init {
        // Attempt current location fix on launch if permission is granted
        useCurrentLocation()
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, errorMessage = null) }
            val loc = locationHelper.getLastKnownLocation()
            if (loc != null && loc.latitude != 0.0 && loc.longitude != 0.0) {
                _uiState.update {
                    it.copy(
                        originName = "Current GPS (${String.format(java.util.Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)})",
                        originLat = loc.latitude,
                        originLon = loc.longitude,
                        isLocating = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        originName = "",
                        originLat = null,
                        originLon = null,
                        isLocating = false,
                        errorMessage = "GPS fix unavailable. Please enter origin or search by place name."
                    )
                }
            }
        }
    }

    fun onOriginQueryChanged(query: String) {
        _uiState.update { it.copy(originName = query) }
        searchOriginJob?.cancel()
        if (query.trim().length >= 2 && query != "Current Location") {
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
        _uiState.update {
            it.copy(
                originName = place.display_name,
                originLat = place.latitude,
                originLon = place.longitude,
                originSuggestions = emptyList()
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
        _uiState.update {
            it.copy(
                destinationName = place.display_name,
                destinationLat = place.latitude,
                destinationLon = place.longitude,
                destSuggestions = emptyList()
            )
        }
    }

    fun selectQuickDestination(name: String, lat: Double, lon: Double) {
        _uiState.update {
            it.copy(
                destinationName = name,
                destinationLat = lat,
                destinationLon = lon,
                destSuggestions = emptyList()
            )
        }
    }

    fun setTravelMode(mode: String) {
        _uiState.update { it.copy(travelMode = mode) }
    }

    fun planRoute() {
        val state = _uiState.value
        val originLat = state.originLat
        val originLon = state.originLon
        if (originLat == null || originLon == null || state.originName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please specify a start location via GPS or search.") }
            return
        }

        val destLat = state.destinationLat
        val destLon = state.destinationLon
        if (destLat == null || destLon == null || state.destinationName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please choose a destination first.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPlanning = true,
                    planningPhase = "Calculating road polyline and alpine corridor...",
                    errorMessage = null
                )
            }
            delay(400)
            _uiState.update { it.copy(planningPhase = "Correlating Open-Meteo hourly weather with estimated arrival times...") }
            delay(400)
            _uiState.update { it.copy(planningPhase = "Scanning NASA FIRMS fire anomalies and forest boundaries...") }

            val result = routeRepository.planRoute(
                originLat = originLat,
                originLon = originLon,
                originName = state.originName,
                destLat = destLat,
                destLon = destLon,
                destName = state.destinationName,
                travelMode = state.travelMode
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

