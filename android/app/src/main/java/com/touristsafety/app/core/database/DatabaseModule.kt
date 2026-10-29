package com.touristsafety.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.touristsafety.app.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `trek_track_points` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `tripId` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `altitudeMeters` REAL NOT NULL,
                    `accuracyMeters` REAL NOT NULL,
                    `speedKmh` REAL NOT NULL,
                    `recordedAtEpochMs` INTEGER NOT NULL,
                    `isAcceptedForDistance` INTEGER NOT NULL,
                    `isSynced` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trek_track_points_tripId` ON `trek_track_points` (`tripId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_trek_track_points_tripId_isSynced` ON `trek_track_points` (`tripId`, `isSynced`)")

            db.execSQL("ALTER TABLE `active_trips` ADD COLUMN `traveledDistanceMeters` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `active_trips` ADD COLUMN `elapsedDurationSeconds` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `active_trips` ADD COLUMN `isPaused` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `active_trips` ADD COLUMN `pausedAtEpochMs` INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `planned_routes` ADD COLUMN `cellularRangeGeoJson` TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `planned_routes` ADD COLUMN `isUserDrawn` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `planned_routes` ADD COLUMN `drawingMode` TEXT NOT NULL DEFAULT 'AUTOMATIC'")
            db.execSQL("ALTER TABLE `planned_routes` ADD COLUMN `verificationStatus` TEXT NOT NULL DEFAULT 'MAPPED_TRAIL'")
            db.execSQL("ALTER TABLE `planned_routes` ADD COLUMN `userDescription` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `planned_routes` ADD COLUMN `proposedConnectorGeoJson` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `planned_routes` ADD COLUMN `waypointsJson` TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE `planned_routes` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `hazard_zones` ADD COLUMN `polygonGeoJson` TEXT DEFAULT NULL")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tourist_safety.db"
        )
            .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideItineraryDao(db: AppDatabase): ItineraryDao = db.itineraryDao()

    @Provides
    fun provideAlertDao(db: AppDatabase): AlertDao = db.alertDao()

    @Provides
    fun provideWeatherDao(db: AppDatabase): WeatherDao = db.weatherDao()

    @Provides
    fun provideMapPointDao(db: AppDatabase): MapPointDao = db.mapPointDao()

    @Provides
    fun provideHazardZoneDao(db: AppDatabase): HazardZoneDao = db.hazardZoneDao()

    @Provides
    fun provideTriggeredHazardDao(db: AppDatabase): TriggeredHazardDao = db.triggeredHazardDao()

    @Provides
    fun provideSubscriberDao(db: AppDatabase): SubscriberDao = db.subscriberDao()

    @Provides
    fun provideForestDao(db: AppDatabase): ForestDao = db.forestDao()

    @Provides
    fun provideFireHotspotDao(db: AppDatabase): FireHotspotDao = db.fireHotspotDao()

    @Provides
    fun provideProviderStatusDao(db: AppDatabase): ProviderStatusDao = db.providerStatusDao()

    @Provides
    fun providePlannedRouteDao(db: AppDatabase): PlannedRouteDao = db.plannedRouteDao()

    @Provides
    fun provideActiveTripDao(db: AppDatabase): ActiveTripDao = db.activeTripDao()

    @Provides
    fun provideTrekTrackPointDao(db: AppDatabase): TrekTrackPointDao = db.trekTrackPointDao()
}
