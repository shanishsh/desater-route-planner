package com.touristsafety.app.core.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Recognized hazard and safety facility categories for the animated map and route timeline.
 * Supports weather extremes, geotechnical hazards, wildfires, seismic events, and safety infrastructure.
 */
enum class HazardCategoryType(
    val id: String,
    val displayName: String,
    val iconKey: String,
    val badgeColor: Color,
    val icon: ImageVector
) {
    THUNDERSTORM(
        id = "thunderstorm",
        displayName = "Thunderstorm Warning",
        iconKey = "icon-anim-thunderstorm",
        badgeColor = Color(0xFFD97706), // Amber-Brown
        icon = Icons.Default.Thunderstorm
    ),
    HEAVY_RAIN(
        id = "heavy_rain",
        displayName = "Heavy Rain / Cloudburst",
        iconKey = "icon-anim-heavy-rain",
        badgeColor = Color(0xFF0284C7), // Sky Blue
        icon = Icons.Default.WaterDrop
    ),
    FLOODING(
        id = "flooding",
        displayName = "River Flooding / Inundation",
        iconKey = "icon-anim-flooding",
        badgeColor = Color(0xFF0D9488), // Teal Water
        icon = Icons.Default.Waves
    ),
    LANDSLIDE(
        id = "landslide",
        displayName = "Landslide / Rockfall Hazard",
        iconKey = "icon-anim-landslide",
        badgeColor = Color(0xFFB45309), // Earth Brown
        icon = Icons.Default.Terrain
    ),
    WILDFIRE(
        id = "wildfire",
        displayName = "Active Wildfire Observation",
        iconKey = "icon-anim-wildfire",
        badgeColor = Color(0xFFDC2626), // Fire Red
        icon = Icons.Default.Whatshot
    ),
    EARTHQUAKE(
        id = "earthquake",
        displayName = "Seismic Activity / Tremor",
        iconKey = "icon-anim-earthquake",
        badgeColor = Color(0xFF7C3AED), // Seismic Purple
        icon = Icons.Default.Sensors
    ),
    TRAIL_CLOSURE(
        id = "trail_closure",
        displayName = "Route Obstruction / Blockage",
        iconKey = "icon-anim-closure",
        badgeColor = Color(0xFFE11D48), // Obstruction Crimson
        icon = Icons.Default.Block
    ),
    SEVERE_WEATHER(
        id = "severe_weather",
        displayName = "Severe Mountain Weather",
        iconKey = "icon-anim-thunderstorm",
        badgeColor = Color(0xFFEA580C), // Orange Warning
        icon = Icons.Default.Warning
    ),
    SAFETY_FACILITY(
        id = "safety_facility",
        displayName = "Verified Safety Facility",
        iconKey = "poi-hospital",
        badgeColor = Color(0xFF059669), // Emerald Green
        icon = Icons.Default.LocalHospital
    );

    companion object {
        /**
         * Resolves the hazard category type from feature details, title, and keywords.
         */
        fun fromFeature(feature: UnifiedMapFeature): HazardCategoryType {
            if (feature.category != MapFeatureCategory.HAZARD_ZONE &&
                feature.category != MapFeatureCategory.ACTIVE_FIRE_DETECTION &&
                feature.category != MapFeatureCategory.WEATHER_POINT
            ) {
                return SAFETY_FACILITY
            }

            if (feature.category == MapFeatureCategory.ACTIVE_FIRE_DETECTION) {
                return WILDFIRE
            }

            val titleLower = feature.title.lowercase()
            val catDetail = (feature.details["Category"] ?: "").lowercase()
            val fullText = "$titleLower $catDetail"

            return when {
                fullText.contains("thunder") || fullText.contains("lightning") || fullText.contains("storm") -> THUNDERSTORM
                fullText.contains("heavy rain") || fullText.contains("rain") || fullText.contains("cloudburst") || fullText.contains("downpour") -> HEAVY_RAIN
                fullText.contains("flood") || fullText.contains("water") || fullText.contains("river") || fullText.contains("inundation") -> FLOODING
                fullText.contains("landslide") || fullText.contains("rockfall") || fullText.contains("mudslide") || fullText.contains("slope") || fullText.contains("debris") -> LANDSLIDE
                fullText.contains("fire") || fullText.contains("wildfire") || fullText.contains("blaze") -> WILDFIRE
                fullText.contains("earthquake") || fullText.contains("seismic") || fullText.contains("tremor") -> EARTHQUAKE
                fullText.contains("closure") || fullText.contains("block") || fullText.contains("obstruction") || fullText.contains("damaged road") -> TRAIL_CLOSURE
                feature.category == MapFeatureCategory.WEATHER_POINT -> HEAVY_RAIN
                else -> SEVERE_WEATHER
            }
        }
    }
}

