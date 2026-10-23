package com.touristsafety.app.core.model

import androidx.compose.ui.graphics.Color

/**
 * Real provider availability and freshness status for each independent map layer.
 * Strictly separates 'user toggle enabled' from whether actual data was successfully retrieved,
 * cached, stale, empty, or failed.
 */
enum class LayerDataStatus(
    val label: String,
    val color: Color
) {
    LOADING("Loading...", Color(0xFFF57C00)),
    LOADED("Live", Color(0xFF2E7D32)),
    CACHED("Offline Cache", Color(0xFF1976D2)),
    STALE("Stale Data", Color(0xFFE65100)),
    EMPTY("None in Area", Color(0xFF757575)),
    FAILED("Provider Offline", Color(0xFFC62828)),
    DISABLED("Hidden", Color(0xFF9E9E9E))
}

