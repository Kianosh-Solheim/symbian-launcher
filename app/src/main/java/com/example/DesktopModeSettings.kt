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

            if (enabled) {
                SettingsSectionHeader("Open in your computer's browser")
                if (addresses.isEmpty()) {
                    Text(
                        "Connect to Wi-Fi, or plug in USB and turn on USB tethering.",
                        color = Color.White, fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                addresses.forEach { (label, url) ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(label, color = Color(0xFFAAAAAA), fontSize = 13.sp)
                        Text(url, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                SettingsSectionHeader("Pairing code")
                Text(
                    code.chunked(3).joinToString(" "),
                    color = MaterialThemeHighlight(), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Text(
                    "Enter this in the browser. The code changes after each pairing.",
                    color = Color(0xFFCCCCCC), fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

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
private fun MaterialThemeHighlight(): Color = androidx.compose.material3.MaterialTheme.colorScheme.secondary
