package com.touristsafety.app.core.designsystem

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val ExpressiveLightColorScheme = lightColorScheme(
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
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = BackgroundLight,
    surfaceContainer = SurfaceVariantLight,
    surfaceContainerHigh = Sage,
    surfaceContainerHighest = SageContainer,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = EmergencyRed,
    onError = OnEmergencyRed,
    errorContainer = EmergencyRedContainer,
    onErrorContainer = EmergencyRed
)

val ExpressiveDarkColorScheme = darkColorScheme(
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
    surfaceContainerLowest = Color(0xFF0C0F0E),
    surfaceContainerLow = BackgroundDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = Color(0xFF38403C),
    outline = OutlineDark,
    error = EmergencyRed,
    onError = OnEmergencyRed,
    errorContainer = EmergencyRed.copy(alpha = 0.25f),
    onErrorContainer = Color(0xFFFFB4AB)
)

/**
 * TouristSafetyTheme modernizes the application theme into Material 3 Expressive.
 * - Dynamic wallpaper color on Android 12+ (API 31+) with expressive alpine fallbacks
 * - Strictly paired container and content color roles
 * - Physics-based motion tokens and reduced-motion accessibility provider
 * - Expressive increased shape tokens and typography
 */
@Composable
fun TouristSafetyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ExpressiveDarkColorScheme
        else -> ExpressiveLightColorScheme
    }

    // Check system animation scale for reduced motion accessibility
    val isReducedMotion = runCatching {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        scale == 0f
    }.getOrDefault(false)

    CompositionLocalProvider(LocalReduceMotion provides isReducedMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TouristSafetyTypography,
            content = content
        )
    }
}
