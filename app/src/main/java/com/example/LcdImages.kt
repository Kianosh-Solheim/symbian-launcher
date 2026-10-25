package com.example

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation

/**
 * Photos (contact pictures, avatars …) in the Classic Green LCD theme: shrunk to a low LCD
 * resolution and drawn in four ink shades with an ordered checkerboard dither, like pictures on old
 * handheld screens. Stored "negative" because the LCD screen filter turns brightness around.
 */
object LcdDither {
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun convert(src: Bitmap, across: Int = 72, scale: Int = 4): Bitmap {
        val w = across; val h = (src.height.toFloat() / src.width * across).toInt().coerceIn(8, across * 3)
        val small = Bitmap.createScaledBitmap(src.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
        val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
        // Stretch contrast to the picture's own range so faces don't turn into grey mush.
        var lo = 1f; var hi = 0f
        val lum = FloatArray(px.size) { val c = px[it]; ((0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)) / 255f).also { v -> if (v < lo) lo = v; if (v > hi) hi = v } }
        val range = (hi - lo).coerceAtLeast(0.2f)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if ((px[i] ushr 24) < 60) { px[i] = 0; continue }
            val l = ((lum[i] - lo) / range).coerceIn(0f, 1f)
            val ink = 1f - l
            val t = (bayer[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f - 0.5f
            val v = (Math.round(ink * 3f + t).coerceIn(0, 3)) * 85
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(out, w * scale, h * scale, false)
    }

    val transformation = object : Transformation {
        override val cacheKey = "lcd-dither-v1"
        override suspend fun transform(input: Bitmap, size: Size): Bitmap = convert(input)
    }
}

/** Coil's AsyncImage, but dithered LCD-style in the Classic Green LCD theme. */
@Composable
fun ThemedAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val lcd = LocalLcd.current
    if (!lcd) { coil.compose.AsyncImage(model = model, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale); return }
    val context = LocalContext.current
    val req = remember(model) { ImageRequest.Builder(context).data(model).allowHardware(false).transformations(LcdDither.transformation).build() }
    coil.compose.AsyncImage(model = req, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale, filterQuality = FilterQuality.None)
}
