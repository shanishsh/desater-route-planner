package com.touristsafety.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.repository.AlertRepository
import com.touristsafety.app.core.repository.EmergencyRepository
import com.touristsafety.app.core.repository.ItineraryRepository
import com.touristsafety.app.core.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val itineraryRepository: ItineraryRepository,
    private val alertRepository: AlertRepository,
    private val weatherRepository: WeatherRepository,
    private val emergencyRepository: EmergencyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeData()
        refreshAll()
    }

    private fun observeData() {
        viewModelScope.launch {
            itineraryRepository.getItineraries().collect { itineraries ->
                val active = itineraries.firstOrNull()
                _uiState.update { current ->
                    current.copy(
                        selectedItinerary = active,
                        isLoading = false
                    )
                }
                if (active != null) {
                    observeWeather(active.startLatitude, active.startLongitude)
                }
            }
        }

        viewModelScope.launch {
            alertRepository.getAlerts().collect { alerts ->
                _uiState.update { it.copy(alerts = alerts) }
            }
        }
    }

    private fun observeWeather(lat: Double, lon: Double) {
        viewModelScope.launch {
            weatherRepository.getWeather(lat, lon).collect { weather ->
                _uiState.update { it.copy(weather = weather) }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val itinResult = itineraryRepository.refreshItineraries()
            val alertResult = alertRepository.refreshAlerts()
            val emergencyResult = emergencyRepository.refreshMapPoints()

            val activeItin = _uiState.value.selectedItinerary
            if (activeItin != null) {
                weatherRepository.refreshWeather(activeItin.startLatitude, activeItin.startLongitude)
            } else {
                // Default coordinates if no itinerary loaded yet
                weatherRepository.refreshWeather(32.2396, 77.1887)
            }

            val isOffline = itinResult.isFailure && alertResult.isFailure
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    isOffline = isOffline,
                    lastSyncTimestamp = System.currentTimeMillis(),
                    errorMessage = if (isOffline) "Unable to reach server. Showing cached data." else null
                )
            }
        }
    }
}

