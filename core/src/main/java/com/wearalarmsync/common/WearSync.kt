package com.wearalarmsync.common

import android.net.Uri

object WearSync {
    const val PATH_NEXT_ALARM = "/alarms/next"
    const val PATH_VIBRATION_SETTINGS = "/settings/vibration"

    /** Выборка всех реплик этого пути на всех узлах (см. DataClient.getDataItems). */
    fun nextAlarmDataLayerUri(): Uri = Uri.parse("wear://*$PATH_NEXT_ALARM")

    /** Путь элемента Data Layer — с учётом различий `Uri.getPath()` на прошивках. */
    fun isNextAlarmPath(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        if (path == PATH_NEXT_ALARM) return true
        val p = path.trimEnd('/')
        return p.endsWith("/alarms/next") || p == "alarms/next"
    }

    fun isVibrationSettingsPath(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val p = path.trimEnd('/')
        return p.endsWith("/settings/vibration") || p == "settings/vibration"
    }
    const val MESSAGE_PATH = "/alarm_command"
    const val KEY_TRIGGER_MS = "trigger_ms"
    /** До 3 будущих срабатываний (наблюдались как «следующий» alarm clock на телефоне). */
    const val KEY_TRIGGER_QUEUE = "trigger_queue_ms"
    const val KEY_SYNC_VERSION = "sync_version"
    const val KEY_VIB_INTENSITY_PCT = "vib_intensity_pct"
    const val KEY_VIB_PULSE_MS = "vib_pulse_ms"
    const val KEY_VIB_GAP_MS = "vib_gap_ms"
    const val NO_ALARM = -1L
}
