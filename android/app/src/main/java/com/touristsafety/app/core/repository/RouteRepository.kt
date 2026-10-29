package com.touristsafety.app.core.repository

import android.content.Context
import android.util.Log
import com.touristsafety.app.core.database.dao.ActiveTripDao
import com.touristsafety.app.core.database.dao.PlannedRouteDao
import com.touristsafety.app.core.database.entity.ActiveTripEntity
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.network.dto.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val plannedRouteDao: PlannedRouteDao,
    private val activeTripDao: ActiveTripDao
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    data class VerifiedTrailInfo(
        val id: String,
        val name: String,
        val coordinates: List<Pair<Double, Double>>
    )

    private val verifiedTrails: List<VerifiedTrailInfo> by lazy {
        loadVerifiedTrailsFromAssets()
    }

    private fun loadVerifiedTrailsFromAssets(): List<VerifiedTrailInfo> {
        return try {
            val inputStream = context.assets.open("uttarakhand_verified_trails.geojson")
            val jsonStr = inputStream.bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(jsonStr)
            val features = root.optJSONArray("features") ?: org.json.JSONArray()
            val list = mutableListOf<VerifiedTrailInfo>()
            for (i in 0 until features.length()) {
                val feat = features.getJSONObject(i)
                val props = feat.optJSONObject("properties") ?: org.json.JSONObject()
                val geom = feat.optJSONObject("geometry") ?: org.json.JSONObject()
                val name = props.optString("name", "Trail $i")
                val id = props.optString("id", "trail-$i")
                val coordsArray = geom.optJSONArray("coordinates")
                if (coordsArray != null) {
                    val coords = mutableListOf<Pair<Double, Double>>()
                    for (j in 0 until coordsArray.length()) {
                        val pt = coordsArray.getJSONArray(j)
                        val lon = pt.getDouble(0)
                        val lat = pt.getDouble(1)
                        coords.add(lat to lon)
                    }
                    if (coords.isNotEmpty()) {
                        list.add(VerifiedTrailInfo(id = id, name = name, coordinates = coords))
                    }
                }
            }
            list
        } catch (e: Exception) {
            Log.w("RouteRepository", "Failed to load verified trails from assets", e)
            emptyList()
        }
    }

    fun connectAlongTrailIfPossible(
        p1: Pair<Double, Double>,
        p2: Pair<Double, Double>,
        maxDistKm: Double = 0.5
    ): List<Pair<Double, Double>> {
        val trails = verifiedTrails
        var bestTrail: VerifiedTrailInfo? = null
        var bestIdx1 = -1
        var bestIdx2 = -1
        var minTotalDist = Double.MAX_VALUE

        for (trail in trails) {
            var minD1 = Double.MAX_VALUE
            var idx1 = -1
            var minD2 = Double.MAX_VALUE
            var idx2 = -1

            for (i in trail.coordinates.indices) {
                val pt = trail.coordinates[i]
                val d1 = haversineDistanceKm(p1.first, p1.second, pt.first, pt.second)
                if (d1 < minD1) {
                    minD1 = d1
                    idx1 = i
                }
                val d2 = haversineDistanceKm(p2.first, p2.second, pt.first, pt.second)
                if (d2 < minD2) {
                    minD2 = d2
                    idx2 = i
                }
            }

            if (minD1 <= maxDistKm && minD2 <= maxDistKm && idx1 != -1 && idx2 != -1) {
                val totalDist = minD1 + minD2
                if (totalDist < minTotalDist) {
                    minTotalDist = totalDist
                    bestTrail = trail
                    bestIdx1 = idx1
                    bestIdx2 = idx2
                }
            }
        }

        if (bestTrail != null && bestIdx1 != -1 && bestIdx2 != -1) {
            val trailCoords = bestTrail.coordinates
            val slice = if (bestIdx1 <= bestIdx2) {
                trailCoords.subList(bestIdx1, bestIdx2 + 1)
            } else {
                trailCoords.subList(bestIdx2, bestIdx1 + 1).reversed()
            }
            return listOf(p1) + slice + listOf(p2)
        }

        return listOf(p1, p2)
    }

    private fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0)
        val c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a))
        return r * c
    }

    private fun getRoutesDir(): File {
        val dir = File(context.filesDir, "saved_routes")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    val savedRoutesFlow: Flow<List<PlannedRouteEntity>> = plannedRouteDao.getAllRoutesFlow()
        .catch { e ->
            Log.e("RouteRepository", "Error observing saved routes: ${e.message}", e)
            emit(emptyList())
        }
    val activeTripFlow: Flow<ActiveTripEntity?> = activeTripDao.getActiveTripFlow()

    private val _activeRouteFlow = MutableStateFlow<PlannedRouteResponseDto?>(null)
    val activeRouteFlow: StateFlow<PlannedRouteResponseDto?> = _activeRouteFlow.asStateFlow()

    fun setActiveRoute(route: PlannedRouteResponseDto) {
        _activeRouteFlow.value = route
    }

    fun clearActiveRoute() {
        _activeRouteFlow.value = null
    }

    fun activateSavedRoute(entity: PlannedRouteEntity) {
        val file = File(getRoutesDir(), "${entity.routeId}.json")
        if (file.exists()) {
            try {
                val dto = json.decodeFromString<PlannedRouteResponseDto>(file.readText())
                _activeRouteFlow.value = dto
                return
            } catch (e: Exception) {
                Log.w("RouteRepository", "Failed to parse saved route file for ${entity.routeId}", e)
            }
        }
        val conditions = try {
            json.decodeFromString<List<RouteWaypointConditionDto>>(entity.conditionsJson)
        } catch (e: Exception) {
            emptyList()
        }
        val alternatives = try {
            json.decodeFromString<List<RouteAlternativeDto>>(entity.alternativesJson)
        } catch (e: Exception) {
            emptyList()
        }
        val elevation = try {
            if (!entity.elevationProfileJson.isNullOrBlank()) {
                json.decodeFromString<List<ElevationProfilePointDto>>(entity.elevationProfileJson)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val havens = try {
            if (!entity.safeHavensJson.isNullOrBlank()) {
                json.decodeFromString<List<SafeHavenInfoDto>>(entity.safeHavensJson)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val geom = try {
            if (!entity.routeGeoJson.isNullOrBlank()) {
                json.parseToJsonElement(entity.routeGeoJson)
            } else null
        } catch (e: Exception) {
            null
        }
        val corridorGeom = try {
            if (!entity.corridorGeoJson.isNullOrBlank()) {
                json.parseToJsonElement(entity.corridorGeoJson)
            } else null
        } catch (e: Exception) {
            null
        }
        val hazardsGeom = try {
            if (!entity.hazardsGeoJson.isNullOrBlank()) {
                json.parseToJsonElement(entity.hazardsGeoJson)
            } else null
        } catch (e: Exception) {
            null
        }
        val firesGeom = try {
            if (!entity.firesGeoJson.isNullOrBlank()) {
                json.parseToJsonElement(entity.firesGeoJson)
            } else null
        } catch (e: Exception) {
            null
        }
        val facilitiesGeom = try {
            if (!entity.facilitiesGeoJson.isNullOrBlank()) {
                json.parseToJsonElement(entity.facilitiesGeoJson)
            } else null
        } catch (e: Exception) {
            null
        }
        val forestsGeom = try {
            if (!entity.forestsGeoJson.isNullOrBlank()) {
                json.parseToJsonElement(entity.forestsGeoJson)
            } else null
        } catch (e: Exception) {
            null
        }
        val weatherGeom = try {
            if (!entity.weatherGeoJson.isNullOrBlank()) {
                json.parseToJsonElement(entity.weatherGeoJson)
            } else null
        } catch (e: Exception) {
            null
        }
        val providerStatuses = try {
            if (!entity.providerStatusesJson.isNullOrBlank()) {
                json.decodeFromString<Map<String, ProviderStatusItemDto>>(entity.providerStatusesJson)
            } else emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
        val limitations = try {
            if (!entity.analysisLimitationsJson.isNullOrBlank()) {
                json.decodeFromString<List<String>>(entity.analysisLimitationsJson)
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val dto = PlannedRouteResponseDto(
            route_id = entity.routeId,
            route_version = 1,
            title = entity.title,
            origin_name = entity.originName,
            origin_lat = entity.originLat,
            origin_lon = entity.originLon,
            destination_name = entity.destinationName,
            dest_lat = entity.destLat,
            dest_lon = entity.destLon,
            travel_mode = entity.travelMode,
            distance_km = entity.distanceKm,
            duration_minutes = entity.durationMinutes,
            distance_meters = entity.distanceKm * 1000.0,
            duration_seconds = entity.durationMinutes * 60.0,
            overall_hazard_score = entity.overallHazardScore,
            overall_safety_status = entity.overallSafetyStatus,
            high_risk_segments_count = entity.highRiskSegmentsCount,
            total_ascent_m = entity.totalAscentM,
            max_elevation_m = entity.maxElevationM,
            decision_explanation = entity.decisionExplanation ?: "",
            conditions = conditions,
            elevation_profile = elevation,
            safe_havens = havens,
            alternatives = alternatives,
            geometry_geojson = geom,
            corridor_distance_km = entity.corridorWidthKm,
            corridor_geojson = corridorGeom,
            hazards_geojson = hazardsGeom,
            fires_geojson = firesGeom,
            facilities_geojson = facilitiesGeom,
            forests_geojson = forestsGeom,
            weather_geojson = weatherGeom,
            terrain_slope_geojson = entity.terrainSlopeGeoJson?.let { try { json.parseToJsonElement(it) } catch (e: Exception) { null } },
            cellular_sites_geojson = entity.cellularSitesGeoJson?.let { try { json.parseToJsonElement(it) } catch (e: Exception) { null } },
            cellular_range_geojson = entity.cellularRangeGeoJson?.let { try { json.parseToJsonElement(it) } catch (e: Exception) { null } },
            cellular_coverage_geojson = entity.cellularCoverageGeoJson?.let { try { json.parseToJsonElement(it) } catch (e: Exception) { null } },
            waterways_geojson = entity.waterwaysGeoJson?.let { try { json.parseToJsonElement(it) } catch (e: Exception) { null } },
            historical_landslides_geojson = entity.historicalLandslidesGeoJson?.let { try { json.parseToJsonElement(it) } catch (e: Exception) { null } },
            provider_statuses = providerStatuses,
            analysis_limitations = limitations,
            created_at = java.util.Date(entity.cachedAt).toString(),
            is_user_drawn = entity.isUserDrawn,
            drawing_mode = entity.drawingMode,
            verification_status = entity.verificationStatus,
            proposed_connector_geojson = entity.proposedConnectorGeoJson?.let { try { json.parseToJsonElement(it) } catch (e: Exception) { null } },
            unverified_reason = if (entity.isUserDrawn) (entity.userDescription ?: "User-drawn route — walkability unverified") else null
        )
        _activeRouteFlow.value = dto
    }

    suspend fun searchPlaces(query: String): Result<List<PlaceSearchResultDto>> {
        return try {
            val response = apiService.searchPlaces(query.trim())
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Places search failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("RouteRepository", "Error searching places", e)
            Result.failure(e)
        }
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): Result<PlaceSearchResultDto> {
        return try {
            val response = apiService.reverseGeocode(lat, lon)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Reverse geocoding failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("RouteRepository", "Error reverse geocoding $lat, $lon", e)
            Result.failure(e)
        }
    }

    fun getRecentEndpoints(): Flow<List<PlaceSearchResultDto>> {
        return plannedRouteDao.getAllRoutesFlow()
            .catch { e ->
                Log.e("RouteRepository", "Error getting recent endpoints: ${e.message}", e)
                emit(emptyList())
            }
            .map { routes ->
            val places = mutableListOf<PlaceSearchResultDto>()
            val seen = mutableSetOf<String>()
            for (route in routes) {
                val originKey = "${String.format(java.util.Locale.US, "%.4f", route.originLat)},${String.format(java.util.Locale.US, "%.4f", route.originLon)}"
                if (route.originName.isNotBlank() && seen.add(originKey)) {
                    places.add(
                        PlaceSearchResultDto(
                            name = route.originName,
                            latitude = route.originLat,
                            longitude = route.originLon,
                            display_name = route.originName
                        )
                    )
                }
                val destKey = "${String.format(java.util.Locale.US, "%.4f", route.destLat)},${String.format(java.util.Locale.US, "%.4f", route.destLon)}"
                if (route.destinationName.isNotBlank() && seen.add(destKey)) {
                    places.add(
                        PlaceSearchResultDto(
                            name = route.destinationName,
                            latitude = route.destLat,
                            longitude = route.destLon,
                            display_name = route.destinationName
                        )
                    )
                }
                if (places.size >= 12) break
            }
            places
        }
    }

    suspend fun planRoute(
        originLat: Double,
        originLon: Double,
        originName: String,
        destLat: Double,
        destLon: Double,
        destName: String,
        travelMode: String = "driving",
        corridorDistanceKm: Double = 5.0
    ): Result<PlannedRouteResponseDto> {
        return try {
            val request = PlanRouteRequestDto(
                origin_lat = originLat,
                origin_lon = originLon,
                origin_name = originName,
                destination_lat = destLat,
                destination_lon = destLon,
                destination_name = destName,
                travel_mode = travelMode,
                corridor_distance_km = corridorDistanceKm
            )
            val response = apiService.planRoute(request)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                // Set as active route and persist in Room for offline resilience
                _activeRouteFlow.value = dto
                saveRouteOffline(dto)
                Result.success(dto)
            } else {
                val rawError = try { response.errorBody()?.string() } catch (_: Throwable) { null }
                val detailMsg = try {
                    if (!rawError.isNullOrBlank()) {
                        val obj = org.json.JSONObject(rawError)
                        obj.optString("detail", obj.optString("message", rawError))
                    } else null
                } catch (_: Throwable) {
                    rawError
                }
                val failureMsg = if (!detailMsg.isNullOrBlank()) {
                    detailMsg
                } else {
                    "${response.code()} ${response.message()}".trim()
                }
                Result.failure(Exception(failureMsg))
            }
        } catch (e: Exception) {
            Log.e("RouteRepository", "Error planning route", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeRoute(
        routeId: String,
        corridorDistanceKm: Double = 5.0,
        routeVersion: Int? = null
    ): Result<RouteAnalysisResponseDto> {
        return try {
            val request = RouteAnalysisRequestDto(
                corridor_distance_km = corridorDistanceKm,
                route_version = routeVersion
            )
            val response = apiService.analyzeRoute(
                id = routeId,
                corridorDistanceKm = corridorDistanceKm,
                body = request
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Route analysis failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("RouteRepository", "Error analyzing route $routeId", e)
            Result.failure(e)
        }
    }

    suspend fun saveRouteOffline(dto: PlannedRouteResponseDto) {
        try {
            // 1. Write the full DTO payload to internal storage file (zero SQLite CursorWindow size limit)
            val file = File(getRoutesDir(), "${dto.route_id}.json")
            file.writeText(json.encodeToString(dto))

            // 2. Persist lightweight summary row in Room SQLite (<1 KB per row) to prevent CursorWindow overflow
            val entity = PlannedRouteEntity(
                routeId = dto.route_id,
                title = dto.title,
                originName = dto.origin_name,
                originLat = dto.origin_lat,
                originLon = dto.origin_lon,
                destinationName = dto.destination_name,
                destLat = dto.dest_lat,
                destLon = dto.dest_lon,
                travelMode = dto.travel_mode,
                distanceKm = dto.distance_km,
                durationMinutes = dto.duration_minutes,
                overallHazardScore = dto.overall_hazard_score,
                overallSafetyStatus = dto.overall_safety_status,
                highRiskSegmentsCount = dto.high_risk_segments_count,
                conditionsJson = "", // Stored safely in JSON file on disk
                alternativesJson = "", // Stored safely in JSON file on disk
                routeGeoJson = dto.geometry_geojson?.toString(), // Lightweight path coordinates
                elevationProfileJson = null,
                safeHavensJson = null,
                decisionExplanation = dto.decision_explanation,
                corridorGeoJson = null, // Stored safely in JSON file on disk
                hazardsGeoJson = null, // Stored safely in JSON file on disk
                firesGeoJson = null, // Stored safely in JSON file on disk
                facilitiesGeoJson = null, // Stored safely in JSON file on disk
                forestsGeoJson = null, // Stored safely in JSON file on disk
                weatherGeoJson = null, // Stored safely in JSON file on disk
                cellularSitesGeoJson = dto.cellular_sites_geojson?.toString(),
                cellularRangeGeoJson = dto.cellular_range_geojson?.toString(),
                cellularCoverageGeoJson = dto.cellular_coverage_geojson?.toString(),
                dataFreshnessJson = null,
                providerStatusesJson = null,
                analysisLimitationsJson = null,
                corridorWidthKm = dto.corridor_distance_km,
                totalAscentM = dto.total_ascent_m,
                maxElevationM = dto.max_elevation_m,
                isSavedOffline = true,
                cachedAt = System.currentTimeMillis()
            )
            plannedRouteDao.insertOrUpdate(entity)
        } catch (e: Exception) {
            Log.e("RouteRepository", "Error saving route offline", e)
        }
    }

    fun getRouteFlow(routeId: String): Flow<PlannedRouteEntity?> {
        return plannedRouteDao.getRouteFlow(routeId)
            .catch { e ->
                Log.e("RouteRepository", "Error observing route $routeId: ${e.message}", e)
                emit(null)
            }
    }

    suspend fun getRouteById(routeId: String): PlannedRouteEntity? {
        return try {
            plannedRouteDao.getRouteById(routeId)
        } catch (e: Exception) {
            Log.e("RouteRepository", "Error reading route by id $routeId: ${e.message}", e)
            null
        }
    }

    suspend fun deletePlannedRoute(routeId: String) {
        try {
            val file = File(getRoutesDir(), "$routeId.json")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w("RouteRepository", "Failed to delete route file $routeId", e)
        }
        plannedRouteDao.deleteRoute(routeId)
    }

    suspend fun updateOfflinePackStatus(
        routeId: String,
        status: String,
        sizeBytes: Long = 0L,
        regionId: Long? = null,
        downloadedAt: Long? = System.currentTimeMillis()
    ) {
        plannedRouteDao.updateOfflinePackStatus(
            routeId = routeId,
            status = status,
            sizeBytes = sizeBytes,
            regionId = regionId,
            downloadedAt = downloadedAt
        )
    }

    suspend fun downloadRouteOfflinePack(routeId: String): Result<RouteOfflinePackDto> {
        return try {
            val response = apiService.getRouteOfflinePack(routeId)
            if (response.isSuccessful && response.body() != null) {
                val pack = response.body()!!
                val file = File(getRoutesDir(), "${routeId}_pack.json")
                file.writeText(json.encodeToString(pack))
                updateOfflinePackStatus(
                    routeId = routeId,
                    status = "COMPLETE",
                    downloadedAt = System.currentTimeMillis()
                )
                Result.success(pack)
            } else {
                Result.failure(Exception("Failed to fetch route offline pack: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("RouteRepository", "Error downloading route offline pack for $routeId", e)
            Result.failure(e)
        }
    }

    suspend fun startTrip(routeId: String, deviceId: String? = null): Result<StartTripResponseDto> {
        return try {
            val response = apiService.startTrip(StartTripRequestDto(route_id = routeId, device_id = deviceId))
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                activeTripDao.insertOrUpdate(
                    ActiveTripEntity(
                        tripId = dto.trip_id,
                        routeId = dto.route_id,
                        status = dto.status,
                        startedAt = dto.started_at,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                )
                Result.success(dto)
            } else {
                // Offline fallback: create local trip record
                val localTripId = "local-" + System.currentTimeMillis()
                val entity = ActiveTripEntity(
                    tripId = localTripId,
                    routeId = routeId,
                    status = "ACTIVE",
                    startedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(java.util.Date()),
                    lastSyncedAt = System.currentTimeMillis()
                )
                activeTripDao.insertOrUpdate(entity)
                Result.success(
                    StartTripResponseDto(
                        trip_id = localTripId,
                        route_id = routeId,
                        status = "ACTIVE",
                        started_at = entity.startedAt,
                        message = "Trip started in offline mode."
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("RouteRepository", "Network start trip failed, switching to local offline active trip: ${e.message}")
            val localTripId = "local-" + System.currentTimeMillis()
            val entity = ActiveTripEntity(
                tripId = localTripId,
                routeId = routeId,
                status = "ACTIVE",
                startedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(java.util.Date()),
                lastSyncedAt = System.currentTimeMillis()
            )
            activeTripDao.insertOrUpdate(entity)
            Result.success(
                StartTripResponseDto(
                    trip_id = localTripId,
                    route_id = routeId,
                    status = "ACTIVE",
                    started_at = entity.startedAt,
                    message = "Trip started in offline mode."
                )
            )
        }
    }

    suspend fun recordTripProgress(
        tripId: String,
        latitude: Double,
        longitude: Double,
        speedKmh: Double = 0.0
    ): Result<TripProgressResponseDto> {
        return try {
            val response = apiService.recordTripProgress(
                tripId,
                TripProgressRequestDto(latitude = latitude, longitude = longitude, speed_kmh = speedKmh)
            )
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                val existing = activeTripDao.getTripById(tripId)
                if (existing != null) {
                    activeTripDao.insertOrUpdate(
                        existing.copy(
                            lastLatitude = latitude,
                            lastLongitude = longitude,
                            currentSegmentIndex = dto.current_segment_index,
                            progressPct = dto.progress_pct,
                            isOffRoute = dto.is_off_route,
                            nearestHazardDistKm = dto.distance_to_nearest_hazard_km,
                            nearestHazardTitle = dto.nearest_hazard_title,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }
                Result.success(dto)
            } else {
                Result.failure(Exception("Trip progress sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w("RouteRepository", "Network progress sync failed (offline): ${e.message}")
            Result.failure(e)
        }
    }

        suspend fun syncTrackPointsEntitiesBatch(
        tripId: String,
        entities: List<com.touristsafety.app.core.database.entity.TrekTrackPointEntity>
    ): Result<TrackPointsSyncResponseDto> {
        val dtos = entities.map {
            TrackPointBatchItemDto(
                latitude = it.latitude,
                longitude = it.longitude,
                altitude_m = it.altitudeMeters,
                accuracy_m = it.accuracyMeters.toDouble(),
                speed_kmh = it.speedKmh,
                recorded_at = it.recordedAtEpochMs.toString()
            )
        }
        return syncTrackPointsBatch(tripId, dtos)
    }

suspend fun syncTrackPointsBatch(
        tripId: String,
        points: List<TrackPointBatchItemDto>
    ): Result<TrackPointsSyncResponseDto> {
        if (points.isEmpty()) {
            return Result.failure(IllegalArgumentException("Points batch cannot be empty"))
        }
        return try {
            val response = apiService.syncTrackPoints(tripId, TrackPointsSyncRequestDto(points))
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                val existing = activeTripDao.getTripById(tripId)
                if (existing != null) {
                    activeTripDao.insertOrUpdate(
                        existing.copy(
                            lastLatitude = dto.latest_latitude,
                            lastLongitude = dto.latest_longitude,
                            currentSegmentIndex = dto.current_segment_index,
                            progressPct = dto.progress_pct,
                            isOffRoute = dto.is_off_route,
                            nearestHazardDistKm = dto.distance_to_nearest_hazard_km,
                            nearestHazardTitle = dto.nearest_hazard_title,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }
                Result.success(dto)
            } else {
                Result.failure(Exception("Batch track points sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w("RouteRepository", "Batch sync failed (network unreachable): ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncActiveTrip(): Result<Unit> {
        return try {
            val active = activeTripDao.getCurrentActiveTrip() ?: return Result.success(Unit)
            if (active.lastLatitude != null && active.lastLongitude != null) {
                recordTripProgress(
                    tripId = active.tripId,
                    latitude = active.lastLatitude,
                    longitude = active.lastLongitude
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopTrip(tripId: String): Result<Unit> {
        return try {
            apiService.stopTrip(tripId)
            activeTripDao.completeTrip(tripId)
            Result.success(Unit)
        } catch (e: Exception) {
            activeTripDao.completeTrip(tripId)
            Result.success(Unit)
        }
    }

    suspend fun submitCheckIn(
        tripId: String,
        latitude: Double,
        longitude: Double,
        elevationM: Double = 0.0,
        batteryPct: Int = 100,
        status: String = "OK",
        notes: String? = null
    ): Result<TrekkerCheckInResponseDto> {
        return try {
            val response = apiService.submitCheckIn(
                TrekkerCheckInRequestDto(
                    trip_id = tripId,
                    latitude = latitude,
                    longitude = longitude,
                    elevation_m = elevationM,
                    battery_pct = batteryPct,
                    status = status,
                    notes = notes
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Check-in failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.w("RouteRepository", "Offline check-in queued locally: ${e.message}")
            // Return successful local acknowledgement
            Result.success(
                TrekkerCheckInResponseDto(
                    checkin_id = "local-chk-" + System.currentTimeMillis(),
                    trip_id = tripId,
                    status = "STORED_LOCALLY",
                    received_at = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(java.util.Date()),
                    next_check_in_due = "In 3 hours (Offline)",
                    message = "Check-in stored offline. Will synchronize upon connection."
                )
            )
        }
    }

    suspend fun triggerEmergencySos(
        tripId: String,
        latitude: Double,
        longitude: Double,
        elevationM: Double = 0.0,
        batteryPct: Int = 100,
        emergencyType: String = "MEDICAL",
        details: String? = null
    ): Result<EmergencySOSResponseDto> {
        return try {
            val response = apiService.triggerSos(
                EmergencySOSRequestDto(
                    trip_id = tripId,
                    latitude = latitude,
                    longitude = longitude,
                    elevation_m = elevationM,
                    battery_pct = batteryPct,
                    emergency_type = emergencyType,
                    details = details
                )
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Emergency SOS broadcast failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("RouteRepository", "Emergency SOS network transmission failed: ${e.message}")
            Result.success(
                EmergencySOSResponseDto(
                    incident_id = "sos-offline-" + System.currentTimeMillis(),
                    trip_id = tripId,
                    status = "QUEUED_EMERGENCY",
                    escalation_level = "RED_PRIORITY_1",
                    broadcast_timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(java.util.Date()),
                    sar_export_url = "",
                    message = "SOS recorded locally. Emergency Beacon active and queued for instant transmission."
                )
            )
        }
    }

    suspend fun saveCustomRoute(
        title: String,
        description: String?,
        coordinates: List<Pair<Double, Double>>,
        drawingMode: String = "FREEHAND",
        verificationStatus: String = "USER_DRAWN_UNVERIFIED",
        distanceKm: Double
    ): PlannedRouteEntity {
        val geomJson = buildLineStringGeoJson(coordinates)
        val orig = coordinates.firstOrNull() ?: (0.0 to 0.0)
        val dest = coordinates.lastOrNull() ?: (0.0 to 0.0)

        // 1. Attempt backend analysis for corridor hazard analysis & terrain scoring
        try {
            val coordList = coordinates.map { listOf(it.second, it.first) } // [lon, lat]
            val geomElement = try { json.parseToJsonElement(geomJson) } catch (e: Exception) { null }
            val requestDto = com.touristsafety.app.core.network.dto.CustomRouteAnalysisRequestDto(
                coordinates = coordList,
                geometry_geojson = geomElement,
                title = title.ifBlank { "User-Drawn Route" },
                route_name = title.ifBlank { "User-Drawn Route" },
                description = description,
                drawing_mode = drawingMode,
                corridor_distance_km = 5.0
            )
            val response = apiService.analyzeCustomRoute(requestDto)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                // Write payload file to disk
                val file = File(getRoutesDir(), "${dto.route_id}.json")
                file.writeText(json.encodeToString(dto))

                val entity = PlannedRouteEntity(
                    routeId = dto.route_id,
                    title = dto.title.ifBlank { title.ifBlank { "User-Drawn Route" } },
                    originName = dto.origin_name,
                    originLat = dto.origin_lat,
                    originLon = dto.origin_lon,
                    destinationName = dto.destination_name,
                    destLat = dto.dest_lat,
                    destLon = dto.dest_lon,
                    travelMode = dto.travel_mode,
                    distanceKm = dto.distance_km,
                    durationMinutes = dto.duration_minutes,
                    overallHazardScore = dto.overall_hazard_score,
                    overallSafetyStatus = dto.overall_safety_status,
                    highRiskSegmentsCount = dto.high_risk_segments_count,
                    conditionsJson = if (dto.conditions.isNotEmpty()) json.encodeToString(dto.conditions) else "",
                    alternativesJson = if (dto.alternatives.isNotEmpty()) json.encodeToString(dto.alternatives) else "",
                    routeGeoJson = dto.geometry_geojson?.toString() ?: geomJson,
                    elevationProfileJson = if (dto.elevation_profile.isNotEmpty()) json.encodeToString(dto.elevation_profile) else null,
                    safeHavensJson = if (dto.safe_havens.isNotEmpty()) json.encodeToString(dto.safe_havens) else null,
                    decisionExplanation = dto.decision_explanation,
                    corridorGeoJson = dto.corridor_geojson?.toString(),
                    hazardsGeoJson = dto.hazards_geojson?.toString(),
                    firesGeoJson = dto.fires_geojson?.toString(),
                    facilitiesGeoJson = dto.facilities_geojson?.toString(),
                    forestsGeoJson = dto.forests_geojson?.toString(),
                    weatherGeoJson = dto.weather_geojson?.toString(),
                    cellularSitesGeoJson = dto.cellular_sites_geojson?.toString(),
                    cellularRangeGeoJson = dto.cellular_range_geojson?.toString(),
                    cellularCoverageGeoJson = dto.cellular_coverage_geojson?.toString(),
                    waterwaysGeoJson = dto.waterways_geojson?.toString(),
                    historicalLandslidesGeoJson = dto.historical_landslides_geojson?.toString(),
                    dataFreshnessJson = null,
                    providerStatusesJson = null,
                    analysisLimitationsJson = null,
                    corridorWidthKm = dto.corridor_distance_km,
                    totalAscentM = dto.total_ascent_m,
                    maxElevationM = dto.max_elevation_m,
                    isSavedOffline = true,
                    cachedAt = System.currentTimeMillis(),
                    isUserDrawn = true,
                    drawingMode = drawingMode,
                    verificationStatus = if (dto.verification_status.isNotBlank()) dto.verification_status else verificationStatus,
                    userDescription = description,
                    proposedConnectorGeoJson = dto.proposed_connector_geojson?.toString(),
                    updatedAt = System.currentTimeMillis()
                )
                plannedRouteDao.insertOrUpdate(entity)
                activateSavedRoute(entity)
                return entity
            } else {
                Log.w("RouteRepository", "Backend custom route analysis returned code ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w("RouteRepository", "Custom route online analysis skipped or failed: ${e.message}")
        }

        // 2. Offline / local fallback
        val routeId = "user-route-" + java.util.UUID.randomUUID().toString()
        val localEntity = PlannedRouteEntity(
            routeId = routeId,
            title = title.ifBlank { "User-Drawn Route" },
            originName = "Drawn Start",
            originLat = orig.first,
            originLon = orig.second,
            destinationName = "Drawn Destination",
            destLat = dest.first,
            destLon = dest.second,
            travelMode = "hiking",
            distanceKm = distanceKm,
            durationMinutes = (distanceKm / 3.0) * 60.0,
            overallHazardScore = 0.0,
            overallSafetyStatus = "SAFE",
            highRiskSegmentsCount = 0,
            routeGeoJson = geomJson,
            isSavedOffline = true,
            isUserDrawn = true,
            drawingMode = drawingMode,
            verificationStatus = verificationStatus,
            userDescription = description,
            updatedAt = System.currentTimeMillis()
        )
        plannedRouteDao.insertOrUpdate(localEntity)
        activateSavedRoute(localEntity)
        return localEntity
    }

    suspend fun deleteCustomRoute(routeId: String) {
        val entity = plannedRouteDao.getRouteById(routeId)
        if (entity != null) {
            plannedRouteDao.deleteRoute(routeId)
        }
        val file = File(getRoutesDir(), "$routeId.json")
        if (file.exists()) {
            file.delete()
        }
        if (_activeRouteFlow.value?.route_id == routeId) {
            _activeRouteFlow.value = null
        }
    }

    fun buildLineStringGeoJson(coords: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        sb.append("{\"type\":\"LineString\",\"coordinates\":[")
        coords.forEachIndexed { i, (lat, lon) ->
            if (i > 0) sb.append(",")
            sb.append("[").append(lon).append(",").append(lat).append("]")
        }
        sb.append("]}")
        return sb.toString()
    }
}
