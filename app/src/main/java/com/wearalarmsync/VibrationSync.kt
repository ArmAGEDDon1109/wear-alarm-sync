package com.wearalarmsync

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.wearalarmsync.common.VibrationSettings
import com.wearalarmsync.common.WearSync

object VibrationSync {
    private const val TAG = "VibrationSync"

    /** Отправляет настройки вибрации на часы (Data Layer). */
    fun push(context: Context): Task<DataItem> {
        val app = context.applicationContext
        val gms = GoogleApiAvailability.getInstance()
        if (gms.isGooglePlayServicesAvailable(app) != ConnectionResult.SUCCESS) {
            return Tasks.forException(
                IllegalStateException("Google Play services unavailable"),
            )
        }
        val s = VibrationPrefs.read(app)
        val putReq = PutDataMapRequest.create(WearSync.PATH_VIBRATION_SETTINGS).run {
            dataMap.putInt(WearSync.KEY_VIB_INTENSITY_PCT, s.intensityPercent)
            dataMap.putInt(WearSync.KEY_VIB_PULSE_MS, s.pulseMs)
            dataMap.putInt(WearSync.KEY_VIB_GAP_MS, s.gapMs)
            dataMap.putLong(WearSync.KEY_SYNC_VERSION, System.currentTimeMillis())
            asPutDataRequest().setUrgent()
        }
        val task = Wearable.getDataClient(app).putDataItem(putReq)
        task.addOnSuccessListener {
            Log.d(TAG, "Synced vibration: $s")
        }
        task.addOnFailureListener { e -> Log.e(TAG, "putDataItem failed", e) }
        return task
    }

    fun pushWithSettings(context: Context, settings: VibrationSettings): Task<DataItem> {
        VibrationPrefs.write(context, settings)
        return push(context)
    }
}
