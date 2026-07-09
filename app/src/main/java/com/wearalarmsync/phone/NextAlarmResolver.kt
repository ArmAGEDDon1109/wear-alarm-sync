package com.wearalarmsync.phone

import android.util.Log
import com.wearalarmsync.common.AlarmToday
import com.wearalarmsync.common.WearSync

/**
 * [android.app.AlarmManager.getNextAlarmClock] возвращает один «следующий» сигнал по всей системе.
 * Полуночь (календарь и т.п.) всегда отфильтровывается; опция «только Часы» — в [AlarmSync].
 */
object NextAlarmResolver {
    private const val TAG = "NextAlarmResolver"
    private const val ONE_HOUR_MS = 60L * 60 * 1000
    private const val FORTY_EIGHT_H_MS = 48L * 60 * 60 * 1000

    fun triggerForWatchSync(rawTriggerMs: Long, nowMs: Long): Long {
        if (rawTriggerMs == WearSync.NO_ALARM || rawTriggerMs <= nowMs) {
            return WearSync.NO_ALARM
        }
        if (!AlarmToday.isExactMidnightLocal(rawTriggerMs)) {
            return rawTriggerMs
        }
        val delta = rawTriggerMs - nowMs
        if (delta in ONE_HOUR_MS until FORTY_EIGHT_H_MS) {
            Log.w(
                TAG,
                "Skip syncing start-of-day nextAlarmClock at $rawTriggerMs (delta=${delta}ms)",
            )
            return WearSync.NO_ALARM
        }
        return rawTriggerMs
    }

    fun wasFilteredAsMidnightGhost(rawTriggerMs: Long, syncedTriggerMs: Long, nowMs: Long): Boolean {
        if (rawTriggerMs == WearSync.NO_ALARM || rawTriggerMs <= nowMs) return false
        if (syncedTriggerMs != WearSync.NO_ALARM) return false
        return AlarmToday.isExactMidnightLocal(rawTriggerMs) &&
            (rawTriggerMs - nowMs) in ONE_HOUR_MS until FORTY_EIGHT_H_MS
    }
}
