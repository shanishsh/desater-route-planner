package com.touristsafety.app.core.repository

import android.util.Log
import com.touristsafety.app.core.model.SafetyEvent
import com.touristsafety.app.core.network.ApiService
import com.touristsafety.app.core.network.dto.AssistantInteractiveQueryRequestDto
import com.touristsafety.app.core.network.dto.LiveSafetyAssistantRequestDto
import com.touristsafety.app.core.network.dto.LiveSafetyAssistantResponseDto
import com.touristsafety.app.core.spatial.OfflineSafetyGuidanceProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository coordinating AI-powered safety guidance and deterministic offline fallbacks.
 * Ensures the mobile client never hangs waiting for network or LLM responses.
 */
@Singleton
class AssistantRepository @Inject constructor(
    private val apiService: ApiService,
    private val offlineGuidanceProvider: OfflineSafetyGuidanceProvider
) {
    companion object {
        private const val TAG = "AssistantRepository"
    }

    // Cache of recent event explanations: key = eventId, value = response
    private val eventGuidanceCache = ConcurrentHashMap<String, LiveSafetyAssistantResponseDto>()

    suspend fun getSafetyGuidance(
        request: LiveSafetyAssistantRequestDto,
        localEvent: SafetyEvent? = null
    ): LiveSafetyAssistantResponseDto {
        val eventId = request.trigger_event?.event_id ?: localEvent?.eventId
        if (eventId != null) {
            val cached = eventGuidanceCache[eventId]
            if (cached != null) {
                return cached
            }
        }

        // 1. If device is offline, immediately return deterministic guidance without network attempt
        if (request.connectivity_status.equals("OFFLINE", ignoreCase = true)) {
            val offlineResp = buildOfflineGuidanceResponse(request, localEvent)
            if (eventId != null) eventGuidanceCache[eventId] = offlineResp
            return offlineResp
        }

        // 2. Attempt remote Groq AI endpoint with fast failure handling
        return try {
            val response = apiService.getSafetyGuidance(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (eventId != null) eventGuidanceCache[eventId] = body
                body
            } else {
                Log.w(TAG, "Backend assistant returned HTTP ${response.code()}; using deterministic fallback.")
                val fallback = buildOfflineGuidanceResponse(request, localEvent)
                if (eventId != null) eventGuidanceCache[eventId] = fallback
                fallback
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed calling assistant guidance endpoint: ${t.message}; using offline fallback.")
            val fallback = buildOfflineGuidanceResponse(request, localEvent)
            if (eventId != null) eventGuidanceCache[eventId] = fallback
            fallback
        }
    }

    suspend fun askAssistant(
        request: AssistantInteractiveQueryRequestDto
    ): LiveSafetyAssistantResponseDto {
        if (request.connectivity_status.equals("OFFLINE", ignoreCase = true)) {
            return buildOfflineQueryResponse(request)
        }

        return try {
            val response = apiService.askAssistant(request)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                Log.w(TAG, "Backend ask endpoint returned HTTP ${response.code()}; using query fallback.")
                buildOfflineQueryResponse(request)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed calling assistant ask endpoint: ${t.message}; using query fallback.")
            buildOfflineQueryResponse(request)
        }
    }

    private fun buildOfflineGuidanceResponse(
        request: LiveSafetyAssistantRequestDto,
        localEvent: SafetyEvent?
    ): LiveSafetyAssistantResponseDto {
        val altTitles = request.verified_alternatives.map { it.title }
        val guidance = if (localEvent != null) {
            offlineGuidanceProvider.getGuidanceForEvent(
                localEvent,
                hasVerifiedAlternatives = request.verified_alternatives.isNotEmpty(),
                alternativeTitles = altTitles
            )
        } else if (request.upcoming_hazards.isNotEmpty()) {
            val firstH = request.upcoming_hazards[0]
            val syntheticEvent = SafetyEvent(
                eventType = com.touristsafety.app.core.model.SafetyEventType.APPROACHING_HAZARD,
                hazardId = firstH.id,
                hazardTitle = firstH.title,
                severity = firstH.severity,
                distanceAheadMeters = firstH.distance_ahead_m,
                deterministicSpokenText = firstH.instructions ?: "Upcoming hazard ahead on route.",
                deterministicActionText = firstH.instructions ?: "Exercise heightened caution."
            )
            offlineGuidanceProvider.getGuidanceForEvent(
                syntheticEvent,
                hasVerifiedAlternatives = request.verified_alternatives.isNotEmpty(),
                alternativeTitles = altTitles
            )
        } else {
            com.touristsafety.app.core.spatial.OfflineSafetyGuidance(
                summary = "Active Trek: ${String.format(java.util.Locale.US, "%.1f", request.route_progress.remaining_km)} km remaining to destination.",
                whatHappened = "No active hazards reported intersecting remaining path.",
                whyRelevant = "Trail polyline is clear of recorded warning zones.",
                recommendedAction = "Continue along verified path and observe terrain.",
                whatToAvoid = "Do not take unverified shortcuts.",
                verifiedAlternatives = altTitles.ifEmpty { listOf("No verified alternatives mapped.") },
                spokenMessage = "Trail clear of known hazards. ${String.format(java.util.Locale.US, "%.1f", request.route_progress.remaining_km)} kilometers remaining."
            )
        }

        return LiveSafetyAssistantResponseDto(
            event_id = request.trigger_event?.event_id ?: localEvent?.eventId,
            context_generation_id = request.context_generation_id,
            summary = guidance.summary,
            what_happened = guidance.whatHappened,
            why_relevant = guidance.whyRelevant,
            recommended_action = guidance.recommendedAction,
            what_to_avoid = guidance.whatToAvoid,
            verified_alternatives = guidance.verifiedAlternatives,
            referenced_hazard_ids = listOfNotNull(request.trigger_event?.hazard_id ?: localEvent?.hazardId),
            data_limitations = listOf("Pre-approved offline safety template; external updates paused."),
            spoken_message = guidance.spokenMessage,
            is_ai_generated = false,
            fallback_used = true,
            disclaimer = "Advisory guidance grounded strictly in verified trail context."
        )
    }

    private fun buildOfflineQueryResponse(
        request: AssistantInteractiveQueryRequestDto
    ): LiveSafetyAssistantResponseDto {
        val q = request.query.lowercase()
        val w = request.current_weather
        val hazards = request.upcoming_hazards
        val facs = request.nearby_facilities

        val (summary, action, spoken) = when {
            q.contains("weather") || q.contains("rain") || q.contains("temp") -> {
                if (w != null) {
                    val tempStr = if (w.temperature_c != null) "${w.temperature_c.toInt()}°C" else "Unknown"
                    val desc = w.weather_description ?: "Variable conditions"
                    val rain = if (w.precipitation_probability_pct != null) "${w.precipitation_probability_pct}%" else "N/A"
                    Triple(
                        "Current weather: $tempStr, $desc. Rain probability: $rain.",
                        "Monitor changing cloud formations from trail vantage points.",
                        "Current weather is $tempStr with $desc. Rain probability $rain."
                    )
                } else {
                    Triple(
                        "Weather data currently unavailable offline.",
                        "Observe ridge conditions and prepare rain gear if overcast.",
                        "Weather data unavailable. Observe local sky conditions."
                    )
                }
            }
            q.contains("hazard") || q.contains("next") || q.contains("danger") -> {
                if (hazards.isNotEmpty()) {
                    val h = hazards[0]
                    val distStr = if ((h.distance_ahead_m ?: 0.0) >= 1000.0) {
                        String.format(java.util.Locale.US, "%.1f km", (h.distance_ahead_m ?: 0.0) / 1000.0)
                    } else "${(h.distance_ahead_m ?: 0.0).toInt()} meters"
                    Triple(
                        "Next hazard: #${h.sequence_number ?: 1} ${h.title}, $distStr ahead.",
                        h.instructions ?: "Maintain visual awareness on trail.",
                        "Next hazard: ${h.title}, $distStr ahead."
                    )
                } else {
                    Triple(
                        "No hazards reported along your remaining route.",
                        "Stay on the marked trail.",
                        "No upcoming hazards reported along your route."
                    )
                }
            }
            q.contains("clinic") || q.contains("hospital") || q.contains("medical") -> {
                if (facs.isNotEmpty()) {
                    val nearest = facs.minByOrNull { it.distance_km }!!
                    Triple(
                        "Nearest medical facility: ${nearest.name} (${String.format(java.util.Locale.US, "%.1f", nearest.distance_km)} km).",
                        "Head toward ${nearest.name} in case of medical emergency.",
                        "Nearest facility is ${nearest.name}, ${String.format(java.util.Locale.US, "%.1f", nearest.distance_km)} kilometers away."
                    )
                } else {
                    Triple(
                        "No medical facilities mapped within 10 km corridor.",
                        "Self-reliance is required in this alpine sector. Turn back early if injured.",
                        "No medical facilities mapped nearby. Evacuate early if injured."
                    )
                }
            }
            q.contains("alternative") || q.contains("bypass") -> {
                if (request.verified_alternatives.isNotEmpty()) {
                    val alt = request.verified_alternatives[0]
                    Triple(
                        "Verified alternative: ${alt.title} (${String.format(java.util.Locale.US, "%.1f", alt.distance_km)} km).",
                        "Inspect alternative route before deciding to divert.",
                        "Verified alternative available: ${alt.title}."
                    )
                } else {
                    Triple(
                        "No verified alternative trail available in database.",
                        "If trail ahead is impassable, return to last safe checkpoint.",
                        "No verified alternative trail exists. If blocked, return to last safe checkpoint."
                    )
                }
            }
            else -> {
                Triple(
                    "Trek monitoring active: ${String.format(java.util.Locale.US, "%.1f", request.route_progress.remaining_km)} km remaining.",
                    "Ask about weather ahead, next hazards, facilities, or alternatives.",
                    "Monitoring active. ${String.format(java.util.Locale.US, "%.1f", request.route_progress.remaining_km)} kilometers remaining."
                )
            }
        }

        return LiveSafetyAssistantResponseDto(
            event_id = null,
            context_generation_id = request.context_generation_id,
            summary = summary,
            what_happened = "Offline local query evaluation.",
            why_relevant = "Offline safety mode active.",
            recommended_action = action,
            what_to_avoid = "Do not leave verified trail without mapped egress.",
            verified_alternatives = request.verified_alternatives.map { it.title }.ifEmpty { listOf("None verified") },
            referenced_hazard_ids = emptyList(),
            data_limitations = listOf("Offline query evaluated locally."),
            spoken_message = spoken,
            is_ai_generated = false,
            fallback_used = true,
            disclaimer = "Advisory guidance grounded strictly in verified trail context."
        )
    }

    fun clearCache() {
        eventGuidanceCache.clear()
    }
}

