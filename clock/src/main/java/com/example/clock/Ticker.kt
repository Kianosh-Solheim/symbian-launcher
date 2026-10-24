package com.example.clock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Current wall-clock time, refreshed on boundaries of [intervalMs] (1 000 = each second,
 * 60 000 = each minute). Ticking pauses automatically while the launcher is not visible,
 * so no clock keeps the CPU awake with the screen off or another app in front.
 */
@Composable
fun rememberTickingTime(intervalMs: Long = 60_000L): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val t = System.currentTimeMillis()
                now = t
                // Sleep until the next boundary rather than a fixed interval, so the display
                // flips exactly on the minute/second and never drifts.
                delay(intervalMs - (t % intervalMs) + 5)
            }
        }
    }
    return now
}

/** Thread-safe cache of date formatters; creating SimpleDateFormat is surprisingly expensive. */
object Formatters {
    private val cache = ConcurrentHashMap<String, ThreadLocal<SimpleDateFormat>>()

    fun get(pattern: String): SimpleDateFormat {
        val key = pattern + "|" + Locale.getDefault().toLanguageTag() + "|" + java.util.TimeZone.getDefault().id
        return cache.getOrPut(key) {
            ThreadLocal.withInitial { SimpleDateFormat(pattern, Locale.getDefault()) }
        }.get()!!
    }

    fun format(pattern: String, timeMillis: Long): String = get(pattern).format(timeMillis)
}
