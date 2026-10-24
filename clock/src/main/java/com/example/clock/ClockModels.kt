package com.example.clock

import java.util.Calendar
import java.util.UUID

/**
 * An alarm. [days] is a bit set of repeat days: bit 0 = Monday … bit 6 = Sunday (0 = rings once).
 */
data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val label: String = "Alarm",
    val enabled: Boolean = true,
    val days: Int = 0,
    val vibrate: Boolean = true,
    val snoozeMin: Int = 10,
    /** When a snooze is pending, the time it rings (ms), else 0. */
    val snoozeUntil: Long = 0
) {
    fun toDisplayString(): String {
        val h = hour.toString().padStart(2, '0')
        val m = minute.toString().padStart(2, '0')
        return "$h:$m"
    }

    fun repeatsOn(day: Int) = days and (1 shl day) != 0

    fun daysLabel(): String = when (days) {
        0 -> "Once"
        0b1111111 -> "Every day"
        0b0011111 -> "Weekdays"
        0b1100000 -> "Weekends"
        else -> DAY_SHORT.filterIndexed { i, _ -> repeatsOn(i) }.joinToString(" ")
    }

    /** Next time this alarm rings after [from] (ignores [enabled]). */
    fun nextTrigger(from: Long = System.currentTimeMillis()): Long {
        if (snoozeUntil > from) return snoozeUntil
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_MONTH, 1)
        if (days != 0) {
            repeat(7) {
                // Calendar: MONDAY = 2 … SUNDAY = 1  →  0 … 6
                val idx = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
                if (repeatsOn(idx)) return c.timeInMillis
                c.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return c.timeInMillis
    }

    companion object {
        val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
}

/** "in 7 h 5 min" style countdown text. */
fun untilText(at: Long, now: Long = System.currentTimeMillis()): String {
    val mins = ((at - now + 59_999) / 60_000).coerceAtLeast(0)
    val d = mins / 1440; val h = (mins % 1440) / 60; val m = mins % 60
    return when {
        d > 0 -> "in $d d $h h"
        h > 0 -> "in $h h $m min"
        else -> "in $m min"
    }
}

data class WorldClockItem(
    val id: String = UUID.randomUUID().toString(),
    val city: String,
    val timeZoneId: String
)
