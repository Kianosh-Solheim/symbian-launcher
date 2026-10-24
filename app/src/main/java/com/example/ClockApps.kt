package com.example

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The clock is now its own app. This picks which clock app the launcher opens (time in the status bar,
 * the menu's Clock icon), passes Symbian Clock the launcher's look, and hands over alarms made with the
 * launcher's old built-in clock.
 */
object ClockApps {
    const val SYMBIAN_CLOCK = "no.kianosh.symbianclock"
    private const val PREF = "clock_app"
    private const val DOWNLOAD_URL = "https://github.com/Kianosh-Solheim/symbian-launcher/releases/latest"
    private val KNOWN = listOf("com.android.deskclock", "com.google.android.deskclock", "com.simplemobiletools.clock",
        "org.fossify.clock", "com.sec.android.app.clockpackage")

    data class Choice(val pkg: String, val label: String, val installed: Boolean)

    /** Shows the one-time "which clock?" question when true. */
    val askNeeded = MutableStateFlow(false)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    fun chosen(ctx: Context): String? = prefs(ctx).getString(PREF, null)

    fun choose(ctx: Context, pkg: String) { prefs(ctx).edit().putString(PREF, pkg).apply(); askNeeded.value = false }

    fun checkFirstRun(ctx: Context) { if (chosen(ctx) == null) askNeeded.value = true }

    fun isInstalled(ctx: Context, pkg: String) = try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }

    fun label(ctx: Context, pkg: String?): String = when {
        pkg == null -> "Not chosen"
        pkg == SYMBIAN_CLOCK -> "Symbian Clock" + if (isInstalled(ctx, pkg)) "" else " (not installed)"
        else -> try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
    }

    /** Symbian Clock first, then every installed app that offers an alarm/clock screen. */
    fun choices(ctx: Context): List<Choice> {
        val pm = ctx.packageManager
        val pkgs = LinkedHashSet<String>()
        pkgs += SYMBIAN_CLOCK
        try { pm.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0).forEach { pkgs += it.activityInfo.packageName } } catch (_: Exception) { }
        KNOWN.filter { isInstalled(ctx, it) }.forEach { pkgs += it }
        pkgs.remove(ctx.packageName)
        return pkgs.map { Choice(it, label(ctx, it), isInstalled(ctx, it)) }
    }

    /** Opens the chosen clock app (asks first if none is chosen). */
    fun open(ctx: Context, state: LauncherState, alarms: Boolean = false) {
        val pkg = chosen(ctx) ?: run { askNeeded.value = true; return }
        if (pkg == SYMBIAN_CLOCK) {
            if (!isInstalled(ctx, pkg)) { openUrl(ctx, DOWNLOAD_URL); return }
            val i = Intent(Intent.ACTION_MAIN).setPackage(pkg).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.packageManager.getLaunchIntentForPackage(pkg)?.component?.let { i.component = it }
            putLook(i, state)
            if (alarms) i.putExtra("open_alarms", true)
            val handover = legacyHandover(ctx, i)
            try { ctx.startActivity(i); if (handover) finishHandover(ctx) } catch (_: Exception) { }
            return
        }
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(AlarmClock.ACTION_SHOW_ALARMS).setPackage(pkg)
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) { askNeeded.value = true }
    }

    private fun openUrl(ctx: Context, url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
    }

    private fun putLook(i: Intent, s: LauncherState) {
        val t = BridgeState.theme.value
        fun c(k: String, def: Int) = try { android.graphics.Color.parseColor(t[k] ?: "") } catch (_: Exception) { def }
        i.putExtra("look_style", t["style"] ?: "symbian")
            .putExtra("look_bg", c("background", 0xFF000000.toInt())).putExtra("look_surface", c("surface", 0xFF1E293B.toInt()))
            .putExtra("look_text", c("text", 0xFFFFFFFF.toInt())).putExtra("look_accent", c("accent", 0xFF3B82F6.toInt()))
            .putExtra("look_highlight", c("highlight", 0xFF60A5FA.toInt()))
            .putExtra("look_is24", s.is24HourFormat).putExtra("look_dateFormat", s.dateFormat.name)
            .putExtra("look_showWeekday", s.showWeekday).putExtra("look_weekdayFormat", s.weekdayFormat.name)
            .putExtra("look_font", s.currentFont.name)
    }

    /** Adds alarms and world clocks from the old built-in clock, once. */
    private fun legacyHandover(ctx: Context, i: Intent): Boolean {
        val p = prefs(ctx)
        if (p.getBoolean("clock_handed_over", false)) return false
        AlarmStore.load(ctx)
        val alarms = p.getString("alarms", "[]") ?: "[]"
        i.putExtra("legacy_alarms", alarms)
        p.getString("world_clocks", null)?.let { i.putExtra("legacy_world_clocks", it) }
        return true
    }

    /** Symbian Clock now owns the alarms, so the launcher stops ringing them itself. */
    private fun finishHandover(ctx: Context) {
        AlarmStore.alarms.value.map { it.id }.forEach { AlarmStore.delete(ctx, it) }
        AlarmStore.resetTimer(ctx)
        prefs(ctx).edit().putBoolean("clock_handed_over", true).apply()
    }
}
