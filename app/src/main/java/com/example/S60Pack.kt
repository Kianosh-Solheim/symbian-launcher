package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Icons from the "S60 Icon Pack" by x1unix (https://github.com/x1unix/s60-icon-pack),
 * licensed CC BY-SA 4.0 — see assets/licenses/S60IconPack-CC-BY-SA-4.0.md.
 * Used by the Classic S60 Sky theme. The pack's own appfilter.xml maps apps to icons;
 * the phone's built-in functions are mapped by name on top of that.
 */
object S60Pack {
    private var appCtx: Context? = null
    fun init(ctx: Context) { if (appCtx == null) appCtx = ctx.applicationContext }

    /** Our names for built-in functions → pack icon names. */
    private val byName = mapOf(
        "msg" to "mms", "mail" to "email", "cal" to "calendar", "person" to "contacts", "apps" to "app",
        "settings" to "settings", "calc" to "calculator", "clock" to "clock", "call" to "call_log", "camera" to "camera",
        "globe" to "browser", "folder" to "files", "gallery" to "gallery", "music" to "music", "notes" to "notepad"
    )
    /** Extra packages the pack doesn't list (LineageOS / DumberOS apps). */
    private val extraPkgs = mapOf(
        "org.lineageos.aperture" to "camera", "org.lineageos.glimpse" to "gallery", "org.lineageos.eleven" to "music",
        "org.lineageos.twelve" to "music", "org.lineageos.recorder" to "sound_recorder", "org.lineageos.etar" to "calendar",
        "com.android.dialer" to "call_log", "com.android.messaging" to "mms", "com.android.calculator2" to "calculator",
        "com.android.calendar" to "calendar", "com.android.gallery3d" to "gallery", "com.android.fmradio" to "fm_radio",
        "org.lineageos.jelly" to "browser", "com.android.stk" to "sim", "com.android.providers.downloads.ui" to "downloads",
        "com.android.wallpaper" to "themes", "com.android.wallpaperpicker" to "themes", "com.android.soundrecorder" to "sound_recorder",
        "no.kianosh.symbianclock" to "clock", "org.fossify.calendar" to "calendar", "org.fossify.gallery" to "gallery",
        "org.fossify.filemanager" to "files", "org.fossify.notes" to "notepad", "org.fossify.musicplayer" to "music",
        "org.fossify.contacts" to "contacts", "org.fossify.phone" to "call_log", "org.fossify.messages" to "mms", "org.fossify.clock" to "clock",
        "org.fossify.voicerecorder" to "sound_recorder", "org.fossify.calculator" to "calculator"
    )

    private val pkgMap: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        try {
            appCtx?.assets?.open("s60pack/appfilter.xml")?.bufferedReader()?.use { r ->
                val re = Regex("""component="ComponentInfo\{([^/}]+)[^"]*"\s+drawable="([^"]+)"""")
                re.findAll(r.readText()).forEach { val (pkg, d) = it.destructured; if (d.isNotEmpty()) m.putIfAbsent(pkg, d) }
            }
        } catch (_: Exception) { }
        m.putAll(extraPkgs)
        m
    }

    private val cache = HashMap<String, Bitmap?>()
    private fun load(name: String): Bitmap? = synchronized(cache) {
        cache.getOrPut(name) { try { appCtx?.assets?.open("s60pack/$name.png")?.use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } }
    }

    fun forPackage(pkg: String?): Bitmap? = pkg?.let { pkgMap[it] }?.let { load(it) }
    fun forName(name: String): Bitmap? = byName[name]?.let { load(it) }
}
