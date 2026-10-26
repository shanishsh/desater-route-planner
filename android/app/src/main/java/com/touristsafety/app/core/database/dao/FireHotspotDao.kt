package com.touristsafety.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.touristsafety.app.core.database.entity.FireHotspotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FireHotspotDao {

    @Query("SELECT * FROM fire_hotspots ORDER BY acquisitionDate DESC, acquisitionTime DESC")
    fun getAllHotspots(): Flow<List<FireHotspotEntity>>

    @Query("SELECT * FROM fire_hotspots ORDER BY acquisitionDate DESC, acquisitionTime DESC")
    suspend fun getAllHotspotsList(): List<FireHotspotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(hotspots: List<FireHotspotEntity>)

    @Query("DELETE FROM fire_hotspots")
    suspend fun clearAll()
}

