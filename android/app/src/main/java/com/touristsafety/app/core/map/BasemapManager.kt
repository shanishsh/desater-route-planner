package com.touristsafety.app.core.map

import android.content.Context
import android.util.Log
import com.touristsafety.app.feature.map.MapUiState
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Coordinates MapLibre basemap style switches while safely preserving:
 * - Camera position, zoom, tilt, and bearing
 * - Active trek routes, drawing drafts, and live GPS tracks
 * - All vector overlays, markers, and polygon features
 * - Thread-safe tokenized cancellation of obsolete async style-loading callbacks
 */
class BasemapManager(
    private val onStyleLoadStarted: (BasemapMode) -> Unit = {},
    private val onStyleLoadSuccess: (BasemapMode, Style) -> Unit = { _, _ -> },
    private val onStyleLoadFailed: (BasemapMode, String?) -> Unit = { _, _ -> }
) {
    companion object {
        private const val TAG = "BasemapManager"
    }

    private var activeLoadingToken: Long = 0L
    var currentMode: BasemapMode = BasemapMode.STREETS_TRAILS
        private set

    /**
     * Transitions the map to the selected BasemapMode while preserving all user and route state.
     */
    fun switchBasemap(
        map: MapLibreMap,
        targetMode: BasemapMode,
        state: MapUiState,
        context: Context? = null
    ) {
        val token = ++activeLoadingToken
        val previousMode = currentMode
        currentMode = targetMode

        Log.i(TAG, "Initiating style switch from ${previousMode.name} to ${targetMode.name} (Token: $token)")
        onStyleLoadStarted(targetMode)

        // 1. Snapshot current camera state to guarantee zero position jumping
        val savedCamera: CameraPosition = map.cameraPosition

        // 2. Build the appropriate style
        val styleBuilder = MapConfig.createStyleBuilder(targetMode)

        try {
            map.setStyle(styleBuilder) { style ->
                if (token != activeLoadingToken) {
                    Log.w(TAG, "Discarded stale style callback for ${targetMode.name} (Token $token != $activeLoadingToken)")
                    return@setStyle
                }

                try {
                    Log.i(TAG, "Basemap style loaded successfully: ${targetMode.name}")

                    // 3. Re-register and re-populate all application layers with satellite awareness
                    MapLibreLayerManager.resetDirtyCache()
                    MapLibreLayerManager.setupSourcesAndLayers(style, context, isSatellite = targetMode.isSatellite)
                    MapLibreLayerManager.updateData(style, state, context, isSatellite = targetMode.isSatellite)

                    // 4. Restore exact camera position
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(savedCamera))

                    onStyleLoadSuccess(targetMode, style)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error configuring layers after style load: ${t.message}", t)
                    onStyleLoadFailed(targetMode, t.message)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initiate style change for ${targetMode.name}: ${t.message}", t)
            onStyleLoadFailed(targetMode, t.message)
        }
    }
}
