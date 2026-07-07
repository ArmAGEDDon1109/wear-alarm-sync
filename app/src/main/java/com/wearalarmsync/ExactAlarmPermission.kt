package com.wearalarmsync

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Доступ «Точные будильники» (API 31+) — особый доступ, не runtime-диалог.
 * [Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM] открывает системный экран выдачи.
 */
object ExactAlarmPermission {
    private const val TAG = "ExactAlarmPermission"

    fun isBlocked(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val am = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return !am.canScheduleExactAlarms()
    }

    /** Если доступ ещё не выдан — открывает системный запрос. */
    fun requestIfBlocked(activity: Activity) {
        if (!isBlocked(activity)) return
        openScheduleExactAlarmScreen(activity)
    }

    /** Открыть экран запроса (для кнопки в UI и как fallback). */
    fun openScheduleExactAlarmScreen(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            activity.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${activity.packageName}")
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_REQUEST_SCHEDULE_EXACT_ALARM failed", e)
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    },
                )
            } catch (e2: Exception) {
                Log.e(TAG, "APPLICATION_DETAILS_SETTINGS failed", e2)
            }
        }
    }
}
