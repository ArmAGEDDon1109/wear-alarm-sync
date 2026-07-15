package com.wearalarmsync.common

/**
 * Type-safe команда Dismiss/Snooze, которой телефон и часы обмениваются через
 * [WearSync.MESSAGE_PATH] (см. `PhoneCommand` и `AlarmCommandListenerService`).
 *
 * [wireValue] — это байты, которые реально уходят в `MessageClient.sendMessage`. Значения
 * "DISMISS"/"SNOOZE" зафиксированы и менять их нельзя: телефон и часы могут быть на разных
 * версиях приложения, и оба конца канала должны понимать один и тот же провод-формат.
 */
sealed class AlarmCommand(val wireValue: String) {
    object Dismiss : AlarmCommand("DISMISS")
    object Snooze : AlarmCommand("SNOOZE")

    companion object {
        /** Разбор входящих байт сообщения обратно в команду; `null` — неизвестная/повреждённая команда. */
        fun fromWire(value: String): AlarmCommand? = when (value) {
            Dismiss.wireValue -> Dismiss
            Snooze.wireValue -> Snooze
            else -> null
        }
    }
}
