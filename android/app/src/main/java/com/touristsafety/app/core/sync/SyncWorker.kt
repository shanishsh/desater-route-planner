package com.touristsafety.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.touristsafety.app.core.repository.AlertRepository
import com.touristsafety.app.core.repository.EmergencyRepository
import com.touristsafety.app.core.repository.ItineraryRepository
import com.touristsafety.app.core.repository.RouteRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val itineraryRepository: ItineraryRepository,
    private val alertRepository: AlertRepository,
    private val emergencyRepository: EmergencyRepository,
    private val routeRepository: RouteRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (runAttemptCount >= 4) {
            return Result.failure()
        }
        return try {
            // Synchronize active trek progress if currently trekking
            routeRepository.syncActiveTrip()

            val itinResult = itineraryRepository.refreshItineraries()
            val alertResult = alertRepository.refreshAlerts()
            val emergencyResult = emergencyRepository.refreshMapPoints()

            if (itinResult.isSuccess && alertResult.isSuccess && emergencyResult.isSuccess) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
