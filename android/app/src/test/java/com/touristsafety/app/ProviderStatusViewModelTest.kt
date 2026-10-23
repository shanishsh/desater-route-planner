package com.touristsafety.app

import com.touristsafety.app.core.model.ProviderStatus
import com.touristsafety.app.core.network.DetailedConnectivityState
import com.touristsafety.app.core.network.NetworkConnectivityMonitor
import com.touristsafety.app.core.network.NetworkStatus
import com.touristsafety.app.core.network.ServerConfigManager
import com.touristsafety.app.core.network.TelephonyContextManager
import com.touristsafety.app.core.network.TelephonyCoverageState
import com.touristsafety.app.core.repository.ProviderStatusRepository
import com.touristsafety.app.feature.providers.ProviderStatusViewModel
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
class ProviderStatusViewModelTest {

    private val providerStatusRepository = mockk<ProviderStatusRepository>(relaxed = true)
    private val routeRepository = mockk<com.touristsafety.app.core.repository.RouteRepository>(relaxed = true)
    private val connectivityMonitor = mockk<NetworkConnectivityMonitor>(relaxed = true)
    private val telephonyManager = mockk<TelephonyContextManager>(relaxed = true)
    private val serverConfigManager = mockk<ServerConfigManager>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { serverConfigManager.getCurrentUrl() } returns "http://172.168.10.238:8000/api/v1/"
        every { serverConfigManager.baseUrl } returns MutableStateFlow("http://172.168.10.238:8000/api/v1/")
        every { telephonyManager.getLocalCoverageState() } returns TelephonyCoverageState(
            networkType = "4G LTE",
            carrierName = "TestCarrier",
            signalLevel = 3,
            isEmergencyOnly = false
        )
        every { connectivityMonitor.state } returns MutableStateFlow(
            DetailedConnectivityState(status = NetworkStatus.BACKEND_REACHABLE, backendPingMs = 15L)
        )
        every { routeRepository.activeRouteFlow } returns MutableStateFlow(null)
        coEvery { connectivityMonitor.probeBackend() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads provider statuses and sets connectivity state`() = runTest(testDispatcher) {
        val sampleStatuses = listOf(
            ProviderStatus(
                providerId = "open_meteo",
                name = "Open-Meteo Weather API",
                category = "weather",
                isConfigured = true,
                status = "HEALTHY",
                lastAttemptAt = "2026-10-23T12:00:00Z",
                lastSuccessAt = "2026-10-23T12:00:00Z",
                lastHttpStatus = 200,
                recordCount = 1,
                dataState = "LIVE",
                isStale = false,
                diagnosticMessage = "Operational",
                attribution = "Open-Meteo",
                sourceUrl = "https://open-meteo.com",
                registrationUrl = ""
            )
        )

        every { providerStatusRepository.getProviderStatuses() } returns flowOf(sampleStatuses)

        val viewModel = ProviderStatusViewModel(
            providerStatusRepository,
            routeRepository,
            connectivityMonitor,
            telephonyManager,
            serverConfigManager
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("http://172.168.10.238:8000/api/v1/", state.currentBaseUrl)
        assertEquals(NetworkStatus.BACKEND_REACHABLE, state.connectivity.status)
        assertEquals(1, state.providers.size)
        assertEquals("open_meteo", state.providers[0].providerId)
        assertEquals("HEALTHY", state.providers[0].status)
    }
}
