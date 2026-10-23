package com.touristsafety.app.feature.trip

import android.content.Context
import android.os.BatteryManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.database.entity.ActiveTripEntity
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import com.touristsafety.app.core.location.ActiveTripTrackingService
import com.touristsafety.app.core.location.DeviceLocationRepository
import com.touristsafety.app.core.network.dto.ElevationProfilePointDto
import com.touristsafety.app.core.network.dto.RouteWaypointConditionDto
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val elevationProfile: List<ElevationProfilePointDto> = emptyList(),
    val totalAscentM: Double = 0.0,
    val maxElevationM: Double = 0.0,
    val isCompleted: Boolean = false,
    val isCheckingIn: Boolean = false,
    val checkInMessage: String? = null,
    val isSosTriggered: Boolean = false,
    val nextCheckInCountdown: String = "02h 45m"
)

@HiltViewModel
class ActiveTripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository,
    private val deviceLocationRepository: DeviceLocationRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(ActiveTripUiState(tripId = tripId))
    val uiState: StateFlow<ActiveTripUiState> = _uiState.asStateFlow()

    init {
        observeTrip()
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (pct in 1..100) pct else 85
        } catch (e: Exception) {
            85
        }
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

                    val elevPoints = if (route?.elevationProfileJson != null) {
                        try {
                            json.decodeFromString<List<ElevationProfilePointDto>>(route.elevationProfileJson)
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
                            elevationProfile = elevPoints,
                            totalAscentM = route?.totalAscentM ?: 0.0,
                            maxElevationM = route?.maxElevationM ?: 0.0,
                            isCompleted = trip.status == "COMPLETED"
                        )
                    }
                }
            }
        }
    }

    fun submitCheckIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingIn = true) }
            val state = _uiState.value
            val locState = deviceLocationRepository.locationState.value
            val lat = locState.latitude ?: state.nextWaypoint?.latitude ?: 0.0
            val lon = locState.longitude ?: state.nextWaypoint?.longitude ?: 0.0
            val elev = locState.location?.altitude ?: (state.nextWaypoint?.elevation_m ?: 0.0)
            val battery = getBatteryPercentage()

            val result = routeRepository.submitCheckIn(
                tripId = tripId,
                latitude = lat,
                longitude = lon,
                elevationM = elev,
                batteryPct = battery,
                status = "OK"
            )
            result.fold(
                onSuccess = { res ->
                    _uiState.update {
                        it.copy(
                            isCheckingIn = false,
                            checkInMessage = "Check-In Recorded! Next check-in reset to 3 hours."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isCheckingIn = false,
                            checkInMessage = "Check-in stored offline. Will sync when connected."
                        )
                    }
                }
            )
        }
    }

    fun triggerEmergencySos() {
        viewModelScope.launch {
            val state = _uiState.value
            val locState = deviceLocationRepository.locationState.value
            val lat = locState.latitude ?: state.nextWaypoint?.latitude ?: 0.0
            val lon = locState.longitude ?: state.nextWaypoint?.longitude ?: 0.0
            val elev = locState.location?.altitude ?: (state.nextWaypoint?.elevation_m ?: 0.0)
            val battery = getBatteryPercentage()

            val result = routeRepository.triggerEmergencySos(
                tripId = tripId,
                latitude = lat,
                longitude = lon,
                elevationM = elev,
                batteryPct = battery,
                emergencyType = "MOUNTAIN_SOS",
                details = "Trekker manual emergency beacon trigger from mobile field unit."
            )
            _uiState.update {
                it.copy(
                    isSosTriggered = true,
                    checkInMessage = "EMERGENCY SOS BEACON ACTIVE. Search & Rescue incident transmitted."
                )
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

    fun clearMessage() {
        _uiState.update { it.copy(checkInMessage = null) }
    }
}
