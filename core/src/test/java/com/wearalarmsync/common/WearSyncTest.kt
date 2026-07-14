package com.wearalarmsync.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSyncTest {

    @Test
    fun `canonical next-alarm path matches`() {
        assertTrue(WearSync.isNextAlarmPath("/alarms/next"))
    }

    @Test
    fun `next-alarm path with node prefix matches`() {
        assertTrue(WearSync.isNextAlarmPath("/some/node/prefix/alarms/next"))
    }

    @Test
    fun `next-alarm path without leading slash matches`() {
        assertTrue(WearSync.isNextAlarmPath("alarms/next"))
    }

    @Test
    fun `next-alarm path with trailing slash matches`() {
        assertTrue(WearSync.isNextAlarmPath("/alarms/next/"))
    }

    @Test
    fun `unrelated path does not match next-alarm`() {
        assertFalse(WearSync.isNextAlarmPath("/settings/vibration"))
    }

    @Test
    fun `null or empty path does not match next-alarm`() {
        assertFalse(WearSync.isNextAlarmPath(null))
        assertFalse(WearSync.isNextAlarmPath(""))
    }

    @Test
    fun `canonical vibration settings path matches`() {
        assertTrue(WearSync.isVibrationSettingsPath("/settings/vibration"))
    }

    @Test
    fun `vibration settings path with node prefix matches`() {
        assertTrue(WearSync.isVibrationSettingsPath("/some/node/prefix/settings/vibration"))
    }

    @Test
    fun `unrelated path does not match vibration settings`() {
        assertFalse(WearSync.isVibrationSettingsPath("/alarms/next"))
    }

    @Test
    fun `null or empty path does not match vibration settings`() {
        assertFalse(WearSync.isVibrationSettingsPath(null))
        assertFalse(WearSync.isVibrationSettingsPath(""))
    }
}
