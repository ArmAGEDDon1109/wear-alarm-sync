package com.wearalarmsync

import android.content.Context
import com.wearalarmsync.common.VibrationSettings
import com.wearalarmsync.common.WearSync

object VibrationPrefs {
    private const val PREFS = "vibration_alarm"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): VibrationSettings {
        val p = prefs(context)
        return VibrationSettings(
            intensityPercent = p.getInt(KEY_INTENSITY, VibrationSettings.DEFAULT.intensityPercent),
            pulseMs = p.getInt(KEY_PULSE, VibrationSettings.DEFAULT.pulseMs),
            gapMs = p.getInt(KEY_GAP, VibrationSettings.DEFAULT.gapMs),
        ).clamped()
    }

    fun write(context: Context, settings: VibrationSettings) {
        val s = settings.clamped()
        prefs(context).edit()
            .putInt(KEY_INTENSITY, s.intensityPercent)
            .putInt(KEY_PULSE, s.pulseMs)
            .putInt(KEY_GAP, s.gapMs)
            .apply()
    }

    /** Пишет значения из Data Map (слой данных с телефона). */
    fun applyFromDataMap(context: Context, map: com.google.android.gms.wearable.DataMap) {
        val s = VibrationSettings(
            intensityPercent = map.getInt(WearSync.KEY_VIB_INTENSITY_PCT, VibrationSettings.DEFAULT.intensityPercent),
            pulseMs = map.getInt(WearSync.KEY_VIB_PULSE_MS, VibrationSettings.DEFAULT.pulseMs),
            gapMs = map.getInt(WearSync.KEY_VIB_GAP_MS, VibrationSettings.DEFAULT.gapMs),
        ).clamped()
        write(context, s)
    }

    private const val KEY_INTENSITY = "intensity_pct"
    private const val KEY_PULSE = "pulse_ms"
    private const val KEY_GAP = "gap_ms"
}
