package com.touristsafety.app.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.touristsafety.app.core.database.dao.HazardZoneDao
import com.touristsafety.app.core.database.dao.TriggeredHazardDao
import com.touristsafety.app.core.database.entity.HazardZoneEntity
import com.touristsafety.app.core.database.entity.TriggeredHazardWarningEntity
import com.touristsafety.app.core.notification.DisasterNotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class HazardGeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hazardZoneDao: HazardZoneDao,
    private val triggeredHazardDao: TriggeredHazardDao,
    private val notificationManager: DisasterNotificationManager
) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _lastKnownLocation = MutableStateFlow<Location?>(null)
    val lastKnownLocation: StateFlow<Location?> = _lastKnownLocation.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        if (locationManager == null || _isMonitoring.value) return

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    15000L, // 15 seconds battery conscious interval
                    25f,    // 25 meters minimum movement
                    this
                )
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    20000L,
                    50f,
                    this
                )
            }
            _isMonitoring.value = true
            Log.i("HazardGeofenceManager", "Offline hazard route monitoring STARTED on-device.")
        } catch (e: SecurityException) {
            Log.e("HazardGeofenceManager", "Location permission missing for hazard monitoring", e)
            _isMonitoring.value = false
        }
    }

    fun stopMonitoring() {
        try {
            locationManager?.removeUpdates(this)
        } catch (e: Exception) {
            Log.w("HazardGeofenceManager", "Error stopping location updates", e)
        }
        _isMonitoring.value = false
        Log.i("HazardGeofenceManager", "Offline hazard route monitoring STOPPED.")
    }

    override fun onLocationChanged(location: Location) {
        _lastKnownLocation.value = location
        // Evaluate distance to cached hazard zones on-device
        scope.launch {
            evaluateLocationAgainstCachedHazards(location.latitude, location.longitude)
        }
    }

    suspend fun evaluateLocationAgainstCachedHazards(lat: Double, lng: Double) {
        val activeZones = hazardZoneDao.getAllActiveList()
        val now = System.currentTimeMillis()

        for (zone in activeZones) {
            val distanceMeters = calculateHaversineDistance(
                lat, lng,
                zone.centerLatitude, zone.centerLongitude
            )

            if (distanceMeters <= zone.radiusMeters) {
                // Check if already triggered (Deduplication across restarts)
                val alreadyTriggered = triggeredHazardDao.hasBeenTriggered(zone.alertId)
                if (!alreadyTriggered) {
                    // Record in Room DB to prevent duplicate alerts
                    triggeredHazardDao.insert(
                        TriggeredHazardWarningEntity(
                            alertId = zone.alertId,
                            triggeredTimestampEpochMs = now,
                            dismissed = false
                        )
                    )

                    // Fire local offline notification
                    notificationManager.showDisasterNotification(
                        alertId = zone.alertId,
                        title = zone.title,
                        instructions = zone.requiredProtectiveInstructions,
                        category = zone.hazardCategory,
                        issuingAuthority = zone.issuingAuthority,
                        isCached = true,
                        isSimulated = zone.isSimulated,
                        isTestAlarm = false
                    )

                    Log.w(
                        "HazardGeofenceManager",
                        "GEOFENCE ENTERED: Zone ${zone.title} (${distanceMeters.toInt()}m away <= ${zone.radiusMeters.toInt()}m). Triggered offline warning."
                    )
                }
            }
        }
    }

    /**
     * Calculates the great-circle distance between two points in meters using the Haversine formula.
     */
    fun calculateHaversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadiusMeters = 6371000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

