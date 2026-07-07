package com.wearalarmsync.common

/**
 * Настройки вибрации будильника на часах; задаются на телефоне и синхронизируются через Data Layer.
 */
data class VibrationSettings(
    /** 20–100 % от максимальной амплитуды. */
    val intensityPercent: Int,
    /** Длительность короткого импульса, мс (типично 300–900). */
    val pulseMs: Int,
    /** Пауза между импульсами, мс (типично 150–600). */
    val gapMs: Int,
) {
    fun clamped(): VibrationSettings = copy(
        intensityPercent = intensityPercent.coerceIn(MIN_INTENSITY, MAX_INTENSITY),
        pulseMs = pulseMs.coerceIn(MIN_PULSE_MS, MAX_PULSE_MS),
        gapMs = gapMs.coerceIn(MIN_GAP_MS, MAX_GAP_MS),
    )

    companion object {
        const val MIN_INTENSITY = 20
        const val MAX_INTENSITY = 100
        const val MIN_PULSE_MS = 300
        const val MAX_PULSE_MS = 900
        const val MIN_GAP_MS = 150
        const val MAX_GAP_MS = 600

        val DEFAULT = VibrationSettings(100, 650, 350)
    }
}
