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

    // Legacy aliases
    const val LEGACY_ICON_HOSPITAL = "icon-hospital"
    const val LEGACY_ICON_CLINIC = "icon-clinic"
    const val LEGACY_ICON_POLICE = "icon-police"
    const val LEGACY_ICON_FUEL = "icon-fuel"
    const val LEGACY_ICON_FIRE_STATION = "icon-fire-station"
    const val LEGACY_ICON_SHELTER = "icon-shelter"

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
}

