package com.touristsafety.app.core.map

import android.graphics.Color
import android.util.Log
import com.touristsafety.app.core.model.MapFeatureCategory
import com.touristsafety.app.core.model.UnifiedMapFeature
import com.touristsafety.app.feature.map.MapUiState
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Manages MapLibre Native vector sources and style layers following standard GeoJSON specifications.
 * Ensures proper layer hierarchy, source updates, polygon rendering (including holes and MultiPolygons),
 * and query-rendered-features hit testing for tap interactions.
 */
object MapLibreLayerManager {

    private const val TAG = "MapLibreLayerManager"

    // Sources
    const val SOURCE_CORRIDOR = "source-route-corridor"
    const val SOURCE_ROUTE_PRIMARY = "source-route-primary"
    const val SOURCE_ROUTE_SECONDARY = "source-route-secondary"
    const val SOURCE_DISASTER_POLYGONS = "source-disaster-polygons"
    const val SOURCE_FOREST_POLYGONS = "source-forest-polygons"
    const val SOURCE_ACTIVE_FIRES = "source-active-fires"
    const val SOURCE_SAFE_HAVENS = "source-safe-havens"
    const val SOURCE_WEATHER_POINTS = "source-weather-points"

    // Layers
    const val LAYER_CORRIDOR_FILL = "layer-corridor-fill"
    const val LAYER_CORRIDOR_LINE = "layer-corridor-line"
    const val LAYER_FOREST_FILL = "layer-forest-fill"
    const val LAYER_FOREST_LINE = "layer-forest-line"
    const val LAYER_DISASTER_FILL = "layer-disaster-fill"
    const val LAYER_DISASTER_LINE = "layer-disaster-line"
    const val LAYER_DISASTER_POINTS_CIRCLE = "layer-disaster-points-circle"
    const val LAYER_ROUTE_SECONDARY_LINE = "layer-route-secondary-line"
    const val LAYER_ROUTE_PRIMARY_LINE = "layer-route-primary-line"
    const val LAYER_ACTIVE_FIRES_CIRCLE = "layer-active-fires-circle"
    const val LAYER_SAFE_HAVENS_CIRCLE = "layer-safe-havens-circle"
    const val LAYER_WEATHER_POINTS_CIRCLE = "layer-weather-points-circle"

    private const val EMPTY_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"

