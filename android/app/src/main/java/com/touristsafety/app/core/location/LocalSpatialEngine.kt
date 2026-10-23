package com.touristsafety.app.core.location

import kotlin.math.*

object LocalSpatialEngine {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates great circle distance between two points in meters.
     */
    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2.0).pow(2) +
                cos(phi1) * cos(phi2) * sin(deltaLambda / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Computes initial bearing from point 1 to point 2 in radians.
     */
    fun initialBearingRadians(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        return (atan2(y, x) + 2 * PI) % (2 * PI)
    }

    /**
     * Calculates cross-track distance in meters from point P to great circle path segment A-B.
     */
    fun crossTrackDistanceMeters(
        pointLat: Double, pointLon: Double,
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double
    ): Double {
        val d13 = haversineDistanceMeters(startLat, startLon, pointLat, pointLon)
        val theta13 = initialBearingRadians(startLat, startLon, pointLat, pointLon)
        val theta12 = initialBearingRadians(startLat, startLon, endLat, endLon)

        val dXt = asin(sin(d13 / EARTH_RADIUS_METERS) * sin(theta13 - theta12)) * EARTH_RADIUS_METERS
        return abs(dXt)
    }

    /**
     * Finds the minimum cross-track distance in meters from current position to a polyline.
     */
    fun computeMinDistanceToPolyline(
        currentLat: Double,
        currentLon: Double,
        polyline: List<Pair<Double, Double>> // List of (Lat, Lon)
    ): Double {
        if (polyline.isEmpty()) return Double.MAX_VALUE
        if (polyline.size == 1) {
            return haversineDistanceMeters(currentLat, currentLon, polyline[0].first, polyline[0].second)
        }

        var minDistance = Double.MAX_VALUE
        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val d = crossTrackDistanceMeters(
                pointLat = currentLat,
                pointLon = currentLon,
                startLat = a.first,
                startLon = a.second,
                endLat = b.first,
                endLon = b.second
            )
            if (d < minDistance) {
                minDistance = d
            }
        }
        return minDistance
    }

    /**
     * Determines whether point (lat, lon) is inside a 2D polygon using Ray-Casting algorithm.
     */
    fun isPointInPolygon(
        lat: Double,
        lon: Double,
        polygonCoordinates: List<Pair<Double, Double>> // (lat, lon)
    ): Boolean {
        if (polygonCoordinates.size < 3) return false
        var inside = false
        val n = polygonCoordinates.size
        var j = n - 1

        for (i in 0 until n) {
            val pi = polygonCoordinates[i]
            val pj = polygonCoordinates[j]

            val xi = pi.second
            val yi = pi.first
            val xj = pj.second
            val yj = pj.first

            val intersect = ((yi > lat) != (yj > lat)) &&
                    (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            if (intersect) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Computes dead-reckoning search uncertainty radius in meters.
     * R = R_gps + v_max * delta_t
     */
    fun computeUncertaintyRadiusMeters(
        lastGpsAccuracyM: Double,
        elapsedMinutes: Double,
        maxSpeedKmh: Double = 3.5
    ): Double {
        val speedMetersPerMinute = (maxSpeedKmh * 1000.0) / 60.0
        val expandedDistance = speedMetersPerMinute * elapsedMinutes
        return min(15000.0, max(lastGpsAccuracyM, lastGpsAccuracyM + expandedDistance))
    }
}

