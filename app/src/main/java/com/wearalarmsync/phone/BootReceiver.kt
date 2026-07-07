package com.wearalarmsync.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wearalarmsync.Device

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Device.isWatch(context)) return
        AlarmSync.pushNextAlarm(context)
    }
}
