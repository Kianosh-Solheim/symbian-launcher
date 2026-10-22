package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/** Phone-side controls for Desktop Mode: on/off, addresses, pairing code, paired browsers. */
@Composable
fun SettingsDesktopMode(state: LauncherState, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val context = LocalContext.current
    val running by BridgeState.running.collectAsState()
    val clients by BridgeState.connectedClients.collectAsState()
    val code by BridgeState.pairingCode.collectAsState()
    // Re-read addresses and pairing count every few seconds while this screen is visible.
    val tick = rememberTickingTime(3_000L)
    val enabled = remember(tick, running) { BridgeState.isEnabled(context) }
    val https = remember(tick) { BridgeState.useHttps(context) }
    val addresses = remember(tick, https) { BridgeState.addresses(https) }
    val paired = remember(tick) { BridgeState.pairedCount(context) }

    val needed = remember {
        buildList {
            add(android.Manifest.permission.READ_SMS)
            add(android.Manifest.permission.SEND_SMS)
            add(android.Manifest.permission.CALL_PHONE)
            add(android.Manifest.permission.READ_CALL_LOG)
            add(android.Manifest.permission.READ_CONTACTS)
            add(android.Manifest.permission.READ_CALENDAR)
            add(android.Manifest.permission.WRITE_CALENDAR)
            add(android.Manifest.permission.READ_PHONE_STATE)
            add(android.Manifest.permission.ANSWER_PHONE_CALLS)
            if (Build.VERSION.SDK_INT >= 33) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
                add(android.Manifest.permission.READ_MEDIA_IMAGES)
                add(android.Manifest.permission.READ_MEDIA_VIDEO)
            } else add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        BridgeService.start(context)
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsToggleItem(
                "Desktop mode", enabled, haptic, isFirst = true,
                subtitle = when {
                    !enabled -> "Use SMS, calls and notifications from a computer's browser"
                    clients > 0 -> "Running · $clients browser${if (clients == 1) "" else "s"} connected"
                    running -> "Running · waiting for a browser"
                    else -> "Starting…"
                }
            ) { on ->
                if (on) {
                    val missing = needed.filter {
                        ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) BridgeService.start(context) else permissionLauncher.launch(missing.toTypedArray())
                } else {
                    BridgeService.stop(context)
                }
            }

            if (!enabled) {
                SettingsSectionHeader("What it does")
                Hint("Turn this on to read and send SMS, answer chats, see notifications, place calls, manage your calendar and browse photos from a computer's web browser. The phone and computer must be on the same Wi-Fi, or joined by a USB cable.")
            }

            if (enabled) {
                val main = addresses.firstOrNull()?.second
                SettingsSectionHeader("Type this into your computer's browser")
                if (main == null) {
                    Hint("No connection yet. Join the same Wi-Fi as your computer — or plug in a USB cable and turn on USB tethering (Android Settings → Network → Hotspot & tethering). The address appears here as soon as there is a connection.")
                } else {
                    Text(
                        main.removePrefix("https://").removePrefix("http://").let { if (https) "https://$it" else "http://$it" },
                        color = MaterialThemeHighlight(), fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    Hint("Type it exactly in the browser's address bar, including ${if (https) "\"https://\"" else "\"http://\""} at the start and \":${BridgeState.PORT}\" at the end, then press Enter.")
                }

                SettingsSectionHeader("Pairing code")
                Text(
                    code.chunked(3).joinToString(" "),
                    color = MaterialThemeHighlight(), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                SettingsSectionHeader("Step by step")
                val steps = buildList {
                    add("On the computer, open Chrome, Edge, Firefox or Vivaldi.")
                    add("Type the address above into the address bar and press Enter.")
                    if (https) add("The browser warns that the connection isn't private — that's expected, as the phone made its own certificate. Choose Advanced, then Proceed (Chrome/Edge/Vivaldi) or Accept the Risk and Continue (Firefox). This is needed only once per browser.")
                    add("Enter the 6-digit pairing code shown above. The browser remembers it; the code changes after each pairing.")
                    add("Optional: bookmark the page. In Tools, allow desktop pop-ups for new messages and calls.")
                }
                steps.forEachIndexed { i, t -> Hint("${i + 1}. $t") }

                if (addresses.size > 1) {
                    SettingsSectionHeader("Other addresses")
                    addresses.drop(1).forEach { (label, url) ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(label, color = Color(0xFFAAAAAA), fontSize = 13.sp)
                            Text(url, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Hint("Use the USB address when the phone is plugged in with USB tethering on — for example on eduroam, which blocks devices from reaching each other.")
                }

                SettingsSectionHeader("If it doesn't connect")
                Hint("• Check the computer is on the same Wi-Fi as the phone (not a guest network).\n• Make sure you typed ${if (https) "https" else "http"}:// and :${BridgeState.PORT}.\n• On university or public Wi-Fi, use USB tethering instead.\n• Still stuck? Switch Desktop mode off and on again.")

                SettingsSectionHeader("Security")
                SettingsToggleItem(
                    "Encrypted connection (HTTPS)", https, haptic,
                    subtitle = if (https) "The browser warns once about the phone's own certificate — choose Advanced → Proceed"
                               else "Unencrypted — fine over USB, not on shared Wi-Fi"
                ) { on ->
                    BridgeState.setHttps(context, on)
                    BridgeService.stop(context); BridgeService.start(context)
                }
                if (https) {
                    val fp = remember { BridgeTls.fingerprint(context) }
                    Text(
                        "Certificate fingerprint (SHA-256):\n" + fp.chunked(24).joinToString("\n"),
                        color = Color(0xFFCCCCCC), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                SettingsMenuItem("Forget paired browsers ($paired)", null, haptic) { BridgeState.forgetAll(context) }

                SettingsSectionHeader("Reliability")
                val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    SettingsMenuItem("Allow running in background", Icons.Default.ChevronRight, haptic) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { }
                    }
                }
                if (SymbianNotificationService.instance == null) {
                    SettingsMenuItem("Turn on notification access", Icons.Default.ChevronRight, haptic) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) = Text(
    text, color = Color(0xFFDDDDDD), fontSize = 15.sp,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
)

@Composable
private fun MaterialThemeHighlight(): Color = androidx.compose.material3.MaterialTheme.colorScheme.secondary
