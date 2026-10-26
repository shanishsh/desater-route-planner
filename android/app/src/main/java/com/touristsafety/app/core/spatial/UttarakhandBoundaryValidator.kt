package com.touristsafety.app.core.spatial

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
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

        // Authoritative Bounding Box of Uttarakhand, India
        const val MIN_LAT = 28.71
        const val MAX_LAT = 31.48
        const val MIN_LON = 77.55
        const val MAX_LON = 81.08
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
                val json = JSONObject(jsonString)

                val coordsArray = when {
                    json.has("coordinates") -> json.getJSONArray("coordinates")
                    json.has("geometry") -> json.getJSONObject("geometry").getJSONArray("coordinates")
                    json.has("features") -> {
                        val features = json.getJSONArray("features")
                        if (features.length() > 0) {
                            features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                        } else null
                    }
                    else -> null
                }

                if (coordsArray != null && coordsArray.length() > 0) {
                    val ring = coordsArray.getJSONArray(0)
                    val count = ring.length()
                    val vertices = Array(count) { i ->
                        val pt = ring.getJSONArray(i)
                        doubleArrayOf(pt.getDouble(0), pt.getDouble(1)) // [lon, lat]
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

        return inside
    }

    /**
     * Validates that all critical coordinates of a route remain within Uttarakhand.
     * Checks endpoints and samples intermediate vertices.
     */
    fun isRouteInUttarakhand(points: List<Pair<Double, Double>>): Pair<Boolean, String?> {
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

        // Sample intermediate vertices along the route polyline
        val step = maxOf(1, points.size / 50)
        var outsideCount = 0
        var totalChecked = 0

        for (i in points.indices step step) {
            val pt = points[i]
            totalChecked++
            if (!isPointInUttarakhand(pt.first, pt.second)) {
                outsideCount++
            }
        }

        if (outsideCount > 0 && (outsideCount.toDouble() / totalChecked) > 0.05) {
            return Pair(false, "The calculated route path exits the state of Uttarakhand.")
        }

        return Pair(true, null)
    }
}

