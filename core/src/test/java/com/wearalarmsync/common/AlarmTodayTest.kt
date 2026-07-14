package com.wearalarmsync.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTodayTest {

    private val zone = ZoneId.systemDefault()

    private fun atLocal(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `exact midnight is detected`() {
        val midnight = atLocal(2026, 7, 15, 0, 0, 0)
        assertTrue(AlarmToday.isExactMidnightLocal(midnight))
    }

    @Test
    fun `one second after midnight is not exact midnight`() {
        val justAfter = atLocal(2026, 7, 15, 0, 0, 1)
        assertFalse(AlarmToday.isExactMidnightLocal(justAfter))
    }

    @Test
    fun `one minute before midnight is not exact midnight`() {
        val justBefore = atLocal(2026, 7, 14, 23, 59, 0)
        assertFalse(AlarmToday.isExactMidnightLocal(justBefore))
    }

    @Test
    fun `upcoming alarm later today is detected`() {
        val now = atLocal(2026, 7, 14, 10, 0, 0)
        val later = atLocal(2026, 7, 14, 22, 30, 0)
        assertTrue(AlarmToday.hasUpcomingAlarmToday(later, now))
    }

    @Test
    fun `alarm tomorrow is not upcoming today`() {
        val now = atLocal(2026, 7, 14, 10, 0, 0)
        val tomorrow = atLocal(2026, 7, 15, 8, 0, 0)
        assertFalse(AlarmToday.hasUpcomingAlarmToday(tomorrow, now))
    }

    @Test
    fun `alarm in the past today is not upcoming`() {
        val now = atLocal(2026, 7, 14, 10, 0, 0)
        val earlierToday = atLocal(2026, 7, 14, 8, 0, 0)
        assertFalse(AlarmToday.hasUpcomingAlarmToday(earlierToday, now))
    }

    @Test
    fun `NO_ALARM is never upcoming`() {
        val now = atLocal(2026, 7, 14, 10, 0, 0)
        assertFalse(AlarmToday.hasUpcomingAlarmToday(WearSync.NO_ALARM, now))
    }
}
