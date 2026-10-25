package com.example.clock

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

enum class DateFormatOption(val pattern: String, val label: String) {
    D_MMM("d MMM", "21 Aug"),
    D_MM("d/MM", "21/08"),
    DD_MM_YYYY("dd.MM.yyyy", "21.08.2026"),
    SYSTEM_DEFAULT("", "System default")
}

enum class WeekdayFormat { SHORT, FULL }

enum class AppFont {
    NOKIA_PURE, PIXEL, MONOSPACE, SANS_SERIF, SERIF;
    fun toFontFamily(): FontFamily = when (this) {
        NOKIA_PURE -> FontFamily.SansSerif
        PIXEL, MONOSPACE -> FontFamily.Monospace
        SANS_SERIF -> FontFamily.Default
        SERIF -> FontFamily.Serif
    }
}

/**
 * Look and clock format. The launcher sends these when it opens the clock ("follow the launcher"),
 * and they are remembered so the clock looks the same when opened any other way.
 */
data class ClockLook(
    val win95: Boolean = false,
    val lcd: Boolean = false,
    val background: Int = 0xFF000000.toInt(),
    val surface: Int = 0xFF1E293B.toInt(),
    val text: Int = 0xFFFFFFFF.toInt(),
    val accent: Int = 0xFF3B82F6.toInt(),
    val highlight: Int = 0xFF60A5FA.toInt(),
    val is24: Boolean = true,
    val dateFormat: DateFormatOption = DateFormatOption.SYSTEM_DEFAULT,
    val showWeekday: Boolean = true,
    val weekdayFormat: WeekdayFormat = WeekdayFormat.SHORT,
    val font: AppFont = AppFont.SANS_SERIF
) {
    fun toJson(): String = JSONObject().put("win95", win95).put("lcd", lcd).put("bg", background).put("surface", surface).put("text", text)
        .put("accent", accent).put("highlight", highlight).put("is24", is24).put("dateFormat", dateFormat.name)
        .put("showWeekday", showWeekday).put("weekdayFormat", weekdayFormat.name).put("font", font.name).toString()

    companion object {
        private inline fun <reified T : Enum<T>> en(name: String?, def: T): T = try { if (name == null) def else enumValueOf<T>(name) } catch (_: Exception) { def }

        fun fromJson(s: String?): ClockLook = try {
            val o = JSONObject(s ?: "{}"); val d = ClockLook()
            ClockLook(o.optBoolean("win95", d.win95), o.optBoolean("lcd", d.lcd), o.optInt("bg", d.background), o.optInt("surface", d.surface), o.optInt("text", d.text),
                o.optInt("accent", d.accent), o.optInt("highlight", d.highlight), o.optBoolean("is24", d.is24),
                en(o.optString("dateFormat", null), d.dateFormat), o.optBoolean("showWeekday", d.showWeekday),
                en(o.optString("weekdayFormat", null), d.weekdayFormat), en(o.optString("font", null), d.font))
        } catch (_: Exception) { ClockLook() }

        /** Reads the launcher's extras (all optional). */
        fun fromIntent(i: Intent?, prev: ClockLook): ClockLook {
            if (i == null || !i.hasExtra("look_style")) return prev
            if (i.getStringExtra("look_style") == "lcd") return prev.copy(
                win95 = false, lcd = true, background = 0xFF97C93D.toInt(), surface = 0xFF86B634.toInt(), text = 0xFF0E1806.toInt(),
                accent = 0xFF0E1806.toInt(), highlight = 0xFF0E1806.toInt(),
                is24 = i.getBooleanExtra("look_is24", prev.is24),
                dateFormat = en(i.getStringExtra("look_dateFormat"), prev.dateFormat),
                showWeekday = i.getBooleanExtra("look_showWeekday", prev.showWeekday),
                weekdayFormat = en(i.getStringExtra("look_weekdayFormat"), prev.weekdayFormat),
                font = AppFont.PIXEL
            )
            return prev.copy(
                win95 = i.getStringExtra("look_style") == "win95", lcd = false,
                background = i.getIntExtra("look_bg", prev.background), surface = i.getIntExtra("look_surface", prev.surface),
                text = i.getIntExtra("look_text", prev.text), accent = i.getIntExtra("look_accent", prev.accent),
                highlight = i.getIntExtra("look_highlight", prev.highlight),
                is24 = i.getBooleanExtra("look_is24", prev.is24),
                dateFormat = en(i.getStringExtra("look_dateFormat"), prev.dateFormat),
                showWeekday = i.getBooleanExtra("look_showWeekday", prev.showWeekday),
                weekdayFormat = en(i.getStringExtra("look_weekdayFormat"), prev.weekdayFormat),
                font = en(i.getStringExtra("look_font"), prev.font)
            )
        }

        fun load(ctx: Context) = fromJson(ctx.getSharedPreferences("clock_prefs", Context.MODE_PRIVATE).getString("look", null))
        fun save(ctx: Context, l: ClockLook) = ctx.getSharedPreferences("clock_prefs", Context.MODE_PRIVATE).edit().putString("look", l.toJson()).apply()
    }
}

