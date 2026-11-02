package com.touristsafety.app

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.touristsafety.app.core.network.dto.TrailReportCreateDto
import com.touristsafety.app.core.network.dto.TrailReportResponseDto
import com.touristsafety.app.core.repository.TrailReportRepository
import com.touristsafety.app.feature.report.ReportTrailConditionViewModel
import com.touristsafety.app.feature.report.ReportWizardStep
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportTrailConditionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var trailReportRepository: TrailReportRepository
    private lateinit var context: Context
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: ReportTrailConditionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        trailReportRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle()

        viewModel = ReportTrailConditionViewModel(
            trailReportRepository = trailReportRepository,
            context = context,
            savedStateHandle = savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateAndStepProgression() {
        assertEquals(ReportWizardStep.CATEGORY, viewModel.uiState.value.currentStep)
        assertEquals("fallen_tree", viewModel.uiState.value.category)

        viewModel.selectCategory("landslide")
        assertEquals("landslide", viewModel.uiState.value.category)

        viewModel.nextStep()
        assertEquals(ReportWizardStep.LOCATION, viewModel.uiState.value.currentStep)

        viewModel.setLocation(30.7339, 79.0669, "gps", 5.0)
        assertEquals(30.7339, viewModel.uiState.value.latitude, 0.0001)
        assertEquals(79.0669, viewModel.uiState.value.longitude, 0.0001)

        viewModel.nextStep()
        assertEquals(ReportWizardStep.EVIDENCE, viewModel.uiState.value.currentStep)

        viewModel.nextStep()
        assertEquals(ReportWizardStep.DETAILS, viewModel.uiState.value.currentStep)

        viewModel.setSeverity("CRITICAL")
        viewModel.setPassability("IMPASSABLE")
        viewModel.setDescription("Massive rockfall blocking entire trail width")
        assertEquals("CRITICAL", viewModel.uiState.value.severity)
        assertEquals("IMPASSABLE", viewModel.uiState.value.passability)
        assertEquals("Massive rockfall blocking entire trail width", viewModel.uiState.value.description)

        viewModel.nextStep()
        assertEquals(ReportWizardStep.REVIEW, viewModel.uiState.value.currentStep)

        // Previous step
        viewModel.prevStep()
        assertEquals(ReportWizardStep.DETAILS, viewModel.uiState.value.currentStep)
    }

    @Test
    fun testSubmitReportOnlineSuccess() = runTest {
        val mockResponse = TrailReportResponseDto(
            id = "rep-123",
            category = "landslide",
            severity = "HIGH",
            passability = "DIFFICULT_REQUIRES_CAUTION",
            latitude = 30.7339,
            longitude = 79.0669,
            location_accuracy_meters = 5.0,
            location_source = "gps",
            observation_time = "2026-10-31T00:00:00Z",
            submission_time = "2026-10-31T00:00:00Z",
            description = "Rockfall on trail",
            photo_url = "https://cdn.trailguard.org/photos/hazard_1.jpg",
            route_id = "route-kedarnath",
            route_version = 1,
            verification_status = "SYNCED",
            confirmations_count = 1,
            disputes_count = 0,
            expires_at = "",
            idempotency_key = "key-1",
            is_expired = false
        )

        coEvery { trailReportRepository.submitReport(any(), any()) } returns Result.success(mockResponse)

        var onSuccessCalled = false
        viewModel.selectCategory("landslide")
        viewModel.submitReport {
            onSuccessCalled = true
        }

        testScheduler.advanceUntilIdle()

        assertTrue(onSuccessCalled)
        assertTrue(viewModel.uiState.value.submitSuccess)
        assertFalse(viewModel.uiState.value.isOfflineQueued)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testSubmitReportOfflineQueuedSuccess() = runTest {
        val offlineResponse = TrailReportResponseDto(
            id = "rep-offline-456",
            category = "flash_flood",
            severity = "CRITICAL",
            passability = "IMPASSABLE",
            latitude = 30.7339,
            longitude = 79.0669,
            location_accuracy_meters = 10.0,
            location_source = "gps",
            observation_time = "2026-10-31T00:00:00Z",
            submission_time = "2026-10-31T00:00:00Z",
            description = "Flooded trail bridge",
            photo_url = null,
            route_id = null,
            route_version = 1,
            verification_status = "QUEUED_OFFLINE",
            confirmations_count = 0,
            disputes_count = 0,
            expires_at = "",
            idempotency_key = "key-2",
            is_expired = false
        )

        coEvery { trailReportRepository.submitReport(any(), any()) } returns Result.success(offlineResponse)

        var onSuccessCalled = false
        viewModel.submitReport {
            onSuccessCalled = true
        }

        testScheduler.advanceUntilIdle()

        assertTrue(onSuccessCalled)
        assertTrue(viewModel.uiState.value.submitSuccess)
        assertTrue(viewModel.uiState.value.isOfflineQueued)
    }

    @Test
    fun testMutationsPersistToSavedStateHandle() {
        val stateHandle = SavedStateHandle()
        val vm = ReportTrailConditionViewModel(
            trailReportRepository = trailReportRepository,
            context = context,
            savedStateHandle = stateHandle
        )

        vm.setStep(ReportWizardStep.DETAILS)
        vm.selectCategory("flash_flood")
        vm.setSeverity("CRITICAL")
        vm.setPassability("IMPASSABLE")
        vm.setLocation(30.7339, 79.0669, "gps", 5.0)
        vm.setDescription("Rising waters near river crossing")

        assertEquals("DETAILS", stateHandle.get<String>("report_wizard_step"))
        assertEquals("flash_flood", stateHandle.get<String>("report_category"))
        assertEquals("CRITICAL", stateHandle.get<String>("report_severity"))
        assertEquals("IMPASSABLE", stateHandle.get<String>("report_passability"))
        assertEquals(30.7339, stateHandle.get<Double>("report_latitude") ?: 0.0, 0.0001)
        assertEquals(79.0669, stateHandle.get<Double>("report_longitude") ?: 0.0, 0.0001)
        assertEquals("Rising waters near river crossing", stateHandle.get<String>("report_description"))
    }

    @Test
    fun testSavedStateHandleRestoresDraftAcrossProcessDeath() {
        val prePopulatedHandle = SavedStateHandle().apply {
            set("report_wizard_step", "EVIDENCE")
            set("report_category", "landslide")
            set("report_severity", "HIGH")
            set("report_passability", "IMPASSABLE")
            set("report_latitude", 30.7339)
            set("report_longitude", 79.0669)
            set("report_accuracy_meters", 8.0)
            set("report_location_source", "manual_map")
            set("report_description", "Heavy rockfall debris")
            set("report_route_id", "route-kedarnath-101")
        }

        val restoredVm = ReportTrailConditionViewModel(
            trailReportRepository = trailReportRepository,
            context = context,
            savedStateHandle = prePopulatedHandle
        )

        val restoredState = restoredVm.uiState.value
        assertEquals(ReportWizardStep.EVIDENCE, restoredState.currentStep)
        assertEquals("landslide", restoredState.category)
        assertEquals("HIGH", restoredState.severity)
        assertEquals("IMPASSABLE", restoredState.passability)
        assertEquals(30.7339, restoredState.latitude, 0.0001)
        assertEquals(79.0669, restoredState.longitude, 0.0001)
        assertEquals(8.0, restoredState.accuracyMeters, 0.001)
        assertEquals("manual_map", restoredState.locationSource)
        assertEquals("Heavy rockfall debris", restoredState.description)
        assertEquals("route-kedarnath-101", restoredState.routeId)
    }
}
