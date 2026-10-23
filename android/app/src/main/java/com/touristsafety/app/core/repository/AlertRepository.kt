package com.touristsafety.app.core.repository

import com.touristsafety.app.core.database.dao.AlertDao
import com.touristsafety.app.core.database.entity.AlertEntity
import com.touristsafety.app.core.model.Alert
import com.touristsafety.app.core.model.AlertCategory
import com.touristsafety.app.core.model.AlertSeverity
import com.touristsafety.app.core.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val apiService: ApiService,
    private val alertDao: AlertDao
) {

    fun getAlerts(): Flow<List<Alert>> {
        return alertDao.getAllAlerts().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getAlertsForItinerary(itineraryId: String): Flow<List<Alert>> {
        return alertDao.getAlertsForItinerary(itineraryId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun refreshAlerts(): Result<Unit> {
        return try {
            val response = apiService.getAlerts()
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!.items
                val entities = dtos.map { dto ->
                    val publishedEpoch = parseIsoTimestamp(dto.source_published_at)
                    val retrievedEpoch = parseIsoTimestamp(dto.retrieved_at)
                    AlertEntity(
                        id = dto.id,
                        title = dto.title,
                        description = dto.description,
                        severity = dto.severity.lowercase(),
                        category = dto.category.lowercase(),
                        affectedItineraryId = dto.affected_itinerary_id,
                        affectedSegmentId = dto.affected_segment_id,
                        sourceName = dto.source_name,
                        sourceUrl = dto.source_url,
                        sourcePublishedAtEpochMs = publishedEpoch,
                        retrievedAtEpochMs = retrievedEpoch,
                        freshnessThresholdSeconds = dto.freshness_threshold_seconds,
                        isSimulation = dto.is_simulation
                    )
                }
                alertDao.clearAlerts()
                alertDao.insertAlerts(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseIsoTimestamp(isoString: String): Long {
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun AlertEntity.toDomainModel(): Alert {
        val severityEnum = when (severity.lowercase()) {
            "low" -> AlertSeverity.LOW
            "medium" -> AlertSeverity.MEDIUM
            "high" -> AlertSeverity.HIGH
            "critical" -> AlertSeverity.CRITICAL
            else -> AlertSeverity.LOW
        }
        val categoryEnum = when (category.lowercase()) {
            "weather" -> AlertCategory.WEATHER
            "hazard" -> AlertCategory.HAZARD
            "transport" -> AlertCategory.TRANSPORT
            "crowd" -> AlertCategory.CROWD
            else -> AlertCategory.HAZARD
        }
        return Alert(
            id = id,
            title = title,
            description = description,
            severity = severityEnum,
            category = categoryEnum,
            affectedItineraryId = affectedItineraryId,
            affectedSegmentId = affectedSegmentId,
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            sourcePublishedAtEpochMs = sourcePublishedAtEpochMs,
            retrievedAtEpochMs = retrievedAtEpochMs,
            freshnessThresholdSeconds = freshnessThresholdSeconds,
            isSimulation = isSimulation
        )
    }
}

