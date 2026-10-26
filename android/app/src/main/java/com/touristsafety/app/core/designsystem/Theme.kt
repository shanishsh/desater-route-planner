package com.touristsafety.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ForestGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = SageContainer,
    onPrimaryContainer = OnSageContainer,
    secondary = ForestGreenLight,
    onSecondary = Color.White,
    secondaryContainer = Sage,
    onSecondaryContainer = ForestGreenDark,
    tertiary = MountainSlate,
    onTertiary = Color.White,
    tertiaryContainer = MountainMist,
    onTertiaryContainer = MountainSlate,
    background = BackgroundLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = EmergencyRed,
    onError = OnEmergencyRed,
    errorContainer = EmergencyRedContainer,
    onErrorContainer = EmergencyRed
)

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenLight,
    onPrimary = Color.White,
    primaryContainer = ForestGreenDark,
    onPrimaryContainer = Sage,
    secondary = Sage,
    onSecondary = ForestGreenDark,
    secondaryContainer = ForestGreenPrimary,
    onSecondaryContainer = Color.White,
    tertiary = MountainMist,
    onTertiary = MountainSlate,
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = EmergencyRed,
    onError = OnEmergencyRed,
    errorContainer = EmergencyRed.copy(alpha = 0.25f),
    onErrorContainer = Color(0xFFFFB4AB)
)

@Composable
fun TouristSafetyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TouristSafetyTypography,
        content = content
    )
}
