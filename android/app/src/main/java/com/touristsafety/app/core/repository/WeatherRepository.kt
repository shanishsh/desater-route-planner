package com.touristsafety.app.core.repository

import com.touristsafety.app.core.database.dao.WeatherDao
import com.touristsafety.app.core.database.entity.WeatherEntity
import com.touristsafety.app.core.model.Weather
import com.touristsafety.app.core.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val apiService: ApiService,
    private val weatherDao: WeatherDao
) {

    fun getWeather(latitude: Double, longitude: Double): Flow<Weather?> {
        val key = makeLocationKey(latitude, longitude)
        return weatherDao.getWeather(key).map { entity ->
            entity?.toDomainModel()
        }
    }

    suspend fun refreshWeather(latitude: Double, longitude: Double): Result<Unit> {
        return try {
            val response = apiService.getWeather(latitude, longitude)
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                val key = makeLocationKey(latitude, longitude)
                val validEpoch = try { Instant.parse(dto.forecast_valid_at).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }
                val retrievedEpoch = try { Instant.parse(dto.retrieved_at).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() }

                val entity = WeatherEntity(
                    locationKey = key,
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    temperatureC = dto.temperature_c,
                    relativeHumidityPct = dto.relative_humidity_pct,
                    windSpeedKmh = dto.wind_speed_kmh,
                    precipitationMm = dto.precipitation_mm,
                    weatherCode = dto.weather_code,
                    weatherDescription = dto.weather_description,
                    forecastValidAtEpochMs = validEpoch,
                    retrievedAtEpochMs = retrievedEpoch,
                    isFallback = dto.is_fallback
                )
                weatherDao.insertWeather(entity)
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP error ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun makeLocationKey(lat: Double, lon: Double): String {
        return String.format(Locale.US, "%.2f:%.2f", lat, lon)
    }

    private fun WeatherEntity.toDomainModel(): Weather {
        val now = System.currentTimeMillis()
        val isStale = (now - retrievedAtEpochMs) > (900 * 1000L) // 15 mins
        return Weather(
            latitude = latitude,
            longitude = longitude,
            temperatureC = temperatureC,
            relativeHumidityPct = relativeHumidityPct,
            windSpeedKmh = windSpeedKmh,
            precipitationMm = precipitationMm,
            weatherCode = weatherCode,
            weatherDescription = weatherDescription,
            forecastValidAtEpochMs = forecastValidAtEpochMs,
            retrievedAtEpochMs = retrievedAtEpochMs,
            isCached = true,
            isFallback = isFallback,
            isStale = isStale
        )
    }
}

