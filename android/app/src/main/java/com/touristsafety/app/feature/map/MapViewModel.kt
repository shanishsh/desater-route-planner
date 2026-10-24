package com.touristsafety.app.feature.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.map.ClusterItem
import com.touristsafety.app.core.map.MapClusterHelper
import com.touristsafety.app.core.model.*
import com.touristsafety.app.core.location.DeviceLocationRepository
import com.touristsafety.app.core.network.NetworkConnectivityMonitor
import com.touristsafety.app.core.network.NetworkStatus
import com.touristsafety.app.core.network.dto.PlaceSearchResultDto
import com.touristsafety.app.core.network.dto.PlannedRouteResponseDto
import com.touristsafety.app.core.network.dto.RouteAlternativeDto
import com.touristsafety.app.core.network.dto.RouteAnalysisResponseDto
import com.touristsafety.app.core.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.json.JSONObject
import javax.inject.Inject

enum class RouteLifecycleState {
    NO_ROUTE_SELECTED,
    CALCULATING_ROUTE,
    ROUTE_AVAILABLE,
    ANALYZING_CORRIDOR,
    PARTIALLY_ANALYZED,
    ANALYZED_WITH_FEATURES,
    ANALYZED_EMPTY,
    OFFLINE_CACHED,
    ANALYSIS_FAILED
}

enum class MapFilterChip(val label: String, val category: MapFeatureCategory?) {
    ALL("All", null),
    MEDICAL("Hospitals", MapFeatureCategory.HOSPITAL),
    CLINICS("Clinics", MapFeatureCategory.CLINIC),
    POLICE("Police", MapFeatureCategory.POLICE_STATION),
    FUEL("Petrol Pumps", MapFeatureCategory.PETROL_PUMP),
    FIRE_STATIONS("Fire Stations", MapFeatureCategory.FIRE_STATION),
    FOREST("Forest", MapFeatureCategory.FOREST_AREA),
    ACTIVE_FIRES("Active Fires", MapFeatureCategory.ACTIVE_FIRE_DETECTION),
    WEATHER("Weather", MapFeatureCategory.WEATHER_POINT),
    DISASTER_ALERTS("Disaster Alerts", MapFeatureCategory.HAZARD_ZONE),
    SHELTERS_ASSISTANCE("Shelters & Info", MapFeatureCategory.SHELTER)
}

