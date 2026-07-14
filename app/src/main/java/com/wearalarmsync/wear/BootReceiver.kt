package com.wearalarmsync.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wearalarmsync.Device
import java.util.concurrent.Executors

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Device.isWatch(context)) return
        val app = context.applicationContext

        // rescheduleFromDataLayer() блокирует поток (Tasks.await на Wearable API) —
        // нельзя выполнять на main thread onReceive, иначе риск ANR сразу после загрузки.
        val pending = goAsync()
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            try {
                AlarmScheduler.rescheduleFromDataLayer(app)
            } catch (e: Exception) {
                Log.e(TAG, "reschedule after boot failed", e)
            } finally {
                pending.finish()
                executor.shutdown()
            }
        }
    }

    companion object {
        private const val TAG = "WearBootReceiver"
    }
}
