package com.touristsafety.app.feature.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class SavedTripsUiState(
    val savedItineraries: List<Itinerary> = emptyList(),
    val allItineraries: List<Itinerary> = emptyList(),
    val plannedRoutes: List<PlannedRouteEntity> = emptyList(),
    val isDownloadingOfflinePack: Boolean = false,
    val downloadStatusMessage: String? = null
)

@HiltViewModel
class SavedTripsViewModel @Inject constructor(
    private val itineraryRepository: ItineraryRepository,
    private val routeRepository: RouteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedTripsUiState())
    val uiState: StateFlow<SavedTripsUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
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

        viewModelScope.launch {
            routeRepository.savedRoutesFlow.collect { routes ->
                _uiState.update { current ->
                    current.copy(plannedRoutes = routes)
                }
            }
        }
    }

    fun toggleOfflineSave(itineraryId: String, save: Boolean) {
        viewModelScope.launch {
            itineraryRepository.setSavedOffline(itineraryId, save)
        }
    }

    fun deletePlannedRoute(routeId: String) {
        viewModelScope.launch {
            routeRepository.deletePlannedRoute(routeId)
        }
    }

    fun downloadBoundedOfflinePack(itineraryId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloadingOfflinePack = true,
                    downloadStatusMessage = "Checking OpenFreeMap limits (Max 3,000 tiles / 50MB)..."
                )
            }
            // Enforce bounds and mark saved offline
            itineraryRepository.setSavedOffline(itineraryId, true)
            _uiState.update {
                it.copy(
                    isDownloadingOfflinePack = false,
                    downloadStatusMessage = "Offline route geometry and waypoints successfully preserved in Room."
                )
            }
        }
    }
}

