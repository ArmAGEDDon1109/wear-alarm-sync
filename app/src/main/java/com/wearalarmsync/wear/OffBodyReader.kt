package com.wearalarmsync.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.wearalarmsync.Device
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Одно чтение датчика [Sensor.TYPE_LOW_LATENCY_OFF_BODY] (нужен [Manifest.permission.BODY_SENSORS]).
 * Возвращает true/false по событию, null — нет датчика, таймут, нет разрешения, API < 30.
 */
object OffBodyReader {
    private const val TAG = "OffBodyReader"
    private const val TIMEOUT_MS = 400L

    /** [Sensor.TYPE_LOW_LATENCY_OFF_BODY] (34), API 30+. */
    private const val SENSOR_TYPE_LOW_LATENCY_OFF_BODY = 34

    /**
     * @return true = на руке, false = сняты; null = неизвестно (показать будильник).
     */
    fun readOnBodyOnceBlocking(context: Context): Boolean? {
        val app = context.applicationContext
        if (!Device.isWatch(app)) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.BODY_SENSORS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "BODY_SENSORS not granted")
            return null
        }
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val sensor = sm.getDefaultSensor(SENSOR_TYPE_LOW_LATENCY_OFF_BODY) ?: run {
            Log.w(TAG, "no TYPE_LOW_LATENCY_OFF_BODY sensor")
            return null
        }
        val latch = CountDownLatch(1)
        val valueHolder = arrayOfNulls<Boolean>(1)
        var finished = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (finished) return
                if (event.values.isEmpty()) return
                // 1.0 — на теле, 0.0 — off-body (AOSP)
                valueHolder[0] = event.values[0] == 1.0f
                finished = true
                latch.countDown()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = sm.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_FASTEST,
        )
        if (!ok) {
            Log.w(TAG, "registerListener failed")
            return null
        }
        return try {
            if (!latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "off-body read timeout")
                null
            } else {
                valueHolder[0]
            }
        } catch (e: InterruptedException) {
            Log.w(TAG, "off-body read interrupted", e)
            null
        } finally {
            sm.unregisterListener(listener)
        }
    }
}
