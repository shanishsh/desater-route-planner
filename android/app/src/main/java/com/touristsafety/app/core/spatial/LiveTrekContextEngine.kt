package com.touristsafety.app.core.spatial

import android.content.Context
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.touristsafety.app.core.audio.SafetyAlertPlayer
import com.touristsafety.app.core.audio.TrekVoiceAssistant
import com.touristsafety.app.core.model.SafetyEvent
import com.touristsafety.app.core.model.SafetyEventType
import com.touristsafety.app.core.network.dto.*
import com.touristsafety.app.core.repository.AssistantRepository
import com.touristsafety.app.core.database.dao.ActiveTripDao
import com.touristsafety.app.core.database.dao.FireHotspotDao
import com.touristsafety.app.core.database.dao.HazardZoneDao
import com.touristsafety.app.core.database.dao.TrekTrackPointDao
import com.touristsafety.app.core.database.entity.TrekTrackPointEntity
import com.touristsafety.app.core.location.ActiveHazardAlert
import com.touristsafety.app.core.location.GpsFilter
import com.touristsafety.app.core.location.HazardAlertEngine
import com.touristsafety.app.core.model.*
import com.touristsafety.app.core.network.dto.NearbyFacilityDto
import com.touristsafety.app.core.repository.PointContextRepository
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.round

/**
 * Centralized, observable Live Trek Context Engine.
 *
 * Drives dynamic, location-aware safety intelligence as the trekker progresses along a mountain trail.
 *
 * Responsibilities:
 * 1. Tracks accepted GPS points and accumulates genuine travelled distance (odometer).
 * 2. Matches live coordinates to selected trail geometry, evaluating chainage, remaining distance, and off-route status.
 * 3. Dynamically reclassifies corridor hazards into:
 *    - Ahead on route (ordered with sequence numbers #01, #02, distance ahead, and estimated arrival minutes)
 *    - Near current location (within physical proximity radius regardless of route alignment)
 *    - Passed (traversed and behind current progress)
 *    - Nearby off-route
 *    - Uncertain
 * 4. Manages tiered event-driven refresh intervals:
 *    - Weather: Refreshed on > 1.5 km movement or 30-minute interval
 *    - Facilities: Refreshed on > 1.0 km movement
 *    - Network status: Reacts to ConnectivityManager and calculates distance to nearest known mast
 * 5. Dispatches loud alarm + vibration alerts for imminent severe/critical hazards with cooldown deduplication.
 */
