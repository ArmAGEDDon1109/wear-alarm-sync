package com.wearalarmsync.phone

import com.wearalarmsync.common.WearSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NextAlarmResolverTest {

    private val hourMs = 60L * 60 * 1000
    private val zone = ZoneId.systemDefault()

    private fun localMidnightMs(daysFromEpochToday: Long): Long =
        LocalDate.ofEpochDay(daysFromEpochToday)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `NO_ALARM stays NO_ALARM`() {
        val now = 1_000_000L
        assertEquals(WearSync.NO_ALARM, NextAlarmResolver.triggerForWatchSync(WearSync.NO_ALARM, now))
    }

    @Test
    fun `trigger in the past becomes NO_ALARM`() {
        val now = 1_000_000L
        assertEquals(WearSync.NO_ALARM, NextAlarmResolver.triggerForWatchSync(now - 1, now))
    }

    @Test
    fun `non-midnight future trigger passes through unchanged`() {
        val now = 1_000_000L
        val trigger = now + hourMs
        assertEquals(trigger, NextAlarmResolver.triggerForWatchSync(trigger, now))
    }

    @Test
    fun `midnight ghost within 1 to 48h window is suppressed`() {
        val todayEpochDay = LocalDate.now(zone).toEpochDay()
        val nextMidnight = localMidnightMs(todayEpochDay + 1)
        // now выбран так, чтобы до полуночи оставалось 10 часов (внутри окна 1..48ч)
        val now = nextMidnight - 10 * hourMs

        val result = NextAlarmResolver.triggerForWatchSync(nextMidnight, now)

        assertEquals(WearSync.NO_ALARM, result)
        assertTrue(NextAlarmResolver.wasFilteredAsMidnightGhost(nextMidnight, result, now))
    }

    @Test
    fun `midnight less than 1h away is not treated as ghost`() {
        val todayEpochDay = LocalDate.now(zone).toEpochDay()
        val nextMidnight = localMidnightMs(todayEpochDay + 1)
        // до полуночи осталось 30 минут — считаем это настоящим будильником "на сегодня в полночь", не фильтруем
        val now = nextMidnight - 30 * 60 * 1000L

        val result = NextAlarmResolver.triggerForWatchSync(nextMidnight, now)

        assertEquals(nextMidnight, result)
        assertFalse(NextAlarmResolver.wasFilteredAsMidnightGhost(nextMidnight, result, now))
    }

    @Test
    fun `midnight more than 48h away is not filtered`() {
        val todayEpochDay = LocalDate.now(zone).toEpochDay()
        val farMidnight = localMidnightMs(todayEpochDay + 5)
        val now = farMidnight - 72 * hourMs

        val result = NextAlarmResolver.triggerForWatchSync(farMidnight, now)

        assertEquals(farMidnight, result)
    }

    @Test
    fun `wasFilteredAsMidnightGhost is false when nothing was filtered`() {
        val now = 1_000_000L
        assertFalse(NextAlarmResolver.wasFilteredAsMidnightGhost(WearSync.NO_ALARM, WearSync.NO_ALARM, now))
        assertFalse(NextAlarmResolver.wasFilteredAsMidnightGhost(now + hourMs, now + hourMs, now))
    }

    @Test
    fun `wasFilteredAsMidnightGhost is false for past raw trigger`() {
        val now = 1_000_000L
        assertFalse(NextAlarmResolver.wasFilteredAsMidnightGhost(now - 1, WearSync.NO_ALARM, now))
    }
}
