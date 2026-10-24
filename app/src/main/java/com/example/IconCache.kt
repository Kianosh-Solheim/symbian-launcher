package com.example

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.WeakHashMap

/**
 * Converts each app/notification icon to a bitmap once and reuses it. Previously every
 * recomposition rasterised every visible icon at full resolution, which on a low-end phone
 * meant constant allocations and garbage-collection stutter.
 *
 * In the Windows 95 theme every icon is redrawn as a 32×32, 16-colour pixel icon instead.
 * [win95] is Compose state, so icons on screen switch style as soon as the theme changes.
 */
object IconCache {
    private const val MAX_PX = 192
    private val cache = WeakHashMap<Drawable, ImageBitmap>()
    private val retro = WeakHashMap<Drawable, ImageBitmap>()

    var win95 by mutableStateOf(false)
    /** Classic S60 Sky theme: glossy versions of every icon. */
    var s60 by mutableStateOf(false)
    private val glossy = WeakHashMap<Drawable, ImageBitmap>()
    private val owners = WeakHashMap<Drawable, String>()

    /** Remembers which app an icon belongs to, so built-in functions can get the theme's own icon. */
    fun register(d: Drawable, pkg: String) = synchronized(owners) { owners[d] = pkg }
    fun ownerOf(d: Drawable): String? = synchronized(owners) { owners[d] }

    fun get(drawable: Drawable): ImageBitmap {
        if (win95) return synchronized(retro) { retro.getOrPut(drawable) { Win95Pixelizer.convert(drawable).asImageBitmap() } }
        if (s60) return synchronized(glossy) { glossy.getOrPut(drawable) { S60Styler.convert(drawable, ownerOf(drawable)).asImageBitmap() } }
        return synchronized(cache) {
            cache.getOrPut(drawable) {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: MAX_PX
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: MAX_PX
                val scale = minOf(1f, MAX_PX.toFloat() / maxOf(w, h))
                drawable.toBitmap(
                    (w * scale).toInt().coerceAtLeast(1),
                    (h * scale).toInt().coerceAtLeast(1)
                ).asImageBitmap()
            }
        }
    }
}

fun Drawable.toCachedImageBitmap(): ImageBitmap = IconCache.get(this)

/** Turns any icon into a Windows 95-style icon: 32×32 pixels, the 16 classic colours and a black outline. */
object Win95Pixelizer {
    private const val N = 32
    private const val INNER = 30
    private const val SCALE = 4 // stored pre-enlarged so it stays crisp however it is drawn
    private val hsv = FloatArray(3)

    fun convert(d: Drawable): Bitmap = synchronized(hsv) {
        val big = d.toBitmap(INNER * 2, INNER * 2, Bitmap.Config.ARGB_8888)
        val small = Bitmap.createScaledBitmap(big, INNER, INNER, true)
        val off = (N - INNER) / 2
        val px = IntArray(N * N)
        val solid = BooleanArray(N * N)
        for (y in 0 until INNER) for (x in 0 until INNER) {
            val c = small.getPixel(x, y)
            if ((c ushr 24) < 110) continue
            val i = (y + off) * N + x + off
            px[i] = quantize(c); solid[i] = true
        }
        for (y in 0 until N) for (x in 0 until N) {
            val i = y * N + x
            if (solid[i]) continue
            if ((x > 0 && solid[i - 1]) || (x < N - 1 && solid[i + 1]) || (y > 0 && solid[i - N]) || (y < N - 1 && solid[i + N]))
                px[i] = 0xFF000000.toInt()
        }
        val out = Bitmap.createBitmap(px, N, N, Bitmap.Config.ARGB_8888)
        Bitmap.createScaledBitmap(out, N * SCALE, N * SCALE, false)
    }

    /** Nearest classic colour, keeping hues vivid the way 1995 icons were (greys only for unsaturated pixels). */
    private fun quantize(c: Int): Int {
        android.graphics.Color.colorToHSV(c, hsv)
        val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
        val r = (c shr 16 and 0xFF) / 255f; val g = (c shr 8 and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        if (v < 0.22f) return 0xFF000000.toInt()
        if (s < 0.28f || (s < 0.45f && v > 0.8f)) {
            val l = 0.3f * r + 0.59f * g + 0.11f * b
            return when { l < 0.22f -> 0xFF000000; l < 0.55f -> 0xFF808080; l < 0.86f -> 0xFFC0C0C0; else -> 0xFFFFFFFF }.toInt()
        }
        val k = if (v > 0.62f) 0xFF else 0x80
        val (rr, gg, bb) = when (((h + 30f) / 60f).toInt() % 6) {
            0 -> Triple(1, 0, 0); 1 -> Triple(1, 1, 0); 2 -> Triple(0, 1, 0)
            3 -> Triple(0, 1, 1); 4 -> Triple(0, 0, 1); else -> Triple(1, 0, 1)
        }
        return (0xFF shl 24) or ((rr * k) shl 16) or ((gg * k) shl 8) or (bb * k)
    }
}
