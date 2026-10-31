package com.touristsafety.app.core.map

import android.graphics.Color
import android.util.Log
import com.touristsafety.app.core.model.MapFeatureCategory
import com.touristsafety.app.core.model.UnifiedMapFeature
import com.touristsafety.app.feature.map.MapUiState
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Manages MapLibre Native vector sources and style layers following standard GeoJSON specifications.
 * Ensures proper layer hierarchy, source updates, polygon rendering (including holes and MultiPolygons),
 * and query-rendered-features hit testing for tap interactions.
 */
object MapLibreLayerManager {

    private const val TAG = "MapLibreLayerManager"

    // High-performance GeoJSON change-detection cache to prevent redundant C++ JNI source updates
    private var lastLoadedStyleRef: Int? = null
    private var lastCorridorGeoJson: String? = null
    private var lastRoutePrimaryGeoJson: String? = null
    private var lastRouteSecondaryGeoJson: String? = null
    private var lastDisasterGeoJson: String? = null
    private var lastForestGeoJson: String? = null
    private var lastFireGeoJson: String? = null
    private var lastHavenGeoJson: String? = null
    private var lastWeatherGeoJson: String? = null
    private var lastSlopeGeoJson: String? = null
    private var lastWaterwayGeoJson: String? = null
    private var lastSelectedPointGeoJson: String? = null
    private var lastHistoricalLandslidesGeoJson: String? = null
    private var lastHazardSequenceGeoJson: String? = null
    private var lastCommunityGeoJson: String? = null

    /**
     * Clears all GeoJSON change-detection hashes.
     * Called whenever a new MapLibre Style instance is loaded to ensure all layers are re-populated.
     */
    fun resetDirtyCache() {
        lastCorridorGeoJson = null
        lastRoutePrimaryGeoJson = null
        lastRouteSecondaryGeoJson = null
        lastDisasterGeoJson = null
        lastForestGeoJson = null
        lastFireGeoJson = null
        lastHavenGeoJson = null
        lastWeatherGeoJson = null
        lastSlopeGeoJson = null
        lastWaterwayGeoJson = null
        lastSelectedPointGeoJson = null
        lastHistoricalLandslidesGeoJson = null
        lastHazardSequenceGeoJson = null
        lastCommunityGeoJson = null
    }

    // Sources
    const val SOURCE_UTTARAKHAND_MASK = "source-uttarakhand-mask"
    const val SOURCE_UTTARAKHAND_BOUNDARY = "source-uttarakhand-boundary"
    const val SOURCE_CORRIDOR = "source-route-corridor"
    const val SOURCE_ROUTE_PRIMARY = "source-route-primary"
    const val SOURCE_ROUTE_SECONDARY = "source-route-secondary"
    const val SOURCE_DISASTER_POLYGONS = "source-disaster-polygons"
    const val SOURCE_FOREST_POLYGONS = "source-forest-polygons"
    const val SOURCE_ACTIVE_FIRES = "source-active-fires"
    const val SOURCE_COMMUNITY_HAZARDS = "source-community-hazards"
    const val SOURCE_SAFE_HAVENS = "source-safe-havens"
    const val SOURCE_WEATHER_POINTS = "source-weather-points"
    const val SOURCE_SELECTED_POINT = "source-selected-point"

    // Layers
    const val LAYER_UTTARAKHAND_MASK_FILL = "layer-uttarakhand-mask-fill"
    const val LAYER_UTTARAKHAND_BOUNDARY_LINE = "layer-uttarakhand-boundary-line"
    const val LAYER_CORRIDOR_FILL = "layer-corridor-fill"
    const val LAYER_CORRIDOR_LINE = "layer-corridor-line"
    const val LAYER_FOREST_FILL = "layer-forest-fill"
    const val LAYER_FOREST_LINE = "layer-forest-line"
    const val LAYER_DISASTER_FILL = "layer-disaster-fill"
    const val LAYER_DISASTER_LINE = "layer-disaster-line"
    const val LAYER_DISASTER_POINTS_CIRCLE = "layer-disaster-points-circle"
    const val LAYER_DISASTER_POINTS_SYMBOL = "layer-disaster-points-symbol"
    const val LAYER_ROUTE_SECONDARY_LINE = "layer-route-secondary-line"
    const val LAYER_ROUTE_PRIMARY_CASING = "layer-route-primary-casing"
    const val LAYER_ROUTE_PRIMARY_LINE = "layer-route-primary-line"
    const val LAYER_ACTIVE_FIRES_CIRCLE = "layer-active-fires-circle"
    const val LAYER_ACTIVE_FIRES_SYMBOL = "layer-active-fires-symbol"
    const val LAYER_COMMUNITY_HAZARDS_CIRCLE = "layer-community-hazards-circle"
    const val LAYER_COMMUNITY_HAZARDS_SYMBOL = "layer-community-hazards-symbol"

    // Dedicated Essential Facility Layers
    const val LAYER_HOSPITALS_CIRCLE = "layer-hospitals-circle"
    const val LAYER_HOSPITALS_SYMBOL = "layer-hospitals-symbol"
    const val LAYER_CLINICS_CIRCLE = "layer-clinics-circle"
    const val LAYER_CLINICS_SYMBOL = "layer-clinics-symbol"
    const val LAYER_POLICE_CIRCLE = "layer-police-circle"
    const val LAYER_POLICE_SYMBOL = "layer-police-symbol"
    const val LAYER_FUEL_CIRCLE = "layer-fuel-circle"
    const val LAYER_FUEL_SYMBOL = "layer-fuel-symbol"
    const val LAYER_FIRE_STATIONS_CIRCLE = "layer-fire-stations-circle"
    const val LAYER_FIRE_STATIONS_SYMBOL = "layer-fire-stations-symbol"
    const val LAYER_SAFE_HAVENS_CIRCLE = "layer-safe-havens-circle"
    const val LAYER_SAFE_HAVENS_SYMBOL = "layer-safe-havens-symbol"
    const val LAYER_WEATHER_POINTS_CIRCLE = "layer-weather-points-circle"
    const val LAYER_SELECTED_POINT_RING = "layer-selected-point-ring"
    const val LAYER_SELECTED_POINT_CIRCLE = "layer-selected-point-circle"

    // Terrain & Safety Intelligence Layers
    const val SOURCE_TERRAIN_SLOPE = "source-terrain-slope"
    const val LAYER_TERRAIN_SLOPE_LINE = "layer-terrain-slope-line"

    const val SOURCE_WATERWAYS = "source-waterways"
    const val LAYER_WATERWAYS_FILL = "layer-waterways-fill"
    const val LAYER_WATERWAYS_LINE = "layer-waterways-line"

    const val SOURCE_HISTORICAL_LANDSLIDES = "source-historical-landslides"
    const val LAYER_HISTORICAL_LANDSLIDES_CIRCLE = "layer-historical-landslides-circle"
    const val LAYER_HISTORICAL_LANDSLIDES_SYMBOL = "layer-historical-landslides-symbol"

    const val SOURCE_CELLULAR_RANGE = "source-cellular-range"
    const val LAYER_CELLULAR_RANGE_FILL = "layer-cellular-range-fill"
    const val LAYER_CELLULAR_RANGE_LINE = "layer-cellular-range-line"

    const val SOURCE_CELLULAR_SITES = "source-cellular-sites"
    const val LAYER_CELLULAR_SITES_CIRCLE = "layer-cellular-sites-circle"
    const val LAYER_CELLULAR_SITES_SYMBOL = "layer-cellular-sites-symbol"

    const val SOURCE_CELLULAR_COVERAGE = "source-cellular-coverage"
    const val LAYER_CELLULAR_COVERAGE_LINE = "layer-cellular-coverage-line"

    // Ordered Route Hazard Sequence (Animated Icons & Sequence Badges)
    const val SOURCE_ROUTE_HAZARD_SEQUENCE = "source-route-hazard-sequence"
    const val LAYER_ROUTE_HAZARD_SEQUENCE_SYMBOL = "layer-route-hazard-sequence-symbol"

    // Active Trek Layers
    // Verified Himalayan Trails
    const val SOURCE_VERIFIED_TRAILS = "source-verified-trails"
    const val LAYER_VERIFIED_TRAILS_LINE = "layer-verified-trails-line"

    // Proposed Off-Trail Connector (Dashed Amber)
    const val SOURCE_PROPOSED_CONNECTOR = "source-proposed-connector"
    const val LAYER_PROPOSED_CONNECTOR_LINE = "layer-proposed-connector-line"

    // User-Drawn Draft Route & Waypoint Pins
    const val SOURCE_DRAFT_ROUTE = "source-draft-route"
    const val LAYER_DRAFT_ROUTE_LINE = "layer-draft-route-line"
    const val SOURCE_DRAFT_WAYPOINTS = "source-draft-waypoints"
    const val LAYER_DRAFT_WAYPOINTS_CIRCLE = "layer-draft-waypoints-circle"

    const val SOURCE_TRAVELED_TRACK = "source-traveled-track"
    const val LAYER_TRAVELED_TRACK_LINE = "layer-traveled-track-line"
    const val SOURCE_TREK_CURRENT_POS = "source-trek-current-pos"
    const val LAYER_TREK_CURRENT_POS_ACCURACY = "layer-trek-current-pos-accuracy"
    const val LAYER_TREK_CURRENT_POS_CIRCLE = "layer-trek-current-pos-circle"

    private const val EMPTY_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"

