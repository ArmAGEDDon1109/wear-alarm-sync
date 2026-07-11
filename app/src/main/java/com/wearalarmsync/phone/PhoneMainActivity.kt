package com.wearalarmsync.phone

import android.app.AlarmManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.Wearable
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wearalarmsync.AlarmSyncPrefs
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

        findViewById<Button>(R.id.alarmSourcesButton).setOnClickListener {
            showAlarmSourcesDialog(status)
        }

        refreshWatchConnection(connection)
        status.text = describeNextAlarm()
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

    private fun showAlarmSourcesDialog(status: TextView) {
        val apps = AlarmSourceApps.discover(this)
        if (apps.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.phone_alarm_sources_empty)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val allowed = AlarmSyncPrefs.allowedPackages(this).toMutableSet()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val checkboxes = LinkedHashMap<String, MaterialCheckBox>()
        for (app in apps) {
            val checkbox = MaterialCheckBox(this).apply {
                text = app.displayLabel()
                isChecked = app.packageName in allowed
            }
            checkboxes[app.packageName] = checkbox
            container.addView(checkbox)
        }
        val scroll = ScrollView(this).apply { addView(container) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.phone_alarm_sources_title)
            .setView(scroll)
            .setNeutralButton(R.string.phone_alarm_sources_add_package) { _, _ ->
                showAddPackageDialog(status, allowed, checkboxes, container, scroll)
            }
            .setPositiveButton(R.string.vibration_save) { _, _ ->
                saveAlarmSourcesSelection(status, checkboxes)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveAlarmSourcesSelection(
        status: TextView,
        checkboxes: Map<String, MaterialCheckBox>,
    ) {
        val selected = checkboxes.filterValues { it.isChecked }.keys
        AlarmSyncPrefs.writeAllowedPackages(this, selected)
        AlarmSync.pushNextAlarm(this)
        status.text = describeNextAlarm()
    }

    private fun showAddPackageDialog(
        status: TextView,
        allowed: MutableSet<String>,
        checkboxes: LinkedHashMap<String, MaterialCheckBox>,
        container: LinearLayout,
        scroll: ScrollView,
    ) {
        val input = EditText(this).apply {
            hint = getString(R.string.phone_alarm_sources_add_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.phone_alarm_sources_add_package)
            .setMessage(R.string.phone_alarm_sources_add_message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pkg = input.text?.toString()?.trim().orEmpty()
                if (pkg.isEmpty()) return@setPositiveButton
                val pm = packageManager
                val installed = try {
                    pm.getApplicationInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
                if (!installed) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(getString(R.string.phone_alarm_sources_unknown_package, pkg))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@setPositiveButton
                }
                AlarmSyncPrefs.addManualPackage(this, pkg)
                allowed.add(pkg)
                if (pkg !in checkboxes) {
                    val app = AlarmSourceApps.discover(this).find { it.packageName == pkg }
                        ?: AlarmSourceApp(
                            pkg,
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(),
                            AlarmSourceKind.MANUAL,
                        )
                    val checkbox = MaterialCheckBox(this).apply {
                        text = app.displayLabel()
                        isChecked = true
                    }
                    checkboxes[pkg] = checkbox
                    container.addView(checkbox)
                } else {
                    checkboxes[pkg]?.isChecked = true
                }
                saveAlarmSourcesSelection(status, checkboxes)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                showAlarmSourcesDialog(status)
            }
            .show()
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
        status.text = buildSyncProgressText(
            step1Done = true,
            step2Done = false,
            summary = summary,
            tail = null,
        )

        AlarmSync.pushNextAlarm(this).addOnCompleteListener { task ->
            val tail = if (task.isSuccessful) {
                getString(R.string.sync_watch_ok)
            } else {
                val err = task.exception?.message ?: task.exception?.javaClass?.simpleName
                getString(R.string.sync_watch_failed, err ?: "unknown")
            }
            status.text = buildSyncProgressText(
                step1Done = true,
                step2Done = true,
                summary = summary,
                tail = tail,
            )
            progress.visibility = View.GONE
            syncButton.isEnabled = true
            refreshWatchConnection(connectionText)
            VibrationSync.push(this@PhoneMainActivity)
        }
    }

    private fun buildSyncProgressText(
        step1Done: Boolean,
        step2Done: Boolean,
        summary: String,
        tail: String?,
    ): CharSequence {
        val green = ContextCompat.getColor(this, R.color.alarm_dismiss_green)
        val text = SpannableStringBuilder()
        text.append(getString(R.string.sync_progress_title))
        text.append('\n')
        appendProgressMark(text, done = step1Done, green = green)
        text.append(getString(R.string.sync_step_1_done))
        text.append('\n')
        appendProgressMark(text, done = step2Done, green = green)
        text.append(
            if (step2Done) {
                getString(R.string.sync_step_2_done)
            } else {
                getString(R.string.sync_step_2_run)
            },
        )
        if (summary.isNotBlank()) {
            text.append("\n\n")
            text.append(summary)
        }
        if (!tail.isNullOrBlank()) {
            text.append("\n\n")
            text.append(tail)
        }
        return text
    }

    private fun appendProgressMark(text: SpannableStringBuilder, done: Boolean, green: Int) {
        val mark = if (done) "✓ " else "⋯ "
        val start = text.length
        text.append(mark)
        if (done) {
            text.setSpan(
                ForegroundColorSpan(green),
                start,
                start + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun describeNextAlarm(): String {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = am.nextAlarmClock
        val now = System.currentTimeMillis()
        val allowed = AlarmSyncPrefs.allowedPackages(this)
        val noToday = getString(R.string.no_alarm_today)
        if (next == null || next.triggerTime == WearSync.NO_ALARM) {
            return noToday
        }
        val raw = next.triggerTime
        if (raw <= now) {
            return noToday
        }
        if (!AlarmSourceRecognizer.isAllowedSource(next, allowed)) {
            // Не показываем сообщение про приложение вне списка источников.
            return ""
        }
        val synced = NextAlarmResolver.triggerForWatchSync(raw, now)
        if (NextAlarmResolver.wasFilteredAsMidnightGhost(raw, synced, now)) {
            return getString(R.string.phone_midnight_sync_skipped)
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
