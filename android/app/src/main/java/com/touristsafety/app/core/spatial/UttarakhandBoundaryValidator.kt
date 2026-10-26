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

        return inside
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

        // For driving: both origin and destination are verified in Uttarakhand.
        // Inter-district routes (e.g., Dehradun to Nainital) naturally transit connecting
        // northern plains highway corridors (e.g. NH 74 / NH 734).
        if (travelMode.equals("driving", ignoreCase = true)) {
            for (pt in points) {
                if (pt.first < 28.0 || pt.first > 32.0 || pt.second < 77.0 || pt.second > 81.5) {
                    return Pair(false, "The calculated route path deviates excessively outside the regional transit corridor.")
                }
            }
            return Pair(true, null)
        }

        // Sample intermediate vertices along the route polyline for pedestrian / hiking routes
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
            return Pair(false, "Calculated trekking path exits the supported Uttarakhand administrative boundary.")
        }

        return Pair(true, null)
    }
}

