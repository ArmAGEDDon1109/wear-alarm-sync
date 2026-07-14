package com.wearalarmsync.phone

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.wearalarmsync.Device
import com.wearalarmsync.common.WearSync

class AlarmCommandListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "AlarmCommandListener"
        private const val REQUEST_DISMISS = 71001
        private const val REQUEST_SNOOZE = 71002
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (Device.isWatch(this)) {
            Log.d(TAG, "onMessageReceived: Not running on phone, ignoring")
            return
        }
        if (messageEvent.path != WearSync.MESSAGE_PATH) {
            Log.d(TAG, "onMessageReceived: Unknown path ${messageEvent.path}, ignoring")
            return
        }

        val cmd = try {
            String(messageEvent.data, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "onMessageReceived: Failed to decode command data", e)
            return
        }

        Log.d(TAG, "onMessageReceived: Command from watch: $cmd")
        mainHandler.post {
            try {
                when (cmd) {
                    WearSync.CMD_DISMISS -> {
                        Log.d(TAG, "onMessageReceived: Processing DISMISS command")
                        dispatchAlarmClockAction(AlarmClock.ACTION_DISMISS_ALARM, REQUEST_DISMISS)
                        schedulePushNextAlarmToWatch()
                    }
                    WearSync.CMD_SNOOZE -> {
                        Log.d(TAG, "onMessageReceived: Processing SNOOZE command")
                        dispatchAlarmClockAction(AlarmClock.ACTION_SNOOZE_ALARM, REQUEST_SNOOZE)
                        schedulePushNextAlarmToWatch()
                    }
                    else -> {
                        Log.w(TAG, "onMessageReceived: Unknown command: $cmd")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onMessageReceived: Exception processing command $cmd", e)
            }
        }
    }

    /**
     * NEXT_ALARM_CLOCK_CHANGED на части прошивок не приходит сразу после dismiss/snooze.
     * Без повторной записи в Data Layer часы остаются со старым времени и перестают планировать сигнал.
     */
    private fun schedulePushNextAlarmToWatch() {
        val app = applicationContext
        Log.d(TAG, "schedulePushNextAlarmToWatch: Scheduling next alarm push")
        mainHandler.postDelayed({ AlarmSync.pushNextAlarm(app) }, 600L)
        mainHandler.postDelayed({ AlarmSync.pushNextAlarm(app) }, 2200L)
    }

    private fun dispatchAlarmClockAction(action: String, requestCode: Int) {
        val intent = buildAlarmClockIntent(action)
        if (intent.resolveActivity(packageManager) == null) {
            Log.e(TAG, "dispatchAlarmClockAction: No handler for $action")
            return
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, requestCode, intent, flags)

        var success = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }
                pendingIntent.send(options.toBundle())
                success = true
            } else {
                pendingIntent.send()
                success = true
            }
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "dispatchAlarmClockAction: PendingIntent canceled for $action", e)
        } catch (e: Exception) {
            Log.w(TAG, "dispatchAlarmClockAction: PendingIntent.send failed for $action, trying startActivity", e)
            try {
                startActivity(intent)
                success = true
            } catch (e2: Exception) {
                Log.e(TAG, "dispatchAlarmClockAction: startActivity failed for $action", e2)
            }
        }

        if (success) {
            Log.d(TAG, "dispatchAlarmClockAction: Successfully dispatched $action")
        } else {
            Log.e(TAG, "dispatchAlarmClockAction: Failed to dispatch $action")
        }
    }

    // WearRecents: этот intent запускает системные Часы на ТЕЛЕФОНЕ (см. Device.isWatch выше) —
    // рекомендация lint про Wear recents здесь неприменима, но проверка срабатывает на весь universal APK.
    @android.annotation.SuppressLint("WearRecents")
    private fun buildAlarmClockIntent(action: String) = Intent(action).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_NEXT)
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    }
}
