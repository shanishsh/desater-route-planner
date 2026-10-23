package com.touristsafety.app.feature.disaster

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touristsafety.app.core.database.entity.HazardZoneEntity
import com.touristsafety.app.core.database.entity.SubscriberEntity
import com.touristsafety.app.core.location.HazardGeofenceManager
import com.touristsafety.app.core.push.DeviceRegistrationHelper
import com.touristsafety.app.core.repository.DisasterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DisasterUiState(
    val isLoading: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val isOtpDialogOpen: Boolean = false,
    val pendingPhone: String = ""
)

@HiltViewModel
class DisasterSafetyViewModel @Inject constructor(
    private val disasterRepository: DisasterRepository,
    private val hazardGeofenceManager: HazardGeofenceManager,
    private val deviceRegistrationHelper: DeviceRegistrationHelper
) : ViewModel() {

    val hazardZones: StateFlow<List<HazardZoneEntity>> = disasterRepository.hazardZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriber: StateFlow<SubscriberEntity?> = disasterRepository.subscriber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isMonitoring: StateFlow<Boolean> = hazardGeofenceManager.isMonitoring
    val isSirenPlaying: StateFlow<Boolean> = disasterRepository.isSirenPlaying
    val lastLocation: StateFlow<Location?> = hazardGeofenceManager.lastKnownLocation

    private val _uiState = MutableStateFlow(DisasterUiState())
    val uiState: StateFlow<DisasterUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            deviceRegistrationHelper.registerDeviceWithBackend()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = disasterRepository.syncDisasterAlerts()
            result.onSuccess { count ->
                _uiState.update { it.copy(isLoading = false, infoMessage = "Synced $count hazard zones") }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Offline mode: Showing cached hazard zones"
                    )
                }
            }
        }
    }

    fun toggleHazardMonitoring(enable: Boolean) {
        if (enable) {
            hazardGeofenceManager.startMonitoring()
            _uiState.update { it.copy(infoMessage = "Route hazard monitoring activated on-device.") }
        } else {
            hazardGeofenceManager.stopMonitoring()
            _uiState.update { it.copy(infoMessage = "Route hazard monitoring stopped.") }
        }
    }

    fun requestOtp(phone: String) {
        val cleanPhone = phone.trim()
        if (cleanPhone.length < 10) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid phone number with country code (e.g. +919876543210)") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = disasterRepository.registerSubscriber(cleanPhone)
            res.onSuccess { msg ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isOtpDialogOpen = true,
                        pendingPhone = cleanPhone,
                        infoMessage = msg
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun verifyOtp(otp: String, consent: Boolean, areas: List<String>) {
        if (!consent) {
            _uiState.update { it.copy(errorMessage = "Consent is required to receive emergency disaster warnings.") }
            return
        }

        val phone = _uiState.value.pendingPhone
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = disasterRepository.verifyOtp(phone, otp, consent, areas)
            res.onSuccess { sub ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isOtpDialogOpen = false,
                        infoMessage = "SMS alert subscription activated for ${sub.phone}!"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun dismissOtpDialog() {
        _uiState.update { it.copy(isOtpDialogOpen = false) }
    }

    fun unsubscribe() {
        val phone = subscriber.value?.phone ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = disasterRepository.unsubscribe(phone)
            res.onSuccess {
                _uiState.update { it.copy(isLoading = false, infoMessage = "Unsubscribed from disaster SMS.") }
            }.onFailure { err ->
                _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun triggerTestAlarm() {
        viewModelScope.launch {
            disasterRepository.triggerTestAlarm()
            _uiState.update { it.copy(infoMessage = "Test alarm siren and notification triggered. Test alarm; no real disaster.") }
        }
    }

    fun triggerDummyAlert() {
        disasterRepository.triggerDummyAlert()
        _uiState.update { it.copy(infoMessage = "🚨 Dummy Disaster Alert triggered! Ringing emergency siren & posting notification.") }
    }

    fun stopDisasterSiren() {
        disasterRepository.stopDisasterSiren()
        _uiState.update { it.copy(infoMessage = "Disaster siren silenced.") }
    }

    fun clearMessages() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}

