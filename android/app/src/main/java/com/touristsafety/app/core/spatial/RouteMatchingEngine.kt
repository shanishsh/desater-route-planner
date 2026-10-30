package com.touristsafety.app.core.spatial

import kotlin.math.*

/**
 * High-performance 2D/3D geodesic route matching and projection engine.
 *
 * Responsibilities:
 * 1. Projects noisy GPS coordinates onto the active trail polyline using local Cartesian metric tangent plane.
 * 2. Accurately calculates progress/chainage along the trail from the trailhead.
 * 3. Prevents erratic chainage jumps across switchbacks using windowed monotonic progression.
 * 4. Determines lateral cross-track distance and off-route status.
 * 5. Evaluates real polygon hazard boundaries against route geometry to compute entry and exit chainage.
 */
object RouteMatchingEngine {

    const val EARTH_RADIUS_METERS = 6371000.0
    const val DEFAULT_OFF_ROUTE_THRESHOLD_METERS = 80.0

    data class RouteProjectionResult(
        val chainageMeters: Double,
        val crossTrackDistanceMeters: Double,
        val segmentIndex: Int,
        val projectedLatitude: Double,
        val projectedLongitude: Double,
        val isOffRoute: Boolean
    )

    data class PolygonRouteIntersection(
        val isDirectIntersection: Boolean,
        val entryChainageMeters: Double,
        val exitChainageMeters: Double,
        val minCrossTrackMeters: Double
    )

    data class HazardClassificationResult(
        val classification: com.touristsafety.app.core.model.HazardCategoryClassification,
        val distanceAheadMeters: Double? = null,
        val crossTrackDistanceMeters: Double = 0.0,
        val chainageMeters: Double = 0.0
    )

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
     * Builds cumulative distance table along the polyline.
     * Table size matches routePoints size, with index 0 = 0.0.
     */
    fun buildCumulativeDistances(routePoints: List<Pair<Double, Double>>): DoubleArray {
        if (routePoints.isEmpty()) return DoubleArray(0)
        val cum = DoubleArray(routePoints.size)
        cum[0] = 0.0
        for (i in 1 until routePoints.size) {
            cum[i] = cum[i - 1] + haversineDistanceMeters(
                routePoints[i - 1].first, routePoints[i - 1].second,
                routePoints[i].first, routePoints[i].second
            )
        }
        return cum
    }

    /**
     * Alias for projectPointOntoRoute.
     */
    fun projectPointToRoute(
        lat: Double,
        lon: Double,
        routePoints: List<Pair<Double, Double>>,
        cumDistances: DoubleArray,
        previousChainageMeters: Double? = null,
        gpsAccuracyMeters: Float = 10f
    ): RouteProjectionResult {
        return projectPointOntoRoute(lat, lon, routePoints, cumDistances, previousChainageMeters, gpsAccuracyMeters)
    }

