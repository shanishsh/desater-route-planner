package com.touristsafety.app.feature.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.location.LocationHelper
import com.touristsafety.app.core.model.MapPoint
import com.touristsafety.app.core.model.PointCategory
import com.touristsafety.app.core.repository.EmergencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmergencyUiState(
    val points: List<MapPoint> = emptyList(),
    val filteredPoints: List<MapPoint> = emptyList(),
    val selectedCategory: PointCategory? = null,
    val isLoading: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val userLatitude: Double? = null,
    val userLongitude: Double? = null
)

@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val emergencyRepository: EmergencyRepository,
    private val locationHelper: LocationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyUiState())
    val uiState: StateFlow<EmergencyUiState> = _uiState.asStateFlow()

    init {
        checkLocation()
        observePoints()
        refresh()
    }

    private fun checkLocation() {
        val hasPerm = locationHelper.hasLocationPermission()
        _uiState.update { it.copy(hasLocationPermission = hasPerm) }
        if (hasPerm) {
            viewModelScope.launch {
                val loc = locationHelper.getLastKnownLocation()
                if (loc != null) {
                    _uiState.update {
                        it.copy(
                            userLatitude = loc.latitude,
                            userLongitude = loc.longitude
                        )
                    }
                }
            }
        }
    }

    private fun observePoints() {
        viewModelScope.launch {
            emergencyRepository.getMapPoints().collect { points ->
                _uiState.update { current ->
                    current.copy(
                        points = points,
                        filteredPoints = if (current.selectedCategory == null) points
                        else points.filter { it.category == current.selectedCategory }
                    )
                }
            }
        }
    }

    fun selectCategory(category: PointCategory?) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                filteredPoints = if (category == null) current.points
                else current.points.filter { it.category == category }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            emergencyRepository.refreshMapPoints()
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}

