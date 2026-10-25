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
    private val pool = Executors.newFixedThreadPool(16)
    private val sseSlots = Semaphore(6)
    private val clients = CopyOnWriteArrayList<SseClient>()
    @Volatile private var running = false

    // Pairing brute-force protection.
    private var failedPairs = 0
    private var lockedUntil = 0L

    private class SseClient(val socket: Socket, val out: OutputStream, val token: String) {
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
                // Disconnect browsers that were forgotten on the phone.
                clients.filter { !BridgeState.isValidToken(context, it.token) }.forEach { c ->
                    clients.remove(c); sseSlots.release(); try { c.socket.close() } catch (_: Exception) { }
                }
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
                val factory = BridgeTls.sslContext(context).socketFactory
                val ssl = factory.createSocket(raw, raw.inetAddress.hostAddress, raw.port, true) as javax.net.ssl.SSLSocket
                ssl.useClientMode = false
                ssl.startHandshake()
                socket = ssl
            }
            socket.soTimeout = 30_000
            val input = BufferedInputStream(socket.getInputStream(), 16 * 1024)
            val out = socket.getOutputStream()
            val req = parse(input, socket.inetAddress) ?: return
            if (req.method == "GET" && req.path == "/api/mirror/stream") {
                if (!BridgeState.isValidToken(context, req.token)) { send(out, 401, "text/plain", ByteArray(0)); return }
                socket.soTimeout = 0
                keepOpen = true
                val s = socket
                Thread({ try { ScreenMirror.stream(out) } finally { try { s.close(); raw.close() } catch (_: Exception) { } } }, "mirror-stream").start()
                return
            }
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
            "GET" to "mirror" -> ScreenMirror.status(context)
            "GET" to "apps" -> data.appsList()
            "POST" to "customicon" -> when (seg.getOrNull(1)) {
                "delete" -> data.removeCustomIcon(req.query["pkg"] ?: "")
                else -> if (req.contentLength > 10_000_000) BridgeData.fail("Picture is larger than 10 MB")
                    else data.setCustomIcon(req.query["pkg"] ?: "", req.body(10_000_000))
            }
            "POST" to "mirror" -> when (seg.getOrNull(1)) {
                "start" -> { ScreenMirror.requestStart(context); ScreenMirror.status(context) }
                "stop" -> { ScreenMirror.stop(context); BridgeData.ok() }
                "setup" -> {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    BridgeData.ok()
                }
                "input" -> {
                    val svc = MirrorInputService.instance
                    if (svc == null) BridgeData.fail("Control is off: turn on \"Symbian Desktop Mode control\" in the phone's Accessibility settings")
                    else { val j = req.json(); val ok = try { svc.handle(j) } catch (_: Exception) { false }; if (ok) BridgeData.ok() else BridgeData.fail("Not possible here") }
                }
                else -> BridgeData.fail("Unknown")
            }
            "GET" to "theme" -> data.theme()
            "GET" to "weather" -> data.weather() ?: JSONObject()
            "GET" to "contacts" -> data.contacts()
            "POST" to "contacts" -> when (seg.getOrNull(1)) {
                "delete" -> data.deleteContact(req.json().optString("id"))
                else -> data.saveContact(req.json())
            }
            "GET" to "photo" -> return bytes(out, data.contactPhoto(req.query["id"] ?: ""), "image/jpeg")
            "GET" to "icon" -> return bytes(out, data.appIcon(req.query["pkg"] ?: ""), "image/png")
            "GET" to "threads" -> data.smsThreads()
            "GET" to "search" -> data.searchMessages(req.query["q"] ?: "")
            "GET" to "messages" -> data.smsMessages(req.query["thread"]?.toLongOrNull() ?: -1)
            "GET" to "mmspart" -> return data.mmsPartFile(req.query["id"]?.toLongOrNull() ?: -1)?.let { streamFile(out, req, it) } ?: send(out, 404, "text/plain", ByteArray(0))
            "POST" to "sms" -> req.json().let { data.sendSms(it.optString("to"), it.optString("body")) }
            "POST" to "mms" -> {
                if (req.contentLength > 20_000_000) BridgeData.fail("Image is larger than 20 MB")
                else {
                    val img = if (req.contentLength > 0) req.body(20_000_000) else null
                    val err = MmsSender.send(context, req.query["to"] ?: "", req.query["text"], img)
                    if (err == null) BridgeData.ok() else BridgeData.fail(err)
                }
            }
            "GET" to "calls" -> data.callLog()
            "GET" to "recordings" -> data.callRecordings()
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
            "GET" to "wallet" -> when (seg.getOrNull(1)) {
                "barcode" -> return bytes(out, data.walletBarcode(req.query["id"] ?: ""), "image/png")
                "logos" -> data.walletLogoSearch(req.query["q"] ?: "")
                else -> data.walletPasses()
            }
            "POST" to "wallet" -> when (seg.getOrNull(1)) {
                "import" -> data.walletImport(req.query["name"] ?: "ticket", req.input, req.contentLength, req.query["title"])
                "add" -> data.walletAddManual(req.json())
                "delete" -> data.walletDelete(req.json().optString("id"))
                "update" -> data.walletUpdate(req.json())
                "logo" -> req.json().let { j -> if (j.optBoolean("clear")) data.walletClearLogo(j.optString("id")) else data.walletSetLogoFromUrl(j.optString("id"), j.optString("url")) }
                "logo-upload" -> data.walletSetLogoFromUpload(req.query["id"] ?: "", req.input, req.contentLength)
                else -> null
            }
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
            "GET" to "clipboard" -> data.clipboardState()
            "POST" to "clipboard" -> when (seg.getOrNull(1)) {
                "settings" -> data.clipboardSettings(req.json())
                else -> data.setClipboard(req.json().optString("text"))
            }
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
            val browser = when {
                ua.contains("Vivaldi") -> "Vivaldi"; ua.contains("Edg/") -> "Edge"; ua.contains("OPR/") -> "Opera"
                ua.contains("Firefox") -> "Firefox"; ua.contains("Chrome") -> "Chrome"; ua.contains("Safari") -> "Safari"; else -> "Browser"
            }
            val os = when {
                ua.contains("Windows") -> "Windows"; ua.contains("Mac OS") -> "Mac"; ua.contains("Android") -> "Android"
                ua.contains("CrOS") -> "ChromeOS"; ua.contains("Linux") -> "Linux"; else -> ""
            }
            val label = if (os.isEmpty()) browser else "$browser on $os"
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
        val client = SseClient(socket, out, req.token ?: "")
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
            "css" -> "text/css; charset=utf-8"; "svg" -> "image/svg+xml"; "png" -> "image/png"; "ttf" -> "font/ttf"
            else -> "application/octet-stream"
        }
        send(out, 200, type, bytes)
    }

    private fun streamMedia(out: OutputStream, req: Request) {
        val f = data.openMedia(req.query["id"]?.toLongOrNull() ?: -1, req.query["video"] == "1", req.query["audio"] == "1")
            ?: return send(out, 404, "text/plain", ByteArray(0))
        streamFile(out, req, f)
    }

    /**
     * Sends a file with HTTP range support, which browsers need to play and seek in video and
     * audio (they ask for pieces of the file rather than the whole thing).
     */
    private fun streamFile(out: OutputStream, req: Request, f: BridgeData.MediaFile) {
        var input: java.io.InputStream = f.input
        var total = f.length
        var afd: android.content.res.AssetFileDescriptor? = null
        try {
            if (f.uri != null) {
                afd = try { context.contentResolver.openAssetFileDescriptor(f.uri, "r") } catch (_: Exception) { null }
                if (afd != null && afd.length >= 0) { total = afd.length; try { f.input.close() } catch (_: Exception) { }; input = afd.createInputStream() }
                else if (total < 0 && afd != null) {
                    val st = afd.parcelFileDescriptor.statSize
                    if (st > 0) { total = st; try { f.input.close() } catch (_: Exception) { }; input = afd.createInputStream() }
                }
            }
            val disposition = if (req.query["download"] == "1") "attachment" else "inline"
            val safe = f.name.replace("\"", "")
            val range = req.headers["range"]?.let { Regex("""bytes=(\d*)-(\d*)""").find(it) }
            val head = StringBuilder()
            var start = 0L; var end = total - 1
            if (range != null && total > 0) {
                val a = range.groupValues[1]; val b = range.groupValues[2]
                if (a.isEmpty() && b.isNotEmpty()) { start = (total - b.toLong()).coerceAtLeast(0) }
                else { start = a.toLongOrNull() ?: 0; if (b.isNotEmpty()) end = minOf(b.toLong(), total - 1) }
                if (start > end || start >= total) {
                    out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush(); return
                }
                head.append("HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $start-$end/$total\r\nContent-Length: ${end - start + 1}\r\n")
            } else {
                head.append("HTTP/1.1 200 OK\r\n")
                if (total >= 0) head.append("Content-Length: $total\r\n")
            }
            head.append("Content-Type: ${f.mime}\r\nAccept-Ranges: bytes\r\nContent-Disposition: $disposition; filename=\"$safe\"\r\n")
                .append("Cache-Control: private, max-age=86400\r\nConnection: close\r\n\r\n")
            out.write(head.toString().toByteArray())
            if (start > 0) { var left = start; while (left > 0) { val n = input.skip(left); if (n <= 0) break; left -= n } }
            var remaining = if (range != null && total > 0) end - start + 1 else Long.MAX_VALUE
            val buf = ByteArray(64 * 1024)
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n < 0) break
                out.write(buf, 0, n); remaining -= n
            }
            out.flush()
        } finally {
            try { input.close() } catch (_: Exception) { }
            try { afd?.close() } catch (_: Exception) { }
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
