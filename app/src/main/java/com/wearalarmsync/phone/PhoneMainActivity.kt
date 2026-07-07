package com.wearalarmsync.phone

import android.app.AlarmManager
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.Wearable
import com.wearalarmsync.BuildConfig
import com.wearalarmsync.ExactAlarmPermission
import com.wearalarmsync.R
import com.wearalarmsync.VibrationAlarmPattern
import com.wearalarmsync.VibrationPrefs
import com.wearalarmsync.VibrationSync
import com.wearalarmsync.common.AlarmToday
import com.wearalarmsync.common.VibrationSettings
import com.wearalarmsync.common.WearSync
import java.text.DateFormat
import java.util.Date

class PhoneMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.mainRoot)
        val extraTopDp = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f,
            resources.displayMetrics,
        ).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                maxOf(bars.left, cutout.left),
                maxOf(bars.top, cutout.top) + extraTopDp,
                maxOf(bars.right, cutout.right),
                maxOf(bars.bottom, cutout.bottom),
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val connection = findViewById<TextView>(R.id.connectionText)
        val progress = findViewById<ProgressBar>(R.id.syncProgress)
        val status = findViewById<TextView>(R.id.statusText)
        val syncButton = findViewById<Button>(R.id.syncButton)
        findViewById<TextView>(R.id.versionText).text =
            getString(R.string.app_version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        syncButton.setOnClickListener {
            runSyncAndShow(connection, progress, status, syncButton)
        }

        refreshWatchConnection(connection)
        runSyncAndShow(connection, progress, status, syncButton)

        window.decorView.postDelayed({
            ExactAlarmPermission.requestIfBlocked(this)
        }, 400L)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.phone_main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_vibration_settings) {
            showVibrationSettingsDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showVibrationSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_vibration_settings, null)
        val seekIntensity = dialogView.findViewById<SeekBar>(R.id.seekIntensity)
        val seekPulse = dialogView.findViewById<SeekBar>(R.id.seekPulse)
        val seekGap = dialogView.findViewById<SeekBar>(R.id.seekGap)
        val labelIntensity = dialogView.findViewById<TextView>(R.id.labelIntensity)
        val labelPulse = dialogView.findViewById<TextView>(R.id.labelPulse)
        val labelGap = dialogView.findViewById<TextView>(R.id.labelGap)

        val start = VibrationPrefs.read(this)
        seekIntensity.max = VibrationSettings.MAX_INTENSITY - VibrationSettings.MIN_INTENSITY
        seekIntensity.progress = start.intensityPercent - VibrationSettings.MIN_INTENSITY

        seekPulse.min = VibrationSettings.MIN_PULSE_MS
        seekPulse.max = VibrationSettings.MAX_PULSE_MS
        seekPulse.progress = start.pulseMs
        seekGap.min = VibrationSettings.MIN_GAP_MS
        seekGap.max = VibrationSettings.MAX_GAP_MS
        seekGap.progress = start.gapMs

        fun readSettingsFromSeeks(): VibrationSettings {
            val intensity = seekIntensity.progress + VibrationSettings.MIN_INTENSITY
            val pulse = seekPulse.progress
            val gap = seekGap.progress
            return VibrationSettings(intensity, pulse, gap).clamped()
        }

        fun refreshLabels() {
            val s = readSettingsFromSeeks()
            labelIntensity.text = getString(R.string.vibration_label_intensity_fmt, s.intensityPercent)
            labelPulse.text = getString(R.string.vibration_label_pulse_fmt, s.pulseMs)
            labelGap.text = getString(R.string.vibration_label_gap_fmt, s.gapMs)
        }

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                refreshLabels()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        seekIntensity.setOnSeekBarChangeListener(listener)
        seekPulse.setOnSeekBarChangeListener(listener)
        seekGap.setOnSeekBarChangeListener(listener)
        refreshLabels()

        dialogView.findViewById<Button>(R.id.buttonTestVibration).setOnClickListener {
            VibrationAlarmPattern.playTest(this, readSettingsFromSeeks())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vibration_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.vibration_save) { _, _ ->
                val s = readSettingsFromSeeks()
                VibrationPrefs.write(this, s)
                VibrationSync.push(this)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refreshWatchConnection(findViewById(R.id.connectionText))
        AlarmSync.pushNextAlarm(this)
        VibrationSync.push(this)
    }

    private fun refreshWatchConnection(connectionText: TextView) {
        connectionText.setText(R.string.connection_checking)
        val gms = GoogleApiAvailability.getInstance()
        if (gms.isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            connectionText.setText(R.string.connection_gms)
            return
        }
        Wearable.getNodeClient(this).connectedNodes.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                val msg = task.exception?.message ?: task.exception?.javaClass?.simpleName ?: "?"
                connectionText.text = getString(R.string.connection_error, msg)
                return@addOnCompleteListener
            }
            val n = task.result?.size ?: 0
            connectionText.text = if (n > 0) {
                getString(R.string.connection_ok, n)
            } else {
                getString(R.string.connection_none)
            }
        }
    }

    private fun runSyncAndShow(
        connectionText: TextView,
        progress: ProgressBar,
        status: TextView,
        syncButton: Button,
    ) {
        syncButton.isEnabled = false
        progress.visibility = View.VISIBLE

        val summary = describeNextAlarm()
        status.text = buildString {
            append(getString(R.string.sync_progress_title))
            append("\n✓ ")
            append(getString(R.string.sync_step_1_done))
            append("\n⋯ ")
            append(getString(R.string.sync_step_2_run))
            append("\n\n")
            append(summary)
        }

        AlarmSync.pushNextAlarm(this).addOnCompleteListener { task ->
            val tail = if (task.isSuccessful) {
                getString(R.string.sync_watch_ok)
            } else {
                val err = task.exception?.message ?: task.exception?.javaClass?.simpleName
                getString(R.string.sync_watch_failed, err ?: "unknown")
            }
            status.text = buildString {
                append(getString(R.string.sync_progress_title))
                append("\n✓ ")
                append(getString(R.string.sync_step_1_done))
                append("\n✓ ")
                append(getString(R.string.sync_step_2_done))
                append("\n\n")
                append(summary)
                append("\n\n")
                append(tail)
            }
            progress.visibility = View.GONE
            syncButton.isEnabled = true
            refreshWatchConnection(connectionText)
            VibrationSync.push(this@PhoneMainActivity)
        }
    }

    private fun describeNextAlarm(): String {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = am.nextAlarmClock
        val now = System.currentTimeMillis()
        val noToday = getString(R.string.no_alarm_today)
        if (next == null || next.triggerTime == WearSync.NO_ALARM) {
            return noToday
        }
        val raw = next.triggerTime
        if (raw <= now) {
            return noToday
        }
        val whenStr = DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
        ).format(Date(raw))
        return buildString {
            if (!AlarmToday.hasUpcomingAlarmToday(raw, now) && !AlarmToday.isExactMidnightLocal(raw)) {
                append(noToday)
                append("\n\n")
            }
            if (AlarmToday.isExactMidnightLocal(raw)) {
                append(getString(R.string.alarms_not_set))
            } else {
                append(getString(R.string.phone_next_alarm, whenStr))
            }
        }
    }
}
