package com.example

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

/**
 * Turns a flat icon into a chunky 3D object in the style of the later Symbian icon sets:
 * the face is turned slightly to the side, the shape gets a silvery extruded edge to the
 * right and bottom, a light rim on the top-left, soft lighting and a drop shadow.
 */
object Belle3D {
    fun render(src: Bitmap, out: Int = 128): Bitmap {
        val inner = (out * 0.74f)
        val k = minOf(inner / src.width, inner / src.height)
        val w = maxOf(1, (src.width * k).toInt()); val h = maxOf(1, (src.height * k).toInt())
        val fp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // 1. Face, turned a little so its right edge recedes, with livelier colours.
        val face = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val m = Matrix()
        m.setPolyToPoly(floatArrayOf(0f, 0f, src.width.toFloat(), 0f, src.width.toFloat(), src.height.toFloat(), 0f, src.height.toFloat()), 0,
            floatArrayOf(0f, 0f, w * 0.93f, h * 0.045f, w * 0.93f, h * 0.955f, 0f, h.toFloat()), 0, 4)
        Canvas(face).drawBitmap(src, m, Paint(fp).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.2f) }) })
        val alpha = face.extractAlpha()
        val ox = (out - w) / 2f - out * 0.05f
        val oy = (out - h) / 2f - out * 0.045f
        val depth = out * 0.075f
        val avg = average(face)

        // 2. Extruded edge: the outline stacked towards bottom-right, shaded silver tinted by the icon.
        val side = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
        val sc = Canvas(side)
        val black = Paint(fp).apply { color = 0xFF000000.toInt() }
        val steps = (depth * 2).toInt().coerceAtLeast(4)
        for (i in steps downTo 1) { val t = depth * i / steps; sc.drawBitmap(alpha, ox + t * 0.95f, oy + t * 0.75f, black) }
        val top = mix(0xFFF7F8FA.toInt(), avg, 0.22f)
        val bot = mix(0xFF868A92.toInt(), darker(avg), 0.3f)
        sc.drawRect(0f, 0f, out.toFloat(), out.toFloat(), Paint().apply {
            shader = LinearGradient(ox, oy, ox + w * 0.4f, oy + h + depth, top, bot, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        })

        val res = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
        val c = Canvas(res)
        // 3. Drop shadow under the whole object.
        val off = IntArray(2)
        val sh = side.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(out * 0.03f, BlurMaskFilter.Blur.NORMAL) }, off)
        c.drawBitmap(sh, off[0] + out * 0.02f, off[1] + out * 0.04f, Paint(fp).apply { color = 0x66000000 })
        c.drawBitmap(side, 0f, 0f, fp)
        // 4. Seam between face and edge, then a light rim on the top-left.
        c.drawBitmap(alpha, ox + 0.9f, oy + 0.7f, Paint(fp).apply { color = 0x50000000 })
        c.drawBitmap(alpha, ox - out * 0.007f, oy - out * 0.007f, Paint(fp).apply { color = 0xD8FFFFFF.toInt() })
        // 5. Face with soft light from the top-left.
        val lit = face.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(lit).drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w * 0.7f, h.toFloat(), intArrayOf(0x48FFFFFF, 0x00FFFFFF, 0x30000000), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        })
        c.drawBitmap(lit, ox, oy, fp)
        return res
    }

    private fun average(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 16, 16, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (y in 0 until 16) for (x in 0 until 16) { val p = s.getPixel(x, y); val a = p ushr 24; if (a > 100) { r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; bl += p and 0xFF; n++ } }
        if (n == 0L) return 0xFF888888.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }
    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    private fun darker(c: Int) = mix(c, 0xFF000000.toInt(), 0.45f)
}
