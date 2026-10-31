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

    private val _uiState = MutableStateFlow(ReportTrailConditionUiState())
    val uiState: StateFlow<ReportTrailConditionUiState> = _uiState.asStateFlow()

    fun initialize(lat: Double?, lon: Double?, accuracyM: Double?, routeId: String?) {
        _uiState.update { current ->
            current.copy(
                latitude = lat ?: current.latitude,
                longitude = lon ?: current.longitude,
                accuracyMeters = accuracyM ?: current.accuracyMeters,
                locationSource = if (lat != null) "gps" else "manual_map",
                routeId = routeId ?: current.routeId
            )
        }
    }

    fun setStep(step: ReportWizardStep) {
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
        _uiState.update { it.copy(currentStep = next, errorMessage = null) }
    }

    fun prevStep() {
        val prev = when (_uiState.value.currentStep) {
            ReportWizardStep.CATEGORY -> ReportWizardStep.CATEGORY
            ReportWizardStep.LOCATION -> ReportWizardStep.CATEGORY
            ReportWizardStep.EVIDENCE -> ReportWizardStep.LOCATION
            ReportWizardStep.DETAILS -> ReportWizardStep.EVIDENCE
            ReportWizardStep.REVIEW -> ReportWizardStep.DETAILS
        }
        _uiState.update { it.copy(currentStep = prev, errorMessage = null) }
    }

    fun selectCategory(cat: String) {
        _uiState.update { it.copy(category = cat) }
    }

    fun setLocation(lat: Double, lon: Double, source: String = "manual_map", accuracyM: Double = 5.0) {
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
        _uiState.update { it.copy(severity = sev) }
    }

    fun setPassability(pass: String) {
        _uiState.update { it.copy(passability = pass) }
    }

    fun setDescription(desc: String) {
        _uiState.update { it.copy(description = desc) }
    }

    fun setPhotoUri(uri: Uri?) {
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
            _uiState.update { it.copy(cameraTempUri = uri) }
            uri
        } catch (t: Throwable) {
            null
        }
    }

    fun onCameraPhotoCaptured(success: Boolean) {
        if (success) {
            _uiState.update { it.copy(photoUri = it.cameraTempUri) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun submitReport(onSuccessCallback: () -> Unit) {
        val state = _uiState.value
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
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
                idempotency_key = "report-${UUID.randomUUID().toString().take(16)}"
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

