package com.example.music

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Album / book covers: the album art MediaStore knows about, else the picture embedded in the file, else cover.jpg next to it. */
object Covers {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()

    suspend fun load(ctx: Context, t: Track, px: Int): Bitmap? {
        val key = "${t.albumId}:${t.folder}:$px"
        cache.get(key)?.let { return it }
        if (key in missing) return null
        val bmp = withContext(Dispatchers.IO) { fromStore(ctx, t, px) ?: embedded(ctx, t.uri, px) ?: folderImage(t, px) }
        if (bmp != null) cache.put(key, bmp) else synchronized(missing) { missing += key }
        return bmp
    }

    private fun fromStore(ctx: Context, t: Track, px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            ctx.contentResolver.loadThumbnail(ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albums"), t.albumId), Size(px, px), null)
        } catch (_: Exception) {
            try { ctx.contentResolver.loadThumbnail(t.uri, Size(px, px), null) } catch (_: Exception) { null }
        }
    }

    private fun embedded(ctx: Context, uri: Uri, px: Int): Bitmap? = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            r.embeddedPicture?.let { decode(it, px) }
        } finally { r.release() }
    } catch (_: Exception) { null }

    private fun folderImage(t: Track, px: Int): Bitmap? {
        val dir = File(t.folder)
        val names = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "albumart.jpg")
        val f = names.map { File(dir, it) }.firstOrNull { runCatching { it.canRead() }.getOrDefault(false) } ?: return null
        return try { decode(f.readBytes(), px) } catch (_: Exception) { null }
    }

    private fun decode(bytes: ByteArray, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }
}
