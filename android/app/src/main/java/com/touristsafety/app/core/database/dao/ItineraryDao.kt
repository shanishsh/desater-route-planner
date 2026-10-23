package com.touristsafety.app.core.database.dao

import androidx.room.*
import com.touristsafety.app.core.database.entity.ItineraryEntity
import com.touristsafety.app.core.database.entity.RouteSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItineraryDao {

    @Query("SELECT * FROM itineraries ORDER BY lastSyncTimestamp DESC")
    fun getAllItineraries(): Flow<List<ItineraryEntity>>

    @Query("SELECT * FROM itineraries WHERE id = :id")
    fun getItineraryById(id: String): Flow<ItineraryEntity?>

    @Query("SELECT * FROM itineraries WHERE isSavedOffline = 1")
    fun getSavedOfflineItineraries(): Flow<List<ItineraryEntity>>

    @Query("SELECT * FROM route_segments WHERE itineraryId = :itineraryId ORDER BY sequenceOrder ASC")
    fun getSegmentsForItinerary(itineraryId: String): Flow<List<RouteSegmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItinerary(itinerary: ItineraryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<RouteSegmentEntity>)

    @Query("UPDATE itineraries SET isSavedOffline = :isSaved WHERE id = :id")
    suspend fun setSavedOffline(id: String, isSaved: Boolean)

    @Transaction
    suspend fun insertItineraryWithSegments(
        itinerary: ItineraryEntity,
        segments: List<RouteSegmentEntity>
    ) {
        insertItinerary(itinerary)
        insertSegments(segments)
    }
}

