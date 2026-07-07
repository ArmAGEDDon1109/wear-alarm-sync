package com.wearalarmsync.wear

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.wearalarmsync.Device
import com.wearalarmsync.common.WearSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "AlarmReceiver fired")
        AlarmScheduler.markAlarmBroadcastConsumed(context)
        val app = context.applicationContext
        if (!Device.isWatch(app)) {
            Log.w(TAG, "not a watch, ignoring")
            return
        }
        val trigger = intent?.getLongExtra(WearSync.KEY_TRIGGER_MS, WearSync.NO_ALARM)
            ?: WearSync.NO_ALARM
        val launch = Intent(app, AlarmActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(WearSync.KEY_TRIGGER_MS, trigger)
        }

        val pending = goAsync()
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "wearalarmsync:AlarmReceiver",
            )
            wakeLock.acquire(15_000L)
            try {
                val skipUi = shouldSkipAlarmBecauseOffBody(app)
                if (skipUi) {
                    Log.i(TAG, "skip alarm UI (off-body + user setting)")
                } else {
                    val uiDone = CountDownLatch(1)
                    Handler(Looper.getMainLooper()).post {
                        try {
                            showAlarmUiOnMainThread(app, launch, trigger)
                        } catch (e: Exception) {
                            Log.e(TAG, "showAlarmUiOnMainThread failed", e)
                        } finally {
                            uiDone.countDown()
                        }
                    }
                    if (!uiDone.await(12, TimeUnit.SECONDS)) {
                        Log.e(TAG, "timeout waiting for main-thread alarm UI")
                    }
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                scheduleResyncFromDataLayer(app)
                pending.finish()
                executor.shutdown()
            }
        }
    }

    /**
     * OPLUS/Wear: прямой [Context.startActivity] из приёмника после [goAsync] даёт
     * `Background activity launch blocked` даже с [MODE_BACKGROUND_ACTIVITY_START_ALLOWED].
     * [PendingIntent.send] с тем же bundle и PI, созданным для активности — рабочий путь; плюс FSI в [WearAlarmNotifier].
     */
    private fun showAlarmUiOnMainThread(app: Context, launch: Intent, trigger: Long) {
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val optsBundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }.toBundle()
        } else {
            null
        }
        try {
            val pi = PendingIntent.getActivity(app, PI_ALARM_ACTIVITY, launch, piFlags)
            if (optsBundle != null) {
                pi.send(app, 0, null, null, null, null, optsBundle)
            } else {
                pi.send()
            }
            Log.i(TAG, "AlarmActivity via PendingIntent.send")
        } catch (e: Exception) {
            Log.e(TAG, "PendingIntent.send(AlarmActivity) failed, try startActivity", e)
            try {
                if (optsBundle != null) {
                    app.startActivity(launch, optsBundle)
                } else {
                    app.startActivity(launch)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "startActivity fallback failed", e2)
            }
        }
        try {
            WearAlarmNotifier.showFullScreenAlarm(app, trigger)
            Log.i(TAG, "WearAlarmNotifier.showFullScreenAlarm ok")
        } catch (e: Exception) {
            Log.e(TAG, "WearAlarmNotifier.showFullScreenAlarm failed", e)
        }
    }

    private fun shouldSkipAlarmBecauseOffBody(app: Context): Boolean {
        if (!WearAlarmPrefs.suppressWhenOffBody(app)) return false
        val onBody = OffBodyReader.readOnBodyOnceBlocking(app) ?: return false
        return !onBody
    }

    private fun scheduleResyncFromDataLayer(app: Context) {
        val h = Handler(Looper.getMainLooper())
        h.postDelayed({
            rescheduleScope.launch {
                AlarmScheduler.rescheduleFromDataLayer(app)
            }
        }, 1500L)
        h.postDelayed({
            rescheduleScope.launch {
                AlarmScheduler.rescheduleFromDataLayer(app)
            }
        }, 4000L)
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val PI_ALARM_ACTIVITY = 94035
        private val rescheduleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