    /**
     * Projects a GPS location onto the route polyline.
     */
    fun projectPointOntoRoute(
        pointLat: Double,
        pointLon: Double,
        routePoints: List<Pair<Double, Double>>,
        cumDistances: DoubleArray,
        previousChainageMeters: Double? = null,
        gpsAccuracyMeters: Float = 10f
    ): RouteProjectionResult {
        if (routePoints.size < 2) {
            val d = if (routePoints.isNotEmpty()) haversineDistanceMeters(pointLat, pointLon, routePoints[0].first, routePoints[0].second) else 0.0
            val offRoute = d > max(DEFAULT_OFF_ROUTE_THRESHOLD_METERS, gpsAccuracyMeters * 2.0)
            return RouteProjectionResult(
                chainageMeters = 0.0,
                crossTrackDistanceMeters = d,
                segmentIndex = 0,
                projectedLatitude = routePoints.firstOrNull()?.first ?: pointLat,
                projectedLongitude = routePoints.firstOrNull()?.second ?: pointLon,
                isOffRoute = offRoute
            )
        }

        // Local metric projection origin around the query point
        val cosLat0 = cos(Math.toRadians(pointLat))

        fun toMetric(lat: Double, lon: Double): Pair<Double, Double> {
            val x = EARTH_RADIUS_METERS * Math.toRadians(lon - pointLon) * cosLat0
            val y = EARTH_RADIUS_METERS * Math.toRadians(lat - pointLat)
            return x to y
        }

        val px = 0.0
        val py = 0.0

        var bestCrossTrack = Double.MAX_VALUE
        var bestChainage = 0.0
        var bestSegIdx = 0
        var bestProjLat = routePoints[0].first
        var bestProjLon = routePoints[0].second

        // In switchback-dense mountain terrain, penalize segments far ahead or far behind previous chainage
        val hasPrior = previousChainageMeters != null && previousChainageMeters > 0.0

        for (i in 0 until routePoints.size - 1) {
            val p1 = toMetric(routePoints[i].first, routePoints[i].second)
            val p2 = toMetric(routePoints[i + 1].first, routePoints[i + 1].second)

            val dx = p2.first - p1.first
            val dy = p2.second - p1.second
            val segLenSq = dx * dx + dy * dy

            val t = if (segLenSq > 0.0001) {
                val rawT = ((px - p1.first) * dx + (py - p1.second) * dy) / segLenSq
                rawT.coerceIn(0.0, 1.0)
            } else 0.0

            val projX = p1.first + t * dx
            val projY = p1.second + t * dy

            val dXt = sqrt(projX * projX + projY * projY)
            val segLen = sqrt(segLenSq)
            val chainageCandidate = cumDistances[i] + t * segLen

            // Switchback stabilization penalty: if point is close to two segments, prefer the one near recent progress
            val switchbackPenalty = if (hasPrior) {
                val delta = chainageCandidate - previousChainageMeters!!
                if (delta < -150.0) {
                    // Moving backwards along route
                    abs(delta) * 0.15
                } else if (delta > 500.0) {
                    // Jumping forward across a switchback
                    (delta - 500.0) * 0.20
                } else 0.0
            } else 0.0

            val effectiveScore = dXt + switchbackPenalty

            if (effectiveScore < bestCrossTrack) {
                bestCrossTrack = dXt
                bestChainage = chainageCandidate
                bestSegIdx = i
                // Interpolate geographic coordinate for projected puck
                bestProjLat = routePoints[i].first + t * (routePoints[i + 1].first - routePoints[i].first)
                bestProjLon = routePoints[i].second + t * (routePoints[i + 1].second - routePoints[i].second)
            }
        }

        val dynamicThreshold = max(DEFAULT_OFF_ROUTE_THRESHOLD_METERS, gpsAccuracyMeters * 2.0)
        val isOff = bestCrossTrack > dynamicThreshold

        return RouteProjectionResult(
            chainageMeters = bestChainage,
            crossTrackDistanceMeters = bestCrossTrack,
            segmentIndex = bestSegIdx,
            projectedLatitude = bestProjLat,
            projectedLongitude = bestProjLon,
            isOffRoute = isOff
        )
    }

