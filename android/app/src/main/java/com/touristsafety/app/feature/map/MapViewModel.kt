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
    MEDICAL("Medical", MapFeatureCategory.HOSPITAL),
    POLICE("Police", MapFeatureCategory.POLICE_STATION),
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
    val userLocation: Pair<Double, Double> = 32.2396 to 77.1887,
    val userLocationAccuracyMeters: Float? = null,
    val isUserLocationAcquired: Boolean = false,
    val isLocationFresh: Boolean = false,
    val isLocating: Boolean = false,
    val locationErrorMessage: String? = null,
    val cameraTarget: Pair<Double, Double> = 32.2396 to 77.1887,
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
    val activeDecisionExplanation: String? = null
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
                            routeSummaryFireCount = 0
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
                    triggerRouteCorridorAnalysis(activeRoute.route_id, activeRoute.route_version, activeRoute)
                }
            }
        }

        // 2. Emergency Map Points (Hospitals, Clinics, Police, Fire Station, Shelters)
        viewModelScope.launch {
            emergencyRepository.getMapPoints().collect { points ->
                rawMapPoints = points
                rebuildUnifiedFeatures()
            }
        }

        // 3. Forest Geography (OSM)
        viewModelScope.launch {
            forestRepository.getForestFeatures().collect { forests ->
                rawForestFeatures = forests
                _uiState.update { it.copy(routeSummaryForestCount = forests.size) }
                rebuildUnifiedFeatures()
            }
        }

        // 4. Wildfire Detections (NASA FIRMS)
        viewModelScope.launch {
            fireHotspotRepository.getHotspots().collect { fires ->
                rawFireHotspots = fires
                _uiState.update { it.copy(routeSummaryFireCount = fires.size) }
                rebuildUnifiedFeatures()
            }
        }

        // 5. Disaster Hazard Zones (Room / DDMA)
        viewModelScope.launch {
            disasterRepository.hazardZones.collect { zones ->
                val userLoc = _uiState.value.userLocation
                rawHazardZones = zones.map { z ->
                    val dist = MapClusterHelper.calculateDistanceMeters(userLoc.first, userLoc.second, z.centerLatitude, z.centerLongitude)
                    // Generate circle perimeter coordinates for map polygon fill
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

    private fun triggerRouteCorridorAnalysis(
        routeId: String,
        version: Int,
        fallbackRoute: PlannedRouteResponseDto
    ) {
        currentRouteAnalysisJob?.cancel()
        val requestId = "${routeId}_$version"
        activeAnalysisRequestId = requestId

        currentRouteAnalysisJob = viewModelScope.launch {
            _uiState.update {
                it.copy(routeLifecycleState = RouteLifecycleState.ANALYZING_CORRIDOR)
            }

            val analysisResult = routeRepository.analyzeRoute(routeId)
            // Ensure this request hasn't been superseded by another route selection
            if (activeAnalysisRequestId != requestId) {
                Log.d("MapViewModel", "Discarded stale route analysis for $requestId")
                return@launch
            }

            analysisResult.fold(
                onSuccess = { analysis ->
                    applyRouteAnalysisFeatures(analysis)
                },
                onFailure = { err ->
                    Log.w("MapViewModel", "Route analysis API call failed: ${err.message}. Applying fallback from planned route.", err)
                    applyFallbackRouteAnalysis(fallbackRoute)
                }
            )
        }
    }

    private fun applyRouteAnalysisFeatures(analysis: RouteAnalysisResponseDto) {
        val userLoc = _uiState.value.userLocation

        // 1. Hazard zones from disaster_alerts
        rawHazardZones = analysis.disaster_alerts.map { a ->
            val dist = (a.distance_to_alert_km * 1000f).toFloat()
            val circleCoords = generateCirclePolygon(userLoc.first, userLoc.second, 3000.0)
            UnifiedMapFeature(
                id = "hazard_${a.alert_id}",
                category = MapFeatureCategory.HAZARD_ZONE,
                title = a.title,
                subtitle = "Severity: ${a.severity} • Category: ${a.hazard_category.uppercase()}",
                latitude = userLoc.first,
                longitude = userLoc.second,
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
                geometryPolygon = circleCoords
            )
        }

        // 2. Active Fires from fire_detections
        rawFireHotspots = analysis.fire_detections.mapIndexed { idx, fire ->
            FireHotspot(
                id = "firms_corridor_$idx",
                latitude = userLoc.first,
                longitude = userLoc.second,
                brightness = 325.0,
                confidence = fire.confidence ?: "nominal",
                satellite = "MODIS/VIIRS",
                acquisitionDate = fire.acquisition_time ?: "NRT",
                acquisitionTime = "",
                frpMw = fire.fire_radiative_power ?: 15.0,
                isSimulated = false
            )
        }

        // 3. Assistance points from assistance_points (Safe Havens)
        rawMapPoints = analysis.assistance_points.mapIndexed { idx, sh ->
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
                description = "Emergency Safe Haven • ${sh.distance_km} km from trail corridor",
                provenance = "OpenStreetMap Overpass Verified",
                verificationStatus = "verified",
                verificationTimestampEpochMs = System.currentTimeMillis()
            )
        }

        // 4. Waypoint Weather
        val firstWeather = analysis.weather_by_waypoint.firstOrNull()
        if (firstWeather != null) {
            val wModel = Weather(
                latitude = userLoc.first,
                longitude = userLoc.second,
                temperatureC = firstWeather.temperature_c,
                relativeHumidityPct = firstWeather.relative_humidity_pct.toDouble(),
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
                routeOverallHazardScore = analysis.overall_hazard_score,
                routeSafetyStatus = analysis.overall_safety_status,
                activeDecisionExplanation = analysis.decision_explanation
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
                activeDecisionExplanation = fallbackRoute.decision_explanation
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
                PointCategory.SHELTER -> MapFeatureCategory.SHELTER
                PointCategory.VISITOR_INFO, PointCategory.TRANSPORT, PointCategory.VERIFIED_PUBLIC -> MapFeatureCategory.TOURIST_ASSISTANCE
                PointCategory.EMERGENCY_SERVICE -> MapFeatureCategory.FIRE_STATION
            }
            val dist = MapClusterHelper.calculateDistanceMeters(userLoc.first, userLoc.second, pt.latitude, pt.longitude)
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
                    details = mapOfNotNull(
                        "Address" to pt.address,
                        "Phone" to pt.phone,
                        "Category" to cat.displayName,
                        "Provenance" to pt.provenance,
                        "Status" to pt.verificationStatus
                    )
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
                MapFilterChip.MEDICAL -> feat.category == MapFeatureCategory.HOSPITAL || feat.category == MapFeatureCategory.CLINIC
                MapFilterChip.POLICE -> feat.category == MapFeatureCategory.POLICE_STATION
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
                destName = place.name
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
                    routeCoordinates = it.primaryRouteCoordinates
                )
            }
            rebuildUnifiedFeatures()
            return
        }

        val alt = current.plannedRouteAlternatives.find { it.id == altId } ?: return
        val altCoords = parseGeoJsonCoordinates(alt.geometry_geojson?.toString())
        if (altCoords.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    selectedAlternativeId = altId,
                    routeCoordinates = altCoords,
                    routeDistanceKm = alt.distance_km,
                    routeDurationHours = alt.duration_minutes / 60.0,
                    routeOverallHazardScore = alt.hazard_score,
                    routeSafetyStatus = if (alt.hazard_score < 35.0) "SAFE" else "CAUTION"
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
