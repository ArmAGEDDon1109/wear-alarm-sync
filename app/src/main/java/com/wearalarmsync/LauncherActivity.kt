package com.wearalarmsync

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.wearalarmsync.phone.PhoneMainActivity
import com.wearalarmsync.wear.WearMainActivity

/**
 * Одна точка входа для телефона и часов: один APK, выбор UI по [PackageManager.FEATURE_WATCH].
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val next = if (Device.isWatch(this)) {
            Intent(this, WearMainActivity::class.java)
        } else {
            Intent(this, PhoneMainActivity::class.java)
        }
        startActivity(next)
        finish()
    }
}
