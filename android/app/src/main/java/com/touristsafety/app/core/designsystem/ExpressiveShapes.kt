package com.touristsafety.app.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Corner Radii and Shape Tokens.
 *
 * Expands beyond flat, uniform rectangles toward expressive increased corner radii:
 * - smallIncreased: 12.dp (chips, small badges, button toggles)
 * - mediumIncreased: 16.dp (nested cards, alert banners)
 * - largeIncreased: 28.dp (feature cards, bottom sheets, search bars)
 * - extraLargeIncreased: 36.dp (hero banners, modal dialogs)
 * - pill: CircleShape (status pills, travel mode toggles, floating toolbars)
 */
object ExpressiveShapes {
    val smallIncreased = RoundedCornerShape(12.dp)
    val mediumIncreased = RoundedCornerShape(16.dp)
    val largeIncreased = RoundedCornerShape(28.dp)
    val extraLargeIncreased = RoundedCornerShape(36.dp)
    val pill = CircleShape
}

