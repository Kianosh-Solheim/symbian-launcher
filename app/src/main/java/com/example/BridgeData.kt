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
import android.graphics.BitmapFactory
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
import android.provider.CalendarContract
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
                .put("birthday", c.birthday ?: "")
                .put("notes", c.notes.joinToString("\n")))
        }
        return arr
    }

    // ───── Contact editing ─────

    private fun phoneType(label: String): Pair<Int, String?> = when (label.trim().lowercase()) {
        "mobile", "mobil", "cell" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE to null
        "home", "hjem", "privat" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME to null
        "work", "jobb", "arbeid" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK to null
        "", "other", "annet" -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER to null
        else -> ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM to label.trim()
    }

    private fun emailType(label: String): Pair<Int, String?> = when (label.trim().lowercase()) {
        "home", "hjem", "privat" -> ContactsContract.CommonDataKinds.Email.TYPE_HOME to null
        "work", "jobb", "arbeid" -> ContactsContract.CommonDataKinds.Email.TYPE_WORK to null
        "", "other", "annet" -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER to null
        else -> ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM to label.trim()
    }

    /** The Google account most contacts live in, so new contacts sync like the rest. */
    private fun defaultContactAccount(): Pair<String?, String?> {
        val counts = HashMap<Pair<String, String>, Int>()
        try {
            context.contentResolver.query(ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME),
                "${ContactsContract.RawContacts.DELETED} = 0", null, null)?.use { c ->
                while (c.moveToNext()) {
                    val t = c.getString(0) ?: continue; val n = c.getString(1) ?: continue
                    counts[t to n] = (counts[t to n] ?: 0) + 1
                }
            }
        } catch (_: Exception) { }
        val best = counts.entries.filter { it.key.first == "com.google" }.maxByOrNull { it.value }
            ?: counts.entries.maxByOrNull { it.value }
        return best?.key?.first to best?.key?.second
    }

    private fun rawContactFor(contactId: String): Long? {
        try {
            context.contentResolver.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0", arrayOf(contactId), null)?.use { c ->
                var first: Long? = null
                while (c.moveToNext()) { if (first == null) first = c.getLong(0); if (c.getString(1) == "com.google") return c.getLong(0) }
                return first
            }
        } catch (_: Exception) { }
        return null
    }

    /** Creates or updates a contact: name, phone numbers, e-mails, birthday, company and notes. */
    fun saveContact(j: JSONObject): JSONObject {
        if (!has(android.Manifest.permission.WRITE_CONTACTS)) return fail("Contacts write permission not granted on the phone")
        val name = j.optString("name").trim()
        if (name.isEmpty()) return fail("Give the contact a name")
        val ops = ArrayList<android.content.ContentProviderOperation>()
        val existing = j.optString("id").trim()
        val rawId: Long?
        if (existing.isNotEmpty()) {
            rawId = rawContactFor(existing) ?: return fail("Contact not found")
            val managed = listOf(
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
            )
            ops += android.content.ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (${managed.joinToString(",") { "?" }})",
                    arrayOf(rawId.toString()) + managed).build()
            ops += android.content.ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                    arrayOf(rawId.toString(), ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString())).build()
        } else {
            rawId = null
            val (type, acct) = defaultContactAccount()
            ops += android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, type)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, acct).build()
        }
        fun data(mime: String): android.content.ContentProviderOperation.Builder {
            val b = android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.MIMETYPE, mime)
            return if (rawId != null) b.withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId) else b.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        }
        ops += data(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build()
        j.optJSONArray("numbers")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue; val num = o.optString("number").trim(); if (num.isEmpty()) continue
                val (t, l) = phoneType(o.optString("label"))
                ops += data(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, num)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, t).withValue(ContactsContract.CommonDataKinds.Phone.LABEL, l).build()
            }
        }
        j.optJSONArray("emails")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue; val addr = o.optString("address").trim(); if (addr.isEmpty()) continue
                val (t, l) = emailType(o.optString("label"))
                ops += data(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, addr)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, t).withValue(ContactsContract.CommonDataKinds.Email.LABEL, l).build()
            }
        }
        j.optString("birthday").trim().takeIf { it.isNotEmpty() }?.let { b ->
            ops += data(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Event.START_DATE, b)
                .withValue(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY).build()
        }
        j.optString("org").trim().takeIf { it.isNotEmpty() }?.let { o ->
            ops += data(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, o).build()
        }
        j.optString("notes").trim().takeIf { it.isNotEmpty() }?.let { n ->
            ops += data(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Note.NOTE, n).build()
        }
        return try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            invalidateContacts(); ok()
        } catch (e: Exception) { fail("Could not save: ${e.message}") }
    }

    fun deleteContact(id: String): JSONObject {
        if (!has(android.Manifest.permission.WRITE_CONTACTS)) return fail("Contacts write permission not granted on the phone")
        return try {
            val n = context.contentResolver.delete(ContactsContract.Contacts.CONTENT_URI, "${ContactsContract.Contacts._ID} = ?", arrayOf(id))
            invalidateContacts(); if (n > 0) ok() else fail("Contact not found")
        } catch (e: Exception) { fail("Could not delete: ${e.message}") }
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
        // Merge picture messages: newer MMS replaces the snippet; MMS-only threads are added.
        mmsLatest().forEach { (thread, m) ->
            val existing = seen[thread]
            if (existing == null) {
                val address = m.optString("address")
                seen[thread] = JSONObject().put("thread", thread).put("address", address).put("name", nameFor(address))
                    .put("snippet", m.optString("snippet")).put("date", m.optLong("date")).put("outgoing", m.optBoolean("outgoing"))
            } else if (m.optLong("date") > existing.optLong("date")) {
                existing.put("snippet", m.optString("snippet")).put("date", m.optLong("date")).put("outgoing", m.optBoolean("outgoing"))
            }
            if (m.optInt("unread") > 0) unread[thread] = (unread[thread] ?: 0) + m.optInt("unread")
        }
        seen.values.sortedByDescending { it.optLong("date") }.forEach { obj -> arr.put(obj.put("unread", unread[obj.getLong("thread")] ?: 0)) }
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
                rows += mmsMessages(thread)
                rows.sortedBy { it.optLong("date") }.takeLast(400).forEach { arr.put(it) }
            }
        } catch (_: Exception) { }
        return arr
    }

    /** Full-text search through SMS bodies, newest first. */
    fun searchMessages(q: String): JSONArray {
        val arr = JSONArray()
        if (!has(android.Manifest.permission.READ_SMS) || q.trim().length < 2) return arr
        val like = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        try {
            context.contentResolver.query(Uri.parse("content://sms"), arrayOf("thread_id", "address", "body", "date", "type"),
                "body LIKE ? ESCAPE '\\'", arrayOf(like), "date DESC")?.use { c ->
                while (c.moveToNext() && arr.length() < 40) {
                    val address = c.getString(1) ?: ""
                    arr.put(JSONObject().put("thread", c.getLong(0)).put("address", address).put("name", nameFor(address))
                        .put("body", (c.getString(2) ?: "").take(300)).put("date", c.getLong(3)).put("outgoing", c.getInt(4) != 1))
                }
            }
        } catch (_: Exception) { }
        return arr
    }

    // ───── MMS (picture messages) ─────

    private fun mmsAddress(id: Long, outgoing: Boolean): String {
        // 137 = from, 151 = to
        val type = if (outgoing) 151 else 137
        try {
            context.contentResolver.query(Uri.parse("content://mms/$id/addr"), arrayOf("address", "type"), "type = $type", null, null)?.use { c ->
                while (c.moveToNext()) {
                    val a = c.getString(0) ?: continue
                    if (a != "insert-address-token") return a
                }
            }
        } catch (_: Exception) { }
        return ""
    }

    private data class MmsPart(val id: Long, val type: String, val text: String?)

    private fun mmsParts(id: Long): List<MmsPart> {
        val parts = mutableListOf<MmsPart>()
        try {
            context.contentResolver.query(Uri.parse("content://mms/part"), arrayOf("_id", "ct", "text", "_data"), "mid = ?", arrayOf(id.toString()), null)?.use { c ->
                while (c.moveToNext()) {
                    val pid = c.getLong(0); val ct = c.getString(1) ?: ""
                    var text: String? = null
                    if (ct == "text/plain") {
                        text = c.getString(2)
                        if (text == null && c.getString(3) != null) {
                            text = try {
                                context.contentResolver.openInputStream(Uri.parse("content://mms/part/$pid"))?.use { it.readBytes().toString(Charsets.UTF_8) }
                            } catch (_: Exception) { null }
                        }
                    }
                    if (ct == "application/smil") continue
                    parts += MmsPart(pid, ct, text)
                }
            }
        } catch (_: Exception) { }
        return parts
    }

    private fun mmsSnippet(parts: List<MmsPart>): String {
        val text = parts.firstOrNull { it.text != null }?.text?.take(160)
        val media = parts.firstOrNull { it.type.startsWith("image/") || it.type.startsWith("video/") || it.type.startsWith("audio/") }
        val label = when {
            media == null -> null
            media.type.startsWith("image/") -> "📷 Photo"
            media.type.startsWith("video/") -> "🎬 Video"
            else -> "🎵 Audio"
        }
        return listOfNotNull(label, text).joinToString(" ").ifBlank { "MMS" }
    }

    /** Latest picture message per thread (thread → snippet, date, outgoing, address, unread). */
    private fun mmsLatest(): Map<Long, JSONObject> {
        val out = LinkedHashMap<Long, JSONObject>()
        try {
            context.contentResolver.query(Uri.parse("content://mms"), arrayOf("_id", "thread_id", "date", "msg_box", "read"), null, null, "date DESC")?.use { c ->
                var rows = 0
                while (c.moveToNext() && rows < 1500) {
                    rows++
                    val thread = c.getLong(1)
                    val unreadIn = if (c.getInt(4) == 0 && c.getInt(3) == 1) 1 else 0
                    val existing = out[thread]
                    if (existing != null) { existing.put("unread", existing.optInt("unread") + unreadIn); continue }
                    val id = c.getLong(0); val outgoing = c.getInt(3) != 1
                    out[thread] = JSONObject().put("snippet", mmsSnippet(mmsParts(id))).put("date", c.getLong(2) * 1000)
                        .put("outgoing", outgoing).put("address", mmsAddress(id, outgoing)).put("unread", unreadIn)
                }
            }
        } catch (_: Exception) { }
        return out
    }

    private fun mmsMessages(thread: Long): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        try {
            context.contentResolver.query(Uri.parse("content://mms"), arrayOf("_id", "date", "msg_box"), "thread_id = ?", arrayOf(thread.toString()), "date DESC")?.use { c ->
                while (c.moveToNext() && list.size < 100) {
                    val id = c.getLong(0); val box = c.getInt(2)
                    val parts = mmsParts(id)
                    list += JSONObject()
                        .put("id", "mms$id")
                        .put("address", mmsAddress(id, box != 1))
                        .put("body", parts.filter { it.text != null }.joinToString("\n") { it.text!! })
                        .put("date", c.getLong(1) * 1000)
                        .put("outgoing", box != 1)
                        .put("failed", box == 5)
                        .put("pending", box == 4)
                        .put("media", JSONArray().apply {
                            parts.filter { it.type.startsWith("image/") || it.type.startsWith("video/") || it.type.startsWith("audio/") }
                                .forEach { put(JSONObject().put("id", it.id).put("type", it.type)) }
                        })
                }
            }
        } catch (_: Exception) { }
        return list
    }

    /** Raw bytes of one MMS attachment (photo, video or audio). */
    fun mmsPart(id: Long): Pair<ByteArray, String>? = try {
        var type = "application/octet-stream"
        context.contentResolver.query(Uri.parse("content://mms/part"), arrayOf("ct"), "_id = ?", arrayOf(id.toString()), null)?.use { c ->
            if (c.moveToFirst()) type = c.getString(0) ?: type
        }
        context.contentResolver.openInputStream(Uri.parse("content://mms/part/$id"))?.use { it.readBytes() }?.let { it to type }
    } catch (_: Exception) { null }

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
                    .put("actions", actions)
                    .put("url", findUrl(extras) ?: ""))
            }
        return arr
    }

    private val urlRegex = Regex("""https?://[^\s"'<>]+""")

    /** Looks through a notification's extras for a web link (news apps often include one). */
    private fun findUrl(extras: Bundle, depth: Int = 0): String? {
        if (depth > 2) return null
        for (k in extras.keySet()) {
            @Suppress("DEPRECATION")
            val v = try { extras.get(k) } catch (_: Exception) { null } ?: continue
            val found = when (v) {
                is android.net.Uri -> v.toString().takeIf { it.startsWith("http") }
                is CharSequence -> urlRegex.find(v)?.value
                is Bundle -> findUrl(v, depth + 1)
                else -> null
            }
            if (found != null) return found.trimEnd('.', ',', ')')
        }
        return null
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

    fun appIcon(pkg: String): ByteArray? = iconCache.getOrPut(when { IconCache.win95 -> "95:$pkg"; IconCache.s60 -> "60:$pkg"; else -> pkg }) {
        try {
            val d = context.packageManager.getApplicationIcon(pkg)
            val bmp = when {
                IconCache.win95 -> Win95Pixelizer.convert(d)
                IconCache.s60 -> S60Styler.convert(d, pkg)
                else -> d.toBitmap(96, 96, Bitmap.Config.ARGB_8888)
            }
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

    // ───────────────────────────── Calendar ─────────────────────────────

    fun calendars(): JSONArray {
        val arr = JSONArray()
        if (!has(android.Manifest.permission.READ_CALENDAR)) return arr
        val default = defaultCalendarId()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.VISIBLE
                ), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    arr.put(JSONObject()
                        .put("id", id)
                        .put("name", c.getString(1) ?: "")
                        .put("account", c.getString(2) ?: "")
                        .put("google", c.getString(3) == "com.google")
                        .put("color", colorHex(c.getInt(4)))
                        .put("writable", c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
                        .put("visible", c.getInt(6) == 1)
                        .put("default", id == default))
                }
            }
        } catch (_: Exception) { }
        return arr
    }

    /** The account's own Google calendar if there is one, else the first calendar we may write to. */
    private fun defaultCalendarId(): Long? {
        data class Cal(val id: Long, val google: Boolean, val primary: Boolean, val own: Boolean, val writable: Boolean)
        val cals = mutableListOf<Cal>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.IS_PRIMARY,
                    CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.OWNER_ACCOUNT,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
                ), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    cals += Cal(
                        c.getLong(0), c.getString(1) == "com.google", c.getInt(2) == 1,
                        c.getString(3) != null && c.getString(3) == c.getString(4),
                        c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    )
                }
            }
        } catch (_: Exception) { }
        val w = cals.filter { it.writable }
        return (w.firstOrNull { it.google && it.primary } ?: w.firstOrNull { it.google && it.own }
            ?: w.firstOrNull { it.google } ?: w.firstOrNull { it.primary } ?: w.firstOrNull())?.id
    }

    private fun colorHex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

    /** Event occurrences (recurring events expanded) between two instants, from visible calendars. */
    fun calendarEvents(from: Long, to: Long): JSONArray {
        val arr = JSONArray()
        if (!has(android.Manifest.permission.READ_CALENDAR) || to <= from) return arr
        try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                android.content.ContentUris.appendId(it, from); android.content.ContentUris.appendId(it, to)
            }.build()
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.DISPLAY_COLOR,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME, CalendarContract.Instances.RRULE,
                    CalendarContract.Instances.CALENDAR_ACCESS_LEVEL
                ),
                "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext() && arr.length() < 2000) {
                    arr.put(JSONObject()
                        .put("id", c.getLong(0))
                        .put("title", c.getString(1)?.ifBlank { null } ?: "(No title)")
                        .put("start", c.getLong(2))
                        .put("end", c.getLong(3))
                        .put("allDay", c.getInt(4) == 1)
                        .put("location", c.getString(5) ?: "")
                        .put("description", c.getString(6) ?: "")
                        .put("calendarId", c.getLong(7))
                        .put("color", colorHex(c.getInt(8)))
                        .put("calendar", c.getString(9) ?: "")
                        .put("recurring", !c.getString(10).isNullOrBlank())
                        .put("writable", c.getInt(11) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR))
                }
            }
        } catch (_: Exception) { }
        return arr
    }

    /**
     * Creates or updates an event. Timed events take start/end in epoch ms; all-day events take
     * date/endDate as yyyy-MM-dd (end inclusive). Google calendars sync to Google automatically.
     */
    fun saveEvent(j: JSONObject): JSONObject {
        if (!has(android.Manifest.permission.WRITE_CALENDAR)) return fail("Calendar write permission not granted on the phone")
        val title = j.optString("title").trim()
        if (title.isEmpty()) return fail("Title is required")
        val allDay = j.optBoolean("allDay")
        val values = ContentValues()
        if (allDay) {
            val utc = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            val start = try { utc.parse(j.optString("date"))!!.time } catch (_: Exception) { return fail("Invalid date") }
            val endIncl = try { utc.parse(j.optString("endDate").ifBlank { j.optString("date") })!!.time } catch (_: Exception) { start }
            values.put(CalendarContract.Events.DTSTART, start)
            values.put(CalendarContract.Events.DTEND, maxOf(endIncl, start) + 86_400_000L)
            values.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            values.put(CalendarContract.Events.ALL_DAY, 1)
        } else {
            val start = j.optLong("start"); val end = j.optLong("end")
            if (start <= 0) return fail("Invalid start time")
            values.put(CalendarContract.Events.DTSTART, start)
            values.put(CalendarContract.Events.DTEND, if (end > start) end else start + 3_600_000L)
            values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            values.put(CalendarContract.Events.ALL_DAY, 0)
        }
        values.put(CalendarContract.Events.TITLE, title)
        values.put(CalendarContract.Events.EVENT_LOCATION, j.optString("location"))
        values.put(CalendarContract.Events.DESCRIPTION, j.optString("description"))
        val reminder = j.optInt("reminder", -1)
        values.put(CalendarContract.Events.HAS_ALARM, if (reminder >= 0) 1 else 0)
        val resolver = context.contentResolver
        return try {
            val existing = j.optLong("id", 0L)
            val eventId: Long
            if (existing > 0) {
                if (j.optBoolean("recurring")) return fail("Repeating events can only be edited on the phone")
                resolver.update(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing), values, null, null)
                eventId = existing
                resolver.delete(CalendarContract.Reminders.CONTENT_URI, "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(existing.toString()))
            } else {
                val cal = j.optLong("calendarId", 0L).takeIf { it > 0 } ?: defaultCalendarId() ?: return fail("No writable calendar on the phone")
                values.put(CalendarContract.Events.CALENDAR_ID, cal)
                val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return fail("Could not create event")
                eventId = uri.lastPathSegment!!.toLong()
            }
            if (reminder >= 0) {
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, reminder)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                })
            }
            ok().put("id", eventId)
        } catch (e: Exception) { fail("Could not save: ${e.message}") }
    }

    fun deleteEvent(id: Long): JSONObject {
        if (!has(android.Manifest.permission.WRITE_CALENDAR)) return fail("Calendar write permission not granted on the phone")
        return try {
            val n = context.contentResolver.delete(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
            if (n > 0) ok() else fail("Event not found")
        } catch (e: Exception) { fail("Could not delete: ${e.message}") }
    }

    // ───────────────────────────── Birthdays ─────────────────────────────

    /** Contacts with a birthday, soonest first, with days remaining and the age they turn. */
    fun birthdays(): JSONArray {
        val list = contactList().mapNotNull { c ->
            val raw = c.birthday ?: c.events.firstOrNull { it.type == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY }?.date
            if (raw.isNullOrBlank()) return@mapNotNull null
            val f = formatContactDate(raw, isBirthday = true) ?: return@mapNotNull null
            if (f.daysRemaining < 0) return@mapNotNull null
            c to f
        }.sortedBy { it.second.daysRemaining }
        val arr = JSONArray()
        list.forEach { (c, f) ->
            arr.put(JSONObject()
                .put("id", c.id)
                .put("name", c.name)
                .put("photo", c.photoUri != null)
                .put("date", f.naturalDate)
                .put("days", f.daysRemaining)
                .put("turning", Regex("""\d+""").find(f.ageText ?: "")?.value?.toInt() ?: JSONObject.NULL)
                .put("numbers", JSONArray().apply { c.phoneNumbers.forEach { put(JSONObject().put("number", it.number).put("label", it.label)) } }))
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

    // ───────────────────────────── Gallery ─────────────────────────────

    private fun hasMedia() = if (Build.VERSION.SDK_INT >= 33)
        has(android.Manifest.permission.READ_MEDIA_IMAGES) || has(android.Manifest.permission.READ_MEDIA_VIDEO)
    else has(android.Manifest.permission.READ_EXTERNAL_STORAGE)

    /** Photos and videos, newest first. */
    fun photos(offset: Int, limit: Int): JSONObject {
        val arr = JSONArray()
        if (!hasMedia()) return JSONObject().put("items", arr).put("permission", false)
        var total = 0
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.DATE_ADDED, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.WIDTH,
                    MediaStore.Files.FileColumns.HEIGHT, MediaStore.Files.FileColumns.MEDIA_TYPE, "datetaken", "duration"),
                sel, null, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { c ->
                total = c.count
                if (c.moveToPosition(offset)) {
                    do {
                        val taken = c.getLong(8)
                        arr.put(JSONObject()
                            .put("id", c.getLong(0))
                            .put("name", c.getString(1) ?: "")
                            .put("mime", c.getString(2) ?: "")
                            .put("date", if (taken > 0) taken else c.getLong(3) * 1000)
                            .put("size", c.getLong(4))
                            .put("w", c.getInt(5)).put("h", c.getInt(6))
                            .put("video", c.getInt(7) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                            .put("duration", c.getLong(9)))
                    } while (arr.length() < limit && c.moveToNext())
                }
            }
        } catch (e: Exception) { android.util.Log.w("SymbianBridge", "Gallery query failed", e) }
        return JSONObject().put("items", arr).put("total", total).put("permission", true)
    }

    private fun mediaUri(id: Long, video: Boolean, audio: Boolean = false): Uri = android.content.ContentUris.withAppendedId(
        when { audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI; video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI; else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI }, id)

    private fun hasAudio() = if (Build.VERSION.SDK_INT >= 33) has(android.Manifest.permission.READ_MEDIA_AUDIO)
        else has(android.Manifest.permission.READ_EXTERNAL_STORAGE)

    private val numberInName = Regex("""\+?\d[\d ]{4,}\d""")

    /** Call recordings made by the phone's own dialer (any audio file in a "call" folder), newest first. */
    fun callRecordings(): JSONObject {
        val arr = JSONArray()
        if (!hasAudio()) return JSONObject().put("items", arr).put("permission", false)
        try {
            val sel = if (Build.VERSION.SDK_INT >= 29) "lower(${MediaStore.MediaColumns.RELATIVE_PATH}) LIKE '%call%' OR lower(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE '%call%'"
                else "lower(${MediaStore.MediaColumns.DATA}) LIKE '%call%'"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE),
                sel, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext() && arr.length() < 500) {
                    val name = c.getString(1) ?: ""
                    // File names usually hold the number plus a date/time; skip the date-like parts.
                    val number = numberInName.findAll(name).map { it.value.replace(" ", "") }
                        .filterNot { Regex("""(19|20)\d{6}\d*""").matches(it) || it.length == 6 }
                        .sortedByDescending { it.startsWith("+") }.firstOrNull()
                    arr.put(JSONObject()
                        .put("id", c.getLong(0)).put("name", name)
                        .put("date", c.getLong(2) * 1000).put("duration", c.getLong(3))
                        .put("size", c.getLong(4)).put("mime", c.getString(5) ?: "audio/mp4")
                        .put("number", number ?: "")
                        .put("contact", if (number != null) nameFor(number) else ""))
                }
            }
        } catch (e: Exception) { android.util.Log.w("SymbianBridge", "Recordings query failed", e) }
        return JSONObject().put("items", arr).put("permission", true)
    }

    private val thumbCache = object : android.util.LruCache<Long, ByteArray>(6 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: ByteArray) = value.size
    }

    fun thumbnail(id: Long, video: Boolean): ByteArray? {
        if (!hasMedia()) return null
        thumbCache.get(id)?.let { return it }
        return try {
            val bmp = if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(mediaUri(id, video), android.util.Size(360, 360), null)
            } else {
                @Suppress("DEPRECATION")
                if (video) MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
            } ?: return null
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray().also { thumbCache.put(id, it) }
        } catch (_: Exception) { null }
    }

    data class MediaFile(val input: InputStream, val length: Long, val mime: String, val name: String)

    fun openMedia(id: Long, video: Boolean, audio: Boolean = false): MediaFile? {
        if (if (audio) !hasAudio() else !hasMedia()) return null
        return try {
            val uri = mediaUri(id, video, audio)
            var name = "file"; var size = -1L; var mime = when { audio -> "audio/mp4"; video -> "video/mp4"; else -> "image/jpeg" }
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE), null, null, null)?.use { c ->
                if (c.moveToFirst()) { name = c.getString(0) ?: name; size = c.getLong(1); mime = c.getString(2) ?: mime }
            }
            val input = context.contentResolver.openInputStream(uri) ?: return null
            MediaFile(input, size, mime, name)
        } catch (_: Exception) { null }
    }

    // ───────────────────────────── Wallet ─────────────────────────────

    val wallet by lazy { WalletRepository(context) }

    fun walletPasses(): JSONArray {
        val arr = JSONArray()
        wallet.passes.value.forEach { p ->
            try {
                val j = JSONObject(p.toJsonString())
                j.remove("stripBitmapBase64") // large; the desktop draws its own card
                arr.put(j)
            } catch (_: Exception) { }
        }
        return arr
    }

    fun walletBarcode(id: String): ByteArray? {
        val p = wallet.passes.value.firstOrNull { it.id == id } ?: return null
        val b = p.barcode ?: return null
        val square = b.format == BarcodeFormatType.QR_CODE || b.format == BarcodeFormatType.AZTEC
        val bmp = BarcodeUtils.generateBarcodeBitmap(b.message, b.format, 600, if (square) 600 else 240) ?: return null
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /**
     * Imports a ticket file: Apple Wallet .pkpass, a PDF ticket (barcode read from its pages),
     * or a screenshot/photo of a barcode.
     */
    fun walletImport(name: String, input: InputStream, length: Long, title: String?): JSONObject {
        if (length <= 0 || length > 25_000_000) return fail("File is empty or larger than 25 MB")
        val bytes = ByteArray(length.toInt()); var read = 0
        while (read < bytes.size) { val n = input.read(bytes, read, bytes.size - read); if (n < 0) break; read += n }
        val lower = name.lowercase()
        val isZip = bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val isPdf = bytes.size > 4 && String(bytes, 0, 4) == "%PDF"
        val pass: WalletPass? = try {
            when {
                isZip || lower.endsWith(".pkpass") -> PkPassParser.parsePkPass(bytes.inputStream())
                isPdf || lower.endsWith(".pdf") -> passFromPdf(bytes, title)
                else -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bmp ->
                    BarcodeUtils.decodeBarcodeFromBitmap(bmp)?.let { BarcodeUtils.createPassFromScannedBarcode(it, title?.ifBlank { null }) }
                }
            }
        } catch (e: Exception) { android.util.Log.w("SymbianBridge", "Wallet import failed", e); null }
        if (pass == null) {
            return fail(when {
                isZip || lower.endsWith(".pkpass") -> "Couldn't read this .pkpass file"
                isPdf -> "No barcode found in the PDF"
                else -> "No barcode found in the image"
            })
        }
        val finalPass = if (!title.isNullOrBlank() && (isZip || lower.endsWith(".pkpass")).not()) pass.copy(organizationName = title) else pass
        runBlocking { wallet.addPass(finalPass) }
        return ok().put("id", finalPass.id).put("name", finalPass.organizationName)
    }

    private fun passFromPdf(bytes: ByteArray, title: String?): WalletPass? {
        val tmp = java.io.File(context.cacheDir, "ticket-${System.nanoTime()}.pdf")
        try {
            tmp.writeBytes(bytes)
            android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                android.graphics.pdf.PdfRenderer(fd).use { r ->
                    for (i in 0 until minOf(r.pageCount, 6)) {
                        r.openPage(i).use { page ->
                            // Render at ~200 dpi so small QR/Aztec codes stay readable.
                            val scale = 200f / 72f
                            val w = (page.width * scale).toInt().coerceAtMost(2400)
                            val h = (page.height * scale).toInt().coerceAtMost(3400)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val found = BarcodeUtils.decodeBarcodeFromBitmap(bmp)
                            bmp.recycle()
                            if (found != null) return BarcodeUtils.createPassFromScannedBarcode(found, title?.ifBlank { null })
                        }
                    }
                }
            }
        } finally { tmp.delete() }
        return null
    }

    /** A card or ticket typed in on the desktop. */
    fun walletAddManual(j: JSONObject): JSONObject {
        val org = j.optString("name").trim()
        val code = j.optString("code").trim()
        if (org.isEmpty()) return fail("Give the card a name")
        val type = runCatching { PassType.valueOf(j.optString("type", "LOYALTY_CARD")) }.getOrDefault(PassType.LOYALTY_CARD)
        val format = runCatching { BarcodeFormatType.valueOf(j.optString("format", "QR_CODE")) }.getOrDefault(BarcodeFormatType.QR_CODE)
        val color = j.optString("color").removePrefix("#").toLongOrNull(16)?.let { 0xFF000000 or it } ?: 0xFF1C2B39
        fun f(key: String, label: String) = j.optString(key).trim().takeIf { it.isNotEmpty() }?.let { PassField(key, label, it) }
        val pass = WalletPass(
            id = java.util.UUID.randomUUID().toString(),
            type = type,
            organizationName = org,
            description = j.optString("description").ifBlank { type.displayName },
            logoText = org.uppercase(),
            backgroundColor = color,
            headerFields = listOfNotNull(f("date", "DATE")),
            primaryFields = listOfNotNull(f("number", if (type == PassType.LOYALTY_CARD) "MEMBER ID" else "TICKET")),
            secondaryFields = listOfNotNull(f("holder", "NAME"), f("seat", "SEAT")),
            auxiliaryFields = listOfNotNull(f("venue", "PLACE")),
            backFields = listOfNotNull(f("notes", "NOTES")),
            barcode = if (code.isNotEmpty()) PassBarcode(code, format, j.optString("number").ifBlank { code }) else null
        )
        runBlocking { wallet.addPass(pass) }
        return ok().put("id", pass.id)
    }

    private fun hexColor(s: String?, fallback: Long): Long =
        s?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it } ?: fallback

    /** Edits a card: name, type, colours, barcode and every field's label/value. */
    fun walletUpdate(j: JSONObject): JSONObject {
        val p = wallet.passes.value.firstOrNull { it.id == j.optString("id") } ?: return fail("Card not found")
        fun fields(key: String, old: List<PassField>): List<PassField> {
            val arr = j.optJSONArray(key) ?: return old
            return (0 until arr.length()).mapNotNull { i ->
                val f = arr.optJSONObject(i) ?: return@mapNotNull null
                val value = f.optString("value").trim()
                if (value.isEmpty()) null else PassField(f.optString("key").ifBlank { "f$i" }, f.optString("label").trim(), value)
            }
        }
        val code = j.optString("code", p.barcode?.message ?: "").trim()
        val format = runCatching { BarcodeFormatType.valueOf(j.optString("format")) }.getOrNull() ?: p.barcode?.format ?: BarcodeFormatType.QR_CODE
        val name = j.optString("name").trim().ifEmpty { p.organizationName }
        val updated = p.copy(
            organizationName = name,
            logoText = if (j.has("name")) name.uppercase() else p.logoText,
            description = j.optString("description", p.description),
            type = runCatching { PassType.valueOf(j.optString("type")) }.getOrNull() ?: p.type,
            backgroundColor = hexColor(j.optString("bg"), p.backgroundColor),
            foregroundColor = hexColor(j.optString("fg"), p.foregroundColor),
            labelColor = hexColor(j.optString("label"), p.labelColor),
            headerFields = fields("headerFields", p.headerFields),
            primaryFields = fields("primaryFields", p.primaryFields),
            secondaryFields = fields("secondaryFields", p.secondaryFields),
            auxiliaryFields = fields("auxiliaryFields", p.auxiliaryFields),
            backFields = fields("backFields", p.backFields),
            barcode = if (code.isEmpty()) null else PassBarcode(code, format, j.optString("altText", p.barcode?.altText ?: code).ifBlank { null })
        )
        runBlocking { wallet.addPass(updated) }
        return ok()
    }

    /** Scales any image to a logo-sized PNG and returns it base64-encoded. */
    private fun logoFromBytes(bytes: ByteArray): String? {
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val scale = minOf(1f, 320f / src.width, 160f / src.height)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        return android.util.Base64.encodeToString(png, android.util.Base64.NO_WRAP)
    }

    private fun setLogo(id: String, base64: String?): JSONObject {
        val p = wallet.passes.value.firstOrNull { it.id == id } ?: return fail("Card not found")
        runBlocking { wallet.addPass(p.copy(logoBitmapBase64 = base64)) }
        return ok()
    }

    private fun httpGet(url: String, limit: Int = 4_000_000): ByteArray? = try {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 8000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "SymbianLauncher/2.0 (logo search)")
        if (c.responseCode in 200..299) c.inputStream.use { s ->
            val out = ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var total = 0
            while (true) { val n = s.read(buf); if (n < 0) break; total += n; if (total > limit) return null; out.write(buf, 0, n) }
            out.toByteArray()
        } else null
    } catch (_: Exception) { null }

    /**
     * Logo candidates for a brand: Wikimedia Commons logo files, plus the brand's website icons
     * (official site from DuckDuckGo when known, otherwise guessed .no/.com domains).
     */
    fun walletLogoSearch(query: String): JSONArray {
        val q = query.trim()
        val out = JSONArray(); val seen = HashSet<String>()
        fun add(url: String, source: String, label: String) { if (seen.add(url)) out.put(JSONObject().put("url", url).put("source", source).put("label", label)) }
        if (q.isEmpty()) return out
        val enc = java.net.URLEncoder.encode(q, "UTF-8")
        // 1. Brand websites
        val domains = LinkedHashSet<String>()
        httpGet("https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q=$enc")?.let { b ->
            try {
                val j = JSONObject(String(b))
                j.optJSONObject("Infobox")?.optJSONArray("content")?.let { a ->
                    for (i in 0 until a.length()) {
                        val e = a.optJSONObject(i) ?: continue
                        if (e.optString("data_type") == "official_website" || e.optString("label").equals("Website", true)) {
                            Uri.parse(e.optString("value").let { if (it.startsWith("http")) it else "https://$it" }).host?.let { domains += it.removePrefix("www.") }
                        }
                    }
                }
                j.optJSONArray("Results")?.optJSONObject(0)?.optString("FirstURL")?.let { Uri.parse(it).host?.let { h -> domains += h.removePrefix("www.") } }
            } catch (_: Exception) { }
        }
        val slug = java.text.Normalizer.normalize(q.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").replace("æ", "ae").replace("ø", "o").replace("å", "a").replace(Regex("[^a-z0-9]"), "")
        if (slug.isNotEmpty()) { domains += "$slug.no"; domains += "$slug.com" }
        domains.take(4).forEach { d ->
            add("https://www.google.com/s2/favicons?sz=256&domain=$d", d, "Website icon")
            add("https://$d/apple-touch-icon.png", d, "App icon")
        }
        // 2. Wikimedia Commons logo files
        httpGet("https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=12" +
                "&gsrsearch=" + java.net.URLEncoder.encode("$q logo", "UTF-8") + "&prop=imageinfo&iiprop=url&iiurlwidth=320")?.let { b ->
            try {
                val pages = JSONObject(String(b)).optJSONObject("query")?.optJSONObject("pages")
                pages?.keys()?.asSequence()?.mapNotNull { pages.optJSONObject(it) }?.sortedBy { it.optInt("index") }?.forEach { pg ->
                    val info = pg.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@forEach
                    val url = info.optString("thumburl").ifEmpty { info.optString("url") }
                    if (url.isNotEmpty()) add(url, "Wikimedia Commons", pg.optString("title").removePrefix("File:"))
                }
            } catch (_: Exception) { }
        }
        return out
    }

    fun walletSetLogoFromUrl(id: String, url: String): JSONObject {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return fail("Invalid address")
        val bytes = httpGet(url) ?: return fail("Couldn't download that image")
        val b64 = logoFromBytes(bytes) ?: return fail("That isn't an image the phone can read")
        return setLogo(id, b64)
    }

    fun walletSetLogoFromUpload(id: String, input: InputStream, length: Long): JSONObject {
        if (length <= 0 || length > 8_000_000) return fail("Image is empty or too large")
        val bytes = ByteArray(length.toInt()); var read = 0
        while (read < bytes.size) { val n = input.read(bytes, read, bytes.size - read); if (n < 0) break; read += n }
        val b64 = logoFromBytes(bytes) ?: return fail("That isn't an image the phone can read")
        return setLogo(id, b64)
    }

    fun walletClearLogo(id: String) = setLogo(id, null)

    fun walletDelete(id: String): JSONObject {
        if (wallet.passes.value.none { it.id == id }) return fail("Card not found")
        runBlocking { wallet.removePass(id) }
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

    fun clipboardState(): JSONObject = JSONObject()
        .put("sync", ClipboardHub.syncEnabled(context))
        .put("instant", ClipboardHub.instantAvailable(context))
        .put("readLogs", ClipboardHub.canReadLogs(context))
        .put("overlay", ClipboardHub.canOverlay(context))
        .put("package", context.packageName)
        .put("history", ClipboardHub.historyJson())

    fun clipboardSettings(j: JSONObject): JSONObject {
        ClipboardHub.setSync(context, j.optBoolean("sync"))
        return clipboardState().put("ok", true)
    }

    fun setClipboard(text: String): JSONObject {
        if (text.isEmpty()) return fail("Nothing to copy")
        ClipboardHub.fromComputer(context, text)
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