@Singleton
class LiveTrekContextEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routeRepository: RouteRepository,
    private val pointContextRepository: PointContextRepository,
    private val hazardZoneDao: HazardZoneDao,
    private val fireHotspotDao: FireHotspotDao,
    private val activeTripDao: ActiveTripDao,
    private val trekTrackPointDao: TrekTrackPointDao,
    private val hazardAlertEngine: HazardAlertEngine,
    private val safetyAlertPlayer: SafetyAlertPlayer,
    private val deterministicDecisionEngine: DeterministicSafetyDecisionEngine,
    private val trekVoiceAssistant: TrekVoiceAssistant,
    private val assistantRepository: AssistantRepository
) {
    companion object {
        private const val TAG = "LiveTrekContextEngine"
        private const val WEATHER_REFRESH_DISTANCE_METERS = 1500.0
        private const val WEATHER_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        private const val FACILITIES_REFRESH_DISTANCE_METERS = 1000.0
        private const val TYPICAL_HIKING_SPEED_KMH = 3.5
        private val cachedLocalBaselineFeatures = mutableListOf<UnifiedMapFeature>()
        private var isBaselineFeaturesLoaded = false
        private val baselineLock = Any()
    }

    private data class PrecomputedHazardRouteInfo(
        val featureId: String,
        val feature: UnifiedMapFeature,
        val categoryType: HazardCategoryType,
        val entryChainageMeters: Double,
        val exitChainageMeters: Double,
        val crossTrackDistanceMeters: Double,
        val isDirectIntersection: Boolean,
        val isPolygon: Boolean,
        val physicalRadiusMeters: Double
    )

    private val precomputedHazardMap = mutableMapOf<String, PrecomputedHazardRouteInfo>()
    private val spokenEventIds = mutableSetOf<String>()

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _liveContext = MutableStateFlow(LiveTrekContext())
    val liveContext: StateFlow<LiveTrekContext> = _liveContext.asStateFlow()

    // Active session configuration & spatial index
    private var activeTripId: String? = null
    private var activeRouteId: String? = null
    private var destinationName: String = "Destination"
    private var routePoints: List<Pair<Double, Double>> = emptyList()
    private var cumDistances: DoubleArray = DoubleArray(0)
    private var totalRouteDistanceMeters: Double = 0.0
    private var destinationCoord: Pair<Double, Double>? = null

    // Progress & GPS accumulation
    private var lastAcceptedLat: Double? = null
    private var lastAcceptedLon: Double? = null
    private var lastAcceptedEpochMs: Long? = null
    private var cumulativeTraveledMeters: Double = 0.0
    private var currentChainageMeters: Double = 0.0
    private var isPaused: Boolean = false
    private var sessionStartTimeEpochMs: Long = 0L

    // Cached hazards, baseline datasets, and facilities
    private var monitoredFeatures: List<UnifiedMapFeature> = emptyList()
    private val cachedCellTowers = mutableListOf<CellTowerInfo>()

    // Assistant & Generation tracking
    private var currentGenerationId: Int = 1
    private var lastGenUpdateLat: Double? = null
    private var lastGenUpdateLon: Double? = null

    // Tiered refresh tracking
    private var lastWeatherQueryLocation: Pair<Double, Double>? = null
    private var lastWeatherQueryEpochMs: Long = 0L
    private var lastFacilitiesQueryLocation: Pair<Double, Double>? = null
    private var isWeatherRefreshing: Boolean = false
    private var isFacilitiesRefreshing: Boolean = false

    // Track point batch buffer
    private val pointBuffer = java.util.Collections.synchronizedList(mutableListOf<TrekTrackPointEntity>())
    private var lastBufferFlushMs: Long = 0L

    private data class CellTowerInfo(
        val id: String,
        val lat: Double,
        val lon: Double,
        val operator: String,
        val rangeMeters: Double
    )

    init {
        loadCellTowersFromAssets()
        observeNetworkState()
        engineScope.launch(Dispatchers.IO) {
            loadAllLocalFeatures()
        }
    }

    private fun loadCellTowersFromAssets() {
        engineScope.launch(Dispatchers.IO) {
            try {
                context.assets.open("uttarakhand_cell_towers.json").use { stream ->
                    val text = stream.bufferedReader(Charsets.UTF_8).readText()
                    val arr = JSONArray(text)
                    val towers = mutableListOf<CellTowerInfo>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val lat = obj.optDouble("latitude")
                        val lon = obj.optDouble("longitude")
                        if (lat.isNaN() || lon.isNaN()) continue
                        towers.add(
                            CellTowerInfo(
                                id = obj.optString("id", "tower_$i"),
                                lat = lat,
                                lon = lon,
                                operator = obj.optString("operator", "Cell Tower"),
                                rangeMeters = obj.optDouble("range_m", 3000.0)
                            )
                        )
                    }
                    synchronized(cachedCellTowers) {
                        cachedCellTowers.clear()
                        cachedCellTowers.addAll(towers)
                    }
                    Log.d(TAG, "Loaded ${cachedCellTowers.size} cell towers for live coverage estimation.")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed loading cell towers asset: ${t.message}")
            }
        }
    }

    private fun observeNetworkState() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    evaluateNetworkStatus()
                }

                override fun onLost(network: android.net.Network) {
                    evaluateNetworkStatus()
                }

                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    evaluateNetworkStatus()
                }
            })
        }
    }

    fun evaluateNetworkStatus() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)

        val isConnected = caps != null && (
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )

        val connType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
            else -> "OFFLINE"
        }

        val lat = lastAcceptedLat
        val lon = lastAcceptedLon

        var nearestTowerDist: Double? = null
        var nearestOperator: String? = null
        var coverageState = "UNKNOWN"

        if (lat != null && lon != null) {
            val towers = synchronized(cachedCellTowers) { ArrayList(cachedCellTowers) }
            var minDist = Double.MAX_VALUE
            var bestTower: CellTowerInfo? = null

            for (t in towers) {
                val d = RouteMatchingEngine.haversineDistanceMeters(lat, lon, t.lat, t.lon)
                if (d < minDist) {
                    minDist = d
                    bestTower = t
                }
            }

            if (bestTower != null) {
                nearestTowerDist = minDist
                nearestOperator = bestTower.operator
                coverageState = when {
                    minDist <= bestTower.rangeMeters -> "GOOD"
                    minDist <= (bestTower.rangeMeters * 1.5) -> "MARGINAL"
                    else -> "DEAD_ZONE_LIKELY"
                }
            }
        }

        _liveContext.update { current ->
            current.copy(
                networkStatus = LiveNetworkStatus(
                    isConnected = isConnected,
                    connectionType = connType,
                    nearestTowerDistanceM = nearestTowerDist,
                    nearestTowerOperator = nearestOperator,
                    estimatedCoverage = coverageState,
                    isOffline = !isConnected,
                    lastSyncedEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Initializes or resumes an active trek session by loading route details from Room storage.
     */
    fun startTrekSession(tripId: String, routeId: String, destination: String) {
        engineScope.launch {
            val route = routeRepository.getRouteById(routeId)
            val points = if (route?.routeGeoJson != null) {
                parseCoordsFromGeoJson(route.routeGeoJson)
            } else emptyList()
            val distKm = route?.distanceKm ?: 0.0
            val trip = activeTripDao.getTripById(tripId)
            val initialDist = trip?.traveledDistanceMeters ?: 0.0
            startTrekSession(
                tripId = tripId,
                routeId = routeId,
                points = points,
                totalDistanceKm = distKm,
                destination = destination,
                initialTraveledMeters = initialDist
            )
        }
    }

    /**
     * Initializes or resumes an active trek session with route geometry and baseline hazards.
     */
    fun startTrekSession(
        tripId: String,
        routeId: String,
        points: List<Pair<Double, Double>>,
        totalDistanceKm: Double,
        destination: String,
        features: List<UnifiedMapFeature> = emptyList(),
        initialTraveledMeters: Double = 0.0
    ) {
        engineScope.launch {
            activeTripId = tripId
            activeRouteId = routeId
            destinationName = destination
            routePoints = points
            cumDistances = RouteMatchingEngine.buildCumulativeDistances(points)
            totalRouteDistanceMeters = if (cumDistances.isNotEmpty()) cumDistances.last() else totalDistanceKm * 1000.0
            destinationCoord = points.lastOrNull()
            cumulativeTraveledMeters = initialTraveledMeters
            sessionStartTimeEpochMs = System.currentTimeMillis()
            isPaused = false
            deterministicDecisionEngine.reset()
            assistantRepository.clearCache()

            // Store features or load local baseline if empty
            monitoredFeatures = if (features.isNotEmpty()) {
                features
            } else {
                loadAllLocalFeatures()
            }
            spokenEventIds.clear()
            precomputeHazardRouteIntersections(monitoredFeatures)

            _liveContext.update {
                it.copy(
                    tripId = tripId,
                    routeId = routeId,
                    destinationName = destination,
                    sessionStatus = TrekSessionStatus.ACTIVE,
                    progress = TrekProgressMetrics(
                        traveledDistanceMeters = initialTraveledMeters,
                        totalRouteDistanceMeters = totalRouteDistanceMeters,
                        remainingDistanceMeters = totalRouteDistanceMeters
                    )
                )
            }

            Log.i(TAG, "Live Trek Session started for trip: $tripId ($destinationName, ${points.size} vertices)")
            evaluateNetworkStatus()
        }
    }

    fun pauseTrekSession() {
        isPaused = true
        _liveContext.update { it.copy(sessionStatus = TrekSessionStatus.PAUSED) }
    }

    fun resumeTrekSession() {
        isPaused = false
        _liveContext.update { it.copy(sessionStatus = TrekSessionStatus.ACTIVE) }
    }

    fun stopTrekSession() {
        deterministicDecisionEngine.reset()
        spokenEventIds.clear()
        synchronized(precomputedHazardMap) { precomputedHazardMap.clear() }
        trekVoiceAssistant.stopSpeaking()
        safetyAlertPlayer.stopSafetyAlert()
        engineScope.launch {
            flushPointBuffer()
            activeTripId = null
            activeRouteId = null
            routePoints = emptyList()
            cumDistances = DoubleArray(0)
            _liveContext.update { it.copy(sessionStatus = TrekSessionStatus.COMPLETED) }
            Log.i(TAG, "Live Trek Session completed.")
        }
    }

    /**
     * Updates the monitored corridor features (e.g. from live backend route analysis).
     */
    fun updateMonitoredFeatures(features: List<UnifiedMapFeature>) {
        if (features.isNotEmpty()) {
            monitoredFeatures = features
            precomputeHazardRouteIntersections(features)
            // Re-evaluate hazard classification immediately with existing location
            lastAcceptedLat?.let { lat ->
                lastAcceptedLon?.let { lon ->
                    reclassifyHazards(lat, lon, currentChainageMeters, _liveContext.value.progress.isOffRoute)
                }
            }
        }
    }

    /**
     * Ingests, filters, and processes incoming GPS locations from location services.
     */
    fun processGpsUpdate(location: Location) {
        val now = System.currentTimeMillis()
        val speedKmh = location.speed * 3.6

        // 1. Noise, drift, and multipath filtering
        val filterResult = GpsFilter.filter(
            lastAcceptedLat = lastAcceptedLat,
            lastAcceptedLon = lastAcceptedLon,
            lastAcceptedEpochMs = lastAcceptedEpochMs,
            lat = location.latitude,
            lon = location.longitude,
            accuracyMeters = location.accuracy,
            speedKmh = speedKmh,
            timestampEpochMs = now
        )

        val isAccepted = filterResult.isAcceptedForDistance && !isPaused

        if (isAccepted) {
            cumulativeTraveledMeters += filterResult.deltaMeters
            lastAcceptedLat = location.latitude
            lastAcceptedLon = location.longitude
            lastAcceptedEpochMs = now
        }

        // Buffer breadcrumb point for active trip session
        activeTripId?.let { tripId ->
            pointBuffer.add(
                TrekTrackPointEntity(
                    tripId = tripId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = location.altitude,
                    accuracyMeters = location.accuracy,
                    speedKmh = speedKmh,
                    recordedAtEpochMs = now,
                    isAcceptedForDistance = isAccepted,
                    isSynced = false
                )
            )
            if (pointBuffer.size >= 5 || (now - lastBufferFlushMs) >= 15_000L) {
                engineScope.launch(Dispatchers.IO) { flushPointBuffer() }
            }
        }

        // 2. Route Matching & Chainage Projection
        val proj = if (routePoints.size >= 2) {
            RouteMatchingEngine.projectPointOntoRoute(
                pointLat = location.latitude,
                pointLon = location.longitude,
                routePoints = routePoints,
                cumDistances = cumDistances,
                previousChainageMeters = if (currentChainageMeters > 0.0) currentChainageMeters else null,
                gpsAccuracyMeters = location.accuracy
            )
        } else {
            RouteMatchingEngine.RouteProjectionResult(
                chainageMeters = 0.0,
                crossTrackDistanceMeters = 0.0,
                segmentIndex = 0,
                projectedLatitude = location.latitude,
                projectedLongitude = location.longitude,
                isOffRoute = false
            )
        }

        currentChainageMeters = proj.chainageMeters

        val remainingMeters = max(0.0, totalRouteDistanceMeters - currentChainageMeters)
        val straightLineMeters = destinationCoord?.let {
            RouteMatchingEngine.haversineDistanceMeters(location.latitude, location.longitude, it.first, it.second)
        } ?: remainingMeters

        val progressPct = if (totalRouteDistanceMeters > 0.0) {
            (((currentChainageMeters / totalRouteDistanceMeters) * 100.0).coerceIn(0.0, 100.0)).toFloat()
        } else 0f

        val elapsedSec = if (sessionStartTimeEpochMs > 0L) {
            max(0L, (now - sessionStartTimeEpochMs) / 1000L)
        } else 0L

        val progressMetrics = TrekProgressMetrics(
            traveledDistanceMeters = cumulativeTraveledMeters,
            routeProgressMeters = currentChainageMeters,
            totalRouteDistanceMeters = totalRouteDistanceMeters,
            remainingDistanceMeters = remainingMeters,
            straightLineToDestMeters = straightLineMeters,
            progressPct = progressPct,
            currentSegmentIndex = proj.segmentIndex,
            crossTrackDistanceMeters = proj.crossTrackDistanceMeters,
            isOffRoute = proj.isOffRoute,
            lastAcceptedLat = location.latitude,
            lastAcceptedLon = location.longitude,
            lastAcceptedAltitudeM = location.altitude,
            currentGpsAccuracyM = location.accuracy,
            currentSpeedKmh = speedKmh,
            elapsedDurationSeconds = elapsedSec
        )

        // 3. Reclassify Hazards
        val categorized = reclassifyHazards(
            location.latitude,
            location.longitude,
            currentChainageMeters,
            proj.isOffRoute,
            speedKmh
        )

        // Check generation distance advancement
        val prevGenLat = lastGenUpdateLat
        val prevGenLon = lastGenUpdateLon
        if (prevGenLat != null && prevGenLon != null) {
            val moved = RouteMatchingEngine.haversineDistanceMeters(location.latitude, location.longitude, prevGenLat, prevGenLon)
            if (moved >= 30.0) {
                currentGenerationId++
                lastGenUpdateLat = location.latitude
                lastGenUpdateLon = location.longitude
            }
        } else {
            lastGenUpdateLat = location.latitude
            lastGenUpdateLon = location.longitude
        }

        // 4. Update Engine State Snapshot
        _liveContext.update { current ->
            current.copy(
                progress = progressMetrics,
                hazards = categorized,
                contextGenerationId = currentGenerationId,
                lastEngineUpdateEpochMs = now
            )
        }

        // 5. Tiered Event-Driven Refreshes
        checkAndTriggerTieredRefreshes(location.latitude, location.longitude, now)

        // 6. Deterministic Safety Evaluation & Grounded Voice/AI Alerts
        evaluateSafetyAndAssistant(progressMetrics, categorized, now)
    }

    private fun precomputeHazardRouteIntersections(features: List<UnifiedMapFeature>) {
        if (routePoints.size < 2 || cumDistances.isEmpty()) {
            synchronized(precomputedHazardMap) {
                precomputedHazardMap.clear()
            }
            return
        }

        val map = mutableMapOf<String, PrecomputedHazardRouteInfo>()
        for (feat in features) {
            if (feat.category == MapFeatureCategory.CELL_TOWER ||
                feat.category == MapFeatureCategory.PETROL_PUMP ||
                feat.category == MapFeatureCategory.WATERWAY
            ) {
                continue
            }
            val catType = HazardCategoryType.fromFeature(feat)
            val physicalRadiusMeters = when (catType) {
                HazardCategoryType.LANDSLIDE -> 600.0
                HazardCategoryType.FLOODING, HazardCategoryType.HEAVY_RAIN -> 1500.0
                HazardCategoryType.WILDFIRE -> 2500.0
                else -> 1000.0
            }

            val polygon = feat.geometryPolygon
            if (polygon != null && polygon.size >= 3) {
                val polyEval = RouteMatchingEngine.evaluatePolygonAgainstRoute(
                    polygon, routePoints, cumDistances
                )
                if (polyEval != null) {
                    map[feat.id] = PrecomputedHazardRouteInfo(
                        featureId = feat.id,
                        feature = feat,
                        categoryType = catType,
                        entryChainageMeters = polyEval.entryChainageMeters,
                        exitChainageMeters = polyEval.exitChainageMeters,
                        crossTrackDistanceMeters = polyEval.minCrossTrackMeters,
                        isDirectIntersection = polyEval.isDirectIntersection,
                        isPolygon = true,
                        physicalRadiusMeters = physicalRadiusMeters
                    )
                }
            } else {
                val proj = RouteMatchingEngine.projectPointOntoRoute(
                    feat.latitude, feat.longitude, routePoints, cumDistances
                )
                if (proj != null) {
                    map[feat.id] = PrecomputedHazardRouteInfo(
                        featureId = feat.id,
                        feature = feat,
                        categoryType = catType,
                        entryChainageMeters = proj.chainageMeters,
                        exitChainageMeters = proj.chainageMeters,
                        crossTrackDistanceMeters = proj.crossTrackDistanceMeters,
                        isDirectIntersection = proj.crossTrackDistanceMeters <= 80.0,
                        isPolygon = false,
                        physicalRadiusMeters = physicalRadiusMeters
                    )
                }
            }
        }

        synchronized(precomputedHazardMap) {
            precomputedHazardMap.clear()
            precomputedHazardMap.putAll(map)
        }
    }

    private fun reclassifyHazards(
        userLat: Double,
        userLon: Double,
        chainageMeters: Double,
        isOffRoute: Boolean,
        speedKmh: Double = TYPICAL_HIKING_SPEED_KMH
    ): CategorizedHazards {
        val upcoming = mutableListOf<RouteOrderedHazardItem>()
        val nearby = mutableListOf<RouteOrderedHazardItem>()
        val passed = mutableListOf<RouteOrderedHazardItem>()
        val offRoute = mutableListOf<RouteOrderedHazardItem>()
        val uncertain = mutableListOf<RouteOrderedHazardItem>()

        val features = if (monitoredFeatures.isNotEmpty()) monitoredFeatures else loadAllLocalFeatures()
        val speed = if (speedKmh > 0.5) speedKmh else TYPICAL_HIKING_SPEED_KMH
        val metersPerMinute = (speed * 1000.0) / 60.0

        if (precomputedHazardMap.isEmpty() && features.isNotEmpty() && routePoints.size >= 2) {
            precomputeHazardRouteIntersections(features)
        }

        for (feat in features) {
            if (feat.category == MapFeatureCategory.CELL_TOWER ||
                feat.category == MapFeatureCategory.PETROL_PUMP ||
                feat.category == MapFeatureCategory.WATERWAY
            ) {
                continue
            }
            val pre = synchronized(precomputedHazardMap) { precomputedHazardMap[feat.id] }
            val catType = pre?.categoryType ?: HazardCategoryType.fromFeature(feat)
            val physicalRadiusMeters = pre?.physicalRadiusMeters ?: when (catType) {
                HazardCategoryType.LANDSLIDE -> 600.0
                HazardCategoryType.FLOODING, HazardCategoryType.HEAVY_RAIN -> 1500.0
                HazardCategoryType.WILDFIRE -> 2500.0
                else -> 1000.0
            }

            val distToUserMeters = RouteMatchingEngine.haversineDistanceMeters(
                userLat, userLon, feat.latitude, feat.longitude
            )

            if (pre != null && pre.isPolygon) {
                if (pre.isDirectIntersection) {
                    val entryDist = pre.entryChainageMeters
                    val exitDist = pre.exitChainageMeters

                    val item = RouteOrderedHazardItem(
                        sequenceNumber = 0,
                        sequenceLabel = "",
                        feature = feat,
                        distanceAlongRouteMeters = entryDist,
                        crossTrackDistanceMeters = 0.0,
                        intersectionType = RouteIntersectionType.DIRECT_INTERSECTION,
                        categoryType = catType,
                        severity = feat.details["Severity"] ?: "WARNING",
                        observationTimeText = feat.subtitle
                    )

                    if (exitDist < chainageMeters - 60.0) {
                        passed.add(item.copy(trekStatus = HazardTrekStatus.PASSED))
                    } else if (entryDist > chainageMeters + 30.0) {
                        val aheadM = entryDist - chainageMeters
                        upcoming.add(
                            item.copy(
                                trekStatus = HazardTrekStatus.AHEAD,
                                distanceAheadMeters = aheadM,
                                estimatedMinutesAhead = max(1, (aheadM / metersPerMinute).toInt())
                            )
                        )
                    } else {
                        // Currently traversing or inside polygon
                        nearby.add(item.copy(trekStatus = HazardTrekStatus.NEARBY, distanceAheadMeters = 0.0, distanceCurrentLocationMeters = distToUserMeters, estimatedMinutesAhead = 0))
                    }
                } else if (distToUserMeters <= physicalRadiusMeters) {
                    nearby.add(
                        RouteOrderedHazardItem(
                            sequenceNumber = 0,
                            sequenceLabel = "",
                            feature = feat,
                            distanceAlongRouteMeters = chainageMeters,
                            crossTrackDistanceMeters = pre.crossTrackDistanceMeters,
                            intersectionType = RouteIntersectionType.CORRIDOR_NEARBY,
                            trekStatus = HazardTrekStatus.NEARBY,
                            categoryType = catType,
                            distanceAheadMeters = 0.0,
                            distanceCurrentLocationMeters = distToUserMeters,
                            estimatedMinutesAhead = 0,
                            severity = feat.details["Severity"] ?: "WARNING"
                        )
                    )
                }
            } else if (pre != null) {
                // Point hazard (precomputed)
                val item = RouteOrderedHazardItem(
                    sequenceNumber = 0,
                    sequenceLabel = "",
                    feature = feat,
                    distanceAlongRouteMeters = pre.entryChainageMeters,
                    crossTrackDistanceMeters = pre.crossTrackDistanceMeters,
                    intersectionType = if (pre.isDirectIntersection) RouteIntersectionType.DIRECT_INTERSECTION else RouteIntersectionType.CORRIDOR_NEARBY,
                    categoryType = catType,
                    severity = feat.details["Severity"] ?: "WARNING",
                    observationTimeText = feat.subtitle
                )

                if (distToUserMeters <= physicalRadiusMeters) {
                    nearby.add(item.copy(trekStatus = HazardTrekStatus.NEARBY, distanceAheadMeters = 0.0, distanceCurrentLocationMeters = distToUserMeters, estimatedMinutesAhead = 0))
                } else if (isOffRoute) {
                    uncertain.add(item.copy(trekStatus = HazardTrekStatus.UNCERTAIN))
                } else if (pre.crossTrackDistanceMeters <= 120.0) {
                    val delta = pre.entryChainageMeters - chainageMeters
                    if (delta < -100.0) {
                        passed.add(item.copy(trekStatus = HazardTrekStatus.PASSED))
                    } else if (delta > 30.0) {
                        upcoming.add(
                            item.copy(
                                trekStatus = HazardTrekStatus.AHEAD,
                                distanceAheadMeters = delta,
                                estimatedMinutesAhead = max(1, (delta / metersPerMinute).toInt())
                            )
                        )
                    } else {
                        nearby.add(item.copy(trekStatus = HazardTrekStatus.NEARBY, distanceAheadMeters = 0.0, distanceCurrentLocationMeters = distToUserMeters, estimatedMinutesAhead = 0))
                    }
                } else if (pre.crossTrackDistanceMeters <= 2000.0) {
                    offRoute.add(item.copy(trekStatus = HazardTrekStatus.UNCERTAIN, intersectionType = RouteIntersectionType.CORRIDOR_NEARBY))
                }
            } else {
                // Fallback for hazards without precomputed route projection
                if (distToUserMeters <= physicalRadiusMeters) {
                    nearby.add(
                        RouteOrderedHazardItem(
                            sequenceNumber = 0,
                            sequenceLabel = "",
                            feature = feat,
                            distanceAlongRouteMeters = chainageMeters,
                            crossTrackDistanceMeters = distToUserMeters,
                            intersectionType = RouteIntersectionType.CORRIDOR_NEARBY,
                            trekStatus = HazardTrekStatus.NEARBY,
                            categoryType = catType,
                            distanceAheadMeters = 0.0,
                            distanceCurrentLocationMeters = distToUserMeters,
                            estimatedMinutesAhead = 0,
                            severity = feat.details["Severity"] ?: "WARNING"
                        )
                    )
                }
            }
        }

        // Sort upcoming hazards strictly ascending by distance ahead
        upcoming.sortBy { it.distanceAheadMeters ?: it.distanceAlongRouteMeters }
        val numberedUpcoming = upcoming.mapIndexed { idx, h ->
            val num = idx + 1
            h.copy(
                sequenceNumber = num,
                sequenceLabel = String.format(java.util.Locale.US, "#%02d", num)
            )
        }

        return CategorizedHazards(
            upcomingHazards = numberedUpcoming,
            nearbyHazards = nearby,
            passedHazards = passed,
            offRouteHazards = offRoute,
            uncertainHazards = uncertain
        )
    }

    private fun checkAndTriggerTieredRefreshes(lat: Double, lon: Double, now: Long) {
        // 1. Weather Tiered Refresh (> 1.5 km movement or > 30 minutes)
        val lastWeatherLoc = lastWeatherQueryLocation
        val weatherDist = if (lastWeatherLoc != null) {
            RouteMatchingEngine.haversineDistanceMeters(lat, lon, lastWeatherLoc.first, lastWeatherLoc.second)
        } else Double.MAX_VALUE

        val weatherElapsed = now - lastWeatherQueryEpochMs

        if ((weatherDist >= WEATHER_REFRESH_DISTANCE_METERS || weatherElapsed >= WEATHER_REFRESH_INTERVAL_MS) && !isWeatherRefreshing) {
            isWeatherRefreshing = true
            lastWeatherQueryLocation = lat to lon
            lastWeatherQueryEpochMs = now

            engineScope.launch(Dispatchers.IO) {
                try {
                    val result = pointContextRepository.getPointContext(lat, lon, radiusKm = 5.0)
                    result.fold(
                        onSuccess = { dto ->
                            val w = dto.weather
                            _liveContext.update { cur ->
                                cur.copy(
                                    weather = cur.weather.copy(
                                        currentLatitude = lat,
                                        currentLongitude = lon,
                                        currentTemperatureC = w?.temperature_c,
                                        weatherDescription = w?.weather_description,
                                        precipitationProbabilityPct = w?.precipitation_probability_pct?.toInt(),
                                        windSpeedKmh = w?.wind_speed_kmh,
                                        thunderstormProbabilityPct = if (dto.active_warnings.any { it.hazard_category.contains("THUNDER", ignoreCase = true) || it.title.contains("THUNDER", ignoreCase = true) }) 80 else null,
                                        retrievedAtEpochMs = now,
                                        isStale = dto.is_stale,
                                        isCached = dto.is_cached
                                    )
                                )
                            }
                        },
                        onFailure = {
                            Log.w(TAG, "Offline weather fallback retained.")
                        }
                    )
                } finally {
                    isWeatherRefreshing = false
                }
            }
        }

        // 2. Facilities Tiered Refresh (> 1.0 km movement)
        val lastFacLoc = lastFacilitiesQueryLocation
        val facDist = if (lastFacLoc != null) {
            RouteMatchingEngine.haversineDistanceMeters(lat, lon, lastFacLoc.first, lastFacLoc.second)
        } else Double.MAX_VALUE

        if (facDist >= FACILITIES_REFRESH_DISTANCE_METERS && !isFacilitiesRefreshing) {
            isFacilitiesRefreshing = true
            lastFacilitiesQueryLocation = lat to lon

            engineScope.launch(Dispatchers.IO) {
                try {
                    val result = pointContextRepository.getPointContext(lat, lon, radiusKm = 10.0)
                    result.onSuccess { dto ->
                        if (dto.nearby_facilities.isNotEmpty()) {
                            _liveContext.update { it.copy(nearbyFacilities = dto.nearby_facilities) }
                        }
                    }
                } finally {
                    isFacilitiesRefreshing = false
                }
            }
        }
    }

    private fun evaluateSafetyAndAssistant(
        progress: TrekProgressMetrics,
        categorized: CategorizedHazards,
        now: Long
    ) {
        val safetyEvent = deterministicDecisionEngine.evaluate(
            upcomingHazards = categorized.upcomingHazards,
            nearbyHazards = categorized.nearbyHazards,
            progress = progress,
            weather = _liveContext.value.weather,
            isOffline = _liveContext.value.networkStatus.isOffline,
            hasVerifiedAlternatives = false,
            now = now
        )

        if (safetyEvent != null) {
            if (safetyEvent.requiresImmediateAlarm) {
                val alert = ActiveHazardAlert(
                    hazardId = safetyEvent.hazardId ?: "hazard_alert",
                    title = safetyEvent.hazardTitle,
                    severity = safetyEvent.severity,
                    distanceKm = round(((safetyEvent.distanceAheadMeters ?: safetyEvent.distanceCurrentMeters) / 1000.0) * 10.0) / 10.0,
                    instructions = safetyEvent.deterministicActionText,
                    issuingAuthority = "USDMA / Live Safety Engine",
                    triggeredAtEpochMs = now
                )
                _liveContext.update { it.copy(activeCriticalAlert = alert, activeSafetyEvent = safetyEvent) }
                safetyAlertPlayer.playSafetyAlert()
            } else {
                _liveContext.update { it.copy(activeSafetyEvent = safetyEvent) }
            }

            var deterministicSpoken = false
            if (safetyEvent.requiresVoiceAlert && _liveContext.value.isVoiceGuidanceEnabled) {
                if (spokenEventIds.add(safetyEvent.eventId)) {
                    trekVoiceAssistant.speakSafetyAlert(
                        text = safetyEvent.deterministicSpokenText,
                        isCritical = safetyEvent.requiresImmediateAlarm,
                        now = now
                    )
                    deterministicSpoken = true
                }
            }

            // Asynchronously dispatch for grounded AI explanation
            val genId = currentGenerationId
            engineScope.launch(Dispatchers.IO) {
                try {
                    val reqDto = buildAssistantRequestDto(progress, categorized, safetyEvent, genId)
                    val aiResponse = assistantRepository.getSafetyGuidance(reqDto, safetyEvent)
                    if (_liveContext.value.contextGenerationId == genId) {
                        _liveContext.update { it.copy(assistantGuidance = aiResponse) }
                        if (!deterministicSpoken && aiResponse.is_ai_generated && aiResponse.spoken_message.isNotBlank() && _liveContext.value.isVoiceGuidanceEnabled) {
                            if (spokenEventIds.add(safetyEvent.eventId)) {
                                trekVoiceAssistant.speakSafetyAlert(
                                    text = aiResponse.spoken_message,
                                    isCritical = safetyEvent.requiresImmediateAlarm,
                                    now = System.currentTimeMillis()
                                )
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error fetching AI guidance: ${t.message}")
                }
            }
        }
    }

    private fun buildAssistantRequestDto(
        progress: TrekProgressMetrics,
        categorized: CategorizedHazards,
        safetyEvent: SafetyEvent?,
        genId: Int
    ): LiveSafetyAssistantRequestDto {
        val w = _liveContext.value.weather
        val facs = _liveContext.value.nearbyFacilities
        val net = _liveContext.value.networkStatus

        return LiveSafetyAssistantRequestDto(
            trip_id = activeTripId ?: "trip_active",
            route_id = activeRouteId ?: "route_active",
            context_generation_id = genId,
            trigger_event = safetyEvent?.let { ev ->
                AssistantSafetyEventDto(
                    event_id = ev.eventId,
                    event_type = ev.eventType.name,
                    hazard_id = ev.hazardId,
                    hazard_title = ev.hazardTitle,
                    severity = ev.severity,
                    distance_ahead_m = ev.distanceAheadMeters,
                    distance_current_m = ev.distanceCurrentMeters,
                    is_direct_route_obstruction = ev.isDirectRouteObstruction,
                    has_verified_alternative = ev.hasVerifiedAlternative,
                    deterministic_spoken_text = ev.deterministicSpokenText,
                    deterministic_action_text = ev.deterministicActionText
                )
            },
            current_location = AssistantLocationDto(
                latitude = progress.lastAcceptedLat ?: 0.0,
                longitude = progress.lastAcceptedLon ?: 0.0,
                altitude_meters = progress.lastAcceptedAltitudeM,
                accuracy_meters = progress.currentGpsAccuracyM,
                speed_kmh = progress.currentSpeedKmh
            ),
            route_progress = AssistantRouteProgressDto(
                traveled_km = progress.traveledDistanceMeters / 1000.0,
                chainage_km = progress.routeProgressMeters / 1000.0,
                remaining_km = progress.remainingDistanceMeters / 1000.0,
                progress_pct = progress.progressPct.toDouble(),
                is_off_route = progress.isOffRoute,
                cross_track_m = progress.crossTrackDistanceMeters
            ),
            upcoming_hazards = categorized.upcomingHazards.map {
                val tag = if (it.feature.category == MapFeatureCategory.COMMUNITY_HAZARD) "[Community Report]" else "[Official Advisory]"
                AssistantHazardContextDto(
                    id = it.feature.id,
                    title = "$tag ${it.feature.title}",
                    category = it.feature.category.name,
                    severity = it.severity,
                    distance_ahead_m = it.distanceAheadMeters,
                    sequence_number = it.sequenceNumber,
                    is_direct_intersection = it.intersectionType == RouteIntersectionType.DIRECT_INTERSECTION
                )
            },
            nearby_hazards = categorized.nearbyHazards.map {
                val tag = if (it.feature.category == MapFeatureCategory.COMMUNITY_HAZARD) "[Community Report]" else "[Official Advisory]"
                AssistantHazardContextDto(
                    id = it.feature.id,
                    title = "$tag ${it.feature.title}",
                    category = it.feature.category.name,
                    severity = it.severity,
                    distance_current_m = it.distanceCurrentLocationMeters
                )
            },
            current_weather = AssistantWeatherContextDto(
                temperature_c = w.currentTemperatureC,
                weather_description = w.weatherDescription,
                precipitation_probability_pct = w.precipitationProbabilityPct,
                wind_speed_kmh = w.windSpeedKmh,
                thunderstorm_probability_pct = w.thunderstormProbabilityPct,
                is_stale = w.isStale
            ),
            nearby_facilities = facs.map {
                AssistantFacilityContextDto(
                    id = it.id,
                    name = it.name,
                    category = it.category,
                    distance_km = it.distance_km,
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            },
            verified_alternatives = emptyList(),
            connectivity_status = if (net.isOffline) "OFFLINE" else net.estimatedCoverage
        )
    }

    fun askAssistant(query: String, onResult: (LiveSafetyAssistantResponseDto) -> Unit = {}) {
        val genId = currentGenerationId
        val progress = _liveContext.value.progress
        val hazards = _liveContext.value.hazards
        val weather = _liveContext.value.weather
        val facilities = _liveContext.value.nearbyFacilities
        val net = _liveContext.value.networkStatus

        engineScope.launch(Dispatchers.IO) {
            val req = AssistantInteractiveQueryRequestDto(
                trip_id = activeTripId ?: "active_trip",
                route_id = activeRouteId ?: "active_route",
                context_generation_id = genId,
                query = query,
                current_location = AssistantLocationDto(
                    latitude = progress.lastAcceptedLat ?: 0.0,
                    longitude = progress.lastAcceptedLon ?: 0.0,
                    altitude_meters = progress.lastAcceptedAltitudeM,
                    accuracy_meters = progress.currentGpsAccuracyM,
                    speed_kmh = progress.currentSpeedKmh
                ),
                route_progress = AssistantRouteProgressDto(
                    traveled_km = progress.traveledDistanceMeters / 1000.0,
                    chainage_km = progress.routeProgressMeters / 1000.0,
                    remaining_km = progress.remainingDistanceMeters / 1000.0,
                    progress_pct = progress.progressPct.toDouble(),
                    is_off_route = progress.isOffRoute,
                    cross_track_m = progress.crossTrackDistanceMeters
                ),
                upcoming_hazards = hazards.upcomingHazards.map {
                    val tag = if (it.feature.category == MapFeatureCategory.COMMUNITY_HAZARD) "[Community Report]" else "[Official Advisory]"
                    AssistantHazardContextDto(
                        id = it.feature.id,
                        title = "$tag ${it.feature.title}",
                        category = it.feature.category.name,
                        severity = it.severity,
                        distance_ahead_m = it.distanceAheadMeters,
                        sequence_number = it.sequenceNumber,
                        is_direct_intersection = it.intersectionType == RouteIntersectionType.DIRECT_INTERSECTION
                    )
                },
                nearby_hazards = hazards.nearbyHazards.map {
                    val tag = if (it.feature.category == MapFeatureCategory.COMMUNITY_HAZARD) "[Community Report]" else "[Official Advisory]"
                    AssistantHazardContextDto(
                        id = it.feature.id,
                        title = "$tag ${it.feature.title}",
                        category = it.feature.category.name,
                        severity = it.severity,
                        distance_current_m = it.distanceCurrentLocationMeters
                    )
                },
                current_weather = AssistantWeatherContextDto(
                    temperature_c = weather.currentTemperatureC,
                    weather_description = weather.weatherDescription,
                    precipitation_probability_pct = weather.precipitationProbabilityPct,
                    wind_speed_kmh = weather.windSpeedKmh,
                    thunderstorm_probability_pct = weather.thunderstormProbabilityPct,
                    is_stale = weather.isStale
                ),
                nearby_facilities = facilities.map {
                    AssistantFacilityContextDto(
                        id = it.id,
                        name = it.name,
                        category = it.category,
                        distance_km = it.distance_km,
                        latitude = it.latitude,
                        longitude = it.longitude
                    )
                },
                connectivity_status = if (net.isOffline) "OFFLINE" else net.estimatedCoverage
            )

            val res = assistantRepository.askAssistant(req)
            withContext(Dispatchers.Main) {
                _liveContext.update { it.copy(assistantGuidance = res) }
                if (_liveContext.value.isVoiceGuidanceEnabled && res.spoken_message.isNotBlank()) {
                    trekVoiceAssistant.speakSafetyAlert(res.spoken_message, isCritical = false)
                }
                onResult(res)
            }
        }
    }

    fun setVoiceGuidanceEnabled(enabled: Boolean) {
        _liveContext.update { it.copy(isVoiceGuidanceEnabled = enabled) }
        trekVoiceAssistant.setVoiceEnabled(enabled)
    }

    fun replayAssistantVoice() {
        val guidance = _liveContext.value.assistantGuidance
        val spoken = guidance?.spoken_message?.ifBlank { null }
            ?: _liveContext.value.activeSafetyEvent?.deterministicSpokenText
        if (!spoken.isNullOrBlank()) {
            trekVoiceAssistant.speakSafetyAlert(spoken, isCritical = true)
        }
    }

    fun dismissCriticalAlert() {
        safetyAlertPlayer.stopSafetyAlert()
        _liveContext.update { it.copy(activeCriticalAlert = null, activeSafetyEvent = null) }
    }

    private fun loadAllLocalFeatures(): List<UnifiedMapFeature> {
        synchronized(baselineLock) {
            if (isBaselineFeaturesLoaded && cachedLocalBaselineFeatures.isNotEmpty()) {
                return cachedLocalBaselineFeatures.toList()
            }
        }

        val list = mutableListOf<UnifiedMapFeature>()
        try {
            // Load baseline hazard zones from asset
            context.assets.open("uttarakhand_hazard_zones.geojson").use { stream ->
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                list.addAll(parseFeaturesFromGeoJson(text, MapFeatureCategory.HAZARD_ZONE))
            }
        } catch (_: Exception) {}

        try {
            // Load historical landslides from asset
            context.assets.open("uttarakhand_historical_landslides.geojson").use { stream ->
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                list.addAll(parseFeaturesFromGeoJson(text, MapFeatureCategory.HAZARD_ZONE))
            }
        } catch (_: Exception) {}

        synchronized(baselineLock) {
            cachedLocalBaselineFeatures.clear()
            cachedLocalBaselineFeatures.addAll(list)
            isBaselineFeaturesLoaded = true
        }

        return list
    }

    private fun parseFeaturesFromGeoJson(geoJson: String, category: MapFeatureCategory): List<UnifiedMapFeature> {
        val results = mutableListOf<UnifiedMapFeature>()
        try {
            val root = org.json.JSONObject(geoJson)
            val features = root.optJSONArray("features") ?: return emptyList()
            for (i in 0 until features.length()) {
                val f = features.optJSONObject(i) ?: continue
                val id = f.optString("id", "feat_$i")
                val props = f.optJSONObject("properties") ?: org.json.JSONObject()
                val geom = f.optJSONObject("geometry") ?: continue
                val geomType = geom.optString("type")

                var lat = props.optDouble("center_latitude", Double.NaN)
                var lon = props.optDouble("center_longitude", Double.NaN)
                var polyCoords: List<Pair<Double, Double>>? = null

                if (geomType.equals("Point", ignoreCase = true)) {
                    val coords = geom.optJSONArray("coordinates")
                    if (coords != null && coords.length() >= 2) {
                        lon = coords.optDouble(0)
                        lat = coords.optDouble(1)
                    }
                } else if (geomType.equals("Polygon", ignoreCase = true)) {
                    val rings = geom.optJSONArray("coordinates")
                    if (rings != null && rings.length() > 0) {
                        val exterior = rings.optJSONArray(0)
                        if (exterior != null) {
                            val pts = mutableListOf<Pair<Double, Double>>()
                            for (p in 0 until exterior.length()) {
                                val pt = exterior.optJSONArray(p) ?: continue
                                pts.add(pt.optDouble(1) to pt.optDouble(0))
                            }
                            polyCoords = pts
                            if (lat.isNaN() || lon.isNaN()) {
                                lat = pts.map { it.first }.average()
                                lon = pts.map { it.second }.average()
                            }
                        }
                    }
                }

                if (!lat.isNaN() && !lon.isNaN()) {
                    results.add(
                        UnifiedMapFeature(
                            id = id,
                            title = props.optString("title", props.optString("name", "Hazard Zone")),
                            subtitle = props.optString("description", props.optString("instructions", "")),
                            category = category,
                            latitude = lat,
                            longitude = lon,
                            geometryPolygon = polyCoords,
                            details = mapOf(
                                "Severity" to props.optString("severity", "WARNING"),
                                "hazard_category" to props.optString("hazard_category", "disaster"),
                                "Instructions" to props.optString("instructions", "")
                            )
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private suspend fun flushPointBuffer() {
        val toFlush = synchronized(pointBuffer) {
            if (pointBuffer.isEmpty()) return@synchronized emptyList<TrekTrackPointEntity>()
            val copy = ArrayList(pointBuffer)
            pointBuffer.clear()
            copy
        }
        if (toFlush.isNotEmpty()) {
            try {
                trekTrackPointDao.insertAll(toFlush)
                lastBufferFlushMs = System.currentTimeMillis()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed flushing points buffer: ${t.message}")
            }
        }
    }

    private fun parseCoordsFromGeoJson(geoJson: String): List<Pair<Double, Double>> {
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
        } catch (_: Exception) {}
        return result
    }
}
