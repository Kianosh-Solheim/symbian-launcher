package com.example

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/**
 * Icons for the Classic Green LCD theme: 40×40 pixels in four ink shades with an ordered
 * (checkerboard-style) dither for in-between tones — pixelated, but with enough dots to keep detail.
 * Tones are stored "negative" because the LCD screen filter turns brightness around.
 */
object LcdPixelizer {
    private const val N = 40
    private const val SCALE = 3
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun convert(d: Drawable): Bitmap = convert(d.toBitmap(N * 2, N * 2, Bitmap.Config.ARGB_8888))

    fun convert(src: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(src, N, N, true)
        val px = IntArray(N * N); small.getPixels(px, 0, N, 0, 0, N, N)
        for (y in 0 until N) for (x in 0 until N) {
            val i = y * N + x; val c = px[i]
            if ((c ushr 24) < 100) { px[i] = 0; continue }
            val l = (0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)) / 255f
            val ink = 1f - l                                   // dark detail → strong ink
            val t = (bayer[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f - 0.5f
            val level = ((ink * 3f + t * 0.9f).let { Math.round(it) }).coerceIn(0, 3)
            val v = level * 85                                 // 0, 85, 170, 255
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val out = Bitmap.createBitmap(px, N, N, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(out, N * SCALE, N * SCALE, false)
    }

    private val builtIn = HashMap<String, ImageBitmap>()
    fun builtIn(name: String): ImageBitmap? = synchronized(builtIn) {
        builtIn[name] ?: S60Icons.bitmap(name, 160)?.let { convert(it).asImageBitmap() }?.also { builtIn[name] = it }
    }
}

/** A launcher icon (Messages, Calendar …) drawn as a detailed LCD pixel icon. */
@Composable
fun LcdIcon(name: String, modifier: Modifier = Modifier) {
    val bmp = remember(name) { LcdPixelizer.builtIn(name) } ?: return
    Image(bmp, contentDescription = null, modifier = modifier.size(32.dp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
}
