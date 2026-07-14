package com.wearalarmsync.common

import org.junit.Assert.assertEquals
import org.junit.Test

class VibrationSettingsTest {

    @Test
    fun `values within range are unchanged`() {
        val settings = VibrationSettings(intensityPercent = 60, pulseMs = 500, gapMs = 300)
        assertEquals(settings, settings.clamped())
    }

    @Test
    fun `intensity below minimum is clamped up`() {
        val settings = VibrationSettings(intensityPercent = 0, pulseMs = 500, gapMs = 300)
        assertEquals(VibrationSettings.MIN_INTENSITY, settings.clamped().intensityPercent)
    }

    @Test
    fun `intensity above maximum is clamped down`() {
        val settings = VibrationSettings(intensityPercent = 500, pulseMs = 500, gapMs = 300)
        assertEquals(VibrationSettings.MAX_INTENSITY, settings.clamped().intensityPercent)
    }

    @Test
    fun `pulse duration is clamped into range`() {
        val tooShort = VibrationSettings(intensityPercent = 60, pulseMs = 10, gapMs = 300)
        val tooLong = VibrationSettings(intensityPercent = 60, pulseMs = 5000, gapMs = 300)
        assertEquals(VibrationSettings.MIN_PULSE_MS, tooShort.clamped().pulseMs)
        assertEquals(VibrationSettings.MAX_PULSE_MS, tooLong.clamped().pulseMs)
    }

    @Test
    fun `gap duration is clamped into range`() {
        val tooShort = VibrationSettings(intensityPercent = 60, pulseMs = 500, gapMs = 0)
        val tooLong = VibrationSettings(intensityPercent = 60, pulseMs = 500, gapMs = 5000)
        assertEquals(VibrationSettings.MIN_GAP_MS, tooShort.clamped().gapMs)
        assertEquals(VibrationSettings.MAX_GAP_MS, tooLong.clamped().gapMs)
    }

    @Test
    fun `default settings are already within valid range`() {
        assertEquals(VibrationSettings.DEFAULT, VibrationSettings.DEFAULT.clamped())
    }
}
