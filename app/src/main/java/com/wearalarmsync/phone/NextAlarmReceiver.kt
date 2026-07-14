package com.wearalarmsync.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wearalarmsync.Device

class NextAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // NEXT_ALARM_CLOCK_CHANGED — protected broadcast (только система шлёт implicit intent),
        // но экспортированный receiver всё равно может получить explicit intent с любым action —
        // проверяем явно, чтобы левый action/пустой action не вызывал ресинк.
        if (intent?.action != android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) return
        if (Device.isWatch(context)) return
        AlarmSync.pushNextAlarm(context)
    }
}
