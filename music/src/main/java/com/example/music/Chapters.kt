package com.example.music

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

data class Chapter(val title: String, val startMs: Long)

/**
 * Reads chapter marks from MP4 audiobooks (.m4b/.m4a): the Nero "chpl" list most tools write,
 * or Apple's QuickTime chapter text track.
 */
object Chapters {
    private val cache = HashMap<String, List<Chapter>>()

    fun read(ctx: Context, uri: Uri, key: String): List<Chapter> = synchronized(cache) {
        cache[key]?.let { return it }
        val r = try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { parse(it) }
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
        cache[key] = r
        r
    }

    private class Box(val type: String, val start: Long, val dataStart: Long, val end: Long)

    private fun boxes(ch: FileChannel, from: Long, to: Long): List<Box> {
        val out = ArrayList<Box>()
        var pos = from
        val hdr = ByteBuffer.allocate(16)
        while (pos + 8 <= to) {
            hdr.clear(); hdr.limit(8); ch.read(hdr, pos); hdr.flip()
            if (hdr.remaining() < 8) break
            var size = hdr.int.toLong() and 0xFFFFFFFFL
            val type = String(ByteArray(4).also { hdr.get(it) }, Charsets.ISO_8859_1)
            var header = 8L
            if (size == 1L) {
                hdr.clear(); hdr.limit(8); ch.read(hdr, pos + 8); hdr.flip(); size = hdr.long; header = 16
            } else if (size == 0L) size = to - pos
            if (size < header) break
            out += Box(type, pos, pos + header, minOf(pos + size, to))
            pos += size
        }
        return out
    }

    private fun child(ch: FileChannel, b: Box, type: String, skip: Long = 0) = boxes(ch, b.dataStart + skip, b.end).firstOrNull { it.type == type }
    private fun bytes(ch: FileChannel, b: Box): ByteBuffer {
        val n = (b.end - b.dataStart).toInt().coerceAtMost(8 * 1024 * 1024)
        val buf = ByteBuffer.allocate(n); ch.read(buf, b.dataStart); buf.flip(); return buf
    }

    private fun parse(ch: FileChannel): List<Chapter> {
        val moov = boxes(ch, 0, ch.size()).firstOrNull { it.type == "moov" } ?: return emptyList()
        // 1. Nero chapters: moov/udta/chpl
        child(ch, moov, "udta")?.let { udta ->
            child(ch, udta, "chpl")?.let { chpl ->
                val b = bytes(ch, chpl)
                val version = b.get().toInt(); b.position(b.position() + 3)
                if (version != 0) b.position(b.position() + 4)
                val n = b.get().toInt() and 0xFF
                val list = ArrayList<Chapter>()
                repeat(n) {
                    if (b.remaining() < 9) return@repeat
                    val t = b.long / 10_000
                    val len = b.get().toInt() and 0xFF
                    val title = ByteArray(minOf(len, b.remaining())).also { b.get(it) }.toString(Charsets.UTF_8)
                    list += Chapter(title.ifBlank { "Chapter ${list.size + 1}" }, t)
                }
                if (list.size > 1) return list
            }
        }
        // 2. QuickTime chapter track: the track referenced by tref/chap, else a text track.
        val traks = boxes(ch, moov.dataStart, moov.end).filter { it.type == "trak" }
        val chapIds = HashSet<Int>()
        traks.forEach { t -> child(ch, t, "tref")?.let { tr -> child(ch, tr, "chap")?.let { c -> val b = bytes(ch, c); while (b.remaining() >= 4) chapIds += b.int } } }
        fun trackId(t: Box): Int? = child(ch, t, "tkhd")?.let { val b = bytes(ch, it); val v = b.get().toInt(); b.position(if (v == 1) 20 else 12); b.int }
        fun handler(t: Box): String? = child(ch, t, "mdia")?.let { m -> child(ch, m, "hdlr")?.let { h -> val b = bytes(ch, h); b.position(8); String(ByteArray(4).also { b.get(it) }, Charsets.ISO_8859_1) } }
        val trak = traks.firstOrNull { trackId(it) in chapIds } ?: traks.firstOrNull { handler(it) == "text" } ?: return emptyList()
        val mdia = child(ch, trak, "mdia") ?: return emptyList()
        val timescale = child(ch, mdia, "mdhd")?.let { val b = bytes(ch, it); val v = b.get().toInt(); b.position(if (v == 1) 20 else 12); (b.int.toLong() and 0xFFFFFFFFL) } ?: return emptyList()
        val stbl = child(ch, mdia, "minf")?.let { child(ch, it, "stbl") } ?: return emptyList()

        val durations = ArrayList<Long>()
        child(ch, stbl, "stts")?.let { val b = bytes(ch, it); b.position(4); repeat(b.int) { val cnt = b.int; val d = b.int.toLong() and 0xFFFFFFFFL; repeat(cnt) { durations += d } } }
        val sizes = ArrayList<Int>()
        child(ch, stbl, "stsz")?.let { val b = bytes(ch, it); b.position(4); val fixed = b.int; val n = b.int; repeat(n) { sizes += if (fixed != 0) fixed else b.int } }
        val offsets = ArrayList<Long>()
        (child(ch, stbl, "stco") ?: child(ch, stbl, "co64"))?.let { bx -> val b = bytes(ch, bx); b.position(4); val n = b.int; repeat(n) { offsets += if (bx.type == "co64") b.long else (b.int.toLong() and 0xFFFFFFFFL) } }
        val stsc = ArrayList<Pair<Int, Int>>() // firstChunk(1-based) → samplesPerChunk
        child(ch, stbl, "stsc")?.let { val b = bytes(ch, it); b.position(4); repeat(b.int) { stsc += b.int to b.int.also { _ -> b.int } } }
        if (sizes.isEmpty() || offsets.isEmpty()) return emptyList()

        // Sample file offsets from chunk layout.
        val sampleOffsets = ArrayList<Long>()
        var s = 0
        for (chunk in offsets.indices) {
            val per = stsc.lastOrNull { it.first <= chunk + 1 }?.second ?: 1
            var off = offsets[chunk]
            repeat(per) { if (s < sizes.size) { sampleOffsets += off; off += sizes[s]; s++ } }
        }
        val list = ArrayList<Chapter>()
        var time = 0L
        for (i in sampleOffsets.indices) {
            val buf = ByteBuffer.allocate(minOf(sizes[i], 1024)); ch.read(buf, sampleOffsets[i]); buf.flip()
            val title = if (buf.remaining() >= 2) { val len = buf.short.toInt() and 0xFFFF; ByteArray(minOf(len, buf.remaining())).also { buf.get(it) }.toString(Charsets.UTF_8) } else ""
            list += Chapter(title.ifBlank { "Chapter ${i + 1}" }, time * 1000 / timescale)
            time += durations.getOrElse(i) { 0L }
        }
        return if (list.size > 1) list else emptyList()
    }
}
