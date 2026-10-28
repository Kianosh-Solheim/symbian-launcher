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
 * Gives every app icon the Classic S60 Sky look: the emblem freed from its square/round tile,
 * richer colours, a soft drop shadow and a white gloss across the top, following the emblem's shape. The app's own artwork is kept, so apps
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
        "no.posten.sporing.controller" to "posten",
        "org.lineageos.jelly" to "globe", "com.android.browser" to "globe",
        "com.android.documentsui" to "folder", "com.google.android.documentsui" to "folder", "org.fossify.filemanager" to "folder",
        "org.lineageos.glimpse" to "gallery", "com.android.gallery3d" to "gallery", "org.fossify.gallery" to "gallery",
        "org.lineageos.eleven" to "music", "org.lineageos.twelve" to "music", "org.fossify.musicplayer" to "music",
        "org.fossify.notes" to "notes", "com.android.notes" to "notes",
        "org.lineageos.aperture" to "camera", "com.android.camera2" to "camera", "com.android.camera" to "camera"
    )

    /**
     * The app's emblem without its background tile: for adaptive icons the foreground layer
     * (Snapchat → the ghost); for older icons, a flat border colour is cut away. If the emblem is
     * plain white (it relied on the tile for colour), it is coloured with the tile's colour instead.
     */
    /** The app's emblem without its tile (see [shapeOnly]); used by the LCD theme too. */
    fun emblem(d: Drawable): Bitmap = shapeOnly(d)

    private fun shapeOnly(d: Drawable): Bitmap {
        val n = 216
        if (d is android.graphics.drawable.AdaptiveIconDrawable && d.foreground != null) {
            val fg = d.foreground.toBitmap(n, n, Bitmap.Config.ARGB_8888)
            val crop = (n * 0.13f).toInt() // outside the visible mask area
            var emblem = trim(Bitmap.createBitmap(fg, crop, crop, n - 2 * crop, n - 2 * crop))
            val bgColor = d.background?.let { average(it.toBitmap(48, 48, Bitmap.Config.ARGB_8888)) }
            if (emblem != null) {
                if (bgColor != null && mostlyWhite(emblem)) emblem = tintWhite(emblem, bgColor)
                return emblem
            }
        }
        val full = d.toBitmap(n, n, Bitmap.Config.ARGB_8888)
        return trim(cutBorder(full)) ?: full
    }

    /** Scales [b] to fit a [size]×[size] square, centred, keeping proportions. */
    private fun fit(b: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val k = size.toFloat() / maxOf(b.width, b.height)
        val w = b.width * k; val h = b.height * k
        Canvas(out).drawBitmap(b, null, RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Crops away fully transparent margins (null if nothing is left). */
    private fun trim(b: Bitmap): Bitmap? {
        val w = b.width; val h = b.height; val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var bt = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 24) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > bt) bt = y }
        if (r < 0) return null
        val pad = 2
        l = maxOf(0, l - pad); t = maxOf(0, t - pad); r = minOf(w - 1, r + pad); bt = minOf(h - 1, bt + pad)
        return Bitmap.createBitmap(b, l, t, r - l + 1, bt - t + 1)
    }

    private fun average(b: Bitmap): Int {
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val c = b.getPixel(x, y); if ((c ushr 24) < 128) continue
            r += c shr 16 and 0xFF; g += c shr 8 and 0xFF; bl += c and 0xFF; n++
        }
        if (n == 0L) return 0xFF808080.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }

    private fun isWhitish(c: Int): Boolean {
        val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
        return minOf(r, g, b) > 200 && maxOf(r, g, b) - minOf(r, g, b) < 30
    }

    private fun mostlyWhite(b: Bitmap): Boolean {
        var white = 0; var solid = 0
        for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) {
            val c = b.getPixel(x, y); if ((c ushr 24) < 128) continue
            solid++; if (isWhitish(c)) white++
        }
        return solid > 0 && white > solid * 0.6
    }

    /** Paints the white parts in [color], shaded slightly by the original brightness. */
    private fun tintWhite(b: Bitmap, color: Int): Bitmap {
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width; val h = out.height; val px = IntArray(w * h); out.getPixels(px, 0, w, 0, 0, w, h)
        val cr = color shr 16 and 0xFF; val cg = color shr 8 and 0xFF; val cb = color and 0xFF
        for (i in px.indices) {
            val c = px[i]; if ((c ushr 24) == 0 || !isWhitish(c)) continue
            val lum = ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 765f
            px[i] = (c and 0xFF000000.toInt()) or ((cr * lum).toInt() shl 16) or ((cg * lum).toInt() shl 8) or (cb * lum).toInt()
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Older square icons: if the border is one flat colour, cut that colour away from the edges inwards. */
    private fun cutBorder(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height; val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        val probe = listOf(px[w * (h / 2) + 2], px[w * 2 + w / 2], px[w * (h / 2) + w - 3], px[w * (h - 3) + w / 2])
        if (probe.any { (it ushr 24) < 200 }) return b // already has a shaped outline
        val ref = probe[0]
        fun near(c: Int) = (c ushr 24) > 0 && Math.abs((c shr 16 and 0xFF) - (ref shr 16 and 0xFF)) + Math.abs((c shr 8 and 0xFF) - (ref shr 8 and 0xFF)) + Math.abs((c and 0xFF) - (ref and 0xFF)) < 60
        if (probe.any { !near(it) }) return b // busy picture: keep as is
        val seen = BooleanArray(w * h); val stack = ArrayDeque<Int>()
        for (x in 0 until w) { stack.add(x); stack.add((h - 1) * w + x) }
        for (y in 0 until h) { stack.add(y * w); stack.add(y * w + w - 1) }
        var removed = 0
        while (stack.isNotEmpty()) {
            val i = stack.removeLast(); if (seen[i]) continue; seen[i] = true
            val c = px[i]
            if ((c ushr 24) < 40) { px[i] = 0; } else if (near(c)) { px[i] = 0; removed++ } else continue
            val x = i % w; val y = i / w
            if (x > 0) stack.add(i - 1); if (x < w - 1) stack.add(i + 1); if (y > 0) stack.add(i - w); if (y < h - 1) stack.add(i + w)
        }
        if (removed > w * h * 0.85) return b // nothing would be left
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    fun convert(d: Drawable, pkg: String?): Bitmap {
        if (pkg?.startsWith("custom:") != true) generic[pkg]?.let { name -> S60Icons.bitmap3d(name, OUT)?.let { return it } }
        return Belle3D.render(shapeOnly(d), OUT)
    }
}
