package com.example.music

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService

/** Needed only so Android lets us see and control other players (Spotify etc.). */
class OtherAppsListener : NotificationListenerService()

data class OtherPlayer(
    val controller: MediaController,
    val appName: String,
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

object OtherApps {
    fun allowed(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(ComponentName(ctx, OtherAppsListener::class.java).flattenToString()) || flat.contains(ctx.packageName + "/")
    }

    fun players(ctx: Context): List<OtherPlayer> {
        if (!allowed(ctx)) return emptyList()
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list = try { msm.getActiveSessions(ComponentName(ctx, OtherAppsListener::class.java)) } catch (_: SecurityException) { return emptyList() }
        val pm = ctx.packageManager
        return list.filter { it.packageName != ctx.packageName }.map { c ->
            val m = c.metadata
            val st = c.playbackState
            val app = try { pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() } catch (_: Exception) { c.packageName }
            OtherPlayer(
                c, app,
                m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Nothing playing",
                m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
                m?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
                m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                st?.state == PlaybackState.STATE_PLAYING,
                st?.position ?: 0L,
                m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            )
        }
    }

    fun toggle(p: OtherPlayer) { val t = p.controller.transportControls; if (p.playing) t.pause() else t.play() }
    fun next(p: OtherPlayer) = p.controller.transportControls.skipToNext()
    fun prev(p: OtherPlayer) = p.controller.transportControls.skipToPrevious()
}
