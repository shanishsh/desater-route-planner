package com.touristsafety.app.core.repository

import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.network.dto.PointContextDto
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PointContextRepository @Inject constructor(
    private val apiService: ApiService
) {

    private data class CachedPointContext(
        val data: PointContextDto,
        val timestampEpochMs: Long
    )

    private val cache = ConcurrentHashMap<String, CachedPointContext>()
    private val cacheTtlMs = 10 * 60 * 1000L // 10 minutes

    suspend fun getPointContext(lat: Double, lon: Double, radiusKm: Double = 5.0): Result<PointContextDto> {
        val key = makeCacheKey(lat, lon, radiusKm)
        val now = System.currentTimeMillis()

        val cached = cache[key]
        if (cached != null && (now - cached.timestampEpochMs) < cacheTtlMs) {
            val isStale = (now - cached.timestampEpochMs) > (5 * 60 * 1000L)
            return Result.success(cached.data.copy(is_cached = true, is_stale = isStale))
        }

        return try {
            val response = apiService.getPointContext(lat, lon, radiusKm)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                cache[key] = CachedPointContext(dto, now)
                Result.success(dto)
            } else {
                // Return stale cache if available
                if (cached != null) {
                    Result.success(cached.data.copy(is_cached = true, is_stale = true))
                } else {
                    Result.failure(Exception("Point inspection failed (HTTP ${response.code()}): ${response.message()}"))
                }
            }
        } catch (e: Exception) {
            if (cached != null) {
                Result.success(cached.data.copy(is_cached = true, is_stale = true))
            } else {
                Result.failure(e)
            }
        }
    }

    private fun makeCacheKey(lat: Double, lon: Double, radiusKm: Double): String {
        return String.format(Locale.US, "%.3f:%.3f:%.1f", lat, lon, radiusKm)
    }
}

