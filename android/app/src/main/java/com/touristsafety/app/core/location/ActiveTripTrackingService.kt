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
import com.touristsafety.app.core.database.dao.HazardZoneDao
import com.touristsafety.app.core.database.entity.HazardZoneEntity
import com.touristsafety.app.core.network.dto.RouteWaypointConditionDto
import com.touristsafety.app.core.notification.DisasterNotificationManager
import com.touristsafety.app.core.repository.RouteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class ActiveTripTrackingService : Service() {

    @Inject
    lateinit var routeRepository: RouteRepository

    @Inject
    lateinit var hazardZoneDao: HazardZoneDao

    @Inject
    lateinit var disasterNotificationManager: DisasterNotificationManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentTripId: String = ""
    private var currentRouteId: String = ""
    private var destinationName: String = "Destination"
    private var cachedWaypoints: List<RouteWaypointConditionDto> = emptyList()

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "TripTrackingService"
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "trip_safety_tracking_v1"
        const val CHANNEL_NAME = "Active Trip Route Safety"

        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val EXTRA_DEST_NAME = "extra_dest_name"
        const val ACTION_START = "ACTION_START_TRACKING"
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
                    onLocationChanged(location)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        currentTripId = intent?.getStringExtra(EXTRA_TRIP_ID) ?: ""
        currentRouteId = intent?.getStringExtra(EXTRA_ROUTE_ID) ?: ""
        destinationName = intent?.getStringExtra(EXTRA_DEST_NAME) ?: "Destination"

        startForegroundWithNotification("Active Trip Monitoring Started", "Tracking route to $destinationName")

        loadRouteWaypoints()
        startLocationUpdates()

        return START_STICKY
    }

    private fun loadRouteWaypoints() {
        serviceScope.launch {
            val route = routeRepository.getRouteById(currentRouteId)
            if (route != null) {
                try {
                    cachedWaypoints = json.decodeFromString(route.conditionsJson)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed parsing cached waypoints: ${e.message}")
                }
            }
        }
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
            Log.e(TAG, "Location permission missing for tracking service: ${e.message}")
        }
    }

    private fun onLocationChanged(location: Location) {
        serviceScope.launch {
            val speedKmh = location.speed * 3.6
            val result = routeRepository.recordTripProgress(
                tripId = currentTripId,
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = speedKmh
            )

            val (statusText, subText) = if (result.isSuccess) {
                val prog = result.getOrNull()!!
                val hazardPart = if (prog.distance_to_nearest_hazard_km != null && prog.distance_to_nearest_hazard_km <= 4.0) {
                    "⚠️ Hazard in ${prog.distance_to_nearest_hazard_km}km"
                } else "Route Normal"

                val wpName = prog.next_waypoint?.name ?: "Next Pass"
                val weather = prog.next_waypoint?.weather?.let { "${it.temperature_c.toInt()}°C ${it.weather_description}" } ?: ""
                Pair("To $destinationName • ${prog.progress_pct.toInt()}% Complete", "$wpName: $weather | $hazardPart")
            } else {
                Pair("En Route to $destinationName (Offline)", "Monitoring offline hazard corridor")
            }

            updateNotification(statusText, subText)

            // Local offline hazard proximity check
            checkOfflineHazardProximity(location)
        }
    }

    private suspend fun checkOfflineHazardProximity(location: Location) {
        val activeZones = hazardZoneDao.getAllActiveList()
        for (zone in activeZones) {
            val distanceM = computeDistanceMeters(
                location.latitude, location.longitude,
                zone.centerLatitude, zone.centerLongitude
            )
            // Trigger emergency warning if tourist enters or is within 1.5 km of hazard
            if (distanceM <= (zone.radiusMeters + 1500.0)) {
                disasterNotificationManager.showDisasterNotification(
                    alertId = zone.alertId,
                    title = "Hazard Zone Near Route: ${zone.title}",
                    instructions = "${zone.requiredProtectiveInstructions}\nDistance: ${(distanceM / 1000.0).format(1)} km",
                    category = zone.hazardCategory,
                    issuingAuthority = zone.issuingAuthority,
                    isCached = true
                )
                break
            }
        }
    }

    private fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun Double.format(digits: Int) = String.format(java.util.Locale.US, "%.${digits}f", this)

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
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