    /**
     * Initializes all vector GeoJSON sources and style layers in exact bottom-to-top z-order.
     * Safe to call multiple times; existing sources and layers are preserved.
     */
    fun setupSourcesAndLayers(style: Style) {
        if (!style.isFullyLoaded) {
            Log.d(TAG, "Style not fully loaded yet; deferring source/layer creation.")
            return
        }

        try {
            // 1. Corridor Source & Layers (Bottom-most)
            if (style.getSource(SOURCE_CORRIDOR) == null) {
                style.addSource(GeoJsonSource(SOURCE_CORRIDOR, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_CORRIDOR_FILL) == null) {
                val corridorFill = FillLayer(LAYER_CORRIDOR_FILL, SOURCE_CORRIDOR).apply {
                    setProperties(
                        fillColor(Color.argb(35, 3, 169, 244)), // Light Blue #03A9F4
                        fillOpacity(0.20f)
                    )
                }
                style.addLayer(corridorFill)
            }
            if (style.getLayer(LAYER_CORRIDOR_LINE) == null) {
                val corridorLine = LineLayer(LAYER_CORRIDOR_LINE, SOURCE_CORRIDOR).apply {
                    setProperties(
                        lineColor(Color.argb(160, 2, 136, 209)), // Primary Blue #0288D1
                        lineWidth(2.0f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(corridorLine)
            }

            // 2. Forest Polygons Source & Layers
            if (style.getSource(SOURCE_FOREST_POLYGONS) == null) {
                style.addSource(GeoJsonSource(SOURCE_FOREST_POLYGONS, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_FOREST_FILL) == null) {
                val forestFill = FillLayer(LAYER_FOREST_FILL, SOURCE_FOREST_POLYGONS).apply {
                    setProperties(
                        fillColor(Color.argb(55, 46, 125, 50)), // Forest Green #2E7D32
                        fillOpacity(0.22f)
                    )
                }
                style.addLayer(forestFill)
            }
            if (style.getLayer(LAYER_FOREST_LINE) == null) {
                val forestLine = LineLayer(LAYER_FOREST_LINE, SOURCE_FOREST_POLYGONS).apply {
                    setProperties(
                        lineColor(Color.rgb(76, 175, 80)), // #4CAF50
                        lineWidth(1.5f),
                        lineJoin(Property.LINE_JOIN_ROUND)
                    )
                }
                style.addLayer(forestLine)
            }

            // 3. Disaster Alerts Polygons Source & Layers
            if (style.getSource(SOURCE_DISASTER_POLYGONS) == null) {
                style.addSource(GeoJsonSource(SOURCE_DISASTER_POLYGONS, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_DISASTER_FILL) == null) {
                val disasterFill = FillLayer(LAYER_DISASTER_FILL, SOURCE_DISASTER_POLYGONS).apply {
                    setProperties(
                        fillColor(Color.argb(70, 229, 57, 53)), // Emergency Red #E53935
                        fillOpacity(0.28f)
                    )
                }
                style.addLayer(disasterFill)
            }
            if (style.getLayer(LAYER_DISASTER_LINE) == null) {
                val disasterLine = LineLayer(LAYER_DISASTER_LINE, SOURCE_DISASTER_POLYGONS).apply {
                    setProperties(
                        lineColor(Color.rgb(211, 47, 47)), // #D32F2F
                        lineWidth(2.5f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(disasterLine)
            }
            if (style.getLayer(LAYER_DISASTER_POINTS_CIRCLE) == null) {
                val disasterPointsCircle = CircleLayer(LAYER_DISASTER_POINTS_CIRCLE, SOURCE_DISASTER_POLYGONS).apply {
                    setProperties(
                        circleColor(Color.rgb(211, 47, 47)), // Emergency Red #D32F2F
                        circleRadius(7.0f),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(2.0f)
                    )
                }
                style.addLayer(disasterPointsCircle)
            }

            // 4. Route Lines Sources & Layers
            if (style.getSource(SOURCE_ROUTE_SECONDARY) == null) {
                style.addSource(GeoJsonSource(SOURCE_ROUTE_SECONDARY, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_ROUTE_SECONDARY_LINE) == null) {
                val secLine = LineLayer(LAYER_ROUTE_SECONDARY_LINE, SOURCE_ROUTE_SECONDARY).apply {
                    setProperties(
                        lineColor(Color.rgb(144, 164, 174)), // Slate Gray #90A4AE
                        lineWidth(4.0f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(secLine)
            }

            if (style.getSource(SOURCE_ROUTE_PRIMARY) == null) {
                style.addSource(GeoJsonSource(SOURCE_ROUTE_PRIMARY, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_ROUTE_PRIMARY_LINE) == null) {
                val primLine = LineLayer(LAYER_ROUTE_PRIMARY_LINE, SOURCE_ROUTE_PRIMARY).apply {
                    setProperties(
                        lineColor(Color.rgb(2, 136, 209)), // Active Blue #0288D1
                        lineWidth(6.0f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(primLine)
            }

            // 5. Point Hazard & Infrastructure Circle Layers (Top-most before labels)
            if (style.getSource(SOURCE_SAFE_HAVENS) == null) {
                style.addSource(GeoJsonSource(SOURCE_SAFE_HAVENS, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_SAFE_HAVENS_CIRCLE) == null) {
                val havenCircle = CircleLayer(LAYER_SAFE_HAVENS_CIRCLE, SOURCE_SAFE_HAVENS).apply {
                    setProperties(
                        circleColor(Color.rgb(46, 125, 50)), // Green #2E7D32
                        circleRadius(6.5f),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(1.8f)
                    )
                }
                style.addLayer(havenCircle)
            }

            if (style.getSource(SOURCE_WEATHER_POINTS) == null) {
                style.addSource(GeoJsonSource(SOURCE_WEATHER_POINTS, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_WEATHER_POINTS_CIRCLE) == null) {
                val weatherCircle = CircleLayer(LAYER_WEATHER_POINTS_CIRCLE, SOURCE_WEATHER_POINTS).apply {
                    setProperties(
                        circleColor(Color.rgb(2, 136, 209)), // Cyan Blue #0288D1
                        circleRadius(5.5f),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(1.5f)
                    )
                }
                style.addLayer(weatherCircle)
            }

            if (style.getSource(SOURCE_ACTIVE_FIRES) == null) {
                style.addSource(GeoJsonSource(SOURCE_ACTIVE_FIRES, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_ACTIVE_FIRES_CIRCLE) == null) {
                val fireCircle = CircleLayer(LAYER_ACTIVE_FIRES_CIRCLE, SOURCE_ACTIVE_FIRES).apply {
                    setProperties(
                        circleColor(Color.rgb(230, 81, 0)), // Fire Orange-Red #E65100
                        circleRadius(7.5f),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(2.0f)
                    )
                }
                style.addLayer(fireCircle)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MapLibre sources and layers: ${e.message}", e)
        }
    }

    /**
     * Updates all GeoJSON sources with the latest state data without destroying layers.
     */
    fun updateData(style: Style, state: MapUiState) {
        if (!style.isFullyLoaded) return
        setupSourcesAndLayers(style)

        try {
            // 1. Update Corridor GeoJSON
            val corridorSource = style.getSourceAs<GeoJsonSource>(SOURCE_CORRIDOR)
            val corrJson = if (!state.corridorGeoJson.isNullOrBlank()) {
                state.corridorGeoJson
            } else {
                buildCorridorGeoJson(state.corridorPolygonCoordinates)
            }
            corridorSource?.setGeoJson(corrJson)

            // 2. Update Primary & Secondary Route GeoJSON
            val primSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_PRIMARY)
            val primJson = if (!state.routePrimaryGeoJson.isNullOrBlank()) {
                state.routePrimaryGeoJson
            } else {
                buildLineStringGeoJson(state.routeCoordinates)
            }
            primSource?.setGeoJson(primJson)

            val secSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_SECONDARY)
            val secJson = if (!state.routeSecondaryGeoJson.isNullOrBlank()) {
                state.routeSecondaryGeoJson
            } else if (state.selectedAlternativeId != null && state.primaryRouteCoordinates.size >= 2) {
                buildLineStringGeoJson(state.primaryRouteCoordinates)
            } else {
                EMPTY_GEOJSON
            }
            secSource?.setGeoJson(secJson)

            // 3. Update Disaster Features (Polygons & Point Advisories)
            val disasterSource = style.getSourceAs<GeoJsonSource>(SOURCE_DISASTER_POLYGONS)
            val disasterJson = if (!state.hazardsGeoJson.isNullOrBlank()) {
                state.hazardsGeoJson
            } else {
                val disasterFeatures = state.allFeatures.filter { it.category == MapFeatureCategory.HAZARD_ZONE && it.geometryPolygon != null }
                buildPolygonFeatureCollection(disasterFeatures)
            }
            disasterSource?.setGeoJson(disasterJson)

            // 4. Update Forest Polygons
            val forestSource = style.getSourceAs<GeoJsonSource>(SOURCE_FOREST_POLYGONS)
            val forestJson = if (!state.forestsGeoJson.isNullOrBlank()) {
                state.forestsGeoJson
            } else {
                val forestFeatures = state.allFeatures.filter { it.category == MapFeatureCategory.FOREST_AREA && it.geometryPolygon != null }
                buildPolygonFeatureCollection(forestFeatures)
            }
            forestSource?.setGeoJson(forestJson)

            // 5. Update Active Fires
            val fireSource = style.getSourceAs<GeoJsonSource>(SOURCE_ACTIVE_FIRES)
            val fireJson = if (!state.firesGeoJson.isNullOrBlank()) {
                state.firesGeoJson
            } else {
                val fireFeatures = state.allFeatures.filter { it.category == MapFeatureCategory.ACTIVE_FIRE_DETECTION }
                buildPointFeatureCollection(fireFeatures)
            }
            fireSource?.setGeoJson(fireJson)

            // 6. Update Safe Havens
            val havenSource = style.getSourceAs<GeoJsonSource>(SOURCE_SAFE_HAVENS)
            val havenJson = if (!state.facilitiesGeoJson.isNullOrBlank()) {
                state.facilitiesGeoJson
            } else {
                val havenFeatures = state.allFeatures.filter {
                    it.category == MapFeatureCategory.SHELTER ||
                    it.category == MapFeatureCategory.HOSPITAL ||
                    it.category == MapFeatureCategory.POLICE_STATION ||
                    it.category == MapFeatureCategory.TOURIST_ASSISTANCE
                }
                buildPointFeatureCollection(havenFeatures)
            }
            havenSource?.setGeoJson(havenJson)

            // 7. Update Weather Points
            val weatherSource = style.getSourceAs<GeoJsonSource>(SOURCE_WEATHER_POINTS)
            val weatherJson = if (!state.weatherGeoJson.isNullOrBlank()) {
                state.weatherGeoJson
            } else {
                val weatherFeatures = state.allFeatures.filter { it.category == MapFeatureCategory.WEATHER_POINT }
                buildPointFeatureCollection(weatherFeatures)
            }
            weatherSource?.setGeoJson(weatherJson)

            // 8. Update Layer Visibilities according to state.layerVisibilities & selectedFilter chip
            updateLayerVisibilities(style, state)

        } catch (e: Exception) {
            Log.e(TAG, "Error updating GeoJSON sources in style: ${e.message}", e)
        }
    }

    private fun updateLayerVisibilities(style: Style, state: MapUiState) {
        val vis = state.layerVisibilities
        val filterCat = state.selectedFilter?.category

        val firesVis = (filterCat == null || filterCat == MapFeatureCategory.ACTIVE_FIRE_DETECTION) &&
                (vis[MapFeatureCategory.ACTIVE_FIRE_DETECTION] != false)

        val hazardVis = (filterCat == null || filterCat == MapFeatureCategory.HAZARD_ZONE) &&
                (vis[MapFeatureCategory.HAZARD_ZONE] != false)

        val forestVis = (filterCat == null || filterCat == MapFeatureCategory.FOREST_AREA) &&
                (vis[MapFeatureCategory.FOREST_AREA] != false)

        val weatherVis = (filterCat == null || filterCat == MapFeatureCategory.WEATHER_POINT) &&
                (vis[MapFeatureCategory.WEATHER_POINT] != false)

        val shelterVis = (filterCat == null ||
                filterCat == MapFeatureCategory.SHELTER ||
                filterCat == MapFeatureCategory.HOSPITAL ||
                filterCat == MapFeatureCategory.POLICE_STATION ||
                filterCat == MapFeatureCategory.FIRE_STATION ||
                filterCat == MapFeatureCategory.TOURIST_ASSISTANCE) &&
                (vis[MapFeatureCategory.SHELTER] != false)

        style.getLayer(LAYER_ACTIVE_FIRES_CIRCLE)?.setProperties(
            visibility(if (firesVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_DISASTER_FILL)?.setProperties(
            visibility(if (hazardVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_DISASTER_LINE)?.setProperties(
            visibility(if (hazardVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_DISASTER_POINTS_CIRCLE)?.setProperties(
            visibility(if (hazardVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_FOREST_FILL)?.setProperties(
            visibility(if (forestVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_FOREST_LINE)?.setProperties(
            visibility(if (forestVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_WEATHER_POINTS_CIRCLE)?.setProperties(
            visibility(if (weatherVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_SAFE_HAVENS_CIRCLE)?.setProperties(
            visibility(if (shelterVis) Property.VISIBLE else Property.NONE)
        )
    }

    private fun buildCorridorGeoJson(rings: List<List<Pair<Double, Double>>>): String {
        if (rings.isEmpty()) return EMPTY_GEOJSON
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        rings.forEachIndexed { rIdx, ring ->
            if (ring.size >= 3) {
                if (rIdx > 0) sb.append(",")
                sb.append("{\"type\":\"Feature\",\"properties\":{\"layer\":\"corridor\"},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[")
                ring.forEachIndexed { pIdx, pt ->
                    if (pIdx > 0) sb.append(",")
                    // RFC 7946: [longitude, latitude]
                    sb.append("[${pt.second},${pt.first}]")
                }
                // Ensure closed ring
                if (ring.first() != ring.last()) {
                    sb.append(",[${ring.first().second},${ring.first().first}]")
                }
                sb.append("]]}}")
            }
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun buildLineStringGeoJson(coordinates: List<Pair<Double, Double>>): String {
        if (coordinates.size < 2) return EMPTY_GEOJSON
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        coordinates.forEachIndexed { idx, pt ->
            if (idx > 0) sb.append(",")
            sb.append("[${pt.second},${pt.first}]")
        }
        sb.append("]}}]}")
        return sb.toString()
    }

    private fun buildPolygonFeatureCollection(features: List<UnifiedMapFeature>): String {
        if (features.isEmpty()) return EMPTY_GEOJSON
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        var count = 0
        features.forEach { feat ->
            val ring = feat.geometryPolygon ?: return@forEach
            if (ring.size >= 3) {
                if (count > 0) sb.append(",")
                val safeTitle = feat.title.replace("\"", "\\\"")
                val safeSub = (feat.subtitle ?: "").replace("\"", "\\\"")
                sb.append("{\"type\":\"Feature\",\"id\":\"${feat.id}\",\"properties\":{")
                sb.append("\"id\":\"${feat.id}\",\"title\":\"$safeTitle\",\"subtitle\":\"$safeSub\",\"category\":\"${feat.category.name}\"")
                sb.append("},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[")
                ring.forEachIndexed { pIdx, pt ->
                    if (pIdx > 0) sb.append(",")
                    sb.append("[${pt.second},${pt.first}]")
                }
                if (ring.first() != ring.last()) {
                    sb.append(",[${ring.first().second},${ring.first().first}]")
                }
                sb.append("]]}}")
                count++
            }
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun buildPointFeatureCollection(features: List<UnifiedMapFeature>): String {
        if (features.isEmpty()) return EMPTY_GEOJSON
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        features.forEachIndexed { idx, feat ->
            if (idx > 0) sb.append(",")
            val safeTitle = feat.title.replace("\"", "\\\"")
            val safeSub = (feat.subtitle ?: "").replace("\"", "\\\"")
            sb.append("{\"type\":\"Feature\",\"id\":\"${feat.id}\",\"properties\":{")
            sb.append("\"id\":\"${feat.id}\",\"title\":\"$safeTitle\",\"subtitle\":\"$safeSub\",\"category\":\"${feat.category.name}\"")
            sb.append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${feat.longitude},${feat.latitude}]}}")
        }
        sb.append("]}")
        return sb.toString()
    }
}
