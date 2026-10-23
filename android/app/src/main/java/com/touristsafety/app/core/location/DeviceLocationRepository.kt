package com.touristsafety.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

enum class LocationError {
    PERMISSION_DENIED,
    GPS_DISABLED,
    TIMEOUT,
    STALE_FIX,
    INVALID_COORDINATES,
    UNAVAILABLE
}

data class DeviceLocationState(
    val location: Location? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val timestampMs: Long = 0L,
    val isFresh: Boolean = false,
    val isMock: Boolean = false,
    val isLocating: Boolean = false,
    val error: LocationError? = null,
    val errorMessage: String? = null
)

@Singleton
class DeviceLocationRepository(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context,
        LocationServices.getFusedLocationProviderClient(context)
    )

    companion object {
        private const val TAG = "DeviceLocationRepo"
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val MAX_ACCEPTABLE_AGE_MS = 60_000L // 1 minute max age for fresh fix
        const val DESIRED_ACCURACY_METERS = 100.0f
    }

    private val _locationState = MutableStateFlow(DeviceLocationState())
    val locationState: StateFlow<DeviceLocationState> = _locationState.asStateFlow()

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        if (lat < -90.0 || lat > 90.0) return false
        if (lon < -180.0 || lon > 180.0) return false
        // Disallow exact 0.0, 0.0 which indicates uninitialized hardware state
        if (lat == 0.0 && lon == 0.0) return false
        return true
    }

    fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    /**
     * Obtains a fresh, high-accuracy device location fix with bounded timeout and validation.
     * Implements strict sequence: Permission Check -> GPS Enabled Check -> Fresh Acquisition -> Validation.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationFix(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxAgeMs: Long = MAX_ACCEPTABLE_AGE_MS
    ): Result<Location> {
        // 1. Permission Check
        if (!hasLocationPermission()) {
            val errState = DeviceLocationState(
                isLocating = false,
                error = LocationError.PERMISSION_DENIED,
                errorMessage = "Location permission not granted. Please enable location access."
            )
            _locationState.value = errState
            return Result.failure(SecurityException("Location permission denied"))
        }

        // 2. Hardware / Service Enabled Check
        if (!isGpsEnabled()) {
            val errState = DeviceLocationState(
                isLocating = false,
                error = LocationError.GPS_DISABLED,
                errorMessage = "Device location services are disabled. Please turn on GPS."
            )
            _locationState.value = errState
            return Result.failure(IllegalStateException("GPS services disabled"))
        }

        _locationState.value = _locationState.value.copy(isLocating = true, error = null, errorMessage = null)

        val cts = CancellationTokenSource()

        val locationFix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val priority = if (hasFineLocationPermission()) {
                    Priority.PRIORITY_HIGH_ACCURACY
                } else {
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY
                }

                fusedLocationClient.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            cont.resume(loc)
                        } else {
                            // If getCurrentLocation is null, attempt lastLocation fallback
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { lastLoc ->
                                    cont.resume(lastLoc)
                                }
                                .addOnFailureListener {
                                    cont.resume(null)
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "FusedLocation getCurrentLocation failed: ${e.message}")
                        // Attempt lastLocation fallback
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { lastLoc ->
                                cont.resume(lastLoc)
                            }
                            .addOnFailureListener {
                                cont.resume(null)
                            }
                    }

                cont.invokeOnCancellation {
                    cts.cancel()
                }
            }
        }

        if (locationFix == null) {
            val err = LocationError.TIMEOUT
            val msg = "Location request timed out after ${timeoutMs / 1000}s. Please check GPS reception."
            _locationState.value = _locationState.value.copy(
                isLocating = false,
                error = err,
                errorMessage = msg
            )
            return Result.failure(Exception(msg))
        }

        // 3. Validation: Coordinates & Freshness
        if (!isValidCoordinate(locationFix.latitude, locationFix.longitude)) {
            val err = LocationError.INVALID_COORDINATES
            val msg = "Received invalid GPS coordinates (${locationFix.latitude}, ${locationFix.longitude})"
            _locationState.value = _locationState.value.copy(
                isLocating = false,
                error = err,
                errorMessage = msg
            )
            return Result.failure(IllegalArgumentException(msg))
        }

        val now = System.currentTimeMillis()
        val fixAge = now - locationFix.time
        val isFresh = fixAge in 0..maxAgeMs
        val isMock = isMockLocation(locationFix)

        val successState = DeviceLocationState(
            location = locationFix,
            latitude = locationFix.latitude,
            longitude = locationFix.longitude,
            accuracyMeters = if (locationFix.hasAccuracy()) locationFix.accuracy else null,
            timestampMs = locationFix.time,
            isFresh = isFresh,
            isMock = isMock,
            isLocating = false,
            error = if (!isFresh) LocationError.STALE_FIX else null,
            errorMessage = if (!isFresh) "Fix is ${fixAge / 1000}s old (last known location)" else null
        )

        _locationState.value = successState
        Log.i(TAG, "Acquired real GPS fix: ${locationFix.latitude}, ${locationFix.longitude} (accuracy: ${locationFix.accuracy}m, fresh: $isFresh, mock: $isMock)")

        return Result.success(locationFix)
    }

    /**
     * Flow of continuous real-time location updates for active trip tracking.
     */
    @SuppressLint("MissingPermission")
    fun getLocationUpdates(
        intervalMs: Long = 5_000L,
        minDistanceMeters: Float = 10f
    ): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission denied"))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(minDistanceMeters)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    if (isValidCoordinate(loc.latitude, loc.longitude)) {
                        val isMock = isMockLocation(loc)
                        _locationState.value = DeviceLocationState(
                            location = loc,
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                            timestampMs = loc.time,
                            isFresh = true,
                            isMock = isMock,
                            isLocating = false
                        )
                        trySend(loc)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, null)
        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }
}
