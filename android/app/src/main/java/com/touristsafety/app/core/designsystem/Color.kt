package com.touristsafety.app.core.designsystem

import androidx.compose.ui.graphics.Color

// ============================================================================
// Official Premium Dark Outdoor Navigation Palette (Reference Design Tokens)
// ============================================================================
val DarkBackground = Color(0xFF151A1C)        // Main background
val DarkSurfacePrimary = Color(0xFF202829)    // Primary surface
val DarkSurfaceSecondary = Color(0xFF263332)  // Secondary surface
val DarkSurfaceElevated = Color(0xFF303D3A)   // Elevated surface
val MintPrimary = Color(0xFFB8F0D6)           // Primary mint (active / selected)
val MintSecondary = Color(0xFF88CDB2)         // Secondary mint
val TextPrimary = Color(0xFFF4F7F5)           // Primary warm white text
val TextSecondary = Color(0xFFAAB7B1)         // Secondary muted grey-green text
val WarningAmber = Color(0xFFF2A477)          // Restrained warning amber
val CriticalRed = Color(0xFFB83232)           // Critical emergency red
val DarkDivider = Color(0xFF35413E)           // Subtle divider & stroke

// Deep Emergency & Distress Card Styling
val DarkEmergencyContainer = Color(0xFF5C1414) // Dark crimson surface
val DarkEmergencyBanner = Color(0xFF6E1818)    // Prominent emergency banner
val DarkEmergencyBorder = Color(0xFF8B1E1E)    // Dark red border stroke
val EmergencyRed = CriticalRed                 // Canonical SOS / 112 / Critical alert

// Alpine Outdoor Safety & Legacy Aliases
val ForestGreenPrimary = MintSecondary
val ForestGreenDark = Color(0xFF1B2624)
val ForestGreenLight = MintPrimary

val Sage = Color(0xFFE2EBE5)
val SageDark = Color(0xFF4A6572)
val OnSageContainer = Color(0xFF132A22)
val SageContainer = Color(0xFF253330)

val MountainSlate = Color(0xFF37474F)
val MountainMist = Color(0xFFECEFF1)

// Neutral Canvas (Light Theme Fallback)
val BackgroundLight = Color(0xFFF4F7F5)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE6EFEA)
val TextPrimaryLight = Color(0xFF151A1C)
val TextSecondaryLight = Color(0xFF4B5752)
val OutlineLight = Color(0xFFB0BFB8)
val OutlineVariantLight = Color(0xFFD2DCD6)

// Dark Theme Canvas Aliases
val BackgroundDark = DarkBackground
val SurfaceDark = DarkSurfacePrimary
val SurfaceVariantDark = DarkSurfaceSecondary
val TextPrimaryDark = TextPrimary
val TextSecondaryDark = TextSecondary
val OutlineDark = DarkDivider

// Semantic Status Colors
val EmergencyRedContainer = DarkEmergencyContainer
val OnEmergencyRed = Color.White

val WarningAmberContainer = Color(0xFF3D2A1F)
val OnWarningAmber = Color.White

val SafeGreen = Color(0xFF10B981)
val SafeGreenContainer = Color(0xFF1A332B)
val OnSafeGreen = Color.White

val InfoBlue = Color(0xFF38BDF8)
val InfoBlueContainer = Color(0xFF172B38)
val OnInfoBlue = Color.White

// Compatibility Aliases
val PrimaryBlue = ForestGreenPrimary
val PrimaryDarkBlue = ForestGreenDark
val SecondaryTeal = ForestGreenLight
val AlertCriticalRed = EmergencyRed
val AlertHighOrange = WarningAmber
val AlertMediumAmber = Color(0xFFF59E0B)
val AlertLowBlue = InfoBlue
val FreshGreen = SafeGreen
val StaleGray = Color(0xFF6B7280)
val SimulationPurple = Color(0xFF8B5CF6)
