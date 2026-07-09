package com.wearalarmsync.phone

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.wearalarmsync.AlarmSyncPrefs
import com.wearalarmsync.common.WearSync

object AlarmSync {
    private const val TAG = "AlarmSync"

    fun pushNextAlarm(context: Context): Task<DataItem> {
        val app = context.applicationContext
        val gms = GoogleApiAvailability.getInstance()
        val result = gms.isGooglePlayServicesAvailable(app)
        if (result != ConnectionResult.SUCCESS) {
            Log.w(TAG, "Google Play services unavailable: $result")
            return Tasks.forException(
                IllegalStateException("Google Play services unavailable (code $result)"),
            )
        }

        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = am.nextAlarmClock
        AlarmSourceApps.recordObservedPackage(app, AlarmSourceRecognizer.creatorPackage(next))
        val now = System.currentTimeMillis()
        val allowed = AlarmSyncPrefs.allowedPackages(app)

        val rawFromSystem = next?.triggerTime ?: WearSync.NO_ALARM
        val rawTrigger = when {
            rawFromSystem == WearSync.NO_ALARM || rawFromSystem <= now -> WearSync.NO_ALARM
            !AlarmSourceRecognizer.isAllowedSource(next, allowed) -> {
                val pkg = AlarmSourceRecognizer.creatorPackage(next) ?: "?"
                Log.w(TAG, "Skipping nextAlarmClock from $pkg (not in allowed sources)")
                WearSync.NO_ALARM
            }
            else -> rawFromSystem
        }

        val triggerMs = NextAlarmResolver.triggerForWatchSync(rawTrigger, now)
        val queueMs = AlarmQueueTracker.observePrimary(app, triggerMs)

        val putReq = PutDataMapRequest.create(WearSync.PATH_NEXT_ALARM).run {
            dataMap.putLong(WearSync.KEY_TRIGGER_MS, triggerMs)
            dataMap.putLongArray(WearSync.KEY_TRIGGER_QUEUE, queueMs)
            dataMap.putLong(WearSync.KEY_SYNC_VERSION, System.currentTimeMillis())
            asPutDataRequest().setUrgent()
        }

        val task = Wearable.getDataClient(app).putDataItem(putReq)
        task.addOnSuccessListener {
            Log.d(
                TAG,
                "Synced next alarm: system=$rawFromSystem allowed=${allowed.size} -> watch=$triggerMs queue=${queueMs.contentToString()}",
            )
        }
        task.addOnFailureListener { e -> Log.e(TAG, "putDataItem failed", e) }
        return task
    }
}
