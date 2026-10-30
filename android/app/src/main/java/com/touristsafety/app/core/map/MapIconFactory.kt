package com.touristsafety.app.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.touristsafety.app.R
import org.maplibre.android.maps.Style

/**
 * Creates and registers crisp, high-contrast, scalable vector icon bitmaps
 * for MapLibre Native SymbolLayers.
 *
 * Guarantees that essential facilities (hospitals, clinics, police, petrol pumps,
 * fire stations, shelters, active fires, disaster advisories) are rendered with
 * distinct, recognizable glyph symbols rather than plain dots.
 */
object MapIconFactory {

    // Canonical POI Icon IDs
    const val ICON_HOSPITAL = "poi-hospital"
    const val ICON_CLINIC = "poi-clinic"
    const val ICON_POLICE = "poi-police"
    const val ICON_FUEL = "poi-fuel"
    const val ICON_FIRE_STATION = "poi-fire-station"
    const val ICON_SHELTER = "poi-shelter"
    const val ICON_WILDFIRE = "icon-wildfire"
    const val ICON_DISASTER = "icon-disaster"
    const val ICON_CELL_TOWER = "poi-cell-tower"
    const val ICON_COMMUNITY_HAZARD = "poi-community-hazard"

    // Animated Hazard Icon Keys
    const val ICON_ANIM_THUNDERSTORM = "icon-anim-thunderstorm"
    const val ICON_ANIM_HEAVY_RAIN = "icon-anim-heavy-rain"
    const val ICON_ANIM_FLOODING = "icon-anim-flooding"
    const val ICON_ANIM_LANDSLIDE = "icon-anim-landslide"
    const val ICON_ANIM_WILDFIRE = "icon-anim-wildfire"
    const val ICON_ANIM_EARTHQUAKE = "icon-anim-earthquake"
    const val ICON_ANIM_CLOSURE = "icon-anim-closure"

    // Legacy aliases
    const val LEGACY_ICON_HOSPITAL = "icon-hospital"
    const val LEGACY_ICON_CLINIC = "icon-clinic"
    const val LEGACY_ICON_POLICE = "icon-police"
    const val LEGACY_ICON_FUEL = "icon-fuel"
    const val LEGACY_ICON_FIRE_STATION = "icon-fire-station"
    const val LEGACY_ICON_SHELTER = "icon-shelter"
    const val LEGACY_ICON_CELL_TOWER = "icon-cell-tower"

    private const val ICON_SIZE = 64 // 64x64 px standard resolution for crisp DPI rendering

