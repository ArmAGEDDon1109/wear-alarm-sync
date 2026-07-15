package com.wearalarmsync.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlin.jvm.Volatile
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataItemBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.wearalarmsync.common.AlarmToday
import com.wearalarmsync.common.WearSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    private const val REQUEST_CODE = 94001

    /**
     * Собственный scope вместо [android.os.Handler]: [AlarmScheduler] — singleton без жизненного
     * цикла своего компонента, поэтому нужен долгоживущий scope, а не привязанный к Activity/Service.
     * [SupervisorJob] — сбой одного отложенного ретрая не должен ронять последующие.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private const val PREFS = "alarm_scheduler_state"
    private const val KEY_LAST_RING_MS = "last_scheduled_ring_ms"

    /**
     * Время последнего `setAlarmClock` (wall clock).
     * Хранится в SharedPreferences — иначе после убийства процесса (cold-start от broadcast/Wearable)
     * `lastScheduledRingMs` сбрасывается, и пришедший от телефона **NO_ALARM** срывает уже летящий broadcast
     * локального будильника, который ещё не успел дойти до [AlarmReceiver] (наблюдалось при двух будильниках подряд).
     */
    @Volatile
    private var lastScheduledRingMs: Long = Long.MIN_VALUE

    private fun loadLastRingMs(context: Context): Long {
        if (lastScheduledRingMs != Long.MIN_VALUE) return lastScheduledRingMs
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = prefs.getLong(KEY_LAST_RING_MS, WearSync.NO_ALARM)
        lastScheduledRingMs = v
        return v
    }

    private fun storeLastRingMs(context: Context, value: Long) {
        lastScheduledRingMs = value
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_RING_MS, value)
            .apply()
    }

    /** Сколько мс после запланированного звонка игнорировать NO_ALARM из Data Layer (телефон шлёт -1 сразу после срабатывания системного будильника). */
    private const val POST_RING_IGNORE_NO_ALARM_MS = 10_000L

    /** Допуск «мы уже в моменте звонка» для границы grace (рассинхрон часов). */
    private const val PRE_RING_TOLERANCE_MS = 2_000L

    /**
     * [AlarmManager.getNextAlarmClock] на телефоне иногда за секунды до звонка отдаёт **другой** «следующий»
     * (например вечерний), Data Layer переписывает часы — локальный [setAlarmClock] на ближайший сигнал снимается,
     * [AlarmReceiver] не вызывается и плашка не появляется.
     */
    private const val IMMINENT_BEFORE_RING_MS = 120_000L

    /** Насколько позже времени текущего звонка должен быть новый триггер, чтобы считать это подменой из API, а не переносом на +10 мин. */
    private const val FAR_FUTURE_JUMP_MS = 3_600_000L

    /**
     * Два будильника с разницей в минуту: `nextAlarmClock` переключается на второй до звонка первого —
     * без этой защиты часы снимают [setAlarmClock] на T1 и пропускают его (в логе сразу только T2).
     */
    private const val CLOSE_STACKED_GAP_MS = 15 * 60 * 1000L

    /**
     * Если локально планировался звонок не более этого назад — игнорируем NO_ALARM от телефона.
     * После cold-start (часы вышли из s2idle к моменту нашего звонка) Wearable callback может прийти
     * раньше, чем доставится собственный broadcast от AlarmManager, и `am.cancel()` сорвёт его.
     */
    private const val POST_FIRE_PROTECT_MS = 90_000L

    private data class SyncRow(val version: Long, val trigger: Long, val queueMs: LongArray)

    private data class ParseResult(val rows: List<SyncRow>, val sawAnyItem: Boolean)

    private data class ScanResult(val rows: List<SyncRow>, val anyItemsInNetwork: Boolean)

    private fun rowComparator(now: Long): Comparator<SyncRow> =
        compareByDescending<SyncRow> { it.version }
            .thenByDescending { tieRank(it.trigger, now) }

    private fun tieRank(trigger: Long, now: Long): Long = when {
        trigger == WearSync.NO_ALARM -> Long.MIN_VALUE / 4
        trigger > now -> trigger
        else -> Long.MIN_VALUE / 2 + trigger
    }

    private inline fun <T> DataItemBuffer.useBuffer(block: (DataItemBuffer) -> T): T {
        try {
            return block(this)
        } finally {
            release()
        }
    }

    private fun parseRowsWithCount(buf: DataItemBuffer): ParseResult {
        val out = ArrayList<SyncRow>(4)
        var sawAny = false
        for (item in buf) {
            sawAny = true
            if (!WearSync.isNextAlarmPath(item.uri.path)) continue
            try {
                val map = DataMapItem.fromDataItem(item).dataMap
                val trigger = map.getLong(WearSync.KEY_TRIGGER_MS, WearSync.NO_ALARM)
                val ver = map.getLong(WearSync.KEY_SYNC_VERSION, 0L)
                val queue = map.getLongArray(WearSync.KEY_TRIGGER_QUEUE) ?: longArrayOf()
                out.add(SyncRow(ver, trigger, queue))
            } catch (e: Exception) {
                Log.w(TAG, "parseRowsWithCount: Failed to parse item, skipping", e)
                // Continue processing other items
            }
        }
        return ParseResult(out, sawAny)
    }

    /** Ближайшее будущее время среди основного триггера и очереди с телефона (без вывода очереди на экран). */
    private fun earliestFutureTrigger(context: Context, primary: Long, queueMs: LongArray, now: Long): Long {
        val cand = ArrayList<Long>(4)
        fun consider(t: Long) {
            if (t != WearSync.NO_ALARM && t > now && !AlarmToday.isExactMidnightLocal(t)) {
                cand.add(t)
            }
        }
        consider(primary)
        for (t in queueMs) consider(t)
        return cand.minOrNull() ?: WearSync.NO_ALARM
    }

    /** После срабатывания broadcast — сбросить «последнее запланированное», чтобы следующая синхронизация не упиралась в защиту от stacked. */
    fun markAlarmBroadcastConsumed(context: Context) {
        storeLastRingMs(context, WearSync.NO_ALARM)
        AlarmKeepAliveService.stop(context)
        Log.d(TAG, "markAlarmBroadcastConsumed")
    }

    /**
     * Сначала [DataClient.getDataItems] по [WearSync.nextAlarmDataLayerUri] (как в документации Wear),
     * если результата нет — обход всех элементов Data Layer и отбор по [WearSync.isNextAlarmPath] (на случай отличий `Uri.getPath()`).
     */
    fun readBestSyncedTriggerOrNull(context: Context): Long? {
        val app = context.applicationContext
        val gms = GoogleApiAvailability.getInstance()
        if (gms.isGooglePlayServicesAvailable(app) != ConnectionResult.SUCCESS) {
            Log.w(TAG, "readBestSyncedTriggerOrNull: Google Play Services unavailable")
            return null
        }
        val client = Wearable.getDataClient(app)
        
        val pref = try {
            Log.d(TAG, "readBestSyncedTriggerOrNull: Attempting targeted query for /alarms/next")
            Tasks.await(client.getDataItems(WearSync.nextAlarmDataLayerUri()))
                .useBuffer { parseRowsWithCount(it) }
        } catch (e: Exception) {
            Log.w(TAG, "readBestSyncedTriggerOrNull: getDataItems(${WearSync.nextAlarmDataLayerUri()}) failed, falling back to full scan", e)
            ParseResult(emptyList(), false)
        }
        
        val scan = try {
            if (pref.rows.isNotEmpty()) {
                Log.d(TAG, "readBestSyncedTriggerOrNull: Using targeted query results (${pref.rows.size} rows)")
                ScanResult(pref.rows, pref.sawAnyItem)
            } else {
                Log.d(TAG, "readBestSyncedTriggerOrNull: Targeted query returned empty, doing full scan")
                Tasks.await(client.dataItems).useBuffer { full ->
                    val pr = parseRowsWithCount(full)
                    ScanResult(pr.rows, pr.sawAnyItem)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readBestSyncedTriggerOrNull: dataItems failed during full scan", e)
            return null
        }
        
        val now = System.currentTimeMillis()
        return when {
            !scan.anyItemsInNetwork -> {
                Log.d(TAG, "readBestSyncedTrigger: Data Layer empty (no network items)")
                null
            }
            scan.rows.isEmpty() -> {
                Log.d(TAG, "readBestSyncedTrigger: No matching /alarms/next path found")
                WearSync.NO_ALARM
            }
            else -> {
                val best = scan.rows.maxWith(rowComparator(now))
                val effective = earliestFutureTrigger(context, best.trigger, best.queueMs, now)
                Log.d(TAG, "readBestSyncedTrigger: effective=$effective syncVer=${best.version}")
                effective
            }
        }
    }

    /**
     * @param allowNoAlarmOverFuture после отложенного повтора: принудительно применить [NO_ALARM], даже если локально
     * ещё висит будущий [setAlarmClock] (иначе транзиентный NO_ALARM между двумя будильниками снимал бы второй).
     */
    // WearRecents: legacyActivity PendingIntent (обход BAL на OPLUS Wear, см. комментарий ниже) собран
    // с NEW_TASK/CLEAR_TOP намеренно — без них лаунч из фонового процесса не проходит на части прошивок.
    @android.annotation.SuppressLint("WearRecents")
    fun scheduleOrCancel(
        context: Context,
        triggerMs: Long,
        allowNoAlarmOverFuture: Boolean = false,
    ) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val lastRing = loadLastRingMs(app)

        if (triggerMs == WearSync.NO_ALARM) {
            val futureLocal = lastRing != WearSync.NO_ALARM && lastRing > now
            val justFired = lastRing != WearSync.NO_ALARM &&
                lastRing <= now &&
                now - lastRing <= POST_FIRE_PROTECT_MS
            if ((futureLocal || justFired) && !allowNoAlarmOverFuture) {
                Log.w(
                    TAG,
                    "NO_ALARM from Data Layer but local ring at $lastRing (now=$now) — deferred resync (transient gap)",
                )
                val appCtx = app.applicationContext
                scope.launch {
                    delay(5_000L)
                    rescheduleFromDataLayer(appCtx, allowNoAlarmOverFuture = true)
                }
                return
            }
        }

        if (triggerMs != WearSync.NO_ALARM &&
            lastRing != WearSync.NO_ALARM &&
            lastRing > now &&
            lastRing - now <= IMMINENT_BEFORE_RING_MS &&
            triggerMs > lastRing &&
            triggerMs <= lastRing + CLOSE_STACKED_GAP_MS
        ) {
            Log.w(
                TAG,
                "Ignoring Data Layer trigger=$triggerMs (stacked soon after $lastRing; keep earlier alarm on watch)",
            )
            return
        }

        if (triggerMs != WearSync.NO_ALARM &&
            lastRing != WearSync.NO_ALARM &&
            lastRing > now &&
            lastRing - now <= IMMINENT_BEFORE_RING_MS &&
            triggerMs > lastRing + FAR_FUTURE_JUMP_MS
        ) {
            Log.w(
                TAG,
                "Ignoring Data Layer trigger=$triggerMs (imminent local ring at $lastRing; likely nextAlarmClock race on phone)",
            )
            return
        }

        // Любое обновление с телефона / перепланирование: убрать «зависшее» кольцо-уведомление
        // (иначе остаётся FSI; повторный «Выключить» бьёт в ACTION_DISMISS по уже «следующему»).
        WearAlarmNotifier.cancel(app)
        // Игнор NO_ALARM только сразу после времени запланированного звонка (не для «следующего» в будущем:
        // иначе `now < lastRing + 10s` выполнялось бы часами до срабатывания и ломало снятие будильника с телефона).
        if (triggerMs == WearSync.NO_ALARM &&
            lastRing != WearSync.NO_ALARM &&
            now >= lastRing - PRE_RING_TOLERANCE_MS &&
            now < lastRing + POST_RING_IGNORE_NO_ALARM_MS
        ) {
            Log.d(
                TAG,
                "Ignoring NO_ALARM from Data Layer during post-ring grace (lastRing=$lastRing now=$now)",
            )
            return
        }

        // Устаревшая строка Data Layer: время уже в прошлом, а локально уже стоит более поздний будильник —
        // нельзя вызывать am.cancel до выхода, иначе снимем следующий сигнал (гонка dismiss / onDestroy / listener).
        if (triggerMs != WearSync.NO_ALARM &&
            triggerMs <= now &&
            lastRing != WearSync.NO_ALARM &&
            lastRing > now &&
            triggerMs < lastRing
        ) {
            Log.d(
                TAG,
                "Ignoring stale past trigger from Data Layer (trigger=$triggerMs activeFuture=$lastRing now=$now)",
            )
            return
        }

        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        // Сборка 1.5: срабатывание шло через getActivity — на OPLUS Wear блокируется BAL.
        val legacyActivity = PendingIntent.getActivity(
            app,
            REQUEST_CODE,
            Intent(app, AlarmActivity::class.java).apply {
                putExtra(WearSync.KEY_TRIGGER_MS, triggerMs)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            flags,
        )
        am.cancel(legacyActivity)

        // Срабатывание через broadcast → full-screen intent (см. [WearAlarmNotifier]).
        val broadcastIntent = Intent(app, AlarmReceiver::class.java).apply {
            putExtra(WearSync.KEY_TRIGGER_MS, triggerMs)
        }
        val alarmPi = PendingIntent.getBroadcast(app, REQUEST_CODE, broadcastIntent, flags)
        am.cancel(alarmPi)

        if (triggerMs == WearSync.NO_ALARM || triggerMs <= now) {
            storeLastRingMs(app, WearSync.NO_ALARM)
            AlarmKeepAliveService.stop(app)
            Log.d(TAG, "No upcoming alarm (cancelled local schedule), triggerMs=$triggerMs")
            return
        }

        // Удерживаем процесс foreground-сервисом: иначе OPLUS/Heytap Wear через ~10 с после screen-off делает
        // forceStopPackage(com.wearalarmsync) и `removeAlarmsForPackage` снимает наш setAlarmClock на следующий сигнал.
        AlarmKeepAliveService.start(app, triggerMs)

        val showIntent = PendingIntent.getActivity(
            app,
            0,
            Intent(app, WearMainActivity::class.java),
            flags,
        )

        val canExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            try {
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerMs, showIntent),
                    alarmPi,
                )
                storeLastRingMs(app, triggerMs)
                Log.d(TAG, "Scheduled setAlarmClock at $triggerMs")
            } catch (e: SecurityException) {
                Log.e(TAG, "setAlarmClock SecurityException, trying fallbacks", e)
                scheduleWithFallback(app, am, triggerMs, alarmPi)
            }
        } else {
            scheduleWithFallback(app, am, triggerMs, alarmPi)
        }
    }

    private fun scheduleWithFallback(context: Context, am: AlarmManager, triggerMs: Long, alarmPi: PendingIntent) {
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, alarmPi)
            storeLastRingMs(context, triggerMs)
            Log.w(TAG, "Fallback: setExactAndAllowWhileIdle at $triggerMs")
            return
        } catch (e: SecurityException) {
            Log.w(TAG, "setExactAndAllowWhileIdle denied", e)
        }
        @Suppress("DEPRECATION")
        am.set(AlarmManager.RTC_WAKEUP, triggerMs, alarmPi)
        storeLastRingMs(context, triggerMs)
        Log.w(
            TAG,
            "Fallback: set(RTC_WAKEUP) at $triggerMs (включите «Точные будильники» на часах для надёжности)",
        )
    }

    fun rescheduleFromDataLayer(context: Context, allowNoAlarmOverFuture: Boolean = false) {
        val app = context.applicationContext
        val gms = GoogleApiAvailability.getInstance()
        if (gms.isGooglePlayServicesAvailable(app) != ConnectionResult.SUCCESS) {
            Log.w(TAG, "rescheduleFromDataLayer: GMS unavailable")
            return
        }
        val trigger = readBestSyncedTriggerOrNull(app) ?: run {
            Log.w(TAG, "rescheduleFromDataLayer: could not read data layer, leaving alarm unchanged")
            return
        }
        scheduleOrCancel(app, trigger, allowNoAlarmOverFuture)
    }
}
