package com.example

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Screen mirroring for Desktop Mode: the phone's screen as a live JPEG stream (MJPEG) that the
 * browser shows in an <img>. Frames are only produced when the screen changes and someone watches,
 * at most ~12 per second, and capture stops by itself shortly after the last viewer leaves.
 */
object ScreenMirror {
    @Volatile var running = false
    @Volatile var width = 0
    @Volatile var height = 0
    @Volatile var awaitingConsent = false
    private val lock = Object()
    private var frame: ByteArray? = null
    private var seq = 0L
    val viewers = AtomicInteger(0)
    @Volatile var lastViewerAt = 0L

    fun status(ctx: Context): JSONObject = JSONObject()
        .put("running", running).put("width", width).put("height", height)
        .put("waiting", awaitingConsent && !running)
        .put("input", MirrorInputService.instance != null)

    /** Asks for capture permission on the phone (Android shows its own "share your screen" prompt). */
    fun requestStart(ctx: Context) {
        if (running) return
        awaitingConsent = true
        ctx.startActivity(Intent(ctx, MirrorConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MirrorService::class.java)) }

    internal fun publish(jpeg: ByteArray) = synchronized(lock) { frame = jpeg; seq++; lock.notifyAll() }
    internal fun ended() = synchronized(lock) { running = false; lock.notifyAll() }

    /** Writes frames to the browser until it disconnects or mirroring stops. */
    fun stream(out: OutputStream) {
        viewers.incrementAndGet(); lastViewerAt = SystemClock.elapsedRealtime()
        try {
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
            var last = -1L
            while (true) {
                val f: ByteArray
                synchronized(lock) {
                    while (running && (seq == last || frame == null)) lock.wait(2_000)
                    if (!running) return
                    f = frame!!; last = seq
                }
                out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.size}\r\n\r\n".toByteArray())
                out.write(f); out.write("\r\n".toByteArray()); out.flush()
                lastViewerAt = SystemClock.elapsedRealtime()
            }
        } catch (_: Exception) {
        } finally {
            viewers.decrementAndGet(); lastViewerAt = SystemClock.elapsedRealtime()
        }
    }
}

/** Invisible activity that shows Android's screen-capture prompt and hands the answer to [MirrorService]. */
class MirrorConsentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val i = if (Build.VERSION.SDK_INT >= 34) mpm.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
            else mpm.createScreenCaptureIntent()
        try { startActivityForResult(i, 1) } catch (_: Exception) { ScreenMirror.awaitingConsent = false; finish() }
    }

    @Deprecated("Uses the platform result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val s = Intent(this, MirrorService::class.java).putExtra("code", resultCode).putExtra("data", data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s) else startService(s)
        } else ScreenMirror.awaitingConsent = false
        finish()
    }
}

/** Foreground service that owns the screen capture (Android requires one while mirroring). */
class MirrorService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var bitmap: Bitmap? = null
    private var dirty = false
    private var lastEncode = 0L
    private var encodeQueued = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("mirror") == null)
            nm.createNotificationChannel(NotificationChannel("mirror", "Screen mirroring", NotificationManager.IMPORTANCE_LOW))
        val stop = android.app.PendingIntent.getService(this, 9101, Intent(this, MirrorService::class.java).setAction("stop"),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(this, "mirror")
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Screen shown in Desktop Mode")
            .setContentText("Your screen is visible in the paired browser.").setOngoing(true)
            .addAction(0, "Stop", stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(9100, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(9100, n)
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra("data")
        if (data == null || projection != null) { if (projection == null) stopSelf(); return START_NOT_STICKY }
        start(code, data)
        return START_NOT_STICKY
    }

    private fun start(code: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = try { mpm.getMediaProjection(code, data) } catch (_: Exception) { null } ?: run { stopSelf(); return }
        projection = mp
        val t = HandlerThread("mirror").apply { start() }; thread = t
        val h = Handler(t.looper); handler = h
        mp.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, h)

        @Suppress("DEPRECATION")
        val dm = android.util.DisplayMetrics().also { (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(it) }
        val scale = minOf(1f, 800f / maxOf(dm.widthPixels, dm.heightPixels))
        val w = (dm.widthPixels * scale).toInt() and 1.inv(); val hgt = (dm.heightPixels * scale).toInt() and 1.inv()
        ScreenMirror.width = dm.widthPixels; ScreenMirror.height = dm.heightPixels

        val r = ImageReader.newInstance(w, hgt, PixelFormat.RGBA_8888, 2); reader = r
        r.setOnImageAvailableListener({ rd ->
            val img = try { rd.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            try {
                if (ScreenMirror.viewers.get() > 0) {
                    val plane = img.planes[0]
                    val rowPx = plane.rowStride / plane.pixelStride
                    var b = bitmap
                    if (b == null || b.width != rowPx || b.height != img.height) { b = Bitmap.createBitmap(rowPx, img.height, Bitmap.Config.ARGB_8888); bitmap = b }
                    b.copyPixelsFromBuffer(plane.buffer)
                    dirty = true
                    scheduleEncode(w)
                }
            } finally { img.close() }
        }, h)
        display = mp.createVirtualDisplay("SymbianMirror", w, hgt, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, h)
        ScreenMirror.running = true
        ScreenMirror.awaitingConsent = false
        ScreenMirror.lastViewerAt = SystemClock.elapsedRealtime()
        h.postDelayed(watchdog, 5_000)
    }

    private fun scheduleEncode(visibleWidth: Int) {
        if (encodeQueued) return
        encodeQueued = true
        val wait = (80 - (SystemClock.elapsedRealtime() - lastEncode)).coerceAtLeast(0)
        handler?.postDelayed({
            encodeQueued = false
            val b = bitmap ?: return@postDelayed
            if (!dirty) return@postDelayed
            dirty = false; lastEncode = SystemClock.elapsedRealtime()
            val crop = if (b.width != visibleWidth) Bitmap.createBitmap(b, 0, 0, visibleWidth, b.height) else b
            val out = ByteArrayOutputStream(64 * 1024)
            crop.compress(Bitmap.CompressFormat.JPEG, 62, out)
            ScreenMirror.publish(out.toByteArray())
        }, wait)
    }

    /** Ends capture when nobody has watched for 20 seconds. */
    private val watchdog = object : Runnable {
        override fun run() {
            if (ScreenMirror.viewers.get() == 0 && SystemClock.elapsedRealtime() - ScreenMirror.lastViewerAt > 20_000) { stopSelf(); return }
            handler?.postDelayed(this, 5_000)
        }
    }

    override fun onDestroy() {
        ScreenMirror.ended(); ScreenMirror.awaitingConsent = false
        handler?.removeCallbacksAndMessages(null)
        try { display?.release() } catch (_: Exception) { }
        try { projection?.stop() } catch (_: Exception) { }
        try { reader?.close() } catch (_: Exception) { }
        thread?.quitSafely()
        display = null; projection = null; reader = null; bitmap = null
        super.onDestroy()
    }
}
