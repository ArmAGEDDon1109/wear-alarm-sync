package com.wearalarmsync

import android.content.Context
import android.content.pm.PackageManager

object Device {
    fun isWatch(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
}
