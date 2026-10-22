package com.example

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves and restores every launcher setting (home screen, theme, pinned apps, messaging apps,
 * weather, alarms, world clocks, wallet cards, Desktop Mode options) as one JSON file.
 * Paired browser tokens are deliberately left out, so a backup never grants access.
 */
object SettingsBackup {
    private val FILES = listOf("launcher_prefs", "symbian_wallet_prefs", "weather_cache", "bridge_prefs")
    private val SKIP = setOf("bridge_prefs/tokens")

    fun export(context: Context): JSONObject {
        val prefs = JSONObject()
        FILES.forEach { name ->
            val obj = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                if ("$name/$k" in SKIP || v == null) return@forEach
                val entry = when (v) {
                    is Boolean -> JSONObject().put("t", "b").put("v", v)
                    is Int -> JSONObject().put("t", "i").put("v", v)
                    is Long -> JSONObject().put("t", "l").put("v", v)
                    is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                    is String -> JSONObject().put("t", "s").put("v", v)
                    is Set<*> -> JSONObject().put("t", "S").put("v", JSONArray(v.map { it.toString() }))
                    else -> null
                }
                entry?.let { obj.put(k, it) }
            }
            prefs.put(name, obj)
        }
        return JSONObject()
            .put("app", "Symbian Launcher")
            .put("version", 1)
            .put("created", System.currentTimeMillis())
            .put("prefs", prefs)
    }

    /** Replaces current settings with the backup. Returns an error message, or null on success. */
    fun import(context: Context, json: JSONObject): String? {
        if (json.optString("app") != "Symbian Launcher") return "This isn't a Symbian Launcher backup"
        val prefs = json.optJSONObject("prefs") ?: return "The backup is empty"
        FILES.forEach { name ->
            val obj = prefs.optJSONObject(name) ?: return@forEach
            val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val keep = if (name == "bridge_prefs") sp.getString("tokens", null) else null
            val e = sp.edit().clear()
            keep?.let { e.putString("tokens", it) }
            obj.keys().forEach { k ->
                val entry = obj.optJSONObject(k) ?: return@forEach
                when (entry.optString("t")) {
                    "b" -> e.putBoolean(k, entry.optBoolean("v"))
                    "i" -> e.putInt(k, entry.optInt("v"))
                    "l" -> e.putLong(k, entry.optLong("v"))
                    "f" -> e.putFloat(k, entry.optDouble("v").toFloat())
                    "s" -> e.putString(k, entry.optString("v"))
                    "S" -> e.putStringSet(k, entry.optJSONArray("v")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet())
                }
            }
            e.commit()
        }
        return null
    }

    /** Restarts the launcher so restored settings take effect everywhere. */
    fun restart(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try { if (intent != null) context.startActivity(intent) } catch (_: Exception) { }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ Runtime.getRuntime().exit(0) }, 400)
    }

    fun fileName(): String =
        "symbian-launcher-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".json"
}
