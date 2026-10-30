package com.touristsafety.app.feature.trip

import android.content.Context
import android.os.BatteryManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.database.dao.ActiveTripDao
import com.touristsafety.app.core.database.dao.TrekTrackPointDao
import com.touristsafety.app.core.location.ActiveHazardAlert
import com.touristsafety.app.core.location.ActiveTripTrackingService
import com.touristsafety.app.core.location.DeviceLocationRepository
import com.touristsafety.app.core.location.HazardAlertEngine
import com.touristsafety.app.core.model.MapFeatureCategory
import com.touristsafety.app.core.model.UnifiedMapFeature
import com.touristsafety.app.core.model.RouteOrderedHazardItem
import com.touristsafety.app.core.model.LiveWeatherContext
import com.touristsafety.app.core.model.LiveNetworkStatus
import com.touristsafety.app.core.model.TrekSessionStatus
import com.touristsafety.app.core.network.dto.NearbyFacilityDto
import com.touristsafety.app.core.spatial.LiveTrekContextEngine
import com.touristsafety.app.core.network.dto.ElevationProfilePointDto
import com.touristsafety.app.core.network.dto.RouteWaypointConditionDto
import com.touristsafety.app.core.repository.RouteRepository
import com.touristsafety.app.core.repository.TrailReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ActiveTripUiState(
    val tripId: String = "",
    val routeId: String = "",
    val status: String = "ACTIVE",
    val destinationName: String = "",
    val traveledDistanceKm: Double = 0.0,
    val elapsedDurationFormatted: String = "00:00:00",
    val isPaused: Boolean = false,
    val progressPct: Double = 0.0,
    val isOffRoute: Boolean = false,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val gpsAccuracyMeters: Float? = null,
    val gpsStatus: String = "SEARCHING_GPS",
    val nearestHazardDistKm: Double? = null,
    val nearestHazardTitle: String? = null,
    val nextWaypoint: RouteWaypointConditionDto? = null,
    val totalDistanceKm: Double = 0.0,
    val routeCoordinates: List<Pair<Double, Double>> = emptyList(),
    val traveledTrackCoordinates: List<Pair<Double, Double>> = emptyList(),
    val elevationProfile: List<ElevationProfilePointDto> = emptyList(),
    val totalAscentM: Double = 0.0,
    val maxElevationM: Double = 0.0,
    val isCompleted: Boolean = false,
    val isCheckingIn: Boolean = false,
    val checkInMessage: String? = null,
    val isSosTriggered: Boolean = false,
    val activeAlertBanner: ActiveHazardAlert? = null,
    val soundAlertsEnabled: Boolean = true,
    val vibrationAlertsEnabled: Boolean = true,
    val layerVisibility: Map<String, Boolean> = mapOf(
        "hazards" to true,
        "fires" to true,
        "hospitals" to true,
        "police" to true,
        "shelters" to true
    ),
    val selectedFeatureDetail: UnifiedMapFeature? = null,
    val remainingDistanceKm: Double = 0.0,
    val routeProgressKm: Double = 0.0,
    val upcomingHazards: List<RouteOrderedHazardItem> = emptyList(),
    val nearbyHazards: List<RouteOrderedHazardItem> = emptyList(),
    val passedHazards: List<RouteOrderedHazardItem> = emptyList(),
    val liveWeather: LiveWeatherContext = LiveWeatherContext(),
    val liveNetwork: LiveNetworkStatus = LiveNetworkStatus(),
    val nearbyFacilities: List<NearbyFacilityDto> = emptyList()
)

