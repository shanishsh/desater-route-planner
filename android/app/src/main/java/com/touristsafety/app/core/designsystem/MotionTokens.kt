package com.touristsafety.app.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Material 3 Expressive Physics-Based Motion Tokens.
 *
 * Replaces hardcoded duration-based tween() curves with physics-based springs:
 * - Spatial specs: For position, bounds, shape morphing, and layout transitions (allowing natural momentum).
 * - Effects specs: For opacity and color transitions (critically damped, zero overshoot).
 *
 * Implements strict audit guardrail: dampingRatio must NEVER be 0f (infinite oscillation).
 */
object MotionTokens {

    // Spatial Spring Tokens (Position, Bounds, Shape Morphing)
    fun <T> fastSpatialSpec(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy, // 0.7f - slight natural overshoot
        stiffness = Spring.StiffnessMedium
    )

    fun <T> defaultSpatialSpec(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy, // 0.75f - moderate bounce
        stiffness = Spring.StiffnessLow
    )

    fun <T> slowSpatialSpec(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy, // 1.0f - smooth dramatic transition
        stiffness = Spring.StiffnessVeryLow
    )

    // Effects Spring Tokens (Alpha, Color, Elevation) - Critically damped, no overshoot
    fun <T> fastEffectsSpec(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy, // 1.0f - immediate clean fade
        stiffness = Spring.StiffnessHigh
    )

    fun <T> defaultEffectsSpec(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy, // 1.0f - smooth feedback
        stiffness = Spring.StiffnessMedium
    )

    fun <T> slowEffectsSpec(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy, // 1.0f - subtle ambient shift
        stiffness = Spring.StiffnessLow
    )
}

/**
 * CompositionLocal to respect accessibility reduced motion preferences.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * Resolves an AnimationSpec respecting user accessibility reduced motion settings.
 */
@Composable
fun <T> expressiveSpatialSpec(
    spec: AnimationSpec<T> = MotionTokens.defaultSpatialSpec()
): AnimationSpec<T> {
    return if (LocalReduceMotion.current) snap() else spec
}

@Composable
fun <T> expressiveEffectsSpec(
    spec: AnimationSpec<T> = MotionTokens.defaultEffectsSpec()
): AnimationSpec<T> {
    return if (LocalReduceMotion.current) snap() else spec
}

