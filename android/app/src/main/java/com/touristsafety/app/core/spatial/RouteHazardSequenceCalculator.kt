package com.touristsafety.app.core.spatial

import com.touristsafety.app.core.model.*
import kotlin.math.*

/**
 * Calculates genuine sequential positioning and relative encounter order of hazards
 * and safety facilities along a selected trek route polyline.
 *
 * Guarantees that:
 * 1. Hazards are ordered strictly by their cumulative distance along the planned route from start.
 * 2. Real polygon geometries are tested for genuine line intersection without creating fake perimeters.
 * 3. Point observations are projected onto the nearest route segment with cross-track distance.
 * 4. Active trek GPS progress classifies hazards into AHEAD, NEARBY, PASSED, or UNCERTAIN.
 */
object RouteHazardSequenceCalculator {

    private const val EARTH_RADIUS_METERS = 6371000.0
    private const val TYPICAL_HIKING_SPEED_KMH = 3.5

    /**
     * Orders and contextualizes all corridor hazards and facilities along the planned route.
     */
    fun calculateSequence(
        routePoints: List<Pair<Double, Double>>,
        features: List<UnifiedMapFeature>,
        userLocation: Pair<Double, Double>? = null,
        corridorDistanceMeters: Double = 5000.0,
        userSpeedKmh: Double? = null
    ): List<RouteOrderedHazardItem> {
        if (routePoints.size < 2 || features.isEmpty()) {
            return emptyList()
        }

        // 1. Build cumulative distance table along the route polyline
        val cumDistances = DoubleArray(routePoints.size)
        cumDistances[0] = 0.0
        for (i in 1 until routePoints.size) {
            cumDistances[i] = cumDistances[i - 1] + haversineDistanceMeters(
                routePoints[i - 1].first, routePoints[i - 1].second,
                routePoints[i].first, routePoints[i].second
            )
        }

        // 2. Metric projection helper around route centroid
        val lat0 = routePoints.map { it.first }.average()
        val lon0 = routePoints.map { it.second }.average()
        val cosLat0 = cos(Math.toRadians(lat0))

        fun toMetric(lat: Double, lon: Double): Pair<Double, Double> {
            val x = EARTH_RADIUS_METERS * Math.toRadians(lon - lon0) * cosLat0
            val y = EARTH_RADIUS_METERS * Math.toRadians(lat - lat0)
            return x to y
        }

        val metricRoute = routePoints.map { toMetric(it.first, it.second) }

        // 3. Evaluate user progress along route if currently trekking
        var userProgressMeters: Double? = null
        var isUserOffRoute = false

        if (userLocation != null && userLocation.first != 0.0 && userLocation.second != 0.0) {
            val userMetric = toMetric(userLocation.first, userLocation.second)
            val (userCrossTrack, userAlong, _) = projectPointOntoPolyline(
                userMetric.first, userMetric.second, metricRoute, cumDistances
            )
            if (userCrossTrack > 800.0) {
                isUserOffRoute = true
            } else {
                userProgressMeters = userAlong
            }
        }

        // 4. Process each feature
        val candidateItems = mutableListOf<CalculatedCandidate>()
        val seenFeatureIds = mutableSetOf<String>()

        for (feat in features) {
            if (seenFeatureIds.contains(feat.id)) continue

            val polygon = feat.geometryPolygon
            if (polygon != null && polygon.size >= 3) {
                // Feature has real polygon geometry (disaster polygon or buffer)
                val metricPoly = polygon.map { toMetric(it.first, it.second) }
                val eval = evaluatePolygonAgainstRoute(
                    metricPoly, metricRoute, cumDistances, corridorDistanceMeters
                )
                if (eval != null) {
                    seenFeatureIds.add(feat.id)
                    candidateItems.add(
                        CalculatedCandidate(
                            feature = feat,
                            distanceAlongRouteMeters = eval.distanceAlongRouteMeters,
                            crossTrackDistanceMeters = eval.crossTrackDistanceMeters,
                            intersectionType = eval.intersectionType
                        )
                    )
                }
            } else {
                // Point feature (fire hotspot, facility, weather point, point advisory)
                val ptMetric = toMetric(feat.latitude, feat.longitude)
                val (crossTrack, distAlong, _) = projectPointOntoPolyline(
                    ptMetric.first, ptMetric.second, metricRoute, cumDistances
                )

                // Read radius if defined in details (e.g. disaster affected radius)
                val radiusM = feat.details["Affected Radius"]?.replace(" meters", "")?.toDoubleOrNull() ?: 0.0

                val effectiveCrossTrack = max(0.0, crossTrack - radiusM)
                if (effectiveCrossTrack <= corridorDistanceMeters) {
                    seenFeatureIds.add(feat.id)
                    val intersectionType = if (effectiveCrossTrack <= 60.0) {
                        RouteIntersectionType.DIRECT_INTERSECTION
                    } else {
                        RouteIntersectionType.CORRIDOR_NEARBY
                    }
                    candidateItems.add(
                        CalculatedCandidate(
                            feature = feat,
                            distanceAlongRouteMeters = distAlong,
                            crossTrackDistanceMeters = crossTrack,
                            intersectionType = intersectionType
                        )
                    )
                }
            }
        }

        // 5. Sort candidates strictly ascending by distance along route from origin
        candidateItems.sortBy { it.distanceAlongRouteMeters }

        // 6. Build finalized items with sequence numbering (01, 02...) and live trek status
        val speedKmh = userSpeedKmh?.takeIf { it > 0.5 } ?: TYPICAL_HIKING_SPEED_KMH
        val metersPerMinute = (speedKmh * 1000.0) / 60.0

        return candidateItems.mapIndexed { index, item ->
            val seqNum = index + 1
            val seqLabel = String.format(java.util.Locale.US, "%02d", seqNum)
            val catType = HazardCategoryType.fromFeature(item.feature)

            val trekStatus: HazardTrekStatus
            val distanceAhead: Double?
            val estMinutes: Int?

            if (userProgressMeters != null && !isUserOffRoute) {
                val delta = item.distanceAlongRouteMeters - userProgressMeters
                if (delta < -150.0) {
                    trekStatus = HazardTrekStatus.PASSED
                    distanceAhead = null
                    estMinutes = null
                } else if (abs(delta) <= 250.0) {
                    trekStatus = HazardTrekStatus.NEARBY
                    distanceAhead = max(0.0, delta)
                    estMinutes = 0
                } else {
                    trekStatus = HazardTrekStatus.AHEAD
                    distanceAhead = delta
                    estMinutes = max(1, (delta / metersPerMinute).toInt())
                }
            } else if (isUserOffRoute) {
                trekStatus = HazardTrekStatus.UNCERTAIN
                distanceAhead = null
                estMinutes = null
            } else {
                trekStatus = HazardTrekStatus.AHEAD
                distanceAhead = item.distanceAlongRouteMeters
                estMinutes = max(1, (item.distanceAlongRouteMeters / metersPerMinute).toInt())
            }

            // Freshness / staleness detection
            val epochMs = item.feature.sourceTimestampEpochMs
            val isStale = if (epochMs != null && epochMs > 0) {
                (System.currentTimeMillis() - epochMs) > (6 * 3600 * 1000L) // > 6 hours old
            } else false

            val obsText = buildObservationText(item.feature, isStale)

            RouteOrderedHazardItem(
                sequenceNumber = seqNum,
                sequenceLabel = seqLabel,
                feature = item.feature,
                distanceAlongRouteMeters = round(item.distanceAlongRouteMeters),
                crossTrackDistanceMeters = round(item.crossTrackDistanceMeters),
                intersectionType = item.intersectionType,
                trekStatus = trekStatus,
                distanceAheadMeters = distanceAhead?.let { round(it) },
                estimatedMinutesAhead = estMinutes,
                categoryType = catType,
                severity = item.feature.details["Severity"] ?: "WARNING",
                observationTimeText = obsText,
                isStale = isStale
            )
        }
    }

