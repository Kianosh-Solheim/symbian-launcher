package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

/**
 * Clipboard sharing between the phone and the desktop browser.
 *
 * Android only lets the focused app (or the keyboard) read the clipboard. So the phone's
 * clipboard is read (1) whenever the launcher has focus — e.g. when you press Home after
 * copying — and (2) in "instant" mode: with the one-time permissions READ_LOGS and
 * "display over other apps" (granted over USB), the system log tells us a copy happened and
 * an invisible one-pixel activity takes focus for a moment to read it, like KDE Connect does.
 */
object ClipboardHub {
    data class Clip(val text: String, val source: String, val time: Long)

    private const val PREFS = "bridge_prefs"
    private val history = ArrayDeque<Clip>()
    /** Emitted whenever a new clip arrives (from either side). */
    val events = MutableSharedFlow<Clip>(extraBufferCapacity = 16)
    @Volatile private var lastText: String? = null
    @Volatile private var watcher: Thread? = null
    @Volatile private var watcherProcess: Process? = null

    fun syncEnabled(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("clip_sync", false)
    fun setSync(c: Context, on: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("clip_sync", on).apply()
        if (on) startInstantWatcher(c.applicationContext) else stopInstantWatcher()
    }

    fun canReadLogs(c: Context) = ContextCompat.checkSelfPermission(c, android.Manifest.permission.READ_LOGS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    fun canOverlay(c: Context) = Settings.canDrawOverlays(c)
    fun instantAvailable(c: Context) = canReadLogs(c) && canOverlay(c)

    @Synchronized
    private fun record(text: String, source: String): Clip? {
        if (text.isBlank() || text == lastText) return null
        lastText = text
        val clip = Clip(text.take(100_000), source, System.currentTimeMillis())
        history.addFirst(clip)
        while (history.size > 50) history.removeLast()
        events.tryEmit(clip)
        return clip
    }

    @Synchronized
    fun historyJson(): JSONArray = JSONArray().apply {
        history.forEach { put(JSONObject().put("text", it.text).put("source", it.source).put("time", it.time)) }
    }

    /** Called with the phone clipboard text whenever we're allowed to read it. */
    fun fromPhone(text: String?) { if (!text.isNullOrBlank()) record(text, "phone") }

    /** Text copied on the computer: put it on the phone's clipboard. */
    fun fromComputer(context: Context, text: String) {
        if (record(text, "computer") == null && text == lastText) return
        Handler(Looper.getMainLooper()).post {
            try {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("From computer", text))
            } catch (_: Exception) { }
        }
    }

    /** Reads the clipboard; only works while one of our windows has focus. */
    fun readNow(context: Context) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return
            if (clip.itemCount > 0) fromPhone(clip.getItemAt(0).coerceToText(context)?.toString())
        } catch (_: Exception) { }
    }

    // ───────── Instant mode ─────────

    fun startInstantWatcher(context: Context) {
        if (watcher?.isAlive == true || !syncEnabled(context) || !instantAvailable(context)) return
        val pkg = context.packageName
        // Registering a listener makes the system log a denial line for us on every copy.
        Handler(Looper.getMainLooper()).post {
            try {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(listener)
            } catch (_: Exception) { }
        }
        watcher = thread(name = "clip-watch", isDaemon = true) {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("logcat", "-T", "1", "-v", "brief", "ClipboardService:E", "*:S"))
                watcherProcess = p
                p.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (Thread.currentThread().isInterrupted) break
                        if (line.contains(pkg) && line.contains("clipboard", ignoreCase = true)) {
                            grab(context)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SymbianClip", "Clipboard watcher stopped", e)
            }
        }
    }

    fun stopInstantWatcher() {
        try { watcherProcess?.destroy() } catch (_: Exception) { }
        watcher?.interrupt(); watcher = null; watcherProcess = null
    }

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { }

    @Volatile private var lastGrab = 0L
    private fun grab(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastGrab < 700) return
        lastGrab = now
        try {
            context.startActivity(Intent(context, ClipboardGrabActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
        } catch (e: Exception) { Log.w("SymbianClip", "Couldn't start grabber", e) }
    }
}

/** Invisible activity that takes focus for an instant to read the clipboard, then closes. */
class ClipboardGrabActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.setLayout(1, 1)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ClipboardHub.readNow(this)
            finish()
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }
}
