package com.touristsafety.app.core.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.*
import com.touristsafety.app.MainActivity
import com.touristsafety.app.R
import com.touristsafety.app.core.database.dao.ActiveTripDao
import com.touristsafety.app.core.database.dao.TrekTrackPointDao
import com.touristsafety.app.core.database.entity.TrekTrackPointEntity
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class ActiveTripTrackingService : Service() {

    @Inject
    lateinit var routeRepository: RouteRepository

    @Inject
    lateinit var trekTrackPointDao: TrekTrackPointDao

    @Inject
    lateinit var activeTripDao: ActiveTripDao

    @Inject
    lateinit var hazardAlertEngine: HazardAlertEngine

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentTripId: String = ""
    private var currentRouteId: String = ""
    private var destinationName: String = "Destination"

    private var lastAcceptedLat: Double? = null
    private var lastAcceptedLon: Double? = null
    private var lastAcceptedEpochMs: Long? = null

    private var cumulativeDistanceMeters: Double = 0.0
    private var startTimeEpochMs: Long = System.currentTimeMillis()
    private var isPaused: Boolean = false
    private var totalPausedDurationMs: Long = 0L
    private var pauseStartEpochMs: Long? = null

    private var durationTimerJob: Job? = null

    companion object {
        private const val TAG = "TripTrackingService"
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "trip_safety_tracking_v1"
        const val CHANNEL_NAME = "Active Trip Route Safety"

        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val EXTRA_DEST_NAME = "extra_dest_name"

        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_PAUSE = "ACTION_PAUSE_TRACKING"
        const val ACTION_RESUME = "ACTION_RESUME_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"

        fun start(context: Context, tripId: String, routeId: String, destinationName: String) {
            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_ID, tripId)
                putExtra(EXTRA_ROUTE_ID, routeId)
                putExtra(EXTRA_DEST_NAME, destinationName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ActiveTripTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    onLocationReceived(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                handlePause()
                return START_STICKY
            }
            ACTION_RESUME -> {
                handleResume()
                return START_STICKY
            }
            else -> {
                currentTripId = intent?.getStringExtra(EXTRA_TRIP_ID) ?: ""
                currentRouteId = intent?.getStringExtra(EXTRA_ROUTE_ID) ?: ""
                destinationName = intent?.getStringExtra(EXTRA_DEST_NAME) ?: "Destination"

                startTimeEpochMs = System.currentTimeMillis()
                startForegroundWithNotification("Active Trek Started", "Monitoring route to $destinationName")

                loadTripStateAndResume()
                startLocationUpdates()
                startDurationTimer()
                return START_STICKY
            }
        }
    }

    private fun loadTripStateAndResume() {
        serviceScope.launch {
            val existingTrip = activeTripDao.getTripById(currentTripId)
            if (existingTrip != null) {
                cumulativeDistanceMeters = existingTrip.traveledDistanceMeters
                isPaused = existingTrip.isPaused
                if (existingTrip.lastLatitude != null && existingTrip.lastLongitude != null) {
                    lastAcceptedLat = existingTrip.lastLatitude
                    lastAcceptedLon = existingTrip.lastLongitude
                    lastAcceptedEpochMs = existingTrip.lastSyncedAt
                }
            }
        }
    }

    private fun handlePause() {
        if (!isPaused) {
            isPaused = true
            pauseStartEpochMs = System.currentTimeMillis()
            updateNotification("Trek Paused", "Distance accumulation suspended • To $destinationName")
            serviceScope.launch {
                val existing = activeTripDao.getTripById(currentTripId)
                if (existing != null) {
                    activeTripDao.insertOrUpdate(
                        existing.copy(
                            isPaused = true,
                            pausedAtEpochMs = pauseStartEpochMs
                        )
                    )
                }
            }
        }
    }

    private fun handleResume() {
        if (isPaused) {
            isPaused = false
            pauseStartEpochMs?.let {
                totalPausedDurationMs += (System.currentTimeMillis() - it)
            }
            pauseStartEpochMs = null
            updateNotification("Trek Active", "Tracking route to $destinationName")
            serviceScope.launch {
                val existing = activeTripDao.getTripById(currentTripId)
                if (existing != null) {
                    activeTripDao.insertOrUpdate(
                        existing.copy(
                            isPaused = false,
                            pausedAtEpochMs = null
                        )
                    )
                }
            }
        }
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = serviceScope.launch {
            while (isActive) {
                delay(5000L)
                updateTripEntityMetrics()
            }
        }
    }

    private fun getElapsedSeconds(): Long {
        val now = System.currentTimeMillis()
        val currentPauseExtra = if (isPaused && pauseStartEpochMs != null) (now - pauseStartEpochMs!!) else 0L
        val activeMs = (now - startTimeEpochMs) - totalPausedDurationMs - currentPauseExtra
        return maxOf(0L, activeMs / 1000L)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing: ${e.message}")
        }
    }

    private fun onLocationReceived(location: Location) {
        serviceScope.launch {
            val now = System.currentTimeMillis()
            val speedKmh = location.speed * 3.6

            // Apply strict GPS noise, drift, and multipath filtering
            val filterResult = GpsFilter.filter(
                lastAcceptedLat = lastAcceptedLat,
                lastAcceptedLon = lastAcceptedLon,
                lastAcceptedEpochMs = lastAcceptedEpochMs,
                lat = location.latitude,
                lon = location.longitude,
                accuracyMeters = location.accuracy,
                speedKmh = speedKmh,
                timestampEpochMs = now
            )

            val isAccepted = filterResult.isAcceptedForDistance && !isPaused

            if (isAccepted) {
                cumulativeDistanceMeters += filterResult.deltaMeters
                lastAcceptedLat = location.latitude
                lastAcceptedLon = location.longitude
                lastAcceptedEpochMs = now
            }

            // Save individual breadcrumb locally in Room
            trekTrackPointDao.insert(
                TrekTrackPointEntity(
                    tripId = currentTripId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = location.altitude,
                    accuracyMeters = location.accuracy,
                    speedKmh = speedKmh,
                    recordedAtEpochMs = now,
                    isAcceptedForDistance = isAccepted,
                    isSynced = false
                )
            )

            // Update ActiveTripEntity
            updateTripEntityMetrics(location)

            // Check hazard proximity
            hazardAlertEngine.evaluatePosition(location.latitude, location.longitude, isInBackground = true)

            // Trigger sync for unsynced points if network is available
            syncPendingPointsBatch()
        }
    }

    private suspend fun updateTripEntityMetrics(latestLocation: Location? = null) {
        val existing = activeTripDao.getTripById(currentTripId) ?: return
        val elapsedSec = getElapsedSeconds()

        val updated = existing.copy(
            lastLatitude = latestLocation?.latitude ?: existing.lastLatitude,
            lastLongitude = latestLocation?.longitude ?: existing.lastLongitude,
            traveledDistanceMeters = cumulativeDistanceMeters,
            elapsedDurationSeconds = elapsedSec,
            isPaused = isPaused,
            lastSyncedAt = System.currentTimeMillis()
        )
        activeTripDao.insertOrUpdate(updated)

        val kmStr = String.format(java.util.Locale.US, "%.2f km", cumulativeDistanceMeters / 1000.0)
        val timeStr = formatDuration(elapsedSec)
        val gpsPart = if (latestLocation != null) "GPS: ±${latestLocation.accuracy.toInt()}m" else "GPS Active"
        val statusPart = if (isPaused) "PAUSED" else "ACTIVE"

        updateNotification(
            title = "$statusPart • $kmStr in $timeStr",
            content = "$gpsPart • En route to $destinationName"
        )
    }

    private suspend fun syncPendingPointsBatch() {
        try {
            val unsynced = trekTrackPointDao.getUnsyncedPoints(currentTripId, limit = 50)
            if (unsynced.isNotEmpty()) {
                val syncResult = routeRepository.syncTrackPointsEntitiesBatch(currentTripId, unsynced)
                if (syncResult.isSuccess) {
                    trekTrackPointDao.markPointsSynced(unsynced.map { it.id })
                }
            }
        } catch (t: Throwable) {
            // Offline resilience: Silently retain in Room queue until next update
            Log.d(TAG, "Batch sync postponed: ${t.message}")
        }
    }

    private fun formatDuration(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing location and corridor safety updates during trip"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification(title: String, content: String) {
        val notification = buildNotification(title, content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, content: String) {
        val notification = buildNotification(title, content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        durationTimerJob?.cancel()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
