package com.example

import android.content.BroadcastReceiver
import android.content.Context
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.os.Build
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

val LocalSymbianSoundsEnabled = androidx.compose.runtime.staticCompositionLocalOf { true }

/** True when the Windows 95 theme is active: highlights become grey 3-D buttons. */
val LocalWin95 = androidx.compose.runtime.staticCompositionLocalOf { false }

/** True when the Classic S60 Sky theme is active (glossy navy bars and the sky backdrop). */
val LocalS60 = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Navy gloss used for the S60 status and soft-key bars. */
val S60BarBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
    listOf(Color(0xFF2A3A8E), Color(0xFF16226A), Color(0xFF0B1245))
)

/**
 * S60 sky backdrop: deep blue at the top fading to turquoise, with large pale "light waves"
 * drifting across, drawn once per size. [alpha] < 1 lets a wallpaper show through.
 */
fun Modifier.s60Backdrop(alpha: Float = 1f): Modifier = this.drawWithCache {
    val w = size.width; val h = size.height
    val sky = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color(0xFF1662C8), Color(0xFF1E86DA), Color(0xFF2BA9DC), Color(0xFF3CC3D6)))
    val wave = Color.White.copy(alpha = 0.10f)
    val wave2 = Color(0xFF7FE3F0).copy(alpha = 0.12f)
    onDrawBehind {
        drawRect(sky, alpha = alpha)
        drawOval(wave, topLeft = androidx.compose.ui.geometry.Offset(-w * 0.35f, h * 0.18f), size = androidx.compose.ui.geometry.Size(w * 1.2f, h * 0.22f), alpha = alpha)
        drawOval(wave2, topLeft = androidx.compose.ui.geometry.Offset(w * 0.25f, h * 0.36f), size = androidx.compose.ui.geometry.Size(w * 1.1f, h * 0.20f), alpha = alpha)
        drawOval(wave, topLeft = androidx.compose.ui.geometry.Offset(-w * 0.2f, h * 0.55f), size = androidx.compose.ui.geometry.Size(w * 1.4f, h * 0.26f), alpha = alpha)
        drawOval(wave2, topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.78f), size = androidx.compose.ui.geometry.Size(w * 1.2f, h * 0.3f), alpha = alpha)
    }
}

/** Windows 95 bevel: grey face, white/light top-left edges, dark/black bottom-right (reversed when sunken). */
fun Modifier.win95Bevel(sunken: Boolean = false): Modifier = this.drawBehind {
    val face = Color(0xFFC0C0C0)
    val light = Color.White; val light2 = Color(0xFFDFDFDF)
    val dark = Color(0xFF808080); val darkest = Color.Black
    val px = 1.dp.toPx().coerceAtLeast(1f)
    drawRect(face)
    val (tl1, tl2, br1, br2) = if (sunken) listOf(darkest, dark, light, light2) else listOf(light, light2, darkest, dark)
    val w = size.width; val h = size.height
    // outer
    drawRect(tl1, topLeft = Offset(0f, 0f), size = Size(w, px)); drawRect(tl1, topLeft = Offset(0f, 0f), size = Size(px, h))
    drawRect(br1, topLeft = Offset(0f, h - px), size = Size(w, px)); drawRect(br1, topLeft = Offset(w - px, 0f), size = Size(px, h))
    // inner
    drawRect(tl2, topLeft = Offset(px, px), size = Size(w - 2 * px, px)); drawRect(tl2, topLeft = Offset(px, px), size = Size(px, h - 2 * px))
    drawRect(br2, topLeft = Offset(px, h - 2 * px), size = Size(w - 2 * px, px)); drawRect(br2, topLeft = Offset(w - 2 * px, px), size = Size(px, h - 2 * px))
}


/** True when the Classic Green LCD theme is active. */
val LocalLcd = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * Classic green LCD (old Nokia style): everything is redrawn in two tones — dark "ink" on a lime
 * green backlight. Brightness is turned around, so white text becomes dark ink and dark panels
 * become the green background, and the yellow selection becomes a dark inverted bar.
 */
private val lcdMatrix: ColorMatrix = run {
    val light = floatArrayOf(170f, 198f, 106f)  // #AAC66A backlight
    val dark = floatArrayOf(28f, 41f, 16f)      // #1C2910 ink
    val m = FloatArray(20)
    for (i in 0..2) {
        val k = (dark[i] - light[i]) / 255f
        m[i * 5] = k * 0.299f; m[i * 5 + 1] = k * 0.587f; m[i * 5 + 2] = k * 0.114f; m[i * 5 + 4] = light[i]
    }
    m[18] = 1f
    ColorMatrix(m)
}

fun Modifier.lcdFilter(enabled: Boolean = true): Modifier = if (enabled) {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(lcdMatrix) }
    this.drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }
} else this

fun Modifier.symbianGrayscale(enabled: Boolean = true): Modifier = if (enabled) {
    val colorMatrix = ColorMatrix().apply { setToSaturation(0f) }
    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(colorMatrix)
    }
    this.drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }
} else this

