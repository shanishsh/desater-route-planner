package com.touristsafety.app.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.database.entity.ActiveTripEntity
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import com.touristsafety.app.core.database.entity.TrekTrackPointEntity
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject

data class CompletedTrekDetailUiState(
    val tripId: String = "",
    val routeTitle: String = "Completed Trek",
    val originName: String = "",
    val destinationName: String = "",
    val startedAt: String = "",
    val traveledDistanceKm: Double = 0.0,
    val durationFormatted: String = "00:00",
    val recordedPointsCount: Int = 0,
    val trackCoordinates: List<Pair<Double, Double>> = emptyList(),
    val plannedRouteCoordinates: List<Pair<Double, Double>> = emptyList(),
    val startCoord: Pair<Double, Double>? = null,
    val finishCoord: Pair<Double, Double>? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class CompletedTrekDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository
) : ViewModel() {

    val tripId: String = savedStateHandle["tripId"] ?: ""

    private val _uiState = MutableStateFlow(CompletedTrekDetailUiState(tripId = tripId))
    val uiState: StateFlow<CompletedTrekDetailUiState> = _uiState.asStateFlow()

    init {
        loadTrekDetails()
    }

    private fun loadTrekDetails() {
        if (tripId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val trip = routeRepository.getTripById(tripId)
            val points = routeRepository.getTrackPointsForTrip(tripId)
            val route = if (trip != null) routeRepository.getRouteById(trip.routeId) else null

            val acceptedPoints = points.filter { it.isAcceptedForDistance }
            val trackCoords = if (acceptedPoints.isNotEmpty()) {
                acceptedPoints.map { it.latitude to it.longitude }
            } else {
                points.map { it.latitude to it.longitude }
            }

            // Extract planned route coordinates from routeGeoJson if available
            val plannedCoords = mutableListOf<Pair<Double, Double>>()
            route?.routeGeoJson?.let { geoJson ->
                try {
                    val obj = JSONObject(geoJson)
                    val coordsArr = obj.optJSONArray("coordinates")
                    if (coordsArr != null) {
                        for (i in 0 until coordsArr.length()) {
                            val pt = coordsArr.getJSONArray(i)
                            plannedCoords.add(pt.getDouble(1) to pt.getDouble(0))
                        }
                    }
                } catch (_: Exception) {}
            }

            val distKm = (trip?.traveledDistanceMeters ?: 0.0) / 1000.0
            val durSec = trip?.elapsedDurationSeconds ?: 0L
            val hrs = durSec / 3600
            val mins = (durSec % 3600) / 60
            val durStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"

            _uiState.update {
                it.copy(
                    isLoading = false,
                    routeTitle = trip?.routeTitle?.ifBlank { route?.title } ?: "Expedition Trek",
                    originName = trip?.originName?.ifBlank { route?.originName } ?: "",
                    destinationName = trip?.destinationName?.ifBlank { route?.destinationName } ?: "",
                    startedAt = trip?.startedAt ?: "",
                    traveledDistanceKm = distKm,
                    durationFormatted = durStr,
                    recordedPointsCount = points.size,
                    trackCoordinates = trackCoords,
                    plannedRouteCoordinates = plannedCoords,
                    startCoord = trackCoords.firstOrNull() ?: plannedCoords.firstOrNull(),
                    finishCoord = trackCoords.lastOrNull() ?: plannedCoords.lastOrNull()
                )
            }
        }
    }
}