    /**
     * Converts an Android Drawable into a hardware-independent ARGB_8888 Bitmap.
     */
    fun drawableToBitmap(drawable: Drawable, width: Int = ICON_SIZE, height: Int = ICON_SIZE): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, width, height, true)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun loadOrGenerateBitmap(context: Context?, resId: Int, fallback: () -> Bitmap): Bitmap {
        if (context != null) {
            try {
                val drawable = ContextCompat.getDrawable(context, resId)
                if (drawable != null) {
                    return drawableToBitmap(drawable)
                }
            } catch (t: Throwable) {
                android.util.Log.w("MapIconFactory", "Failed to inflate drawable resource $resId: ${t.message}. Using fallback.")
            }
        }
        return fallback()
    }

    /**
     * Registers all required facility and hazard marker images into the MapLibre Style.
     * Safe to invoke on every style load or style reload; skips images already registered.
     */
    fun registerMarkerImages(style: Style, context: Context? = null) {
        if (!style.isFullyLoaded) return

        // Hospital (both canonical poi-* and legacy icon-*)
        val hospitalBitmap = loadOrGenerateBitmap(context, R.drawable.ic_poi_hospital) { createHospitalBitmap() }
        if (style.getImage(ICON_HOSPITAL) == null) {
            style.addImage(ICON_HOSPITAL, hospitalBitmap)
        }
        if (style.getImage(LEGACY_ICON_HOSPITAL) == null) {
            style.addImage(LEGACY_ICON_HOSPITAL, hospitalBitmap)
        }

        // Clinic
        val clinicBitmap = loadOrGenerateBitmap(context, R.drawable.ic_poi_clinic) { createClinicBitmap() }
        if (style.getImage(ICON_CLINIC) == null) {
            style.addImage(ICON_CLINIC, clinicBitmap)
        }
        if (style.getImage(LEGACY_ICON_CLINIC) == null) {
            style.addImage(LEGACY_ICON_CLINIC, clinicBitmap)
        }

        // Police
        val policeBitmap = loadOrGenerateBitmap(context, R.drawable.ic_poi_police) { createPoliceBitmap() }
        if (style.getImage(ICON_POLICE) == null) {
            style.addImage(ICON_POLICE, policeBitmap)
        }
        if (style.getImage(LEGACY_ICON_POLICE) == null) {
            style.addImage(LEGACY_ICON_POLICE, policeBitmap)
        }

        // Fuel / Petrol Pump
        val fuelBitmap = loadOrGenerateBitmap(context, R.drawable.ic_poi_fuel) { createFuelBitmap() }
        if (style.getImage(ICON_FUEL) == null) {
            style.addImage(ICON_FUEL, fuelBitmap)
        }
        if (style.getImage(LEGACY_ICON_FUEL) == null) {
            style.addImage(LEGACY_ICON_FUEL, fuelBitmap)
        }

        // Fire Station
        val fireStationBitmap = loadOrGenerateBitmap(context, R.drawable.ic_poi_fire_station) { createFireStationBitmap() }
        if (style.getImage(ICON_FIRE_STATION) == null) {
            style.addImage(ICON_FIRE_STATION, fireStationBitmap)
        }
        if (style.getImage(LEGACY_ICON_FIRE_STATION) == null) {
            style.addImage(LEGACY_ICON_FIRE_STATION, fireStationBitmap)
        }

        // Shelter
        val shelterBitmap = loadOrGenerateBitmap(context, R.drawable.ic_poi_shelter) { createShelterBitmap() }
        if (style.getImage(ICON_SHELTER) == null) {
            style.addImage(ICON_SHELTER, shelterBitmap)
        }
        if (style.getImage(LEGACY_ICON_SHELTER) == null) {
            style.addImage(LEGACY_ICON_SHELTER, shelterBitmap)
        }

        // Wildfire & Disaster Advisories
        if (style.getImage(ICON_WILDFIRE) == null) {
            style.addImage(ICON_WILDFIRE, createWildfireBitmap())
        }
        if (style.getImage(ICON_DISASTER) == null) {
            style.addImage(ICON_DISASTER, createDisasterBitmap())
        }

        // Cell Tower
        val cellTowerBitmap = loadOrGenerateBitmap(context, R.drawable.ic_poi_cell_tower) { createCellTowerBitmap() }
        if (style.getImage(ICON_CELL_TOWER) == null) {
            style.addImage(ICON_CELL_TOWER, cellTowerBitmap)
        }
        if (style.getImage(LEGACY_ICON_CELL_TOWER) == null) {
            style.addImage(LEGACY_ICON_CELL_TOWER, cellTowerBitmap)
        }
        if (style.getImage(ICON_COMMUNITY_HAZARD) == null) {
            style.addImage(ICON_COMMUNITY_HAZARD, createCommunityHazardBitmap())
        }

        // Register Animated Category Icons (Initial Static Frame 0)
        if (style.getImage(ICON_ANIM_THUNDERSTORM) == null) style.addImage(ICON_ANIM_THUNDERSTORM, thunderstormFrames[0])
        if (style.getImage(ICON_ANIM_HEAVY_RAIN) == null) style.addImage(ICON_ANIM_HEAVY_RAIN, heavyRainFrames[0])
        if (style.getImage(ICON_ANIM_FLOODING) == null) style.addImage(ICON_ANIM_FLOODING, floodingFrames[0])
        if (style.getImage(ICON_ANIM_LANDSLIDE) == null) style.addImage(ICON_ANIM_LANDSLIDE, landslideFrames[0])
        if (style.getImage(ICON_ANIM_WILDFIRE) == null) style.addImage(ICON_ANIM_WILDFIRE, wildfireFrames[0])
        if (style.getImage(ICON_ANIM_EARTHQUAKE) == null) style.addImage(ICON_ANIM_EARTHQUAKE, earthquakeFrames[0])
        if (style.getImage(ICON_ANIM_CLOSURE) == null) style.addImage(ICON_ANIM_CLOSURE, closureFrames[0])
    }

    /**
     * Verifies whether a specific icon ID is successfully registered in the MapLibre Style.
     */
    fun isIconRegistered(style: Style, iconId: String): Boolean {
        return style.isFullyLoaded && style.getImage(iconId) != null
    }

    /**
     * Checks registration status of all essential facility icon identifiers.
     */
    fun verifyAllIconsRegistered(style: Style): Map<String, Boolean> {
        val required = listOf(
            ICON_HOSPITAL,
            ICON_CLINIC,
            ICON_POLICE,
            ICON_FUEL,
            ICON_FIRE_STATION,
            ICON_SHELTER,
            ICON_WILDFIRE,
            ICON_DISASTER
        )
        return required.associateWith { isIconRegistered(style, it) }
    }

    /**
     * Community Trail Hazard Icon: Amber circular badge with white exclamation mark.
     */
    fun createCommunityHazardBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 158, 11) // Amber #F59E0B
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, strokePaint)

        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        // Exclamation mark stem
        canvas.drawRoundRect(
            RectF(center - 3.5f, center - 16f, center + 3.5f, center + 4f),
            2f, 2f, fgPaint
        )
        // Exclamation mark dot
        canvas.drawCircle(center, center + 12f, 4f, fgPaint)

        return bitmap
    }

    /**
     * Hospital Icon: Red circular badge with prominent white medical cross.
     */
    fun createHospitalBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        // Outer circular background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(229, 57, 53) // Medical Red #E53935
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        // White border
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, strokePaint)

        // White medical cross (+)
        val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val barThickness = 7f
        val barLength = 26f
        val halfThick = barThickness / 2f
        val halfLen = barLength / 2f

        // Vertical bar
        canvas.drawRoundRect(
            RectF(center - halfThick, center - halfLen, center + halfThick, center + halfLen),
            2f, 2f, crossPaint
        )
        // Horizontal bar
        canvas.drawRoundRect(
            RectF(center - halfLen, center - halfThick, center + halfLen, center + halfThick),
            2f, 2f, crossPaint
        )

        return bitmap
    }

    /**
     * Clinic Icon: Teal circular badge with white healthcare cross and circular halo.
     */
    fun createClinicBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 137, 123) // Clinic Teal #00897B
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, strokePaint)

        // White cross with central circle
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val barThickness = 6f
        val barLength = 22f
        val halfThick = barThickness / 2f
        val halfLen = barLength / 2f

        canvas.drawRoundRect(
            RectF(center - halfThick, center - halfLen, center + halfThick, center + halfLen),
            2f, 2f, fgPaint
        )
        canvas.drawRoundRect(
            RectF(center - halfLen, center - halfThick, center + halfLen, center + halfThick),
            2f, 2f, fgPaint
        )
        canvas.drawCircle(center, center, 4.5f, fgPaint)

        return bitmap
    }

    /**
     * Police Station Icon: Blue circular badge with white police security shield glyph.
     */
    fun createPoliceBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 136, 229) // Police Blue #1E88E5
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, strokePaint)

        // White shield path
        val shieldPath = Path().apply {
            moveTo(center, center - 14f)
            lineTo(center + 12f, center - 8f)
            lineTo(center + 12f, center + 4f)
            quadTo(center + 10f, center + 14f, center, center + 17f)
            quadTo(center - 10f, center + 14f, center - 12f, center + 4f)
            lineTo(center - 12f, center - 8f)
            close()
        }
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(shieldPath, fgPaint)

        // Inner star on shield
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 136, 229)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center + 1f, 4f, innerPaint)

        return bitmap
    }

    /**
     * Petrol Pump / Fuel Station Icon: Amber-Orange circular badge with white fuel dispenser and nozzle glyph.
     */
    fun createFuelBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 124, 0) // Fuel Amber-Orange #F57C00
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, strokePaint)

        // White fuel pump dispenser body
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        // Pump main column
        canvas.drawRoundRect(
            RectF(center - 11f, center - 12f, center + 3f, center + 14f),
            3f, 3f, fgPaint
        )
        // Top cap
        canvas.drawRoundRect(
            RectF(center - 9f, center - 15f, center + 1f, center - 12f),
            1f, 1f, fgPaint
        )

        // Pump window cutout
        val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 124, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRect(
            RectF(center - 8f, center - 9f, center, center - 3f),
            windowPaint
        )

        // Hose & nozzle path on right
        val hosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.8f
            strokeCap = Paint.Cap.ROUND
        }
        val hosePath = Path().apply {
            moveTo(center + 3f, center - 4f)
            lineTo(center + 9f, center - 4f)
            lineTo(center + 11f, center + 2f)
            lineTo(center + 11f, center + 9f)
            lineTo(center + 8f, center + 7f)
        }
        canvas.drawPath(hosePath, hosePaint)

        return bitmap
    }

    /**
     * Fire Station Icon: Deep Orange-Red badge with white rescue flame / helmet glyph.
     */
    fun createFireStationBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 81, 0) // Fire Station Deep Orange #E65100
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, strokePaint)

        // Stylized flame / helmet glyph
        val flamePath = Path().apply {
            moveTo(center, center - 15f)
            cubicTo(center + 11f, center - 4f, center + 13f, center + 4f, center + 10f, center + 11f)
            cubicTo(center + 7f, center + 16f, center - 7f, center + 16f, center - 10f, center + 11f)
            cubicTo(center - 13f, center + 4f, center - 9f, center - 4f, center, center - 15f)
            close()
        }
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(flamePath, fgPaint)

        // Inner core flame in orange
        val innerFlame = Path().apply {
            moveTo(center, center - 2f)
            cubicTo(center + 5f, center + 3f, center + 5f, center + 8f, center + 3f, center + 11f)
            cubicTo(center + 1f, center + 13f, center - 1f, center + 13f, center - 3f, center + 11f)
            cubicTo(center - 5f, center + 8f, center - 3f, center + 3f, center, center - 2f)
            close()
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 81, 0)
            style = Paint.Style.FILL
        }
        canvas.drawPath(innerFlame, innerPaint)

        return bitmap
    }

    /**
     * Alpine Shelter / Cabin Icon: Forest green circular badge with white cabin roof and door glyph.
     */
    fun createShelterBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(46, 125, 50) // Shelter Forest Green #2E7D32
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, strokePaint)

        // White cabin shape
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        // Roof triangle
        val roofPath = Path().apply {
            moveTo(center, center - 14f)
            lineTo(center + 14f, center - 1f)
            lineTo(center - 14f, center - 1f)
            close()
        }
        canvas.drawPath(roofPath, fgPaint)

        // Cabin wall rect
        canvas.drawRect(
            RectF(center - 10f, center - 1f, center + 10f, center + 13f),
            fgPaint
        )

        // Door cutout
        val doorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(46, 125, 50)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(center - 3.5f, center + 4f, center + 3.5f, center + 13f),
            1.5f, 1.5f, doorPaint
        )

        return bitmap
    }

    /**
     * Active Wildfire Icon: Red circle with bright yellow stroke and dual flame glyph.
     */
    fun createWildfireBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(211, 47, 47) // #D32F2F Fire Red
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 235, 59) // Bright Yellow #FFEB3B
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, strokePaint)

        // Yellow flame glyph
        val flamePath = Path().apply {
            moveTo(center, center - 14f)
            cubicTo(center + 10f, center - 4f, center + 12f, center + 5f, center + 9f, center + 11f)
            cubicTo(center + 6f, center + 15f, center - 6f, center + 15f, center - 9f, center + 11f)
            cubicTo(center - 12f, center + 5f, center - 7f, center - 4f, center, center - 14f)
            close()
        }
        val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 235, 59)
            style = Paint.Style.FILL
        }
        canvas.drawPath(flamePath, flamePaint)

        return bitmap
    }

    /**
     * Disaster Advisory Icon: Dark emergency red circle with white warning triangle and exclamation.
     */
    fun createDisasterBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(183, 28, 28) // Deep Alert Red #B71C1C
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, strokePaint)

        // White triangle
        val triPath = Path().apply {
            moveTo(center, center - 14f)
            lineTo(center + 13f, center + 10f)
            lineTo(center - 13f, center + 10f)
            close()
        }
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(triPath, fgPaint)

        // Exclamation mark inside triangle
        val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(183, 28, 28)
            style = Paint.Style.FILL
        }
        canvas.drawRect(
            RectF(center - 1.5f, center - 5f, center + 1.5f, center + 3f),
            markPaint
        )
        canvas.drawCircle(center, center + 6.5f, 1.8f, markPaint)

        return bitmap
    }

    /**
     * Cell Tower Icon: Indigo circular badge with white antenna lattice mast and radiating radio waves.
     */
    fun createCellTowerBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        // Indigo background #651FFF
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(101, 31, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        // White border
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, strokePaint)

        // White antenna mast path
        val mastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val mastPath = Path().apply {
            // Legs
            moveTo(center - 10f, 48f)
            lineTo(center, 22f)
            lineTo(center + 10f, 48f)
            // Crossbars
            moveTo(center - 5f, 31f)
            lineTo(center + 5f, 31f)
            moveTo(center - 8.5f, 40f)
            lineTo(center + 8.5f, 40f)
            // Spire
            moveTo(center, 22f)
            lineTo(center, 15f)
        }
        canvas.drawPath(mastPath, mastPaint)

        // Radiating radio arcs
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
            strokeCap = Paint.Cap.ROUND
        }
        val innerRect = RectF(center - 9f, 9f, center + 9f, 27f)
        canvas.drawArc(innerRect, 215f, 50f, false, wavePaint)
        canvas.drawArc(innerRect, 275f, 50f, false, wavePaint)

        val outerRect = RectF(center - 15f, 5f, center + 15f, 35f)
        canvas.drawArc(outerRect, 210f, 55f, false, wavePaint)
        canvas.drawArc(outerRect, 275f, 55f, false, wavePaint)

        // Beacon dot at tip
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, 15f, 2.0f, dotPaint)

        return bitmap
    }

    // --- Pre-rendered Frame Arrays (Zero Allocation During Animation Ticker) ---
    val thunderstormFrames: Array<Bitmap> by lazy { Array(6) { createThunderstormFrame(it) } }
    val heavyRainFrames: Array<Bitmap> by lazy { Array(4) { createHeavyRainFrame(it) } }
    val floodingFrames: Array<Bitmap> by lazy { Array(4) { createFloodingFrame(it) } }
    val landslideFrames: Array<Bitmap> by lazy { Array(4) { createLandslideFrame(it) } }
    val wildfireFrames: Array<Bitmap> by lazy { Array(5) { createWildfireFrame(it) } }
    val earthquakeFrames: Array<Bitmap> by lazy { Array(4) { createEarthquakeFrame(it) } }
    val closureFrames: Array<Bitmap> by lazy { Array(4) { createClosureFrame(it) } }

    /**
     * Thunderstorm Icon Frame: Dark storm cloud with rain and intermittent lightning flash on frames 3 & 4.
     */
    fun createThunderstormFrame(frame: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        // Dark storm background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59) // Slate-900 #1E293B
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        // Amber warning border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(245, 158, 11) // Amber-500 #F59E0B
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, borderPaint)

        // Cloud shape in upper half
        val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184) // Slate-400
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center - 8f, center - 6f, 8f, cloudPaint)
        canvas.drawCircle(center + 6f, center - 8f, 10f, cloudPaint)
        canvas.drawRoundRect(RectF(center - 14f, center - 5f, center + 14f, center + 4f), 5f, 5f, cloudPaint)

        // Raindrops
        val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 189, 248) // Sky blue
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
        }
        val rainShift = (frame * 2.5f) % 7f
        canvas.drawLine(center - 8f, center + 7f + rainShift, center - 10f, center + 12f + rainShift, rainPaint)
        canvas.drawLine(center, center + 7f + ((rainShift + 3f) % 7f), center - 2f, center + 12f + ((rainShift + 3f) % 7f), rainPaint)
        canvas.drawLine(center + 8f, center + 7f + ((rainShift + 5f) % 7f), center + 6f, center + 12f + ((rainShift + 5f) % 7f), rainPaint)

        // Intermittent lightning flash (Frames 3 & 4)
        if (frame == 3 || frame == 4) {
            val boltPath = Path().apply {
                moveTo(center + 2f, center - 4f)
                lineTo(center - 4f, center + 5f)
                lineTo(center + 1f, center + 5f)
                lineTo(center - 3f, center + 16f)
                lineTo(center + 6f, center + 4f)
                lineTo(center + 1f, center + 4f)
                close()
            }
            val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(250, 204, 21) // Vivid Yellow #FACC15
                style = Paint.Style.FILL
            }
            canvas.drawPath(boltPath, boltPaint)

            // Inner white lightning core
            val boltCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawPath(boltPath, boltCorePaint)
        }

        return bitmap
    }

    /**
     * Heavy Rain Frame: Rain cloud with animated cascading raindrops.
     */
    fun createHeavyRainFrame(frame: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(2, 132, 199) // Sky-600 #0284C7
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, borderPaint)

        // Cloud
        val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center - 8f, center - 6f, 8f, cloudPaint)
        canvas.drawCircle(center + 6f, center - 8f, 10f, cloudPaint)
        canvas.drawRoundRect(RectF(center - 14f, center - 5f, center + 14f, center + 4f), 5f, 5f, cloudPaint)

        // 3 cascading raindrop streaks
        val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(224, 242, 254) // Light Blue
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeCap = Paint.Cap.ROUND
        }
        val yShift = (frame * 3f) % 9f
        canvas.drawLine(center - 9f, center + 6f + yShift, center - 12f, center + 12f + yShift, dropPaint)
        canvas.drawLine(center - 1f, center + 6f + ((yShift + 3f) % 9f), center - 4f, center + 12f + ((yShift + 3f) % 9f), dropPaint)
        canvas.drawLine(center + 7f, center + 6f + ((yShift + 6f) % 9f), center + 4f, center + 12f + ((yShift + 6f) % 9f), dropPaint)

        return bitmap
    }

    /**
     * Flooding Frame: Water waves and ripple expanding outward.
     */
    fun createFloodingFrame(frame: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(13, 148, 136) // Teal-600 #0D9488
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, borderPaint)

        // Expanding ripple rings
        val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val rippleR1 = 6f + (frame * 3f)
        val rippleR2 = 12f + (frame * 2.5f)
        ripplePaint.alpha = (255 - frame * 40).coerceIn(80, 255)
        canvas.drawCircle(center, center - 2f, rippleR1, ripplePaint)

        // Water wave base
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val wavePath = Path().apply {
            moveTo(center - 15f, center + 7f)
            quadTo(center - 7f, center + 3f + (frame % 2) * 2f, center, center + 7f)
            quadTo(center + 7f, center + 11f - (frame % 2) * 2f, center + 15f, center + 7f)
            lineTo(center + 15f, center + 14f)
            lineTo(center - 15f, center + 14f)
            close()
        }
        canvas.drawPath(wavePath, wavePaint)

        return bitmap
    }

    /**
     * Landslide Frame: Mountain slope with tumbling rock and attention beacon.
     */
    fun createLandslideFrame(frame: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 83, 9) // Amber-700 #B45309
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, borderPaint)

        // Mountain slope silhouette
        val slopePath = Path().apply {
            moveTo(center - 15f, center + 13f)
            lineTo(center - 7f, center - 8f)
            lineTo(center + 14f, center + 13f)
            close()
        }
        val slopePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(69, 26, 3) // Deep Earth
            style = Paint.Style.FILL
        }
        canvas.drawPath(slopePath, slopePaint)

        // Tumbling rock along slope
        val rockX = center - 2f + (frame * 3.5f)
        val rockY = center + (frame * 2.8f)
        val rockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(254, 240, 138) // Light stone yellow
            style = Paint.Style.FILL
        }
        canvas.drawCircle(rockX, rockY, 3.5f, rockPaint)

        // Warning triangle at top peak
        val beaconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (frame % 2 == 0) Color.YELLOW else Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center - 7f, center - 11f, 3f, beaconPaint)

        return bitmap
    }

    /**
     * Wildfire Frame: Flickering flame licking upward with oscillating height and color.
     */
    fun createWildfireFrame(frame: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(185, 28, 28) // Deep Alert Red #B91C1C
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(253, 224, 71) // Yellow border #FDE047
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, borderPaint)

        // Outer Flame (Flickering height)
        val tipY = center - 14f - ((frame % 3) * 2f)
        val flamePath = Path().apply {
            moveTo(center, tipY)
            cubicTo(center + 10f, center - 4f, center + 12f, center + 6f, center + 9f, center + 12f)
            cubicTo(center + 6f, center + 16f, center - 6f, center + 16f, center - 9f, center + 12f)
            cubicTo(center - 12f, center + 6f, center - 8f, center - 4f, center, tipY)
            close()
        }
        val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(249, 115, 22) // Orange-500 #F97316
            style = Paint.Style.FILL
        }
        canvas.drawPath(flamePath, flamePaint)

        // Inner Bright Core Flame
        val innerTipY = tipY + 6f
        val innerPath = Path().apply {
            moveTo(center, innerTipY)
            cubicTo(center + 5f, center + 2f, center + 6f, center + 9f, center + 4f, center + 13f)
            cubicTo(center + 2f, center + 15f, center - 2f, center + 15f, center - 4f, center + 13f)
            cubicTo(center - 6f, center + 9f, center - 4f, center + 2f, center, innerTipY)
            close()
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(254, 240, 138) // Light Yellow #FEF08A
            style = Paint.Style.FILL
        }
        canvas.drawPath(innerPath, innerPaint)

        return bitmap
    }

    /**
     * Earthquake Frame: Seismic epicenter with radiating shockwave rings.
     */
    fun createEarthquakeFrame(frame: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(124, 58, 237) // Purple-600 #7C3AED
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, borderPaint)

        // Concentric shockwave circles
        val shockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(233, 213, 255) // Light purple
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        val r1 = 5f + (frame * 3.5f)
        shockPaint.alpha = (255 - frame * 45).coerceIn(60, 255)
        canvas.drawCircle(center, center, r1, shockPaint)

        val r2 = 12f + (frame * 2.5f)
        shockPaint.alpha = (220 - frame * 45).coerceIn(40, 220)
        canvas.drawCircle(center, center, r2, shockPaint)

        // Epicenter core dot
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 4.5f, corePaint)

        return bitmap
    }

    /**
     * Trail Closure / Obstruction Frame: Construction barrier with alternating warning lights.
     */
    fun createClosureFrame(frame: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = ICON_SIZE / 2f
        val radius = center - 3f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 29, 72) // Crimson-600 #E11D48
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, radius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        canvas.drawCircle(center, center, radius, borderPaint)

        // Barrier bar (Yellow with black hazard stripes)
        val barRect = RectF(center - 13f, center - 4f, center + 13f, center + 6f)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(250, 204, 21) // Yellow #FACC15
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(barRect, 2f, 2f, barPaint)

        // Black stripes
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawRect(RectF(center - 9f, center - 4f, center - 5f, center + 6f), stripePaint)
        canvas.drawRect(RectF(center - 1f, center - 4f, center + 3f, center + 6f), stripePaint)
        canvas.drawRect(RectF(center + 7f, center - 4f, center + 11f, center + 6f), stripePaint)

        // Alternating flashing beacons on barrier legs
        val leftLightOn = frame % 2 == 0
        val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        lightPaint.color = if (leftLightOn) Color.YELLOW else Color.rgb(156, 163, 175)
        canvas.drawCircle(center - 9f, center - 8f, 3.2f, lightPaint)

        lightPaint.color = if (!leftLightOn) Color.YELLOW else Color.rgb(156, 163, 175)
        canvas.drawCircle(center + 9f, center - 8f, 3.2f, lightPaint)

        return bitmap
    }
}
