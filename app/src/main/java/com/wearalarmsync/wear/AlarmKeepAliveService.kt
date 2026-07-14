package com.wearalarmsync.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wearalarmsync.R
import java.text.DateFormat
import java.util.Date

/**
 * Foreground-сервис, удерживающий процесс приложения от force-stop, который OPLUS Wear OS делает после выключения экрана
 * (наблюдалось `ActivityManager: Force stopping com.wearalarmsync ... from pid 1331` через ~10 с после screen-off,
 * с одновременной отменой всех зарегистрированных alarm’ов через `removeAlarmsForPackage`).
 *
 * Сервис стартует из [AlarmScheduler.scheduleOrCancel] перед каждым `setAlarmClock` и останавливается, когда
 * актуального будильника нет (`triggerMs == NO_ALARM`).
 */
class AlarmKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // intent == null возможен при перезапуске системой после kill (START_STICKY) —
        // время сигнала неизвестно, но сервис обязан вызвать startForeground немедленно.
        val triggerMs = intent?.getLongExtra(EXTRA_TRIGGER_MS, -1L) ?: -1L
        Log.d(TAG, "onStartCommand triggerMs=$triggerMs")
        try {
            startForeground(NOTIFICATION_ID, buildNotification(triggerMs))
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed, stopping self", e)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }

    private fun buildNotification(triggerMs: Long): Notification {
        ensureChannel(this)
        val whenStr = if (triggerMs > 0L) {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(triggerMs))
        } else {
            "—"
        }
        val openIntent = Intent(this, WearMainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.wear_alarm_label))
            .setContentText(whenStr)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        private const val TAG = "AlarmKeepAlive"
        const val CHANNEL_ID = "wear_alarm_keepalive"
        const val NOTIFICATION_ID = 94055
        const val EXTRA_TRIGGER_MS = "trigger_ms"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val ch = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wear_alarm_label),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.wear_alarm_keepalive_channel_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            nm.createNotificationChannel(ch)
        }

        fun start(context: Context, triggerMs: Long) {
            val app = context.applicationContext
            val intent = Intent(app, AlarmKeepAliveService::class.java).apply {
                putExtra(EXTRA_TRIGGER_MS, triggerMs)
            }
            try {
                app.startForegroundService(intent)
                Log.d(TAG, "start triggerMs=$triggerMs")
            } catch (e: Exception) {
                Log.e(TAG, "start failed", e)
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            try {
                app.stopService(Intent(app, AlarmKeepAliveService::class.java))
                Log.d(TAG, "stop")
            } catch (e: Exception) {
                Log.w(TAG, "stop failed", e)
            }
        }
    }
}
