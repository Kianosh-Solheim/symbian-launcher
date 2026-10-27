package com.example

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Read-only look of the launcher's current theme, so the companion apps (Snake, Blocks) can dress
 * themselves to match. content://no.kianosh.symbianlauncher.look → one row: style, bg, surface, text, accent, highlight.
 */
class LookProvider : ContentProvider() {
    companion object {
        private const val PREFS = "launcher_look"
        fun save(ctx: Context, theme: Map<String, String>) {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            theme.forEach { (k, v) -> p.putString(k, v) }
            p.apply()
        }
    }
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val p = context!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun c(k: String, def: Int) = try { android.graphics.Color.parseColor(p.getString(k, null) ?: "") } catch (_: Exception) { def }
        val cur = MatrixCursor(arrayOf("style", "bg", "surface", "text", "accent", "highlight"))
        cur.addRow(arrayOf<Any>(
            p.getString("style", "symbian") ?: "symbian",
            c("background", 0xFF000000.toInt()), c("surface", 0xFF1E293B.toInt()), c("text", 0xFFFFFFFF.toInt()),
            c("accent", 0xFF3B82F6.toInt()), c("highlight", 0xFF60A5FA.toInt())
        ))
        return cur
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
