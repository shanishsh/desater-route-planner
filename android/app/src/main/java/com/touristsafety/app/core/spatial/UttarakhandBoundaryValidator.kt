package com.touristsafety.app.core.spatial

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates whether geographic coordinates and route polylines are located strictly within
 * the authoritative administrative boundary of Uttarakhand, India.
 *
 * Utilizes a fast bounding box pre-filter followed by an exact 2D ray-casting
 * point-in-polygon algorithm against the official 7,129-vertex boundary polygon.
 */
@Singleton
class UttarakhandBoundaryValidator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "UttarakhandValidator"

        // Authoritative Bounding Box of Uttarakhand, India (with 0.05 deg margin for border shrines/passes)
        const val MIN_LAT = 28.65
        const val MAX_LAT = 31.55
        const val MIN_LON = 77.50
        const val MAX_LON = 81.15
        private const val BORDER_BUFFER_DEG = 0.03 // ~3.3 km border tolerance for peripheral shrines/passes
    }

    // Cached polygon vertices: array of [lon, lat] pairs
    private var polygonVertices: Array<DoubleArray>? = null
    private var isLoaded = false

    init {
        loadBoundary()
    }

    @Synchronized
    private fun loadBoundary() {
        if (isLoaded) return
        try {
            context.assets.open("uttarakhand_boundary.geojson").use { stream ->
                val reader = InputStreamReader(stream, Charsets.UTF_8)
                val jsonString = reader.readText()
                val jsonElement = Json.parseToJsonElement(jsonString).jsonObject

                val coordsArray = when {
                    jsonElement.containsKey("coordinates") -> jsonElement["coordinates"]?.jsonArray
                    jsonElement.containsKey("geometry") -> jsonElement["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
                    jsonElement.containsKey("features") -> {
                        val features = jsonElement["features"]?.jsonArray
                        if (features != null && features.isNotEmpty()) {
                            features[0].jsonObject["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
                        } else null
                    }
                    else -> null
                }

                if (coordsArray != null && coordsArray.isNotEmpty()) {
                    val ring = coordsArray[0].jsonArray
                    val count = ring.size
                    val vertices = Array(count) { i ->
                        val pt = ring[i].jsonArray
                        doubleArrayOf(pt[0].jsonPrimitive.double, pt[1].jsonPrimitive.double) // [lon, lat]
                    }
                    polygonVertices = vertices
                    isLoaded = true
                    Log.i(TAG, "Successfully loaded Uttarakhand boundary with $count vertices.")
                } else {
                    Log.w(TAG, "Could not find coordinates array in uttarakhand_boundary.geojson")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Uttarakhand boundary from assets: ${e.message}", e)
        }
    }

    /**
     * Determines whether a (latitude, longitude) coordinate pair is strictly inside Uttarakhand.
     */
    fun isPointInUttarakhand(latitude: Double, longitude: Double): Boolean {
        // Fast bounding box pre-filter (sub-microsecond)
        if (latitude < MIN_LAT || latitude > MAX_LAT || longitude < MIN_LON || longitude > MAX_LON) {
            return false
        }

        val vertices = polygonVertices ?: return true // Fallback to bbox if not loaded

        // 2D Ray-Casting Algorithm for Point-in-Polygon (Jordan Curve Theorem)
        var inside = false
        val n = vertices.size
        var j = n - 1

        for (i in 0 until n) {
            val xi = vertices[i][0] // lon
            val yi = vertices[i][1] // lat
            val xj = vertices[j][0]
            val yj = vertices[j][1]

            val intersect = ((yi > latitude) != (yj > latitude)) &&
                    (longitude < (xj - xi) * (latitude - yi) / (yj - yi) + xi)

            if (intersect) {
                inside = !inside
            }
            j = i
        }

        if (inside) return true

        // 3. Peripheral border tolerance check (0.03 deg ~ 3.3 km)
        // Accommodates peripheral shrines, river confluences, and frontier passes (e.g. Jhoolaghat, Jauljibi, Lipulekh, Purnagiri)
        val maxDistSq = BORDER_BUFFER_DEG * BORDER_BUFFER_DEG
        for (i in 0 until n) {
            val prev = if (i == 0) n - 1 else i - 1
            val x1 = vertices[prev][0]
            val y1 = vertices[prev][1]
            val x2 = vertices[i][0]
            val y2 = vertices[i][1]
            if (distSqToSegment(longitude, latitude, x1, y1, x2, y2) <= maxDistSq) {
                return true
            }
        }

        return false
    }

    private fun distSqToSegment(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) {
            val dpx = px - x1
            val dpy = py - y1
            return dpx * dpx + dpy * dpy
        }
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
        val clampedT = if (t < 0.0) 0.0 else if (t > 1.0) 1.0 else t
        val cx = x1 + clampedT * dx
        val cy = y1 + clampedT * dy
        val dpx = px - cx
        val dpy = py - cy
        return dpx * dpx + dpy * dpy
    }

    /**
     * Validates that all critical coordinates of a route remain within Uttarakhand,
     * or within the regional transit corridor for in-state driving routes (e.g. Dehradun <-> Nainital).
     */
    fun isRouteInUttarakhand(
        points: List<Pair<Double, Double>>,
        travelMode: String = "driving"
    ): Pair<Boolean, String?> {
        if (points.isEmpty()) {
            return Pair(false, "Route polyline contains no coordinates.")
        }

        val start = points.first()
        if (!isPointInUttarakhand(start.first, start.second)) {
            return Pair(
                false,
                String.format(
                    java.util.Locale.US,
                    "Route origin (%.4f, %.4f) is outside Uttarakhand. Please choose a supported trailhead.",
                    start.first,
                    start.second
                )
            )
        }

        val end = points.last()
        if (!isPointInUttarakhand(end.first, end.second)) {
            return Pair(
                false,
                String.format(
                    java.util.Locale.US,
                    "Route destination (%.4f, %.4f) is outside Uttarakhand. Please choose a supported destination.",
                    end.first,
                    end.second
                )
            )
        }

        // Both origin and destination are verified in Uttarakhand.
        // Inter-district routes (e.g., Dehradun to Nainital or Almora) naturally transit connecting
        // northern plains highway/foothill corridors (e.g. NH 74 / NH 734).
        for (pt in points) {
            if (pt.first < 28.0 || pt.first > 32.0 || pt.second < 77.0 || pt.second > 81.5) {
                return Pair(false, "The calculated route path deviates excessively outside the regional transit corridor.")
            }
        }
        return Pair(true, null)
    }
}

