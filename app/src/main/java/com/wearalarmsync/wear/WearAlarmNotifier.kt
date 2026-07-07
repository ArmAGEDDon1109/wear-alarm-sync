package com.wearalarmsync.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wearalarmsync.R
import com.wearalarmsync.common.WearSync

/**
 * Полноэкранное уведомление (full-screen intent): на Wear/OPLUS прямой запуск [AlarmActivity]
 * из [PendingIntent] будильника часто блокируется (BAL); FSI — типичный обход для категории alarm.
 */
object WearAlarmNotifier {
    const val NOTIFICATION_ID: Int = 94031
    private const val CHANNEL_ID = "wear_alarm_ring"
    private const val PI_FULL_SCREEN = 94032
    private const val PI_CONTENT = 94033

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val app = context.applicationContext
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            app.getString(R.string.alarm_title),
            NotificationManager.IMPORTANCE_MAX,
        ).apply {
            description = app.getString(R.string.wear_alarm_channel_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

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

        NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }
}
