package com.touristsafety.app.core.database.dao

import androidx.room.*
import com.touristsafety.app.core.database.entity.TrailReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrailReportDao {
    @Query("SELECT * FROM trail_reports ORDER BY observationTimeEpochMs DESC")
    fun getAllReports(): Flow<List<TrailReportEntity>>

    @Query("SELECT * FROM trail_reports WHERE syncStatus = 'PENDING'")
    suspend fun getPendingReports(): List<TrailReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: TrailReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<TrailReportEntity>)

    @Query("UPDATE trail_reports SET syncStatus = :status, serverPhotoUrl = :photoUrl WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, photoUrl: String? = null)

    @Query("DELETE FROM trail_reports WHERE id = :id")
    suspend fun deleteReport(id: String)
}

