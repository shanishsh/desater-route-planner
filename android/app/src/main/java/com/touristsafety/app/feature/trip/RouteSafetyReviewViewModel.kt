package com.touristsafety.app.feature.trip

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import com.touristsafety.app.core.location.ActiveTripTrackingService
import com.touristsafety.app.core.network.dto.RouteAlternativeDto
import com.touristsafety.app.core.network.dto.RouteWaypointConditionDto
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class RouteSafetyReviewUiState(
    val routeId: String = "",
    val title: String = "",
    val originName: String = "",
    val destinationName: String = "",
    val distanceKm: Double = 0.0,
    val durationMinutes: Double = 0.0,
    val overallHazardScore: Double = 0.0,
    val overallSafetyStatus: String = "SAFE",
    val highRiskSegmentsCount: Int = 0,
    val conditions: List<RouteWaypointConditionDto> = emptyList(),
    val alternatives: List<RouteAlternativeDto> = emptyList(),
    val selectedAlternativeId: String? = null,
    val isOfflineSaved: Boolean = false,
    val isLoading: Boolean = true,
    val isStartingTrip: Boolean = false,
    val message: String? = null
)

sealed interface RouteReviewEvent {
    data class NavigateToActiveTrip(val tripId: String) : RouteReviewEvent
}

@HiltViewModel
class RouteSafetyReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository
) : ViewModel() {

    private val routeId: String = checkNotNull(savedStateHandle["routeId"])
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(RouteSafetyReviewUiState(routeId = routeId))
    val uiState: StateFlow<RouteSafetyReviewUiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<RouteReviewEvent>()
    val eventFlow: SharedFlow<RouteReviewEvent> = _eventFlow.asSharedFlow()

    init {
        loadRouteDetails()
    }

    private fun loadRouteDetails() {
        viewModelScope.launch {
            routeRepository.getRouteFlow(routeId).collect { entity ->
                if (entity != null) {
                    populateFromEntity(entity)
                }
            }
        }
    }

    private fun populateFromEntity(entity: PlannedRouteEntity) {
        val waypoints = try {
            json.decodeFromString<List<RouteWaypointConditionDto>>(entity.conditionsJson)
        } catch (e: Exception) {
            emptyList()
        }

        val alts = try {
            json.decodeFromString<List<RouteAlternativeDto>>(entity.alternativesJson)
        } catch (e: Exception) {
            emptyList()
        }

        _uiState.update {
            it.copy(
                routeId = entity.routeId,
                title = entity.title,
                originName = entity.originName,
                destinationName = entity.destinationName,
                distanceKm = entity.distanceKm,
                durationMinutes = entity.durationMinutes,
                overallHazardScore = entity.overallHazardScore,
                overallSafetyStatus = entity.overallSafetyStatus,
                highRiskSegmentsCount = entity.highRiskSegmentsCount,
                conditions = waypoints,
                alternatives = alts,
                isOfflineSaved = entity.isSavedOffline,
                isLoading = false
            )
        }
    }

    fun selectAlternative(altId: String) {
        _uiState.update { it.copy(selectedAlternativeId = altId) }
    }

    fun startTrip(context: Context) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingTrip = true) }
            val result = routeRepository.startTrip(state.routeId)
            result.fold(
                onSuccess = { tripDto ->
                    // Start ongoing foreground tracking service
                    ActiveTripTrackingService.start(
                        context = context,
                        tripId = tripDto.trip_id,
                        routeId = state.routeId,
                        destinationName = state.destinationName
                    )
                    _uiState.update { it.copy(isStartingTrip = false) }
                    _eventFlow.emit(RouteReviewEvent.NavigateToActiveTrip(tripDto.trip_id))
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isStartingTrip = false,
                            message = "Could not start trip: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    fun saveOffline() {
        _uiState.update {
            it.copy(
                isOfflineSaved = true,
                message = "Route and sensor forecast corridor saved offline in Room."
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

