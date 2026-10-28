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
import android.graphics.RectF
import android.graphics.Shader

/**
 * Turns a flat icon into a chunky 3D object in the style of the later Symbian icon sets:
 * the face is turned so its left edge recedes, a polished chrome side shows on the right
 * (bright near the face, sky reflection on top, a dark horizon band), a reflective silver
 * frame runs round the face, light comes from the top-left and a soft shadow falls below-right.
 */
object Belle3D {
    fun render(src: Bitmap, out: Int = 128): Bitmap {
        val big = draw(src, out * 2)
        return Bitmap.createScaledBitmap(big, out, out, true)
    }

    private fun draw(src: Bitmap, S: Int): Bitmap {
        val fp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val inner = S * 0.70f
        val k = minOf(inner / src.width, inner / src.height)
        val w = maxOf(2f, src.width * k); val h = maxOf(2f, src.height * k)
        val fw = w * 0.84f

        // 1. Face, turned so the left edge goes away from the viewer.
        val face = Bitmap.createBitmap(fw.toInt() + 1, h.toInt() + 1, Bitmap.Config.ARGB_8888)
        val m = Matrix()
        val sw = src.width.toFloat(); val sh = src.height.toFloat()
        m.setPolyToPoly(floatArrayOf(0f, 0f, sw, 0f, sw, sh, 0f, sh), 0,
            floatArrayOf(0f, h * 0.08f, fw, 0f, fw, h, 0f, h * 0.92f), 0, 4)
        Canvas(face).drawBitmap(src, m, Paint(fp).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.15f) }) })
        val alpha = face.extractAlpha()
        val depth = S * 0.095f
        val ox = (S - fw - depth) / 2f
        val oy = (S - h) / 2f - S * 0.01f

        // 2. Chrome side: the outline stacked towards the back-right, shrinking a little with distance.
        val side = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val sc = Canvas(side)
        val n = depth.toInt().coerceAtLeast(6)
        val sp = Paint(fp)
        for (i in n downTo 1) {
            val t = i.toFloat() / n
            val s = 1f - 0.08f * t
            val ww = fw * s; val hh = h * s
            val x = ox + depth * t + (fw - ww); val y = oy + (h - hh) / 2f + depth * 0.12f * t
            sp.color = metal(t)
            sc.drawBitmap(alpha, null, RectF(x, y, x + ww, y + hh), sp)
        }
        // Reflection: sky-tinted brightness on top, a sharp dark horizon just below the middle.
        sc.drawRect(0f, 0f, S.toFloat(), S.toFloat(), Paint().apply {
            shader = LinearGradient(0f, oy, 0f, oy + h,
                intArrayOf(0x40EEF4FF, 0x14EEF4FF, 0x00000000, 0x6A000000, 0x3C000000, 0x10FFFFFF),
                floatArrayOf(0f, 0.12f, 0.44f, 0.47f, 0.62f, 1f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        })

        // Silhouette of the whole object (for shadow and outline).
        val sil = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        Canvas(sil).apply { drawBitmap(side, 0f, 0f, null); drawBitmap(alpha, ox, oy, Paint().apply { color = 0xFF000000.toInt() }) }

        val res = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(res)
        // 3. Soft shadow below-right, then a thin dark outline round everything.
        val off = IntArray(2)
        val shadow = sil.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(S * 0.025f, BlurMaskFilter.Blur.NORMAL) }, off)
        c.drawBitmap(shadow, off[0] + S * 0.02f, off[1] + S * 0.045f, Paint(fp).apply { color = 0x800A1E3C.toInt() })
        val silA = sil.extractAlpha()
        dilate(c, silA, 0f, 0f, S * 0.006f, Paint(fp).apply { color = 0x96283038.toInt() })
        c.drawBitmap(side, 0f, 0f, fp)

        // 4. Reflective silver frame round the face: chrome bands running down the face diagonally.
        val rim = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val rc = Canvas(rim)
        dilate(rc, alpha, ox, oy, S * 0.0105f, Paint(fp).apply { color = 0xFF000000.toInt() })
        fun g(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or minOf(255, v + 5)
        rc.drawRect(0f, 0f, S.toFloat(), S.toFloat(), Paint().apply {
            shader = LinearGradient(ox, oy, ox + fw * 0.3f, oy + h * 0.7f / 0.7f,
                intArrayOf(g(255), g(250), g(215), g(95), g(150), g(240), g(205), g(120)),
                floatArrayOf(0f, 0.22f, 0.40f, 0.46f, 0.52f, 0.70f, 0.85f, 1f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        })
        c.drawBitmap(rim, 0f, 0f, fp)
        // Bright seam where the face meets the side.
        c.drawBitmap(alpha, ox + S * 0.004f, oy, Paint(fp).apply { color = 0xFFFFFFFF.toInt() })

        // 5. Face, lit from the top-left.
        val lit = face.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(lit).drawRect(0f, 0f, fw + 1, h + 1, Paint().apply {
            shader = LinearGradient(0f, 0f, fw * 0.55f, h, intArrayOf(0x30FFFFFF, 0x00FFFFFF, 0x40000000), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        })
        c.drawBitmap(lit, ox, oy, fp)
        return res
    }

    /** Draws an alpha mask grown by r pixels (stamped round a circle). */
    private fun dilate(c: Canvas, a: Bitmap, x: Float, y: Float, r: Float, p: Paint) {
        for (i in 0 until 12) {
            val ang = i * Math.PI / 6
            c.drawBitmap(a, x + (r * Math.cos(ang)).toFloat(), y + (r * Math.sin(ang)).toFloat(), p)
        }
        c.drawBitmap(a, x, y, p)
    }

    /** Polished-metal shade across the depth of the side: near-white by the face, darker towards the back. */
    private fun metal(t: Float): Int {
        val xs = floatArrayOf(0f, 0.3f, 0.42f, 0.75f, 0.9f, 1f)
        val vs = floatArrayOf(250f, 246f, 206f, 170f, 120f, 92f)
        var v = vs.last()
        for (i in 0 until xs.size - 1) if (t <= xs[i + 1]) { v = vs[i] + (vs[i + 1] - vs[i]) * (t - xs[i]) / (xs[i + 1] - xs[i]); break }
        val g = v.toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (g shl 16) or (g shl 8) or minOf(255, g + 4)
    }
}
