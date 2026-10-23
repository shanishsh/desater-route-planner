package com.touristsafety.app

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.touristsafety.app.core.location.DeviceLocationRepository
import com.touristsafety.app.core.location.LocationError
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLocationRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val locationManager = mockk<LocationManager>(relaxed = true)
    private val fusedLocationClient = mockk<FusedLocationProviderClient>(relaxed = true)

    private lateinit var repository: DeviceLocationRepository

    @Before
    fun setup() {
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
        repository = DeviceLocationRepository(context, fusedLocationClient)
    }

    @Test
    fun `getCurrentLocationFix fails with PERMISSION_DENIED when permission not granted`() = runTest {
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_DENIED

        val result = repository.getCurrentLocationFix()

        assertTrue(result.isFailure)
        val state = repository.locationState.value
        assertEquals(LocationError.PERMISSION_DENIED, state.error)
        assertFalse(state.isLocating)
    }

    @Test
    fun `getCurrentLocationFix fails with GPS_DISABLED when providers disabled`() = runTest {
        // Permissions granted
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
        // GPS disabled
        every { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns false
        every { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } returns false

        val result = repository.getCurrentLocationFix()

        assertTrue(result.isFailure)
        val state = repository.locationState.value
        assertEquals(LocationError.GPS_DISABLED, state.error)
    }

    @Test
    fun `getCurrentLocationFix succeeds and exposes valid location and accuracy`() = runTest {
        // Permissions granted
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
        // GPS enabled
        every { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns true

        val mockLocation = mockk<Location>(relaxed = true) {
            every { latitude } returns 32.2396
            every { longitude } returns 77.1887
            every { hasAccuracy() } returns true
            every { accuracy } returns 8.5f
            every { time } returns System.currentTimeMillis()
        }

        val mockTask = mockk<Task<Location>>()
        every { mockTask.addOnSuccessListener(any<OnSuccessListener<Location>>()) } answers {
            firstArg<OnSuccessListener<Location>>().onSuccess(mockLocation)
            mockTask
        }
        every { mockTask.addOnFailureListener(any<OnFailureListener>()) } returns mockTask
        every { fusedLocationClient.getCurrentLocation(any<Int>(), any()) } returns mockTask

        val result = repository.getCurrentLocationFix()

        assertTrue(result.isSuccess)
        val location = result.getOrNull()
        assertNotNull(location)
        assertEquals(32.2396, location!!.latitude, 0.0001)
        assertEquals(77.1887, location.longitude, 0.0001)

        val state = repository.locationState.value
        assertEquals(32.2396, state.latitude!!, 0.0001)
        assertEquals(77.1887, state.longitude!!, 0.0001)
        assertEquals(8.5f, state.accuracyMeters!!, 0.01f)
        assertTrue(state.isFresh)
        assertNull(state.error)
    }

    @Test
    fun `isValidCoordinate correctly validates boundaries and rejects 0,0`() {
        assertTrue(repository.isValidCoordinate(32.2396, 77.1887))
        assertTrue(repository.isValidCoordinate(-33.8688, 151.2093))
        assertFalse(repository.isValidCoordinate(0.0, 0.0)) // Uninitialized hardware state
        assertFalse(repository.isValidCoordinate(95.0, 77.0)) // Latitude out of bounds
        assertFalse(repository.isValidCoordinate(32.0, 190.0)) // Longitude out of bounds
    }

    @Test
    fun `getCurrentLocationFix rejects invalid 0,0 coordinate fix`() = runTest {
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } returns true

        val mockLocation = mockk<Location>(relaxed = true) {
            every { latitude } returns 0.0
            every { longitude } returns 0.0
            every { time } returns System.currentTimeMillis()
        }

        val mockTask = mockk<Task<Location>>()
        every { mockTask.addOnSuccessListener(any<OnSuccessListener<Location>>()) } answers {
            firstArg<OnSuccessListener<Location>>().onSuccess(mockLocation)
            mockTask
        }
        every { mockTask.addOnFailureListener(any<OnFailureListener>()) } returns mockTask
        every { fusedLocationClient.getCurrentLocation(any<Int>(), any()) } returns mockTask

        val result = repository.getCurrentLocationFix()

        assertTrue(result.isFailure)
        assertEquals(LocationError.INVALID_COORDINATES, repository.locationState.value.error)
    }
}
