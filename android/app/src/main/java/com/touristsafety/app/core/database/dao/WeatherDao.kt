package com.touristsafety.app.core.database.dao

import androidx.room.*
import com.touristsafety.app.core.database.entity.WeatherEntity
import com.touristsafety.app.core.database.entity.MapPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    @Query("SELECT * FROM weather_cache WHERE locationKey = :key LIMIT 1")
    fun getWeather(key: String): Flow<WeatherEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(weather: WeatherEntity)
}

@Dao
interface MapPointDao {

    @Query("SELECT * FROM map_points ORDER BY name ASC")
    fun getAllMapPoints(): Flow<List<MapPointEntity>>

    @Query("SELECT * FROM map_points WHERE category = :category ORDER BY name ASC")
    fun getPointsByCategory(category: String): Flow<List<MapPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapPoints(points: List<MapPointEntity>)

    @Query("DELETE FROM map_points")
    suspend fun clearMapPoints()
}

