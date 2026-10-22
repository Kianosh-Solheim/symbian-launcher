package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Everything the desktop page can read or do, as JSON. All calls are safe to make off the main thread. */
class BridgeData(private val context: Context) {
    private val repository = AppRepository(context)
    private val main = Handler(Looper.getMainLooper())
    private val nameCache = ConcurrentHashMap<String, String>()
    private val iconCache = ConcurrentHashMap<String, ByteArray>()

    @Volatile var signalLevel: Int = -1
    @Volatile var callState: JSONObject = JSONObject().put("state", "idle")

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // ───────────────────────────── Status ─────────────────────────────

    fun status(): JSONObject {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val stat = try { StatFs(Environment.getDataDirectory().path) } catch (_: Exception) { null }
        return JSONObject()
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("battery", if (level >= 0) (level * 100 / scale) else JSONObject.NULL)
            .put("charging", plugged)
            .put("signal", signalLevel)
            .put("operator", tm?.networkOperatorName ?: "")
            .put("time", System.currentTimeMillis())
            .put("timezone", java.util.TimeZone.getDefault().id)
            .put("storageFree", stat?.availableBytes ?: 0)
            .put("storageTotal", stat?.totalBytes ?: 0)
            .put("call", callState)
            .put("permissions", JSONObject()
                .put("sms", has(android.Manifest.permission.READ_SMS))
                .put("sendSms", has(android.Manifest.permission.SEND_SMS))
                .put("calls", has(android.Manifest.permission.READ_CALL_LOG))
                .put("placeCalls", has(android.Manifest.permission.CALL_PHONE))
                .put("contacts", has(android.Manifest.permission.READ_CONTACTS))
                .put("notifications", SymbianNotificationService.instance != null))
    }

    fun theme(): JSONObject = JSONObject(BridgeState.theme.value as Map<*, *>)

    fun weather(): JSONObject? = BridgeState.weather.value

    // ───────────────────────────── Contacts ─────────────────────────────

    @Volatile private var contactsCache: Pair<Long, List<ContactInfo>>? = null

    private fun contactList(): List<ContactInfo> {
        contactsCache?.let { if (System.currentTimeMillis() - it.first < 60_000) return it.second }
        if (!has(android.Manifest.permission.READ_CONTACTS)) return emptyList()
        val list = runBlocking { repository.getContacts() }
        contactsCache = System.currentTimeMillis() to list
        return list
    }

    fun invalidateContacts() { contactsCache = null; nameCache.clear() }

    fun contacts(): JSONArray {
        val arr = JSONArray()
        contactList().sortedBy { it.name.lowercase() }.forEach { c ->
            arr.put(JSONObject()
                .put("id", c.id)
                .put("name", c.name)
                .put("photo", c.photoUri != null)
                .put("org", c.organization?.company ?: "")
                .put("numbers", JSONArray().apply { c.phoneNumbers.forEach { put(JSONObject().put("number", it.number).put("label", it.label)) } })
                .put("emails", JSONArray().apply { c.emails.forEach { put(JSONObject().put("address", it.address).put("label", it.label)) } })
                .put("birthday", c.birthday ?: ""))
        }
        return arr
    }

