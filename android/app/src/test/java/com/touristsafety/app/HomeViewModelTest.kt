package com.touristsafety.app

import com.touristsafety.app.core.model.Alert
import com.touristsafety.app.core.model.AlertCategory
import com.touristsafety.app.core.model.AlertSeverity
import com.touristsafety.app.core.model.Itinerary
import com.touristsafety.app.core.network.DetailedConnectivityState
import com.touristsafety.app.core.network.NetworkConnectivityMonitor
import com.touristsafety.app.core.network.NetworkStatus
import com.touristsafety.app.core.repository.AlertRepository
import com.touristsafety.app.core.repository.AuthRepository
import com.touristsafety.app.core.repository.DisasterRepository
import com.touristsafety.app.core.repository.EmergencyRepository
import com.touristsafety.app.core.repository.ItineraryRepository
import com.touristsafety.app.core.repository.ProviderStatusRepository
import com.touristsafety.app.core.repository.RouteRepository
import com.touristsafety.app.core.repository.WeatherRepository
import com.touristsafety.app.feature.home.HomeViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val itineraryRepository = mockk<ItineraryRepository>(relaxed = true)
    private val alertRepository = mockk<AlertRepository>(relaxed = true)
    private val weatherRepository = mockk<WeatherRepository>(relaxed = true)
    private val emergencyRepository = mockk<EmergencyRepository>(relaxed = true)
    private val providerStatusRepository = mockk<ProviderStatusRepository>(relaxed = true)
    private val disasterRepository = mockk<DisasterRepository>(relaxed = true)
    private val connectivityMonitor = mockk<NetworkConnectivityMonitor>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val routeRepository = mockk<RouteRepository>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { connectivityMonitor.state } returns MutableStateFlow(
            DetailedConnectivityState(status = NetworkStatus.BACKEND_REACHABLE)
        )
        coEvery { connectivityMonitor.probeBackend() } returns Unit
        every { disasterRepository.isSirenPlaying } returns MutableStateFlow(false)
        every { authRepository.getUserEmail() } returns "test@tourist.org"
        every { routeRepository.activeTripFlow } returns MutableStateFlow(null)
        every { routeRepository.completedTripsFlow } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads itineraries and alerts from Room into UI state`() = runTest(testDispatcher) {
        val sampleItinerary = Itinerary(
            id = "itin-1",
            title = "Rohtang Valley Ridge Trail",
            description = "Scenic trek",
            region = "Himachal",
            difficulty = "moderate",
            distanceKm = 14.5,
            estimatedDurationHours = 5.5,
            startLatitude = 32.2396,
            startLongitude = 77.1887,
            endLatitude = 32.2970,
            endLongitude = 77.2470,
            routeGeoJson = "{}"
        )
        val sampleAlert = Alert(
            id = "alert-1",
            title = "[SIMULATED] High Rain-Risk Advisory",
            description = "Rain along ridge",
            severity = AlertSeverity.MEDIUM,
            category = AlertCategory.WEATHER,
            sourceName = "Meteo",
            sourcePublishedAtEpochMs = System.currentTimeMillis() - 300000L,
            retrievedAtEpochMs = System.currentTimeMillis(),
            freshnessThresholdSeconds = 3600,
            isSimulation = true
        )

        every { itineraryRepository.getItineraries() } returns flowOf(listOf(sampleItinerary))
        every { alertRepository.getAlerts() } returns flowOf(listOf(sampleAlert))
        every { weatherRepository.getWeather(any(), any()) } returns flowOf(null)

        val viewModel = HomeViewModel(
            itineraryRepository,
            alertRepository,
            weatherRepository,
            emergencyRepository,
            providerStatusRepository,
            disasterRepository,
            connectivityMonitor,
            authRepository,
            routeRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Rohtang Valley Ridge Trail", state.selectedItinerary?.title)
        assertEquals(1, state.alerts.size)
        assertEquals("[SIMULATED] High Rain-Risk Advisory", state.alerts[0].title)
    }

    @Test
    fun `stopDisasterSiren delegates to disaster repository`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            itineraryRepository,
            alertRepository,
            weatherRepository,
            emergencyRepository,
            providerStatusRepository,
            disasterRepository,
            connectivityMonitor,
            authRepository,
            routeRepository
        )

        viewModel.stopDisasterSiren()
        verify(exactly = 1) { disasterRepository.stopDisasterSiren() }
    }
}

