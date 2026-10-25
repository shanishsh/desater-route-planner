package com.touristsafety.app.feature.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.model.ProviderStatus
import com.touristsafety.app.core.network.DetailedConnectivityState
import com.touristsafety.app.core.network.NetworkConnectivityMonitor
import com.touristsafety.app.core.network.ServerConfigManager
import com.touristsafety.app.core.network.TelephonyContextManager
import com.touristsafety.app.core.network.TelephonyCoverageState
import com.touristsafety.app.core.repository.ProviderStatusRepository
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderStatusUiState(
    val providers: List<ProviderStatus> = emptyList(),
    val connectivity: DetailedConnectivityState = DetailedConnectivityState(),
    val telephony: TelephonyCoverageState? = null,
    val currentBaseUrl: String = "",
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val activeRouteTitle: String? = null,
    val geometryDiagnostics: Map<String, String> = emptyMap()
)

@HiltViewModel
class ProviderStatusViewModel @Inject constructor(
    private val providerStatusRepository: ProviderStatusRepository,
    private val routeRepository: RouteRepository,
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val telephonyManager: TelephonyContextManager,
    private val serverConfigManager: ServerConfigManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProviderStatusUiState(
            currentBaseUrl = serverConfigManager.getCurrentUrl(),
            telephony = null
        )
    )
    val uiState: StateFlow<ProviderStatusUiState> = _uiState.asStateFlow()

    init {
        observeData()
        refreshAll()
    }

    private fun observeData() {
        viewModelScope.launch {
            providerStatusRepository.getProviderStatuses().collect { list ->
                _uiState.value = _uiState.value.copy(providers = list)
            }
        }

        viewModelScope.launch {
            connectivityMonitor.state.collect { conn ->
                _uiState.value = _uiState.value.copy(connectivity = conn)
            }
        }

        viewModelScope.launch {
            serverConfigManager.baseUrl.collect { url ->
                _uiState.value = _uiState.value.copy(currentBaseUrl = url)
            }
        }

        viewModelScope.launch {
            routeRepository.activeRouteFlow.collect { route ->
                if (route != null) {
                    _uiState.update { current ->
                        current.copy(
                            activeRouteTitle = route.title,
                            geometryDiagnostics = route.geometry_diagnostics.mapValues { it.value.toString() }
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(activeRouteTitle = null, geometryDiagnostics = emptyMap())
                    }
                }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, userMessage = null)
            val result = providerStatusRepository.refreshStatuses()
            connectivityMonitor.probeBackend()
            val localTelephony = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { telephonyManager.getLocalCoverageState() }.getOrNull()
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                telephony = localTelephony,
                userMessage = if (result.isSuccess) "Provider statuses updated." else "Failed to contact backend."
            )
        }
    }

    fun setServerUrl(newUrl: String) {
        serverConfigManager.setBaseUrl(newUrl)
        refreshAll()
    }

    fun triggerUpstreamSmokeRefresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, userMessage = "Triggering live provider tests...")
            val result = providerStatusRepository.triggerProviderRefresh()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                userMessage = if (result.isSuccess) "Live provider tests completed." else "Smoke test request failed."
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }
}