@HiltViewModel
class ActiveTripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository,
    private val deviceLocationRepository: DeviceLocationRepository,
    private val trekTrackPointDao: TrekTrackPointDao,
    private val activeTripDao: ActiveTripDao,
    private val hazardAlertEngine: HazardAlertEngine,
    private val liveTrekContextEngine: LiveTrekContextEngine,
    val trailReportRepository: TrailReportRepository? = null,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val locationState = deviceLocationRepository.locationState
    val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(ActiveTripUiState(tripId = tripId))
    val uiState: StateFlow<ActiveTripUiState> = _uiState.asStateFlow()

    // Cached static route metadata to prevent repeated Room queries and JSON parsing on GPS ticks
    private var cachedRouteId: String? = null
    private var cachedWaypoints: List<RouteWaypointConditionDto> = emptyList()
    private var cachedElevPoints: List<ElevationProfilePointDto> = emptyList()
    private var cachedRouteCoords: List<Pair<Double, Double>> = emptyList()
    private var cachedDestinationName: String = "Destination"
    private var cachedTotalDistanceKm: Double = 0.0
    private var cachedTotalAscentM: Double = 0.0
    private var cachedMaxElevationM: Double = 0.0

    init {
        observeTrip()
        observeTrackPoints()
        observeGpsLocation()
        observeHazardAlerts()
        observeLiveTrekEngine()
    }

    private fun observeLiveTrekEngine() {
        viewModelScope.launch {
            liveTrekContextEngine.liveContext.collect { context ->
                if (context.sessionStatus != TrekSessionStatus.IDLE) {
                    _uiState.update { current ->
                        current.copy(
                            traveledDistanceKm = context.progress.traveledDistanceMeters / 1000.0,
                            routeProgressKm = context.progress.routeProgressMeters / 1000.0,
                            remainingDistanceKm = context.progress.remainingDistanceMeters / 1000.0,
                            progressPct = context.progress.progressPct.toDouble(),
                            isOffRoute = context.progress.isOffRoute,
                            currentLatitude = context.progress.lastAcceptedLat ?: current.currentLatitude,
                            currentLongitude = context.progress.lastAcceptedLon ?: current.currentLongitude,
                            gpsAccuracyMeters = context.progress.currentGpsAccuracyM ?: current.gpsAccuracyMeters,
                            upcomingHazards = context.hazards.upcomingHazards,
                            nearbyHazards = context.hazards.nearbyHazards,
                            passedHazards = context.hazards.passedHazards,
                            liveWeather = context.weather,
                            liveNetwork = context.networkStatus,
                            nearbyFacilities = context.nearbyFacilities,
                            activeAlertBanner = context.activeCriticalAlert ?: current.activeAlertBanner
                        )
                    }
                }
            }
        }
    }

    private fun getBatteryPercentage(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (pct in 1..100) pct else 85
        } catch (e: Exception) {
            85
        }
    }

    private fun observeTrip() {
        viewModelScope.launch {
            routeRepository.activeTripFlow.collect { trip ->
                if (trip != null && trip.tripId == tripId) {
                    // Load static route definition once into memory
                    if (cachedRouteId != trip.routeId) {
                        cachedRouteId = trip.routeId
                        val route = routeRepository.getRouteById(trip.routeId)
                        if (route != null) {
                            cachedDestinationName = route.destinationName
                            cachedTotalDistanceKm = route.distanceKm
                            cachedTotalAscentM = route.totalAscentM
                            cachedMaxElevationM = route.maxElevationM
                            cachedWaypoints = try {
                                json.decodeFromString<List<RouteWaypointConditionDto>>(route.conditionsJson)
                            } catch (_: Exception) {
                                emptyList()
                            }
                            cachedElevPoints = if (route.elevationProfileJson != null) {
                                try {
                                    json.decodeFromString<List<ElevationProfilePointDto>>(route.elevationProfileJson)
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            } else emptyList()
                            cachedRouteCoords = if (route.routeGeoJson != null) {
                                parseRouteCoordsFromGeoJson(route.routeGeoJson)
                            } else emptyList()

                            if (cachedRouteCoords.isNotEmpty() && liveTrekContextEngine.liveContext.value.sessionStatus == TrekSessionStatus.IDLE) {
                                liveTrekContextEngine.startTrekSession(
                                    tripId = trip.tripId,
                                    routeId = trip.routeId,
                                    points = cachedRouteCoords,
                                    totalDistanceKm = cachedTotalDistanceKm,
                                    destination = cachedDestinationName,
                                    initialTraveledMeters = trip.traveledDistanceMeters
                                )
                            }
                        }
                    }

                    val nextWp = if (cachedWaypoints.isNotEmpty() && trip.currentSegmentIndex < cachedWaypoints.size) {
                        cachedWaypoints[minOf(cachedWaypoints.size - 1, trip.currentSegmentIndex + 1)]
                    } else null

                    val elapsedFormatted = formatDuration(trip.elapsedDurationSeconds)

                    _uiState.update {
                        it.copy(
                            tripId = trip.tripId,
                            routeId = trip.routeId,
                            status = trip.status,
                            destinationName = cachedDestinationName,
                            traveledDistanceKm = trip.traveledDistanceMeters / 1000.0,
                            elapsedDurationFormatted = elapsedFormatted,
                            isPaused = trip.isPaused,
                            progressPct = trip.progressPct,
                            isOffRoute = trip.isOffRoute,
                            nearestHazardDistKm = trip.nearestHazardDistKm,
                            nearestHazardTitle = trip.nearestHazardTitle,
                            nextWaypoint = nextWp,
                            totalDistanceKm = cachedTotalDistanceKm,
                            routeCoordinates = cachedRouteCoords,
                            elevationProfile = cachedElevPoints,
                            totalAscentM = cachedTotalAscentM,
                            maxElevationM = cachedMaxElevationM,
                            isCompleted = trip.status == "COMPLETED"
                        )
                    }
                }
            }
        }
    }

    private fun observeTrackPoints() {
        viewModelScope.launch {
            trekTrackPointDao.getAllAcceptedPointsFlow(tripId).collect { points ->
                if (points.size != _uiState.value.traveledTrackCoordinates.size) {
                    val coords = points.map { Pair(it.latitude, it.longitude) }
                    _uiState.update { it.copy(traveledTrackCoordinates = coords) }
                }
            }
        }
    }

    private fun observeGpsLocation() {
        viewModelScope.launch {
            deviceLocationRepository.locationState.collect { loc ->
                val accuracy = loc.accuracyMeters ?: loc.location?.accuracy
                val status = when {
                    accuracy == null -> "SEARCHING_GPS"
                    accuracy <= 15.0f -> "GPS_ACCURATE"
                    accuracy <= 35.0f -> "GPS_MEDIUM"
                    else -> "GPS_WEAK"
                }
                _uiState.update {
                    it.copy(
                        currentLatitude = loc.latitude,
                        currentLongitude = loc.longitude,
                        gpsAccuracyMeters = accuracy,
                        gpsStatus = status
                    )
                }
            }
        }
    }

    private fun observeHazardAlerts() {
        viewModelScope.launch {
            hazardAlertEngine.activeAlert.collect { alert ->
                _uiState.update { it.copy(activeAlertBanner = alert) }
            }
        }
    }

    fun pauseTrek(ctx: Context) {
        ActiveTripTrackingService.pause(ctx)
        liveTrekContextEngine.pauseTrekSession()
        _uiState.update { it.copy(isPaused = true) }
    }

    fun resumeTrek(ctx: Context) {
        ActiveTripTrackingService.resume(ctx)
        liveTrekContextEngine.resumeTrekSession()
        _uiState.update { it.copy(isPaused = false) }
    }

    fun endTrip(ctx: Context) {
        viewModelScope.launch {
            routeRepository.stopTrip(tripId)
            ActiveTripTrackingService.stop(ctx)
            liveTrekContextEngine.stopTrekSession()
            hazardAlertEngine.dismissActiveAlert()
            _uiState.update { it.copy(isCompleted = true, status = "COMPLETED") }
        }
    }

    fun dismissActiveAlert() {
        hazardAlertEngine.dismissActiveAlert()
        _uiState.update { it.copy(activeAlertBanner = null) }
    }

    fun toggleSoundAlerts() {
        val newVal = !_uiState.value.soundAlertsEnabled
        hazardAlertEngine.isSoundEnabled = newVal
        _uiState.update { it.copy(soundAlertsEnabled = newVal) }
    }

    fun toggleVibrationAlerts() {
        val newVal = !_uiState.value.vibrationAlertsEnabled
        hazardAlertEngine.isVibrationEnabled = newVal
        _uiState.update { it.copy(vibrationAlertsEnabled = newVal) }
    }

    fun toggleLayer(category: String) {
        _uiState.update {
            val current = it.layerVisibility[category] ?: true
            val updated = it.layerVisibility.toMutableMap()
            updated[category] = !current
            it.copy(layerVisibility = updated)
        }
    }

    fun selectFeatureDetail(feature: UnifiedMapFeature?) {
        _uiState.update { it.copy(selectedFeatureDetail = feature) }
    }

    fun submitCheckIn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingIn = true) }
            val state = _uiState.value
            val locState = deviceLocationRepository.locationState.value
            val lat = locState.latitude ?: state.nextWaypoint?.latitude ?: 0.0
            val lon = locState.longitude ?: state.nextWaypoint?.longitude ?: 0.0
            val elev = locState.location?.altitude ?: (state.nextWaypoint?.elevation_m ?: 0.0)
            val battery = getBatteryPercentage()

            val result = routeRepository.submitCheckIn(
                tripId = tripId,
                latitude = lat,
                longitude = lon,
                elevationM = elev,
                batteryPct = battery,
                status = "OK"
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isCheckingIn = false,
                            checkInMessage = "Check-In Verified: Next check-in in 3 hours."
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isCheckingIn = false,
                            checkInMessage = "Check-in stored offline. Will sync when connected."
                        )
                    }
                }
            )
        }
    }

    fun triggerEmergencySos() {
        viewModelScope.launch {
            val state = _uiState.value
            val locState = deviceLocationRepository.locationState.value
            val lat = locState.latitude ?: state.nextWaypoint?.latitude ?: 0.0
            val lon = locState.longitude ?: state.nextWaypoint?.longitude ?: 0.0
            val elev = locState.location?.altitude ?: (state.nextWaypoint?.elevation_m ?: 0.0)
            val battery = getBatteryPercentage()

            routeRepository.triggerEmergencySos(
                tripId = tripId,
                latitude = lat,
                longitude = lon,
                elevationM = elev,
                batteryPct = battery,
                emergencyType = "MOUNTAIN_SOS",
                details = "Trekker manual emergency beacon trigger from mobile field unit."
            )
            _uiState.update {
                it.copy(
                    isSosTriggered = true,
                    checkInMessage = "EMERGENCY SOS BEACON ACTIVE. Search & Rescue incident transmitted."
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(checkInMessage = null) }
    }

    private fun parseRouteCoordsFromGeoJson(geoJson: String): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        try {
            val coordsPattern = """"coordinates"\s*:\s*\[(\s*\[[^\]]+\]\s*,?)+\]""".toRegex()
            val coordPairPattern = """\[\s*(-?\d+\.?\d*)\s*,\s*(-?\d+\.?\d*)\s*\]""".toRegex()

            val match = coordsPattern.find(geoJson)
            if (match != null) {
                coordPairPattern.findAll(match.value).forEach { m ->
                    val lon = m.groupValues[1].toDoubleOrNull()
                    val lat = m.groupValues[2].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        result.add(Pair(lat, lon))
                    }
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return result
    }

    private fun formatDuration(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
