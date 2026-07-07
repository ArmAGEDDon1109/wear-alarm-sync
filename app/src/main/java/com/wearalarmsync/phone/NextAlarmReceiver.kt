package com.wearalarmsync.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wearalarmsync.Device

class NextAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Device.isWatch(context)) return
        AlarmSync.pushNextAlarm(context)
    }
}
