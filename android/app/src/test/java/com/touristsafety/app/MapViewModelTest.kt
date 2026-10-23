package com.touristsafety.app

import com.touristsafety.app.core.database.entity.HazardZoneEntity
import com.touristsafety.app.core.location.DeviceLocationRepository
import com.touristsafety.app.core.location.DeviceLocationState
import com.touristsafety.app.core.model.*
import com.touristsafety.app.core.network.DetailedConnectivityState
import com.touristsafety.app.core.network.NetworkConnectivityMonitor
import com.touristsafety.app.core.network.NetworkStatus
import com.touristsafety.app.core.network.dto.PlaceSearchResultDto
import com.touristsafety.app.core.repository.*
import com.touristsafety.app.core.network.dto.PlannedRouteResponseDto
import com.touristsafety.app.core.network.dto.RouteAnalysisResponseDto
import com.touristsafety.app.feature.map.MapFilterChip
import com.touristsafety.app.feature.map.MapViewModel
import com.touristsafety.app.feature.map.RouteLifecycleState
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
class MapViewModelTest {

    private val itineraryRepository = mockk<ItineraryRepository>(relaxed = true)
    private val routeRepository = mockk<RouteRepository>(relaxed = true)
    private val emergencyRepository = mockk<EmergencyRepository>(relaxed = true)
    private val forestRepository = mockk<ForestRepository>(relaxed = true)
    private val fireHotspotRepository = mockk<FireHotspotRepository>(relaxed = true)
    private val disasterRepository = mockk<DisasterRepository>(relaxed = true)
    private val weatherRepository = mockk<WeatherRepository>(relaxed = true)
    private val providerStatusRepository = mockk<ProviderStatusRepository>(relaxed = true)
    private val connectivityMonitor = mockk<NetworkConnectivityMonitor>(relaxed = true)
    private val deviceLocationRepository = mockk<DeviceLocationRepository>(relaxed = true)
    private val locationStateFlow = MutableStateFlow(DeviceLocationState())
    private val activeRouteStateFlow = MutableStateFlow<PlannedRouteResponseDto?>(null)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        activeRouteStateFlow.value = null
        every { connectivityMonitor.state } returns MutableStateFlow(
            DetailedConnectivityState(status = NetworkStatus.BACKEND_REACHABLE)
        )
        every { deviceLocationRepository.locationState } returns locationStateFlow
        every { routeRepository.activeRouteFlow } returns activeRouteStateFlow
        coEvery { deviceLocationRepository.getCurrentLocationFix(any(), any()) } returns Result.failure(Exception("No GPS fix in mock setup"))
        coEvery { deviceLocationRepository.getCurrentLocationFix() } returns Result.failure(Exception("No GPS fix in mock setup"))
        coEvery { emergencyRepository.refreshMapPoints() } returns Result.success(Unit)
        coEvery { forestRepository.refreshForestFeatures(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { fireHotspotRepository.refreshHotspots(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { disasterRepository.syncDisasterAlerts() } returns Result.success(0)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads all layers into unified features and category counts`() = runTest(testDispatcher) {
        val samplePoints = listOf(
            MapPoint(
                id = "pt-1",
                name = "District Alpine Civil Hospital",
                category = PointCategory.MEDICAL,
                latitude = 32.2410,
                longitude = 77.1850
            ),
            MapPoint(
                id = "pt-2",
                name = "Tourist Police Post",
                category = PointCategory.POLICE,
                latitude = 32.2425,
                longitude = 77.1910
            )
        )
        val sampleForest = listOf(
            ForestFeature(
                id = "forest-1",
                osmId = 12345L,
                name = "Hadimba Woodland",
                woodType = "coniferous",
                coordinates = listOf(32.24 to 77.18, 32.25 to 77.19, 32.24 to 77.19),
                isPolygon = true
            )
        )
        val sampleFire = listOf(
            FireHotspot(
                id = "fire-1",
                latitude = 32.26,
                longitude = 77.20,
                brightness = 340.5,
                confidence = "nominal",
                frpMw = 12.8,
                acquisitionDate = "2026-10-23",
                acquisitionTime = "0630",
                satellite = "VIIRS",
                isSimulated = true
            )
        )
        val sampleHazard = listOf(
            HazardZoneEntity(
                alertId = "alert-1",
                title = "Flash Flood Warning",
                description = "Beas River high water",
                hazardCategory = "flash_flood",
                severity = "CRITICAL",
                centerLatitude = 32.2450,
                centerLongitude = 77.1880,
                radiusMeters = 500f,
                requiredProtectiveInstructions = "Evacuate riverbank",
                issuingAuthority = "DDMA",
                publishedAtEpochMs = System.currentTimeMillis(),
                receivedAtEpochMs = System.currentTimeMillis(),
                downloadedAtEpochMs = System.currentTimeMillis(),
                isSimulated = false,
                status = "active"
            )
        )

        every { emergencyRepository.getMapPoints() } returns flowOf(samplePoints)
        every { forestRepository.getForestFeatures() } returns flowOf(sampleForest)
        every { fireHotspotRepository.getHotspots() } returns flowOf(sampleFire)
        every { disasterRepository.hazardZones } returns flowOf(sampleHazard)
        every { weatherRepository.getWeather(any(), any()) } returns flowOf(null)
        every { itineraryRepository.getItineraries() } returns flowOf(emptyList())

        val viewModel = MapViewModel(
            itineraryRepository,
            routeRepository,
            emergencyRepository,
            forestRepository,
            fireHotspotRepository,
            disasterRepository,
            weatherRepository,
            providerStatusRepository,
            connectivityMonitor,
            deviceLocationRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.allFeatures.size)
        assertEquals(1, state.categoryCounts[MapFeatureCategory.HOSPITAL])
        assertEquals(1, state.categoryCounts[MapFeatureCategory.POLICE_STATION])
        assertEquals(1, state.categoryCounts[MapFeatureCategory.FOREST_AREA])
        assertEquals(1, state.categoryCounts[MapFeatureCategory.ACTIVE_FIRE_DETECTION])
        assertEquals(1, state.categoryCounts[MapFeatureCategory.HAZARD_ZONE])
    }

    @Test
    fun `filter selection updates visible features correctly`() = runTest(testDispatcher) {
        val samplePoints = listOf(
            MapPoint("pt-1", "Hospital A", PointCategory.MEDICAL, 32.24, 77.18),
            MapPoint("pt-2", "Police B", PointCategory.POLICE, 32.25, 77.19)
        )
        every { emergencyRepository.getMapPoints() } returns flowOf(samplePoints)
        every { forestRepository.getForestFeatures() } returns flowOf(emptyList())
        every { fireHotspotRepository.getHotspots() } returns flowOf(emptyList())
        every { disasterRepository.hazardZones } returns flowOf(emptyList())
        every { weatherRepository.getWeather(any(), any()) } returns flowOf(null)
        every { itineraryRepository.getItineraries() } returns flowOf(emptyList())

        val viewModel = MapViewModel(
            itineraryRepository,
            routeRepository,
            emergencyRepository,
            forestRepository,
            fireHotspotRepository,
            disasterRepository,
            weatherRepository,
            providerStatusRepository,
            connectivityMonitor,
            deviceLocationRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // Filter by MEDICAL
        viewModel.onFilterSelected(MapFilterChip.MEDICAL)
        val medicalState = viewModel.uiState.value
        assertEquals(1, medicalState.visibleFeatures.size)
        assertEquals(MapFeatureCategory.HOSPITAL, medicalState.visibleFeatures[0].category)

        // Filter by POLICE
        viewModel.onFilterSelected(MapFilterChip.POLICE)
        val policeState = viewModel.uiState.value
        assertEquals(1, policeState.visibleFeatures.size)
        assertEquals(MapFeatureCategory.POLICE_STATION, policeState.visibleFeatures[0].category)

        // Reset to ALL
        viewModel.onFilterSelected(MapFilterChip.ALL)
        assertEquals(2, viewModel.uiState.value.visibleFeatures.size)
    }

    @Test
    fun `layer toggle hides and shows features independently`() = runTest(testDispatcher) {
        val samplePoints = listOf(
            MapPoint("pt-1", "Hospital A", PointCategory.MEDICAL, 32.24, 77.18)
        )
        every { emergencyRepository.getMapPoints() } returns flowOf(samplePoints)
        every { forestRepository.getForestFeatures() } returns flowOf(emptyList())
        every { fireHotspotRepository.getHotspots() } returns flowOf(emptyList())
        every { disasterRepository.hazardZones } returns flowOf(emptyList())
        every { weatherRepository.getWeather(any(), any()) } returns flowOf(null)
        every { itineraryRepository.getItineraries() } returns flowOf(emptyList())

        val viewModel = MapViewModel(
            itineraryRepository,
            routeRepository,
            emergencyRepository,
            forestRepository,
            fireHotspotRepository,
            disasterRepository,
            weatherRepository,
            providerStatusRepository,
            connectivityMonitor,
            deviceLocationRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.visibleFeatures.size)

        // Toggle Hospital layer off
        viewModel.toggleLayer(MapFeatureCategory.HOSPITAL)
        assertEquals(0, viewModel.uiState.value.visibleFeatures.size)

        // Show all layers
        viewModel.showAllLayers()
        assertEquals(1, viewModel.uiState.value.visibleFeatures.size)
    }

    @Test
    fun `selectDestination triggers route planning in repository`() = runTest(testDispatcher) {
        val viewModel = MapViewModel(
            itineraryRepository,
            routeRepository,
            emergencyRepository,
            forestRepository,
            fireHotspotRepository,
            disasterRepository,
            weatherRepository,
            providerStatusRepository,
            connectivityMonitor,
            deviceLocationRepository
        )

        val destination = PlaceSearchResultDto(
            name = "Solang Valley",
            latitude = 32.3166,
            longitude = 77.1583,
            display_name = "Solang Valley, Himachal Pradesh"
        )

        coEvery { routeRepository.planRoute(any(), any(), any(), any(), any(), any(), any()) } returns Result.failure(Exception())

        viewModel.selectDestination(destination)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            routeRepository.planRoute(
                originLat = any(),
                originLon = any(),
                originName = "Current Location",
                destLat = 32.3166,
                destLon = 77.1583,
                destName = "Solang Valley"
            )
        }
    }

    @Test
    fun `device location emission updates userLocation, accuracy, and acquired status`() = runTest(testDispatcher) {
        val viewModel = MapViewModel(
            itineraryRepository,
            routeRepository,
            emergencyRepository,
            forestRepository,
            fireHotspotRepository,
            disasterRepository,
            weatherRepository,
            providerStatusRepository,
            connectivityMonitor,
            deviceLocationRepository
        )

        locationStateFlow.value = DeviceLocationState(
            latitude = 32.2450,
            longitude = 77.1890,
            accuracyMeters = 8.5f,
            isFresh = true,
            isLocating = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(32.2450 to 77.1890, state.userLocation)
        assertEquals(8.5f, state.userLocationAccuracyMeters)
        assertTrue(state.isUserLocationAcquired)
        assertTrue(state.isLocationFresh)
    }

    @Test
    fun `recenterToUserLocation updates cameraTarget to acquired user location`() = runTest(testDispatcher) {
        val viewModel = MapViewModel(
            itineraryRepository,
            routeRepository,
            emergencyRepository,
            forestRepository,
            fireHotspotRepository,
            disasterRepository,
            weatherRepository,
            providerStatusRepository,
            connectivityMonitor,
            deviceLocationRepository
        )

        locationStateFlow.value = DeviceLocationState(
            latitude = 32.2500,
            longitude = 77.1950,
            accuracyMeters = 5f,
            isFresh = true
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.recenterToUserLocation()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(32.2500 to 77.1950, viewModel.uiState.value.cameraTarget)
    }

    @Test
    fun `selectRouteAlternative updates route coordinates to alternative geometry`() = runTest(testDispatcher) {
        val viewModel = MapViewModel(
            itineraryRepository,
            routeRepository,
            emergencyRepository,
            forestRepository,
            fireHotspotRepository,
            disasterRepository,
            weatherRepository,
            providerStatusRepository,
            connectivityMonitor,
            deviceLocationRepository
        )

        viewModel.selectRouteAlternative(null)
        assertNull(viewModel.uiState.value.selectedAlternativeId)
    }

    @Test
    fun `activeRouteFlow emission transitions routeLifecycleState to ROUTE_AVAILABLE and triggers corridor analysis`() = runTest(testDispatcher) {
        val samplePlannedRoute = PlannedRouteResponseDto(
            route_id = "route-manali-solang-123",
            route_version = 1,
            title = "Manali to Solang Pass",
            origin_name = "Manali Mall Road",
            origin_lat = 32.2396,
            origin_lon = 77.1887,
            destination_name = "Solang Valley",
            dest_lat = 32.3166,
            dest_lon = 77.1583,
            travel_mode = "hiking",
            distance_km = 12.4,
            duration_minutes = 180.0,
            overall_hazard_score = 22.5,
            overall_safety_status = "SAFE"
        )

        val sampleAnalysis = RouteAnalysisResponseDto(
            route_id = "route-manali-solang-123",
            route_version = 1,
            analysis_timestamp = "2026-10-24T00:00:00Z",
            disaster_alerts = emptyList(),
            fire_detections = emptyList(),
            assistance_points = emptyList(),
            weather_by_waypoint = emptyList(),
            provider_statuses = emptyMap()
        )

        coEvery { routeRepository.analyzeRoute("route-manali-solang-123", any(), any()) } returns Result.success(sampleAnalysis)

        val viewModel = MapViewModel(
            itineraryRepository,
            routeRepository,
            emergencyRepository,
            forestRepository,
            fireHotspotRepository,
            disasterRepository,
            weatherRepository,
            providerStatusRepository,
            connectivityMonitor,
            deviceLocationRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(RouteLifecycleState.NO_ROUTE_SELECTED, viewModel.uiState.value.routeLifecycleState)

        // Emit new active route
        activeRouteStateFlow.value = samplePlannedRoute
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("route-manali-solang-123", state.activeRouteId)
        assertEquals(1, state.activeRouteVersion)
        assertEquals("Manali to Solang Pass", state.routeTitle)
        assertEquals(RouteLifecycleState.ANALYZED_EMPTY, state.routeLifecycleState)

        coVerify(atLeast = 1) {
            routeRepository.analyzeRoute("route-manali-solang-123", any(), any())
        }
    }

    @Test
    fun `setCorridorDistance updates corridor width and triggers re-analysis for active route`() = runTest(testDispatcher) {
        val samplePlannedRoute = PlannedRouteResponseDto(
            route_id = "route-manali-solang-123",
            route_version = 1,
            title = "Manali to Solang Pass",
            origin_name = "Manali Mall Road",
            origin_lat = 32.2396,
            origin_lon = 77.1887,
            destination_name = "Solang Valley",
            dest_lat = 32.3166,
            dest_lon = 77.1583,
            travel_mode = "hiking",
            distance_km = 12.4,
            duration_minutes = 180.0,
            overall_hazard_score = 22.5,
            overall_safety_status = "SAFE"
        )

        val analysis5km = RouteAnalysisResponseDto(
            route_id = "route-manali-solang-123",
            route_version = 1,
            corridor_distance_km = 5.0,
            analysis_timestamp = "2026-10-24T00:00:00Z"
        )
        val analysis10km = RouteAnalysisResponseDto(
            route_id = "route-manali-solang-123",
            route_version = 1,
            corridor_distance_km = 10.0,
            analysis_timestamp = "2026-10-24T00:01:00Z"
        )

        coEvery { routeRepository.analyzeRoute("route-manali-solang-123", 5.0, 1) } returns Result.success(analysis5km)
        coEvery { routeRepository.analyzeRoute("route-manali-solang-123", 10.0, 1) } returns Result.success(analysis10km)

        val viewModel = MapViewModel(
            itineraryRepository,
            routeRepository,
            emergencyRepository,
            forestRepository,
            fireHotspotRepository,
            disasterRepository,
            weatherRepository,
            providerStatusRepository,
            connectivityMonitor,
            deviceLocationRepository
        )

        testDispatcher.scheduler.advanceUntilIdle()
        activeRouteStateFlow.value = samplePlannedRoute
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5.0, viewModel.uiState.value.corridorDistanceKm, 0.001)

        // Change corridor distance to 10 km
        viewModel.setCorridorDistance(10.0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(10.0, viewModel.uiState.value.corridorDistanceKm, 0.001)
        coVerify(exactly = 1) {
            routeRepository.analyzeRoute("route-manali-solang-123", 10.0, 1)
        }
    }

    @Test
    fun `clearRoute invokes routeRepository clearActiveRoute`() = runTest(testDispatcher) {
        val viewModel = MapViewModel(
            itineraryRepository,
            routeRepository,
            emergencyRepository,
            forestRepository,
            fireHotspotRepository,
            disasterRepository,
            weatherRepository,
            providerStatusRepository,
            connectivityMonitor,
            deviceLocationRepository
        )

        viewModel.clearRoute()
        verify(exactly = 1) { routeRepository.clearActiveRoute() }
    }
}

