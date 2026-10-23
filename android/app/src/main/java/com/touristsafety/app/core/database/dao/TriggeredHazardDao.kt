package com.touristsafety.app.core.database.dao

import androidx.room.*
import com.touristsafety.app.core.database.entity.TriggeredHazardWarningEntity

@Dao
interface TriggeredHazardDao {
    @Query("SELECT EXISTS(SELECT 1 FROM triggered_hazard_warnings WHERE alertId = :alertId)")
    suspend fun hasBeenTriggered(alertId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(warning: TriggeredHazardWarningEntity)

    @Query("SELECT * FROM triggered_hazard_warnings")
    suspend fun getAllTriggered(): List<TriggeredHazardWarningEntity>

    @Query("DELETE FROM triggered_hazard_warnings WHERE alertId = :alertId")
    suspend fun clearWarning(alertId: String)
}

