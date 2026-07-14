package com.wearalarmsync.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.wearalarmsync.R
import com.wearalarmsync.VibrationAlarmPattern
import com.wearalarmsync.VibrationPrefs
import com.wearalarmsync.VibrationSync
import com.wearalarmsync.common.VibrationSettings

/**
 * Настройка вибрации будильника на часах (те же [VibrationPrefs], что и на телефоне; синхронизация через Data Layer)
 * и локальные опции будильника (например off-body), без синхронизации с телефоном.
 */
class WearVibrationSettingsActivity : ComponentActivity() {

    private val suppressOffBody = mutableStateOf(false)

    private val bodySensorsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            WearAlarmPrefs.setSuppressWhenOffBody(this, granted)
            suppressOffBody.value = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressOffBody.value = WearAlarmPrefs.suppressWhenOffBody(this)
        val initial = VibrationPrefs.read(this)
        setContent {
            ComposeMaterialTheme(colorScheme = darkColorScheme()) {
            MaterialTheme {
                var intensity by remember { mutableFloatStateOf(initial.intensityPercent.toFloat()) }
                var pulse by remember { mutableFloatStateOf(initial.pulseMs.toFloat()) }
                var gap by remember { mutableFloatStateOf(initial.gapMs.toFloat()) }
                var suppress by suppressOffBody

                fun currentSettings(): VibrationSettings =
                    VibrationSettings(
                        intensityPercent = intensity.toInt(),
                        pulseMs = pulse.toInt(),
                        gapMs = gap.toInt(),
                    ).clamped()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.wear_vibration_title),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = suppress,
                            onCheckedChange = { want ->
                                if (want) {
                                    if (!hasBodySensors()) {
                                        bodySensorsPermission.launch(Manifest.permission.BODY_SENSORS)
                                    } else {
                                        WearAlarmPrefs.setSuppressWhenOffBody(
                                            this@WearVibrationSettingsActivity,
                                            true,
                                        )
                                        suppress = true
                                    }
                                } else {
                                    WearAlarmPrefs.setSuppressWhenOffBody(
                                        this@WearVibrationSettingsActivity,
                                        false,
                                    )
                                    suppress = false
                                }
                            },
                        )
                        Text(
                            text = stringResource(R.string.wear_setting_suppress_off_body),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.wear_setting_suppress_off_body_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.vibration_label_intensity_fmt, intensity.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        valueRange = VibrationSettings.MIN_INTENSITY.toFloat()..
                            VibrationSettings.MAX_INTENSITY.toFloat(),
                        steps = VibrationSettings.MAX_INTENSITY - VibrationSettings.MIN_INTENSITY - 1,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vibration_label_pulse_fmt, pulse.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Slider(
                        value = pulse,
                        onValueChange = { pulse = it },
                        valueRange = VibrationSettings.MIN_PULSE_MS.toFloat()..
                            VibrationSettings.MAX_PULSE_MS.toFloat(),
                        steps = (VibrationSettings.MAX_PULSE_MS - VibrationSettings.MIN_PULSE_MS) / 10 - 1,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vibration_label_gap_fmt, gap.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Slider(
                        value = gap,
                        onValueChange = { gap = it },
                        valueRange = VibrationSettings.MIN_GAP_MS.toFloat()..
                            VibrationSettings.MAX_GAP_MS.toFloat(),
                        steps = (VibrationSettings.MAX_GAP_MS - VibrationSettings.MIN_GAP_MS) / 10 - 1,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { VibrationAlarmPattern.playTest(this@WearVibrationSettingsActivity, currentSettings()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.vibration_test))
                    }
                    Button(
                        onClick = {
                            val s = currentSettings()
                            VibrationPrefs.write(this@WearVibrationSettingsActivity, s)
                            VibrationSync.push(this@WearVibrationSettingsActivity)
                            finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.vibration_save))
                    }
                    Button(
                        onClick = { finish() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.wear_vibration_back))
                    }
                }
            }
            }
        }
    }

    private fun hasBodySensors(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED
}
