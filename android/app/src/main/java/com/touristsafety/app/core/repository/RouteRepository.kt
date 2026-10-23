package com.touristsafety.app.core.repository

import android.util.Log
import com.touristsafety.app.core.database.dao.ActiveTripDao
import com.touristsafety.app.core.database.dao.PlannedRouteDao
import com.touristsafety.app.core.database.entity.ActiveTripEntity
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.network.dto.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepository @Inject constructor(
    private val apiService: ApiService,
    private val plannedRouteDao: PlannedRouteDao,
    private val activeTripDao: ActiveTripDao
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    val savedRoutesFlow: Flow<List<PlannedRouteEntity>> = plannedRouteDao.getAllRoutesFlow()
    val activeTripFlow: Flow<ActiveTripEntity?> = activeTripDao.getActiveTripFlow()

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

    suspend fun planRoute(
        originLat: Double,
        originLon: Double,
        originName: String,
        destLat: Double,
        destLon: Double,
        destName: String,
        travelMode: String = "driving"
    ): Result<PlannedRouteResponseDto> {
        return try {
            val request = PlanRouteRequestDto(
                origin_lat = originLat,
                origin_lon = originLon,
                origin_name = originName,
                destination_lat = destLat,
                destination_lon = destLon,
                destination_name = destName,
                travel_mode = travelMode
            )
            val response = apiService.planRoute(request)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                // Automatically persist in Room for offline resilience
                saveRouteOffline(dto)
                Result.success(dto)
            } else {
                Result.failure(Exception("Route planning failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("RouteRepository", "Error planning route", e)
            Result.failure(e)
        }
    }

    suspend fun saveRouteOffline(dto: PlannedRouteResponseDto) {
        try {
            val conditionsStr = json.encodeToString(dto.conditions)
            val alternativesStr = json.encodeToString(dto.alternatives)
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
                conditionsJson = conditionsStr,
                alternativesJson = alternativesStr,
                routeGeoJson = dto.geometry_geojson?.toString(),
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
    }

    suspend fun getRouteById(routeId: String): PlannedRouteEntity? {
        return plannedRouteDao.getRouteById(routeId)
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
}

