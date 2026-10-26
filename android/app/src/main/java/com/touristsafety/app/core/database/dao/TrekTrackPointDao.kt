package com.touristsafety.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.touristsafety.app.core.database.entity.TrekTrackPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrekTrackPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: TrekTrackPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<TrekTrackPointEntity>): List<Long>

    @Query("SELECT * FROM trek_track_points WHERE tripId = :tripId AND isSynced = 0 ORDER BY recordedAtEpochMs ASC LIMIT :limit")
    suspend fun getUnsyncedPoints(tripId: String, limit: Int = 100): List<TrekTrackPointEntity>

    @Query("UPDATE trek_track_points SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markPointsSynced(ids: List<Long>)

    @Query("SELECT * FROM trek_track_points WHERE tripId = :tripId AND isAcceptedForDistance = 1 ORDER BY recordedAtEpochMs ASC")
    fun getAllAcceptedPointsFlow(tripId: String): Flow<List<TrekTrackPointEntity>>

    @Query("SELECT * FROM trek_track_points WHERE tripId = :tripId ORDER BY recordedAtEpochMs ASC")
    suspend fun getAllPointsForTrip(tripId: String): List<TrekTrackPointEntity>

    @Query("SELECT COUNT(*) FROM trek_track_points WHERE tripId = :tripId AND isSynced = 0")
    fun getUnsyncedCountFlow(tripId: String): Flow<Int>

    @Query("DELETE FROM trek_track_points WHERE tripId = :tripId")
    suspend fun deletePointsForTrip(tripId: String)
}

