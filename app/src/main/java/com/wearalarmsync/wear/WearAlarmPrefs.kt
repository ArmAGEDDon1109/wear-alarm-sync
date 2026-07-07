package com.wearalarmsync.wear

import android.content.Context

/** Локальные настройки будильника на часах (не синхронизируются с телефоном). */
object WearAlarmPrefs {
    private const val PREFS = "wear_alarm"
    private const val KEY_SUPPRESS_WHEN_OFF_BODY = "suppress_when_off_body"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Если true — при срабатывании не показывать UI/вибрацию, пока часы сняты (off-body).
     * По умолчанию false: снятые часы всё равно будят, как раньше.
     */
    fun suppressWhenOffBody(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUPPRESS_WHEN_OFF_BODY, false)

    fun setSuppressWhenOffBody(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SUPPRESS_WHEN_OFF_BODY, value).apply()
    }
}
