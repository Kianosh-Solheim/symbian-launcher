package com.example

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A deliberately small HTTP/1.1 server for Desktop Mode. One request per connection,
 * JSON in and out, plus a Server-Sent Events stream for live updates. Only accepts
 * connections from the local network (Wi-Fi, USB tethering, hotspot) and requires a
 * token obtained by entering the pairing code shown on the phone.
 */
class BridgeServer(
    private val context: Context,
    private val data: BridgeData,
    private val onClientsChanged: (Int) -> Unit
) {
    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(8)
    private val sseSlots = Semaphore(6)
    private val clients = CopyOnWriteArrayList<SseClient>()
    @Volatile private var running = false

    // Pairing brute-force protection.
    private var failedPairs = 0
    private var lockedUntil = 0L

    private class SseClient(val socket: Socket, val out: OutputStream) {
        @Synchronized fun send(text: String): Boolean = try {
            out.write(text.toByteArray(Charsets.UTF_8)); out.flush(); true
        } catch (_: Exception) { false }
    }

    private class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val input: InputStream,
        val contentLength: Long,
        val remote: InetAddress
    ) {
        private var cached: ByteArray? = null
        fun body(limit: Int = 1_000_000): ByteArray {
            cached?.let { return it }
            val len = contentLength.coerceAtMost(limit.toLong()).toInt().coerceAtLeast(0)
            val buf = ByteArray(len); var read = 0
            while (read < len) { val n = input.read(buf, read, len - read); if (n < 0) break; read += n }
            return buf.also { cached = it }
        }
        fun json(): JSONObject = try { JSONObject(String(body(), Charsets.UTF_8)) } catch (_: Exception) { JSONObject() }
        val token: String? get() = headers["x-token"] ?: query["token"]
    }

    fun start() {
        if (running) return
        running = true
        thread(name = "bridge-accept", isDaemon = true) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(BridgeState.PORT), 32)
                server = ss
                BridgeState.running.value = true
                while (running) {
                    val socket = try { ss.accept() } catch (_: Exception) { if (running) continue else break }
                    if (!isLocal(socket.inetAddress)) { try { socket.close() } catch (_: Exception) { }; continue }
                    pool.execute { handle(socket) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Server stopped", e)
            } finally {
                BridgeState.running.value = false
            }
        }
        thread(name = "bridge-heartbeat", isDaemon = true) {
            while (running) {
                try { Thread.sleep(20_000) } catch (_: InterruptedException) { break }
                broadcastRaw(": ping\n\n")
            }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) { }
        clients.forEach { try { it.socket.close() } catch (_: Exception) { } }
        clients.clear()
        onClientsChanged(0)
        pool.shutdownNow()
        BridgeState.running.value = false
    }

    /** Pushes a named event with a JSON payload to every connected browser. */
    fun broadcast(event: String, payload: Any) = broadcastRaw("event: $event\ndata: $payload\n\n")

    private fun broadcastRaw(text: String) {
        var changed = false
        clients.forEach { c ->
            if (!c.send(text)) {
                clients.remove(c); changed = true
                try { c.socket.close() } catch (_: Exception) { }
                sseSlots.release()
            }
        }
        if (changed) onClientsChanged(clients.size)
    }

    private fun isLocal(addr: InetAddress): Boolean =
        addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress

    // ───────────────────────────── Request handling ─────────────────────────────

    private fun handle(raw: Socket) {
        var keepOpen = false
        var socket: Socket = raw
        try {
            raw.soTimeout = 30_000
            if (BridgeState.useHttps(context)) {
                // Look at the first byte: a TLS handshake starts with 0x16. Anything else is plain
                // HTTP typed into the address bar, which gets redirected to https://.
                val rawIn = raw.getInputStream()
                val first = rawIn.read()
                if (first < 0) return
                if (first == 0x16) {
                    val factory = BridgeTls.sslContext(context).socketFactory
                    socket = factory.createSocket(raw, java.io.ByteArrayInputStream(byteArrayOf(first.toByte())), true)
                    (socket as javax.net.ssl.SSLSocket).startHandshake()
                } else {
                    val plain = BufferedInputStream(java.io.SequenceInputStream(java.io.ByteArrayInputStream(byteArrayOf(first.toByte())), rawIn))
                    val req = parse(plain, raw.inetAddress) ?: return
                    val host = req.headers["host"] ?: "${raw.localAddress.hostAddress}:${BridgeState.PORT}"
                    raw.getOutputStream().apply {
                        write(("HTTP/1.1 301 Moved Permanently\r\nLocation: https://$host${req.path}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                        flush()
                    }
                    return
                }
            }
            socket.soTimeout = 30_000
            val input = BufferedInputStream(socket.getInputStream(), 16 * 1024)
            val out = socket.getOutputStream()
            val req = parse(input, socket.inetAddress) ?: return
            if (req.method == "GET" && req.path == "/api/events") {
                keepOpen = openEventStream(req, socket, out)
                return
            }
            route(req, out)
        } catch (e: Exception) {
            Log.w(TAG, "Request failed", e)
        } finally {
            if (!keepOpen) try { socket.close(); raw.close() } catch (_: Exception) { }
        }
    }

    private fun parse(input: InputStream, remote: InetAddress): Request? {
        val head = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            head.write(b)
            last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
            if (head.size() > 32_768) return null
        }
        val lines = head.toString("UTF-8").split("\r\n")
        val parts = lines.first().split(" ")
        if (parts.size < 2) return null
        val headers = HashMap<String, String>()
        lines.drop(1).forEach { l ->
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = HashMap<String, String>()
        target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach {
            val k = it.substringBefore('='); val v = it.substringAfter('=', "")
            query[URLDecoder.decode(k, "UTF-8")] = URLDecoder.decode(v, "UTF-8")
        }
        return Request(parts[0].uppercase(), URLDecoder.decode(path, "UTF-8"), query, headers, input,
            headers["content-length"]?.toLongOrNull() ?: 0L, remote)
    }

    private fun route(req: Request, out: OutputStream) {
        val p = req.path
        // Public: the page itself, its assets, and pairing.
        when {
            req.method == "GET" && (p == "/" || p == "/index.html") -> return asset(out, "desktop/index.html")
            req.method == "GET" && p.startsWith("/static/") -> return asset(out, "desktop/" + p.removePrefix("/static/"))
            req.method == "GET" && p == "/api/hello" -> return json(out, JSONObject()
                .put("app", "Symbian Launcher").put("paired", BridgeState.isValidToken(context, req.token))
                .put("device", android.os.Build.MODEL))
            req.method == "POST" && p == "/api/pair" -> return pair(req, out)
        }
        if (!BridgeState.isValidToken(context, req.token)) return send(out, 401, "application/json", """{"ok":false,"error":"not paired"}""".toByteArray())

        val seg = p.removePrefix("/api/").split('/')
        val r: Any? = when (req.method to seg[0]) {
            "GET" to "status" -> data.status()
            "GET" to "theme" -> data.theme()
            "GET" to "weather" -> data.weather() ?: JSONObject()
            "GET" to "contacts" -> data.contacts()
            "GET" to "photo" -> return bytes(out, data.contactPhoto(req.query["id"] ?: ""), "image/jpeg")
            "GET" to "icon" -> return bytes(out, data.appIcon(req.query["pkg"] ?: ""), "image/png")
            "GET" to "threads" -> data.smsThreads()
            "GET" to "messages" -> data.smsMessages(req.query["thread"]?.toLongOrNull() ?: -1)
            "GET" to "mmspart" -> return data.mmsPart(req.query["id"]?.toLongOrNull() ?: -1).let { bytes(out, it?.first, it?.second ?: "application/octet-stream") }
            "POST" to "sms" -> req.json().let { data.sendSms(it.optString("to"), it.optString("body")) }
            "GET" to "calls" -> data.callLog()
            "POST" to "call" -> when (seg.getOrNull(1)) {
                "answer" -> data.answerCall()
                "end" -> data.endCall()
                "speaker" -> data.setSpeaker(req.json().optBoolean("on"))
                else -> data.placeCall(req.json().optString("number"))
            }
            "GET" to "notifications" -> data.notifications()
            "POST" to "notifications" -> {
                val j = req.json()
                when (seg.getOrNull(1)) {
                    "reply" -> data.reply(j.optString("key"), j.optString("text"))
                    "action" -> data.runAction(j.optString("key"), j.optInt("index", -1))
                    "dismiss" -> data.dismiss(j.optString("key"))
                    "clear" -> data.dismissAll()
                    else -> null
                }
            }
            "GET" to "agenda" -> data.agenda()
            "GET" to "calendars" -> data.calendars()
            "GET" to "calendar" -> data.calendarEvents(req.query["from"]?.toLongOrNull() ?: 0L, req.query["to"]?.toLongOrNull() ?: 0L)
            "POST" to "calendar" -> when (seg.getOrNull(1)) {
                "delete" -> data.deleteEvent(req.json().optLong("id"))
                else -> data.saveEvent(req.json())
            }
            "GET" to "birthdays" -> data.birthdays()
            "GET" to "photos" -> data.photos(req.query["offset"]?.toIntOrNull() ?: 0, (req.query["limit"]?.toIntOrNull() ?: 60).coerceIn(1, 200))
            "GET" to "thumb" -> return bytes(out, data.thumbnail(req.query["id"]?.toLongOrNull() ?: -1, req.query["video"] == "1"), "image/jpeg")
            "GET" to "media-file" -> return streamMedia(out, req)
            "GET" to "backup" -> return send(out, 200, "application/json; charset=utf-8",
                SettingsBackup.export(context).toString(2).toByteArray(Charsets.UTF_8),
                extra = "Content-Disposition: attachment; filename=\"${SettingsBackup.fileName()}\"\r\n")
            "POST" to "restore" -> {
                val body = try { JSONObject(String(req.body(5_000_000), Charsets.UTF_8)) } catch (_: Exception) { null }
                val err = if (body == null) "Not a valid backup file" else SettingsBackup.import(context, body)
                if (err == null) {
                    json(out, BridgeData.ok())
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ SettingsBackup.restart(context) }, 800)
                    return
                }
                BridgeData.fail(err)
            }
            "GET" to "media" -> data.media()
            "POST" to "media" -> data.mediaCommand(seg.getOrNull(1) ?: "")
            "POST" to "ring" -> data.ringPhone(req.json().optBoolean("on", true))
            "POST" to "clipboard" -> data.setClipboard(req.json().optString("text"))
            "POST" to "send" -> data.sendToPhone(req.json().optString("text"))
            "POST" to "upload" -> data.saveUpload(
                req.query["name"] ?: "upload", req.headers["content-type"] ?: "", req.input, req.contentLength.coerceAtMost(1L shl 31))
            else -> null
        }
        if (r == null) send(out, 404, "application/json", """{"ok":false,"error":"not found"}""".toByteArray())
        else json(out, r)
    }

    private fun pair(req: Request, out: OutputStream) {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return json(out, BridgeData.fail("Too many attempts — wait a minute"))
        val code = req.json().optString("code").filter { it.isDigit() }
        if (code.isNotEmpty() && code == BridgeState.pairingCode.value) {
            failedPairs = 0
            val ua = req.headers["user-agent"] ?: "Browser"
            val label = Regex("(Firefox|Edg|Chrome|Safari|Vivaldi)").find(ua)?.value ?: "Browser"
            json(out, BridgeData.ok().put("token", BridgeState.issueToken(context, label)))
        } else {
            failedPairs++
            if (failedPairs >= 5) { lockedUntil = now + 60_000; failedPairs = 0 }
            json(out, BridgeData.fail("Wrong code"))
        }
    }

    private fun openEventStream(req: Request, socket: Socket, out: OutputStream): Boolean {
        if (!BridgeState.isValidToken(context, req.token)) {
            send(out, 401, "text/plain", "not paired".toByteArray()); return false
        }
        if (!sseSlots.tryAcquire(0, TimeUnit.SECONDS)) {
            send(out, 503, "text/plain", "too many clients".toByteArray()); return false
        }
        socket.soTimeout = 0
        socket.keepAlive = true
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\n" +
            "Cache-Control: no-cache\r\nConnection: keep-alive\r\nX-Accel-Buffering: no\r\n\r\n").toByteArray())
        out.write("retry: 3000\n\n".toByteArray())
        out.flush()
        val client = SseClient(socket, out)
        clients += client
        onClientsChanged(clients.size)
        client.send("event: status\ndata: ${data.status()}\n\n")
        return true
    }

    // ───────────────────────────── Responses ─────────────────────────────

    private fun json(out: OutputStream, body: Any) {
        val text = when (body) { is JSONObject, is JSONArray -> body.toString(); else -> body.toString() }
        send(out, 200, "application/json; charset=utf-8", text.toByteArray(Charsets.UTF_8))
    }

    private fun bytes(out: OutputStream, data: ByteArray?, type: String) {
        if (data == null) send(out, 404, "text/plain", ByteArray(0))
        else send(out, 200, type, data, cache = true)
    }

    private fun asset(out: OutputStream, name: String) {
        if (name.contains("..")) return send(out, 404, "text/plain", ByteArray(0))
        val bytes = try { context.assets.open(name).use { it.readBytes() } } catch (_: Exception) { null }
            ?: return send(out, 404, "text/plain", "not found".toByteArray())
        val type = when (name.substringAfterLast('.')) {
            "html" -> "text/html; charset=utf-8"; "js" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"; "svg" -> "image/svg+xml"; "png" -> "image/png"
            else -> "application/octet-stream"
        }
        send(out, 200, type, bytes)
    }

    private fun streamMedia(out: OutputStream, req: Request) {
        val f = data.openMedia(req.query["id"]?.toLongOrNull() ?: -1, req.query["video"] == "1")
            ?: return send(out, 404, "text/plain", ByteArray(0))
        f.input.use { input ->
            val disposition = if (req.query["download"] == "1") "attachment" else "inline"
            val safe = f.name.replace("\"", "")
            val head = StringBuilder("HTTP/1.1 200 OK\r\nContent-Type: ${f.mime}\r\n")
            if (f.length >= 0) head.append("Content-Length: ${f.length}\r\n")
            head.append("Content-Disposition: $disposition; filename=\"$safe\"\r\nCache-Control: private, max-age=86400\r\nConnection: close\r\n\r\n")
            out.write(head.toString().toByteArray())
            input.copyTo(out, 64 * 1024)
            out.flush()
        }
    }

    private fun send(out: OutputStream, status: Int, type: String, body: ByteArray, cache: Boolean = false, extra: String = "") {
        val reason = when (status) { 200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; 503 -> "Service Unavailable"; else -> "Error" }
        val headers = StringBuilder()
            .append("HTTP/1.1 $status $reason\r\n")
            .append("Content-Type: $type\r\n")
            .append("Content-Length: ${body.size}\r\n")
            .append("Connection: close\r\n")
            .append("X-Content-Type-Options: nosniff\r\n")
            .append("Referrer-Policy: no-referrer\r\n")
            .append(if (cache) "Cache-Control: private, max-age=86400\r\n" else "Cache-Control: no-store\r\n")
            .append(extra)
            .append("\r\n")
        out.write(headers.toString().toByteArray())
        out.write(body)
        out.flush()
    }

    companion object { private const val TAG = "SymbianBridge" }
}
