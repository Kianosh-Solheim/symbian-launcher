package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Runs the Desktop Mode server while it is switched on. Watches SMS, calls, notifications,
 * battery, media and weather, and pushes changes to connected browsers. Holds a Wi-Fi lock
 * only while at least one browser is connected, so it costs nothing when idle.
 */
class BridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var data: BridgeData
    private var server: BridgeServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var smsPending = false

    private val smsObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            if (smsPending) return
            smsPending = true
            main.postDelayed({ smsPending = false; server?.broadcast("sms", JSONObject()) }, 600)
        }
    }

    private var calendarPending = false
    private val calendarObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            if (calendarPending) return
            calendarPending = true
            main.postDelayed({ calendarPending = false; server?.broadcast("calendar", JSONObject()) }, 1000)
        }
    }

    private val contactsObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { data.invalidateContacts() }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        private var last = ""
        override fun onReceive(c: Context, i: Intent) {
            val status = data.status()
            val key = "${status.opt("battery")}|${status.opt("charging")}"
            if (key != last) { last = key; server?.broadcast("status", status) }
        }
    }

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val ok = resultCode == android.app.Activity.RESULT_OK
            server?.broadcast("sms_status", JSONObject().put("ok", ok).put("address", i.getStringExtra("address") ?: ""))
            server?.broadcast("sms", JSONObject())
        }
    }

    @Suppress("DEPRECATION")
    private val phoneListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, number: String?) {
            val s = when (state) {
                TelephonyManager.CALL_STATE_RINGING -> "ringing"
                TelephonyManager.CALL_STATE_OFFHOOK -> "active"
                else -> "idle"
            }
            val prev = data.callState
            val num = number?.takeIf { it.isNotBlank() } ?: prev.optString("number").takeIf { s != "idle" } ?: ""
            data.callState = JSONObject().put("state", s).put("number", num)
                .put("name", if (num.isNotBlank()) data.nameFor(num) else "")
                .put("since", if (s == prev.optString("state")) prev.optLong("since") else System.currentTimeMillis())
            server?.broadcast("call", data.callState)
            if (s == "idle") server?.broadcast("calls", JSONObject())
        }

        @Deprecated("Deprecated in Java")
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            val lvl = try { signalStrength?.level ?: -1 } catch (_: Exception) { -1 }
            if (lvl != data.signalLevel) { data.signalLevel = lvl; server?.broadcast("status", data.status()) }
        }
    }

    private var mediaControllers = listOf<MediaController>()
    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) { pushMedia() }
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) { pushMedia() }
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> watchMedia(list ?: emptyList()) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        data = BridgeData(this)
        startInForeground()
        BridgeState.regenerateCode()
        server = BridgeServer(this, data) { n -> onClients(n) }.also { it.start() }

        try {
            contentResolver.registerContentObserver(Uri.parse("content://sms"), true, smsObserver)
            contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, smsObserver)
            contentResolver.registerContentObserver(android.provider.ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)
            contentResolver.registerContentObserver(android.provider.CalendarContract.CONTENT_URI, true, calendarObserver)
        } catch (_: Exception) { }
        ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, smsSentReceiver, IntentFilter(ACTION_SMS_SENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            @Suppress("DEPRECATION")
            (getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                .listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE or PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } catch (_: Exception) { }
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = android.content.ComponentName(this, SymbianNotificationService::class.java)
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
            watchMedia(msm.getActiveSessions(cn))
        } catch (_: Exception) { }

        scope.launch { NotificationStore.notifications.drop(1).collectLatest { server?.broadcast("notifications", JSONObject()) } }
        scope.launch { data.wallet.passes.drop(1).collectLatest { server?.broadcast("wallet", JSONObject()) } }
        scope.launch { BridgeState.weather.drop(1).collectLatest { w -> if (w != null) server?.broadcast("weather", w) } }
        scope.launch { BridgeState.theme.drop(1).collectLatest { server?.broadcast("theme", data.theme()) } }
        scope.launch { BridgeState.pairingCode.collectLatest { updateNotification() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BridgeState.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop(); server = null
        scope.cancel()
        try { contentResolver.unregisterContentObserver(smsObserver) } catch (_: Exception) { }
        try { contentResolver.unregisterContentObserver(contactsObserver) } catch (_: Exception) { }
        try { contentResolver.unregisterContentObserver(calendarObserver) } catch (_: Exception) { }
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
        try { unregisterReceiver(smsSentReceiver) } catch (_: Exception) { }
        try {
            @Suppress("DEPRECATION")
            (getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).listen(phoneListener, PhoneStateListener.LISTEN_NONE)
        } catch (_: Exception) { }
        try { (getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) { }
        mediaControllers.forEach { try { it.unregisterCallback(mediaCallback) } catch (_: Exception) { } }
        data.ringPhone(false)
        releaseWifi()
        BridgeState.connectedClients.value = 0
        super.onDestroy()
    }

    private fun watchMedia(list: List<MediaController>) {
        mediaControllers.forEach { try { it.unregisterCallback(mediaCallback) } catch (_: Exception) { } }
        mediaControllers = list
        list.forEach { try { it.registerCallback(mediaCallback, main) } catch (_: Exception) { } }
        pushMedia()
    }

    private var mediaPending = false
    private fun pushMedia() {
        if (mediaPending) return
        mediaPending = true
        main.postDelayed({ mediaPending = false; server?.broadcast("media", data.media()) }, 300)
    }

    private fun onClients(n: Int) {
        BridgeState.connectedClients.value = n
        if (n > 0) acquireWifi() else releaseWifi()
        main.post { updateNotification() }
    }

    @Suppress("DEPRECATION")
    private fun acquireWifi() {
        if (wifiLock?.isHeld == true) return
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm.createWifiLock(mode, "symbian:desktop").apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) { }
    }

    private fun releaseWifi() {
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) { }
        wifiLock = null
    }

    // ───────────────────────────── Notification ─────────────────────────────

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Desktop mode", NotificationManager.IMPORTANCE_LOW))
        }
        val address = BridgeState.addresses(BridgeState.useHttps(this)).firstOrNull()?.second ?: "No Wi-Fi or USB connection"
        val clients = BridgeState.connectedClients.value
        val stop = PendingIntent.getService(this, 1, Intent(this, BridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(if (clients > 0) "Desktop connected" else "Desktop mode on")
            .setContentText("$address  ·  code ${BridgeState.pairingCode.value}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .apply { open?.let { setContentIntent(it) } }
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun startInForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun updateNotification() {
        try { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification()) } catch (_: Exception) { }
    }

    companion object {
        const val ACTION_SMS_SENT = "no.kianosh.symbianlauncher.SMS_SENT"
        const val ACTION_STOP = "no.kianosh.symbianlauncher.STOP_DESKTOP"
        private const val CHANNEL = "desktop_mode"
        private const val NOTIFICATION_ID = 4711

        fun start(context: Context) {
            BridgeState.setEnabled(context, true)
            try { ContextCompat.startForegroundService(context, Intent(context, BridgeService::class.java)) } catch (_: Exception) { }
        }

        fun stop(context: Context) {
            BridgeState.setEnabled(context, false)
            context.stopService(Intent(context, BridgeService::class.java))
        }

        /** Called when the launcher starts: restores Desktop Mode if it was left on. */
        fun restoreIfEnabled(context: Context) {
            if (BridgeState.isEnabled(context) && !BridgeState.running.value) start(context)
        }
    }
}
