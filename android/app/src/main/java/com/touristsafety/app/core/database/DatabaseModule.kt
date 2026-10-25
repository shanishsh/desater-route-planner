package com.touristsafety.app.core.database

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tourist_safety.db"
        )
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
}

