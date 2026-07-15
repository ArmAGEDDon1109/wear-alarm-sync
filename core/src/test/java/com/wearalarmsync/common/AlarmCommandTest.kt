package com.wearalarmsync.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmCommandTest {

    @Test
    fun `dismiss wire value round-trips`() {
        assertEquals("DISMISS", AlarmCommand.Dismiss.wireValue)
        assertEquals(AlarmCommand.Dismiss, AlarmCommand.fromWire("DISMISS"))
    }

    @Test
    fun `snooze wire value round-trips`() {
        assertEquals("SNOOZE", AlarmCommand.Snooze.wireValue)
        assertEquals(AlarmCommand.Snooze, AlarmCommand.fromWire("SNOOZE"))
    }

    @Test
    fun `unknown wire value returns null`() {
        assertNull(AlarmCommand.fromWire("PING"))
        assertNull(AlarmCommand.fromWire(""))
    }
}
