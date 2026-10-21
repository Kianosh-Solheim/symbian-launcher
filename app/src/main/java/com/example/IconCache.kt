package com.example

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.WeakHashMap

/**
 * Converts each app/notification icon to a bitmap once and reuses it. Previously every
 * recomposition rasterised every visible icon at full resolution, which on a low-end phone
 * meant constant allocations and garbage-collection stutter.
 */
object IconCache {
    private const val MAX_PX = 192
    private val cache = WeakHashMap<Drawable, ImageBitmap>()

    fun get(drawable: Drawable): ImageBitmap = synchronized(cache) {
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

fun Drawable.toCachedImageBitmap(): ImageBitmap = IconCache.get(this)
