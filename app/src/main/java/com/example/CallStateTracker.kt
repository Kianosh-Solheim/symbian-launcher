package com.example

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Resolves a phone number to a contact using the system's own matcher (handles +47 / 0047 / local forms). */
object CallerLookup {
    data class Caller(val name: String?, val photoUri: String?)

    private val cache = object : LinkedHashMap<String, Caller>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Caller>?) = size > 64
    }

    suspend fun lookup(context: Context, number: String, contacts: List<ContactInfo>): Caller {
        synchronized(cache) { cache[number]?.let { return it } }
        val result = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) Caller(c.getString(0), c.getString(1)) else null
                }
            } catch (e: Exception) {
                null
            } ?: fallbackMatch(number, contacts)
        }
        synchronized(cache) { cache[number] = result }
        return result
    }

    /** In-memory match requiring at least 7 trailing digits, so short codes never match a wrong contact. */
    private fun fallbackMatch(number: String, contacts: List<ContactInfo>): Caller {
        val digits = number.filter { it.isDigit() }
        if (digits.length < 7) return Caller(null, null)
        val tail = digits.takeLast(8)
        val match = contacts.firstOrNull { c ->
            c.phoneNumbers.any { p -> p.normalizedNumber.filter { it.isDigit() }.endsWith(tail) }
        }
        return Caller(match?.name, match?.photoUri)
    }
}

/**
 * Live call state for the status bar. Event-driven: reacts to telephony call-state changes and the
 * VoIP notification monitor instead of polling. While a call is active it refreshes the speaker
 * indicator once a second, and only while the launcher is visible.
 */
@Composable
fun rememberActiveCallState(context: Context, contacts: List<ContactInfo>): ActiveCallState {
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val lifecycleOwner = LocalLifecycleOwner.current

    var telephonyCallActive by remember { mutableStateOf(false) }
    var telephonyNumber by remember { mutableStateOf<String?>(null) }
    var telephonyStart by remember { mutableStateOf(0L) }
    var audioModeInCall by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var caller by remember { mutableStateOf(CallerLookup.Caller(null, null)) }
    val voipCall by VoipCallMonitor.voipCallState.collectAsState()

    // Telephony call state (cellular calls).
    DisposableEffect(context) {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        @Suppress("DEPRECATION")
        val listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (!telephonyCallActive) telephonyStart = System.currentTimeMillis()
                        telephonyCallActive = true
                        if (!phoneNumber.isNullOrEmpty()) telephonyNumber = phoneNumber
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        if (!phoneNumber.isNullOrEmpty()) telephonyNumber = phoneNumber
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        telephonyCallActive = false
                        telephonyNumber = null
                        telephonyStart = 0L
                    }
                }
            }
        }
        @Suppress("DEPRECATION")
        try { telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE) } catch (_: Exception) { }
        onDispose {
            @Suppress("DEPRECATION")
            try { telephonyManager?.listen(listener, PhoneStateListener.LISTEN_NONE) } catch (_: Exception) { }
        }
    }

    // Audio-mode fallback for calls the notification monitor missed (event-driven on Android 12+).
    DisposableEffect(audioManager) {
        val am = audioManager
        fun inCallMode(mode: Int) = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        audioModeInCall = am?.let { inCallMode(it.mode) } ?: false
        var modeListener: Any? = null
        if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val l = AudioManager.OnModeChangedListener { mode -> audioModeInCall = inCallMode(mode) }
            try { am.addOnModeChangedListener(context.mainExecutor, l); modeListener = l } catch (_: Exception) { }
        }
        onDispose {
            if (am != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (modeListener as? AudioManager.OnModeChangedListener)?.let {
                    try { am.removeOnModeChangedListener(it) } catch (_: Exception) { }
                }
            }
        }
    }

    // Pre-Android-12 fallback: check audio mode every few seconds, only while visible.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        LaunchedEffect(audioManager, lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
                    audioModeInCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
                    delay(3_000)
                }
            }
        }
    }

    val anyCall = telephonyCallActive || voipCall.isActive || audioModeInCall
    var fallbackStart by remember { mutableStateOf(0L) }
    LaunchedEffect(anyCall) { fallbackStart = if (anyCall) System.currentTimeMillis() else 0L }
    val stableStart = if (fallbackStart > 0) fallbackStart else System.currentTimeMillis()

    // Speaker indicator: refresh only during a call, only while visible.
    LaunchedEffect(anyCall, lifecycleOwner) {
        if (!anyCall) { isSpeakerOn = false; return@LaunchedEffect }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                @Suppress("DEPRECATION")
                isSpeakerOn = audioManager?.isSpeakerphoneOn ?: false
                delay(1_000)
            }
        }
    }

    // Resolve the caller once per call, off the main thread.
    LaunchedEffect(telephonyNumber, voipCall.contactName, contacts) {
        caller = when {
            !telephonyNumber.isNullOrEmpty() -> CallerLookup.lookup(context, telephonyNumber!!, contacts)
            voipCall.isActive && voipCall.contactName != null -> {
                val match = contacts.firstOrNull { it.name.equals(voipCall.contactName, ignoreCase = true) }
                CallerLookup.Caller(voipCall.contactName, match?.photoUri)
            }
            else -> CallerLookup.Caller(null, null)
        }
    }

    return when {
        telephonyCallActive -> ActiveCallState(
            isActive = true,
            phoneNumber = telephonyNumber,
            contactName = caller.name ?: telephonyNumber ?: "Unknown Caller",
            photoUri = caller.photoUri,
            startTime = if (telephonyStart > 0) telephonyStart else stableStart,
            isSpeakerOn = isSpeakerOn
        )
        voipCall.isActive -> voipCall.copy(
            photoUri = caller.photoUri ?: voipCall.photoUri,
            startTime = if (voipCall.startTime > 0) voipCall.startTime else stableStart,
            isSpeakerOn = isSpeakerOn
        )
        audioModeInCall -> ActiveCallState(
            isActive = true,
            contactName = "Ongoing Call",
            startTime = stableStart,
            isSpeakerOn = isSpeakerOn
        )
        else -> ActiveCallState()
    }
}