    fun contactPhoto(id: String): ByteArray? {
        val c = contactList().firstOrNull { it.id == id } ?: return null
        val uri = c.photoUri ?: return null
        return try { context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } } catch (_: Exception) { null }
    }

    /** Display name for a number, using the system matcher; falls back to the number itself. */
    fun nameFor(number: String?): String {
        if (number.isNullOrBlank()) return "Unknown"
        nameCache[number]?.let { return it }
        var name: String? = null
        if (has(android.Manifest.permission.READ_CONTACTS)) {
            try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) name = it.getString(0)
                }
            } catch (_: Exception) { }
        }
        val result = name ?: number
        nameCache[number] = result
        return result
    }

    // ───────────────────────────── SMS ─────────────────────────────

    fun smsThreads(): JSONArray {
        val arr = JSONArray()
        if (!has(android.Manifest.permission.READ_SMS)) return arr
        val seen = LinkedHashMap<Long, JSONObject>()
        val unread = HashMap<Long, Int>()
        try {
            context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("thread_id", "address", "body", "date", "type", "read"),
                null, null, "date DESC"
            )?.use { c ->
                var rows = 0
                while (c.moveToNext() && rows < 5000) {
                    rows++
                    val thread = c.getLong(0)
                    if (c.getInt(5) == 0 && c.getInt(4) == 1) unread[thread] = (unread[thread] ?: 0) + 1
                    if (seen.containsKey(thread)) continue
                    val address = c.getString(1) ?: ""
                    seen[thread] = JSONObject()
                        .put("thread", thread)
                        .put("address", address)
                        .put("name", nameFor(address))
                        .put("snippet", (c.getString(2) ?: "").take(160))
                        .put("date", c.getLong(3))
                        .put("outgoing", c.getInt(4) != 1)
                }
            }
        } catch (_: Exception) { }
        seen.forEach { (thread, obj) -> arr.put(obj.put("unread", unread[thread] ?: 0)) }
        return arr
    }

    fun smsMessages(thread: Long): JSONArray {
        val arr = JSONArray()
        if (!has(android.Manifest.permission.READ_SMS)) return arr
        try {
            context.contentResolver.query(
                Uri.parse("content://sms"),
                arrayOf("_id", "address", "body", "date", "type", "status"),
                "thread_id = ?", arrayOf(thread.toString()), "date DESC"
            )?.use { c ->
                val rows = mutableListOf<JSONObject>()
                while (c.moveToNext() && rows.size < 400) {
                    val type = c.getInt(4)
                    rows += JSONObject()
                        .put("id", c.getLong(0))
                        .put("address", c.getString(1) ?: "")
                        .put("body", c.getString(2) ?: "")
                        .put("date", c.getLong(3))
                        .put("outgoing", type != 1)
                        .put("failed", type == 5)
                        .put("pending", type == 4 || type == 6)
                }
                rows.asReversed().forEach { arr.put(it) }
            }
        } catch (_: Exception) { }
        return arr
    }

    /** Sends an SMS (split into parts if long). Delivery results arrive via [BridgeService]. */
    fun sendSms(address: String, body: String): JSONObject {
        if (!has(android.Manifest.permission.SEND_SMS)) return fail("SMS permission not granted on the phone")
        if (address.isBlank() || body.isBlank()) return fail("Recipient and message are required")
        return try {
            @Suppress("DEPRECATION")
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            val parts = sms.divideMessage(body)
            val sent = ArrayList<PendingIntent>()
            parts.indices.forEach { i ->
                val intent = Intent(BridgeService.ACTION_SMS_SENT).setPackage(context.packageName)
                    .putExtra("address", address).putExtra("part", i).putExtra("parts", parts.size)
                sent += PendingIntent.getBroadcast(context, (System.nanoTime() % Int.MAX_VALUE).toInt() + i, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
            }
            sms.sendMultipartTextMessage(address, null, parts, sent, null)
            ok()
        } catch (e: Exception) {
            fail("Could not send: ${e.message}")
        }
    }

    // ───────────────────────────── Calls ─────────────────────────────

    fun callLog(): JSONArray {
        val arr = JSONArray()
        if (!has(android.Manifest.permission.READ_CALL_LOG)) return arr
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.CACHED_NAME),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext() && arr.length() < 300) {
                    val number = c.getString(0) ?: ""
                    val type = when (c.getInt(1)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        CallLog.Calls.BLOCKED_TYPE -> "blocked"
                        else -> "other"
                    }
                    arr.put(JSONObject()
                        .put("number", number)
                        .put("name", c.getString(4)?.takeIf { it.isNotBlank() } ?: nameFor(number))
                        .put("type", type)
                        .put("date", c.getLong(2))
                        .put("duration", c.getLong(3)))
                }
            }
        } catch (_: Exception) { }
        return arr
    }

    fun placeCall(number: String): JSONObject {
        if (!has(android.Manifest.permission.CALL_PHONE)) return fail("Phone permission not granted on the phone")
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
            ok()
        } catch (e: Exception) { fail("Could not call: ${e.message}") }
    }

    @Suppress("DEPRECATION")
    fun answerCall(): JSONObject = try {
        (context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).acceptRingingCall(); ok()
    } catch (e: Exception) { fail("Could not answer: ${e.message}") }

    @Suppress("DEPRECATION")
    fun endCall(): JSONObject = try {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { tm.endCall(); ok() } else fail("Not supported on this Android version")
    } catch (e: Exception) { fail("Could not end call: ${e.message}") }

    fun setSpeaker(on: Boolean): JSONObject = try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = on
        ok()
    } catch (e: Exception) { fail(e.message ?: "failed") }

    // ───────────────────────────── Notifications ─────────────────────────────

    private fun appLabel(pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    fun notifications(): JSONArray {
        val arr = JSONArray()
        NotificationStore.notifications.value
            .filter { isValidUserNotification(it) && it.packageName != context.packageName }
            .sortedByDescending { it.postTime }
            .forEach { sbn ->
                val n = sbn.notification
                val extras = n.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val messages = JSONArray()
                @Suppress("DEPRECATION")
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { p ->
                    (p as? Bundle)?.let { b ->
                        val sender = b.getCharSequence("sender")?.toString()
                            ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                                (b.get("sender_person") as? android.app.Person)?.name?.toString() else null)
                        messages.put(JSONObject()
                            .put("text", b.getCharSequence("text")?.toString() ?: "")
                            .put("sender", sender ?: "")
                            .put("time", b.getLong("time")))
                    }
                }
                val actions = JSONArray()
                var canReply = false
                n.actions?.forEachIndexed { i, a ->
                    val reply = a.remoteInputs?.any { it.allowFreeFormInput } == true
                    if (reply) canReply = true
                    actions.put(JSONObject().put("index", i).put("title", a.title?.toString() ?: "").put("reply", reply))
                }
                arr.put(JSONObject()
                    .put("key", sbn.key)
                    .put("pkg", sbn.packageName)
                    .put("app", appLabel(sbn.packageName))
                    .put("title", title)
                    .put("text", text)
                    .put("time", sbn.postTime)
                    .put("ongoing", sbn.isOngoing)
                    .put("clearable", sbn.isClearable)
                    .put("category", n.category ?: "")
                    .put("messaging", messages.length() > 0 || n.category == Notification.CATEGORY_MESSAGE)
                    .put("messages", messages)
                    .put("canReply", canReply)
                    .put("actions", actions))
            }
        return arr
    }

    private fun findNotification(key: String) = NotificationStore.notifications.value.firstOrNull { it.key == key }

    /** Replies through the notification's own reply action — this is how RCS, Signal, WhatsApp etc. are answered. */
    fun reply(key: String, text: String): JSONObject {
        val sbn = findNotification(key) ?: return fail("Notification no longer exists")
        val action = sbn.notification.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
            ?: return fail("This notification has no reply action")
        return try {
            val inputs = action.remoteInputs
            val intent = Intent()
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, text) }
            RemoteInput.addResultsToIntent(inputs, intent, results)
            action.actionIntent.send(context, 0, intent)
            ok()
        } catch (e: Exception) { fail("Reply failed: ${e.message}") }
    }

    fun runAction(key: String, index: Int): JSONObject {
        val sbn = findNotification(key) ?: return fail("Notification no longer exists")
        val action = sbn.notification.actions?.getOrNull(index) ?: return fail("Action not found")
        return try { action.actionIntent.send(); ok() } catch (e: Exception) { fail("Action failed: ${e.message}") }
    }

    fun dismiss(key: String): JSONObject {
        val svc = SymbianNotificationService.instance ?: return fail("Notification access is off")
        svc.clearNotification(key); return ok()
    }

    fun dismissAll(): JSONObject {
        val svc = SymbianNotificationService.instance ?: return fail("Notification access is off")
        svc.clearAll(); return ok()
    }

    fun appIcon(pkg: String): ByteArray? = iconCache.getOrPut(pkg) {
        try {
            val d = context.packageManager.getApplicationIcon(pkg)
            val bmp = d.toBitmap(96, 96, Bitmap.Config.ARGB_8888)
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        } catch (_: Exception) { return null }
    }

    // ───────────────────────────── Agenda ─────────────────────────────

    fun agenda(): JSONArray {
        val arr = JSONArray()
        if (!has(android.Manifest.permission.READ_CALENDAR)) return arr
        val events = try { runBlocking { repository.getUpcomingEvents(null) } } catch (_: Exception) { emptyList() }
        events.forEach { e ->
            arr.put(JSONObject().put("title", e.title).put("start", e.dtStart)
                .put("end", e.getEffectiveEndTime()).put("allDay", e.allDay))
        }
        return arr
    }

    // ───────────────────────────── Media ─────────────────────────────

    private fun mediaController(): MediaController? = try {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = msm.getActiveSessions(ComponentName(context, SymbianNotificationService::class.java))
        sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
    } catch (_: Exception) { null }

    fun media(): JSONObject {
        val mc = mediaController() ?: return JSONObject().put("active", false)
        val md = mc.metadata
        return JSONObject()
            .put("active", true)
            .put("app", appLabel(mc.packageName))
            .put("title", md?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "")
            .put("artist", md?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "")
            .put("album", md?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: "")
            .put("playing", mc.playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    fun mediaCommand(cmd: String): JSONObject {
        val t = mediaController()?.transportControls ?: return fail("Nothing is playing")
        when (cmd) {
            "play" -> t.play()
            "pause" -> t.pause()
            "next" -> t.skipToNext()
            "previous" -> t.skipToPrevious()
            "toggle" -> if (mediaController()?.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            else -> return fail("Unknown command")
        }
        return ok()
    }

    // ───────────────────────────── Tools ─────────────────────────────

    @Volatile private var ringtone: Ringtone? = null

    /** Rings at full alarm volume for 30 seconds, even in silent mode, to find the phone. */
    fun ringPhone(start: Boolean): JSONObject {
        main.post {
            ringtone?.stop(); ringtone = null
            if (start) {
                try {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    val r = RingtoneManager.getRingtone(context, uri)
                    r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                    r.play(); ringtone = r
                    main.postDelayed({ if (ringtone === r) { r.stop(); ringtone = null } }, 30_000)
                } catch (_: Exception) { }
            }
        }
        return ok()
    }

    fun setClipboard(text: String): JSONObject {
        main.post {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("From desktop", text))
            } catch (_: Exception) { }
        }
        return ok()
    }

    /** Shows text or a link as a phone notification; tapping a link opens it. */
    fun sendToPhone(text: String): JSONObject {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_INBOX, "From desktop", NotificationManager.IMPORTANCE_HIGH))
        }
        val url = Regex("""https?://\S+""").find(text)?.value
        val tap = if (url != null) {
            PendingIntent.getActivity(context, url.hashCode(), Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE)
        } else null
        val n = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_INBOX)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(if (url != null) "Link from desktop" else "Note from desktop")
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .apply { tap?.let { setContentIntent(it) } }
            .build()
        return try { nm.notify((System.currentTimeMillis() % 100000).toInt() + 2000, n); ok() } catch (e: Exception) { fail(e.message ?: "failed") }
    }

    /** Saves an uploaded file into the phone's Downloads folder. */
    fun saveUpload(name: String, mime: String, input: InputStream, length: Long): JSONObject {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return fail("Needs Android 10 or newer")
        val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120).ifBlank { "upload" }
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return fail("Could not create file")
            resolver.openOutputStream(uri)?.use { out ->
                val buf = ByteArray(64 * 1024); var left = length
                while (left > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n); left -= n
                }
            }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            ok().put("name", safe)
        } catch (e: Exception) { fail("Upload failed: ${e.message}") }
    }

    companion object {
        const val CHANNEL_INBOX = "desktop_inbox"
        fun ok(): JSONObject = JSONObject().put("ok", true)
        fun fail(msg: String): JSONObject = JSONObject().put("ok", false).put("error", msg)
    }
}
