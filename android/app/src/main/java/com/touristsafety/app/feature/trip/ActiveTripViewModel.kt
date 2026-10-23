package com.touristsafety.app.feature.trip

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.database.entity.ActiveTripEntity
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import com.touristsafety.app.core.location.ActiveTripTrackingService
import com.touristsafety.app.core.network.dto.RouteWaypointConditionDto
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ActiveTripUiState(
    val tripId: String = "",
    val routeId: String = "",
    val status: String = "ACTIVE",
    val destinationName: String = "",
    val progressPct: Double = 0.0,
    val isOffRoute: Boolean = false,
    val nearestHazardDistKm: Double? = null,
    val nearestHazardTitle: String? = null,
    val nextWaypoint: RouteWaypointConditionDto? = null,
    val totalDistanceKm: Double = 0.0,
    val isCompleted: Boolean = false
)

@HiltViewModel
class ActiveTripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository
) : ViewModel() {

    val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(ActiveTripUiState(tripId = tripId))
    val uiState: StateFlow<ActiveTripUiState> = _uiState.asStateFlow()

    init {
        observeTrip()
    }

    private fun observeTrip() {
        viewModelScope.launch {
            routeRepository.activeTripFlow.collect { trip ->
                if (trip != null && trip.tripId == tripId) {
                    val route = routeRepository.getRouteById(trip.routeId)
                    val waypoints = if (route != null) {
                        try {
                            json.decodeFromString<List<RouteWaypointConditionDto>>(route.conditionsJson)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else emptyList()

                    val nextWp = if (waypoints.isNotEmpty() && trip.currentSegmentIndex < waypoints.size) {
                        waypoints[minOf(waypoints.size - 1, trip.currentSegmentIndex + 1)]
                    } else null

                    _uiState.update {
                        it.copy(
                            tripId = trip.tripId,
                            routeId = trip.routeId,
                            status = trip.status,
                            destinationName = route?.destinationName ?: "Destination",
                            progressPct = trip.progressPct,
                            isOffRoute = trip.isOffRoute,
                            nearestHazardDistKm = trip.nearestHazardDistKm,
                            nearestHazardTitle = trip.nearestHazardTitle,
                            nextWaypoint = nextWp,
                            totalDistanceKm = route?.distanceKm ?: 0.0,
                            isCompleted = trip.status == "COMPLETED"
                        )
                    }
                }
            }
        }
    }

    fun endTrip(context: Context) {
        viewModelScope.launch {
            routeRepository.stopTrip(tripId)
            ActiveTripTrackingService.stop(context)
            _uiState.update { it.copy(isCompleted = true, status = "COMPLETED") }
        }
    }
}
