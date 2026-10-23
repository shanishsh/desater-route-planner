package com.touristsafety.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.touristsafety.app.core.database.entity.ProviderStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderStatusDao {

    @Query("SELECT * FROM provider_statuses ORDER BY category ASC")
    fun getAllProviderStatuses(): Flow<List<ProviderStatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(statuses: List<ProviderStatusEntity>)
}

