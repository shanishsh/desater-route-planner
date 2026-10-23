package com.touristsafety.app.feature.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.model.Alert
import com.touristsafety.app.core.model.AlertCategory
import com.touristsafety.app.core.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AlertFilter {
    ALL, FRESH_ONLY, WEATHER, HAZARD, TRANSPORT
}

data class AlertsUiState(
    val alerts: List<Alert> = emptyList(),
    val filteredAlerts: List<Alert> = emptyList(),
    val activeFilter: AlertFilter = AlertFilter.ALL,
    val isLoading: Boolean = false,
    val isOffline: Boolean = false
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        observeAlerts()
        refresh()
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            alertRepository.getAlerts().collect { list ->
                _uiState.update { current ->
                    current.copy(
                        alerts = list,
                        filteredAlerts = applyFilter(list, current.activeFilter)
                    )
                }
            }
        }
    }

    fun setFilter(filter: AlertFilter) {
        _uiState.update { current ->
            current.copy(
                activeFilter = filter,
                filteredAlerts = applyFilter(current.alerts, filter)
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = alertRepository.refreshAlerts()
            _uiState.update { it.copy(isLoading = false, isOffline = result.isFailure) }
        }
    }

    private fun applyFilter(alerts: List<Alert>, filter: AlertFilter): List<Alert> {
        return when (filter) {
            AlertFilter.ALL -> alerts
            AlertFilter.FRESH_ONLY -> alerts.filter { !it.isStale() }
            AlertFilter.WEATHER -> alerts.filter { it.category == AlertCategory.WEATHER }
            AlertFilter.HAZARD -> alerts.filter { it.category == AlertCategory.HAZARD }
            AlertFilter.TRANSPORT -> alerts.filter { it.category == AlertCategory.TRANSPORT }
        }
    }
}

