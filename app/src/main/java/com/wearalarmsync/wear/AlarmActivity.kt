package com.wearalarmsync.wear

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.wearalarmsync.R
import com.wearalarmsync.VibrationAlarmPattern
import com.wearalarmsync.VibrationPrefs
import com.wearalarmsync.common.AlarmCommand
import com.wearalarmsync.common.WearSync
import com.wearalarmsync.ui.ColoredAlarmButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

class AlarmActivity : ComponentActivity() {

    private var alarmWakeLock: PowerManager.WakeLock? = null
    private var vibrationRunning: Boolean = false
    private var triggerMs: Long = WearSync.NO_ALARM
    private var returnedFromBackground: Boolean = false
    private var lostWindowFocus: Boolean = false
    private var checkingStaleOnResume: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        alarmWakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "wearalarmsync:AlarmActivity",
        ).apply { acquire(10_000L) }
        super.onCreate(savedInstanceState)
        WearAlarmNotifier.cancel(this)
        showOnLockScreen()

        triggerMs = intent.getLongExtra(WearSync.KEY_TRIGGER_MS, WearSync.NO_ALARM)
        val timeLabel = if (triggerMs > 0L) {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(triggerMs))
        } else {
            ""
        }

        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                var busy by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf<String?>(null) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    Text(
                        text = stringResource(R.string.alarm_title),
                        textAlign = TextAlign.Center,
                    )
                    if (timeLabel.isNotEmpty()) {
                        Text(
                            text = timeLabel,
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    status?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    ColoredAlarmButton(
                        onClick = {
                            if (busy) return@ColoredAlarmButton
                            busy = true
                            scope.launch {
                                val next = withContext(Dispatchers.IO) {
                                    AlarmScheduler.readBestSyncedTriggerOrNull(this@AlarmActivity)
                                }
                                if (isStaleRingSession(triggerMs, next)) {
                                    WearAlarmNotifier.cancel(this@AlarmActivity)
                                    busy = false
                                    finish()
                                    return@launch
                                }
                                val ok = withContext(Dispatchers.IO) {
                                    PhoneCommand.send(this@AlarmActivity, AlarmCommand.Dismiss)
                                }
                                status = if (ok) "" else getString(R.string.no_phone)
                                busy = false
                                if (ok) finish()
                            }
                        },
                        modifier = Modifier.padding(top = 16.dp),
                        enabled = !busy,
                        text = stringResource(R.string.dismiss),
                        backgroundColor = colorResource(R.color.alarm_dismiss_green),
                    )
                    ColoredAlarmButton(
                        onClick = {
                            if (busy) return@ColoredAlarmButton
                            busy = true
                            scope.launch {
                                val next = withContext(Dispatchers.IO) {
                                    AlarmScheduler.readBestSyncedTriggerOrNull(this@AlarmActivity)
                                }
                                if (isStaleRingSession(triggerMs, next)) {
                                    WearAlarmNotifier.cancel(this@AlarmActivity)
                                    busy = false
                                    finish()
                                    return@launch
                                }
                                val ok = withContext(Dispatchers.IO) {
                                    PhoneCommand.send(this@AlarmActivity, AlarmCommand.Snooze)
                                }
                                status = if (ok) "" else getString(R.string.no_phone)
                                busy = false
                                if (ok) finish()
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                        enabled = !busy,
                        text = stringResource(R.string.snooze),
                        backgroundColor = colorResource(R.color.alarm_snooze_red),
                    )
                    }
                }
            }
        }

        scheduleRingSessionValidation(startVibrationIfValid = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        triggerMs = intent.getLongExtra(WearSync.KEY_TRIGGER_MS, WearSync.NO_ALARM)
        returnedFromBackground = false
        lostWindowFocus = false
        scheduleRingSessionValidation(startVibrationIfValid = true)
    }

    override fun onResume() {
        super.onResume()
        if (returnedFromBackground || lostWindowFocus) {
            returnedFromBackground = false
            scheduleRingSessionValidation(startVibrationIfValid = true)
        }
    }

    override fun onPause() {
        stopCallLikeVibration()
        returnedFromBackground = true
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        val sessionTrigger = triggerMs
        lifecycleScope.launch {
            val next = withContext(Dispatchers.IO) {
                AlarmScheduler.readBestSyncedTriggerOrNull(applicationContext)
            }
            if (!isFinishing && shouldCloseStaleSession(sessionTrigger, next)) {
                runOnUiThread {
                    if (!isFinishing) closeStaleRingSession()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            lostWindowFocus = true
            stopCallLikeVibration()
            return
        }
        if (lostWindowFocus) {
            lostWindowFocus = false
            scheduleRingSessionValidation(startVibrationIfValid = true)
            return
        }
        if (!vibrationRunning && !checkingStaleOnResume) {
            window.decorView.post { startCallLikeVibration() }
        }
    }

    private fun scheduleRingSessionValidation(startVibrationIfValid: Boolean) {
        if (checkingStaleOnResume) return
        checkingStaleOnResume = true
        val sessionTrigger = triggerMs
        lifecycleScope.launch {
            try {
                val next = withContext(Dispatchers.IO) {
                    AlarmScheduler.readBestSyncedTriggerOrNull(this@AlarmActivity)
                }
                if (isFinishing) return@launch
                if (shouldCloseStaleSession(sessionTrigger, next)) {
                    Log.i(TAG, "stale ring session trigger=$sessionTrigger next=$next — closing")
                    closeStaleRingSession()
                } else if (startVibrationIfValid && !vibrationRunning) {
                    window.decorView.post { startCallLikeVibration() }
                }
            } finally {
                checkingStaleOnResume = false
            }
        }
    }

    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    // Lint предлагает освобождать wakelock в onPause, но здесь это единственный полноэкранный
    // экран будильника с 10-секундным авто-таймаутом на acquire(): держим его до onDestroy,
    // чтобы CPU не спал в момент ухода активности в фон (например при уходе в настройки вибрации).
    @SuppressLint("Wakelock")
    override fun onDestroy() {
        stopCallLikeVibration()
        alarmWakeLock?.let { if (it.isHeld) it.release() }
        alarmWakeLock = null
        val app = applicationContext
        GlobalScope.launch(Dispatchers.IO) {
            AlarmScheduler.rescheduleFromDataLayer(app)
        }
        super.onDestroy()
    }

    /** Повторяющийся паттерн «как звонок»; параметры с телефона ([VibrationPrefs]). */
    private fun startCallLikeVibration() {
        val v = VibrationAlarmPattern.defaultVibrator(this) ?: return
        v.cancel()
        val settings = VibrationPrefs.read(this)
        val effect = if (v.hasAmplitudeControl()) {
            VibrationAlarmPattern.buildEffect(settings)
        } else {
            VibrationAlarmPattern.buildLegacyEffect(settings)
        }
        VibrationAlarmPattern.vibrateWithAlarmUsage(v, effect)
        vibrationRunning = true
    }

    private fun stopCallLikeVibration() {
        if (vibrationRunning) {
            VibrationAlarmPattern.defaultVibrator(this)?.cancel()
            vibrationRunning = false
        }
    }

    /**
     * True, если в Data Layer уже другое «следующее» — и мы **не** в окне текущего звонка:
     * иначе гонка [nextAlarmClock] (в Data Layer уже «вечер», а на телефоне ещё звонит «утро»)
     * помечала сессию устаревшей и **DISMISS** на телефон не уходил.
     *
     * Пока пользователь в разумном окне вокруг [ourTrigger], всегда шлём команду — это именно снятие **этого** сигнала.
     */
    private fun isStaleRingSession(ourTrigger: Long, nextFromPhone: Long?): Boolean {
        if (ourTrigger <= 0L) return false
        val now = System.currentTimeMillis()
        if (now >= ourTrigger - RING_WINDOW_BEFORE_MS && now <= ourTrigger + RING_WINDOW_AFTER_MS) {
            return false
        }
        if (nextFromPhone == null) return false
        if (nextFromPhone == WearSync.NO_ALARM) return true
        if (abs(nextFromPhone - ourTrigger) <= SESSION_MATCH_MS) return false
        return true
    }

    /**
     * Закрыть «зомби»-экран: возврат из recents / FSI / потеря фокуса на Wear, когда звонок уже прошёл.
     * В активном окне вокруг [sessionTrigger] не закрываем — иначе гонка NO_ALARM сразу после срабатывания.
     */
    private fun shouldCloseStaleSession(sessionTrigger: Long, nextFromPhone: Long?): Boolean {
        if (sessionTrigger <= 0L) return true
        val now = System.currentTimeMillis()
        if (now >= sessionTrigger - RING_WINDOW_BEFORE_MS &&
            now <= sessionTrigger + POST_RING_NO_ALARM_GRACE_MS
        ) {
            return false
        }
        if (nextFromPhone == null || nextFromPhone == WearSync.NO_ALARM) return true
        if (nextFromPhone > now && abs(nextFromPhone - sessionTrigger) <= SESSION_MATCH_MS) {
            return false
        }
        return true
    }

    private fun closeStaleRingSession() {
        WearAlarmNotifier.cancel(this)
        stopCallLikeVibration()
        startActivity(
            Intent(this, WearMainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    private companion object {
        private const val TAG = "AlarmActivity"
        private const val SESSION_MATCH_MS = 120_000L
        private const val RING_WINDOW_BEFORE_MS = 60_000L
        private const val RING_WINDOW_AFTER_MS = 15 * 60_000L
        /** Как [AlarmScheduler] POST_RING_IGNORE_NO_ALARM_MS — гонка NO_ALARM сразу после звонка. */
        private const val POST_RING_NO_ALARM_GRACE_MS = 10_000L
    }
}
