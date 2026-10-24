package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Rings an alarm or the timer: loops the alarm sound on the alarm volume (fading in), vibrates, keeps the
 * CPU awake and shows the full-screen ring screen. Stops after 10 minutes if nobody answers.
 */
class AlarmRingService : Service() {
    data class Ringing(val id: String, val label: String, val isTimer: Boolean, val since: Long)

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private val main = Handler(Looper.getMainLooper())
    private var current: Ringing? = null
    private var volume = 0.15f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { finish(snooze = false); return START_NOT_STICKY }
            ACTION_SNOOZE -> { finish(snooze = true); return START_NOT_STICKY }
        }
        val id = intent?.getStringExtra("ALARM_ID") ?: run { stopSelf(); return START_NOT_STICKY }
        AlarmStore.load(this)
        val isTimer = id == AlarmStore.TIMER_ID
        val alarm = if (isTimer) null else AlarmStore.get(this, id)
        if (!isTimer && alarm == null) { stopSelf(); return START_NOT_STICKY }
        // A new alarm while one rings: the older one counts as stopped.
        current?.let { if (it.id != id) complete(it, snooze = false) }
        val r = Ringing(id, if (isTimer) "Timer finished" else alarm!!.label.ifBlank { "Alarm" }, isTimer, System.currentTimeMillis())
        current = r
        state.value = r

        val notification = buildNotification(r)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, notification)

        wake?.release()
        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "symbian:alarm").apply { acquire(11 * 60_000L) }
        startSound()
        if (isTimer || alarm!!.vibrate) startVibration()
        // Show the ring screen right away (works from the background because the launcher may draw over apps);
        // otherwise the full-screen notification opens it.
        try { startActivity(ringIntent(this)) } catch (_: Exception) { }

        main.removeCallbacksAndMessages(null)
        main.postDelayed({ finish(snooze = !isTimer, timedOut = true) }, 10 * 60_000L)
        main.post(fadeIn)
        return START_NOT_STICKY
    }

    private val fadeIn = object : Runnable {
        override fun run() {
            volume = (volume + 0.05f).coerceAtMost(1f)
            try { player?.setVolume(volume, volume) } catch (_: Exception) { }
            if (volume < 1f) main.postDelayed(this, 1_500)
        }
    }

    private fun startSound() {
        stopSound()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        // If the alarm volume is muted, raise it to a moderate level so the alarm can't silently fail.
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM) / 2, 0)
        volume = 0.15f
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                setVolume(volume, volume)
                prepare(); start()
            }
        } catch (_: Exception) {
            // Last resort: the built-in ringtone player.
            try { RingtoneManager.getRingtone(this, uri)?.apply { audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(); play() } } catch (_: Exception) { }
            null
        }
    }

    private fun stopSound() {
        main.removeCallbacks(fadeIn)
        try { player?.stop() } catch (_: Exception) { }
        player?.release(); player = null
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= 31) (getSystemService(android.os.VibratorManager::class.java)).defaultVibrator
            else getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 600, 400, 600, 1200)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        try { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs) } catch (_: Exception) { }
    }

    private fun finish(snooze: Boolean, timedOut: Boolean = false) {
        val r = current
        if (r != null) complete(r, snooze)
        if (timedOut && r != null) notifyMissed(r)
        stopSound()
        try { vibrator?.cancel() } catch (_: Exception) { }
        wake?.let { if (it.isHeld) it.release() }
        main.removeCallbacksAndMessages(null)
        current = null
        state.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun complete(r: Ringing, snooze: Boolean) {
        if (r.isTimer) AlarmStore.timerFinished(this)
        else if (snooze) AlarmStore.snooze(this, r.id)
        else AlarmStore.finished(this, r.id)
    }

    private fun notifyMissed(r: Ringing) {
        val nm = getSystemService(NotificationManager::class.java)
        ensureChannels(this)
        nm.notify(NOTIF_ID + 1, NotificationCompat.Builder(this, CHANNEL_INFO)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Missed: ${r.label}")
            .setContentText(if (r.isTimer) "The timer rang for 10 minutes." else "Rang for 10 minutes — snoozed.")
            .setAutoCancel(true).build())
    }

    private fun buildNotification(r: Ringing): Notification {
        ensureChannels(this)
        val full = PendingIntent.getActivity(this, 7002, ringIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(a: String, code: Int) = PendingIntent.getService(this, code,
            Intent(this, AlarmRingService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(this, CHANNEL_RING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(r.label)
            .setContentText(Formatters.get("HH:mm").format(java.util.Date(r.since)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Stop", action(ACTION_STOP, 7003))
        if (!r.isTimer) b.addAction(0, "Snooze", action(ACTION_SNOOZE, 7004))
        return b.build()
    }

    override fun onDestroy() {
        stopSound()
        try { vibrator?.cancel() } catch (_: Exception) { }
        wake?.let { if (it.isHeld) it.release() }
        main.removeCallbacksAndMessages(null)
        state.value = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "no.kianosh.symbianlauncher.ALARM_STOP"
        const val ACTION_SNOOZE = "no.kianosh.symbianlauncher.ALARM_SNOOZE"
        private const val CHANNEL_RING = "alarm_ringing"
        private const val CHANNEL_INFO = "alarm_info"
        private const val NOTIF_ID = 4242

        /** What is ringing right now (null when quiet); the ring screen observes this. */
        val state = MutableStateFlow<Ringing?>(null)

        fun start(ctx: Context, id: String) {
            val i = Intent(ctx, AlarmRingService::class.java).putExtra("ALARM_ID", id)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun send(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, AlarmRingService::class.java).setAction(action))
        }

        fun ringIntent(ctx: Context) = Intent(ctx, AlarmRingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun ensureChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_RING) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_RING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null); enableVibration(false) // the service plays sound and vibration itself
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                })
            }
            if (nm.getNotificationChannel(CHANNEL_INFO) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL_INFO, "Alarm messages", NotificationManager.IMPORTANCE_DEFAULT))
            // The old notification-only alarm channel is no longer used.
            nm.deleteNotificationChannel("alarm_channel")
        }
    }
}
