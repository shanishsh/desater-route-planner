package com.touristsafety.app.core.location

import com.touristsafety.app.core.audio.SafetyAlertPlayer
import com.touristsafety.app.core.database.dao.FireHotspotDao
import com.touristsafety.app.core.database.dao.HazardZoneDao
import com.touristsafety.app.core.database.entity.FireHotspotEntity
import com.touristsafety.app.core.database.entity.HazardZoneEntity
import com.touristsafety.app.core.notification.DisasterNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveHazardAlert(
    val hazardId: String,
    val title: String,
    val severity: String,
    val distanceKm: Double,
    val instructions: String,
    val issuingAuthority: String?,
    val triggeredAtEpochMs: Long = System.currentTimeMillis()
)

data class HazardCooldownState(
    val lastAlertedTimestamp: Long,
    val lastAlertedSeverity: String,
    val lastAlertedDistanceKm: Double
)

@Singleton
class HazardAlertEngine @Inject constructor(
    private val hazardZoneDao: HazardZoneDao,
    private val fireHotspotDao: FireHotspotDao,
    private val safetyAlertPlayer: SafetyAlertPlayer,
    private val disasterNotificationManager: DisasterNotificationManager
) {
    companion object {
        const val COOLDOWN_DURATION_MS = 15 * 60 * 1000L // 15 minutes
        const val CRITICAL_PROXIMITY_KM = 3.0
        const val HIGH_PROXIMITY_KM = 2.5
        const val MODERATE_PROXIMITY_KM = 1.0
    }

    private val cooldownCache = mutableMapOf<String, HazardCooldownState>()

    private val _activeAlert = MutableStateFlow<ActiveHazardAlert?>(null)
    val activeAlert: StateFlow<ActiveHazardAlert?> = _activeAlert.asStateFlow()

    var isSoundEnabled: Boolean = true
    var isVibrationEnabled: Boolean = true

    suspend fun evaluatePosition(
        lat: Double,
        lon: Double,
        isInBackground: Boolean = false
    ): ActiveHazardAlert? {
        val now = System.currentTimeMillis()

        // 1. Check Hazard Zones (polygons / zones)
        val activeZones = hazardZoneDao.getAllActiveList()
        for (zone in activeZones) {
            val distMeters = TrekDistanceCalculator.computeDistanceMeters(
                lat, lon,
                zone.centerLatitude, zone.centerLongitude
            )
            val distKm = distMeters / 1000.0

            val thresholdKm = when (zone.severity.uppercase()) {
                "CRITICAL", "EXTREME" -> CRITICAL_PROXIMITY_KM + (zone.radiusMeters / 1000.0)
                "HIGH" -> HIGH_PROXIMITY_KM + (zone.radiusMeters / 1000.0)
                else -> MODERATE_PROXIMITY_KM + (zone.radiusMeters / 1000.0)
            }

            if (distKm <= thresholdKm) {
                if (shouldTriggerAlert(zone.alertId, zone.severity, distKm, now)) {
                    val alert = ActiveHazardAlert(
                        hazardId = "zone_${zone.alertId}",
                        title = zone.title,
                        severity = zone.severity,
                        distanceKm = (distKm * 10.0).toInt() / 10.0,
                        instructions = zone.requiredProtectiveInstructions,
                        issuingAuthority = zone.issuingAuthority,
                        triggeredAtEpochMs = now
                    )
                    triggerAlert(alert, isInBackground)
                    return alert
                }
            }
        }

        // 2. Check Active Wildfire Hotspots (NASA FIRMS)
        val activeFires = fireHotspotDao.getAllHotspotsList()
        for (fire in activeFires) {
            val distMeters = TrekDistanceCalculator.computeDistanceMeters(
                lat, lon,
                fire.latitude, fire.longitude
            )
            val distKm = distMeters / 1000.0

            if (distKm <= HIGH_PROXIMITY_KM) {
                val fireId = "fire_${fire.id}"
                if (shouldTriggerAlert(fireId, "HIGH", distKm, now)) {
                    val alert = ActiveHazardAlert(
                        hazardId = fireId,
                        title = "NASA FIRMS Satellite Thermal Anomaly",
                        severity = "HIGH",
                        distanceKm = (distKm * 10.0).toInt() / 10.0,
                        instructions = "Active wildfire or thermal hotspot detected nearby. Check wind direction and safe haven egress.",
                        issuingAuthority = "NASA FIRMS",
                        triggeredAtEpochMs = now
                    )
                    triggerAlert(alert, isInBackground)
                    return alert
                }
            }
        }

        return null
    }

    private fun shouldTriggerAlert(
        hazardId: String,
        severity: String,
        distKm: Double,
        now: Long
    ): Boolean {
        val lastState = cooldownCache[hazardId] ?: return true

        // Bypass cooldown if severity escalated
        if (getSeverityRank(severity) > getSeverityRank(lastState.lastAlertedSeverity)) {
            return true
        }

        // Suppress if within 15-minute cooldown
        if (now - lastState.lastAlertedTimestamp < COOLDOWN_DURATION_MS) {
            return false
        }

        return true
    }

    private fun triggerAlert(alert: ActiveHazardAlert, isInBackground: Boolean) {
        cooldownCache[alert.hazardId] = HazardCooldownState(
            lastAlertedTimestamp = alert.triggeredAtEpochMs,
            lastAlertedSeverity = alert.severity,
            lastAlertedDistanceKm = alert.distanceKm
        )

        _activeAlert.value = alert

        // Trigger loud siren sound and emergency vibration
        safetyAlertPlayer.playSafetyAlert(
            soundEnabled = isSoundEnabled,
            vibrationEnabled = isVibrationEnabled
        )

        // Post high-priority notification with custom siren
        disasterNotificationManager.showDisasterNotification(
            alertId = alert.hazardId,
            title = "⚠️ ${alert.severity}: ${alert.title}",
            instructions = "${alert.instructions}\nDistance: ${alert.distanceKm} km",
            category = "HAZARD_PROXIMITY",
            issuingAuthority = alert.issuingAuthority ?: "State Disaster Authority",
            isCached = true
        )
    }

    fun dismissActiveAlert() {
        _activeAlert.value = null
        safetyAlertPlayer.stopSafetyAlert()
    }

    private fun getSeverityRank(sev: String): Int {
        return when (sev.uppercase()) {
            "CRITICAL", "EXTREME" -> 4
            "HIGH" -> 3
            "MEDIUM", "MODERATE" -> 2
            "LOW" -> 1
            else -> 0
        }
    }
}