    private data class CalculatedCandidate(
        val feature: UnifiedMapFeature,
        val distanceAlongRouteMeters: Double,
        val crossTrackDistanceMeters: Double,
        val intersectionType: RouteIntersectionType
    )

    private data class PolygonEvalResult(
        val distanceAlongRouteMeters: Double,
        val crossTrackDistanceMeters: Double,
        val intersectionType: RouteIntersectionType
    )

    /**
     * Determines whether a polygon intersects the route polyline or lies within the corridor buffer.
     * When intersecting, returns the distance along the route to the earliest intersection point.
     */
    private fun evaluatePolygonAgainstRoute(
        metricPoly: List<Pair<Double, Double>>,
        metricRoute: List<Pair<Double, Double>>,
        cumDistances: DoubleArray,
        corridorDistanceMeters: Double
    ): PolygonEvalResult? {
        var earliestIntersectionAlong: Double? = null

        // 1. Check segment-by-segment intersections between route and polygon edges
        for (r in 0 until metricRoute.size - 1) {
            val rP1 = metricRoute[r]
            val rP2 = metricRoute[r + 1]
            val segLen = haversineSegmentLength(rP1, rP2)

            for (p in metricPoly.indices) {
                val pP1 = metricPoly[p]
                val pP2 = metricPoly[(p + 1) % metricPoly.size]

                val t = segmentIntersectionT(rP1, rP2, pP1, pP2)
                if (t != null && t in 0.0..1.0) {
                    val along = cumDistances[r] + (t * segLen)
                    if (earliestIntersectionAlong == null || along < earliestIntersectionAlong) {
                        earliestIntersectionAlong = along
                    }
                }
            }
        }

        if (earliestIntersectionAlong != null) {
            return PolygonEvalResult(
                distanceAlongRouteMeters = earliestIntersectionAlong,
                crossTrackDistanceMeters = 0.0,
                intersectionType = RouteIntersectionType.DIRECT_INTERSECTION
            )
        }

        // 2. Check if any route vertex is inside the polygon (point-in-polygon)
        for (r in metricRoute.indices) {
            if (isPointInPolygon(metricRoute[r].first, metricRoute[r].second, metricPoly)) {
                return PolygonEvalResult(
                    distanceAlongRouteMeters = cumDistances[r],
                    crossTrackDistanceMeters = 0.0,
                    intersectionType = RouteIntersectionType.DIRECT_INTERSECTION
                )
            }
        }

        // 3. If no direct intersection, find closest polygon vertex to the route
        var minCrossTrack = Double.MAX_VALUE
        var bestAlong = 0.0

        for (vertex in metricPoly) {
            val (dist, along, _) = projectPointOntoPolyline(
                vertex.first, vertex.second, metricRoute, cumDistances
            )
            if (dist < minCrossTrack) {
                minCrossTrack = dist
                bestAlong = along
            }
        }

        if (minCrossTrack <= corridorDistanceMeters) {
            return PolygonEvalResult(
                distanceAlongRouteMeters = bestAlong,
                crossTrackDistanceMeters = minCrossTrack,
                intersectionType = RouteIntersectionType.CORRIDOR_NEARBY
            )
        }

        return null // Outside corridor
    }

