package com.touristsafety.app.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.model.Itinerary
import com.touristsafety.app.core.model.MapPoint
import com.touristsafety.app.core.repository.EmergencyRepository
import com.touristsafety.app.core.repository.ItineraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class MapUiState(
    val selectedItinerary: Itinerary? = null,
    val routeCoordinates: List<Pair<Double, Double>> = emptyList(),
    val mapPoints: List<MapPoint> = emptyList(),
    val selectedPoint: MapPoint? = null,
    val isOfflineMode: Boolean = false
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val itineraryRepository: ItineraryRepository,
    private val emergencyRepository: EmergencyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            itineraryRepository.getItineraries().collect { itineraries ->
                val active = itineraries.firstOrNull()
                val coords = parseRouteGeoJson(active?.routeGeoJson)
                _uiState.update {
                    it.copy(
                        selectedItinerary = active,
                        routeCoordinates = coords
                    )
                }
            }
        }

        viewModelScope.launch {
            emergencyRepository.getMapPoints().collect { points ->
                _uiState.update { it.copy(mapPoints = points) }
            }
        }
    }

    fun selectPoint(point: MapPoint?) {
        _uiState.update { it.copy(selectedPoint = point) }
    }

    private fun parseRouteGeoJson(geoJsonString: String?): List<Pair<Double, Double>> {
        if (geoJsonString.isNullOrBlank()) return emptyList()
        val coordinates = mutableListOf<Pair<Double, Double>>()
        try {
            val root = JSONObject(geoJsonString)
            val features = root.optJSONArray("features") ?: return emptyList()
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val geometry = feature.getJSONObject("geometry")
                val type = geometry.getString("type")
                if (type == "LineString") {
                    val coordsArray = geometry.getJSONArray("coordinates")
                    for (j in 0 until coordsArray.length()) {
                        val pt = coordsArray.getJSONArray(j)
                        val lon = pt.getDouble(0)
                        val lat = pt.getDouble(1)
                        coordinates.add(lat to lon)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors on malformed GeoJSON
        }
        return coordinates
    }
}

