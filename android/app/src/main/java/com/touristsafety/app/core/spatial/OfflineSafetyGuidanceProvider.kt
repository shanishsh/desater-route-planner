package com.touristsafety.app.core.spatial

import com.touristsafety.app.core.model.SafetyEvent
import com.touristsafety.app.core.model.SafetyEventType
import javax.inject.Inject
import javax.inject.Singleton

data class OfflineSafetyGuidance(
    val summary: String,
    val whatHappened: String,
    val whyRelevant: String,
    val recommendedAction: String,
    val whatToAvoid: String,
    val verifiedAlternatives: List<String>,
    val spokenMessage: String,
    val isOfflineTemplate: Boolean = true
)

/**
 * Pre-approved, expert-vetted safety guidance templates for offline conditions.
 * Ensures the trekker receives deterministic, high-quality mountain safety advice
 * even when cellular connectivity is severed.
 */
@Singleton
class OfflineSafetyGuidanceProvider @Inject constructor() {

    fun getGuidanceForEvent(
        event: SafetyEvent,
        hasVerifiedAlternatives: Boolean = false,
        alternativeTitles: List<String> = emptyList()
    ): OfflineSafetyGuidance {
        val title = event.hazardTitle.uppercase()
        val distStr = if ((event.distanceAheadMeters ?: event.distanceCurrentMeters) >= 1000.0) {
            String.format(java.util.Locale.US, "%.1f km", (event.distanceAheadMeters ?: event.distanceCurrentMeters) / 1000.0)
        } else {
            "${(event.distanceAheadMeters ?: event.distanceCurrentMeters).toInt()} meters"
        }

        val alts = if (hasVerifiedAlternatives && alternativeTitles.isNotEmpty()) {
            alternativeTitles
        } else {
            listOf("No verified alternative trails mapped for this sector.")
        }

        return when {
            title.contains("LANDSLIDE") || title.contains("ROCKFALL") || event.eventType == SafetyEventType.ROUTE_CLOSURE_OBSTRUCTION -> {
                OfflineSafetyGuidance(
                    summary = "${event.severity} landslide advisory $distStr ahead along trail.",
                    whatHappened = "Active geological instability or rockfall reported intersecting trail path [${event.hazardId ?: "OFFICIAL"}].",
                    whyRelevant = "Falling rocks and unstable scree chutes pose severe blunt force and footing hazards.",
                    recommendedAction = "Halt progress before entering the active debris zone. Survey overhead slopes from protected cover.",
                    whatToAvoid = "Do not attempt to traverse moving debris, scree, or climb over active slide deposits.",
                    verifiedAlternatives = alts,
                    spokenMessage = "Warning: Landslide $distStr ahead. Do not cross active debris. Halt in protected terrain."
                )
            }

            title.contains("THUNDER") || event.eventType == SafetyEventType.NEWLY_RELEVANT_WEATHER_WARNING -> {
                OfflineSafetyGuidance(
                    summary = "Severe thunderstorm warning active for current elevation.",
                    whatHappened = "Atmospheric conditions indicate high risk of lightning strikes and torrential rainfall.",
                    whyRelevant = "Exposed ridges and summits are high-probability targets for cloud-to-ground lightning.",
                    recommendedAction = "Descend immediately from exposed ridgelines, col passes, and peaks to lower forested ground.",
                    whatToAvoid = "Avoid solitary tall trees, metal safety wire cables, wet climbing ropes, and shallow rock overhangs.",
                    verifiedAlternatives = alts,
                    spokenMessage = "Thunderstorm warning active. Descend from ridges immediately. Avoid solitary trees and metal fences."
                )
            }

            title.contains("FLOOD") || title.contains("CLOUDBURST") -> {
                OfflineSafetyGuidance(
                    summary = "Flash flood hazard warning in river drainage sector.",
                    whatHappened = "Rapid runoff or cloudburst upstream causes sudden surges in mountain torrents.",
                    whyRelevant = "Mountain streams can surge by multiple meters in minutes, sweeping away crossings.",
                    recommendedAction = "Move to high ground immediately away from riverbeds and canyon floors.",
                    whatToAvoid = "Never attempt to ford rapidly moving, murky, or waist-deep mountain water.",
                    verifiedAlternatives = alts,
                    spokenMessage = "Flash flood warning. Move away from riverbanks to high ground. Do not ford swollen streams."
                )
            }

            event.eventType == SafetyEventType.COMMUNITY_HAZARD_REPORT -> {
                OfflineSafetyGuidance(
                    summary = "Community trekker report: ${event.hazardTitle} ($distStr).",
                    whatHappened = "A fellow trekker reported a hazard [${event.hazardId ?: "COMMUNITY"}] along the trail.",
                    whyRelevant = "Trail conditions may have deteriorated recently due to local weather or physical obstacles.",
                    recommendedAction = "Visually verify trail condition ahead before advancing. Be prepared to halt or bypass if unsafe.",
                    whatToAvoid = "Do not disregard community warnings, especially during heavy rain or low visibility.",
                    verifiedAlternatives = alts,
                    spokenMessage = "Community report: A fellow trekker reported ${event.hazardTitle} $distStr ahead. Exercise caution."
                )
            }

            event.eventType == SafetyEventType.SIGNIFICANT_ROUTE_DEVIATION -> {
                val devM = (event.distanceAheadMeters ?: event.distanceCurrentMeters).toInt()
                OfflineSafetyGuidance(
                    summary = "Route deviation: $devM meters off verified trail.",
                    whatHappened = "GPS tracking indicates lateral movement away from the planned trail alignment.",
                    whyRelevant = "Unmarked mountain slopes frequently lead to dead-end cliffs, steep ravines, and lost orientation.",
                    recommendedAction = "Stop advancing. Orient using your map compass and carefully retrace steps to the verified trail.",
                    whatToAvoid = "Do not push forward through dense brush or scramble down unfamiliar gullies.",
                    verifiedAlternatives = listOf("Return to verified planned polyline."),
                    spokenMessage = "Off-route warning: $devM meters off planned trail. Stop and backtrack to verified trail."
                )
            }

            event.eventType == SafetyEventType.CONNECTIVITY_LOSS -> {
                OfflineSafetyGuidance(
                    summary = "Cellular signal lost. Operating in offline safety mode.",
                    whatHappened = "Device has transitioned outside mobile network coverage into a mountain dead zone.",
                    whyRelevant = "External real-time updates are paused; safety calculations are using cached trail data.",
                    recommendedAction = "Follow the cached trail polyline. Conserve phone battery by limiting screen on-time.",
                    whatToAvoid = "Do not rely on online messaging or web queries until cellular signal returns.",
                    verifiedAlternatives = alts,
                    spokenMessage = "Cellular connection lost. Offline safety guidance and GPS tracking remain active."
                )
            }

            else -> {
                OfflineSafetyGuidance(
                    summary = "${event.severity}: ${event.hazardTitle} ($distStr).",
                    whatHappened = "Environmental hazard [${event.hazardId ?: "ID"}] detected in the active corridor.",
                    whyRelevant = "Conditions require heightened situational awareness.",
                    recommendedAction = event.deterministicActionText.ifBlank { "Proceed with caution and maintain visual trail awareness." },
                    whatToAvoid = "Avoid traveling in darkness or during low visibility.",
                    verifiedAlternatives = alts,
                    spokenMessage = event.deterministicSpokenText.ifBlank { "Caution: ${event.hazardTitle} $distStr ahead." }
                )
            }
        }
    }
}

