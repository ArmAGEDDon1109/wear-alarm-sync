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

    /**
     * @return true, если будильник разрешён к синхронизации на часы.
     * [creatorPackage] недоступен на API < 31 и когда `showIntent`/`creatorPackage` отсутствуют —
     * в этом случае фильтр по источнику технически неприменим, и мы **пропускаем** будильник
     * (fail-open), а не блокируем его: иначе на Android 8–11 будильник никогда бы не синхронизировался.
     */
    fun isAllowedSource(next: AlarmManager.AlarmClockInfo?, allowedPackages: Set<String>): Boolean {
        if (next == null) return false
        return isAllowedForCreator(creatorPackage(next), allowedPackages)
    }

    /**
     * Чистое ядро решения без Android framework типов (тестируется юнит-тестами без Robolectric/mock).
     * @param creatorPackage результат [creatorPackage], либо `null`, если источник неопределим.
     */
    fun isAllowedForCreator(creatorPackage: String?, allowedPackages: Set<String>): Boolean {
        if (allowedPackages.isEmpty()) return false
        if (creatorPackage == null) {
            Log.d(TAG, "Cannot determine alarm source package (API<31 or no showIntent); allowing sync")
            return true
        }
        val allowed = allowedPackages.contains(creatorPackage)
        if (!allowed) {
            Log.d(TAG, "nextAlarmClock creator=$creatorPackage not in allowed set")
        }
        return allowed
    }
}
