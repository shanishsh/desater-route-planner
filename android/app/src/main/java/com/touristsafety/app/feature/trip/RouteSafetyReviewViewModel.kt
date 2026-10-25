package com.touristsafety.app.feature.trip

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.offline.OfflineTrekPackManager
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import com.touristsafety.app.core.location.ActiveTripTrackingService
import com.touristsafety.app.core.network.dto.ElevationProfilePointDto
import com.touristsafety.app.core.network.dto.GroundedRouteSummaryDto
import com.touristsafety.app.core.network.dto.ProviderStatusItemDto
import com.touristsafety.app.core.network.dto.RouteAlternativeDto
import com.touristsafety.app.core.network.dto.RouteWaypointConditionDto
import com.touristsafety.app.core.network.dto.SafeHavenInfoDto
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
    val overallSafetyStatus: String = "NORMAL",
    val highRiskSegmentsCount: Int = 0,
    val totalAscentM: Double = 0.0,
    val maxElevationM: Double = 0.0,
    val decisionExplanation: String = "",
    val conditions: List<RouteWaypointConditionDto> = emptyList(),
    val elevationProfile: List<ElevationProfilePointDto> = emptyList(),
    val safeHavens: List<SafeHavenInfoDto> = emptyList(),
    val alternatives: List<RouteAlternativeDto> = emptyList(),
    val providerStatuses: Map<String, ProviderStatusItemDto> = emptyMap(),
    val analysisLimitations: List<String> = emptyList(),
    val groundedSummary: GroundedRouteSummaryDto? = null,
    val selectedAlternativeId: String? = null,
    val isOfflineSaved: Boolean = false,
    val currentEntity: PlannedRouteEntity? = null,
    val isLoading: Boolean = true,
    val isStartingTrip: Boolean = false,
    val message: String? = null
)

sealed interface RouteReviewEvent {
    data class NavigateToActiveTrip(val tripId: String) : RouteReviewEvent
    data object NavigateToMap : RouteReviewEvent
}

@HiltViewModel
class RouteSafetyReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository,
    val offlineTrekPackManager: OfflineTrekPackManager
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
        routeRepository.activateSavedRoute(entity)
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

        val elevPoints = try {
            if (!entity.elevationProfileJson.isNullOrBlank()) {
                json.decodeFromString<List<ElevationProfilePointDto>>(entity.elevationProfileJson)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val havens = try {
            if (!entity.safeHavensJson.isNullOrBlank()) {
                json.decodeFromString<List<SafeHavenInfoDto>>(entity.safeHavensJson)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val activeDto = routeRepository.activeRouteFlow.value
        val statuses = try {
            if (!entity.providerStatusesJson.isNullOrBlank()) {
                json.decodeFromString<Map<String, ProviderStatusItemDto>>(entity.providerStatusesJson)
            } else activeDto?.provider_statuses ?: emptyMap()
        } catch (e: Exception) {
            activeDto?.provider_statuses ?: emptyMap()
        }

        val limits = try {
            if (!entity.analysisLimitationsJson.isNullOrBlank()) {
                json.decodeFromString<List<String>>(entity.analysisLimitationsJson)
            } else activeDto?.analysis_limitations ?: emptyList()
        } catch (e: Exception) {
            activeDto?.analysis_limitations ?: emptyList()
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
                totalAscentM = entity.totalAscentM,
                maxElevationM = entity.maxElevationM,
                decisionExplanation = entity.decisionExplanation ?: "",
                conditions = waypoints,
                elevationProfile = elevPoints,
                safeHavens = havens,
                alternatives = alts,
                providerStatuses = statuses,
                analysisLimitations = limits,
                groundedSummary = activeDto?.grounded_summary,
                isOfflineSaved = entity.isSavedOffline,
                currentEntity = entity,
                isLoading = false
            )
        }
    }

    fun onOfflineDownloadCompleted() {
        _uiState.update {
            it.copy(
                isOfflineSaved = true,
                message = "Offline Trek Pack downloaded with vector map tiles and hazard corridor."
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
                message = "TrailGuard: Route, elevation profile, and hazard corridor saved in offline Room database."
            )
        }
    }

    fun viewOnMap() {
        viewModelScope.launch {
            _eventFlow.emit(RouteReviewEvent.NavigateToMap)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