    /**
     * Projects a point onto a polyline, returning (crossTrackDistance, distanceAlongRoute, segmentIndex).
     */
    private fun projectPointOntoPolyline(
        px: Double,
        py: Double,
        polyline: List<Pair<Double, Double>>,
        cumDistances: DoubleArray
    ): Triple<Double, Double, Int> {
        var minDistance = Double.MAX_VALUE
        var bestAlong = 0.0
        var bestSeg = 0

        for (i in 0 until polyline.size - 1) {
            val (x1, y1) = polyline[i]
            val (x2, y2) = polyline[i + 1]

            val dx = x2 - x1
            val dy = y2 - y1
            val segLenSq = dx * dx + dy * dy

            val (dist, t) = if (segLenSq == 0.0) {
                hypot(px - x1, py - y1) to 0.0
            } else {
                val tClamped = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / segLenSq))
                val projX = x1 + tClamped * dx
                val projY = y1 + tClamped * dy
                hypot(px - projX, py - projY) to tClamped
            }

            if (dist < minDistance) {
                minDistance = dist
                val segLen = sqrt(segLenSq)
                bestAlong = cumDistances[i] + (t * segLen)
                bestSeg = i
            }
        }

        return Triple(minDistance, bestAlong, bestSeg)
    }

    /**
     * Line segment intersection test returning projection t on segment (p1->p2).
     */
    private fun segmentIntersectionT(
        p1: Pair<Double, Double>,
        p2: Pair<Double, Double>,
        p3: Pair<Double, Double>,
        p4: Pair<Double, Double>
    ): Double? {
        val x1 = p1.first; val y1 = p1.second
        val x2 = p2.first; val y2 = p2.second
        val x3 = p3.first; val y3 = p3.second
        val x4 = p4.first; val y4 = p4.second

        val denom = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1)
        if (denom == 0.0) return null // Parallel

        val ua = ((x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)) / denom
        val ub = ((x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3)) / denom

        return if (ua in 0.0..1.0 && ub in 0.0..1.0) ua else null
    }

    /**
     * Standard ray-casting point-in-polygon test.
     */
    private fun isPointInPolygon(px: Double, py: Double, polygon: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (xi, yi) = polygon[i]
            val (xj, yj) = polygon[j]
            val intersect = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun haversineSegmentLength(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        return hypot(p2.first - p1.first, p2.second - p1.second)
    }

    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)

        val a = sin(dPhi / 2.0).pow(2.0) + cos(phi1) * cos(phi2) * sin(dLambda / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    private fun buildObservationText(feature: UnifiedMapFeature, isStale: Boolean): String {
        val source = feature.sourceName
        val agePart = if (feature.sourceTimestampEpochMs != null && feature.sourceTimestampEpochMs > 0) {
            val mins = ((System.currentTimeMillis() - feature.sourceTimestampEpochMs) / 60000L).coerceAtLeast(0)
            if (mins < 60) "${mins}m ago" else "${mins / 60}h ago"
        } else "Verified Feed"

        return if (isStale) {
            "$source • Cached ($agePart) • Needs Refresh"
        } else {
            "$source • $agePart • Active"
        }
    }
}