    /**
     * Evaluates a polygon hazard against the planned route polyline.
     * Calculates genuine edge intersections and computes entry/exit chainage.
     */
    fun evaluatePolygonAgainstRoute(
        polygon: List<Pair<Double, Double>>,
        routePoints: List<Pair<Double, Double>>,
        cumDistances: DoubleArray,
        corridorDistanceMeters: Double = 5000.0
    ): PolygonRouteIntersection? {
        if (polygon.size < 3 || routePoints.size < 2) return null

        val lat0 = routePoints.map { it.first }.average()
        val lon0 = routePoints.map { it.second }.average()
        val cosLat0 = cos(Math.toRadians(lat0))

        fun toMetric(lat: Double, lon: Double): Pair<Double, Double> {
            val x = EARTH_RADIUS_METERS * Math.toRadians(lon - lon0) * cosLat0
            val y = EARTH_RADIUS_METERS * Math.toRadians(lat - lat0)
            return x to y
        }

        val polyMetric = polygon.map { toMetric(it.first, it.second) }
        val routeMetric = routePoints.map { toMetric(it.first, it.second) }

        val intersectionChainages = mutableListOf<Double>()
        var minCrossTrack = Double.MAX_VALUE

        // 1. Check if route vertices lie inside the polygon
        for (i in routePoints.indices) {
            val pt = routeMetric[i]
            if (isPointInsidePolygonMetric(pt.first, pt.second, polyMetric)) {
                intersectionChainages.add(cumDistances[i])
                minCrossTrack = 0.0
            }
        }

        // 2. Check segment-to-segment line intersections between route and polygon edges
        val polyEdges = mutableListOf<Pair<Pair<Double, Double>, Pair<Double, Double>>>()
        for (p in 0 until polyMetric.size) {
            val nextP = (p + 1) % polyMetric.size
            polyEdges.add(polyMetric[p] to polyMetric[nextP])
        }

        for (r in 0 until routeMetric.size - 1) {
            val r1 = routeMetric[r]
            val r2 = routeMetric[r + 1]
            val rLen = haversineDistanceMeters(routePoints[r].first, routePoints[r].second, routePoints[r + 1].first, routePoints[r + 1].second)

            for (edge in polyEdges) {
                val p1 = edge.first
                val p2 = edge.second
                val t = lineSegmentIntersectionT(r1, r2, p1, p2)
                if (t != null && t in 0.0..1.0) {
                    val hitChainage = cumDistances[r] + t * rLen
                    intersectionChainages.add(hitChainage)
                    minCrossTrack = 0.0
                }
            }
        }

        if (intersectionChainages.isNotEmpty()) {
            val sorted = intersectionChainages.sorted()
            return PolygonRouteIntersection(
                isDirectIntersection = true,
                entryChainageMeters = sorted.first(),
                exitChainageMeters = sorted.last(),
                minCrossTrackMeters = 0.0
            )
        }

        // 3. Polygon does not directly intersect, calculate minimum distance to corridor
        for (pt in polyMetric) {
            for (r in 0 until routeMetric.size - 1) {
                val r1 = routeMetric[r]
                val r2 = routeMetric[r + 1]
                val dx = r2.first - r1.first
                val dy = r2.second - r1.second
                val segLenSq = dx * dx + dy * dy
                val t = if (segLenSq > 0.0001) {
                    (((pt.first - r1.first) * dx + (pt.second - r1.second) * dy) / segLenSq).coerceIn(0.0, 1.0)
                } else 0.0
                val px = r1.first + t * dx
                val py = r1.second + t * dy
                val dist = sqrt((pt.first - px).pow(2) + (pt.second - py).pow(2))
                if (dist < minCrossTrack) {
                    minCrossTrack = dist
                }
            }
        }

        if (minCrossTrack <= corridorDistanceMeters) {
            // Find centroid chainage
            val centroidLat = polygon.map { it.first }.average()
            val centroidLon = polygon.map { it.second }.average()
            val proj = projectPointOntoRoute(centroidLat, centroidLon, routePoints, cumDistances)
            return PolygonRouteIntersection(
                isDirectIntersection = false,
                entryChainageMeters = proj.chainageMeters,
                exitChainageMeters = proj.chainageMeters,
                minCrossTrackMeters = minCrossTrack
            )
        }

        return null
    }

    /**
     * Convenience method returning entry and exit chainage (or null if no intersection).
     */
    fun calculatePolygonEntryExit(
        polygon: List<Pair<Double, Double>>,
        routePoints: List<Pair<Double, Double>>,
        cumDistances: DoubleArray
    ): Pair<Double, Double>? {
        val eval = evaluatePolygonAgainstRoute(polygon, routePoints, cumDistances)
        return if (eval != null && eval.isDirectIntersection) {
            eval.entryChainageMeters to eval.exitChainageMeters
        } else null
    }

