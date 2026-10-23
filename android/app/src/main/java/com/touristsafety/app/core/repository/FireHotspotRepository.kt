package com.touristsafety.app.core.repository

import com.touristsafety.app.core.database.dao.FireHotspotDao
import com.touristsafety.app.core.database.entity.FireHotspotEntity
import com.touristsafety.app.core.model.FireHotspot
import com.touristsafety.app.core.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FireHotspotRepository @Inject constructor(
    private val apiService: ApiService,
    private val fireHotspotDao: FireHotspotDao
) {

    fun getHotspots(): Flow<List<FireHotspot>> {
        return fireHotspotDao.getAllHotspots().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun refreshHotspots(
        minLat: Double = 32.0,
        minLon: Double = 77.0,
        maxLat: Double = 32.5,
        maxLon: Double = 77.5,
        days: Int = 1
    ): Result<Unit> {
        return try {
            val response = apiService.getActiveFires(minLat, minLon, maxLat, maxLon, days)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                if (!dto.is_configured) {
                    return Result.failure(Exception(dto.diagnostic_message ?: "NASA FIRMS is unconfigured. A free MAP_KEY is required on the backend."))
                }
                if (dto.provider_status != "HEALTHY" && dto.provider_status != "STANDBY") {
                    return Result.failure(Exception(dto.diagnostic_message ?: "NASA FIRMS error: ${dto.provider_status}"))
                }
                val entities = dto.records.map { r ->
                    FireHotspotEntity(
                        id = r.id,
                        latitude = r.latitude,
                        longitude = r.longitude,
                        brightness = r.brightness,
                        confidence = r.confidence,
                        frpMw = r.frp_mw,
                        acquisitionDate = r.acquisition_date,
                        acquisitionTime = r.acquisition_time,
                        satellite = r.satellite,
                        instrument = r.instrument,
                        isSimulated = r.is_simulated,
                        retrievedAtEpochMs = System.currentTimeMillis()
                    )
                }
                fireHotspotDao.clearAll()
                if (entities.isNotEmpty()) {
                    fireHotspotDao.insertAll(entities)
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun FireHotspotEntity.toDomainModel(): FireHotspot {
        return FireHotspot(
            id = id,
            latitude = latitude,
            longitude = longitude,
            brightness = brightness,
            confidence = confidence,
            frpMw = frpMw,
            acquisitionDate = acquisitionDate,
            acquisitionTime = acquisitionTime,
            satellite = satellite,
            isSimulated = isSimulated
        )
    }
}

