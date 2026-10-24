package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Your own pictures as app icons (picked on the phone or uploaded from Desktop Mode). A plain
 * white or single-colour background around the picture is cut away by the S60 theme, which then
 * adds its gloss and shadow like for any other icon.
 */
object CustomIcons {
    /** Bumped whenever an icon is added or removed, so the app list reloads. */
    val version = MutableStateFlow(0)

    private fun dir(ctx: Context) = File(ctx.filesDir, "custom_icons").apply { mkdirs() }
    private fun file(ctx: Context, pkg: String) = File(dir(ctx), pkg.replace(Regex("[^A-Za-z0-9._]"), "_") + ".png")

    fun has(ctx: Context, pkg: String) = file(ctx, pkg).exists()

    fun load(ctx: Context, pkg: String): Drawable? {
        val f = file(ctx, pkg); if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)?.let { BitmapDrawable(ctx.resources, it) }
    }

    /** Stores [bytes] (any image) as the icon for [pkg], scaled to at most 256 px. */
    fun save(ctx: Context, pkg: String, bytes: ByteArray): Boolean {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0) return false
        var sample = 1; while (maxOf(opts.outWidth, opts.outHeight) / sample > 1024) sample *= 2
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val k = minOf(1f, 256f / maxOf(src.width, src.height))
        val b = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true) else src
        file(ctx, pkg).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        version.value++
        return true
    }

    fun remove(ctx: Context, pkg: String) { if (file(ctx, pkg).delete()) version.value++ }

    fun list(ctx: Context): List<String> = dir(ctx).listFiles()?.map { it.name.removeSuffix(".png") } ?: emptyList()
}