    /**
     * Dynamically classifies a hazard feature relative to the live trekker progress.
     */
    fun classifyHazard(
        feature: com.touristsafety.app.core.model.UnifiedMapFeature,
        routePoints: List<Pair<Double, Double>>,
        cumDistances: DoubleArray,
        currentChainageMeters: Double,
        currentLat: Double,
        currentLon: Double,
        gpsAccuracyMeters: Float = 10f,
        isOffRoute: Boolean = false
    ): HazardClassificationResult {
        val distToUserMeters = haversineDistanceMeters(currentLat, currentLon, feature.latitude, feature.longitude)
        val titleLower = feature.title.lowercase()
        val physicalRadiusMeters = when {
            titleLower.contains("landslide") || titleLower.contains("rockfall") -> 600.0
            titleLower.contains("flood") || titleLower.contains("avalanche") -> 1500.0
            feature.category == com.touristsafety.app.core.model.MapFeatureCategory.ACTIVE_FIRE_DETECTION || titleLower.contains("fire") -> 2500.0
            else -> 500.0
        }

        if (distToUserMeters <= physicalRadiusMeters) {
            return HazardClassificationResult(
                classification = com.touristsafety.app.core.model.HazardCategoryClassification.NEAR_CURRENT_LOCATION,
                distanceAheadMeters = 0.0,
                crossTrackDistanceMeters = distToUserMeters
            )
        }

        if (isOffRoute) {
            return HazardClassificationResult(
                classification = com.touristsafety.app.core.model.HazardCategoryClassification.UNCERTAIN,
                crossTrackDistanceMeters = distToUserMeters
            )
        }

        val proj = projectPointOntoRoute(feature.latitude, feature.longitude, routePoints, cumDistances)
        if (proj.crossTrackDistanceMeters <= 150.0) {
            val delta = proj.chainageMeters - currentChainageMeters
            return if (delta < -80.0) {
                HazardClassificationResult(
                    classification = com.touristsafety.app.core.model.HazardCategoryClassification.PASSED,
                    crossTrackDistanceMeters = proj.crossTrackDistanceMeters,
                    chainageMeters = proj.chainageMeters
                )
            } else if (delta > 30.0) {
                HazardClassificationResult(
                    classification = com.touristsafety.app.core.model.HazardCategoryClassification.AHEAD_ON_ROUTE,
                    distanceAheadMeters = delta,
                    crossTrackDistanceMeters = proj.crossTrackDistanceMeters,
                    chainageMeters = proj.chainageMeters
                )
            } else {
                HazardClassificationResult(
                    classification = com.touristsafety.app.core.model.HazardCategoryClassification.NEAR_CURRENT_LOCATION,
                    distanceAheadMeters = 0.0,
                    crossTrackDistanceMeters = proj.crossTrackDistanceMeters,
                    chainageMeters = proj.chainageMeters
                )
            }
        } else if (proj.crossTrackDistanceMeters <= 5000.0) {
            return HazardClassificationResult(
                classification = com.touristsafety.app.core.model.HazardCategoryClassification.NEARBY_OFF_ROUTE,
                crossTrackDistanceMeters = proj.crossTrackDistanceMeters,
                chainageMeters = proj.chainageMeters
            )
        } else {
            return HazardClassificationResult(
                classification = com.touristsafety.app.core.model.HazardCategoryClassification.UNCERTAIN,
                crossTrackDistanceMeters = proj.crossTrackDistanceMeters
            )
        }
    }

    private fun isPointInsidePolygonMetric(x: Double, y: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.second > y) != (pj.second > y) &&
                x < (pj.first - pi.first) * (y - pi.second) / (pj.second - pi.second) + pi.first
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun lineSegmentIntersectionT(
        p1: Pair<Double, Double>, p2: Pair<Double, Double>,
        q1: Pair<Double, Double>, q2: Pair<Double, Double>
    ): Double? {
        val dx1 = p2.first - p1.first
        val dy1 = p2.second - p1.second
        val dx2 = q2.first - q1.first
        val dy2 = q2.second - q1.second

        val denom = dx1 * dy2 - dy1 * dx2
        if (abs(denom) < 1e-9) return null

        val t = ((q1.first - p1.first) * dy2 - (q1.second - p1.second) * dx2) / denom
        val u = ((q1.first - p1.first) * dy1 - (q1.second - p1.second) * dx1) / denom

        return if (t in 0.0..1.0 && u in 0.0..1.0) t else null
    }
}

