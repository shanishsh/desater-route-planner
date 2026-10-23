package com.touristsafety.app.core.database.dao

import androidx.room.*
import com.touristsafety.app.core.database.entity.SubscriberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriberDao {
    @Query("SELECT * FROM subscribers LIMIT 1")
    fun getSubscriber(): Flow<SubscriberEntity?>

    @Query("SELECT * FROM subscribers LIMIT 1")
    suspend fun getSubscriberSync(): SubscriberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(subscriber: SubscriberEntity)

    @Query("DELETE FROM subscribers")
    suspend fun clear()
}

