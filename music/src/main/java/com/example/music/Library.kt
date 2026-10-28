package com.example.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val trackNo: Int,
) {
    val folder: String get() = File(path).parent ?: ""
    val fileName: String get() = File(path).nameWithoutExtension
}

data class Album(val id: Long, val title: String, val artist: String, val tracks: List<Track>)
data class Artist(val name: String, val albums: List<Album>) { val trackCount get() = albums.sumOf { it.tracks.size } }

/** A book is one .m4b/.m4a file (chapters inside) or a folder of files (one chapter per file). */
data class Book(val id: String, val title: String, val author: String, val files: List<Track>, val singleFile: Boolean) {
    val durationMs get() = files.sumOf { it.durationMs }
    val cover: Track get() = files.first()
}

enum class FolderRole { MUSIC, BOOKS, HIDDEN }

data class Library(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val books: List<Book> = emptyList(),
    val folders: Map<String, FolderRole> = emptyMap(),
    val loaded: Boolean = false,
)

object LibraryScanner {
    private const val PREFS = "music_lib"

    fun roleOverrides(ctx: Context): Map<String, FolderRole> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val o = try { JSONObject(p.getString("roles", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        return o.keys().asSequence().mapNotNull { k -> runCatching { k to FolderRole.valueOf(o.getString(k)) }.getOrNull() }.toMap()
    }

    fun setRole(ctx: Context, folder: String, role: FolderRole) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val o = try { JSONObject(p.getString("roles", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        o.put(folder, role.name)
        p.edit().putString("roles", o.toString()).apply()
    }

    /** Guess what a folder holds when the user hasn't said. */
    private fun guess(folder: String, files: List<Track>): FolderRole {
        val f = folder.lowercase()
        val hidden = listOf("/ringtones", "/notifications", "/alarms", "/recordings", "/call", "/whatsapp", "/telegram", "/voice")
        if (hidden.any { f.contains(it) }) return FolderRole.HIDDEN
        val bookWords = listOf("audiobook", "audio book", "lydbok", "lydbøker", "lydboker", "hörbuch", "/books")
        if (bookWords.any { f.contains(it) }) return FolderRole.BOOKS
        if (files.any { it.path.endsWith(".m4b", true) }) return FolderRole.BOOKS
        return FolderRole.MUSIC
    }

    fun scan(ctx: Context): Library {
        val tracks = ArrayList<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TRACK
        )
        val sel = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0"
        try {
            ctx.contentResolver.query(uri, proj, sel, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val path = c.getString(6) ?: continue
                    val dur = c.getLong(5)
                    if (dur in 1..9_999) continue // skip short sound effects
                    fun clean(s: String?) = s?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    tracks += Track(
                        id, ContentUris.withAppendedId(uri, id),
                        clean(c.getString(1)) ?: File(path).nameWithoutExtension,
                        clean(c.getString(2)) ?: "Unknown artist",
                        clean(c.getString(3)) ?: "Unknown album",
                        c.getLong(4), dur, path, c.getInt(7) % 1000
                    )
                }
            }
        } catch (_: SecurityException) { return Library(loaded = true) }

        val byFolder = tracks.groupBy { it.folder }
        val overrides = roleOverrides(ctx)
        // A folder inherits an explicit role from its nearest parent that has one.
        fun roleOf(folder: String): FolderRole {
            var f: String? = folder
            while (f != null && f.length > 1) { overrides[f]?.let { return it }; f = File(f).parent }
            return guess(folder, byFolder[folder] ?: emptyList())
        }
        val roles = byFolder.keys.associateWith { roleOf(it) }

        val music = tracks.filter { roles[it.folder] == FolderRole.MUSIC }
        val albums = music.groupBy { it.albumId }.map { (id, ts) ->
            val sorted = ts.sortedWith(compareBy({ it.trackNo }, { it.fileName.lowercase() }))
            val artist = ts.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "Unknown artist"
            Album(id, ts.first().album, artist, sorted)
        }.sortedBy { it.title.lowercase() }
        val artists = albums.groupBy { it.artist }.map { (n, a) -> Artist(n, a) }.sortedBy { it.name.lowercase() }

        val bookTracks = tracks.filter { roles[it.folder] == FolderRole.BOOKS }
        val books = ArrayList<Book>()
        val (single, multi) = bookTracks.partition { it.path.endsWith(".m4b", true) }
        single.forEach { books += Book(it.path, it.album.takeIf { a -> a != "Unknown album" && a.isNotBlank() } ?: it.title, it.artist, listOf(it), true) }
        multi.groupBy { it.folder }.forEach { (folder, ts) ->
            if (ts.size == 1 && ts[0].path.endsWith(".m4a", true)) {
                val t = ts[0]; books += Book(t.path, t.album.takeIf { it != "Unknown album" } ?: t.title, t.artist, ts, true)
            } else {
                val sorted = ts.sortedWith(compareBy({ it.trackNo.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { naturalKey(it.fileName) }))
                val name = File(folder).name
                val album = ts.first().album.takeIf { it != "Unknown album" && ts.all { t -> t.album == it } }
                books += Book(folder, album ?: name, ts.first().artist, sorted, false)
            }
        }
        books.sortBy { it.title.lowercase() }
        return Library(music.sortedBy { it.title.lowercase() }, albums, artists, books, roles, true)
    }

    /** "Chapter 2" before "Chapter 10". */
    private fun naturalKey(s: String): String = Regex("\\d+").replace(s.lowercase()) { it.value.padStart(8, '0') }
}

/** Per-book progress, speed and bookmarks, kept in SharedPreferences. */
object BookStore {
    private fun p(ctx: Context) = ctx.getSharedPreferences("books", Context.MODE_PRIVATE)

    data class Pos(val index: Int, val ms: Long)
    data class Mark(val index: Int, val ms: Long, val note: String, val created: Long)

    fun position(ctx: Context, id: String): Pos? = p(ctx).getString("pos:$id", null)?.split(':')?.let {
        runCatching { Pos(it[0].toInt(), it[1].toLong()) }.getOrNull()
    }
    fun savePosition(ctx: Context, id: String, index: Int, ms: Long) { p(ctx).edit().putString("pos:$id", "$index:$ms").putLong("last:$id", System.currentTimeMillis()).apply() }
    fun lastPlayed(ctx: Context, id: String) = p(ctx).getLong("last:$id", 0L)
    fun speed(ctx: Context, id: String) = p(ctx).getFloat("speed:$id", 1f)
    fun setSpeed(ctx: Context, id: String, s: Float) { p(ctx).edit().putFloat("speed:$id", s).apply() }

    fun marks(ctx: Context, id: String): List<Mark> {
        val a = try { JSONArray(p(ctx).getString("marks:$id", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).map { val o = a.getJSONObject(it); Mark(o.getInt("i"), o.getLong("ms"), o.optString("note"), o.optLong("t")) }
    }
    private fun saveMarks(ctx: Context, id: String, list: List<Mark>) {
        val a = JSONArray(); list.forEach { a.put(JSONObject().put("i", it.index).put("ms", it.ms).put("note", it.note).put("t", it.created)) }
        p(ctx).edit().putString("marks:$id", a.toString()).apply()
    }
    fun addMark(ctx: Context, id: String, m: Mark) = saveMarks(ctx, id, (marks(ctx, id) + m).sortedWith(compareBy({ it.index }, { it.ms })))
    fun removeMark(ctx: Context, id: String, m: Mark) = saveMarks(ctx, id, marks(ctx, id) - m)
}
