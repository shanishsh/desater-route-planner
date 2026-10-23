package com.touristsafety.app.core.repository

import com.touristsafety.app.core.database.dao.ItineraryDao
import com.touristsafety.app.core.database.entity.ItineraryEntity
import com.touristsafety.app.core.database.entity.RouteSegmentEntity
import com.touristsafety.app.core.model.Itinerary
import com.touristsafety.app.core.model.RouteSegment
import com.touristsafety.app.core.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItineraryRepository @Inject constructor(
    private val apiService: ApiService,
    private val itineraryDao: ItineraryDao
) {

    fun getItineraries(): Flow<List<Itinerary>> {
        return itineraryDao.getAllItineraries().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getItinerary(id: String): Flow<Itinerary?> {
        return itineraryDao.getItineraryById(id).flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                itineraryDao.getSegmentsForItinerary(id).map { segments ->
                    entity.toDomainModel(segments.map { it.toDomainModel() })
                }
            }
        }
    }

    fun getSavedOfflineItineraries(): Flow<List<Itinerary>> {
        return itineraryDao.getSavedOfflineItineraries().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun setSavedOffline(itineraryId: String, isSaved: Boolean) {
        itineraryDao.setSavedOffline(itineraryId, isSaved)
    }

    suspend fun refreshItineraries(): Result<Unit> {
        return try {
            val response = apiService.getItineraries()
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!.items
                for (dto in dtos) {
                    val entity = ItineraryEntity(
                        id = dto.id,
                        title = dto.title,
                        description = dto.description,
                        region = dto.region,
                        difficulty = dto.difficulty,
                        distanceKm = dto.distance_km,
                        estimatedDurationHours = dto.estimated_duration_hours,
                        startLatitude = dto.start_latitude,
                        startLongitude = dto.start_longitude,
                        endLatitude = dto.end_latitude,
                        endLongitude = dto.end_longitude,
                        routeGeoJson = dto.route_geojson.toString(),
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    val segments = dto.segments.map { s ->
                        RouteSegmentEntity(
                            id = s.id,
                            itineraryId = dto.id,
                            sequenceOrder = s.sequence_order,
                            name = s.name,
                            elevationGainM = s.elevation_gain_m,
                            hazardLevel = s.hazard_level
                        )
                    }
                    itineraryDao.insertItineraryWithSegments(entity, segments)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ItineraryEntity.toDomainModel(segments: List<RouteSegment> = emptyList()) = Itinerary(
        id = id,
        title = title,
        description = description,
        region = region,
        difficulty = difficulty,
        distanceKm = distanceKm,
        estimatedDurationHours = estimatedDurationHours,
        startLatitude = startLatitude,
        startLongitude = startLongitude,
        endLatitude = endLatitude,
        endLongitude = endLongitude,
        routeGeoJson = routeGeoJson,
        segments = segments,
        isSavedOffline = isSavedOffline,
        lastSyncTimestamp = lastSyncTimestamp
    )

    private fun RouteSegmentEntity.toDomainModel() = RouteSegment(
        id = id,
        sequenceOrder = sequenceOrder,
        name = name,
        elevationGainM = elevationGainM,
        hazardLevel = hazardLevel
    )
}

