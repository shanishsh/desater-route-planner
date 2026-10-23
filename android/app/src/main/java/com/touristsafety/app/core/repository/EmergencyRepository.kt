package com.touristsafety.app.core.repository

import com.touristsafety.app.core.database.dao.MapPointDao
import com.touristsafety.app.core.database.entity.MapPointEntity
import com.touristsafety.app.core.model.MapPoint
import com.touristsafety.app.core.model.PointCategory
import com.touristsafety.app.core.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyRepository @Inject constructor(
    private val apiService: ApiService,
    private val mapPointDao: MapPointDao
) {

    fun getMapPoints(): Flow<List<MapPoint>> {
        return mapPointDao.getAllMapPoints().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getPointsByCategory(category: PointCategory): Flow<List<MapPoint>> {
        return mapPointDao.getPointsByCategory(category.name.lowercase()).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun refreshMapPoints(): Result<Unit> {
        return try {
            val response = apiService.getMapPoints(limit = 100)
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!.items
                val entities = dtos.map { dto ->
                    val verifiedEpoch = try {
                        Instant.parse(dto.verification_timestamp).toEpochMilli()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                    MapPointEntity(
                        id = dto.id,
                        name = dto.name,
                        category = dto.category.lowercase(),
                        latitude = dto.latitude,
                        longitude = dto.longitude,
                        address = dto.address,
                        phone = dto.phone,
                        description = dto.description,
                        verificationStatus = dto.verification_status,
                        verificationTimestampEpochMs = verifiedEpoch,
                        provenance = dto.provenance
                    )
                }
                mapPointDao.clearMapPoints()
                mapPointDao.insertMapPoints(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun MapPointEntity.toDomainModel(): MapPoint {
        val cat = when (category.lowercase()) {
            "emergency_service" -> PointCategory.EMERGENCY_SERVICE
            "medical", "hospital" -> PointCategory.MEDICAL
            "clinic" -> PointCategory.CLINIC
            "police" -> PointCategory.POLICE
            "fire_station", "fire" -> PointCategory.FIRE_STATION
            "shelter" -> PointCategory.SHELTER
            "visitor_info", "tourist_assistance" -> PointCategory.VISITOR_INFO
            "transport" -> PointCategory.TRANSPORT
            else -> PointCategory.VERIFIED_PUBLIC
        }
        return MapPoint(
            id = id,
            name = name,
            category = cat,
            latitude = latitude,
            longitude = longitude,
            address = address,
            phone = phone,
            description = description,
            verificationStatus = verificationStatus,
            verificationTimestampEpochMs = verificationTimestampEpochMs,
            provenance = provenance
        )
    }
}