fun Modifier.symbianHighlight(
    isFocused: Boolean, 
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    unfocusedColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent
): Modifier = this.composed {
    val soundsEnabled = LocalSymbianSoundsEnabled.current
    val win95 = LocalWin95.current
    androidx.compose.runtime.LaunchedEffect(isFocused) {
        if (isFocused && soundsEnabled) {
            com.example.SymbianSoundManager.playClick()
        }
    }
    if (isFocused && win95) {
        this.win95Bevel()
    } else if (isFocused) {
        val baseColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary
        val topColor = androidx.compose.ui.graphics.lerp(baseColor, androidx.compose.ui.graphics.Color.White, 0.4f)
        val brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0.0f to topColor,
            0.5f to topColor,
            0.501f to baseColor,
            1.0f to baseColor
        )
        this.background(brush = brush, shape = shape)
    } else {
        this.background(unfocusedColor, shape = shape)
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_CLOCK = "no.kianosh.symbianlauncher.OPEN_CLOCK"
        /** Set when something (the status-bar alarm icon) asks for the Alarm tab; LauncherScreen consumes it. */
        val openClockRequest = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    private fun handleIntent(i: Intent?) { if (i?.action == ACTION_OPEN_CLOCK) openClockRequest.value = true }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    override fun onStart() { super.onStart(); AppVisibility.visible = true }
    override fun onStop() { AppVisibility.visible = false; super.onStop() }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Coming back to the home screen after copying something: share it with the desktop.
        if (hasFocus && BridgeState.running.value && ClipboardHub.syncEnabled(this)) ClipboardHub.readNow(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BridgeService.restoreIfEnabled(this)
        handleIntent(intent)
        AlarmStore.load(this)
        AlarmRingService.ensureChannels(this)
        AlarmStore.scheduleAll(this) // keeps alarms armed after updates, even if the phone never rebooted
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        setContent {
            val viewModel: LauncherViewModel = viewModel()
            val state by viewModel.state.collectAsState()
            
            DisposableEffect(Unit) {
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                        viewModel.loadApps()
                    }
                }
                val filter = android.content.IntentFilter().apply {
                    addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
                    addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                    addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
                    addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                try {
                    androidx.core.content.ContextCompat.registerReceiver(
                        this@MainActivity,
                        receiver,
                        filter,
                        androidx.core.content.ContextCompat.RECEIVER_EXPORTED
                    )
                } catch (e: Exception) {
                    try {
                        registerReceiver(receiver, filter)
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
                onDispose {
                    try {
                        unregisterReceiver(receiver)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            MyApplicationTheme(state = state) {
                // Share the active theme with Desktop Mode so the browser page matches the phone.
                val cs = androidx.compose.material3.MaterialTheme.colorScheme
                LaunchedEffect(cs.background, cs.surface, cs.primary, cs.secondary, cs.onBackground, state.currentFont, state.currentTheme) {
                    IconCache.win95 = state.currentTheme == AppTheme.WIN95 // app icons become pixel icons
                    IconCache.s60 = state.currentTheme == AppTheme.S60 // app icons get the S60 gloss
                    IconCache.lcd = state.currentTheme == AppTheme.LCD // app icons become LCD pixel icons
                    fun hex(c: androidx.compose.ui.graphics.Color) = String.format("#%06X", 0xFFFFFF and c.toArgb())
                    BridgeState.theme.value = mapOf(
                        "background" to hex(cs.background), "surface" to hex(cs.surface), "text" to hex(cs.onBackground),
                        "accent" to hex(cs.primary), "highlight" to hex(cs.secondary), "font" to state.currentFont.name,
                        "style" to when (state.currentTheme) { AppTheme.WIN95 -> "win95"; AppTheme.S60 -> "s60"; AppTheme.LCD -> "lcd"; else -> "symbian" }
                    )
                }
                val currentDensity = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSymbianSoundsEnabled provides state.symbianSoundsEnabled,
                    LocalWin95 provides (state.currentTheme == AppTheme.WIN95),
                    LocalS60 provides (state.currentTheme == AppTheme.S60),
                    LocalLcd provides (state.currentTheme == AppTheme.LCD),
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        density = currentDensity.density,
                        fontScale = currentDensity.fontScale * state.textScale
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .symbianGrayscale(state.currentTheme == AppTheme.GREY)
                            .lcdFilter(state.currentTheme == AppTheme.LCD)
                    ) {
                        SymbianLauncherScreen(viewModel, state)
                    }
                }
            }
        }
    }
}

enum class Screen { 
    MAIN, MENU, WEATHER_FORECAST, NOTIFICATIONS, MESSAGES, EMAILS, APP_DRAWER, CONTACTS_DRAWER, USAGE, AGENDA, CONTACT_DETAIL,
    WALLET, WALLET_PASS_DETAIL, CLOCK, CALCULATOR,
    ALARM_EDIT, ALARM_OPTIONS, WORLD_CLOCK_SEARCH, WORLD_CLOCK_OPTIONS,
    SETTINGS_ROOT, SETTINGS_HOME_SCREEN, SETTINGS_ICON_PACK, 
    SETTINGS_STATUS_BAR, SETTINGS_WEATHER, SETTINGS_TIME_DATE, SETTINGS_DESKTOP, SETTINGS_BACKUP,
    SETTINGS_HOME_CONTENT, SETTINGS_T9_SEARCH, SETTINGS_PINNED_APPS, SETTINGS_REORDER_PINNED, SETTINGS_MESSAGES, SETTINGS_REORDER_MESSAGES, SETTINGS_EMAIL, SETTINGS_REORDER_EMAIL, SETTINGS_CALENDAR, SETTINGS_GLOBAL, SETTINGS_CUSTOM_THEME,
    // Old screens to keep for potential other flows if needed (or remove if fully replaced)
    SETTINGS_QUICK_INFO, SETTINGS_MANAGE_APPS, SETTINGS_MESSAGING_APPS, SETTINGS_LOOK_AND_FEEL
}

enum class UsageTimeRange(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

data class BatteryInfo(
    val percentage: Float,
    val isCharging: Boolean
)
