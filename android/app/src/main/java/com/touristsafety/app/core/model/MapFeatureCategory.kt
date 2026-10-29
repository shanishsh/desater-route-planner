package com.touristsafety.app.core.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralized enumeration of all map feature categories.
 * Provides consistent icons, brand colors, and display names across MapLibre layers,
 * filter chips, clustering, and compact bottom sheets.
 */
enum class MapFeatureCategory(
    val displayName: String,
    val icon: ImageVector,
    val primaryColor: Color,
    val isAreaFeature: Boolean = false
) {
    HOSPITAL(
        displayName = "Hospital",
        icon = Icons.Default.LocalHospital,
        primaryColor = Color(0xFFE53935) // Medical Red
    ),
    CLINIC(
        displayName = "First-Aid Clinic",
        icon = Icons.Default.MedicalServices,
        primaryColor = Color(0xFF00897B) // Teal Clinic
    ),
    POLICE_STATION(
        displayName = "Police Post",
        icon = Icons.Default.LocalPolice,
        primaryColor = Color(0xFF1E88E5) // Police Blue
    ),
    FIRE_STATION(
        displayName = "Fire Station",
        icon = Icons.Default.FireTruck,
        primaryColor = Color(0xFFE65100) // Fire Service Amber-Red
    ),
    SHELTER(
        displayName = "Evacuation Shelter",
        icon = Icons.Default.NightShelter,
        primaryColor = Color(0xFF43A047) // Shelter Green
    ),
    PETROL_PUMP(
        displayName = "Petrol Pump / Fuel",
        icon = Icons.Default.LocalGasStation,
        primaryColor = Color(0xFFF57C00) // Fuel Amber Orange
    ),
    TOURIST_ASSISTANCE(
        displayName = "Assistance & Info",
        icon = Icons.Default.Info,
        primaryColor = Color(0xFF8E24AA) // Purple Assistance
    ),
    FOREST_AREA(
        displayName = "Forest Cover",
        icon = Icons.Default.Park,
        primaryColor = Color(0xFF2E7D32), // Forest Green
        isAreaFeature = true
    ),
    ACTIVE_FIRE_DETECTION(
        displayName = "Active Wildfire",
        icon = Icons.Default.Whatshot,
        primaryColor = Color(0xFFD32F2F) // Alert Flame Red
    ),
    WEATHER_POINT(
        displayName = "Route Weather",
        icon = Icons.Default.WbSunny,
        primaryColor = Color(0xFF0288D1) // Sky Blue
    ),
    HAZARD_ZONE(
        displayName = "Disaster Hazard Zone",
        icon = Icons.Default.Warning,
        primaryColor = Color(0xFFC62828), // Critical Hazard Deep Red
        isAreaFeature = true
    ),
    ROUTE_WAYPOINT(
        displayName = "Route Waypoint",
        icon = Icons.Default.LocationOn,
        primaryColor = Color(0xFF00ACC1) // Waypoint Cyan
    ),
    CELL_TOWER(
        displayName = "Mobile Network Tower",
        icon = Icons.Default.CellTower,
        primaryColor = Color(0xFF651FFF) // Deep Purple / Indigo
    ),
    WATERWAY(
        displayName = "Rivers & Waterways",
        icon = Icons.Default.WaterDrop,
        primaryColor = Color(0xFF0288D1), // River Cyan-Blue
        isAreaFeature = true
    )
}
