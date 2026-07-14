package com.wearalarmsync.wear

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wearalarmsync.R
import com.wearalarmsync.common.WearSync

/**
 * Полноэкранное уведомление (full-screen intent): на Wear/OPLUS прямой запуск [AlarmActivity]
 * из [PendingIntent] будильника часто блокируется (BAL); FSI — типичный обход для категории alarm.
 */
object WearAlarmNotifier {
    private const val TAG = "WearAlarmNotifier"
    const val NOTIFICATION_ID: Int = 94031
    private const val CHANNEL_ID = "wear_alarm_ring"
    private const val PI_FULL_SCREEN = 94032
    private const val PI_CONTENT = 94033

    fun ensureChannel(context: Context) {
        val app = context.applicationContext
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            app.getString(R.string.alarm_title),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = app.getString(R.string.wear_alarm_channel_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    // WearRecents: NEW_TASK/CLEAR_TOP обязательны — activity запускается из full-screen intent
    // уведомления вне какого-либо task, без них лаунч на части Wear OEM не проходит.
    @SuppressLint("WearRecents")
    fun showFullScreenAlarm(context: Context, triggerMs: Long) {
        val app = context.applicationContext
        ensureChannel(app)
        val launch = Intent(app, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(WearSync.KEY_TRIGGER_MS, triggerMs)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // BAL в bundle при создании PI сбрасывает система (опция только для отправителя при [PendingIntent.send]);
        // запуск активности — в [AlarmReceiver.showAlarmUiOnMainThread].
        val fullScreenPi = PendingIntent.getActivity(app, PI_FULL_SCREEN, launch, flags)
        val contentPi = PendingIntent.getActivity(app, PI_CONTENT, launch, flags)

        val ringVibe = longArrayOf(0, 380, 180, 380, 180, 380, 500)
        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(app.getString(R.string.alarm_title))
            .setContentText(app.getString(R.string.wear_alarm_tap_to_open))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(ringVibe)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .setOngoing(true)
            .setDefaults(0)

        if (!hasNotificationPermission(app)) {
            // Без разрешения FSI-уведомление не появится (тихо проигнорируется системой),
            // но экран будильника всё равно запускается напрямую из AlarmReceiver — не блокируем звонок.
            Log.w(TAG, "POST_NOTIFICATIONS not granted, skipping notify() (alarm UI still launched directly)")
            return
        }
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "notify() denied", e)
        }
    }

    fun cancel(context: Context) {
        try {
            NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        } catch (e: SecurityException) {
            Log.e(TAG, "cancel() denied", e)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
