package com.touristsafety.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.database.entity.ActiveTripEntity
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import com.touristsafety.app.core.model.Itinerary
import com.touristsafety.app.core.repository.ItineraryRepository
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrekHistoryUiState(
    val selectedTab: Int = 0, // 0 = Completed Treks, 1 = Offline Packs & Saved
    val completedTrips: List<ActiveTripEntity> = emptyList(),
    val plannedRoutes: List<PlannedRouteEntity> = emptyList(),
    val savedItineraries: List<Itinerary> = emptyList(),
    val allItineraries: List<Itinerary> = emptyList(),
    val isLoading: Boolean = false,
    val downloadStatusMessage: String? = null
)

@HiltViewModel
class TrekHistoryViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val itineraryRepository: ItineraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrekHistoryUiState())
    val uiState: StateFlow<TrekHistoryUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    private fun observeData() {
        viewModelScope.launch {
            routeRepository.completedTripsFlow.collect { trips ->
                _uiState.update { it.copy(completedTrips = trips) }
            }
        }

        viewModelScope.launch {
            routeRepository.savedRoutesFlow.collect { routes ->
                _uiState.update { it.copy(plannedRoutes = routes) }
            }
        }

        viewModelScope.launch {
            itineraryRepository.getItineraries().collect { all ->
                _uiState.update { current ->
                    current.copy(
                        allItineraries = all,
                        savedItineraries = all.filter { it.isSavedOffline }
                    )
                }
            }
        }
    }

    fun activateRoute(route: PlannedRouteEntity) {
        routeRepository.activateSavedRoute(route)
    }

    fun deletePlannedRoute(routeId: String) {
        viewModelScope.launch {
            routeRepository.deletePlannedRoute(routeId)
        }
    }

    fun toggleOfflineSave(itineraryId: String, save: Boolean) {
        viewModelScope.launch {
            itineraryRepository.setSavedOffline(itineraryId, save)
        }
    }
}
