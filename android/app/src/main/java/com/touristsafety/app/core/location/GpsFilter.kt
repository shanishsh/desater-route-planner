package com.touristsafety.app.core.location

enum class GpsFilterStatus {
    ACCEPTED,
    REJECTED_LOW_ACCURACY,
    REJECTED_STATIONARY_DRIFT,
    REJECTED_IMPOSSIBLE_JUMP
}

data class GpsFilterResult(
    val status: GpsFilterStatus,
    val isAcceptedForDistance: Boolean,
    val deltaMeters: Double
)

/**
 * Pure Kotlin GPS noise, stationary jitter, and multipath drift filter.
 * Protects trekker distance accumulation from erroneous jumps while recording
 * raw telemetry for diagnostic tracking.
 */
object GpsFilter {

    const val MAX_ACCURACY_THRESHOLD = 35.0 // meters
    const val MIN_STATIONARY_DISTANCE_METERS = 2.5 // meters
    const val MIN_STATIONARY_SPEED_KMH = 1.8 // km/h (0.5 m/s)
    const val MAX_PLAUSIBLE_SPEED_MPS = 15.0 // m/s (~54 km/h mountain trek limit)

    fun filter(
        lastAcceptedLat: Double?,
        lastAcceptedLon: Double?,
        lastAcceptedEpochMs: Long?,
        lat: Double,
        lon: Double,
        accuracyMeters: Float,
        speedKmh: Double,
        timestampEpochMs: Long
    ): GpsFilterResult {
        // 1. Accuracy Check: Reject points with excessive uncertainty
        if (accuracyMeters > MAX_ACCURACY_THRESHOLD) {
            return GpsFilterResult(
                status = GpsFilterStatus.REJECTED_LOW_ACCURACY,
                isAcceptedForDistance = false,
                deltaMeters = 0.0
            )
        }

        // First accepted point initialization
        if (lastAcceptedLat == null || lastAcceptedLon == null || lastAcceptedEpochMs == null) {
            return GpsFilterResult(
                status = GpsFilterStatus.ACCEPTED,
                isAcceptedForDistance = true,
                deltaMeters = 0.0
            )
        }

        val deltaMeters = TrekDistanceCalculator.computeDistanceMeters(
            lastAcceptedLat, lastAcceptedLon,
            lat, lon
        )

        val deltaTimeSeconds = (timestampEpochMs - lastAcceptedEpochMs) / 1000.0

        // 2. Stationary Drift Check: Small movements with near-zero speed
        if (deltaMeters < MIN_STATIONARY_DISTANCE_METERS && speedKmh < MIN_STATIONARY_SPEED_KMH) {
            return GpsFilterResult(
                status = GpsFilterStatus.REJECTED_STATIONARY_DRIFT,
                isAcceptedForDistance = false,
                deltaMeters = 0.0
            )
        }

        // 3. Impossible Jump Check: Excessive speed indicating GPS multipath teleport
        if (deltaTimeSeconds > 0.0) {
            val calculatedSpeedMps = deltaMeters / deltaTimeSeconds
            if (calculatedSpeedMps > MAX_PLAUSIBLE_SPEED_MPS) {
                return GpsFilterResult(
                    status = GpsFilterStatus.REJECTED_IMPOSSIBLE_JUMP,
                    isAcceptedForDistance = false,
                    deltaMeters = 0.0
                )
            }
        }

        return GpsFilterResult(
            status = GpsFilterStatus.ACCEPTED,
            isAcceptedForDistance = true,
            deltaMeters = deltaMeters
        )
    }
}

