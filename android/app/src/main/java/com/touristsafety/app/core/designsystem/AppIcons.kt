package com.touristsafety.app.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralized semantic icon language for the Uttarakhand Trek Safety application.
 * Ensures consistent visual vocabulary across all screens.
 */
object AppIcons {
    // Navigation & Primary Pillars
    val Home: ImageVector = Icons.Default.Home
    val PlanTrip: ImageVector = Icons.Default.Directions
    val Map: ImageVector = Icons.Default.Map
    val Emergency: ImageVector = Icons.Default.Emergency
    val Alerts: ImageVector = Icons.Default.Warning
    val Saved: ImageVector = Icons.Default.CloudDownload
    val DisasterSafety: ImageVector = Icons.Default.Shield
    val ProviderStatus: ImageVector = Icons.Default.CloudQueue

    // Actions
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Refresh: ImageVector = Icons.Default.Refresh
    val Search: ImageVector = Icons.Default.Search
    val Clear: ImageVector = Icons.Default.Clear
    val Close: ImageVector = Icons.Default.Close
    val Share: ImageVector = Icons.Default.Share
    val Download: ImageVector = Icons.Default.Download
    val StartTrek: ImageVector = Icons.Default.PlayArrow
    val PauseTrek: ImageVector = Icons.Default.Pause
    val StopTrek: ImageVector = Icons.Default.Stop
    val MyLocation: ImageVector = Icons.Default.MyLocation
    val Layers: ImageVector = Icons.Default.Layers
    val Filter: ImageVector = Icons.Default.FilterList
    val Check: ImageVector = Icons.Default.Check
    val ChevronRight: ImageVector = Icons.Default.ChevronRight

    // Travel Modes
    val Hiking: ImageVector = Icons.AutoMirrored.Filled.DirectionsWalk
    val Driving: ImageVector = Icons.Default.DirectionsCar

    // Hazards
    val HazardGeneric: ImageVector = Icons.Default.Warning
    val Thunderstorm: ImageVector = Icons.Default.Thunderstorm
    val Flood: ImageVector = Icons.Default.WaterDrop
    val Wildfire: ImageVector = Icons.Default.LocalFireDepartment
    val Landslide: ImageVector = Icons.Default.Terrain
    val TrailClosure: ImageVector = Icons.Default.Block

    // Facilities
    val Hospital: ImageVector = Icons.Default.LocalHospital
    val Police: ImageVector = Icons.Default.LocalPolice
    val Shelter: ImageVector = Icons.Default.Cabin
    val Pharmacy: ImageVector = Icons.Default.MedicalServices

    // Status
    val Safe: ImageVector = Icons.Default.CheckCircle
    val Caution: ImageVector = Icons.Default.Warning
    val Critical: ImageVector = Icons.Default.CrisisAlert
    val Offline: ImageVector = Icons.Default.CloudOff
    val Synced: ImageVector = Icons.Default.CloudDone

    // Emergency & Assistance
    val DialEmergency: ImageVector = Icons.Default.PhoneInTalk
    val Phone: ImageVector = Icons.Default.Phone
    val SosBeacon: ImageVector = Icons.Default.CrisisAlert
    val Siren: ImageVector = Icons.Default.VolumeUp
}

