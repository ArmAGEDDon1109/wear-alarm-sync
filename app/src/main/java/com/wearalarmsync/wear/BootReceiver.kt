package com.wearalarmsync.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wearalarmsync.Device

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Device.isWatch(context)) return
        val app = context.applicationContext
        try {
            AlarmScheduler.rescheduleFromDataLayer(app)
        } catch (e: Exception) {
            Log.e(TAG, "reschedule after boot failed", e)
        }
    }

    companion object {
        private const val TAG = "WearBootReceiver"
    }
}
