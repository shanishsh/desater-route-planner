package com.touristsafety.app.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.model.FireHotspot
import com.touristsafety.app.core.model.ForestFeature
import com.touristsafety.app.core.model.Itinerary
import com.touristsafety.app.core.model.MapPoint
import com.touristsafety.app.core.repository.EmergencyRepository
import com.touristsafety.app.core.repository.FireHotspotRepository
import com.touristsafety.app.core.repository.ForestRepository
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
    val forestFeatures: List<ForestFeature> = emptyList(),
    val fireHotspots: List<FireHotspot> = emptyList(),
    val selectedPoint: MapPoint? = null,
    val selectedForest: ForestFeature? = null,
    val selectedFire: FireHotspot? = null,
    val showWeatherLayer: Boolean = true,
    val showForestLayer: Boolean = true,
    val showFireLayer: Boolean = true,
    val showEmergencyLayer: Boolean = true,
    val isOfflineMode: Boolean = false
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val itineraryRepository: ItineraryRepository,
    private val emergencyRepository: EmergencyRepository,
    private val forestRepository: ForestRepository,
    private val fireHotspotRepository: FireHotspotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        observeData()
        refreshLayers()
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

        viewModelScope.launch {
            forestRepository.getForestFeatures().collect { forests ->
                _uiState.update { it.copy(forestFeatures = forests) }
            }
        }

        viewModelScope.launch {
            fireHotspotRepository.getHotspots().collect { fires ->
                _uiState.update { it.copy(fireHotspots = fires) }
            }
        }
    }

    fun refreshLayers() {
        viewModelScope.launch {
            forestRepository.refreshForestFeatures()
            fireHotspotRepository.refreshHotspots()
        }
    }

    fun toggleForestLayer() {
        _uiState.update { it.copy(showForestLayer = !it.showForestLayer) }
    }

    fun toggleFireLayer() {
        _uiState.update { it.copy(showFireLayer = !it.showFireLayer) }
    }

    fun toggleEmergencyLayer() {
        _uiState.update { it.copy(showEmergencyLayer = !it.showEmergencyLayer) }
    }

    fun selectPoint(point: MapPoint?) {
        _uiState.update { it.copy(selectedPoint = point, selectedForest = null, selectedFire = null) }
    }

    fun selectForest(forest: ForestFeature?) {
        _uiState.update { it.copy(selectedForest = forest, selectedPoint = null, selectedFire = null) }
    }

    fun selectFire(fire: FireHotspot?) {
        _uiState.update { it.copy(selectedFire = fire, selectedPoint = null, selectedForest = null) }
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