    /**
     * Initializes all vector GeoJSON sources and style layers in exact bottom-to-top z-order.
     * Safe to call multiple times; existing sources and layers are preserved.
     */
    fun setupSourcesAndLayers(
        style: Style,
        context: android.content.Context? = null,
        isSatellite: Boolean = false
    ) {
        if (!style.isFullyLoaded) {
            Log.d(TAG, "Style not fully loaded yet; deferring source/layer creation.")
            return
        }

        // Detect new or reloaded Style instances and invalidate dirty cache to guarantee fresh re-population
        if (lastLoadedStyleRef != System.identityHashCode(style)) {
            Log.d(TAG, "New or reloaded Style detected (ref=${System.identityHashCode(style)}). Resetting dirty cache.")
            resetDirtyCache()
            lastLoadedStyleRef = System.identityHashCode(style)
        }

        try {
            // 0. Register custom vector glyph icons into Style if not already present
            MapIconFactory.registerMarkerImages(style, context)
            val iconStatus = MapIconFactory.verifyAllIconsRegistered(style)
            Log.d(TAG, "Registered MapLibre icons status: $iconStatus")

            // 0b. Uttarakhand State Boundary & Outside Mask (Base Overlays)
            // The outside mask occludes all base-map tiles and labels outside Uttarakhand.
            // The boundary line renders the state's authoritative perimeter.
            if (context != null) {
                if (style.getSource(SOURCE_UTTARAKHAND_MASK) == null) {
                    val maskJson = getOrLoadMaskGeoJson(context)
                    if (maskJson != null) {
                        style.addSource(GeoJsonSource(SOURCE_UTTARAKHAND_MASK, maskJson))
                    }
                }
                val existingMask = style.getLayer(LAYER_UTTARAKHAND_MASK_FILL) as? FillLayer
                if (existingMask == null && style.getSource(SOURCE_UTTARAKHAND_MASK) != null) {
                    val maskFill = FillLayer(LAYER_UTTARAKHAND_MASK_FILL, SOURCE_UTTARAKHAND_MASK).apply {
                        setProperties(
                            fillColor(if (isSatellite) Color.parseColor("#0B131E") else Color.parseColor("#EAE8E3")),
                            fillOpacity(if (isSatellite) 0.60f else 1.0f)
                        )
                    }
                    style.addLayer(maskFill)
                } else if (existingMask != null) {
                    existingMask.setProperties(
                        fillColor(if (isSatellite) Color.parseColor("#0B131E") else Color.parseColor("#EAE8E3")),
                        fillOpacity(if (isSatellite) 0.60f else 1.0f)
                    )
                }

                if (style.getSource(SOURCE_UTTARAKHAND_BOUNDARY) == null) {
                    val boundaryJson = getOrLoadBoundaryGeoJson(context)
                    if (boundaryJson != null) {
                        style.addSource(GeoJsonSource(SOURCE_UTTARAKHAND_BOUNDARY, boundaryJson))
                    }
                }
                if (style.getLayer(LAYER_UTTARAKHAND_BOUNDARY_LINE) == null && style.getSource(SOURCE_UTTARAKHAND_BOUNDARY) != null) {
                    val boundaryLine = LineLayer(LAYER_UTTARAKHAND_BOUNDARY_LINE, SOURCE_UTTARAKHAND_BOUNDARY).apply {
                        setProperties(
                            lineColor(Color.parseColor("#1E3A8A")), // Crisp dark navy border
                            lineWidth(2.5f),
                            lineOpacity(0.9f),
                            lineJoin(Property.LINE_JOIN_ROUND),
                            lineCap(Property.LINE_CAP_ROUND)
                        )
                    }
                    style.addLayer(boundaryLine)
                }
            }

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
                val initialForest = if (context != null) getOrLoadForestsBaselineGeoJson(context) ?: EMPTY_GEOJSON else (cachedForestsBaselineGeoJson ?: EMPTY_GEOJSON)
                style.addSource(GeoJsonSource(SOURCE_FOREST_POLYGONS, initialForest))
            }
            if (style.getLayer(LAYER_FOREST_FILL) == null) {
                val forestFill = FillLayer(LAYER_FOREST_FILL, SOURCE_FOREST_POLYGONS).apply {
                    setProperties(
                        fillColor(Color.rgb(46, 125, 50)), // Forest Green #2E7D32
                        fillOpacity(0.35f)
                    )
                }
                style.addLayer(forestFill)
            }
            if (style.getLayer(LAYER_FOREST_LINE) == null) {
                val forestLine = LineLayer(LAYER_FOREST_LINE, SOURCE_FOREST_POLYGONS).apply {
                    setProperties(
                        lineColor(Color.rgb(27, 94, 32)), // Dark Green outline #1B5E20
                        lineWidth(2.0f),
                        lineOpacity(0.85f),
                        lineJoin(Property.LINE_JOIN_ROUND)
                    )
                }
                style.addLayer(forestLine)
            }

            // 3. Disaster Alerts Polygons Source & Layers
            if (style.getSource(SOURCE_DISASTER_POLYGONS) == null) {
                val initialDisaster = if (context != null) getOrLoadHazardZonesBaselineGeoJson(context) ?: EMPTY_GEOJSON else (cachedHazardZonesBaselineGeoJson ?: EMPTY_GEOJSON)
                style.addSource(GeoJsonSource(SOURCE_DISASTER_POLYGONS, initialDisaster))
            }
            if (style.getLayer(LAYER_DISASTER_FILL) == null) {
                val disasterFill = FillLayer(LAYER_DISASTER_FILL, SOURCE_DISASTER_POLYGONS).apply {
                    setFilter(any(
                        eq(geometryType(), literal("Polygon")),
                        eq(geometryType(), literal("MultiPolygon")),
                        eq(get("geom_type"), literal("Polygon"))
                    ))
                    setProperties(
                        fillColor(
                            match(
                                get("hazard_category"),
                                literal("flash_flood"), color(Color.rgb(0, 151, 167)), // Flood Inundation Teal #0097A7
                                literal("flood"), color(Color.rgb(0, 151, 167)),
                                literal("landslide"), color(Color.rgb(216, 67, 21)), // Orange-Brown Landslide
                                color(Color.rgb(229, 57, 53)) // Emergency Red #E53935
                            )
                        ),
                        fillOpacity(0.38f)
                    )
                }
                style.addLayer(disasterFill)
            }
            if (style.getLayer(LAYER_DISASTER_LINE) == null) {
                val disasterLine = LineLayer(LAYER_DISASTER_LINE, SOURCE_DISASTER_POLYGONS).apply {
                    setFilter(any(
                        eq(geometryType(), literal("Polygon")),
                        eq(geometryType(), literal("MultiPolygon")),
                        eq(get("geom_type"), literal("Polygon"))
                    ))
                    setProperties(
                        lineColor(
                            match(
                                get("hazard_category"),
                                literal("flash_flood"), color(Color.rgb(0, 121, 107)), // Flood Inundation Dark Teal #00796B
                                literal("flood"), color(Color.rgb(0, 121, 107)),
                                literal("landslide"), color(Color.rgb(191, 54, 12)),
                                color(Color.rgb(211, 47, 47)) // #D32F2F
                            )
                        ),
                        lineWidth(2.8f),
                        lineOpacity(0.95f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(disasterLine)
            }
            if (style.getLayer(LAYER_DISASTER_POINTS_CIRCLE) == null) {
                val disasterPointsCircle = CircleLayer(LAYER_DISASTER_POINTS_CIRCLE, SOURCE_DISASTER_POLYGONS).apply {
                    setFilter(any(
                        eq(geometryType(), literal("Point")),
                        eq(get("geom_type"), literal("Point"))
                    ))
                    setProperties(
                        circleColor(Color.rgb(211, 47, 47)), // Emergency Red #D32F2F
                        circleRadius(10.0f),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(2.0f)
                    )
                }
                style.addLayer(disasterPointsCircle)
            }
            if (style.getLayer(LAYER_DISASTER_POINTS_SYMBOL) == null) {
                val disasterSymbol = SymbolLayer(LAYER_DISASTER_POINTS_SYMBOL, SOURCE_DISASTER_POLYGONS).apply {
                    setFilter(any(
                        eq(geometryType(), literal("Point")),
                        eq(get("geom_type"), literal("Point"))
                    ))
                    setProperties(
                        iconImage(MapIconFactory.ICON_DISASTER),
                        iconSize(0.85f),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(false),
                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                }
                style.addLayer(disasterSymbol)
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
            if (style.getLayer(LAYER_ROUTE_PRIMARY_CASING) == null) {
                val primCasing = LineLayer(LAYER_ROUTE_PRIMARY_CASING, SOURCE_ROUTE_PRIMARY).apply {
                    setProperties(
                        lineColor(Color.WHITE),
                        lineWidth(if (isSatellite) 8.5f else 0.0f),
                        lineOpacity(if (isSatellite) 0.90f else 0.0f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                if (style.getLayer(LAYER_ROUTE_PRIMARY_LINE) != null) {
                    style.addLayerBelow(primCasing, LAYER_ROUTE_PRIMARY_LINE)
                } else {
                    style.addLayer(primCasing)
                }
            } else {
                (style.getLayer(LAYER_ROUTE_PRIMARY_CASING) as? LineLayer)?.setProperties(
                    lineWidth(if (isSatellite) 8.5f else 0.0f),
                    lineOpacity(if (isSatellite) 0.90f else 0.0f)
                )
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

            // 4. Verified Himalayan Trails Layer (Baseline green dashed path)
            if (context != null && style.getSource(SOURCE_VERIFIED_TRAILS) == null) {
                val trailsJson = getOrLoadVerifiedTrailsGeoJson(context)
                if (trailsJson != null) {
                    style.addSource(GeoJsonSource(SOURCE_VERIFIED_TRAILS, trailsJson))
                }
            }
            if (style.getLayer(LAYER_VERIFIED_TRAILS_LINE) == null && style.getSource(SOURCE_VERIFIED_TRAILS) != null) {
                val trailLine = LineLayer(LAYER_VERIFIED_TRAILS_LINE, SOURCE_VERIFIED_TRAILS).apply {
                    setProperties(
                        lineColor(Color.parseColor("#10B981")),
                        lineWidth(2.5f),
                        lineOpacity(0.70f),
                        lineDasharray(arrayOf(2.0f, 2.0f)),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(trailLine)
            }

            // 4a-1. Proposed Connector Layer (Dashed Amber)
            if (style.getSource(SOURCE_PROPOSED_CONNECTOR) == null) {
                style.addSource(GeoJsonSource(SOURCE_PROPOSED_CONNECTOR, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_PROPOSED_CONNECTOR_LINE) == null) {
                val connLine = LineLayer(LAYER_PROPOSED_CONNECTOR_LINE, SOURCE_PROPOSED_CONNECTOR).apply {
                    setProperties(
                        lineColor(Color.parseColor("#F59E0B")),
                        lineWidth(3.5f),
                        lineOpacity(0.95f),
                        lineDasharray(arrayOf(2.5f, 2.5f)),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(connLine)
            }

            // 4a-2. User-Drawn Draft Route & Waypoints Layers
            if (style.getSource(SOURCE_DRAFT_ROUTE) == null) {
                style.addSource(GeoJsonSource(SOURCE_DRAFT_ROUTE, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_DRAFT_ROUTE_LINE) == null) {
                val draftLine = LineLayer(LAYER_DRAFT_ROUTE_LINE, SOURCE_DRAFT_ROUTE).apply {
                    setProperties(
                        lineColor(Color.parseColor("#8B5CF6")),
                        lineWidth(4.5f),
                        lineOpacity(0.90f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(draftLine)
            }
            if (style.getSource(SOURCE_DRAFT_WAYPOINTS) == null) {
                style.addSource(GeoJsonSource(SOURCE_DRAFT_WAYPOINTS, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_DRAFT_WAYPOINTS_CIRCLE) == null) {
                val draftCircle = CircleLayer(LAYER_DRAFT_WAYPOINTS_CIRCLE, SOURCE_DRAFT_WAYPOINTS).apply {
                    setProperties(
                        circleColor(Color.WHITE),
                        circleRadius(6.0f),
                        circleStrokeColor(Color.parseColor("#8B5CF6")),
                        circleStrokeWidth(2.5f)
                    )
                }
                style.addLayer(draftCircle)
            }

            // 4a-3. Traveled Track Line (Live GPS Recording)
            if (style.getSource(SOURCE_TRAVELED_TRACK) == null) {
                style.addSource(GeoJsonSource(SOURCE_TRAVELED_TRACK, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_TRAVELED_TRACK_LINE) == null) {
                val trackLine = LineLayer(LAYER_TRAVELED_TRACK_LINE, SOURCE_TRAVELED_TRACK).apply {
                    setProperties(
                        lineColor(Color.parseColor("#10B981")),
                        lineWidth(5.0f),
                        lineOpacity(0.95f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(trackLine)
            }

            // 4b. Terrain Slope Gradient Layer
            if (style.getSource(SOURCE_TERRAIN_SLOPE) == null) {
                style.addSource(GeoJsonSource(SOURCE_TERRAIN_SLOPE, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_TERRAIN_SLOPE_LINE) == null) {
                val slopeLine = LineLayer(LAYER_TERRAIN_SLOPE_LINE, SOURCE_TERRAIN_SLOPE).apply {
                    setProperties(
                        lineColor(
                            match(
                                get("slope_bracket"),
                                literal("Gentle (<15°)"), color(Color.rgb(76, 175, 80)),
                                literal("Moderate (15°-30°)"), color(Color.rgb(255, 179, 0)),
                                literal("Steep (30°-45°)"), color(Color.rgb(251, 140, 0)),
                                literal("Precipitous (>45°)"), color(Color.rgb(211, 47, 47)),
                                color(Color.rgb(120, 144, 156))
                            )
                        ),
                        lineWidth(5.0f),
                        lineOpacity(0.85f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(slopeLine)
            }

            // 4c. Authoritative Rivers, Lakes, Water Channels and Flood Plains
            if (style.getSource(SOURCE_WATERWAYS) == null) {
                val initialWater = if (context != null) getOrLoadWaterwaysBaselineGeoJson(context) ?: EMPTY_GEOJSON else (cachedWaterwaysBaselineGeoJson ?: EMPTY_GEOJSON)
                style.addSource(GeoJsonSource(SOURCE_WATERWAYS, initialWater))
            }
            if (style.getLayer(LAYER_WATERWAYS_FILL) == null) {
                val waterFill = FillLayer(LAYER_WATERWAYS_FILL, SOURCE_WATERWAYS).apply {
                    setFilter(any(
                        eq(geometryType(), literal("Polygon")),
                        eq(geometryType(), literal("MultiPolygon")),
                        eq(get("geom_type"), literal("Polygon"))
                    ))
                    setProperties(
                        fillColor(
                            match(
                                get("category"),
                                literal("flood_inundation_zone"), color(Color.argb(125, 0, 151, 167)), // Flood Inundation Plain #0097A7
                                literal("lake_reservoir"), color(Color.argb(150, 2, 136, 209)), // Lake / Reservoir Deep Blue #0288D1
                                color(Color.argb(135, 3, 169, 244)) // River Channel / Riverbed Light Cyan-Blue #03A9F4
                            )
                        ),
                        fillOpacity(0.65f)
                    )
                }
                style.addLayer(waterFill)
            }
            if (style.getLayer(LAYER_WATERWAYS_LINE) == null) {
                val waterLine = LineLayer(LAYER_WATERWAYS_LINE, SOURCE_WATERWAYS).apply {
                    setProperties(
                        lineColor(
                            match(
                                get("category"),
                                literal("flood_inundation_zone"), color(Color.rgb(0, 121, 107)), // Deep Teal outline #00796B
                                color(Color.rgb(1, 87, 155)) // Deep Water Blue centerline & boundary #01579B
                            )
                        ),
                        lineWidth(2.5f),
                        lineOpacity(0.85f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(waterLine)
            }

            // 4d. Historical Landslides (GSI Bhukosh / USDMA)
            if (style.getSource(SOURCE_HISTORICAL_LANDSLIDES) == null) {
                val initialLandslides = if (context != null) getOrLoadHistoricalLandslidesBaselineGeoJson(context) ?: EMPTY_GEOJSON else (cachedHistoricalLandslidesBaselineGeoJson ?: EMPTY_GEOJSON)
                style.addSource(GeoJsonSource(SOURCE_HISTORICAL_LANDSLIDES, initialLandslides))
            }
            if (style.getLayer(LAYER_HISTORICAL_LANDSLIDES_CIRCLE) == null) {
                val lsCircle = CircleLayer(LAYER_HISTORICAL_LANDSLIDES_CIRCLE, SOURCE_HISTORICAL_LANDSLIDES).apply {
                    setProperties(
                        circleColor(Color.rgb(141, 110, 99)), // Earth Brown #8D6E63
                        circleRadius(8.0f),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(1.8f)
                    )
                }
                style.addLayer(lsCircle)
            }
            if (style.getLayer(LAYER_HISTORICAL_LANDSLIDES_SYMBOL) == null) {
                val lsSymbol = SymbolLayer(LAYER_HISTORICAL_LANDSLIDES_SYMBOL, SOURCE_HISTORICAL_LANDSLIDES).apply {
                    setProperties(
                        iconImage(MapIconFactory.ICON_ANIM_LANDSLIDE),
                        iconSize(0.70f),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(false),
                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                }
                style.addLayer(lsSymbol)
            }

            // 4e. Cellular Range Halos, Coverage Line & Mast Sites
            if (style.getSource(SOURCE_CELLULAR_RANGE) == null) {
                style.addSource(GeoJsonSource(SOURCE_CELLULAR_RANGE, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_CELLULAR_RANGE_FILL) == null) {
                val rangeFill = FillLayer(LAYER_CELLULAR_RANGE_FILL, SOURCE_CELLULAR_RANGE).apply {
                    setProperties(
                        fillColor(Color.rgb(124, 77, 255)), // Indigo #7C4DFF
                        fillOpacity(0.18f)
                    )
                }
                style.addLayer(rangeFill)
            }
            if (style.getLayer(LAYER_CELLULAR_RANGE_LINE) == null) {
                val rangeLine = LineLayer(LAYER_CELLULAR_RANGE_LINE, SOURCE_CELLULAR_RANGE).apply {
                    setProperties(
                        lineColor(Color.rgb(101, 31, 255)), // Deep Indigo #651FFF
                        lineWidth(1.8f),
                        lineOpacity(0.6f),
                        lineDasharray(arrayOf(3f, 2f))
                    )
                }
                style.addLayer(rangeLine)
            }

            if (style.getSource(SOURCE_CELLULAR_COVERAGE) == null) {
                style.addSource(GeoJsonSource(SOURCE_CELLULAR_COVERAGE, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_CELLULAR_COVERAGE_LINE) == null) {
                val covLine = LineLayer(LAYER_CELLULAR_COVERAGE_LINE, SOURCE_CELLULAR_COVERAGE).apply {
                    setProperties(
                        lineColor(
                            match(
                                get("status"),
                                literal("PROBABLE_COVERAGE"), color(Color.rgb(0, 137, 123)),
                                color(Color.rgb(158, 158, 158))
                            )
                        ),
                        lineWidth(3.5f),
                        lineOpacity(0.75f),
                        lineJoin(Property.LINE_JOIN_ROUND),
                        lineCap(Property.LINE_CAP_ROUND)
                    )
                }
                style.addLayer(covLine)
            }

            if (style.getSource(SOURCE_CELLULAR_SITES) == null) {
                style.addSource(GeoJsonSource(SOURCE_CELLULAR_SITES, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_CELLULAR_SITES_CIRCLE) == null) {
                val cellCircle = CircleLayer(LAYER_CELLULAR_SITES_CIRCLE, SOURCE_CELLULAR_SITES).apply {
                    setProperties(
                        circleColor(Color.rgb(101, 31, 255)), // Indigo #651FFF
                        circleRadius(10f),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(2f)
                    )
                }
                style.addLayer(cellCircle)
            }
            if (style.getLayer(LAYER_CELLULAR_SITES_SYMBOL) == null) {
                val cellSymbol = SymbolLayer(LAYER_CELLULAR_SITES_SYMBOL, SOURCE_CELLULAR_SITES).apply {
                    setProperties(
                        iconImage(MapIconFactory.ICON_CELL_TOWER),
                        iconSize(0.65f),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true)
                    )
                }
                style.addLayer(cellSymbol)
            }

            // 5. Point Hazard & Infrastructure Circle & Symbol Layers
            if (style.getSource(SOURCE_SAFE_HAVENS) == null) {
                style.addSource(GeoJsonSource(SOURCE_SAFE_HAVENS, EMPTY_GEOJSON))
            }

            // Dedicated Circle & Symbol Layers for Hospitals
            if (style.getLayer(LAYER_HOSPITALS_CIRCLE) == null) {
                val hospCircle = CircleLayer(LAYER_HOSPITALS_CIRCLE, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 5.5f
                    setFilter(any(
                        eq(get("category"), literal("hospital")),
                        eq(get("category"), literal("HOSPITAL")),
                        eq(get("category"), literal("medical")),
                        eq(get("category"), literal("MEDICAL")),
                        eq(get("facility_category"), literal("hospital")),
                        eq(get("category_enum"), literal("HOSPITAL"))
                    ))
                    setProperties(
                        circleColor(Color.rgb(229, 57, 53)), // #E53935 Medical Red
                        circleRadius(interpolate(linear(), zoom(), stop(6.0f, 3.5f), stop(8.0f, 5.0f), stop(10.0f, 7.5f), stop(13.0f, 10.5f), stop(16.0f, 13.5f))),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(1.8f)
                    )
                }
                style.addLayer(hospCircle)
            }
            if (style.getLayer(LAYER_HOSPITALS_SYMBOL) == null) {
                val hospSymbol = SymbolLayer(LAYER_HOSPITALS_SYMBOL, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 6.0f
                    setFilter(any(
                        eq(get("category"), literal("hospital")),
                        eq(get("category"), literal("HOSPITAL")),
                        eq(get("category"), literal("medical")),
                        eq(get("category"), literal("MEDICAL")),
                        eq(get("facility_category"), literal("hospital")),
                        eq(get("category_enum"), literal("HOSPITAL"))
                    ))
                    setProperties(
                        iconImage(MapIconFactory.ICON_HOSPITAL),
                        iconSize(interpolate(linear(), zoom(), stop(6.0f, 0.45f), stop(8.0f, 0.60f), stop(10.0f, 0.80f), stop(13.0f, 0.95f), stop(16.0f, 1.15f))),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                }
                style.addLayer(hospSymbol)
            }

            // Dedicated Circle & Symbol Layers for Clinics
            if (style.getLayer(LAYER_CLINICS_CIRCLE) == null) {
                val clinicCircle = CircleLayer(LAYER_CLINICS_CIRCLE, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 5.5f
                    setFilter(any(
                        eq(get("category"), literal("clinic")),
                        eq(get("category"), literal("CLINIC")),
                        eq(get("facility_category"), literal("clinic")),
                        eq(get("category_enum"), literal("CLINIC"))
                    ))
                    setProperties(
                        circleColor(Color.rgb(0, 137, 123)), // #00897B Clinic Teal
                        circleRadius(interpolate(linear(), zoom(), stop(6.0f, 3.5f), stop(8.0f, 5.0f), stop(10.0f, 7.5f), stop(13.0f, 10.5f), stop(16.0f, 13.5f))),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(1.8f)
                    )
                }
                style.addLayer(clinicCircle)
            }
            if (style.getLayer(LAYER_CLINICS_SYMBOL) == null) {
                val clinicSymbol = SymbolLayer(LAYER_CLINICS_SYMBOL, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 6.0f
                    setFilter(any(
                        eq(get("category"), literal("clinic")),
                        eq(get("category"), literal("CLINIC")),
                        eq(get("facility_category"), literal("clinic")),
                        eq(get("category_enum"), literal("CLINIC"))
                    ))
                    setProperties(
                        iconImage(MapIconFactory.ICON_CLINIC),
                        iconSize(interpolate(linear(), zoom(), stop(6.0f, 0.45f), stop(8.0f, 0.60f), stop(10.0f, 0.80f), stop(13.0f, 0.95f), stop(16.0f, 1.15f))),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                }
                style.addLayer(clinicSymbol)
            }

            // Dedicated Circle & Symbol Layers for Police
            if (style.getLayer(LAYER_POLICE_CIRCLE) == null) {
                val policeCircle = CircleLayer(LAYER_POLICE_CIRCLE, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 5.5f
                    setFilter(any(
                        eq(get("category"), literal("police")),
                        eq(get("category"), literal("POLICE")),
                        eq(get("category"), literal("POLICE_STATION")),
                        eq(get("facility_category"), literal("police")),
                        eq(get("category_enum"), literal("POLICE_STATION"))
                    ))
                    setProperties(
                        circleColor(Color.rgb(30, 136, 229)), // #1E88E5 Police Blue
                        circleRadius(interpolate(linear(), zoom(), stop(6.0f, 3.5f), stop(8.0f, 5.0f), stop(10.0f, 7.5f), stop(13.0f, 10.5f), stop(16.0f, 13.5f))),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(2.0f)
                    )
                }
                style.addLayer(policeCircle)
            }
            if (style.getLayer(LAYER_POLICE_SYMBOL) == null) {
                val policeSymbol = SymbolLayer(LAYER_POLICE_SYMBOL, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 6.0f
                    setFilter(any(
                        eq(get("category"), literal("police")),
                        eq(get("category"), literal("POLICE")),
                        eq(get("category"), literal("POLICE_STATION")),
                        eq(get("facility_category"), literal("police")),
                        eq(get("category_enum"), literal("POLICE_STATION"))
                    ))
                    setProperties(
                        iconImage(MapIconFactory.ICON_POLICE),
                        iconSize(interpolate(linear(), zoom(), stop(6.0f, 0.45f), stop(8.0f, 0.60f), stop(10.0f, 0.80f), stop(13.0f, 0.95f), stop(16.0f, 1.15f))),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                }
                style.addLayer(policeSymbol)
            }

            // Dedicated Circle & Symbol Layers for Petrol Pumps
            if (style.getLayer(LAYER_FUEL_CIRCLE) == null) {
                val fuelCircle = CircleLayer(LAYER_FUEL_CIRCLE, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 5.5f
                    setFilter(any(
                        eq(get("category"), literal("fuel")),
                        eq(get("category"), literal("fuel_station")),
                        eq(get("category"), literal("petrol_pump")),
                        eq(get("category"), literal("PETROL_PUMP")),
                        eq(get("category"), literal("FUEL_STATION")),
                        eq(get("facility_category"), literal("fuel")),
                        eq(get("category_enum"), literal("PETROL_PUMP"))
                    ))
                    setProperties(
                        circleColor(Color.rgb(245, 124, 0)), // #F57C00 Fuel Amber
                        circleRadius(interpolate(linear(), zoom(), stop(6.0f, 3.5f), stop(8.0f, 5.0f), stop(10.0f, 7.5f), stop(13.0f, 10.5f), stop(16.0f, 13.5f))),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(1.8f)
                    )
                }
                style.addLayer(fuelCircle)
            }
            if (style.getLayer(LAYER_FUEL_SYMBOL) == null) {
                val fuelSymbol = SymbolLayer(LAYER_FUEL_SYMBOL, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 6.0f
                    setFilter(any(
                        eq(get("category"), literal("fuel")),
                        eq(get("category"), literal("fuel_station")),
                        eq(get("category"), literal("petrol_pump")),
                        eq(get("category"), literal("PETROL_PUMP")),
                        eq(get("category"), literal("FUEL_STATION")),
                        eq(get("facility_category"), literal("fuel")),
                        eq(get("category_enum"), literal("PETROL_PUMP"))
                    ))
                    setProperties(
                        iconImage(MapIconFactory.ICON_FUEL),
                        iconSize(interpolate(linear(), zoom(), stop(6.0f, 0.45f), stop(8.0f, 0.60f), stop(10.0f, 0.80f), stop(13.0f, 0.95f), stop(16.0f, 1.15f))),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                }
                style.addLayer(fuelSymbol)
            }

            // Dedicated Circle & Symbol Layers for Fire Stations
            if (style.getLayer(LAYER_FIRE_STATIONS_CIRCLE) == null) {
                val fireStCircle = CircleLayer(LAYER_FIRE_STATIONS_CIRCLE, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 5.5f
                    setFilter(any(
                        eq(get("category"), literal("fire_station")),
                        eq(get("category"), literal("FIRE_STATION")),
                        eq(get("category"), literal("fire")),
                        eq(get("facility_category"), literal("fire_station")),
                        eq(get("category_enum"), literal("FIRE_STATION"))
                    ))
                    setProperties(
                        circleColor(Color.rgb(230, 81, 0)), // #E65100 Fire Orange-Red
                        circleRadius(interpolate(linear(), zoom(), stop(6.0f, 3.5f), stop(8.0f, 5.0f), stop(10.0f, 7.5f), stop(13.0f, 10.5f), stop(16.0f, 13.5f))),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(2.0f)
                    )
                }
                style.addLayer(fireStCircle)
            }
            if (style.getLayer(LAYER_FIRE_STATIONS_SYMBOL) == null) {
                val fireStSymbol = SymbolLayer(LAYER_FIRE_STATIONS_SYMBOL, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 6.0f
                    setFilter(any(
                        eq(get("category"), literal("fire_station")),
                        eq(get("category"), literal("FIRE_STATION")),
                        eq(get("category"), literal("fire")),
                        eq(get("facility_category"), literal("fire_station")),
                        eq(get("category_enum"), literal("FIRE_STATION"))
                    ))
                    setProperties(
                        iconImage(MapIconFactory.ICON_FIRE_STATION),
                        iconSize(interpolate(linear(), zoom(), stop(6.0f, 0.45f), stop(8.0f, 0.60f), stop(10.0f, 0.80f), stop(13.0f, 0.95f), stop(16.0f, 1.15f))),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                }
                style.addLayer(fireStSymbol)
            }

            // Fallback Circle & Symbol Layer for Shelters & General Safe Havens
            if (style.getLayer(LAYER_SAFE_HAVENS_CIRCLE) == null) {
                val havenCircle = CircleLayer(LAYER_SAFE_HAVENS_CIRCLE, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 5.5f
                    setFilter(any(
                        eq(get("category"), literal("shelter")),
                        eq(get("category"), literal("SHELTER")),
                        eq(get("facility_category"), literal("shelter")),
                        eq(get("category"), literal("tourist_assistance")),
                        eq(get("category_enum"), literal("SHELTER")),
                        eq(get("category_enum"), literal("TOURIST_ASSISTANCE"))
                    ))
                    setProperties(
                        circleColor(Color.rgb(46, 125, 50)), // #2E7D32 Green
                        circleRadius(interpolate(linear(), zoom(), stop(6.0f, 3.5f), stop(8.0f, 5.0f), stop(10.0f, 7.5f), stop(13.0f, 10.5f), stop(16.0f, 13.5f))),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(1.8f)
                    )
                }
                style.addLayer(havenCircle)
            }
            if (style.getLayer(LAYER_SAFE_HAVENS_SYMBOL) == null) {
                val havenSymbol = SymbolLayer(LAYER_SAFE_HAVENS_SYMBOL, SOURCE_SAFE_HAVENS).apply {
                    minZoom = 6.0f
                    setFilter(any(
                        eq(get("category"), literal("shelter")),
                        eq(get("category"), literal("SHELTER")),
                        eq(get("facility_category"), literal("shelter")),
                        eq(get("category"), literal("tourist_assistance")),
                        eq(get("category_enum"), literal("SHELTER")),
                        eq(get("category_enum"), literal("TOURIST_ASSISTANCE"))
                    ))
                    setProperties(
                        iconImage(MapIconFactory.ICON_SHELTER),
                        iconSize(interpolate(linear(), zoom(), stop(6.0f, 0.45f), stop(8.0f, 0.60f), stop(10.0f, 0.80f), stop(13.0f, 0.95f), stop(16.0f, 1.15f))),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                }
                style.addLayer(havenSymbol)
            }

            if (style.getSource(SOURCE_WEATHER_POINTS) == null) {
                style.addSource(GeoJsonSource(SOURCE_WEATHER_POINTS, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_WEATHER_POINTS_CIRCLE) == null) {
                val weatherCircle = CircleLayer(LAYER_WEATHER_POINTS_CIRCLE, SOURCE_WEATHER_POINTS).apply {
                    setProperties(
                        circleColor(Color.rgb(2, 136, 209)), // Cyan Blue #0288D1
                        circleRadius(6.0f),
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
                        circleRadius(9.5f),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(2.0f)
                    )
                }
                style.addLayer(fireCircle)
            }
            if (style.getLayer(LAYER_ACTIVE_FIRES_SYMBOL) == null) {
                val fireSymbol = SymbolLayer(LAYER_ACTIVE_FIRES_SYMBOL, SOURCE_ACTIVE_FIRES).apply {
                    setProperties(
                        iconImage(MapIconFactory.ICON_WILDFIRE),
                        iconSize(0.85f),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(false),
                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                }
                style.addLayer(fireSymbol)
            }

            // 7g-1. Community Hazard Reports Layer
            if (style.getSource(SOURCE_COMMUNITY_HAZARDS) == null) {
                style.addSource(GeoJsonSource(SOURCE_COMMUNITY_HAZARDS, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_COMMUNITY_HAZARDS_CIRCLE) == null) {
                val commCircle = CircleLayer(LAYER_COMMUNITY_HAZARDS_CIRCLE, SOURCE_COMMUNITY_HAZARDS).apply {
                    setProperties(
                        circleColor(Color.rgb(245, 124, 0)), // Hazard Amber #F57C00
                        circleRadius(10.0f),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(2.0f)
                    )
                }
                style.addLayer(commCircle)
            }
            if (style.getLayer(LAYER_COMMUNITY_HAZARDS_SYMBOL) == null) {
                val commSymbol = SymbolLayer(LAYER_COMMUNITY_HAZARDS_SYMBOL, SOURCE_COMMUNITY_HAZARDS).apply {
                    setProperties(
                        iconImage(MapIconFactory.ICON_COMMUNITY_HAZARD),
                        iconSize(0.85f),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(false),
                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                    )
                }
                style.addLayer(commSymbol)
            }

            // 8. Ordered Route Hazard Sequence Layer (Animated Category Glyphs + Numbered Badges)
            if (style.getSource(SOURCE_ROUTE_HAZARD_SEQUENCE) == null) {
                style.addSource(GeoJsonSource(SOURCE_ROUTE_HAZARD_SEQUENCE, EMPTY_GEOJSON))
            }
            if (style.getLayer(LAYER_ROUTE_HAZARD_SEQUENCE_SYMBOL) == null) {
                val seqSymbol = SymbolLayer(LAYER_ROUTE_HAZARD_SEQUENCE_SYMBOL, SOURCE_ROUTE_HAZARD_SEQUENCE).apply {
                    setProperties(
                        iconImage(get("icon_id")),
                        iconSize(interpolate(linear(), zoom(), stop(8.0f, 0.75f), stop(11.0f, 0.95f), stop(14.0f, 1.20f))),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(false),
                        iconAnchor(Property.ICON_ANCHOR_CENTER),
                        textField(get("sequence_label")),
                        textSize(11.5f),
                        textColor(Color.WHITE),
                        textHaloColor(Color.BLACK),
                        textHaloWidth(1.8f),
                        textOffset(arrayOf(0f, 1.45f)),
                        textAnchor(Property.TEXT_ANCHOR_TOP),
                        textAllowOverlap(true)
                    )
                }
                style.addLayer(seqSymbol)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MapLibre sources and layers: ${e.message}", e)
        }
    }

    /**
     * Updates all GeoJSON sources with the latest state data without destroying layers.
     */
    fun updateData(
        style: Style,
        state: MapUiState,
        context: android.content.Context? = null,
        isSatellite: Boolean = state.basemapMode.isSatellite
    ) {
        if (!style.isFullyLoaded) return
        setupSourcesAndLayers(style, context, isSatellite)

        Log.d(TAG, "MapLibre updateData: allFeatures=${state.allFeatures.size}, " +
                "hazardsGeoJson=${!state.hazardsGeoJson.isNullOrBlank()}, " +
                "forestsGeoJson=${!state.forestsGeoJson.isNullOrBlank()}, " +
                "facilitiesGeoJson=${!state.facilitiesGeoJson.isNullOrBlank()}, " +
                "firesGeoJson=${!state.firesGeoJson.isNullOrBlank()}, " +
                "weatherGeoJson=${!state.weatherGeoJson.isNullOrBlank()}")

        try {
            // 1. Update Corridor GeoJSON
            try {
                val corridorSource = style.getSourceAs<GeoJsonSource>(SOURCE_CORRIDOR)
                val corrJson = if (!state.corridorGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.corridorGeoJson)
                } else {
                    buildCorridorGeoJson(state.corridorPolygonCoordinates)
                }
                if (corrJson != lastCorridorGeoJson) {
                    corridorSource?.setGeoJson(corrJson)
                    lastCorridorGeoJson = corrJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating corridor GeoJSON: ${t.message}")
            }

            // 1b. Update Proposed Connector GeoJSON
            try {
                if (state.proposedConnectorCoordinates.size >= 2) {
                    updateProposedConnector(style, state.proposedConnectorCoordinates)
                } else {
                    style.getSourceAs<GeoJsonSource>(SOURCE_PROPOSED_CONNECTOR)?.setGeoJson(EMPTY_GEOJSON)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating proposed connector: ${t.message}")
            }

            // 1c. Update User-Drawn Draft Route GeoJSON
            try {
                if (state.isDrawingMode) {
                    updateDraftRoute(style, state.draftWaypoints, state.draftPolyline)
                } else {
                    clearDraftRoute(style)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating draft route: ${t.message}")
            }

            // 2. Update Primary & Secondary Route GeoJSON
            try {
                val primSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_PRIMARY)
                val primJson = if (!state.routePrimaryGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.routePrimaryGeoJson)
                } else {
                    buildLineStringGeoJson(state.routeCoordinates)
                }
                if (primJson != lastRoutePrimaryGeoJson) {
                    primSource?.setGeoJson(primJson)
                    lastRoutePrimaryGeoJson = primJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating primary route GeoJSON: ${t.message}")
            }

            try {
                val secSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_SECONDARY)
                val secJson = if (!state.routeSecondaryGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.routeSecondaryGeoJson)
                } else if (state.selectedAlternativeId != null && state.primaryRouteCoordinates.size >= 2) {
                    buildLineStringGeoJson(state.primaryRouteCoordinates)
                } else {
                    EMPTY_GEOJSON
                }
                if (secJson != lastRouteSecondaryGeoJson) {
                    secSource?.setGeoJson(secJson)
                    lastRouteSecondaryGeoJson = secJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating secondary route GeoJSON: ${t.message}")
            }

            // 3. Update Disaster Features (Polygons & Point Advisories)
            try {
                val disasterSource = style.getSourceAs<GeoJsonSource>(SOURCE_DISASTER_POLYGONS)
                val routeHazardsHaveFeatures = hasFeatures(state.hazardsGeoJson)
                val disasterFeatures = state.allFeatures.filter { it.category == MapFeatureCategory.HAZARD_ZONE }
                val baselineDisasterJson = if (context != null) getOrLoadHazardZonesBaselineGeoJson(context) else cachedHazardZonesBaselineGeoJson

                val disasterJson = if (routeHazardsHaveFeatures) {
                    ensureFeatureCollection(state.hazardsGeoJson)
                } else if (disasterFeatures.isNotEmpty()) {
                    buildDisasterFeatureCollection(disasterFeatures)
                } else if (!baselineDisasterJson.isNullOrBlank()) {
                    baselineDisasterJson
                } else if (!state.hazardsGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.hazardsGeoJson)
                } else {
                    EMPTY_GEOJSON
                }
                if (disasterJson != lastDisasterGeoJson) {
                    disasterSource?.setGeoJson(disasterJson)
                    lastDisasterGeoJson = disasterJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating disaster GeoJSON: ${t.message}")
            }

            // 4. Update Forest Polygons
            try {
                val forestSource = style.getSourceAs<GeoJsonSource>(SOURCE_FOREST_POLYGONS)
                val forestFeatures = state.allFeatures.filter { it.category == MapFeatureCategory.FOREST_AREA && it.geometryPolygon != null }
                val routeForestHasFeatures = !state.forestsGeoJson.isNullOrBlank() &&
                        state.forestsGeoJson.contains("\"coordinates\"") &&
                        !state.forestsGeoJson.contains("\"features\":[]")
                val baselineJson = if (context != null) getOrLoadForestsBaselineGeoJson(context) else cachedForestsBaselineGeoJson
                val forestJson = if (routeForestHasFeatures) {
                    ensureFeatureCollection(state.forestsGeoJson)
                } else if (forestFeatures.isNotEmpty()) {
                    buildPolygonFeatureCollection(forestFeatures)
                } else if (!baselineJson.isNullOrBlank()) {
                    baselineJson
                } else if (!state.forestsGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.forestsGeoJson)
                } else {
                    EMPTY_GEOJSON
                }
                if (forestJson != lastForestGeoJson) {
                    forestSource?.setGeoJson(forestJson)
                    lastForestGeoJson = forestJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating forest GeoJSON: ${t.message}")
            }

            // 5. Update Active Fires
            try {
                val fireSource = style.getSourceAs<GeoJsonSource>(SOURCE_ACTIVE_FIRES)
                val fireJson = if (!state.firesGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.firesGeoJson)
                } else {
                    val fireFeatures = state.allFeatures.filter { it.category == MapFeatureCategory.ACTIVE_FIRE_DETECTION }
                    buildPointFeatureCollection(fireFeatures)
                }
                if (fireJson != lastFireGeoJson) {
                    fireSource?.setGeoJson(fireJson)
                    lastFireGeoJson = fireJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating fire GeoJSON: ${t.message}")
            }

            // 6. Update Essential Facilities & Safe Havens
            try {
                val havenSource = style.getSourceAs<GeoJsonSource>(SOURCE_SAFE_HAVENS)
                val havenJson = if (!state.facilitiesGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.facilitiesGeoJson)
                } else {
                    val havenFeatures = state.allFeatures.filter {
                        it.category == MapFeatureCategory.SHELTER ||
                        it.category == MapFeatureCategory.HOSPITAL ||
                        it.category == MapFeatureCategory.CLINIC ||
                        it.category == MapFeatureCategory.POLICE_STATION ||
                        it.category == MapFeatureCategory.PETROL_PUMP ||
                        it.category == MapFeatureCategory.FIRE_STATION ||
                        it.category == MapFeatureCategory.TOURIST_ASSISTANCE
                    }
                    buildPointFeatureCollection(havenFeatures)
                }
                if (havenJson != lastHavenGeoJson) {
                    havenSource?.setGeoJson(havenJson)
                    lastHavenGeoJson = havenJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating facilities GeoJSON: ${t.message}")
            }

            // 7. Update Weather Points
            try {
                val weatherSource = style.getSourceAs<GeoJsonSource>(SOURCE_WEATHER_POINTS)
                val weatherJson = if (!state.weatherGeoJson.isNullOrBlank()) {
                    state.weatherGeoJson
                } else {
                    val weatherFeatures = state.allFeatures.filter { it.category == MapFeatureCategory.WEATHER_POINT }
                    buildPointFeatureCollection(weatherFeatures)
                }
                if (weatherJson != lastWeatherGeoJson) {
                    weatherSource?.setGeoJson(weatherJson)
                    lastWeatherGeoJson = weatherJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating weather GeoJSON: ${t.message}")
            }

            // 7b. Update Terrain Slope GeoJSON
            try {
                val slopeSource = style.getSourceAs<GeoJsonSource>(SOURCE_TERRAIN_SLOPE)
                val slopeJson = if (!state.terrainSlopeGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.terrainSlopeGeoJson)
                } else EMPTY_GEOJSON
                if (slopeJson != lastSlopeGeoJson) {
                    slopeSource?.setGeoJson(slopeJson)
                    lastSlopeGeoJson = slopeJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating terrain slope GeoJSON: ${t.message}")
            }

            // 7c. Update Waterways GeoJSON
            try {
                val waterSource = style.getSourceAs<GeoJsonSource>(SOURCE_WATERWAYS)
                val routeWaterHasFeatures = !state.waterwaysGeoJson.isNullOrBlank() &&
                    !state.waterwaysGeoJson.trim().startsWith("{\"type\":\"FeatureCollection\",\"features\":[]}") &&
                    !state.waterwaysGeoJson.trim().startsWith("{\"type\": \"FeatureCollection\", \"features\": []}")
                val baselineJson = if (context != null) getOrLoadWaterwaysBaselineGeoJson(context) else cachedWaterwaysBaselineGeoJson
                val waterJson = if (routeWaterHasFeatures) {
                    ensureFeatureCollection(state.waterwaysGeoJson)
                } else if (!baselineJson.isNullOrBlank()) {
                    baselineJson
                } else EMPTY_GEOJSON
                if (waterJson != lastWaterwayGeoJson) {
                    waterSource?.setGeoJson(waterJson)
                    lastWaterwayGeoJson = waterJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating waterways GeoJSON: ${t.message}")
            }

            // 7d. Update Historical Landslides GeoJSON
            try {
                val lsSource = style.getSourceAs<GeoJsonSource>(SOURCE_HISTORICAL_LANDSLIDES)
                val routeLsHasFeatures = hasFeatures(state.historicalLandslidesGeoJson)
                val baselineLsJson = if (context != null) getOrLoadHistoricalLandslidesBaselineGeoJson(context) else cachedHistoricalLandslidesBaselineGeoJson

                val lsJson = if (routeLsHasFeatures) {
                    ensureFeatureCollection(state.historicalLandslidesGeoJson)
                } else if (!baselineLsJson.isNullOrBlank()) {
                    baselineLsJson
                } else if (!state.historicalLandslidesGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.historicalLandslidesGeoJson)
                } else {
                    EMPTY_GEOJSON
                }
                if (lsJson != lastHistoricalLandslidesGeoJson) {
                    lsSource?.setGeoJson(lsJson)
                    lastHistoricalLandslidesGeoJson = lsJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating historical landslides GeoJSON: ${t.message}")
            }

            // 7d-2. Update Cellular Range Halos GeoJSON
            try {
                val rangeSource = style.getSourceAs<GeoJsonSource>(SOURCE_CELLULAR_RANGE)
                var rangeJson = state.cellularRangeGeoJson
                if (rangeJson.isNullOrBlank()) {
                    val sitesJson = if (!state.cellularSitesGeoJson.isNullOrBlank()) {
                        state.cellularSitesGeoJson
                    } else if (context != null) {
                        getOrLoadCellTowersBaselineGeoJson(context)
                    } else cachedCellTowersBaselineGeoJson
                    if (!sitesJson.isNullOrBlank()) {
                        rangeJson = synthesizeCellularRangeGeoJson(sitesJson)
                    }
                }
                val finalRangeJson = if (!rangeJson.isNullOrBlank()) {
                    ensureFeatureCollection(rangeJson)
                } else EMPTY_GEOJSON
                rangeSource?.setGeoJson(finalRangeJson)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating cellular range GeoJSON: ${t.message}")
            }

            // 7e. Update Cellular Sites GeoJSON
            try {
                val cellSiteSource = style.getSourceAs<GeoJsonSource>(SOURCE_CELLULAR_SITES)
                val baselineCellSites = if (context != null) getOrLoadCellTowersBaselineGeoJson(context) else cachedCellTowersBaselineGeoJson
                val cellSiteJson = if (!state.cellularSitesGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.cellularSitesGeoJson)
                } else if (!baselineCellSites.isNullOrBlank()) {
                    baselineCellSites
                } else EMPTY_GEOJSON
                cellSiteSource?.setGeoJson(cellSiteJson)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating cellular sites GeoJSON: ${t.message}")
            }

            // 7f. Update Cellular Coverage GeoJSON
            try {
                val cellCovSource = style.getSourceAs<GeoJsonSource>(SOURCE_CELLULAR_COVERAGE)
                val cellCovJson = if (!state.cellularCoverageGeoJson.isNullOrBlank()) {
                    ensureFeatureCollection(state.cellularCoverageGeoJson)
                } else EMPTY_GEOJSON
                cellCovSource?.setGeoJson(cellCovJson)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating cellular coverage GeoJSON: ${t.message}")
            }

            // 7g. Update Route Hazard Sequence GeoJSON
            try {
                val seqSource = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_HAZARD_SEQUENCE)
                val seqJson = if (state.routeHazardSequence.isNotEmpty()) {
                    buildHazardSequenceGeoJson(state.routeHazardSequence, state.selectedHazardSequenceItem?.feature?.id)
                } else EMPTY_GEOJSON
                if (seqJson != lastHazardSequenceGeoJson) {
                    seqSource?.setGeoJson(seqJson)
                    lastHazardSequenceGeoJson = seqJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating route hazard sequence GeoJSON: ${t.message}")
            }

            // 7g-2. Update Community Hazards GeoJSON
            try {
                val commSource = style.getSourceAs<GeoJsonSource>(SOURCE_COMMUNITY_HAZARDS)
                val commFeatures = state.allFeatures.filter { it.category == MapFeatureCategory.COMMUNITY_HAZARD }
                val commJson = buildPointFeatureCollection(commFeatures)
                if (commJson != lastCommunityGeoJson) {
                    commSource?.setGeoJson(commJson)
                    lastCommunityGeoJson = commJson
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating community hazards GeoJSON: ${t.message}")
            }

            // 8. Update Layer Visibilities according to state.layerVisibilities & selectedFilter chip
            try {
                updateLayerVisibilities(style, state)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed updating layer visibilities: ${t.message}")
            }

        } catch (e: Throwable) {
            Log.e(TAG, "Error updating GeoJSON sources in style: ${e.message}", e)
        }
    }

    /**
     * Safely clears route and corridor geometries when changing routes.
     */
    fun clearRouteSpecificLayers(style: Style) {
        if (!style.isFullyLoaded) return
        try {
            style.getSourceAs<GeoJsonSource>(SOURCE_CORRIDOR)?.setGeoJson(EMPTY_GEOJSON)
            style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_PRIMARY)?.setGeoJson(EMPTY_GEOJSON)
            style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_SECONDARY)?.setGeoJson(EMPTY_GEOJSON)
            style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_HAZARD_SEQUENCE)?.setGeoJson(EMPTY_GEOJSON)
            Log.d(TAG, "Cleared route and corridor layers from MapLibre style.")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed clearing route layers: ${e.message}")
        }
    }

    private fun updateLayerVisibilities(style: Style, state: MapUiState) {
        val vis = state.layerVisibilities
        val filterCat = state.selectedFilter?.category

        val firesVis = (filterCat == null || filterCat == MapFeatureCategory.ACTIVE_FIRE_DETECTION) &&
                (vis[MapFeatureCategory.ACTIVE_FIRE_DETECTION] != false)

        val commVis = (filterCat == null || filterCat == MapFeatureCategory.COMMUNITY_HAZARD) &&
                (vis[MapFeatureCategory.COMMUNITY_HAZARD] != false)

        val hazardVis = (filterCat == null || filterCat == MapFeatureCategory.HAZARD_ZONE) &&
                (vis[MapFeatureCategory.HAZARD_ZONE] != false)

        val forestVis = (filterCat == null || filterCat == MapFeatureCategory.FOREST_AREA) &&
                (vis[MapFeatureCategory.FOREST_AREA] != false)

        val weatherVis = (filterCat == null || filterCat == MapFeatureCategory.WEATHER_POINT) &&
                (vis[MapFeatureCategory.WEATHER_POINT] != false)

        // Facility visibility toggles
        val hospVis = (filterCat == null || filterCat == MapFeatureCategory.HOSPITAL) &&
                (vis[MapFeatureCategory.HOSPITAL] != false)

        val clinicVis = (filterCat == null || filterCat == MapFeatureCategory.CLINIC) &&
                (vis[MapFeatureCategory.CLINIC] != false)

        val polVis = (filterCat == null || filterCat == MapFeatureCategory.POLICE_STATION) &&
                (vis[MapFeatureCategory.POLICE_STATION] != false)

        val fuelVis = (filterCat == null || filterCat == MapFeatureCategory.PETROL_PUMP) &&
                (vis[MapFeatureCategory.PETROL_PUMP] != false)

        val fireStVis = (filterCat == null || filterCat == MapFeatureCategory.FIRE_STATION) &&
                (vis[MapFeatureCategory.FIRE_STATION] != false)

        val shelterVis = (filterCat == null || filterCat == MapFeatureCategory.SHELTER || filterCat == MapFeatureCategory.TOURIST_ASSISTANCE) &&
                (vis[MapFeatureCategory.SHELTER] != false)

        // Fires
        style.getLayer(LAYER_ACTIVE_FIRES_CIRCLE)?.setProperties(
            visibility(if (firesVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_ACTIVE_FIRES_SYMBOL)?.setProperties(
            visibility(if (firesVis) Property.VISIBLE else Property.NONE)
        )

        // Community Hazards
        style.getLayer(LAYER_COMMUNITY_HAZARDS_CIRCLE)?.setProperties(
            visibility(if (commVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_COMMUNITY_HAZARDS_SYMBOL)?.setProperties(
            visibility(if (commVis) Property.VISIBLE else Property.NONE)
        )

        // Hazards
        style.getLayer(LAYER_DISASTER_FILL)?.setProperties(
            visibility(if (hazardVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_DISASTER_LINE)?.setProperties(
            visibility(if (hazardVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_DISASTER_POINTS_CIRCLE)?.setProperties(
            visibility(if (hazardVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_DISASTER_POINTS_SYMBOL)?.setProperties(
            visibility(if (hazardVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_HISTORICAL_LANDSLIDES_CIRCLE)?.setProperties(
            visibility(if (hazardVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_HISTORICAL_LANDSLIDES_SYMBOL)?.setProperties(
            visibility(if (hazardVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_ROUTE_HAZARD_SEQUENCE_SYMBOL)?.setProperties(
            visibility(if (hazardVis) Property.VISIBLE else Property.NONE)
        )

        // Forests
        style.getLayer(LAYER_FOREST_FILL)?.setProperties(
            visibility(if (forestVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_FOREST_LINE)?.setProperties(
            visibility(if (forestVis) Property.VISIBLE else Property.NONE)
        )

        // Weather
        style.getLayer(LAYER_WEATHER_POINTS_CIRCLE)?.setProperties(
            visibility(if (weatherVis) Property.VISIBLE else Property.NONE)
        )

        // Facilities individual toggles (circle background + icon symbol)
        style.getLayer(LAYER_HOSPITALS_CIRCLE)?.setProperties(
            visibility(if (hospVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_HOSPITALS_SYMBOL)?.setProperties(
            visibility(if (hospVis) Property.VISIBLE else Property.NONE)
        )

        style.getLayer(LAYER_CLINICS_CIRCLE)?.setProperties(
            visibility(if (clinicVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_CLINICS_SYMBOL)?.setProperties(
            visibility(if (clinicVis) Property.VISIBLE else Property.NONE)
        )

        style.getLayer(LAYER_POLICE_CIRCLE)?.setProperties(
            visibility(if (polVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_POLICE_SYMBOL)?.setProperties(
            visibility(if (polVis) Property.VISIBLE else Property.NONE)
        )

        style.getLayer(LAYER_FUEL_CIRCLE)?.setProperties(
            visibility(if (fuelVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_FUEL_SYMBOL)?.setProperties(
            visibility(if (fuelVis) Property.VISIBLE else Property.NONE)
        )

        style.getLayer(LAYER_FIRE_STATIONS_CIRCLE)?.setProperties(
            visibility(if (fireStVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_FIRE_STATIONS_SYMBOL)?.setProperties(
            visibility(if (fireStVis) Property.VISIBLE else Property.NONE)
        )

        style.getLayer(LAYER_SAFE_HAVENS_CIRCLE)?.setProperties(
            visibility(if (shelterVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_SAFE_HAVENS_SYMBOL)?.setProperties(
            visibility(if (shelterVis) Property.VISIBLE else Property.NONE)
        )

        // Cellular Tower & Range Visibility
        val cellVis = (filterCat == null || filterCat == MapFeatureCategory.CELL_TOWER) &&
                (vis[MapFeatureCategory.CELL_TOWER] != false)

        style.getLayer(LAYER_CELLULAR_RANGE_FILL)?.setProperties(
            visibility(if (cellVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_CELLULAR_RANGE_LINE)?.setProperties(
            visibility(if (cellVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_CELLULAR_SITES_CIRCLE)?.setProperties(
            visibility(if (cellVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_CELLULAR_SITES_SYMBOL)?.setProperties(
            visibility(if (cellVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_CELLULAR_COVERAGE_LINE)?.setProperties(
            visibility(if (cellVis) Property.VISIBLE else Property.NONE)
        )

        // Waterways Visibility (Rivers, Lakes, Flood Inundation Zones)
        val waterVis = (filterCat == null || filterCat == MapFeatureCategory.WATERWAY) &&
                (vis[MapFeatureCategory.WATERWAY] != false)

        style.getLayer(LAYER_WATERWAYS_FILL)?.setProperties(
            visibility(if (waterVis) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(LAYER_WATERWAYS_LINE)?.setProperties(
            visibility(if (waterVis) Property.VISIBLE else Property.NONE)
        )
    }

    /**
     * Synthesizes 32-point geodesic circular polygons for cell masts directly on Android client
     * if precomputed range polygons are absent. Guarantees visible range halos across all conditions.
     */
    fun synthesizeCellularRangeGeoJson(sitesGeoJson: String, numPoints: Int = 32): String {
        return try {
            val json = org.json.JSONObject(sitesGeoJson)
            val features = json.optJSONArray("features") ?: return EMPTY_GEOJSON
            val rangeFeatures = org.json.JSONArray()
            val rEarth = 6371000.0

            for (i in 0 until features.length()) {
                val feat = features.optJSONObject(i) ?: continue
                val geom = feat.optJSONObject("geometry") ?: continue
                val coords = geom.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue
                val lon = coords.optDouble(0)
                val lat = coords.optDouble(1)
                if (lat.isNaN() || lon.isNaN()) continue

                val props = feat.optJSONObject("properties") ?: org.json.JSONObject()
                val rangeM = props.optDouble("range_m", 2500.0).coerceAtLeast(500.0)
                val operator = props.optString("operator", "Mobile Network")
                val radio = props.optString("radio", "LTE")
                val cellId = feat.optString("id", "tower_$i")

                val latRad = Math.toRadians(lat)
                val lonRad = Math.toRadians(lon)
                val dDivR = rangeM / rEarth

                val ringArray = org.json.JSONArray()
                for (p in 0..numPoints) {
                    val bearing = 2.0 * Math.PI * (p % numPoints) / numPoints
                    val ptLat = Math.asin(
                        Math.sin(latRad) * Math.cos(dDivR) +
                                Math.cos(latRad) * Math.sin(dDivR) * Math.cos(bearing)
                    )
                    val ptLon = lonRad + Math.atan2(
                        Math.sin(bearing) * Math.sin(dDivR) * Math.cos(latRad),
                        Math.cos(dDivR) - Math.sin(latRad) * Math.sin(ptLat)
                    )
                    val ptCoord = org.json.JSONArray()
                    ptCoord.put(Math.round(Math.toDegrees(ptLon) * 1000000.0) / 1000000.0)
                    ptCoord.put(Math.round(Math.toDegrees(ptLat) * 1000000.0) / 1000000.0)
                    ringArray.put(ptCoord)
                }

                val polyCoords = org.json.JSONArray()
                polyCoords.put(ringArray)

                val polyGeom = org.json.JSONObject()
                polyGeom.put("type", "Polygon")
                polyGeom.put("coordinates", polyCoords)

                val rangeProp = org.json.JSONObject()
                rangeProp.put("id", cellId)
                rangeProp.put("operator", operator)
                rangeProp.put("radio", radio)
                rangeProp.put("range_m", rangeM)

                val rangeFeat = org.json.JSONObject()
                rangeFeat.put("type", "Feature")
                rangeFeat.put("id", "range_$cellId")
                rangeFeat.put("geometry", polyGeom)
                rangeFeat.put("properties", rangeProp)

                rangeFeatures.put(rangeFeat)
            }

            val out = org.json.JSONObject()
            out.put("type", "FeatureCollection")
            out.put("features", rangeFeatures)
            out.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Error synthesizing cellular range: ${e.message}")
            EMPTY_GEOJSON
        }
    }

    fun escapeJson(text: String?): String {
        if (text == null) return ""
        val sb = StringBuilder()
        for (c in text) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code in 0x00..0x1F) {
                        sb.append(String.format(java.util.Locale.US, "\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun buildCorridorGeoJson(rings: List<List<Pair<Double, Double>>>): String {
        if (rings.isEmpty()) return EMPTY_GEOJSON
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        var count = 0
        rings.forEach { ring ->
            if (ring.size >= 3) {
                if (count > 0) sb.append(",")
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
                count++
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
                val safeTitle = escapeJson(feat.title)
                val safeSub = escapeJson(feat.subtitle)
                val safeId = escapeJson(feat.id)
                sb.append("{\"type\":\"Feature\",\"id\":\"$safeId\",\"properties\":{")
                sb.append("\"id\":\"$safeId\",\"title\":\"$safeTitle\",\"subtitle\":\"$safeSub\",\"category\":\"${feat.category.name}\"")
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

    private fun buildDisasterFeatureCollection(features: List<UnifiedMapFeature>): String {
        if (features.isEmpty()) return EMPTY_GEOJSON
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        var count = 0
        features.forEach { feat ->
            val safeTitle = escapeJson(feat.title)
            val safeSub = escapeJson(feat.subtitle)
            val safeId = escapeJson(feat.id)
            val hazardCat = (feat.details["hazard_category"] ?: feat.details["Category"] ?: feat.details["hazard_type"] ?: "")
                .lowercase().replace(' ', '_').trim()
            val safeHazardCat = escapeJson(hazardCat)
            val severity = (feat.details["severity"] ?: feat.details["Severity"] ?: "moderate")
                .lowercase().trim()
            val safeSeverity = escapeJson(severity)
            val ring = feat.geometryPolygon
            if (ring != null && ring.size >= 3) {
                if (count > 0) sb.append(",")
                sb.append("{\"type\":\"Feature\",\"id\":\"$safeId\",\"properties\":{")
                sb.append("\"id\":\"$safeId\",\"title\":\"$safeTitle\",\"subtitle\":\"$safeSub\",\"category\":\"${feat.category.name}\",\"hazard_category\":\"$safeHazardCat\",\"severity\":\"$safeSeverity\",\"geom_type\":\"Polygon\"")
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

                // Dual emission: Also emit a centroid Point feature so symbol & circle layers render the hazard warning icon
                sb.append(",")
                sb.append("{\"type\":\"Feature\",\"id\":\"${safeId}_center\",\"properties\":{")
                sb.append("\"id\":\"$safeId\",\"title\":\"$safeTitle\",\"subtitle\":\"$safeSub\",\"category\":\"${feat.category.name}\",\"hazard_category\":\"$safeHazardCat\",\"severity\":\"$safeSeverity\",\"geom_type\":\"Point\"")
                sb.append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${feat.longitude},${feat.latitude}]}}")
                count++
            } else {
                // Point hazard advisory
                if (count > 0) sb.append(",")
                sb.append("{\"type\":\"Feature\",\"id\":\"$safeId\",\"properties\":{")
                sb.append("\"id\":\"$safeId\",\"title\":\"$safeTitle\",\"subtitle\":\"$safeSub\",\"category\":\"${feat.category.name}\",\"hazard_category\":\"$safeHazardCat\",\"severity\":\"$safeSeverity\",\"geom_type\":\"Point\"")
                sb.append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${feat.longitude},${feat.latitude}]}}")
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
            val safeTitle = escapeJson(feat.title)
            val safeSub = escapeJson(feat.subtitle)
            val safeId = escapeJson(feat.id)
            val catSlug = when (feat.category) {
                MapFeatureCategory.HOSPITAL -> "hospital"
                MapFeatureCategory.CLINIC -> "clinic"
                MapFeatureCategory.POLICE_STATION -> "police"
                MapFeatureCategory.PETROL_PUMP -> "fuel"
                MapFeatureCategory.FIRE_STATION -> "fire_station"
                MapFeatureCategory.SHELTER -> "shelter"
                else -> feat.category.name.lowercase()
            }
            val safePhone = escapeJson(feat.phone)
            val safeAddr = escapeJson(feat.address)
            val safeProv = escapeJson(feat.sourceName)
            sb.append("{\"type\":\"Feature\",\"id\":\"$safeId\",\"properties\":{")
            sb.append("\"id\":\"$safeId\",\"name\":\"$safeTitle\",\"title\":\"$safeTitle\",\"subtitle\":\"$safeSub\",")
            sb.append("\"category\":\"$catSlug\",\"category_enum\":\"${feat.category.name}\",\"facility_category\":\"$catSlug\",")
            sb.append("\"phone\":\"$safePhone\",\"address\":\"$safeAddr\",\"provenance\":\"$safeProv\"")
            sb.append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${feat.longitude},${feat.latitude}]}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    /**
     * Ensures any raw GeoJSON string (e.g. LineString or Polygon) is wrapped into a valid FeatureCollection
     * so that MapLibre GeoJsonSource can parse and render it without runtime schema errors.
     */
    fun ensureFeatureCollection(rawJson: String?): String {
        if (rawJson.isNullOrBlank() || rawJson.trim() == "{}" || rawJson.trim() == "null") {
            return "{\"type\":\"FeatureCollection\",\"features\":[]}"
        }
        val trimmed = rawJson.trim()
        return try {
            val root = org.json.JSONObject(trimmed)
            val type = root.optString("type")
            if (type.equals("FeatureCollection", ignoreCase = true)) {
                trimmed
            } else if (type.equals("Feature", ignoreCase = true)) {
                "{\"type\":\"FeatureCollection\",\"features\":[$trimmed]}"
            } else {
                "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":$trimmed}]}"
            }
        } catch (_: Exception) {
            "{\"type\":\"FeatureCollection\",\"features\":[]}"
        }
    }

    /**
     * Checks whether a GeoJSON string contains at least one non-empty feature.
     * Prevents empty FeatureCollections from overwriting baseline or memory features.
     */
    fun hasFeatures(rawJson: String?): Boolean {
        if (rawJson.isNullOrBlank()) return false
        val trimmed = rawJson.trim()
        if (trimmed == "{}" || trimmed == "null" || trimmed.isEmpty()) return false
        return try {
            val root = org.json.JSONObject(trimmed)
            val features = root.optJSONArray("features")
            if (features != null) {
                features.length() > 0
            } else {
                root.has("geometry") || root.optString("type").equals("Feature", ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    private var cachedMaskGeoJson: String? = null
    private var cachedBoundaryGeoJson: String? = null
    private var cachedVerifiedTrailsGeoJson: String? = null

    private fun getOrLoadMaskGeoJson(context: android.content.Context): String? {
        if (cachedMaskGeoJson != null) return cachedMaskGeoJson
        return try {
            context.assets.open("uttarakhand_mask.geojson").use { stream ->
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                cachedMaskGeoJson = ensureFeatureCollection(text)
                cachedMaskGeoJson
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uttarakhand_mask.geojson from assets: ${e.message}", e)
            null
        }
    }

    private var cachedForestsBaselineGeoJson: String? = null

    private fun getOrLoadForestsBaselineGeoJson(context: android.content.Context): String? {
        if (cachedForestsBaselineGeoJson != null) return cachedForestsBaselineGeoJson
        return try {
            context.assets.open("uttarakhand_forest_reserves.geojson").use { stream ->
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                cachedForestsBaselineGeoJson = ensureFeatureCollection(text)
                cachedForestsBaselineGeoJson
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uttarakhand_forest_reserves.geojson from assets: ${e.message}", e)
            null
        }
    }

    private var cachedWaterwaysBaselineGeoJson: String? = null

    private fun getOrLoadWaterwaysBaselineGeoJson(context: android.content.Context): String? {
        if (cachedWaterwaysBaselineGeoJson != null) return cachedWaterwaysBaselineGeoJson
        return try {
            context.assets.open("uttarakhand_major_waterways.geojson").use { stream ->
                val str = stream.bufferedReader(Charsets.UTF_8).readText()
                cachedWaterwaysBaselineGeoJson = ensureFeatureCollection(str)
                cachedWaterwaysBaselineGeoJson
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uttarakhand_major_waterways.geojson from assets: ${e.message}", e)
            null
        }
    }

    private var cachedHazardZonesBaselineGeoJson: String? = null

    private fun getOrLoadHazardZonesBaselineGeoJson(context: android.content.Context): String? {
        if (cachedHazardZonesBaselineGeoJson != null) return cachedHazardZonesBaselineGeoJson
        return try {
            context.assets.open("uttarakhand_hazard_zones.geojson").use { stream ->
                val str = stream.bufferedReader(Charsets.UTF_8).readText()
                cachedHazardZonesBaselineGeoJson = ensureFeatureCollection(str)
                cachedHazardZonesBaselineGeoJson
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uttarakhand_hazard_zones.geojson from assets: ${e.message}", e)
            null
        }
    }

    private var cachedHistoricalLandslidesBaselineGeoJson: String? = null

    private fun getOrLoadHistoricalLandslidesBaselineGeoJson(context: android.content.Context): String? {
        if (cachedHistoricalLandslidesBaselineGeoJson != null) return cachedHistoricalLandslidesBaselineGeoJson
        return try {
            context.assets.open("uttarakhand_historical_landslides.geojson").use { stream ->
                val str = stream.bufferedReader(Charsets.UTF_8).readText()
                cachedHistoricalLandslidesBaselineGeoJson = ensureFeatureCollection(str)
                cachedHistoricalLandslidesBaselineGeoJson
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uttarakhand_historical_landslides.geojson from assets: ${e.message}", e)
            null
        }
    }

    private fun getOrLoadVerifiedTrailsGeoJson(context: android.content.Context): String? {
        if (cachedVerifiedTrailsGeoJson != null) return cachedVerifiedTrailsGeoJson
        return try {
            context.assets.open("uttarakhand_verified_trails.geojson").use { stream ->
                val str = stream.bufferedReader(Charsets.UTF_8).readText()
                cachedVerifiedTrailsGeoJson = ensureFeatureCollection(str)
                cachedVerifiedTrailsGeoJson
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uttarakhand_verified_trails.geojson from assets: ${e.message}", e)
            null
        }
    }

    private var cachedCellTowersBaselineGeoJson: String? = null

    private fun getOrLoadCellTowersBaselineGeoJson(context: android.content.Context): String? {
        if (cachedCellTowersBaselineGeoJson != null) return cachedCellTowersBaselineGeoJson
        return try {
            context.assets.open("uttarakhand_cell_towers.json").use { stream ->
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                val jsonArray = org.json.JSONArray(text)
                val featuresArray = org.json.JSONArray()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val lat = obj.optDouble("latitude")
                    val lon = obj.optDouble("longitude")
                    if (lat.isNaN() || lon.isNaN()) continue

                    val geom = org.json.JSONObject()
                    geom.put("type", "Point")
                    geom.put("coordinates", org.json.JSONArray().put(lon).put(lat))

                    val props = org.json.JSONObject()
                    props.put("id", obj.optString("id", "tower_$i"))
                    props.put("operator", obj.optString("operator", "Mobile Network"))
                    props.put("radio", obj.optString("radio", "LTE"))
                    props.put("range_m", obj.optDouble("range_m", 3000.0))
                    props.put("district", obj.optString("district", ""))
                    props.put("town", obj.optString("town", ""))

                    val feat = org.json.JSONObject()
                    feat.put("type", "Feature")
                    feat.put("id", obj.optString("id", "tower_$i"))
                    feat.put("geometry", geom)
                    feat.put("properties", props)

                    featuresArray.put(feat)
                }
                val fc = org.json.JSONObject()
                fc.put("type", "FeatureCollection")
                fc.put("features", featuresArray)
                cachedCellTowersBaselineGeoJson = fc.toString()
                cachedCellTowersBaselineGeoJson
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uttarakhand_cell_towers.json from assets: ${e.message}", e)
            null
        }
    }

    private fun getOrLoadBoundaryGeoJson(context: android.content.Context): String? {
        if (cachedBoundaryGeoJson != null) return cachedBoundaryGeoJson
        return try {
            context.assets.open("uttarakhand_boundary.geojson").use { stream ->
                val text = stream.bufferedReader(Charsets.UTF_8).readText()
                cachedBoundaryGeoJson = ensureFeatureCollection(text)
                cachedBoundaryGeoJson
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uttarakhand_boundary.geojson from assets: ${e.message}", e)
            null
        }
    }

    /**
     * Serializes ordered route hazard items into MapLibre GeoJSON FeatureCollection.
     * Attaches category icon keys, sequence numbering labels, and selection states.
     */
    fun buildHazardSequenceGeoJson(
        items: List<com.touristsafety.app.core.model.RouteOrderedHazardItem>,
        selectedId: String? = null
    ): String {
        if (items.isEmpty()) return EMPTY_GEOJSON
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        items.forEachIndexed { idx, item ->
            if (idx > 0) sb.append(",")
            val isSelected = item.feature.id == selectedId || item.sequenceLabel == selectedId
            val safeId = escapeJson(item.feature.id)
            val safeTitle = escapeJson(item.feature.title)
            val safeLabel = escapeJson(item.sequenceLabel)
            val iconId = escapeJson(item.categoryType.iconKey)
            sb.append("{\"type\":\"Feature\",\"id\":\"$safeId\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
            sb.append(item.feature.longitude).append(",").append(item.feature.latitude)
            sb.append("]},\"properties\":{")
            sb.append("\"id\":\"$safeId\",")
            sb.append("\"sequence_label\":\"$safeLabel\",")
            sb.append("\"sequence_number\":${item.sequenceNumber},")
            sb.append("\"title\":\"$safeTitle\",")
            sb.append("\"category\":\"${item.feature.category.name}\",")
            sb.append("\"hazard_category\":\"${item.categoryType.id}\",")
            sb.append("\"icon_id\":\"$iconId\",")
            sb.append("\"is_selected\":$isSelected,")
            sb.append("\"is_direct_intersection\":${item.intersectionType == com.touristsafety.app.core.model.RouteIntersectionType.DIRECT_INTERSECTION},")
            sb.append("\"trek_status\":\"${item.trekStatus.name}\"")
            sb.append("}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    fun updateDraftRoute(style: Style, waypoints: List<Pair<Double, Double>>, polyline: List<Pair<Double, Double>>) {
        if (!style.isFullyLoaded) return
        val routeSource = style.getSourceAs<GeoJsonSource>(SOURCE_DRAFT_ROUTE)
        val wptSource = style.getSourceAs<GeoJsonSource>(SOURCE_DRAFT_WAYPOINTS)
        if (polyline.size < 2) {
            routeSource?.setGeoJson(EMPTY_GEOJSON)
        } else {
            val sb = StringBuilder()
            sb.append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
            polyline.forEachIndexed { i, (lat, lon) ->
                if (i > 0) sb.append(",")
                sb.append("[").append(lon).append(",").append(lat).append("]")
            }
            sb.append("]}}]}")
            routeSource?.setGeoJson(sb.toString())
        }

        if (waypoints.isEmpty()) {
            wptSource?.setGeoJson(EMPTY_GEOJSON)
        } else {
            val sb = StringBuilder()
            sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
            waypoints.forEachIndexed { i, (lat, lon) ->
                if (i > 0) sb.append(",")
                sb.append("{\"type\":\"Feature\",\"properties\":{\"index\":").append(i).append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                sb.append(lon).append(",").append(lat).append("]}}")
            }
            sb.append("]}")
            wptSource?.setGeoJson(sb.toString())
        }
    }

    fun clearDraftRoute(style: Style) {
        if (!style.isFullyLoaded) return
        style.getSourceAs<GeoJsonSource>(SOURCE_DRAFT_ROUTE)?.setGeoJson(EMPTY_GEOJSON)
        style.getSourceAs<GeoJsonSource>(SOURCE_DRAFT_WAYPOINTS)?.setGeoJson(EMPTY_GEOJSON)
    }

    fun updateProposedConnector(style: Style, connectorCoords: List<Pair<Double, Double>>) {
        if (!style.isFullyLoaded) return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_PROPOSED_CONNECTOR) ?: return
        if (connectorCoords.size < 2) {
            source.setGeoJson(EMPTY_GEOJSON)
            return
        }
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        connectorCoords.forEachIndexed { i, (lat, lon) ->
            if (i > 0) sb.append(",")
            sb.append("[").append(lon).append(",").append(lat).append("]")
        }
        sb.append("]}}]}")
        source.setGeoJson(sb.toString())
    }

    fun updateTraveledTrack(style: Style, coordinates: List<Pair<Double, Double>>) {
        if (!style.isFullyLoaded) return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_TRAVELED_TRACK) ?: return
        if (coordinates.size < 2) {
            source.setGeoJson(EMPTY_GEOJSON)
            return
        }
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        coordinates.forEachIndexed { idx, (lat, lon) ->
            if (idx > 0) sb.append(",")
            sb.append("[").append(lon).append(",").append(lat).append("]")
        }
        sb.append("]}}]}")
        source.setGeoJson(sb.toString())
    }

    fun updatePrimaryRoute(style: Style, coordinates: List<Pair<Double, Double>>) {
        if (!style.isFullyLoaded) return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_PRIMARY) ?: return
        if (coordinates.size < 2) {
            source.setGeoJson(EMPTY_GEOJSON)
            return
        }
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        coordinates.forEachIndexed { idx, (lat, lon) ->
            if (idx > 0) sb.append(",")
            sb.append("[").append(lon).append(",").append(lat).append("]")
        }
        sb.append("]}}]}")
        source.setGeoJson(sb.toString())
    }

    fun updateTrekCurrentPosition(style: Style, lat: Double, lon: Double, accuracyMeters: Float) {
        if (!style.isFullyLoaded) return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_TREK_CURRENT_POS) ?: return
        val radiusDisplay = maxOf(10.0f, minOf(50.0f, accuracyMeters))
        val geoJson = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[$lon,$lat]},\"properties\":{\"radius\":$radiusDisplay}}]}"
        source.setGeoJson(geoJson)
    }

    fun updateRouteHazardSequence(style: Style, items: List<com.touristsafety.app.core.model.RouteOrderedHazardItem>, selectedId: String? = null) {
        if (!style.isFullyLoaded) return
        try {
            val source = style.getSourceAs<GeoJsonSource>(SOURCE_ROUTE_HAZARD_SEQUENCE) ?: return
            val geoJson = if (items.isNotEmpty()) {
                buildHazardSequenceGeoJson(items, selectedId)
            } else {
                EMPTY_GEOJSON
            }
            source.setGeoJson(geoJson)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed updating route hazard sequence: ${t.message}")
        }
    }

    fun setLayerCategoryVisibility(style: Style, category: String, visible: Boolean) {
        if (!style.isFullyLoaded) return
        val visibility = if (visible) Property.VISIBLE else Property.NONE
        when (category.lowercase()) {
            "hazards" -> {
                style.getLayer(LAYER_ROUTE_HAZARD_SEQUENCE_SYMBOL)?.setProperties(visibility(visibility))
                style.getLayer(LAYER_DISASTER_FILL)?.setProperties(visibility(visibility))
                style.getLayer(LAYER_DISASTER_LINE)?.setProperties(visibility(visibility))
            }
            "fires" -> {
                style.getLayer(LAYER_ACTIVE_FIRES_CIRCLE)?.setProperties(visibility(visibility))
                style.getLayer(LAYER_ACTIVE_FIRES_SYMBOL)?.setProperties(visibility(visibility))
            }
            "hospitals" -> {
                style.getLayer(LAYER_HOSPITALS_CIRCLE)?.setProperties(visibility(visibility))
                style.getLayer(LAYER_HOSPITALS_SYMBOL)?.setProperties(visibility(visibility))
                style.getLayer(LAYER_CLINICS_CIRCLE)?.setProperties(visibility(visibility))
                style.getLayer(LAYER_CLINICS_SYMBOL)?.setProperties(visibility(visibility))
            }
            "police" -> {
                style.getLayer(LAYER_POLICE_CIRCLE)?.setProperties(visibility(visibility))
                style.getLayer(LAYER_POLICE_SYMBOL)?.setProperties(visibility(visibility))
            }
            "shelters" -> {
                style.getLayer(LAYER_SAFE_HAVENS_CIRCLE)?.setProperties(visibility(visibility))
                style.getLayer(LAYER_SAFE_HAVENS_SYMBOL)?.setProperties(visibility(visibility))
            }
        }
    }
}
