package com.touristsafety.app.core.map

import android.util.Log
import kotlinx.coroutines.*
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import kotlin.math.sin

/**
 * High-performance, lifecycle-aware animation controller for MapLibre Native hazard symbols and polygons.
 *
 * Key Architectural Guarantees:
 * 1. Zero Garbage Collection Overhead: Cycles pre-rendered bitmap frames cached in [MapIconFactory].
 * 2. Restrained Timing: Runs at 180ms (~5.5 FPS) interval to deliver recognizable animations without strobing or battery drain.
 * 3. Accessibility / Reduced Motion: Falls back immediately to crisp static frame 0 when animations are disabled.
 * 4. Safe Lifecycle: Instantly cancels ticker coroutines on screen pause, destroy, or style reload.
 */
class HazardAnimationController {

    companion object {
        private const val TAG = "HazardAnimController"
        private const val FRAME_INTERVAL_MS = 180L
    }

    private var animationJob: Job? = null
    private var currentFrameIndex = 0

    /**
     * Starts the animated hazard icon loop on the loaded MapLibre style.
     */
    fun start(
        coroutineScope: CoroutineScope,
        style: Style?,
        isAnimationEnabled: Boolean = true
    ) {
        stop()

        if (style == null || !style.isFullyLoaded) {
            Log.d(TAG, "Cannot start animation: style not fully loaded yet.")
            return
        }

        if (!isAnimationEnabled) {
            applyStaticFrames(style)
            return
        }

        animationJob = coroutineScope.launch(Dispatchers.Main.immediate) {
            try {
                while (isActive) {
                    if (style.isFullyLoaded) {
                        currentFrameIndex++
                        updateStyleFrames(style, currentFrameIndex)
                    }
                    delay(FRAME_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Animation ticker cancelled safely.")
            } catch (t: Throwable) {
                Log.w(TAG, "Error in hazard animation frame: ${t.message}")
            }
        }
    }

    /**
     * Stops and cancels any active animation coroutines.
     */
    fun stop() {
        animationJob?.cancel()
        animationJob = null
    }

    /**
     * Updates registered symbol images in the MapLibre style with the next pre-rendered frame.
     */
    private fun updateStyleFrames(style: Style, frame: Int) {
        try {
            // 1. Thunderstorm (6 frames: cloud, raindrops, intermittent lightning flash on frames 3 & 4)
            val tFrame = MapIconFactory.thunderstormFrames[frame % MapIconFactory.thunderstormFrames.size]
            style.addImage(MapIconFactory.ICON_ANIM_THUNDERSTORM, tFrame)

            // 2. Heavy Rain (4 frames: cascading raindrops)
            val rFrame = MapIconFactory.heavyRainFrames[frame % MapIconFactory.heavyRainFrames.size]
            style.addImage(MapIconFactory.ICON_ANIM_HEAVY_RAIN, rFrame)

            // 3. Flooding (4 frames: expanding ripple rings and wave motion)
            val fFrame = MapIconFactory.floodingFrames[frame % MapIconFactory.floodingFrames.size]
            style.addImage(MapIconFactory.ICON_ANIM_FLOODING, fFrame)

            // 4. Landslide (4 frames: tumbling boulder and slope attention beacon)
            val lFrame = MapIconFactory.landslideFrames[frame % MapIconFactory.landslideFrames.size]
            style.addImage(MapIconFactory.ICON_ANIM_LANDSLIDE, lFrame)

            // 5. Wildfire (5 frames: licking, flickering flames)
            val wFrame = MapIconFactory.wildfireFrames[frame % MapIconFactory.wildfireFrames.size]
            style.addImage(MapIconFactory.ICON_ANIM_WILDFIRE, wFrame)

            // 6. Earthquake (4 frames: concentric shockwaves)
            val eFrame = MapIconFactory.earthquakeFrames[frame % MapIconFactory.earthquakeFrames.size]
            style.addImage(MapIconFactory.ICON_ANIM_EARTHQUAKE, eFrame)

            // 7. Route Obstruction / Closure (4 frames: alternating hazard lights)
            val cFrame = MapIconFactory.closureFrames[frame % MapIconFactory.closureFrames.size]
            style.addImage(MapIconFactory.ICON_ANIM_CLOSURE, cFrame)

            // Restrained polygon opacity transition for real hazard polygons
            val disasterFillLayer = style.getLayer(MapLibreLayerManager.LAYER_DISASTER_FILL) as? FillLayer
            if (disasterFillLayer != null) {
                val pulsedOpacity = 0.28f + (0.12f * sin(frame * 0.35f).toFloat())
                disasterFillLayer.setProperties(fillOpacity(pulsedOpacity))
            }
        } catch (t: Throwable) {
            // Guard against edge-case style detachment during screen transitions
            Log.d(TAG, "Frame update skipped: ${t.message}")
        }
    }

    /**
     * Resets all category icons to static frame 0 and polygon opacity to standard base.
     */
    fun applyStaticFrames(style: Style?) {
        if (style == null || !style.isFullyLoaded) return
        try {
            style.addImage(MapIconFactory.ICON_ANIM_THUNDERSTORM, MapIconFactory.thunderstormFrames[0])
            style.addImage(MapIconFactory.ICON_ANIM_HEAVY_RAIN, MapIconFactory.heavyRainFrames[0])
            style.addImage(MapIconFactory.ICON_ANIM_FLOODING, MapIconFactory.floodingFrames[0])
            style.addImage(MapIconFactory.ICON_ANIM_LANDSLIDE, MapIconFactory.landslideFrames[0])
            style.addImage(MapIconFactory.ICON_ANIM_WILDFIRE, MapIconFactory.wildfireFrames[0])
            style.addImage(MapIconFactory.ICON_ANIM_EARTHQUAKE, MapIconFactory.earthquakeFrames[0])
            style.addImage(MapIconFactory.ICON_ANIM_CLOSURE, MapIconFactory.closureFrames[0])

            val disasterFillLayer = style.getLayer(MapLibreLayerManager.LAYER_DISASTER_FILL) as? FillLayer
            disasterFillLayer?.setProperties(fillOpacity(0.35f))
        } catch (t: Throwable) {
            Log.d(TAG, "Failed applying static frames: ${t.message}")
        }
    }
}

