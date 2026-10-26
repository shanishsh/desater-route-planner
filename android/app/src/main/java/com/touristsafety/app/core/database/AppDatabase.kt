package com.touristsafety.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.touristsafety.app.core.database.dao.*
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
        SubscriberEntity::class,
        ForestEntity::class,
        FireHotspotEntity::class,
        ProviderStatusEntity::class,
        PlannedRouteEntity::class,
        ActiveTripEntity::class,
        TrekTrackPointEntity::class
    ],
    version = 10,
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
    abstract fun forestDao(): ForestDao
    abstract fun fireHotspotDao(): FireHotspotDao
    abstract fun providerStatusDao(): ProviderStatusDao
    abstract fun plannedRouteDao(): PlannedRouteDao
    abstract fun activeTripDao(): ActiveTripDao
    abstract fun trekTrackPointDao(): TrekTrackPointDao
}
