package com.touristsafety.app.core.spatial

import com.touristsafety.app.core.model.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic Safety Decision Engine.
 *
 * Implements strict, testable rules to evaluate safety events independently of any AI service.
 * Determines when to trigger loud alarms, sirens, vibrations, spoken alerts, and visual warnings.
 */
@Singleton
class DeterministicSafetyDecisionEngine @Inject constructor(
    private val offlineGuidanceProvider: OfflineSafetyGuidanceProvider
) {
    companion object {
        private const val ALERT_COOLDOWN_MS = 15 * 60 * 1000L // 15-minute deduplication cooldown
        private const val CRITICAL_DISTANCE_THRESHOLD_METERS = 2500.0 // 2.5 km for severe hazards
        private const val OFF_ROUTE_THRESHOLD_METERS = 80.0
        private const val HIGH_WIND_THRESHOLD_KMH = 55.0
    }

    // Cooldown map: key = hazardId or event category, value = last dispatched timestamp
    private val eventCooldowns = ConcurrentHashMap<String, Long>()
    private var lastOfflineState: Boolean? = null

    /**
     * Evaluates current trek conditions and returns a SafetyEvent if attention or alarm is required.
     */
    fun evaluate(
        upcomingHazards: List<RouteOrderedHazardItem>,
        nearbyHazards: List<RouteOrderedHazardItem>,
        progress: TrekProgressMetrics,
        weather: LiveWeatherContext,
        isOffline: Boolean,
        hasVerifiedAlternatives: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): SafetyEvent? {

        // 1. Check for Critical or Severe Hazard directly ahead on route within threshold
        val imminentObstruction = upcomingHazards.firstOrNull { h ->
            val dist = h.distanceAheadMeters ?: 9999.0
            val isSevereOrCritical = h.severity.equals("CRITICAL", ignoreCase = true) ||
                    h.severity.equals("SEVERE", ignoreCase = true) ||
                    h.feature.category == MapFeatureCategory.COMMUNITY_HAZARD
            isSevereOrCritical && dist <= CRITICAL_DISTANCE_THRESHOLD_METERS
        }

        if (imminentObstruction != null) {
            val key = "hazard_${imminentObstruction.feature.id}"
            val lastSent = eventCooldowns[key] ?: 0L
            val dist = imminentObstruction.distanceAheadMeters ?: 0.0

            val isCommunity = imminentObstruction.feature.category == MapFeatureCategory.COMMUNITY_HAZARD
            // Escalate if within 500m even if previously alerted
            val isEscalation = dist <= 500.0 && (now - lastSent) >= (3 * 60 * 1000L)

            if ((now - lastSent) >= ALERT_COOLDOWN_MS || isEscalation) {
                eventCooldowns[key] = now
                val evType = when {
                    isCommunity -> SafetyEventType.COMMUNITY_HAZARD_REPORT
                    imminentObstruction.feature.title.contains("Landslide", ignoreCase = true) ||
                    imminentObstruction.feature.title.contains("Rockfall", ignoreCase = true) -> SafetyEventType.ROUTE_CLOSURE_OBSTRUCTION
                    else -> SafetyEventType.CRITICAL_HAZARD_WARNING
                }

                val guidance = offlineGuidanceProvider.getGuidanceForEvent(
                    SafetyEvent(
                        eventType = evType,
                        hazardId = imminentObstruction.feature.id,
                        hazardTitle = imminentObstruction.feature.title,
                        severity = imminentObstruction.severity,
                        distanceAheadMeters = dist,
                        isDirectRouteObstruction = true,
                        deterministicSpokenText = "",
                        deterministicActionText = ""
                    ),
                    hasVerifiedAlternatives = hasVerifiedAlternatives
                )

                return SafetyEvent(
                    eventType = evType,
                    hazardId = imminentObstruction.feature.id,
                    hazardTitle = imminentObstruction.feature.title,
                    severity = imminentObstruction.severity,
                    distanceAheadMeters = dist,
                    distanceCurrentMeters = dist,
                    isDirectRouteObstruction = true,
                    hasVerifiedAlternative = hasVerifiedAlternatives,
                    requiresImmediateAlarm = !isCommunity,
                    requiresVoiceAlert = true,
                    deterministicSpokenText = guidance.spokenMessage,
                    deterministicActionText = guidance.recommendedAction,
                    triggeredAtEpochMs = now
                )
            }
        }

        // 2. Check for Immediate Physical Proximity Hazards (< 600m)
        val immediateNearby = nearbyHazards.firstOrNull { h ->
            val dist = h.distanceCurrentLocationMeters ?: 9999.0
            val isSevereOrCritical = h.severity.equals("CRITICAL", ignoreCase = true) ||
                    h.severity.equals("SEVERE", ignoreCase = true)
            isSevereOrCritical && dist <= 600.0
        }

        if (immediateNearby != null) {
            val key = "nearby_${immediateNearby.feature.id}"
            val lastSent = eventCooldowns[key] ?: 0L
            if ((now - lastSent) >= ALERT_COOLDOWN_MS) {
                eventCooldowns[key] = now
                val dist = immediateNearby.distanceCurrentLocationMeters ?: 0.0

                val guidance = offlineGuidanceProvider.getGuidanceForEvent(
                    SafetyEvent(
                        eventType = SafetyEventType.HIGH_PRIORITY_HAZARD_WARNING,
                        hazardId = immediateNearby.feature.id,
                        hazardTitle = immediateNearby.feature.title,
                        severity = immediateNearby.severity,
                        distanceCurrentMeters = dist,
                        deterministicSpokenText = "",
                        deterministicActionText = ""
                    ),
                    hasVerifiedAlternatives = hasVerifiedAlternatives
                )

                return SafetyEvent(
                    eventType = SafetyEventType.HIGH_PRIORITY_HAZARD_WARNING,
                    hazardId = immediateNearby.feature.id,
                    hazardTitle = immediateNearby.feature.title,
                    severity = immediateNearby.severity,
                    distanceAheadMeters = null,
                    distanceCurrentMeters = dist,
                    isDirectRouteObstruction = false,
                    hasVerifiedAlternative = hasVerifiedAlternatives,
                    requiresImmediateAlarm = true,
                    requiresVoiceAlert = true,
                    deterministicSpokenText = guidance.spokenMessage,
                    deterministicActionText = guidance.recommendedAction,
                    triggeredAtEpochMs = now
                )
            }
        }

        // 3. Significant Route Deviation (> 80m off trail)
        if (progress.isOffRoute && progress.crossTrackDistanceMeters >= OFF_ROUTE_THRESHOLD_METERS) {
            val key = "route_deviation"
            val lastSent = eventCooldowns[key] ?: 0L
            if ((now - lastSent) >= (5 * 60 * 1000L)) { // 5-minute cooldown for off-route reminders
                eventCooldowns[key] = now
                val devM = progress.crossTrackDistanceMeters

                val guidance = offlineGuidanceProvider.getGuidanceForEvent(
                    SafetyEvent(
                        eventType = SafetyEventType.SIGNIFICANT_ROUTE_DEVIATION,
                        hazardTitle = "Off Planned Trail",
                        severity = "WARNING",
                        distanceCurrentMeters = devM,
                        deterministicSpokenText = "",
                        deterministicActionText = ""
                    )
                )

                return SafetyEvent(
                    eventType = SafetyEventType.SIGNIFICANT_ROUTE_DEVIATION,
                    hazardId = null,
                    hazardTitle = "Off Planned Trail",
                    severity = "WARNING",
                    distanceAheadMeters = null,
                    distanceCurrentMeters = devM,
                    isDirectRouteObstruction = false,
                    hasVerifiedAlternative = false,
                    requiresImmediateAlarm = false,
                    requiresVoiceAlert = true,
                    deterministicSpokenText = guidance.spokenMessage,
                    deterministicActionText = guidance.recommendedAction,
                    triggeredAtEpochMs = now
                )
            }
        }

        // 4. Severe Weather Warning (Thunderstorm or Gale Winds)
        val isThunderstorm = (weather.thunderstormProbabilityPct ?: 0) >= 60 ||
                (weather.weatherDescription?.contains("thunder", ignoreCase = true) == true)
        val isGaleWind = (weather.windSpeedKmh ?: 0.0) >= HIGH_WIND_THRESHOLD_KMH

        if (isThunderstorm || isGaleWind) {
            val key = "severe_weather_event"
            val lastSent = eventCooldowns[key] ?: 0L
            if ((now - lastSent) >= ALERT_COOLDOWN_MS) {
                eventCooldowns[key] = now
                val title = if (isThunderstorm) "Severe Thunderstorm Warning" else "Gale Force Winds Warning"

                val guidance = offlineGuidanceProvider.getGuidanceForEvent(
                    SafetyEvent(
                        eventType = SafetyEventType.NEWLY_RELEVANT_WEATHER_WARNING,
                        hazardTitle = title,
                        severity = "HIGH",
                        deterministicSpokenText = "",
                        deterministicActionText = ""
                    ),
                    hasVerifiedAlternatives = hasVerifiedAlternatives
                )

                return SafetyEvent(
                    eventType = SafetyEventType.NEWLY_RELEVANT_WEATHER_WARNING,
                    hazardId = null,
                    hazardTitle = title,
                    severity = "HIGH",
                    distanceAheadMeters = null,
                    distanceCurrentMeters = 0.0,
                    isDirectRouteObstruction = false,
                    hasVerifiedAlternative = hasVerifiedAlternatives,
                    requiresImmediateAlarm = false,
                    requiresVoiceAlert = true,
                    deterministicSpokenText = guidance.spokenMessage,
                    deterministicActionText = guidance.recommendedAction,
                    triggeredAtEpochMs = now
                )
            }
        }

        // 5. Connectivity Loss Transition
        if (lastOfflineState == false && isOffline) {
            lastOfflineState = true
            val key = "connectivity_loss"
            val lastSent = eventCooldowns[key] ?: 0L
            if ((now - lastSent) >= (30 * 60 * 1000L)) {
                eventCooldowns[key] = now

                val guidance = offlineGuidanceProvider.getGuidanceForEvent(
                    SafetyEvent(
                        eventType = SafetyEventType.CONNECTIVITY_LOSS,
                        hazardTitle = "Cellular Network Lost",
                        severity = "INFO",
                        deterministicSpokenText = "",
                        deterministicActionText = ""
                    )
                )

                return SafetyEvent(
                    eventType = SafetyEventType.CONNECTIVITY_LOSS,
                    hazardId = null,
                    hazardTitle = "Cellular Network Lost",
                    severity = "INFO",
                    distanceAheadMeters = null,
                    distanceCurrentMeters = 0.0,
                    isDirectRouteObstruction = false,
                    hasVerifiedAlternative = hasVerifiedAlternatives,
                    requiresImmediateAlarm = false,
                    requiresVoiceAlert = true,
                    deterministicSpokenText = guidance.spokenMessage,
                    deterministicActionText = guidance.recommendedAction,
                    triggeredAtEpochMs = now
                )
            }
        } else if (lastOfflineState == null) {
            lastOfflineState = isOffline
        } else if (!isOffline) {
            lastOfflineState = false
        }

        return null
    }

    /**
     * Clears all cooldowns (e.g. when changing routes or starting a new trip).
     */
    fun reset() {
        eventCooldowns.clear()
        lastOfflineState = null
    }
}