data class MapUiState(
    val selectedItinerary: Itinerary? = null,
    val routeLifecycleState: RouteLifecycleState = RouteLifecycleState.NO_ROUTE_SELECTED,
    val activeRouteId: String? = null,
    val activeRouteVersion: Int = 1,
    val routeTitle: String = "Explore Map",
    val routeCoordinates: List<Pair<Double, Double>> = emptyList(),
    val primaryRouteCoordinates: List<Pair<Double, Double>> = emptyList(),
    val routeDistanceKm: Double? = null,
    val routeDurationHours: Double? = null,
    val userLocation: Pair<Double, Double> = 30.6534 to 79.0268,
    val userLocationAccuracyMeters: Float? = null,
    val isUserLocationAcquired: Boolean = false,
    val isLocationFresh: Boolean = false,
    val isLocating: Boolean = false,
    val locationErrorMessage: String? = null,
    val cameraTarget: Pair<Double, Double> = 30.6534 to 79.0268,
    val zoomLevel: Double = 13.0,
    val isOfflineMode: Boolean = false,
    val selectedFilter: MapFilterChip = MapFilterChip.ALL,
    val layerVisibilities: Map<MapFeatureCategory, Boolean> = MapFeatureCategory.entries.associateWith { true },
    val layerStatuses: Map<MapFeatureCategory, LayerDataStatus> = MapFeatureCategory.entries.associateWith { LayerDataStatus.LOADED },
    val categoryCounts: Map<MapFeatureCategory, Int> = emptyMap(),
    val allFeatures: List<UnifiedMapFeature> = emptyList(),
    val visibleFeatures: List<UnifiedMapFeature> = emptyList(),
    val clusteredItems: List<ClusterItem> = emptyList(),
    val nearbyAssistanceList: List<UnifiedMapFeature> = emptyList(),
    val selectedFeature: UnifiedMapFeature? = null,
    val showLayersSheet: Boolean = false,
    val showNearbySheet: Boolean = false,
    val showLegendSheet: Boolean = false,
    val showSearchDialog: Boolean = false,
    val showComparisonSheet: Boolean = false,
    val selectedComparisonPreference: String = "balanced",
    val isBottomRouteExpanded: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<PlaceSearchResultDto> = emptyList(),
    val isSearching: Boolean = false,
    val activeDisasterAlertsCount: Int = 0,
    val routeSummaryWeather: Weather? = null,
    val routeSummaryFireCount: Int = 0,
    val routeSummaryForestCount: Int = 0,
    val plannedRouteAlternatives: List<RouteAlternativeDto> = emptyList(),
    val selectedAlternativeId: String? = null,
    val routeOverallHazardScore: Double? = null,
    val routeSafetyStatus: String? = null,
    val activeDecisionExplanation: String? = null,
    val primaryRouteOverallHazardScore: Double? = null,
    val primaryRouteSafetyStatus: String? = null,
    val primaryDecisionExplanation: String? = null,
    val corridorDistanceKm: Double = 5.0,
    val corridorPolygonCoordinates: List<List<Pair<Double, Double>>> = emptyList(),
    val primaryCorridorPolygonCoordinates: List<List<Pair<Double, Double>>> = emptyList(),
    val isAnalyzingCorridor: Boolean = false,
    val analysisLimitations: List<String> = emptyList(),
    val corridorGeoJson: String? = null,
    val routePrimaryGeoJson: String? = null,
    val routeSecondaryGeoJson: String? = null,
    val forestsGeoJson: String? = null,
    val hazardsGeoJson: String? = null,
    val firesGeoJson: String? = null,
    val facilitiesGeoJson: String? = null,
    val weatherGeoJson: String? = null,
    val geometryDiagnostics: Map<String, JsonElement> = emptyMap()
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val itineraryRepository: ItineraryRepository,
    private val routeRepository: RouteRepository,
    private val emergencyRepository: EmergencyRepository,
    private val forestRepository: ForestRepository,
    private val fireHotspotRepository: FireHotspotRepository,
    private val disasterRepository: DisasterRepository,
    private val weatherRepository: WeatherRepository,
    private val providerStatusRepository: ProviderStatusRepository,
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val deviceLocationRepository: DeviceLocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var rawMapPoints: List<MapPoint> = emptyList()
    private var rawForestFeatures: List<ForestFeature> = emptyList()
    private var rawFireHotspots: List<FireHotspot> = emptyList()
    private var rawHazardZones: List<UnifiedMapFeature> = emptyList()
    private var rawWeatherPoints: List<UnifiedMapFeature> = emptyList()

    private var searchJob: Job? = null
    private var currentRouteAnalysisJob: Job? = null
    private var activeAnalysisRequestId: String? = null

    init {
        observeConnectivity()
        observeDeviceLocation()
        observeData()
        requestCurrentLocationFix(recenter = false)
        refreshAllLayers()
    }

    private fun observeDeviceLocation() {
        viewModelScope.launch {
            deviceLocationRepository.locationState.collect { locState ->
                if (locState.latitude != null && locState.longitude != null) {
                    _uiState.update { current ->
                        current.copy(
                            userLocation = locState.latitude to locState.longitude,
                            userLocationAccuracyMeters = locState.accuracyMeters,
                            isUserLocationAcquired = true,
                            isLocationFresh = locState.isFresh,
                            isLocating = locState.isLocating,
                            locationErrorMessage = locState.errorMessage
                        )
                    }
                } else {
                    _uiState.update { current ->
                        current.copy(
                            isLocating = locState.isLocating,
                            locationErrorMessage = locState.errorMessage
                        )
                    }
                }
            }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityMonitor.state.collect { conn ->
                val offline = conn.status == NetworkStatus.NO_NETWORK ||
                        conn.status == NetworkStatus.CONNECTED_NO_INTERNET
                _uiState.update { it.copy(isOfflineMode = offline) }
                updateLayerStatuses(offline)
            }
        }
    }

    private fun observeData() {
        // 1. Observe Real Active Planned Route from RouteRepository
        viewModelScope.launch {
            routeRepository.activeRouteFlow.collect { activeRoute ->
                if (activeRoute == null) {
                    currentRouteAnalysisJob?.cancel()
                    activeAnalysisRequestId = null
                    rawHazardZones = emptyList()
                    rawFireHotspots = emptyList()
                    rawWeatherPoints = emptyList()
                    _uiState.update {
                        it.copy(
                            selectedItinerary = null,
                            routeLifecycleState = RouteLifecycleState.NO_ROUTE_SELECTED,
                            activeRouteId = null,
                            activeRouteVersion = 1,
                            routeTitle = "Explore Map",
                            routeCoordinates = emptyList(),
                            primaryRouteCoordinates = emptyList(),
                            routeDistanceKm = null,
                            routeDurationHours = null,
                            routeOverallHazardScore = null,
                            routeSafetyStatus = null,
                            activeDecisionExplanation = null,
                            plannedRouteAlternatives = emptyList(),
                            selectedAlternativeId = null,
                            activeDisasterAlertsCount = 0,
                            routeSummaryFireCount = 0,
                            routeSummaryForestCount = 0,
                            corridorGeoJson = null,
                            routePrimaryGeoJson = null,
                            routeSecondaryGeoJson = null,
                            forestsGeoJson = null,
                            hazardsGeoJson = null,
                            firesGeoJson = null,
                            facilitiesGeoJson = null,
                            weatherGeoJson = null,
                            geometryDiagnostics = emptyMap(),
                            corridorPolygonCoordinates = emptyList(),
                            primaryCorridorPolygonCoordinates = emptyList()
                        )
                    }
                    rebuildUnifiedFeatures()
                } else {
                    val primaryCoords = parseGeoJsonCoordinates(activeRoute.geometry_geojson?.toString())
                        .ifEmpty { activeRoute.conditions.map { it.latitude to it.longitude } }

                    val start = primaryCoords.firstOrNull() ?: _uiState.value.userLocation

                    _uiState.update {
                        it.copy(
                            selectedItinerary = null,
                            routeLifecycleState = RouteLifecycleState.ROUTE_AVAILABLE,
                            activeRouteId = activeRoute.route_id,
                            activeRouteVersion = activeRoute.route_version,
                            routeTitle = activeRoute.title,
                            routeCoordinates = primaryCoords,
                            primaryRouteCoordinates = primaryCoords,
                            routePrimaryGeoJson = activeRoute.geometry_geojson?.toString(),
                            corridorGeoJson = activeRoute.corridor_geojson?.toString(),
                            forestsGeoJson = activeRoute.forests_geojson?.toString(),
                            hazardsGeoJson = activeRoute.hazards_geojson?.toString(),
                            firesGeoJson = activeRoute.fires_geojson?.toString(),
                            facilitiesGeoJson = activeRoute.facilities_geojson?.toString(),
                            weatherGeoJson = activeRoute.weather_geojson?.toString(),
                            geometryDiagnostics = activeRoute.geometry_diagnostics,
                            routeDistanceKm = activeRoute.distance_km,
                            routeDurationHours = activeRoute.duration_minutes / 60.0,
                            routeOverallHazardScore = activeRoute.overall_hazard_score,
                            routeSafetyStatus = activeRoute.overall_safety_status,
                            plannedRouteAlternatives = activeRoute.alternatives,
                            selectedAlternativeId = null,
                            activeDecisionExplanation = activeRoute.decision_explanation,
                            cameraTarget = start,
                            zoomLevel = 13.0
                        )
                    }

                    // Trigger genuine corridor hazard analysis for this route
                    triggerRouteCorridorAnalysis(activeRoute.route_id, activeRoute.route_version, _uiState.value.corridorDistanceKm, activeRoute)
                }
            }
        }

        // 2. Emergency Map Points (Hospitals, Clinics, Police, Fire Station, Shelters)
        viewModelScope.launch {
            emergencyRepository.getMapPoints().collect { points ->
                if (_uiState.value.activeRouteId == null) {
                    rawMapPoints = points
                    rebuildUnifiedFeatures()
                }
            }
        }

        // 3. Forest Geography (OSM)
        viewModelScope.launch {
            forestRepository.getForestFeatures().collect { forests ->
                if (_uiState.value.activeRouteId == null) {
                    rawForestFeatures = forests
                    _uiState.update { it.copy(routeSummaryForestCount = forests.size) }
                    rebuildUnifiedFeatures()
                }
            }
        }

        // 4. Wildfire Detections (NASA FIRMS)
        viewModelScope.launch {
            fireHotspotRepository.getHotspots().collect { fires ->
                if (_uiState.value.activeRouteId == null) {
                    rawFireHotspots = fires
                    _uiState.update { it.copy(routeSummaryFireCount = fires.size) }
                    rebuildUnifiedFeatures()
                }
            }
        }

        // 5. Disaster Hazard Zones (Room / DDMA)
        viewModelScope.launch {
            disasterRepository.hazardZones.collect { zones ->
                // When a route is actively planned, corridor-filtered alerts from route analysis take precedence.
                // Never overwrite route-corridor alerts with global database hazards.
                if (_uiState.value.activeRouteId == null) {
                    val userLoc = _uiState.value.userLocation
                    rawHazardZones = zones.map { z ->
                        val dist = MapClusterHelper.calculateDistanceMeters(userLoc.first, userLoc.second, z.centerLatitude, z.centerLongitude)
                        val circleCoords = generateCirclePolygon(z.centerLatitude, z.centerLongitude, z.radiusMeters.toDouble())
                        UnifiedMapFeature(
                            id = "hazard_${z.alertId}",
                            category = MapFeatureCategory.HAZARD_ZONE,
                            title = z.title,
                            subtitle = "Severity: ${z.severity} • Radius: ${(z.radiusMeters / 1000f).toInt()} km",
                            latitude = z.centerLatitude,
                            longitude = z.centerLongitude,
                            distanceMeters = dist,
                            sourceName = z.issuingAuthority,
                            sourceTimestampEpochMs = z.publishedAtEpochMs,
                            isVerified = true,
                            isSimulated = z.isSimulated,
                            isCached = true,
                            details = mapOf(
                                "Severity" to z.severity,
                                "Category" to z.hazardCategory.replace('_', ' ').uppercase(),
                                "Affected Radius" to "${z.radiusMeters.toInt()} meters",
                                "Instructions" to z.requiredProtectiveInstructions,
                                "Issuing Authority" to z.issuingAuthority,
                                "Status" to z.status
                            ),
                            geometryPolygon = circleCoords
                        )
                    }
                    _uiState.update { it.copy(activeDisasterAlertsCount = zones.size) }
                    rebuildUnifiedFeatures()
                }
            }
        }

        // 6. Weather Observation
        viewModelScope.launch {
            val userLoc = _uiState.value.userLocation
            weatherRepository.getWeather(userLoc.first, userLoc.second).collect { weather ->
                _uiState.update { it.copy(routeSummaryWeather = weather) }
                if (weather != null) {
                    val weatherFeat = UnifiedMapFeature(
                        id = "weather_route_corridor",
                        category = MapFeatureCategory.WEATHER_POINT,
                        title = "Route Corridor Weather",
                        subtitle = "${weather.temperatureC}°C • ${weather.weatherDescription}",
                        latitude = weather.latitude,
                        longitude = weather.longitude,
                        distanceMeters = 0f,
                        sourceName = weather.providerNotice,
                        sourceTimestampEpochMs = weather.retrievedAtEpochMs,
                        isVerified = true,
                        isCached = weather.isCached,
                        details = mapOf(
                            "Temperature" to "${weather.temperatureC}°C",
                            "Conditions" to weather.weatherDescription,
                            "Precipitation" to "${weather.precipitationMm} mm",
                            "Wind Speed" to "${weather.windSpeedKmh} km/h",
                            "Relative Humidity" to "${weather.relativeHumidityPct}%",
                            "Forecast Valid" to "${java.util.Date(weather.forecastValidAtEpochMs)}"
                        )
                    )
                    rawWeatherPoints = listOf(weatherFeat)
                } else {
                    rawWeatherPoints = emptyList()
                }
                rebuildUnifiedFeatures()
            }
        }
    }

    fun clearRoute() {
        routeRepository.clearActiveRoute()
    }

    fun setCorridorDistance(distanceKm: Double) {
        val validDistances = listOf(1.0, 2.0, 5.0, 10.0)
        val validDistance = if (validDistances.contains(distanceKm)) distanceKm else 5.0
        if (_uiState.value.corridorDistanceKm == validDistance) return

        _uiState.update { it.copy(corridorDistanceKm = validDistance) }

        val activeId = _uiState.value.activeRouteId
        val activeVer = _uiState.value.activeRouteVersion
        if (activeId != null) {
            triggerRouteCorridorAnalysis(activeId, activeVer, validDistance)
        }
    }

    fun triggerRouteCorridorAnalysis(
        routeId: String,
        version: Int = 1,
        corridorDistanceKm: Double = _uiState.value.corridorDistanceKm,
        fallbackRoute: PlannedRouteResponseDto? = null
    ) {
        currentRouteAnalysisJob?.cancel()
        val requestId = "${routeId}_${version}_${corridorDistanceKm}"
        activeAnalysisRequestId = requestId

        currentRouteAnalysisJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    routeLifecycleState = RouteLifecycleState.ANALYZING_CORRIDOR,
                    isAnalyzingCorridor = true
                )
            }

            val analysisResult = routeRepository.analyzeRoute(
                routeId = routeId,
                corridorDistanceKm = corridorDistanceKm,
                routeVersion = version
            )
            // Ensure this request hasn't been superseded by another route selection or corridor change
            if (activeAnalysisRequestId != requestId) {
                Log.d("MapViewModel", "Discarded stale route analysis for $requestId")
                return@launch
            }

            analysisResult.fold(
                onSuccess = { analysis ->
                    applyRouteAnalysisFeatures(analysis)
                },
                onFailure = { err ->
                    Log.w("MapViewModel", "Route analysis API call failed: ${err.message}. Applying fallback.", err)
                    if (fallbackRoute != null) {
                        applyFallbackRouteAnalysis(fallbackRoute)
                    } else {
                        _uiState.update {
                            it.copy(
                                isAnalyzingCorridor = false,
                                routeLifecycleState = RouteLifecycleState.ANALYSIS_FAILED
                            )
                        }
                    }
                }
            )
        }
    }

    private fun parseGeoJsonPolygonCoordinates(jsonElement: JsonElement?): List<List<Pair<Double, Double>>> {
        if (jsonElement == null || jsonElement !is JsonObject) return emptyList()
        val ringsList = mutableListOf<List<Pair<Double, Double>>>()
        try {
            val geomObj = (jsonElement["geometry"] as? JsonObject) ?: jsonElement
            val coordsArray = geomObj["coordinates"] as? JsonArray
            if (coordsArray != null) {
                for (ringElem in coordsArray) {
                    val ring = ringElem as? JsonArray ?: continue
                    val ringPoints = mutableListOf<Pair<Double, Double>>()
                    for (ptElem in ring) {
                        val ptArr = ptElem as? JsonArray ?: continue
                        if (ptArr.size >= 2) {
                            val lon = (ptArr[0] as? JsonPrimitive)?.doubleOrNull ?: continue
                            val lat = (ptArr[1] as? JsonPrimitive)?.doubleOrNull ?: continue
                            ringPoints.add(lat to lon)
                        }
                    }
                    if (ringPoints.isNotEmpty()) {
                        ringsList.add(ringPoints)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MapViewModel", "Error parsing GeoJson polygon coordinates", e)
        }
        return ringsList
    }

    private fun applyRouteAnalysisFeatures(analysis: RouteAnalysisResponseDto) {
        val userLoc = _uiState.value.userLocation

        // 0. Parse Corridor Polygon from analysis.corridor_geojson
        val corridorCoords = parseGeoJsonPolygonCoordinates(analysis.corridor_geojson)

        // 1. Hazard zones from disaster_alerts with real spatial coordinates
        rawHazardZones = analysis.disaster_alerts.mapNotNull { a ->
            val dist = (a.distance_to_alert_km * 1000f).toFloat()
            val lat = a.center_latitude ?: userLoc.first
            val lon = a.center_longitude ?: userLoc.second

            var polygonCoords: List<Pair<Double, Double>>? = null
            try {
                val pGeom = a.polygon_geojson
                if (pGeom != null && pGeom is JsonObject) {
                    val coords = pGeom["coordinates"] as? JsonArray
                    val ring = coords?.firstOrNull() as? JsonArray
                    if (ring != null) {
                        polygonCoords = ring.mapNotNull { ptElem ->
                            val ptArr = ptElem as? JsonArray ?: return@mapNotNull null
                            if (ptArr.size >= 2) {
                                val pLon = (ptArr[0] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                                val pLat = (ptArr[1] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                                pLat to pLon
                            } else null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MapViewModel", "Error parsing alert polygon", e)
            }

            if (polygonCoords == null && a.center_latitude != null && a.center_longitude != null && (a.affected_radius_meters ?: 0.0) > 0.0) {
                polygonCoords = generateCirclePolygon(lat, lon, a.affected_radius_meters ?: 3000.0)
            }

            UnifiedMapFeature(
                id = "hazard_${a.alert_id}",
                category = MapFeatureCategory.HAZARD_ZONE,
                title = a.title,
                subtitle = "Severity: ${a.severity} • Category: ${a.hazard_category.uppercase()}",
                latitude = lat,
                longitude = lon,
                distanceMeters = dist,
                sourceName = "NDMA SACHET / CAP Official",
                sourceTimestampEpochMs = System.currentTimeMillis(),
                isVerified = true,
                isSimulated = a.is_simulated,
                isCached = false,
                details = mapOf(
                    "Severity" to a.severity,
                    "Category" to a.hazard_category.replace('_', ' ').uppercase(),
                    "Distance to Route" to "${a.distance_to_alert_km} km",
                    "Protective Instructions" to a.required_protective_instructions,
                ),
                geometryPolygon = polygonCoords
            )
        }

        // 1b. Parse Forest features from analysis.forests_geojson or analysis.forest_features
        val forestList = mutableListOf<ForestFeature>()
        analysis.forests_geojson?.let { fJson ->
            try {
                if (fJson is JsonObject) {
                    val features = fJson["features"] as? JsonArray
                    features?.forEachIndexed { idx, featElem ->
                        val fObj = featElem as? JsonObject ?: return@forEachIndexed
                        val geom = fObj["geometry"] as? JsonObject
                        val props = fObj["properties"] as? JsonObject
                        val fName = (props?.get("name") as? JsonPrimitive)?.contentOrNull ?: "Forest Area"
                        val fType = (props?.get("type") as? JsonPrimitive)?.contentOrNull ?: (props?.get("wood_type") as? JsonPrimitive)?.contentOrNull ?: "woodland"
                        val ringCoords = mutableListOf<Pair<Double, Double>>()
                        if (geom != null) {
                            val coordsArray = geom["coordinates"] as? JsonArray
                            val ring = coordsArray?.firstOrNull() as? JsonArray
                            ring?.forEach { ptElem ->
                                val pt = ptElem as? JsonArray ?: return@forEach
                                if (pt.size >= 2) {
                                    val lon = (pt[0] as? JsonPrimitive)?.doubleOrNull ?: return@forEach
                                    val lat = (pt[1] as? JsonPrimitive)?.doubleOrNull ?: return@forEach
                                    ringCoords.add(lat to lon)
                                }
                            }
                        }
                        if (ringCoords.isNotEmpty()) {
                            forestList.add(
                                ForestFeature(
                                    id = "forest_corridor_$idx",
                                    osmId = idx.toLong(),
                                    name = fName,
                                    woodType = fType,
                                    coordinates = ringCoords,
                                    isPolygon = true,
                                    isCached = false
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MapViewModel", "Error parsing corridor forests", e)
            }
        }
        rawForestFeatures = forestList

        // 2. Active Fires from fire_detections with genuine coordinates
        rawFireHotspots = analysis.fire_detections.mapIndexed { idx, fire ->
            FireHotspot(
                id = "firms_corridor_$idx",
                latitude = fire.latitude ?: userLoc.first,
                longitude = fire.longitude ?: userLoc.second,
                brightness = fire.brightness ?: 325.0,
                confidence = fire.confidence ?: "nominal",
                satellite = "MODIS/VIIRS",
                acquisitionDate = fire.acquisition_time ?: "NRT",
                acquisitionTime = "",
                frpMw = fire.fire_radiative_power ?: 15.0,
                isSimulated = false
            )
        }

        // 3. Assistance points from assistance_points (Safe Havens & Facilities)
        rawMapPoints = analysis.assistance_points.mapIndexed { idx, sh ->
            val cat = when (sh.category.lowercase()) {
                "hospital" -> PointCategory.MEDICAL
                "clinic" -> PointCategory.CLINIC
                "police" -> PointCategory.POLICE
                "fuel", "petrol_pump" -> PointCategory.FUEL_STATION
                "fire_station", "fire" -> PointCategory.FIRE_STATION
                "shelter", "mountain_hut" -> PointCategory.SHELTER
                else -> PointCategory.VISITOR_INFO
            }
            val details = mutableMapOf<String, String>()
            sh.opening_hours?.let { details["opening_hours"] = it }
            sh.phone?.let { details["phone"] = it }
            sh.website?.let { details["website"] = it }
            sh.brand?.let { details["brand"] = it }
            sh.operator?.let { details["operator"] = it }
            sh.address?.let { details["address"] = it }
            if (sh.emergency_verified) {
                details["emergency_verified"] = "true"
            }
            MapPoint(
                id = "haven_$idx",
                name = sh.name,
                category = cat,
                latitude = sh.latitude,
                longitude = sh.longitude,
                address = sh.address,
                phone = sh.phone,
                description = "Verified ${sh.category.replace('_', ' ')} • ${sh.distance_km} km from route",
                provenance = sh.source ?: "OpenStreetMap Overpass",
                verificationStatus = "verified",
                verificationTimestampEpochMs = System.currentTimeMillis(),
                details = details
            )
        }

        // 4. Waypoint Weather - Create sample points along corridor
        if (analysis.weather_by_waypoint.isNotEmpty()) {
            val firstWeather = analysis.weather_by_waypoint.first()
            val wModel = Weather(
                latitude = userLoc.first,
                longitude = userLoc.second,
                temperatureC = firstWeather.temperature_c,
                relativeHumidityPct = firstWeather.relative_humidity_pct,
                precipitationMm = firstWeather.precipitation_mm,
                windSpeedKmh = firstWeather.wind_speed_kmh,
                weatherCode = firstWeather.weather_code,
                weatherDescription = firstWeather.weather_description,
                forecastValidAtEpochMs = System.currentTimeMillis(),
                retrievedAtEpochMs = System.currentTimeMillis(),
                providerNotice = firstWeather.source,
                isCached = false
            )
            _uiState.update { it.copy(routeSummaryWeather = wModel) }

            rawWeatherPoints = analysis.weather_by_waypoint.mapIndexed { idx, wpWeather ->
                val sampleLat = if (idx < _uiState.value.routeCoordinates.size) _uiState.value.routeCoordinates[idx].first else userLoc.first
                val sampleLon = if (idx < _uiState.value.routeCoordinates.size) _uiState.value.routeCoordinates[idx].second else userLoc.second
                UnifiedMapFeature(
                    id = "weather_route_sample_$idx",
                    category = MapFeatureCategory.WEATHER_POINT,
                    title = "Sample ${idx + 1}: ${wpWeather.weather_description}",
                    subtitle = "${wpWeather.temperature_c}°C • Wind: ${wpWeather.wind_speed_kmh} km/h",
                    latitude = sampleLat,
                    longitude = sampleLon,
                    distanceMeters = 0f,
                    sourceName = wpWeather.source,
                    sourceTimestampEpochMs = System.currentTimeMillis(),
                    isVerified = true,
                    isCached = false,
                    details = mapOf(
                        "Temperature" to "${wpWeather.temperature_c}°C",
                        "Precipitation" to "${wpWeather.precipitation_mm} mm (${wpWeather.precipitation_probability_pct}%)",
                        "Wind Speed" to "${wpWeather.wind_speed_kmh} km/h",
                        "Relative Humidity" to "${wpWeather.relative_humidity_pct}%",
                        "Forecast Time" to wpWeather.forecast_valid_at,
                        "Warning Level" to wpWeather.warning_level
                    )
                )
            }
        }

        // 5. Update layer statuses from provider_statuses
        val updatedStatuses = _uiState.value.layerStatuses.toMutableMap()
        analysis.provider_statuses.forEach { (pKey, pVal) ->
            val statusEnum = when (pVal.status.uppercase()) {
                "LOADED" -> LayerDataStatus.LOADED
                "EMPTY" -> LayerDataStatus.EMPTY
                "CACHED" -> LayerDataStatus.CACHED
                "UNAVAILABLE" -> LayerDataStatus.UNAVAILABLE
                else -> LayerDataStatus.FAILED
            }
            when (pKey) {
                "nasa_firms" -> updatedStatuses[MapFeatureCategory.ACTIVE_FIRE_DETECTION] = statusEnum
                "ndma_sachet" -> updatedStatuses[MapFeatureCategory.HAZARD_ZONE] = statusEnum
                "open_meteo" -> updatedStatuses[MapFeatureCategory.WEATHER_POINT] = statusEnum
                "osm_overpass" -> {
                    updatedStatuses[MapFeatureCategory.HOSPITAL] = statusEnum
                    updatedStatuses[MapFeatureCategory.SHELTER] = statusEnum
                    updatedStatuses[MapFeatureCategory.FOREST_AREA] = statusEnum
                }
            }
        }

        val hasHazards = rawHazardZones.isNotEmpty() || rawFireHotspots.isNotEmpty()
        _uiState.update {
            it.copy(
                routeLifecycleState = if (hasHazards) RouteLifecycleState.ANALYZED_WITH_FEATURES else RouteLifecycleState.ANALYZED_EMPTY,
                layerStatuses = updatedStatuses,
                activeDisasterAlertsCount = rawHazardZones.size,
                routeSummaryFireCount = rawFireHotspots.size,
                routeSummaryForestCount = rawForestFeatures.size,
                routeOverallHazardScore = analysis.overall_hazard_score,
                routeSafetyStatus = analysis.overall_safety_status,
                activeDecisionExplanation = analysis.decision_explanation,
                primaryRouteOverallHazardScore = analysis.overall_hazard_score,
                primaryRouteSafetyStatus = analysis.overall_safety_status,
                primaryDecisionExplanation = analysis.decision_explanation,
                corridorDistanceKm = analysis.corridor_distance_km,
                corridorPolygonCoordinates = corridorCoords,
                primaryCorridorPolygonCoordinates = corridorCoords,
                corridorGeoJson = analysis.corridor_geojson?.toString(),
                forestsGeoJson = analysis.forests_geojson?.toString(),
                hazardsGeoJson = analysis.hazards_geojson?.toString(),
                firesGeoJson = analysis.fires_geojson?.toString(),
                facilitiesGeoJson = analysis.facilities_geojson?.toString(),
                weatherGeoJson = analysis.weather_geojson?.toString(),
                geometryDiagnostics = analysis.geometry_diagnostics,
                plannedRouteAlternatives = if (analysis.alternatives.isNotEmpty()) analysis.alternatives else it.plannedRouteAlternatives,
                analysisLimitations = analysis.analysis_limitations,
                isAnalyzingCorridor = false
            )
        }
        rebuildUnifiedFeatures()
    }

    private fun applyFallbackRouteAnalysis(fallbackRoute: PlannedRouteResponseDto) {
        val userLoc = _uiState.value.userLocation
        // Extract conditions alerts
        val alertList = mutableListOf<UnifiedMapFeature>()
        fallbackRoute.conditions.forEach { wp ->
            wp.alerts.forEach { a ->
                if (alertList.none { it.id == "hazard_${a.alert_id}" }) {
                    val circleCoords = generateCirclePolygon(wp.latitude, wp.longitude, 3000.0)
                    alertList.add(
                        UnifiedMapFeature(
                            id = "hazard_${a.alert_id}",
                            category = MapFeatureCategory.HAZARD_ZONE,
                            title = a.title,
                            subtitle = "Severity: ${a.severity} • Category: ${a.hazard_category.uppercase()}",
                            latitude = wp.latitude,
                            longitude = wp.longitude,
                            distanceMeters = (a.distance_to_alert_km * 1000f).toFloat(),
                            sourceName = "NDMA SACHET / CAP (Cached)",
                            sourceTimestampEpochMs = System.currentTimeMillis(),
                            isVerified = true,
                            isSimulated = a.is_simulated,
                            isCached = true,
                            details = mapOf(
                                "Severity" to a.severity,
                                "Category" to a.hazard_category,
                                "Protective Instructions" to a.required_protective_instructions
                            ),
                            geometryPolygon = circleCoords
                        )
                    )
                }
            }
        }
        rawHazardZones = alertList

        // Extract safe havens
        rawMapPoints = fallbackRoute.safe_havens.mapIndexed { idx, sh ->
            val cat = when (sh.category.lowercase()) {
                "hospital", "clinic" -> PointCategory.MEDICAL
                "police" -> PointCategory.POLICE
                "shelter", "mountain_hut" -> PointCategory.SHELTER
                else -> PointCategory.VISITOR_INFO
            }
            MapPoint(
                id = "haven_$idx",
                name = sh.name,
                category = cat,
                latitude = sh.latitude,
                longitude = sh.longitude,
                description = "Emergency Safe Haven • ${sh.distance_km} km",
                provenance = "Cached Route Plan",
                verificationStatus = "verified",
                verificationTimestampEpochMs = System.currentTimeMillis()
            )
        }

        _uiState.update {
            it.copy(
                routeLifecycleState = RouteLifecycleState.OFFLINE_CACHED,
                activeDisasterAlertsCount = rawHazardZones.size,
                routeOverallHazardScore = fallbackRoute.overall_hazard_score,
                routeSafetyStatus = fallbackRoute.overall_safety_status,
                activeDecisionExplanation = fallbackRoute.decision_explanation,
                corridorGeoJson = fallbackRoute.corridor_geojson?.toString(),
                hazardsGeoJson = fallbackRoute.hazards_geojson?.toString(),
                firesGeoJson = fallbackRoute.fires_geojson?.toString(),
                facilitiesGeoJson = fallbackRoute.facilities_geojson?.toString(),
                forestsGeoJson = fallbackRoute.forests_geojson?.toString(),
                weatherGeoJson = fallbackRoute.weather_geojson?.toString(),
                routePrimaryGeoJson = fallbackRoute.geometry_geojson?.toString()
            )
        }
        rebuildUnifiedFeatures()
    }

    private fun rebuildUnifiedFeatures() {
        val userLoc = _uiState.value.userLocation
        val list = mutableListOf<UnifiedMapFeature>()

        // 1. Map Points
        rawMapPoints.forEach { pt ->
            val cat = when (pt.category) {
                PointCategory.MEDICAL -> MapFeatureCategory.HOSPITAL
                PointCategory.CLINIC -> MapFeatureCategory.CLINIC
                PointCategory.POLICE -> MapFeatureCategory.POLICE_STATION
                PointCategory.FIRE_STATION -> MapFeatureCategory.FIRE_STATION
                PointCategory.FUEL_STATION -> MapFeatureCategory.PETROL_PUMP
                PointCategory.SHELTER -> MapFeatureCategory.SHELTER
                PointCategory.VISITOR_INFO, PointCategory.TRANSPORT, PointCategory.VERIFIED_PUBLIC -> MapFeatureCategory.TOURIST_ASSISTANCE
                PointCategory.EMERGENCY_SERVICE -> MapFeatureCategory.FIRE_STATION
            }
            val dist = MapClusterHelper.calculateDistanceMeters(userLoc.first, userLoc.second, pt.latitude, pt.longitude)
            val detailMap = mutableMapOf<String, String>()
            pt.address?.let { detailMap["Address"] = it }
            pt.phone?.let { detailMap["Phone"] = it }
            detailMap["Category"] = cat.displayName
            detailMap["Provenance"] = pt.provenance
            detailMap["Status"] = pt.verificationStatus
            detailMap.putAll(pt.details)

            list.add(
                UnifiedMapFeature(
                    id = "poi_${pt.id}",
                    category = cat,
                    title = pt.name,
                    subtitle = pt.description,
                    latitude = pt.latitude,
                    longitude = pt.longitude,
                    distanceMeters = dist,
                    sourceName = pt.provenance,
                    sourceTimestampEpochMs = pt.verificationTimestampEpochMs,
                    isVerified = pt.verificationStatus == "verified",
                    phone = pt.phone,
                    address = pt.address,
                    details = detailMap
                )
            )
        }

        // 2. Forest Features
        rawForestFeatures.forEach { f ->
            if (f.coordinates.isEmpty()) return@forEach
            val centerLat = f.coordinates.map { it.first }.average()
            val centerLon = f.coordinates.map { it.second }.average()
            val dist = MapClusterHelper.calculateDistanceMeters(userLoc.first, userLoc.second, centerLat, centerLon)
            list.add(
                UnifiedMapFeature(
                    id = "forest_${f.id}",
                    category = MapFeatureCategory.FOREST_AREA,
                    title = f.name,
                    subtitle = "${f.woodType.replaceFirstChar { it.titlecase() }} Woodland",
                    latitude = centerLat,
                    longitude = centerLon,
                    distanceMeters = dist,
                    sourceName = "OpenStreetMap Contributors (ODbL)",
                    isVerified = true,
                    isCached = f.isCached,
                    geometryPolygon = f.coordinates,
                    details = mapOf(
                        "Wood Type" to f.woodType,
                        "Area Geometry" to "Polygon with ${f.coordinates.size} nodes",
                        "Source License" to "Open Database License (ODbL)"
                    )
                )
            )
        }

        // 3. Fire Hotspots
        rawFireHotspots.forEach { fire ->
            val dist = MapClusterHelper.calculateDistanceMeters(userLoc.first, userLoc.second, fire.latitude, fire.longitude)
            list.add(
                UnifiedMapFeature(
                    id = "fire_${fire.id}",
                    category = MapFeatureCategory.ACTIVE_FIRE_DETECTION,
                    title = "Thermal Hotspot (${fire.satellite})",
                    subtitle = "${fire.brightness}K • FRP: ${fire.frpMw} MW",
                    latitude = fire.latitude,
                    longitude = fire.longitude,
                    distanceMeters = dist,
                    sourceName = "NASA FIRMS (${fire.satellite})",
                    isVerified = true,
                    isSimulated = fire.isSimulated,
                    isCached = true,
                    details = mapOf(
                        "Brightness Temp" to "${fire.brightness} Kelvin",
                        "Fire Radiative Power" to "${fire.frpMw} MW",
                        "Satellite Sensor" to fire.satellite,
                        "Acquisition Date" to "${fire.acquisitionDate} ${fire.acquisitionTime} UTC",
                        "Confidence" to fire.confidence
                    )
                )
            )
        }

        // 4. Hazard Zones & Weather
        list.addAll(rawHazardZones)
        list.addAll(rawWeatherPoints)

        // Calculate counts per category
        val counts = mutableMapOf<MapFeatureCategory, Int>()
        list.forEach { feat ->
            counts[feat.category] = (counts[feat.category] ?: 0) + 1
        }

        // Extract assistance items sorted by distance
        val assistance = list
            .filter { it.category in listOf(MapFeatureCategory.HOSPITAL, MapFeatureCategory.CLINIC, MapFeatureCategory.POLICE_STATION, MapFeatureCategory.FIRE_STATION, MapFeatureCategory.SHELTER, MapFeatureCategory.TOURIST_ASSISTANCE) }
            .sortedBy { it.distanceMeters ?: Float.MAX_VALUE }

        _uiState.update { state ->
            val filtered = filterFeatures(list, state.selectedFilter, state.layerVisibilities)
            val clustered = MapClusterHelper.clusterFeatures(
                features = filtered.filter { !it.category.isAreaFeature },
                zoom = state.zoomLevel
            )
            state.copy(
                allFeatures = list,
                categoryCounts = counts,
                visibleFeatures = filtered,
                clusteredItems = clustered,
                nearbyAssistanceList = assistance
            )
        }
    }

    private fun filterFeatures(
        all: List<UnifiedMapFeature>,
        chip: MapFilterChip,
        visibilities: Map<MapFeatureCategory, Boolean>
    ): List<UnifiedMapFeature> {
        return all.filter { feat ->
            val layerVisible = visibilities[feat.category] ?: true
            if (!layerVisible) return@filter false

            when (chip) {
                MapFilterChip.ALL -> true
                MapFilterChip.MEDICAL -> feat.category == MapFeatureCategory.HOSPITAL
                MapFilterChip.CLINICS -> feat.category == MapFeatureCategory.CLINIC
                MapFilterChip.POLICE -> feat.category == MapFeatureCategory.POLICE_STATION
                MapFilterChip.FUEL -> feat.category == MapFeatureCategory.PETROL_PUMP
                MapFilterChip.FIRE_STATIONS -> feat.category == MapFeatureCategory.FIRE_STATION
                MapFilterChip.FOREST -> feat.category == MapFeatureCategory.FOREST_AREA
                MapFilterChip.ACTIVE_FIRES -> feat.category == MapFeatureCategory.ACTIVE_FIRE_DETECTION
                MapFilterChip.WEATHER -> feat.category == MapFeatureCategory.WEATHER_POINT
                MapFilterChip.DISASTER_ALERTS -> feat.category == MapFeatureCategory.HAZARD_ZONE
                MapFilterChip.SHELTERS_ASSISTANCE -> feat.category == MapFeatureCategory.SHELTER || feat.category == MapFeatureCategory.TOURIST_ASSISTANCE
            }
        }
    }

    private fun updateLayerStatuses(isOffline: Boolean) {
        val statuses = mutableMapOf<MapFeatureCategory, LayerDataStatus>()
        MapFeatureCategory.entries.forEach { cat ->
            statuses[cat] = if (isOffline) LayerDataStatus.CACHED else LayerDataStatus.LOADED
        }
        _uiState.update { it.copy(layerStatuses = statuses) }
    }

    fun onFilterSelected(chip: MapFilterChip) {
        _uiState.update { state ->
            val filtered = filterFeatures(state.allFeatures, chip, state.layerVisibilities)
            val clustered = MapClusterHelper.clusterFeatures(
                features = filtered.filter { !it.category.isAreaFeature },
                zoom = state.zoomLevel
            )
            state.copy(
                selectedFilter = chip,
                visibleFeatures = filtered,
                clusteredItems = clustered
            )
        }
    }

    fun toggleLayer(category: MapFeatureCategory) {
        _uiState.update { state ->
            val current = state.layerVisibilities[category] ?: true
            val updated = state.layerVisibilities.toMutableMap().apply { put(category, !current) }
            val filtered = filterFeatures(state.allFeatures, state.selectedFilter, updated)
            val clustered = MapClusterHelper.clusterFeatures(
                features = filtered.filter { !it.category.isAreaFeature },
                zoom = state.zoomLevel
            )
            state.copy(
                layerVisibilities = updated,
                visibleFeatures = filtered,
                clusteredItems = clustered
            )
        }
    }

    fun showAllLayers() {
        val allEnabled = MapFeatureCategory.entries.associateWith { true }
        _uiState.update { state ->
            val filtered = filterFeatures(state.allFeatures, state.selectedFilter, allEnabled)
            val clustered = MapClusterHelper.clusterFeatures(
                features = filtered.filter { !it.category.isAreaFeature },
                zoom = state.zoomLevel
            )
            state.copy(
                layerVisibilities = allEnabled,
                visibleFeatures = filtered,
                clusteredItems = clustered
            )
        }
    }

    fun resetLayerDefaults() {
        showAllLayers()
        onFilterSelected(MapFilterChip.ALL)
    }

    fun selectFeature(feature: UnifiedMapFeature?) {
        _uiState.update { it.copy(selectedFeature = feature) }
    }

    fun onClusterClicked(cluster: ClusterItem) {
        if (cluster.isCluster) {
            // Zoom camera closer and center
            _uiState.update {
                it.copy(
                    cameraTarget = cluster.latitude to cluster.longitude,
                    zoomLevel = (it.zoomLevel + 2.0).coerceAtMost(16.0)
                )
            }
            rebuildUnifiedFeatures()
        } else {
            selectFeature(cluster.singleFeature)
        }
    }

    fun setZoomLevel(zoom: Double) {
        _uiState.update { state ->
            val clustered = MapClusterHelper.clusterFeatures(
                features = state.visibleFeatures.filter { !it.category.isAreaFeature },
                zoom = zoom
            )
            state.copy(zoomLevel = zoom, clusteredItems = clustered)
        }
    }

    fun recenterToUserLocation() {
        if (_uiState.value.isUserLocationAcquired) {
            _uiState.update { it.copy(cameraTarget = it.userLocation, zoomLevel = 14.5) }
        }
        requestCurrentLocationFix(recenter = true)
    }

    fun requestCurrentLocationFix(recenter: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true, locationErrorMessage = null) }
            val result = deviceLocationRepository.getCurrentLocationFix()
            result.onSuccess { loc ->
                _uiState.update { current ->
                    current.copy(
                        userLocation = loc.latitude to loc.longitude,
                        userLocationAccuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                        isUserLocationAcquired = true,
                        isLocationFresh = true,
                        isLocating = false,
                        locationErrorMessage = null,
                        cameraTarget = if (recenter) (loc.latitude to loc.longitude) else current.cameraTarget,
                        zoomLevel = if (recenter) 14.5 else current.zoomLevel
                    )
                }
                rebuildUnifiedFeatures()
            }.onFailure { err ->
                _uiState.update { current ->
                    current.copy(
                        isLocating = false,
                        locationErrorMessage = err.message ?: "Failed to acquire real GPS fix. Tap recenter to retry."
                    )
                }
            }
        }
    }

    fun fitToRoute() {
        val coords = _uiState.value.routeCoordinates
        if (coords.isNotEmpty()) {
            val centerLat = coords.map { it.first }.average()
            val centerLon = coords.map { it.second }.average()
            _uiState.update { it.copy(cameraTarget = centerLat to centerLon, zoomLevel = 12.0) }
        }
    }

    fun toggleLayersSheet(show: Boolean) {
        _uiState.update { it.copy(showLayersSheet = show) }
    }

    fun toggleNearbySheet(show: Boolean) {
        _uiState.update { it.copy(showNearbySheet = show) }
    }

    fun toggleLegendSheet(show: Boolean) {
        _uiState.update { it.copy(showLegendSheet = show) }
    }

    fun toggleComparisonSheet(show: Boolean) {
        _uiState.update { it.copy(showComparisonSheet = show) }
    }

    fun setComparisonPreference(pref: String) {
        _uiState.update { it.copy(selectedComparisonPreference = pref) }
    }

    fun toggleSearchDialog(show: Boolean) {
        _uiState.update { it.copy(showSearchDialog = show, searchResults = emptyList(), searchQuery = "") }
    }

    fun toggleBottomRouteExpanded() {
        _uiState.update { it.copy(isBottomRouteExpanded = !it.isBottomRouteExpanded) }
    }

    fun toggleOfflineSchematic() {
        _uiState.update { it.copy(isOfflineMode = !it.isOfflineMode) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val res = routeRepository.searchPlaces(query)
            res.onSuccess { places ->
                _uiState.update { it.copy(searchResults = places, isSearching = false) }
            }.onFailure {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            }
        }
    }

    fun selectDestination(place: PlaceSearchResultDto) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showSearchDialog = false,
                    routeTitle = "Route to ${place.name}",
                    routeLifecycleState = RouteLifecycleState.CALCULATING_ROUTE,
                    cameraTarget = place.latitude to place.longitude,
                    zoomLevel = 13.5
                )
            }
            // Request route planning from actual user location
            val userLoc = _uiState.value.userLocation
            val res = routeRepository.planRoute(
                originLat = userLoc.first,
                originLon = userLoc.second,
                originName = if (_uiState.value.isUserLocationAcquired) "My Location" else "Current Location",
                destLat = place.latitude,
                destLon = place.longitude,
                destName = place.name,
                corridorDistanceKm = _uiState.value.corridorDistanceKm
            )
            res.onFailure { err ->
                _uiState.update {
                    it.copy(
                        routeLifecycleState = RouteLifecycleState.ANALYSIS_FAILED,
                        locationErrorMessage = "Failed to calculate route to ${place.name}: ${err.message}"
                    )
                }
            }
        }
    }

    fun selectRouteAlternative(altId: String?) {
        val current = _uiState.value
        if (altId == null) {
            // Revert to primary (shortest/direct) route
            _uiState.update {
                it.copy(
                    selectedAlternativeId = null,
                    routeCoordinates = it.primaryRouteCoordinates,
                    corridorPolygonCoordinates = it.primaryCorridorPolygonCoordinates,
                    routeSecondaryGeoJson = null,
                    routeOverallHazardScore = it.primaryRouteOverallHazardScore,
                    routeSafetyStatus = it.primaryRouteSafetyStatus,
                    activeDecisionExplanation = it.primaryDecisionExplanation
                )
            }
            rebuildUnifiedFeatures()
            return
        }

        val alt = current.plannedRouteAlternatives.find { it.id == altId } ?: return
        val altCoords = parseGeoJsonCoordinates(alt.geometry_geojson?.toString())
        if (altCoords.isNotEmpty()) {
            val altCorridorRings = parseGeoJsonPolygonCoordinates(alt.corridor_geojson)
            _uiState.update {
                it.copy(
                    selectedAlternativeId = altId,
                    routeCoordinates = altCoords,
                    routeSecondaryGeoJson = alt.geometry_geojson?.toString(),
                    corridorPolygonCoordinates = if (altCorridorRings.isNotEmpty()) altCorridorRings else it.primaryCorridorPolygonCoordinates,
                    corridorGeoJson = alt.corridor_geojson?.toString() ?: it.corridorGeoJson,
                    routeDistanceKm = alt.distance_km,
                    routeDurationHours = alt.duration_minutes / 60.0,
                    routeOverallHazardScore = alt.hazard_score,
                    routeSafetyStatus = if (alt.hazard_score >= 60.0) "HIGH_RISK" else (if (alt.hazard_score >= 25.0) "CAUTION" else "NORMAL"),
                    activeDecisionExplanation = alt.recommendation_reason ?: alt.safety_label
                )
            }
            rebuildUnifiedFeatures()
        }
    }

    fun refreshAllLayers() {
        viewModelScope.launch {
            // Set loading on active layers
            _uiState.update { state ->
                val loadingStatuses = state.layerStatuses.toMutableMap()
                MapFeatureCategory.entries.forEach { cat ->
                    loadingStatuses[cat] = LayerDataStatus.LOADING
                }
                state.copy(layerStatuses = loadingStatuses)
            }

            val emRes = emergencyRepository.refreshMapPoints()
            val forRes = forestRepository.refreshForestFeatures()
            val fireRes = fireHotspotRepository.refreshHotspots()
            val disRes = disasterRepository.syncDisasterAlerts()

            val updatedStatuses = _uiState.value.layerStatuses.toMutableMap()

            // Fire hotspot status
            fireRes.fold(
                onSuccess = {
                    updatedStatuses[MapFeatureCategory.ACTIVE_FIRE_DETECTION] =
                        if (rawFireHotspots.isEmpty()) LayerDataStatus.EMPTY else LayerDataStatus.LOADED
                },
                onFailure = { err ->
                    val msg = err.message.orEmpty().lowercase()
                    updatedStatuses[MapFeatureCategory.ACTIVE_FIRE_DETECTION] =
                        if ("unconfigured" in msg || "key" in msg) LayerDataStatus.UNAVAILABLE
                        else if (rawFireHotspots.isNotEmpty()) LayerDataStatus.CACHED
                        else LayerDataStatus.FAILED
                }
            )

            // Forest status
            forRes.fold(
                onSuccess = {
                    updatedStatuses[MapFeatureCategory.FOREST_AREA] =
                        if (rawForestFeatures.isEmpty()) LayerDataStatus.EMPTY else LayerDataStatus.LOADED
                },
                onFailure = {
                    updatedStatuses[MapFeatureCategory.FOREST_AREA] =
                        if (rawForestFeatures.isNotEmpty()) LayerDataStatus.CACHED else LayerDataStatus.FAILED
                }
            )

            // Emergency status (Hospitals, Police, Fire Stations, Clinics)
            val emCategories = listOf(
                MapFeatureCategory.HOSPITAL,
                MapFeatureCategory.CLINIC,
                MapFeatureCategory.POLICE_STATION,
                MapFeatureCategory.FIRE_STATION,
                MapFeatureCategory.SHELTER,
                MapFeatureCategory.TOURIST_ASSISTANCE
            )
            emRes.fold(
                onSuccess = {
                    emCategories.forEach { cat ->
                        val count = rawMapPoints.count { pt ->
                            when (pt.category) {
                                PointCategory.MEDICAL -> cat == MapFeatureCategory.HOSPITAL
                                PointCategory.CLINIC -> cat == MapFeatureCategory.CLINIC
                                PointCategory.POLICE -> cat == MapFeatureCategory.POLICE_STATION
                                PointCategory.FIRE_STATION, PointCategory.EMERGENCY_SERVICE -> cat == MapFeatureCategory.FIRE_STATION
                                PointCategory.SHELTER -> cat == MapFeatureCategory.SHELTER
                                else -> cat == MapFeatureCategory.TOURIST_ASSISTANCE
                            }
                        }
                        updatedStatuses[cat] = if (count > 0) LayerDataStatus.LOADED else LayerDataStatus.EMPTY
                    }
                },
                onFailure = {
                    emCategories.forEach { cat ->
                        updatedStatuses[cat] = if (rawMapPoints.isNotEmpty()) LayerDataStatus.CACHED else LayerDataStatus.FAILED
                    }
                }
            )

            // Disaster Alerts
            disRes.fold(
                onSuccess = {
                    updatedStatuses[MapFeatureCategory.HAZARD_ZONE] =
                        if (rawHazardZones.isEmpty()) LayerDataStatus.EMPTY else LayerDataStatus.LOADED
                },
                onFailure = {
                    updatedStatuses[MapFeatureCategory.HAZARD_ZONE] =
                        if (rawHazardZones.isNotEmpty()) LayerDataStatus.CACHED else LayerDataStatus.FAILED
                }
            )

            // Weather status
            val weather = _uiState.value.routeSummaryWeather
            updatedStatuses[MapFeatureCategory.WEATHER_POINT] = when {
                weather != null && weather.isStale -> LayerDataStatus.STALE
                weather != null && weather.isCached -> LayerDataStatus.CACHED
                weather != null -> LayerDataStatus.LOADED
                else -> LayerDataStatus.EMPTY
            }

            _uiState.update { it.copy(layerStatuses = updatedStatuses) }
        }
    }

    private fun generateCirclePolygon(centerLat: Double, centerLon: Double, radiusMeters: Double): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val earthRadius = 6371000.0
        val dLat = (radiusMeters / earthRadius) * (180.0 / Math.PI)
        val dLon = dLat / Math.cos(Math.toRadians(centerLat))
        for (i in 0..36) {
            val angle = i * 10.0 * (Math.PI / 180.0)
            val lat = centerLat + (dLat * Math.sin(angle))
            val lon = centerLon + (dLon * Math.cos(angle))
            points.add(lat to lon)
        }
        return points
    }

    private fun parseGeoJsonCoordinates(geoJsonString: String?): List<Pair<Double, Double>> {
        return parseRouteGeoJson(geoJsonString)
    }

    private fun parseRouteGeoJson(geoJsonString: String?): List<Pair<Double, Double>> {
        if (geoJsonString.isNullOrBlank()) return emptyList()
        val coordinates = mutableListOf<Pair<Double, Double>>()
        try {
            val root = JSONObject(geoJsonString)
            val type = root.optString("type")
            if (type == "LineString") {
                val coordsArray = root.optJSONArray("coordinates")
                if (coordsArray != null) {
                    for (j in 0 until coordsArray.length()) {
                        val pt = coordsArray.getJSONArray(j)
                        val lon = pt.getDouble(0)
                        val lat = pt.getDouble(1)
                        coordinates.add(lat to lon)
                    }
                }
            } else {
                val features = root.optJSONArray("features") ?: return emptyList()
                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val geometry = feature.getJSONObject("geometry")
                    val geomType = geometry.getString("type")
                    if (geomType == "LineString") {
                        val coordsArray = geometry.getJSONArray("coordinates")
                        for (j in 0 until coordsArray.length()) {
                            val pt = coordsArray.getJSONArray(j)
                            val lon = pt.getDouble(0)
                            val lat = pt.getDouble(1)
                            coordinates.add(lat to lon)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("MapViewModel", "Route geojson parse fallback: ${e.message}")
        }
        return coordinates
    }

    private fun <K, V> mapOfNotNull(vararg pairs: Pair<K, V?>): Map<K, V> {
        val result = mutableMapOf<K, V>()
        for ((k, v) in pairs) {
            if (v != null) result[k] = v
        }
        return result
    }
}
