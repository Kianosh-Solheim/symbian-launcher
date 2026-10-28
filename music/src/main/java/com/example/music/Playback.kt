package com.example.music

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** What is loaded in the player right now. */
sealed class Queue {
    data class Music(val tracks: List<Track>, val title: String) : Queue()
    data class Audiobook(val book: Book, val chapters: List<Chapter>) : Queue()
}

/**
 * The single player, owned by [PlaybackService] and used directly by the UI (same process, main thread).
 * Also keeps audiobook positions saved and runs the sleep timer.
 */
object Engine {
    var player: ExoPlayer? = null
        private set
    lateinit var appCtx: Context

    var queue by mutableStateOf<Queue?>(null)
    var isPlaying by mutableStateOf(false)
    var index by mutableIntStateOf(0)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var speed by mutableFloatStateOf(1f)
    /** Sleep timer: wall-clock end time, or -1 = at end of chapter, 0 = off. */
    var sleepAt by mutableLongStateOf(0L)

    private val main = Handler(Looper.getMainLooper())
    private var lastSave = 0L

    fun attach(p: ExoPlayer, ctx: Context) {
        appCtx = ctx.applicationContext
        player = p
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing; if (!playing) saveBook() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { index = p.currentMediaItemIndex; durationMs = p.duration.coerceAtLeast(0) }
            override fun onPlaybackStateChanged(state: Int) { durationMs = p.duration.coerceAtLeast(0) }
            override fun onShuffleModeEnabledChanged(on: Boolean) { shuffle = on }
            override fun onRepeatModeChanged(mode: Int) { repeat = mode }
        })
        main.post(ticker)
    }

    fun detach() { saveBook(); main.removeCallbacks(ticker); player = null; isPlaying = false }

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            positionMs = p.currentPosition.coerceAtLeast(0)
            durationMs = p.duration.coerceAtLeast(0)
            index = p.currentMediaItemIndex
            if (p.isPlaying && System.currentTimeMillis() - lastSave > 5000) saveBook()
            checkSleep(p)
            main.postDelayed(this, 400)
        }
    }

    private fun checkSleep(p: ExoPlayer) {
        val at = sleepAt
        if (at > 0) {
            val left = at - System.currentTimeMillis()
            if (left <= 0) { p.pause(); p.volume = 1f; sleepAt = 0 }
            else if (left < 8000) p.volume = (left / 8000f).coerceIn(0.05f, 1f)
        } else if (at == -1L) {
            val q = queue
            val end = if (q is Queue.Audiobook && q.chapters.isNotEmpty()) {
                val c = chapterIndex(); q.chapters.getOrNull(c + 1)?.startMs
            } else null
            val atEnd = if (end != null) p.currentPosition >= end - 600 else (p.duration > 0 && p.currentPosition >= p.duration - 600)
            if (atEnd) { p.pause(); sleepAt = 0 }
        }
    }

    // ---- loading ----

    private fun item(t: Track, book: Book? = null) = MediaItem.Builder()
        .setUri(t.uri).setMediaId(t.id.toString())
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(if (book != null && book.singleFile) book.title else t.title)
            .setArtist(t.artist).setAlbumTitle(book?.title ?: t.album)
            .setArtworkUri(android.net.Uri.parse("content://media/external/audio/albumart/${t.albumId}"))
            .build())
        .build()

    fun playMusic(tracks: List<Track>, start: Int, title: String, shuffleOn: Boolean = false) {
        val p = player ?: return
        saveBook()
        queue = Queue.Music(tracks, title)
        sleepAt = 0
        p.setPlaybackParameters(PlaybackParameters(1f)); speed = 1f
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        p.shuffleModeEnabled = shuffleOn
        p.setMediaItems(tracks.map { item(it) }, start.coerceIn(0, tracks.lastIndex), 0)
        p.prepare(); p.play()
    }

    fun playBook(book: Book, at: BookStore.Pos? = null) {
        val p = player ?: return
        saveBook()
        val chapters = if (book.singleFile) Chapters.read(appCtx, book.files[0].uri, book.id) else emptyList()
        queue = Queue.Audiobook(book, chapters)
        sleepAt = 0
        val pos = at ?: BookStore.position(appCtx, book.id) ?: BookStore.Pos(0, 0)
        p.shuffleModeEnabled = false
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        speed = BookStore.speed(appCtx, book.id)
        p.setPlaybackParameters(PlaybackParameters(speed))
        // Rewind a few seconds on resume so you catch the sentence again.
        p.setMediaItems(book.files.map { item(it, book) }, pos.index.coerceIn(0, book.files.lastIndex), (pos.ms - 3000).coerceAtLeast(0))
        p.prepare(); p.play()
    }

    fun saveBook() {
        val p = player ?: return
        val q = queue as? Queue.Audiobook ?: return
        if (p.mediaItemCount == 0) return
        BookStore.savePosition(appCtx, q.book.id, p.currentMediaItemIndex, p.currentPosition.coerceAtLeast(0))
        lastSave = System.currentTimeMillis()
    }

    // ---- controls ----

    fun toggle() { val p = player ?: return; if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0); p.play() } }
    fun seekBy(ms: Long) { val p = player ?: return; p.seekTo((p.currentPosition + ms).coerceIn(0, maxOf(0, p.duration))) }
    fun next() {
        val p = player ?: return
        val q = queue
        if (q is Queue.Audiobook && q.chapters.isNotEmpty()) { q.chapters.getOrNull(chapterIndex() + 1)?.let { p.seekTo(it.startMs) }; return }
        if (p.hasNextMediaItem()) p.seekToNextMediaItem()
    }
    fun prev() {
        val p = player ?: return
        val q = queue
        if (q is Queue.Audiobook && q.chapters.isNotEmpty()) {
            val c = chapterIndex()
            val start = q.chapters[c].startMs
            p.seekTo(if (p.currentPosition - start > 4000 || c == 0) start else q.chapters[c - 1].startMs); return
        }
        if (p.currentPosition > 4000 || !p.hasPreviousMediaItem()) p.seekTo(0) else p.seekToPreviousMediaItem()
    }
    fun jumpTo(index: Int, ms: Long = 0) { val p = player ?: return; p.seekTo(index, ms); p.play() }
    fun setSpeedTo(s: Float) {
        val p = player ?: return
        speed = s; p.setPlaybackParameters(PlaybackParameters(s))
        (queue as? Queue.Audiobook)?.let { BookStore.setSpeed(appCtx, it.book.id, s) }
    }
    fun toggleShuffle() { player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() { player?.let { it.repeatMode = when (it.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } } }
    fun setSleep(minutes: Int) { player?.volume = 1f; sleepAt = when { minutes == 0 -> 0; minutes < 0 -> -1; else -> System.currentTimeMillis() + minutes * 60_000L } }

    /** Chapter the player is in (for single-file books with chapters, else the file index). */
    fun chapterIndex(): Int {
        val q = queue as? Queue.Audiobook ?: return index
        if (q.chapters.isEmpty()) return index
        val pos = player?.currentPosition ?: positionMs
        return q.chapters.indexOfLast { it.startMs <= pos + 300 }.coerceAtLeast(0)
    }

    fun currentTrack(): Track? = when (val q = queue) {
        is Queue.Music -> q.tracks.getOrNull(player?.currentMediaItemIndex ?: index)
        is Queue.Audiobook -> q.book.files.getOrNull(player?.currentMediaItemIndex ?: index)
        null -> null
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(30_000).setSeekForwardIncrementMs(30_000)
            .build()
        val open = PendingIntent.getActivity(this, 0, Intent(this, MusicActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        Engine.attach(player, this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        Engine.detach()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
