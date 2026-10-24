package com.example

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Alarms and the countdown timer, stored in prefs and scheduled with AlarmManager.setAlarmClock():
 * exact, allowed in Doze, and shown as the system "next alarm" — the most reliable way an app can ring.
 */
object AlarmStore {
    private const val PREFS = "launcher_prefs"
    const val TIMER_ID = "__timer__"
    private val _alarms = MutableStateFlow<List<AlarmItem>>(emptyList())
    val alarms: StateFlow<List<AlarmItem>> = _alarms
    private var loaded = false

    /** Timer: end time when running, remaining ms when paused, and the last length set. */
    data class TimerState(val endAt: Long = 0, val pausedLeft: Long = 0, val length: Long = 5 * 60_000L) {
        val running get() = endAt > 0
        fun left(now: Long = System.currentTimeMillis()) = if (running) (endAt - now).coerceAtLeast(0) else if (pausedLeft > 0) pausedLeft else length
    }
    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer

    fun load(ctx: Context) = synchronized(this) {
        if (loaded) return@synchronized
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = mutableListOf<AlarmItem>()
        try {
            val a = JSONArray(p.getString("alarms", "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list += AlarmItem(
                    id = o.getString("id"), hour = o.getInt("hour"), minute = o.getInt("minute"),
                    label = o.optString("label", "Alarm"), enabled = o.optBoolean("enabled", true),
                    days = o.optInt("days", 0), vibrate = o.optBoolean("vibrate", true),
                    snoozeMin = o.optInt("snoozeMin", 10), snoozeUntil = o.optLong("snoozeUntil", 0)
                )
            }
        } catch (_: Exception) { }
        _alarms.value = list.sortedBy { it.hour * 60 + it.minute }
        _timer.value = TimerState(p.getLong("timer_end", 0), p.getLong("timer_paused", 0), p.getLong("timer_length", 5 * 60_000L))
        loaded = true
    }

    private fun persist(ctx: Context, list: List<AlarmItem>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("hour", it.hour).put("minute", it.minute).put("label", it.label)
                .put("enabled", it.enabled).put("days", it.days).put("vibrate", it.vibrate)
                .put("snoozeMin", it.snoozeMin).put("snoozeUntil", it.snoozeUntil))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("alarms", a.toString()).apply()
        _alarms.value = list.sortedBy { it.hour * 60 + it.minute }
    }

    fun get(ctx: Context, id: String): AlarmItem? { load(ctx); return _alarms.value.firstOrNull { it.id == id } }

    fun upsert(ctx: Context, alarm: AlarmItem) {
        load(ctx)
        val list = _alarms.value.filterNot { it.id == alarm.id } + alarm.copy(snoozeUntil = 0)
        persist(ctx, list)
        scheduleAll(ctx)
    }

    fun delete(ctx: Context, id: String) {
        load(ctx)
        cancel(ctx, id)
        persist(ctx, _alarms.value.filterNot { it.id == id })
        scheduleAll(ctx)
    }

    /** Called when an alarm has rung and been stopped: one-off alarms switch off, repeating ones move on. */
    fun finished(ctx: Context, id: String) {
        val a = get(ctx, id) ?: return
        persist(ctx, _alarms.value.map { if (it.id == id) it.copy(enabled = it.days != 0 && it.enabled, snoozeUntil = 0) else it })
        scheduleAll(ctx)
    }

    fun snooze(ctx: Context, id: String) {
        if (id == TIMER_ID) return
        val a = get(ctx, id) ?: return
        val at = System.currentTimeMillis() + a.snoozeMin * 60_000L
        persist(ctx, _alarms.value.map { if (it.id == id) it.copy(snoozeUntil = at, enabled = true) else it })
        scheduleAll(ctx)
    }

    // ── Timer ──
    fun setTimerLength(ctx: Context, ms: Long) { if (!_timer.value.running) saveTimer(ctx, TimerState(0, 0, ms.coerceIn(1_000L, 24 * 3_600_000L))) }
    fun startTimer(ctx: Context) {
        val t = _timer.value
        if (t.running) return
        val left = t.left()
        saveTimer(ctx, t.copy(endAt = System.currentTimeMillis() + left, pausedLeft = 0))
        scheduleAll(ctx)
    }
    fun pauseTimer(ctx: Context) {
        val t = _timer.value
        if (!t.running) return
        saveTimer(ctx, t.copy(endAt = 0, pausedLeft = t.left().coerceAtLeast(1)))
        cancel(ctx, TIMER_ID)
    }
    fun resetTimer(ctx: Context) { saveTimer(ctx, TimerState(length = _timer.value.length)); cancel(ctx, TIMER_ID) }
    fun timerFinished(ctx: Context) = resetTimer(ctx)
    private fun saveTimer(ctx: Context, t: TimerState) {
        _timer.value = t
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("timer_end", t.endAt).putLong("timer_paused", t.pausedLeft).putLong("timer_length", t.length).apply()
    }

    /** The next enabled alarm and when it rings. */
    fun next(now: Long = System.currentTimeMillis()): Pair<AlarmItem, Long>? =
        _alarms.value.filter { it.enabled }.map { it to it.nextTrigger(now) }.minByOrNull { it.second }

    /** (Re)schedules every enabled alarm and the running timer. Safe to call any time (boot, time change, edits). */
    fun scheduleAll(ctx: Context) {
        load(ctx)
        val now = System.currentTimeMillis()
        _alarms.value.forEach { a -> if (a.enabled) schedule(ctx, a.id, a.nextTrigger(now)) else cancel(ctx, a.id) }
        val t = _timer.value
        if (t.running) {
            if (t.endAt <= now) ctx.sendBroadcast(fireIntent(ctx, TIMER_ID)) else schedule(ctx, TIMER_ID, t.endAt)
        }
    }

    private fun fireIntent(ctx: Context, id: String) = Intent(ctx, AlarmReceiver::class.java)
        .setAction(AlarmReceiver.ACTION_FIRE).putExtra("ALARM_ID", id)

    private fun pending(ctx: Context, id: String, flags: Int = 0) = PendingIntent.getBroadcast(
        ctx, id.hashCode(), fireIntent(ctx, id), PendingIntent.FLAG_IMMUTABLE or flags)

    private fun schedule(ctx: Context, id: String, at: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(ctx, 7001, Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CLOCK)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val op = pending(ctx, id, PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        }
    }

    fun cancel(ctx: Context, id: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, id, PendingIntent.FLAG_UPDATE_CURRENT))
    }
}
