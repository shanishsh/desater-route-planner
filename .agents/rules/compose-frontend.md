---
trigger: glob
description: "Enforces Material 3 Expressive theming, physics-based MotionScheme springs, adaptive Navigation 3 layouts, and zero-recomposition performance rules for all Kotlin Jetpack Compose files."
globs: "*.kt, *.kts"
---
# Android Jetpack Compose & Material 3 Expressive Frontend Standards

## 1. Compose & Material 3 Expressive First
- All UI must use Jetpack Compose and MaterialExpressiveTheme semantics.
- Never use `androidx.compose.material.icons`; use semantic vector drawables or clean Material Symbols.
- Use `expressiveLightColorScheme()` and `expressiveDarkColorScheme()` as fallbacks when dynamic color (API 31+) is unavailable. Always pair color roles strictly (`primaryContainer` with `onPrimaryContainer`, `surfaceContainer` roles).
- Use `*Emphasized` typography tokens (`displayLargeEmphasized`, `headlineMediumEmphasized`, `titleLargeEmphasized`, `labelLargeEmphasized`) only for hero headlines, selected states, and focal callouts—never for body copy everywhere.

## 2. Physics-Based Motion & Phase Deferral
- Never use duration-based `tween()` for spatial movement, size, or shape morphing. Always use physics-based spring tokens (`fastSpatialSpec()`, `defaultSpatialSpec()`, `slowSpatialSpec()`).
- Use `fastEffectsSpec()` or `defaultEffectsSpec()` for color and opacity transitions.
- Never set spring `dampingRatio = 0f` (minimum practical bouncy value is `Spring.DampingRatioLowBouncy / 0.2f`).
- Always defer animated state reads to the Draw/Layout phases via `Modifier.graphicsLayer { ... }` (including `clip = true; shape = animatedShape` for animated shape clipping) and `Modifier.offset { IntOffset(...) }`.

## 3. Edge-to-Edge, Insets & Adaptive Navigation
- Activities must call `enableEdgeToEdge()` before `setContent`.
- Set `android:windowSoftInputMode="adjustResize"` in `AndroidManifest.xml` and do not lock configuration changes.
- Use adaptive navigation scaffolding (bottom navigation bar on compact phones, navigation rail on foldables/tablets). Never apply `safeDrawingPadding()` to parent containers; pass insets directly to child list `contentPadding`.

## 4. State Hoisting, Strong Skipping & Lazy Lists
- Split screens into `<Feature>Screen` (stateful route, injects ViewModel, collects flows via `collectAsStateWithLifecycle()`) and `<Feature>Content` (stateless, takes immutable UiState and event lambdas, annotated with `@Preview`).
- Strong Skipping Mode is active: NEVER annotate classes containing List, Set, or Map with `@Immutable` or `@Stable`, and never use `ImmutableList`.
- Every `LazyColumn`, `LazyRow`, and `LazyVerticalGrid` item block MUST define both a primitive `key` and a `contentType`. For small fixed lists (1–10 items), use a standard `Column` or `Row` instead of a lazy container.

