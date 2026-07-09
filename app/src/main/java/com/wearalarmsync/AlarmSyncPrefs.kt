package com.wearalarmsync

import android.content.Context
import com.wearalarmsync.phone.AlarmSourceApps

object AlarmSyncPrefs {
    private const val PREFS = "alarm_sync_prefs"
    private const val KEY_ALLOWED_PACKAGES = "allowed_packages_csv"
    private const val KEY_OBSERVED_PACKAGES = "observed_packages_csv"
    private const val KEY_MANUAL_PACKAGES = "manual_packages_csv"
    private const val KEY_CLOCK_ONLY_LEGACY = "sync_clock_app_only"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun allowedPackages(context: Context): Set<String> {
        val p = prefs(context)
        val stored = p.getString(KEY_ALLOWED_PACKAGES, null)
        if (!stored.isNullOrBlank()) {
            return stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
        if (p.contains(KEY_CLOCK_ONLY_LEGACY)) {
            val migrated = if (p.getBoolean(KEY_CLOCK_ONLY_LEGACY, true)) {
                AlarmSourceApps.defaultAllowedPackages(context)
            } else {
                AlarmSourceApps.discover(context).map { it.packageName }.toSet()
            }
            writeAllowedPackages(context, migrated)
            p.edit().remove(KEY_CLOCK_ONLY_LEGACY).apply()
            return migrated
        }
        val defaults = AlarmSourceApps.defaultAllowedPackages(context)
        writeAllowedPackages(context, defaults)
        return defaults
    }

    fun writeAllowedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit()
            .putString(KEY_ALLOWED_PACKAGES, packages.sorted().joinToString(","))
            .apply()
    }

    fun isPackageAllowed(context: Context, packageName: String): Boolean =
        allowedPackages(context).contains(packageName)

    fun observedPackages(context: Context): Set<String> =
        readCsvSet(context, KEY_OBSERVED_PACKAGES)

    fun addObservedPackage(context: Context, packageName: String) {
        val next = observedPackages(context).toMutableSet()
        if (next.add(packageName.trim())) {
            writeCsvSet(context, KEY_OBSERVED_PACKAGES, next)
        }
    }

    fun manualPackages(context: Context): Set<String> =
        readCsvSet(context, KEY_MANUAL_PACKAGES)

    fun addManualPackage(context: Context, packageName: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        val next = manualPackages(context).toMutableSet()
        next.add(pkg)
        writeCsvSet(context, KEY_MANUAL_PACKAGES, next)
    }

    private fun readCsvSet(context: Context, key: String): Set<String> =
        prefs(context).getString(key, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    private fun writeCsvSet(context: Context, key: String, values: Set<String>) {
        prefs(context).edit()
            .putString(key, values.sorted().joinToString(","))
            .apply()
    }
}
