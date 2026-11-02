package com.touristsafety.app.feature.report

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.network.dto.TrailReportCreateDto
import com.touristsafety.app.core.repository.TrailReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class ReportWizardStep {
    CATEGORY,
    LOCATION,
    EVIDENCE,
    DETAILS,
    REVIEW
}

data class ReportTrailConditionUiState(
    val currentStep: ReportWizardStep = ReportWizardStep.CATEGORY,
    val category: String = "fallen_tree",
    val severity: String = "MEDIUM",
    val passability: String = "DIFFICULT_REQUIRES_CAUTION",
    val latitude: Double = 30.3165,
    val longitude: Double = 78.0322,
    val accuracyMeters: Double = 15.0,
    val locationSource: String = "gps",
    val photoUri: Uri? = null,
    val cameraTempUri: Uri? = null,
    val description: String = "",
    val routeId: String? = null,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val isOfflineQueued: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ReportTrailConditionViewModel @Inject constructor(
    private val trailReportRepository: TrailReportRepository,
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_STEP = "report_wizard_step"
        private const val KEY_CATEGORY = "report_category"
        private const val KEY_SEVERITY = "report_severity"
        private const val KEY_PASSABILITY = "report_passability"
        private const val KEY_LAT = "report_latitude"
        private const val KEY_LON = "report_longitude"
        private const val KEY_ACCURACY = "report_accuracy_meters"
        private const val KEY_LOC_SOURCE = "report_location_source"
        private const val KEY_PHOTO_URI = "report_photo_uri_str"
        private const val KEY_TEMP_URI = "report_camera_temp_uri_str"
        private const val KEY_DESCRIPTION = "report_description"
        private const val KEY_ROUTE_ID = "report_route_id"
        private const val KEY_IDEMPOTENCY = "report_idempotency_key"
    }

    private val initialStep = savedStateHandle.get<String>(KEY_STEP)?.let {
        runCatching { ReportWizardStep.valueOf(it) }.getOrNull()
    } ?: ReportWizardStep.CATEGORY

    private val initialCategory = savedStateHandle.get<String>(KEY_CATEGORY) ?: "fallen_tree"
    private val initialSeverity = savedStateHandle.get<String>(KEY_SEVERITY) ?: "MEDIUM"
    private val initialPassability = savedStateHandle.get<String>(KEY_PASSABILITY) ?: "DIFFICULT_REQUIRES_CAUTION"
    private val initialLat = savedStateHandle.get<Double>(KEY_LAT) ?: 30.3165
    private val initialLon = savedStateHandle.get<Double>(KEY_LON) ?: 78.0322
    private val initialAccuracy = savedStateHandle.get<Double>(KEY_ACCURACY) ?: 15.0
    private val initialSource = savedStateHandle.get<String>(KEY_LOC_SOURCE) ?: "gps"
    private val initialPhotoUri = savedStateHandle.get<String>(KEY_PHOTO_URI)?.let {
        runCatching { Uri.parse(it) }.getOrNull()
    }
    private val initialTempUri = savedStateHandle.get<String>(KEY_TEMP_URI)?.let {
        runCatching { Uri.parse(it) }.getOrNull()
    }
    private val initialDescription = savedStateHandle.get<String>(KEY_DESCRIPTION) ?: ""
    private val initialRouteId = savedStateHandle.get<String>(KEY_ROUTE_ID)

    private val _uiState = MutableStateFlow(
        ReportTrailConditionUiState(
            currentStep = initialStep,
            category = initialCategory,
            severity = initialSeverity,
            passability = initialPassability,
            latitude = initialLat,
            longitude = initialLon,
            accuracyMeters = initialAccuracy,
            locationSource = initialSource,
            photoUri = initialPhotoUri,
            cameraTempUri = initialTempUri,
            description = initialDescription,
            routeId = initialRouteId
        )
    )
    val uiState: StateFlow<ReportTrailConditionUiState> = _uiState.asStateFlow()

    private fun getOrCreateIdempotencyKey(): String {
        return savedStateHandle.get<String>(KEY_IDEMPOTENCY) ?: run {
            val key = "report-${UUID.randomUUID().toString().take(16)}"
            savedStateHandle[KEY_IDEMPOTENCY] = key
            key
        }
    }

    fun initialize(lat: Double?, lon: Double?, accuracyM: Double?, routeId: String?) {
        _uiState.update { current ->
            val newLat = lat ?: current.latitude
            val newLon = lon ?: current.longitude
            val newAcc = accuracyM ?: current.accuracyMeters
            val newSource = if (lat != null) "gps" else current.locationSource
            val newRoute = routeId ?: current.routeId

            savedStateHandle[KEY_LAT] = newLat
            savedStateHandle[KEY_LON] = newLon
            savedStateHandle[KEY_ACCURACY] = newAcc
            savedStateHandle[KEY_LOC_SOURCE] = newSource
            if (newRoute != null) savedStateHandle[KEY_ROUTE_ID] = newRoute

            current.copy(
                latitude = newLat,
                longitude = newLon,
                accuracyMeters = newAcc,
                locationSource = newSource,
                routeId = newRoute
            )
        }
    }

    fun setStep(step: ReportWizardStep) {
        savedStateHandle[KEY_STEP] = step.name
        _uiState.update { it.copy(currentStep = step, errorMessage = null) }
    }

    fun nextStep() {
        val next = when (_uiState.value.currentStep) {
            ReportWizardStep.CATEGORY -> ReportWizardStep.LOCATION
            ReportWizardStep.LOCATION -> ReportWizardStep.EVIDENCE
            ReportWizardStep.EVIDENCE -> ReportWizardStep.DETAILS
            ReportWizardStep.DETAILS -> ReportWizardStep.REVIEW
            ReportWizardStep.REVIEW -> ReportWizardStep.REVIEW
        }
        setStep(next)
    }

    fun prevStep() {
        val prev = when (_uiState.value.currentStep) {
            ReportWizardStep.CATEGORY -> ReportWizardStep.CATEGORY
            ReportWizardStep.LOCATION -> ReportWizardStep.CATEGORY
            ReportWizardStep.EVIDENCE -> ReportWizardStep.LOCATION
            ReportWizardStep.DETAILS -> ReportWizardStep.EVIDENCE
            ReportWizardStep.REVIEW -> ReportWizardStep.DETAILS
        }
        setStep(prev)
    }

    fun selectCategory(cat: String) {
        savedStateHandle[KEY_CATEGORY] = cat
        _uiState.update { it.copy(category = cat) }
    }

    fun setLocation(lat: Double, lon: Double, source: String = "manual_map", accuracyM: Double = 5.0) {
        savedStateHandle[KEY_LAT] = lat
        savedStateHandle[KEY_LON] = lon
        savedStateHandle[KEY_LOC_SOURCE] = source
        savedStateHandle[KEY_ACCURACY] = accuracyM
        _uiState.update {
            it.copy(
                latitude = lat,
                longitude = lon,
                locationSource = source,
                accuracyMeters = accuracyM
            )
        }
    }

    fun setSeverity(sev: String) {
        savedStateHandle[KEY_SEVERITY] = sev
        _uiState.update { it.copy(severity = sev) }
    }

    fun setPassability(pass: String) {
        savedStateHandle[KEY_PASSABILITY] = pass
        _uiState.update { it.copy(passability = pass) }
    }

    fun setDescription(desc: String) {
        savedStateHandle[KEY_DESCRIPTION] = desc
        _uiState.update { it.copy(description = desc) }
    }

    fun setPhotoUri(uri: Uri?) {
        savedStateHandle[KEY_PHOTO_URI] = uri?.toString()
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun createCameraUri(): Uri? {
        return try {
            val photoFile = File(context.cacheDir, "camera_report_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            savedStateHandle[KEY_TEMP_URI] = uri.toString()
            _uiState.update { it.copy(cameraTempUri = uri) }
            uri
        } catch (t: Throwable) {
            null
        }
    }

    fun onCameraPhotoCaptured(success: Boolean) {
        if (success) {
            val captured = _uiState.value.cameraTempUri
            savedStateHandle[KEY_PHOTO_URI] = captured?.toString()
            _uiState.update { it.copy(photoUri = captured) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun resetWizard() {
        savedStateHandle.remove<String>(KEY_STEP)
        savedStateHandle.remove<String>(KEY_CATEGORY)
        savedStateHandle.remove<String>(KEY_SEVERITY)
        savedStateHandle.remove<String>(KEY_PASSABILITY)
        savedStateHandle.remove<Double>(KEY_LAT)
        savedStateHandle.remove<Double>(KEY_LON)
        savedStateHandle.remove<Double>(KEY_ACCURACY)
        savedStateHandle.remove<String>(KEY_LOC_SOURCE)
        savedStateHandle.remove<String>(KEY_PHOTO_URI)
        savedStateHandle.remove<String>(KEY_TEMP_URI)
        savedStateHandle.remove<String>(KEY_DESCRIPTION)
        savedStateHandle.remove<String>(KEY_ROUTE_ID)
        savedStateHandle.remove<String>(KEY_IDEMPOTENCY)
        _uiState.value = ReportTrailConditionUiState()
    }

    fun submitReport(onSuccessCallback: () -> Unit) {
        val state = _uiState.value
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            val idempotency = getOrCreateIdempotencyKey()
            val reportDto = TrailReportCreateDto(
                category = state.category,
                severity = state.severity,
                passability = state.passability,
                latitude = state.latitude,
                longitude = state.longitude,
                location_accuracy_meters = state.accuracyMeters,
                location_source = state.locationSource,
                description = state.description.ifBlank { null },
                photo_url = null,
                route_id = state.routeId,
                idempotency_key = idempotency
            )

            val result = trailReportRepository.submitReport(reportDto, localPhotoUri = state.photoUri)
            _uiState.update { it.copy(isSubmitting = false) }

            result.fold(
                onSuccess = { response ->
                    val isOffline = response.verification_status == "QUEUED_OFFLINE"
                    _uiState.update {
                        it.copy(
                            submitSuccess = true,
                            isOfflineQueued = isOffline
                        )
                    }
                    // Clear draft keys upon successful submission
                    savedStateHandle.remove<String>(KEY_STEP)
                    savedStateHandle.remove<String>(KEY_PHOTO_URI)
                    savedStateHandle.remove<String>(KEY_TEMP_URI)
                    savedStateHandle.remove<String>(KEY_DESCRIPTION)
                    savedStateHandle.remove<String>(KEY_IDEMPOTENCY)
                    onSuccessCallback()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            errorMessage = err.message ?: "Failed to submit trail condition report."
                        )
                    }
                }
            )
        }
    }
}
