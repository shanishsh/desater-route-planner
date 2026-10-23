package com.touristsafety.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.touristsafety.app.core.database.dao.AlertDao
import com.touristsafety.app.core.database.dao.HazardZoneDao
import com.touristsafety.app.core.database.dao.ItineraryDao
import com.touristsafety.app.core.database.dao.MapPointDao
import com.touristsafety.app.core.database.dao.SubscriberDao
import com.touristsafety.app.core.database.dao.TriggeredHazardDao
import com.touristsafety.app.core.database.dao.WeatherDao
import com.touristsafety.app.core.database.entity.*

@Database(
    entities = [
        ItineraryEntity::class,
        RouteSegmentEntity::class,
        AlertEntity::class,
        WeatherEntity::class,
        MapPointEntity::class,
        HazardZoneEntity::class,
        TriggeredHazardWarningEntity::class,
        SubscriberEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itineraryDao(): ItineraryDao
    abstract fun alertDao(): AlertDao
    abstract fun weatherDao(): WeatherDao
    abstract fun mapPointDao(): MapPointDao
    abstract fun hazardZoneDao(): HazardZoneDao
    abstract fun triggeredHazardDao(): TriggeredHazardDao
    abstract fun subscriberDao(): SubscriberDao
}

