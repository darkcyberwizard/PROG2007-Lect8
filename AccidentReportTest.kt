package com.example.lect8testdebug

import android.location.Location
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Local (host-side) unit tests for isReportValid() — no emulator needed,
 * but real Location/Uri behavior, thanks to Robolectric. Plain JUnit on
 * the stub android.jar can't safely construct a working Location or Uri
 * (every unmocked framework call/field comes back null/0/false) —
 * Robolectric swaps in real, working shadow implementations instead.
 * @Config pins Robolectric to SDK 34, which it definitely supports —
 * independent of the app's real compileSdk 37.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccidentReportTest {

    private val validLocation = Location("test").apply {
        latitude = 63.4
        longitude = 10.4
    }
    private val validUri: Uri = Uri.parse("content://fake/photo.jpg")

    // --- Positive ---------------------------------------------------------

    @Test
    fun `report with location and photo is valid`() {
        // Arrange
        val report = AccidentReport(
            location = Location("test").apply {
                latitude = 63.4; longitude = 10.4
            },
            photoUri = Uri.parse("content://fake/photo.jpg")
        )
        // Act
        val result = isReportValid(report)
        // Assert
        assertTrue(result)
    }

    // --- Negative -----------------------------------------------------------

    @Test
    fun `report with no location is invalid`() {
        val report = AccidentReport(location = null, photoUri = validUri)
        assertFalse(isReportValid(report))
    }

    @Test
    fun `report with no photo is invalid`() {
        val report = AccidentReport(location = validLocation, photoUri = null)
        assertFalse(isReportValid(report))
    }

    @Test
    fun `report with neither location nor photo is invalid`() {
        val report = AccidentReport(location = null, photoUri = null)
        assertFalse(isReportValid(report))
    }

    // --- Boundary -----------------------------------------------------------

    @Test
    fun `report with zero-zero coordinates is still valid`() {
        val zeroLocation = Location("test").apply {
            latitude = 0.0; longitude = 0.0
        }
        val report = AccidentReport(location = zeroLocation, photoUri = validUri)
        assertTrue(isReportValid(report))
    }

    @Test
    fun `report with empty description is still valid`() {
        val report = AccidentReport(
            location = validLocation,
            photoUri = validUri,
            description = ""
        )
        assertTrue(isReportValid(report))
    }
}
