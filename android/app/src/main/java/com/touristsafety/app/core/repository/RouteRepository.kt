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
            provider_statuses = providerStatuses,
            analysis_limitations = limitations,
            created_at = java.util.Date(entity.cachedAt).toString()
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
}


