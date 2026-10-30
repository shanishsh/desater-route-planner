package com.touristsafety.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val TouristSafetyTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

/**
 * Material 3 Expressive Emphasized Typography Tokens.
 *
 * Applied selectively to hero headers, focal metrics, active tabs, and primary CTAs
 * to establish a clear visual hierarchy without flattening body copy.
 */
val Typography.displayLargeEmphasized: TextStyle
    get() = displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)

val Typography.headlineLargeEmphasized: TextStyle
    get() = headlineMedium.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)

val Typography.headlineMediumEmphasized: TextStyle
    get() = headlineMedium.copy(fontWeight = FontWeight.Bold)

val Typography.titleLargeEmphasized: TextStyle
    get() = titleLarge.copy(fontWeight = FontWeight.Bold)

val Typography.titleMediumEmphasized: TextStyle
    get() = titleMedium.copy(fontWeight = FontWeight.Bold)

val Typography.labelLargeEmphasized: TextStyle
    get() = labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.25.sp)

val Typography.labelMediumEmphasized: TextStyle
    get() = labelMedium.copy(fontWeight = FontWeight.Bold)


