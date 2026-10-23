package com.touristsafety.app

import com.touristsafety.app.core.database.dao.ItineraryDao
import com.touristsafety.app.core.database.entity.ItineraryEntity
import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.repository.ItineraryRepository
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class OfflineRepositoryTest {

    private val apiService = mockk<ApiService>()
    private val itineraryDao = mockk<ItineraryDao>(relaxed = true)
    private lateinit var repository: ItineraryRepository

    @Before
    fun setup() {
        repository = ItineraryRepository(apiService, itineraryDao)
    }

    @Test
    fun `getItineraries returns local Room data as single source of truth`() = runTest {
        val cachedEntities = listOf(
            ItineraryEntity(
                id = "itin-1",
                title = "Cached Alpine Trail",
                description = "Saved trail in Room DB",
                region = "Himalayas",
                difficulty = "moderate",
                distanceKm = 10.0,
                estimatedDurationHours = 3.5,
                startLatitude = 32.24,
                startLongitude = 77.19,
                endLatitude = 32.26,
                endLongitude = 77.21,
                routeGeoJson = "{}",
                isSavedOffline = true,
                lastSyncTimestamp = 1700000000000L
            )
        )

        every { itineraryDao.getAllItineraries() } returns flowOf(cachedEntities)

        val result = repository.getItineraries().first()

        assertEquals(1, result.size)
        assertEquals("Cached Alpine Trail", result[0].title)
        assertTrue(result[0].isSavedOffline)
    }

    @Test
    fun `refreshItineraries failure retains existing data and reports error`() = runTest {
        coEvery { apiService.getItineraries() } throws java.io.IOException("Network unreachable")

        val result = repository.refreshItineraries()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { itineraryDao.insertItinerary(any()) }
    }
}

