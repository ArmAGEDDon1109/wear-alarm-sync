package com.wearalarmsync.phone

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import com.wearalarmsync.AlarmSyncPrefs

enum class AlarmSourceKind {
    CLOCK,
    CALENDAR,
    OBSERVED,
    MANUAL,
}

data class AlarmSourceApp(
    val packageName: String,
    val label: String,
    val kind: AlarmSourceKind,
) {
    fun displayLabel(): String = "$label ($packageName)"
}

/**
 * Источники для выбора пользователем. [AlarmClock.ACTION_SET_ALARM] видит только «Часы»;
 * календарь и напоминания ставят будильник через [android.app.AlarmManager] без этого intent.
 */
object AlarmSourceApps {
    private val PREFERRED_CLOCK_PACKAGES = listOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.miui.clock",
        "com.sec.android.app.clockpackage",
        "com.oneplus.deskclock",
    )

    /** Частые пакеты календаря/напоминаний в [AlarmManager.getNextAlarmClock]. */
    private val KNOWN_CALENDAR_PACKAGES = listOf(
        "com.android.calendar",
        "com.google.android.calendar",
        "com.samsung.android.calendar",
        "com.android.providers.calendar",
        "com.miui.calendar",
    )

    fun discover(context: Context): List<AlarmSourceApp> {
        val app = context.applicationContext
        val pm = app.packageManager
        val seen = LinkedHashMap<String, AlarmSourceApp>()

        fun put(appEntry: AlarmSourceApp) {
            seen.putIfAbsent(appEntry.packageName, appEntry)
        }

        for (entry in fromSetAlarmHandlers(pm)) {
            put(entry)
        }
        for (pkg in KNOWN_CALENDAR_PACKAGES) {
            if (isInstalled(pm, pkg)) {
                put(AlarmSourceApp(pkg, resolveLabel(pm, pkg), AlarmSourceKind.CALENDAR))
            }
        }
        for (pkg in AlarmSyncPrefs.observedPackages(app)) {
            if (isInstalled(pm, pkg)) {
                put(AlarmSourceApp(pkg, resolveLabel(pm, pkg), AlarmSourceKind.OBSERVED))
            }
        }
        for (pkg in AlarmSyncPrefs.manualPackages(app)) {
            if (isInstalled(pm, pkg)) {
                put(AlarmSourceApp(pkg, resolveLabel(pm, pkg), AlarmSourceKind.MANUAL))
            }
        }
        // Выбранные ранее, но ещё не в списке (например после обновления логики)
        for (pkg in AlarmSyncPrefs.allowedPackages(app)) {
            if (pkg !in seen && isInstalled(pm, pkg)) {
                put(AlarmSourceApp(pkg, resolveLabel(pm, pkg), AlarmSourceKind.OBSERVED))
            }
        }

        return seen.values.sortedWith(
            compareBy<AlarmSourceApp> { kindOrder(it.kind) }
                .thenBy { it.label.lowercase() },
        )
    }

    fun defaultAllowedPackages(context: Context): Set<String> {
        val apps = discover(context)
        val discovered = apps.map { it.packageName }.toSet()
        val preferred = PREFERRED_CLOCK_PACKAGES.filter { it in discovered }.toSet()
        if (preferred.isNotEmpty()) return preferred
        return apps.firstOrNull { it.kind == AlarmSourceKind.CLOCK }?.packageName
            ?.let { setOf(it) }
            ?: discovered.take(1).toSet()
    }

    fun displayLabelFor(context: Context, packageName: String): String =
        discover(context).find { it.packageName == packageName }?.displayLabel()
            ?: "${resolveLabel(context.packageManager, packageName)} ($packageName)"

    fun recordObservedPackage(context: Context, packageName: String?) {
        if (packageName.isNullOrBlank()) return
        AlarmSyncPrefs.addObservedPackage(context, packageName)
    }

    private fun fromSetAlarmHandlers(pm: PackageManager): List<AlarmSourceApp> {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
        @Suppress("DEPRECATION")
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        return pm.queryIntentActivities(intent, flags)
            .mapNotNull { resolve ->
                val pkg = resolve.activityInfo?.packageName ?: return@mapNotNull null
                val label = resolve.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) null else AlarmSourceApp(pkg, label, AlarmSourceKind.CLOCK)
            }
            .distinctBy { it.packageName }
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        try {
            pm.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun resolveLabel(pm: PackageManager, packageName: String): String =
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString().trim()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }

    private fun kindOrder(kind: AlarmSourceKind): Int = when (kind) {
        AlarmSourceKind.CLOCK -> 0
        AlarmSourceKind.CALENDAR -> 1
        AlarmSourceKind.OBSERVED -> 2
        AlarmSourceKind.MANUAL -> 3
    }
}
