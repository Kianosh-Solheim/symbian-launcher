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
    androidx.compose.runtime.LaunchedEffect(isFocused) {
        if (isFocused && soundsEnabled) {
            com.example.SymbianSoundManager.playClick()
        }
    }
    if (isFocused) {
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
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Coming back to the home screen after copying something: share it with the desktop.
        if (hasFocus && BridgeState.running.value && ClipboardHub.syncEnabled(this)) ClipboardHub.readNow(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BridgeService.restoreIfEnabled(this)
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
                LaunchedEffect(cs.background, cs.surface, cs.primary, cs.secondary, cs.onBackground, state.currentFont) {
                    fun hex(c: androidx.compose.ui.graphics.Color) = String.format("#%06X", 0xFFFFFF and c.toArgb())
                    BridgeState.theme.value = mapOf(
                        "background" to hex(cs.background), "surface" to hex(cs.surface), "text" to hex(cs.onBackground),
                        "accent" to hex(cs.primary), "highlight" to hex(cs.secondary), "font" to state.currentFont.name
                    )
                }
                val currentDensity = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSymbianSoundsEnabled provides state.symbianSoundsEnabled,
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        density = currentDensity.density,
                        fontScale = currentDensity.fontScale * state.textScale
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .symbianGrayscale(state.currentTheme == AppTheme.GREY)
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
