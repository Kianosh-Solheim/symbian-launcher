package com.example

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Sends a picture message (MMS) through the phone's own MMS service. Android's API takes a
 * ready-encoded message, so this builds a minimal WAP "m-send-req" PDU: one photo plus optional
 * text in a multipart/mixed body. The photo is resized to fit typical operator limits.
 */
object MmsSender {
    private const val MAX_IMAGE_BYTES = 280_000

    fun send(context: Context, to: String, text: String?, image: ByteArray?): String? {
        val number = to.filter { it.isDigit() || it == '+' }
        if (number.length < 3) return "Invalid recipient"
        if (image == null && text.isNullOrBlank()) return "Nothing to send"
        val jpeg = image?.let { shrink(it) ?: return "That image can't be read" }
        val pdu = buildPdu(number, text?.trim()?.ifEmpty { null }, jpeg)

        val dir = File(context.cacheDir, "mms").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000 }?.forEach { it.delete() }
        val file = File(dir, "send-${System.currentTimeMillis()}.pdu").apply { writeBytes(pdu) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", file)
        listOf("com.android.phone", "com.android.mms.service").forEach {
            try { context.grantUriPermission(it, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        }
        val sent = PendingIntent.getBroadcast(
            context, (System.nanoTime() % Int.MAX_VALUE).toInt(),
            Intent(BridgeService.ACTION_SMS_SENT).setPackage(context.packageName).putExtra("address", number).putExtra("mms", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
        return try {
            @Suppress("DEPRECATION")
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            sms.sendMultimediaMessage(context, uri, null, null, sent)
            null
        } catch (e: Exception) {
            "Could not send: ${e.message}"
        }
    }

    /** Re-encodes as JPEG, shrinking until it fits the operator size limit. */
    private fun shrink(bytes: ByteArray): ByteArray? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 2560) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        var maxSide = 1600
        while (true) {
            val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            for (q in intArrayOf(85, 75, 65, 55)) {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
                if (out.size() <= MAX_IMAGE_BYTES) return out.toByteArray()
            }
            if (maxSide <= 480) return null
            maxSide = (maxSide * 0.75f).toInt()
            bmp = scaled
        }
    }

    // ───────── WAP binary encoding (OMA MMS 1.2 encapsulation) ─────────

    private fun uintvar(v: Int): ByteArray {
        val bytes = ArrayList<Byte>(); var n = v
        bytes += (n and 0x7F).toByte(); n = n ushr 7
        while (n > 0) { bytes += ((n and 0x7F) or 0x80).toByte(); n = n ushr 7 }
        return bytes.reversed().toByteArray()
    }

    private fun valueLength(n: Int): ByteArray = if (n < 31) byteArrayOf(n.toByte()) else byteArrayOf(0x1F) + uintvar(n)

    private fun textString(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        val prefix = if (b.isNotEmpty() && (b[0].toInt() and 0xFF) >= 0x80) byteArrayOf(0x7F) else ByteArray(0)
        return prefix + b + byteArrayOf(0)
    }

    private fun buildPdu(to: String, text: String?, jpeg: ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        fun w(vararg b: Int) = b.forEach { out.write(it) }
        w(0x8C, 0x80)                                            // X-Mms-Message-Type: m-send-req
        w(0x98); out.write(textString("T" + java.lang.Long.toHexString(System.currentTimeMillis())))  // Transaction-ID
        w(0x8D, 0x92)                                            // MMS-Version 1.2
        w(0x89, 0x01, 0x81)                                      // From: insert-address-token
        w(0x97); out.write(textString("$to/TYPE=PLMN"))          // To
        w(0x84, 0xA3)                                            // Content-Type: application/vnd.wap.multipart.mixed

        val parts = ArrayList<Pair<ByteArray, ByteArray>>()     // headers to data
        if (jpeg != null) {
            // Content-Type image/jpeg (0x1E) with Name parameter, then Content-Location.
            val ct = byteArrayOf(0x9E.toByte(), 0x85.toByte()) + textString("photo.jpg")
            val headers = valueLength(ct.size) + ct + byteArrayOf(0x8E.toByte()) + textString("photo.jpg")
            parts += headers to jpeg
        }
        if (text != null) {
            // Content-Type text/plain (0x03) with charset utf-8 (106), then Content-Location.
            val ct = byteArrayOf(0x83.toByte(), 0x81.toByte(), 0xEA.toByte())
            val headers = valueLength(ct.size) + ct + byteArrayOf(0x8E.toByte()) + textString("text.txt")
            parts += headers to text.toByteArray(Charsets.UTF_8)
        }
        out.write(uintvar(parts.size))
        parts.forEach { (h, d) ->
            out.write(uintvar(h.size)); out.write(uintvar(d.size)); out.write(h); out.write(d)
        }
        return out.toByteArray()
    }
}
