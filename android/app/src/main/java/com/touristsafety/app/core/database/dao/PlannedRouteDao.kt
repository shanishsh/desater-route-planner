package com.touristsafety.app.core.database.dao

import androidx.room.*
import com.touristsafety.app.core.database.entity.PlannedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedRouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(route: PlannedRouteEntity)

    @Query("SELECT * FROM planned_routes WHERE routeId = :routeId")
    suspend fun getRouteById(routeId: String): PlannedRouteEntity?

    @Query("SELECT * FROM planned_routes WHERE routeId = :routeId")
    fun getRouteFlow(routeId: String): Flow<PlannedRouteEntity?>

    @Query("SELECT * FROM planned_routes ORDER BY cachedAt DESC")
    fun getAllRoutesFlow(): Flow<List<PlannedRouteEntity>>

    @Query("UPDATE planned_routes SET offlinePackStatus = :status, offlinePackSizeBytes = :sizeBytes, offlineRegionId = :regionId, offlineDownloadedAt = :downloadedAt WHERE routeId = :routeId")
    suspend fun updateOfflinePackStatus(
        routeId: String,
        status: String,
        sizeBytes: Long,
        regionId: Long?,
        downloadedAt: Long?
    )

    @Query("DELETE FROM planned_routes WHERE routeId = :routeId")
    suspend fun deleteRoute(routeId: String)

    @Query("DELETE FROM planned_routes")
    suspend fun clearAll()
}

