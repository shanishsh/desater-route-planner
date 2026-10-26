package com.touristsafety.app.core.location

import kotlin.math.*

/**
 * Geodesic distance calculation and distance accumulation for active mountain treks.
 * Uses the spherical Haversine formula.
 */
object TrekDistanceCalculator {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Computes distance in meters between two WGS84 geographic points.
     */
    fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Converts meters to kilometers formatted as decimal.
     */
    fun metersToKm(meters: Double): Double {
        return (meters / 1000.0 * 100.0).roundToInt() / 100.0
    }
}

