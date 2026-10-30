package com.touristsafety.app.core.database.dao

import androidx.room.*
import com.touristsafety.app.core.database.entity.HazardZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HazardZoneDao {
    @Query("SELECT * FROM hazard_zones WHERE status IN ('active', 'updated') ORDER BY publishedAtEpochMs DESC")
    fun getAllActive(): Flow<List<HazardZoneEntity>>

    @Query("SELECT * FROM hazard_zones WHERE status IN ('active', 'updated')")
    suspend fun getAllActiveList(): List<HazardZoneEntity>

    @Query("SELECT * FROM hazard_zones WHERE alertId = :id")
    suspend fun getById(id: String): HazardZoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(zones: List<HazardZoneEntity>)

    @Query("DELETE FROM hazard_zones")
    suspend fun deleteAll()
}

