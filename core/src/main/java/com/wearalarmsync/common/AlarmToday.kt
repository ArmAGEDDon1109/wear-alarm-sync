package com.wearalarmsync.common

import java.time.Instant
import java.time.ZoneId

object AlarmToday {
    /** Ровно 00:00:00.000 начала локальных суток (часто не будильник «Часов», а другой сигнал). */
    fun isExactMidnightLocal(triggerMs: Long): Boolean {
        val zone = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(triggerMs).atZone(zone)
        return t.hour == 0 && t.minute == 0 && t.second == 0 && t.nano == 0
    }

    /** Следующий сигнал ещё впереди и приходится на **сегодня** по локальному календарю. */
    fun hasUpcomingAlarmToday(triggerMs: Long, nowMs: Long): Boolean {
        if (triggerMs == WearSync.NO_ALARM || triggerMs <= nowMs) return false
        val zone = ZoneId.systemDefault()
        val triggerDay = Instant.ofEpochMilli(triggerMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return triggerDay == today
    }
}
