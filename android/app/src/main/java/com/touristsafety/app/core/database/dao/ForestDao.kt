package com.touristsafety.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.touristsafety.app.core.database.entity.ForestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ForestDao {

    @Query("SELECT * FROM forest_features")
    fun getAllForestFeatures(): Flow<List<ForestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(features: List<ForestEntity>)

    @Query("DELETE FROM forest_features")
    suspend fun clearAll()
}

