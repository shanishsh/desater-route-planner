package com.touristsafety.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed interface LocationFixResult {
    data class Success(val location: Location) : LocationFixResult
    data object PermissionDenied : LocationFixResult
    data object LocationServicesDisabled : LocationFixResult
    data object Timeout : LocationFixResult
    data class Error(val message: String) : LocationFixResult
}

@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

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

    fun isLocationServicesEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        return suspendCancellableCoroutine { continuation ->
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentOrLastLocation(timeoutMs: Long = 8000L): LocationFixResult {
        if (!hasLocationPermission()) {
            return LocationFixResult.PermissionDenied
        }

        if (!isLocationServicesEnabled()) {
            return LocationFixResult.LocationServicesDisabled
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        return try {
            val freshLocation = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cts = CancellationTokenSource()
                    cont.invokeOnCancellation { cts.cancel() }

                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                }
            }

            if (freshLocation != null && isValidCoordinate(freshLocation.latitude, freshLocation.longitude)) {
                return LocationFixResult.Success(freshLocation)
            }

            // Fallback to last known fix
            val lastLoc = getLastKnownLocation()
            if (lastLoc != null && isValidCoordinate(lastLoc.latitude, lastLoc.longitude)) {
                return LocationFixResult.Success(lastLoc)
            }

            if (freshLocation == null) {
                LocationFixResult.Timeout
            } else {
                LocationFixResult.Error("Could not obtain a valid GPS fix from location hardware.")
            }
        } catch (e: Exception) {
            val fallback = getLastKnownLocation()
            if (fallback != null && isValidCoordinate(fallback.latitude, fallback.longitude)) {
                LocationFixResult.Success(fallback)
            } else {
                LocationFixResult.Error(e.message ?: "Failed to get location fix")
            }
        }
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat != 0.0 && lon != 0.0 && lat in -90.0..90.0 && lon in -180.0..180.0
    }
}
