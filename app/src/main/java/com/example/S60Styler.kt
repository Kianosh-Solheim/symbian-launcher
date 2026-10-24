package com.example

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asAndroidPath
import androidx.core.graphics.drawable.toBitmap

/**
 * Gives every app icon the Classic S60 Sky look: richer colours, a soft drop shadow and a white
 * gloss across the top, following each icon's own shape. The app's own artwork is kept, so apps
 * stay recognisable. Built-in phone functions (calendar, calculator, messages, clock, phone,
 * contacts, settings, camera) get the theme's own drawn icons instead.
 */
object S60Styler {
    private const val OUT = 128

    private val generic = mapOf(
        "com.google.android.calendar" to "cal", "com.android.calendar" to "cal", "org.fossify.calendar" to "cal", "org.lineageos.etar" to "cal",
        "com.google.android.calculator" to "calc", "com.android.calculator2" to "calc", "org.lineageos.recorder" to null,
        "com.google.android.apps.messaging" to "msg", "com.android.messaging" to "msg", "com.android.mms" to "msg",
        "no.kianosh.symbianclock" to "clock", "com.android.deskclock" to "clock", "com.google.android.deskclock" to "clock",
        "com.android.dialer" to "call", "com.google.android.dialer" to "call",
        "com.android.contacts" to "person", "com.google.android.contacts" to "person",
        "com.android.settings" to "settings",
        "org.lineageos.aperture" to "camera", "com.android.camera2" to "camera", "com.android.camera" to "camera"
    )

    fun convert(d: Drawable, pkg: String?): Bitmap {
        generic[pkg]?.let { name -> S60Icons.bitmap(name, OUT)?.let { return it } }
        val inner = (OUT * 0.84f).toInt()
        val off = (OUT - inner) / 2
        val src = d.toBitmap(inner, inner, Bitmap.Config.ARGB_8888)
        val out = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)

        // Soft shadow below-right, from the icon's own outline.
        val shadow = src.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(OUT * 0.03f, BlurMaskFilter.Blur.NORMAL) }, IntArray(2))
        c.drawBitmap(shadow, off + OUT * 0.02f - 0f, off + OUT * 0.035f, Paint().apply { color = 0x55000000; isFilterBitmap = true })

        // The icon itself, a little more saturated and contrasty like the old phone themes.
        val boost = ColorMatrix().apply { setSaturation(1.25f) }
        val contrast = ColorMatrix(floatArrayOf(1.08f, 0f, 0f, 0f, -8f, 0f, 1.08f, 0f, 0f, -8f, 0f, 0f, 1.08f, 0f, -8f, 0f, 0f, 0f, 1f, 0f))
        boost.postConcat(contrast)
        val layer = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        lc.drawBitmap(src, off.toFloat(), off.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(boost) })

        // Gloss: white fading out over the top half, curved at the bottom, clipped to the icon's pixels.
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, off.toFloat(), 0f, OUT * 0.55f, 0x8CFFFFFF.toInt(), 0x0DFFFFFF, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        lc.drawOval(RectF(-OUT * 0.25f, -OUT * 0.55f, OUT * 1.25f, OUT * 0.52f), gloss)
        // Faint dark edge at the bottom for depth.
        val shade = Paint().apply {
            shader = LinearGradient(0f, OUT * 0.6f, 0f, (off + inner).toFloat(), 0x00000000, 0x30000000, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        lc.drawRect(0f, OUT * 0.6f, OUT.toFloat(), OUT.toFloat(), shade)

        c.drawBitmap(layer, 0f, 0f, null)
        return out
    }
}
