package com.wearalarmsync.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.wearalarmsync.Device
import com.wearalarmsync.VibrationPrefs
import com.wearalarmsync.common.WearSync

class AlarmDataListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "AlarmDataListener"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        if (!Device.isWatch(this)) {
            Log.d(TAG, "onDataChanged: Not running on watch, ignoring")
            return
        }

        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) {
                Log.d(TAG, "onDataChanged: Skipping non-CHANGE event type: ${event.type}")
                continue
            }

            val item = event.dataItem
            val path = item.uri.path ?: continue

            try {
                when {
                    WearSync.isVibrationSettingsPath(path) -> {
                        Log.d(TAG, "onDataChanged: Processing vibration settings from $path")
                        val map = DataMapItem.fromDataItem(item).dataMap
                        VibrationPrefs.applyFromDataMap(this, map)
                    }
                    WearSync.isNextAlarmPath(path) -> {
                        Log.d(TAG, "onDataChanged: next alarm path $path → rescheduleFromDataLayer")
                        AlarmScheduler.rescheduleFromDataLayer(this)
                    }
                    else -> {
                        Log.d(TAG, "onDataChanged: Unknown path $path, ignoring")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "onDataChanged: Exception processing event from $path", e)
                // Continue processing other events even if one fails
            }
        }
    }
}
