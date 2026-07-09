package com.wearalarmsync.phone

import android.app.AlarmManager
import android.os.Build
import android.util.Log

/**
 * Пакет источника берётся из [AlarmManager.AlarmClockInfo.getShowIntent].
 * Android не отдаёт источник [getNextAlarmClock] напрямую.
 */
object AlarmSourceRecognizer {
    private const val TAG = "AlarmSourceRecognizer"

    fun creatorPackage(next: AlarmManager.AlarmClockInfo?): String? {
        if (next == null) return null
        val showIntent = next.showIntent
        if (showIntent == null) {
            Log.d(TAG, "No showIntent on nextAlarmClock")
            return null
        }
        val creator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            showIntent.creatorPackage
        } else {
            null
        }
        if (creator.isNullOrBlank()) {
            Log.d(TAG, "showIntent without creatorPackage")
        }
        return creator
    }

    fun isAllowedSource(next: AlarmManager.AlarmClockInfo?, allowedPackages: Set<String>): Boolean {
        if (allowedPackages.isEmpty()) return false
        val creator = creatorPackage(next) ?: return false
        val allowed = allowedPackages.contains(creator)
        if (!allowed) {
            Log.d(TAG, "nextAlarmClock creator=$creator not in allowed set")
        }
        return allowed
    }
}
