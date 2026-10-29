package com.touristsafety.app.core.database.dao

import androidx.room.*
import com.touristsafety.app.core.database.entity.ActiveTripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveTripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(trip: ActiveTripEntity)

    @Query("SELECT * FROM active_trips WHERE tripId = :tripId")
    suspend fun getTripById(tripId: String): ActiveTripEntity?

    @Query("SELECT * FROM active_trips WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveTripFlow(): Flow<ActiveTripEntity?>

    @Query("SELECT * FROM active_trips WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getCurrentActiveTrip(): ActiveTripEntity?

    @Query("UPDATE active_trips SET status = 'COMPLETED' WHERE tripId = :tripId")
    suspend fun completeTrip(tripId: String)

    @Query("SELECT * FROM active_trips WHERE status = 'COMPLETED' ORDER BY lastSyncedAt DESC")
    fun getCompletedTripsFlow(): Flow<List<ActiveTripEntity>>

    @Query("SELECT * FROM active_trips ORDER BY lastSyncedAt DESC")
    fun getAllTripsFlow(): Flow<List<ActiveTripEntity>>

    @Query("UPDATE active_trips SET status = 'COMPLETED', completedAtEpochMs = :completedAt, traveledDistanceMeters = :distanceMeters, elapsedDurationSeconds = :durationSeconds, recordedPointsCount = :pointsCount WHERE tripId = :tripId")
    suspend fun completeTripWithMetrics(
        tripId: String,
        completedAt: Long,
        distanceMeters: Double,
        durationSeconds: Long,
        pointsCount: Int
    )

    @Query("DELETE FROM active_trips")
    suspend fun clearAll()
}

