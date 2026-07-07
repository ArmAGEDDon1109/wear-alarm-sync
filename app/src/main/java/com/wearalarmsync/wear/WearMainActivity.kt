package com.wearalarmsync.wear

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Wearable
import com.wearalarmsync.BuildConfig
import com.wearalarmsync.ExactAlarmPermission
import com.wearalarmsync.R
import com.wearalarmsync.common.AlarmToday
import com.wearalarmsync.common.WearSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private data class WearMainLines(
    val connectionLine: String,
    val alarmTimeText: String,
)

class WearMainActivity : ComponentActivity() {

    private val linesRefreshTick = mutableStateOf(0)

    /** API 31+: пока false — система не дала точные будильники; диалога нет, только экран настроек. */
    private val exactAlarmBlocked = mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        refreshExactAlarmFlag()
        window.decorView.postDelayed({
            ExactAlarmPermission.requestIfBlocked(this@WearMainActivity)
        }, 400L)
        setContent {
            MaterialTheme {
                val needsExactAlarm by exactAlarmBlocked
                var isLoading by remember { mutableStateOf(true) }
                var progressLabel by remember { mutableStateOf("") }
                var connectionLine by remember { mutableStateOf("") }
                var alarmTimeText by remember { mutableStateOf("") }

                val scanningLabel = stringResource(R.string.wear_progress_scan)
                val refreshTick by linesRefreshTick

                LaunchedEffect(Unit, refreshTick) {
                    isLoading = true
                    progressLabel = scanningLabel
                    val lines = withContext(Dispatchers.IO) {
                        val l = buildWearMainLines()
                        AlarmScheduler.rescheduleFromDataLayer(this@WearMainActivity)
                        l
                    }
                    connectionLine = lines.connectionLine
                    alarmTimeText = lines.alarmTimeText
                    progressLabel = ""
                    isLoading = false
                }

                DisposableEffect(Unit) {
                    val client = Wearable.getDataClient(this@WearMainActivity)
                    val listener = DataClient.OnDataChangedListener { events ->
                        try {
                            for (event in events) {
                                if (event.type != DataEvent.TYPE_CHANGED) continue
                                val path = event.dataItem.uri.path ?: continue
                                if (!WearSync.isNextAlarmPath(path)) continue
                                break
                            }
                        } finally {
                            events.release()
                        }
                        lifecycleScope.launch {
                            isLoading = true
                            progressLabel =
                                getString(R.string.wear_progress_incoming)
                            val lines = withContext(Dispatchers.IO) {
                                val l = buildWearMainLines()
                                AlarmScheduler.rescheduleFromDataLayer(this@WearMainActivity)
                                l
                            }
                            connectionLine = lines.connectionLine
                            alarmTimeText = lines.alarmTimeText
                            progressLabel = ""
                            isLoading = false
                        }
                    }
                    client.addListener(listener)
                    onDispose {
                        client.removeListener(listener)
                    }
                }

                val scroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .verticalScroll(scroll)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(40.dp)
                                .padding(bottom = 8.dp),
                        )
                        if (progressLabel.isNotEmpty()) {
                            Text(
                                text = progressLabel,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    if (!isLoading) {
                        Text(
                            text = connectionLine,
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            text = stringResource(R.string.wear_alarm_label),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                        Text(
                            text = alarmTimeText,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Button(
                        onClick = {
                            startActivity(
                                Intent(this@WearMainActivity, WearVibrationSettingsActivity::class.java),
                            )
                        },
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.wear_settings_button_cd),
                        )
                    }
                    if (!isLoading && needsExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Text(
                            text = stringResource(R.string.permission_exact_alarm_title),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        Text(
                            text = stringResource(R.string.permission_exact_alarm_body),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Button(
                            onClick = { ExactAlarmPermission.openScheduleExactAlarmScreen(this@WearMainActivity) },
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            Text(stringResource(R.string.permission_exact_alarm_button))
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.app_version_label,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshExactAlarmFlag()
        lifecycleScope.launch(Dispatchers.IO) {
            AlarmScheduler.rescheduleFromDataLayer(this@WearMainActivity)
            withContext(Dispatchers.Main) {
                linesRefreshTick.value = linesRefreshTick.value + 1
            }
        }
    }

    private fun refreshExactAlarmFlag() {
        exactAlarmBlocked.value =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !(getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                    .canScheduleExactAlarms()
    }

    private fun buildWearMainLines(): WearMainLines {
        val gms = GoogleApiAvailability.getInstance()
        val gmsResult = gms.isGooglePlayServicesAvailable(this)
        if (gmsResult != ConnectionResult.SUCCESS) {
            return WearMainLines(
                connectionLine = getString(R.string.wear_connection_no),
                alarmTimeText = getString(R.string.wear_alarm_time_none),
            )
        }

        val nodes = try {
            Tasks.await(Wearable.getNodeClient(this).connectedNodes)
        } catch (_: Exception) {
            return WearMainLines(
                connectionLine = getString(R.string.wear_connection_no),
                alarmTimeText = getString(R.string.wear_alarm_time_none),
            )
        }

        val connectionOk = nodes.isNotEmpty()
        val connectionLine = if (connectionOk) {
            getString(R.string.wear_connection_yes)
        } else {
            getString(R.string.wear_connection_no)
        }

        if (!connectionOk) {
            return WearMainLines(
                connectionLine = connectionLine,
                alarmTimeText = getString(R.string.wear_alarm_time_none),
            )
        }

        val trigger = AlarmScheduler.readBestSyncedTriggerOrNull(this)
        val alarmTimeText = when {
            trigger == null ||
                trigger == WearSync.NO_ALARM ||
                trigger <= 0L -> getString(R.string.wear_alarm_time_none)
            AlarmToday.isExactMidnightLocal(trigger) -> getString(R.string.wear_alarm_time_none)
            else -> formatShortTime(trigger)
        }

        return WearMainLines(
            connectionLine = connectionLine,
            alarmTimeText = alarmTimeText,
        )
    }

    private fun formatShortTime(ms: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
}
