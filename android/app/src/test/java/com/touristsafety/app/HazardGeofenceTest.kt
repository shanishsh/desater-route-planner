package com.touristsafety.app

import com.touristsafety.app.core.database.dao.HazardZoneDao
import com.touristsafety.app.core.database.dao.TriggeredHazardDao
import com.touristsafety.app.core.database.entity.HazardZoneEntity
import com.touristsafety.app.core.database.entity.TriggeredHazardWarningEntity
import com.touristsafety.app.core.location.HazardGeofenceManager
import com.touristsafety.app.core.notification.DisasterNotificationManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HazardGeofenceTest {

    private val hazardZoneDao = mockk<HazardZoneDao>(relaxed = true)
    private val triggeredHazardDao = mockk<TriggeredHazardDao>(relaxed = true)
    private val notificationManager = mockk<DisasterNotificationManager>(relaxed = true)

    private lateinit var geofenceManager: HazardGeofenceManager

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any<String>()) } returns 0
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0

        geofenceManager = HazardGeofenceManager(
            context = mockk(relaxed = true),
            hazardZoneDao = hazardZoneDao,
            triggeredHazardDao = triggeredHazardDao,
            notificationManager = notificationManager
        )
    }

    @Test
    fun testHaversineDistanceAccuracy() {
        // Distance between Manali (32.2396, 77.1887) and Solang Valley (32.3167, 77.1578) is approx ~9.0 km
        val distance = geofenceManager.calculateHaversineDistance(
            32.2396, 77.1887,
            32.3167, 77.1578
        )
        // Check within 8.5 km to 9.5 km
        assertTrue("Distance should be approx 9 km but was $distance", distance in 8500.0..9500.0)
    }

    @Test
    fun testGeofenceTriggerWhenInsideHazardZone() = runTest {
        val testZone = HazardZoneEntity(
            alertId = "zone-flash-flood-1",
            title = "Flash Flood Warning",
            description = "Rapid water surge",
            hazardCategory = "flash_flood",
            severity = "CRITICAL",
            centerLatitude = 32.2600,
            centerLongitude = 77.2100,
            radiusMeters = 3000f,
            requiredProtectiveInstructions = "Evacuate riverbank",
            issuingAuthority = "HPSDMA",
            publishedAtEpochMs = 1000L,
            receivedAtEpochMs = 1000L,
            downloadedAtEpochMs = 1000L,
            isSimulated = true,
            status = "active"
        )

        coEvery { hazardZoneDao.getAllActiveList() } returns listOf(testZone)
        coEvery { triggeredHazardDao.hasBeenTriggered("zone-flash-flood-1") } returns false

        // User is at (32.2610, 77.2110) - ~150 meters from center, well within 3000m radius
        geofenceManager.evaluateLocationAgainstCachedHazards(32.2610, 77.2110)

        // Verifies record inserted into Room for deduplication
        coVerify(exactly = 1) {
            triggeredHazardDao.insert(match { it.alertId == "zone-flash-flood-1" })
        }

        // Verifies local notification is displayed with cached warning flag
        verify(exactly = 1) {
            notificationManager.showDisasterNotification(
                alertId = "zone-flash-flood-1",
                title = "Flash Flood Warning",
                instructions = "Evacuate riverbank",
                category = "flash_flood",
                issuingAuthority = "HPSDMA",
                isCached = true,
                isSimulated = true,
                isTestAlarm = false
            )
        }
    }

    @Test
    fun testGeofenceDeduplicationPreventsRepetitiveAlerts() = runTest {
        val testZone = HazardZoneEntity(
            alertId = "zone-landslide-2",
            title = "Landslide Warning",
            description = "Debris flow",
            hazardCategory = "landslide",
            severity = "SEVERE",
            centerLatitude = 32.2800,
            centerLongitude = 77.2300,
            radiusMeters = 2000f,
            requiredProtectiveInstructions = "Halt vehicle",
            issuingAuthority = "BRO",
            publishedAtEpochMs = 1000L,
            receivedAtEpochMs = 1000L,
            downloadedAtEpochMs = 1000L,
            isSimulated = true,
            status = "active"
        )

        coEvery { hazardZoneDao.getAllActiveList() } returns listOf(testZone)
        // Already triggered previously!
        coEvery { triggeredHazardDao.hasBeenTriggered("zone-landslide-2") } returns true

        // User is inside the zone
        geofenceManager.evaluateLocationAgainstCachedHazards(32.2805, 77.2305)

        // Must NOT insert again and must NOT notify again
        coVerify(exactly = 0) { triggeredHazardDao.insert(any()) }
        verify(exactly = 0) { notificationManager.showDisasterNotification(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun testOutsideHazardZoneDoesNotTrigger() = runTest {
        val testZone = HazardZoneEntity(
            alertId = "zone-trail-closure-3",
            title = "Ridge Trail Closure",
            description = "High winds",
            hazardCategory = "trail_closure",
            severity = "WARNING",
            centerLatitude = 32.3500,
            centerLongitude = 77.2500,
            radiusMeters = 1000f,
            requiredProtectiveInstructions = "Return to base",
            issuingAuthority = "Rangers",
            publishedAtEpochMs = 1000L,
            receivedAtEpochMs = 1000L,
            downloadedAtEpochMs = 1000L,
            isSimulated = true,
            status = "active"
        )

        coEvery { hazardZoneDao.getAllActiveList() } returns listOf(testZone)
        coEvery { triggeredHazardDao.hasBeenTriggered(any()) } returns false

        // User is far outside (>15 km away at Manali town 32.2396, 77.1887)
        geofenceManager.evaluateLocationAgainstCachedHazards(32.2396, 77.1887)

        coVerify(exactly = 0) { triggeredHazardDao.insert(any()) }
        verify(exactly = 0) { notificationManager.showDisasterNotification(any(), any(), any(), any(), any(), any(), any(), any()) }
    }
}
