package com.example

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * Shared state between the launcher UI and the Desktop Mode server: theme colours and weather
 * published by the launcher, pairing code and paired browser tokens, connection count.
 */
object BridgeState {
    const val PORT = 8765
    private const val PREFS = "bridge_prefs"

    /** Launcher theme as CSS hex colours, so the desktop page matches the phone. */
    val theme = MutableStateFlow(
        mapOf(
            "background" to "#0066CC", "surface" to "#005080", "text" to "#FFFFFF",
            "accent" to "#00CCCC", "highlight" to "#FDD835", "font" to "DEFAULT"
        )
    )

    /** Latest weather JSON published by the launcher (null until first fetch). */
    val weather = MutableStateFlow<JSONObject?>(null)

    /** Number of desktop browsers currently connected (live event stream open). */
    val connectedClients = MutableStateFlow(0)

    /** True while the server is listening. */
    val running = MutableStateFlow(false)

    private val random = SecureRandom()

    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false)

    /** HTTPS with the phone's own certificate (default). Plain HTTP stays available as an option for USB. */
    fun useHttps(context: Context) = prefs(context).getBoolean("https", true)
    fun setHttps(context: Context, on: Boolean) = prefs(context).edit().putBoolean("https", on).apply()
    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("enabled", enabled).apply()

    /** Six-digit code shown on the phone; regenerated on each start and after each pairing. */
    val pairingCode = MutableStateFlow(newCode())

    fun regenerateCode() { pairingCode.value = newCode() }
    private fun newCode() = (100000 + SecureRandom().nextInt(900000)).toString()

    fun issueToken(context: Context, label: String): String {
        val bytes = ByteArray(24).also { random.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        val arr = tokensJson(context)
        arr.put(JSONObject().put("token", token).put("label", label).put("created", System.currentTimeMillis()))
        prefs(context).edit().putString("tokens", arr.toString()).apply()
        regenerateCode()
        return token
    }

    fun isValidToken(context: Context, token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val arr = tokensJson(context)
        for (i in 0 until arr.length()) {
            val stored = arr.getJSONObject(i).getString("token")
            if (java.security.MessageDigest.isEqual(stored.toByteArray(), token.toByteArray())) return true
        }
        return false
    }

    fun pairedCount(context: Context) = tokensJson(context).length()

    fun forgetAll(context: Context) {
        prefs(context).edit().remove("tokens").apply()
        regenerateCode()
    }

    private fun tokensJson(context: Context): JSONArray =
        try { JSONArray(prefs(context).getString("tokens", "[]")) } catch (_: Exception) { JSONArray() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Addresses a browser can use: Wi-Fi first, then USB tethering, then anything else. */
    fun addresses(https: Boolean = true): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                val label = when {
                    nif.name.startsWith("wlan") -> "Wi-Fi"
                    nif.name.startsWith("rndis") || nif.name.startsWith("usb") || nif.name.startsWith("ncm") -> "USB"
                    nif.name.startsWith("ap") || nif.name.startsWith("swlan") -> "Hotspot"
                    nif.name.startsWith("rmnet") || nif.name.startsWith("ccmni") -> return@forEach // mobile data: never expose
                    else -> nif.name
                }
                nif.inetAddresses.toList().filterIsInstance<Inet4Address>().forEach { addr ->
                    if (!addr.isLoopbackAddress) result += label to "${if (https) "https" else "http"}://${addr.hostAddress}:$PORT"
                }
            }
        } catch (_: Exception) { }
        val order = listOf("Wi-Fi", "USB", "Hotspot")
        return result.sortedBy { order.indexOf(it.first).let { i -> if (i < 0) 99 else i } }
    }
}
