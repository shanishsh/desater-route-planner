package com.touristsafety.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.network.NetworkConnectivityMonitor
import com.touristsafety.app.core.network.NetworkStatus
import com.touristsafety.app.core.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val itineraryRepository: ItineraryRepository,
    private val alertRepository: AlertRepository,
    private val weatherRepository: WeatherRepository,
    private val emergencyRepository: EmergencyRepository,
    private val providerStatusRepository: ProviderStatusRepository,
    private val disasterRepository: DisasterRepository,
    private val connectivityMonitor: NetworkConnectivityMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val isSirenPlaying: StateFlow<Boolean> = disasterRepository.isSirenPlaying

    private var weatherJob: Job? = null

    init {
        // Observe default coordinates immediately so weather renders even before itineraries finish loading
        observeWeather(32.2396, 77.1887)
        observeData()
        refreshAll()
    }

    private fun observeData() {
        viewModelScope.launch {
            connectivityMonitor.state.collect { conn ->
                val isOffline = conn.status == NetworkStatus.NO_NETWORK || conn.status == NetworkStatus.CONNECTED_NO_INTERNET
                _uiState.update { it.copy(isOffline = isOffline) }
            }
        }

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
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            weatherRepository.getWeather(lat, lon).collect { weather ->
                _uiState.update { it.copy(weather = weather) }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            connectivityMonitor.probeBackend()

            val itinResult = itineraryRepository.refreshItineraries()
            val alertResult = alertRepository.refreshAlerts()
            val emergencyResult = emergencyRepository.refreshMapPoints()
            providerStatusRepository.refreshStatuses()

            val activeItin = _uiState.value.selectedItinerary
            if (activeItin != null) {
                weatherRepository.refreshWeather(activeItin.startLatitude, activeItin.startLongitude)
            } else {
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

    fun stopDisasterSiren() {
        disasterRepository.stopDisasterSiren()
    }
}

