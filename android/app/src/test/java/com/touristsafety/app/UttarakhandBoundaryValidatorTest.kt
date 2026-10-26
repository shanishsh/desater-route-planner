package com.touristsafety.app

import android.content.Context
import android.content.res.AssetManager
import com.touristsafety.app.core.spatial.UttarakhandBoundaryValidator
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class UttarakhandBoundaryValidatorTest {

    private lateinit var validator: UttarakhandBoundaryValidator
    private val mockContext = mockk<Context>()
    private val mockAssets = mockk<AssetManager>()

    @Before
    fun setup() {
        // Resolve real boundary GeoJSON from project assets or backend resources
        val candidatePaths = listOf(
            "src/main/assets/uttarakhand_boundary.geojson",
            "android/app/src/main/assets/uttarakhand_boundary.geojson",
            "backend/app/resources/uttarakhand_boundary.geojson",
            "../../backend/app/resources/uttarakhand_boundary.geojson"
        )
        val file = candidatePaths.map { File(it) }.firstOrNull { it.exists() }
            ?: throw IllegalStateException("uttarakhand_boundary.geojson not found in test search paths")

        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("uttarakhand_boundary.geojson") } answers { FileInputStream(file) }

        validator = UttarakhandBoundaryValidator(mockContext)
    }

    @Test
    fun testLocationsInsideUttarakhandAreAccepted() {
        // Dehradun
        assertTrue(validator.isPointInUttarakhand(30.3165, 78.0322))
        // Gaurikund
        assertTrue(validator.isPointInUttarakhand(30.6534, 79.0268))
        // Kedarnath Temple
        assertTrue(validator.isPointInUttarakhand(30.7339, 79.0669))
        // Rishikesh
        assertTrue(validator.isPointInUttarakhand(30.0869, 78.2676))
        // Mussoorie
        assertTrue(validator.isPointInUttarakhand(30.4598, 78.0700))
        // Joshimath
        assertTrue(validator.isPointInUttarakhand(30.5564, 79.5662))
    }

    @Test
    fun testLocationsOutsideUttarakhandAreRejected() {
        // New Delhi
        assertFalse(validator.isPointInUttarakhand(28.6139, 77.2090))
        // Chandigarh
        assertFalse(validator.isPointInUttarakhand(30.7333, 76.7794))
        // Shimla, Himachal Pradesh
        assertFalse(validator.isPointInUttarakhand(31.1048, 77.1734))
        // Mumbai
        assertFalse(validator.isPointInUttarakhand(19.0760, 72.8777))
        // Bengaluru
        assertFalse(validator.isPointInUttarakhand(12.9716, 77.5946))
    }

    @Test
    fun testRouteWithinUttarakhandPasses() {
        val rishikeshToGaurikund = listOf(
            30.0869 to 78.2676,
            30.3000 to 78.6000,
            30.6534 to 79.0268
        )
        val (isValid, reason) = validator.isRouteInUttarakhand(rishikeshToGaurikund)
        assertTrue(isValid)
        assertTrue(reason == null)
    }

    @Test
    fun testRouteOriginatingOutsideUttarakhandFails() {
        val delhiToKedarnath = listOf(
            28.6139 to 77.2090, // Delhi
            30.6534 to 79.0268,
            30.7339 to 79.0669
        )
        val (isValid, reason) = validator.isRouteInUttarakhand(delhiToKedarnath)
        assertFalse(isValid)
        assertTrue(reason?.contains("outside Uttarakhand") == true)
    }

    @Test
    fun testInterDistrictDrivingRouteTransitingPlainsPasses() {
        // Dehradun to Nainital route transiting plains highway (e.g. Najibabad/Kashipur)
        val dehradunToNainital = listOf(
            30.3165 to 78.0322, // Dehradun (UK)
            29.6100 to 78.3400, // Najibabad area (UP plains transit corridor)
            29.2100 to 78.9600, // Kashipur area
            29.3919 to 79.4542  // Nainital (UK)
        )
        val (isValid, reason) = validator.isRouteInUttarakhand(dehradunToNainital, travelMode = "driving")
        assertTrue(isValid)
        assertTrue(reason == null)
    }

    @Test
    fun testInterDistrictHikingRouteTransitingPlainsFails() {
        val dehradunToNainital = listOf(
            30.3165 to 78.0322,
            29.6100 to 78.3400,
            29.2100 to 78.9600,
            29.3919 to 79.4542
        )
        val (isValid, reason) = validator.isRouteInUttarakhand(dehradunToNainital, travelMode = "hiking")
        assertFalse(isValid)
        assertTrue(reason?.contains("exits the supported Uttarakhand administrative boundary") == true)
    }
}

