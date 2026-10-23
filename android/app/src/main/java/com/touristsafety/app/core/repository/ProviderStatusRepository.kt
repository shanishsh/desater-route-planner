package com.touristsafety.app.core.repository

import com.touristsafety.app.core.database.dao.ProviderStatusDao
import com.touristsafety.app.core.database.entity.ProviderStatusEntity
import com.touristsafety.app.core.model.ProviderStatus
import com.touristsafety.app.core.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderStatusRepository @Inject constructor(
    private val apiService: ApiService,
    private val providerStatusDao: ProviderStatusDao
) {

    fun getProviderStatuses(): Flow<List<ProviderStatus>> {
        return providerStatusDao.getAllProviderStatuses().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun refreshStatuses(): Result<Unit> {
        return try {
            val response = apiService.getProviderStatuses()
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                val entities = dtos.map { d ->
                    ProviderStatusEntity(
                        providerId = d.provider_id,
                        name = d.name,
                        category = d.category,
                        isConfigured = d.is_configured,
                        status = d.status,
                        lastAttemptAt = d.last_attempt_at,
                        lastSuccessAt = d.last_success_at,
                        lastHttpStatus = d.last_http_status,
                        recordCount = d.record_count,
                        dataState = d.data_state,
                        isStale = d.is_stale,
                        diagnosticMessage = d.diagnostic_message,
                        attribution = d.attribution,
                        sourceUrl = d.source_url,
                        registrationUrl = d.registration_url,
                        retrievedAtEpochMs = System.currentTimeMillis()
                    )
                }
                providerStatusDao.insertAll(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerProviderRefresh(lat: Double = 32.2396, lon: Double = 77.1887): Result<Unit> {
        return try {
            val response = apiService.refreshProviders(lat, lon)
            if (response.isSuccessful) {
                refreshStatuses()
            } else {
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ProviderStatusEntity.toDomainModel(): ProviderStatus {
        return ProviderStatus(
            providerId = providerId,
            name = name,
            category = category,
            isConfigured = isConfigured,
            status = status,
            lastAttemptAt = lastAttemptAt,
            lastSuccessAt = lastSuccessAt,
            lastHttpStatus = lastHttpStatus,
            recordCount = recordCount,
            dataState = dataState,
            isStale = isStale,
            diagnosticMessage = diagnosticMessage,
            attribution = attribution,
            sourceUrl = sourceUrl,
            registrationUrl = registrationUrl
        )
    }
}

