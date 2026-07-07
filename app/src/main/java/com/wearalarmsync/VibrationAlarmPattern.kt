package com.wearalarmsync

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.wearalarmsync.common.VibrationSettings

/**
 * Паттерн «как звонок» из трёх пар импульс + финальный длинный импульс; общий для теста на телефоне и будильника на часах.
 */
object VibrationAlarmPattern {

    private const val TAG = "WearAlarmVib"
    /** Максимальная амплитуда для [VibrationEffect.createWaveform]; не путать с [VibrationEffect.DEFAULT_AMPLITUDE] (-1). */
    private const val WAVE_MAX_AMPLITUDE = 255

    fun defaultVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun scaleAmplitude(intensityPercent: Int): Int {
        val p = intensityPercent.coerceIn(VibrationSettings.MIN_INTENSITY, VibrationSettings.MAX_INTENSITY)
        return (WAVE_MAX_AMPLITUDE * p / 100f).toInt().coerceIn(1, WAVE_MAX_AMPLITUDE)
    }

    /**
     * Тайминги: 0, pulse, gap, pulse, gap, pulse, pulse+250, gap — как в исходном «звонке».
     *
     * @param repeatIndex индекс в [timings] для зацикливания, либо -1 один раз ([playTest]).
     */
    fun buildEffect(settings: VibrationSettings, repeatIndex: Int = 1): VibrationEffect {
        val s = settings.clamped()
        val p = s.pulseMs
        val g = s.gapMs
        val longPulse = p + 250
        val timings = longArrayOf(0, p.toLong(), g.toLong(), p.toLong(), g.toLong(), p.toLong(), longPulse.toLong(), g.toLong())
        val amp = scaleAmplitude(s.intensityPercent)
        val amps = intArrayOf(0, amp, 0, amp, 0, amp, amp, 0)
        return VibrationEffect.createWaveform(timings, amps, repeatIndex)
    }

    fun buildLegacyEffect(settings: VibrationSettings, repeatIndex: Int = 1): VibrationEffect {
        val s = settings.clamped()
        val p = s.pulseMs
        val g = s.gapMs
        val longPulse = p + 250
        val timings = longArrayOf(0, p.toLong(), g.toLong(), p.toLong(), g.toLong(), p.toLong(), longPulse.toLong(), g.toLong())
        @Suppress("DEPRECATION")
        return VibrationEffect.createWaveform(timings, repeatIndex)
    }

    /** Будильник и тест: [USAGE_ALARM] надёжнее, чем RINGTONE вне сценария звонка. */
    fun vibrateWithAlarmUsage(v: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            v.vibrate(effect, attrs)
        } else {
            v.vibrate(effect)
        }
    }

    fun playTest(context: Context, settings: VibrationSettings) {
        val v = defaultVibrator(context) ?: run {
            Log.w(TAG, "playTest: no vibrator")
            return
        }
        v.cancel()
        val once = -1
        if (v.hasAmplitudeControl()) {
            vibrateWithAlarmUsage(v, buildEffect(settings, once))
        } else {
            vibrateWithAlarmUsage(v, buildLegacyEffect(settings, once))
        }
        Log.d(TAG, "playTest ok amplitudeControl=${v.hasAmplitudeControl()} settings=$settings")
    }
}