/** What the clock screens read (same shape the launcher used, so the screens moved over unchanged). */
data class ClockUiState(
    val alarms: List<AlarmItem> = emptyList(),
    val worldClocks: List<WorldClockItem> = emptyList(),
    val clockTab: Int = 0,
    val is24HourFormat: Boolean = true,
    val dateFormat: DateFormatOption = DateFormatOption.SYSTEM_DEFAULT,
    val showWeekday: Boolean = true,
    val weekdayFormat: WeekdayFormat = WeekdayFormat.SHORT,
    val currentFont: AppFont = AppFont.SANS_SERIF
)

object WorldClockStore {
    fun load(ctx: Context): List<WorldClockItem> {
        val s = ctx.getSharedPreferences("clock_prefs", Context.MODE_PRIVATE).getString("world_clocks", null)
            ?: return listOf(WorldClockItem(city = "London", timeZoneId = "Europe/London"),
                WorldClockItem(city = "New York", timeZoneId = "America/New_York"),
                WorldClockItem(city = "Tokyo", timeZoneId = "Asia/Tokyo"))
        return parse(s)
    }
    fun parse(s: String): List<WorldClockItem> = try {
        val a = JSONArray(s)
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            WorldClockItem(id = it.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                city = it.getString("city"), timeZoneId = it.getString("timeZoneId"))
        }
    } catch (_: Exception) { emptyList() }
    fun save(ctx: Context, list: List<WorldClockItem>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("city", it.city).put("timeZoneId", it.timeZoneId)) }
        ctx.getSharedPreferences("clock_prefs", Context.MODE_PRIVATE).edit().putString("world_clocks", a.toString()).apply()
    }
}

val LocalWin95 = staticCompositionLocalOf { false }
val LocalLcd = staticCompositionLocalOf { false }

@Composable
fun win95Fg(): Color = if (LocalWin95.current) Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.onBackground

/** Windows 95 bevel: grey face, light top-left edges, dark bottom-right. */
fun Modifier.win95Bevel(sunken: Boolean = false): Modifier = this.drawBehind {
    val px = 1.dp.toPx().coerceAtLeast(1f); val w = size.width; val h = size.height
    drawRect(Color(0xFFC0C0C0))
    val (a, b, c, d) = if (sunken) listOf(Color.Black, Color(0xFF808080), Color.White, Color(0xFFDFDFDF))
        else listOf(Color.White, Color(0xFFDFDFDF), Color.Black, Color(0xFF808080))
    drawRect(a, Offset.Zero, Size(w, px)); drawRect(a, Offset.Zero, Size(px, h))
    drawRect(c, Offset(0f, h - px), Size(w, px)); drawRect(c, Offset(w - px, 0f), Size(px, h))
    drawRect(b, Offset(px, px), Size(w - 2 * px, px)); drawRect(b, Offset(px, px), Size(px, h - 2 * px))
    drawRect(d, Offset(px, h - 2 * px), Size(w - 2 * px, px)); drawRect(d, Offset(w - 2 * px, px), Size(px, h - 2 * px))
}

/** Focus highlight: Symbian two-tone gradient bar, or a raised grey button in the Windows 95 theme. */
fun Modifier.symbianHighlight(
    isFocused: Boolean,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    unfocusedColor: Color = Color.Transparent
): Modifier = this.composed {
    val win95 = LocalWin95.current
    val lcd = LocalLcd.current
    when {
        isFocused && win95 -> this.win95Bevel()
        isFocused && lcd -> this.background(androidx.compose.material3.MaterialTheme.colorScheme.onBackground)
        isFocused -> {
            val base = androidx.compose.material3.MaterialTheme.colorScheme.secondary
            val top = androidx.compose.ui.graphics.lerp(base, Color.White, 0.4f)
            this.background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to top, 0.5f to top, 0.501f to base, 1f to base), shape)
        }
        else -> this.background(unfocusedColor, shape)
    }
}
