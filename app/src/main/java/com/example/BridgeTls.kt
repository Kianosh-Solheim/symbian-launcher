package com.example

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * HTTPS for Desktop Mode. Creates a self-signed certificate on first use (stored privately on
 * the phone) and keeps it, so a browser only has to accept it once. The fingerprint is shown on
 * the phone so it can be compared with what the browser reports.
 */
object BridgeTls {
    private const val KEY_FILE = "desktop_tls_key.der"
    private const val CERT_FILE = "desktop_tls_cert.der"
    @Volatile private var context: SSLContext? = null
    @Volatile private var cert: X509Certificate? = null

    fun sslContext(ctx: Context): SSLContext {
        context?.let { return it }
        synchronized(this) {
            context?.let { return it }
            val (key, certificate) = loadOrCreate(ctx)
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            val pass = CharArray(0)
            ks.setKeyEntry("desktop", key, pass, arrayOf(certificate))
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, pass) }
            val sc = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, SecureRandom()) }
            cert = certificate
            context = sc
            return sc
        }
    }

    /** SHA-256 fingerprint as shown by browsers, e.g. "AB:12:…". */
    fun fingerprint(ctx: Context): String = try {
        val c = cert ?: loadOrCreate(ctx).second
        MessageDigest.getInstance("SHA-256").digest(c.encoded).joinToString(":") { "%02X".format(it) }
    } catch (_: Exception) { "" }

    private fun loadOrCreate(ctx: Context): Pair<PrivateKey, X509Certificate> {
        val kf = File(ctx.filesDir, KEY_FILE)
        val cf = File(ctx.filesDir, CERT_FILE)
        if (kf.exists() && cf.exists()) {
            try {
                val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
                val c = CertificateFactory.getInstance("X.509").generateCertificate(cf.inputStream()) as X509Certificate
                if (c.notAfter.after(Date())) return key to c
            } catch (_: Exception) { }
        }
        val kpg = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val kp = kpg.generateKeyPair()
        val der = buildCertificate(kp.public.encoded, kp.private)
        val c = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
        kf.writeBytes(kp.private.encoded)
        cf.writeBytes(der)
        return kp.private to c
    }

    // ───────── Minimal DER encoding for one self-signed X.509 v3 certificate ─────────

    private fun tlv(tag: Int, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val n = body.size
        when {
            n < 0x80 -> out.write(n)
            n < 0x100 -> { out.write(0x81); out.write(n) }
            n < 0x10000 -> { out.write(0x82); out.write(n shr 8); out.write(n and 0xFF) }
            else -> { out.write(0x83); out.write(n shr 16); out.write((n shr 8) and 0xFF); out.write(n and 0xFF) }
        }
        out.write(body)
        return out.toByteArray()
    }
    private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun set(vararg parts: ByteArray) = tlv(0x31, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun int(v: BigInteger) = tlv(0x02, v.toByteArray())
    private fun oid(vararg arcs: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * 40 + arcs[1]).toInt())
        for (i in 2 until arcs.size) {
            var v = arcs[i]; val stack = ArrayList<Int>()
            stack += (v and 0x7F).toInt(); v = v shr 7
            while (v > 0) { stack += ((v and 0x7F) or 0x80).toInt(); v = v shr 7 }
            stack.asReversed().forEach { out.write(it) }
        }
        return tlv(0x06, out.toByteArray())
    }
    private fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
    private fun time(d: Date): ByteArray {
        val f = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return tlv(0x18, f.format(d).toByteArray()) // GeneralizedTime
    }
    private fun bitString(b: ByteArray) = tlv(0x03, byteArrayOf(0) + b)

    private fun buildCertificate(spki: ByteArray, key: PrivateKey): ByteArray {
        val ecdsaSha256 = seq(oid(1, 2, 840, 10045, 4, 3, 2))
        val name = seq(set(seq(oid(2, 5, 4, 3), utf8("Symbian Launcher Desktop"))))
        val now = System.currentTimeMillis()
        val validity = seq(time(Date(now - 86_400_000L)), time(Date(now + 3650L * 86_400_000L)))
        // Extensions: basicConstraints CA:false, keyUsage digitalSignature, extKeyUsage serverAuth.
        val basic = seq(oid(2, 5, 29, 19), tlv(0x04, seq()))
        val keyUsage = seq(oid(2, 5, 29, 15), tlv(0x01, byteArrayOf(0xFF.toByte())), tlv(0x04, tlv(0x03, byteArrayOf(7, 0x80.toByte()))))
        val eku = seq(oid(2, 5, 29, 37), tlv(0x04, seq(oid(1, 3, 6, 1, 5, 5, 7, 3, 1))))
        val extensions = tlv(0xA3, seq(basic, keyUsage, eku))
        val serial = BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE)
        val tbs = seq(
            tlv(0xA0, int(BigInteger.valueOf(2))), // v3
            int(serial), ecdsaSha256, name, validity, name, spki, extensions
        )
        val sig = Signature.getInstance("SHA256withECDSA").run { initSign(key); update(tbs); sign() }
        return seq(tbs, ecdsaSha256, bitString(sig))
    }
}
