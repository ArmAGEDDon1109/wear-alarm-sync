package com.wearalarmsync.phone

import android.content.Context
import android.util.Log
import com.wearalarmsync.common.AlarmToday
import com.wearalarmsync.common.WearSync

/**
 * Накапливает до [MAX] наблюдаемых будущих времён при смене системного «следующего» будильника.
 * На часах по [WearSync.KEY_TRIGGER_QUEUE] выбирается ближайшее кольцо, если API уже перескочило на второй сигнал.
 */
object AlarmQueueTracker {
    private const val TAG = "AlarmQueueTracker"
    private const val PREFS = "alarm_queue_tracker"
    private const val KEY_LAST_PRIMARY = "last_primary_ms"
    private const val KEY_SLOTS = "slots_csv"
    private const val MAX = 3

    fun observePrimary(context: Context, primaryMs: Long): LongArray {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastPrimary = prefs.getLong(KEY_LAST_PRIMARY, WearSync.NO_ALARM)

        val slots = prefs.getString(KEY_SLOTS, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.filter { it != WearSync.NO_ALARM && it > now && !AlarmToday.isExactMidnightLocal(it) }
            ?.toMutableSet()
            ?: mutableSetOf()

        when {
            primaryMs != WearSync.NO_ALARM &&
                primaryMs > now &&
                !AlarmToday.isExactMidnightLocal(primaryMs) -> {
                if (lastPrimary != WearSync.NO_ALARM &&
                    lastPrimary != primaryMs &&
                    lastPrimary > now &&
                    !AlarmToday.isExactMidnightLocal(lastPrimary)
                ) {
                    slots.add(lastPrimary)
                }
                prefs.edit().putLong(KEY_LAST_PRIMARY, primaryMs).apply()
            }
            primaryMs == WearSync.NO_ALARM || primaryMs <= now -> {
                prefs.edit().putLong(KEY_LAST_PRIMARY, WearSync.NO_ALARM).apply()
            }
        }

        if (primaryMs != WearSync.NO_ALARM &&
            primaryMs > now &&
            !AlarmToday.isExactMidnightLocal(primaryMs)
        ) {
            slots.add(primaryMs)
        }

        val sorted = slots.sorted().take(MAX).toLongArray()
        prefs.edit().putString(KEY_SLOTS, sorted.joinToString(",")).apply()
        Log.d(TAG, "observePrimary primary=$primaryMs -> queue=${sorted.contentToString()}")
        return sorted
    }
}
