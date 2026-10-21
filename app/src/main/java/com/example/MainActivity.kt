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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    SETTINGS_STATUS_BAR, SETTINGS_WEATHER, SETTINGS_TIME_DATE,
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


@Composable
fun rememberBatteryInfo(context: Context): BatteryInfo {
    var batteryInfo by remember { mutableStateOf(BatteryInfo(1f, false)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                
                if (level != -1 && scale != -1) {
                    batteryInfo = BatteryInfo(level / scale.toFloat(), isCharging)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        try {
            val stickyIntent = androidx.core.content.ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            stickyIntent?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                if (level != -1 && scale != -1) {
                    batteryInfo = BatteryInfo(level / scale.toFloat(), isCharging)
                }
            }
        } catch (e: Exception) {
            try {
                val stickyIntent = context.registerReceiver(receiver, filter)
                stickyIntent?.let { intent ->
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    if (level != -1 && scale != -1) {
                        batteryInfo = BatteryInfo(level / scale.toFloat(), isCharging)
                    }
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    return batteryInfo
}





@Composable
fun rememberSignalLevel(context: Context): Int {
    var level by remember { mutableIntStateOf(4) }
    DisposableEffect(context) {
        val telephonyManager = try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (e: Exception) {
            null
        }
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(sigStrength: SignalStrength?) {
                sigStrength?.let {
                    try {
                        level = it.level
                    } catch (e: Exception) {
                        level = 4
                    }
                }
            }
        }
        try {
            telephonyManager?.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } catch (e: Exception) {
            // Permission denied or unavailable, ignore
        }
        onDispose {
            try {
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_NONE)
            } catch (e: Exception) {}
        }
    }
    return level
}

@OptIn(ExperimentalFoundationApi::class)


@Composable
fun ClassicClockWidget(state: LauncherState, modifier: Modifier = Modifier, clockSize: androidx.compose.ui.unit.Dp = 120.dp) {
    val currentTime = rememberTickingTime(1_000L)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        SkeuomorphicAnalogueClock(
            timeMillis = currentTime,
            clockSize = clockSize,
            showSeconds = true,
            showNumerals = true
        )
    }
}


@Composable
fun SymbianLauncherScreen(viewModel: LauncherViewModel, state: LauncherState) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }

    
    var selectedContactForDetail by remember { mutableStateOf<ContactInfo?>(null) }
    var selectedWalletPass by remember { mutableStateOf<WalletPass?>(null) }
    var showFullscreenBarcode by remember { mutableStateOf(false) }

var backStack by remember { mutableStateOf(listOf<Screen>()) }

    val navigateTo: (Screen) -> Unit = { screen ->
        if (screen == Screen.MAIN) {
            backStack = emptyList()
        } else if (screen != currentScreen) {
            backStack = backStack + currentScreen
        }
        currentScreen = screen
    }

    val navigateBack = {
        if (currentScreen == Screen.WALLET_PASS_DETAIL && showFullscreenBarcode) {
            showFullscreenBarcode = false
        } else if (backStack.isNotEmpty()) {
            currentScreen = backStack.last()
            backStack = backStack.dropLast(1)
        } else {
            currentScreen = Screen.MAIN
        }
    }
    var selectedAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var selectedWorldClock by remember { mutableStateOf<WorldClockItem?>(null) }
    
    val viewForHaptic = LocalView.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator }
    val haptic = remember(state.hapticFeedbackEnabled, state.hapticIntensity, viewForHaptic) {
        object : androidx.compose.ui.hapticfeedback.HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                if (state.hapticFeedbackEnabled) {
                    if (vibrator.hasVibrator()) {
                        val duration = 5L + (state.hapticIntensity * 45L).toLong()
                        val amplitude = 1 + (state.hapticIntensity * 254).toInt()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            if (vibrator.hasAmplitudeControl()) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, amplitude))
                            } else {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(duration)
                        }
                    } else {
                        // Fallback to view-based haptics if no vibrator or permissions
                        viewForHaptic.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                        )
                    }
                }
            }
        }
    }
    
    val focusRequester = remember { FocusRequester() }
    
        LaunchedEffect(state.showClockScreen) {
        if (state.showClockScreen) {
            navigateTo(Screen.CLOCK)
            viewModel.toggleClockDialog(false)
        }
    }

    val usageLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(usageLifecycleOwner, currentScreen) {
        var screenStartTime = System.currentTimeMillis()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                val now = System.currentTimeMillis()
                val durationMs = now - screenStartTime
                if (durationMs > 0) {
                    val appName = when(currentScreen) {
                        Screen.WEATHER_FORECAST -> "Weather"
                        Screen.WALLET, Screen.WALLET_PASS_DETAIL -> "Wallet"
                        Screen.CLOCK, Screen.WORLD_CLOCK_SEARCH, Screen.WORLD_CLOCK_OPTIONS, Screen.ALARM_EDIT, Screen.ALARM_OPTIONS -> "Clock"
                        Screen.CALCULATOR -> "Calculator"
                        Screen.AGENDA -> "Calendar"
                        Screen.CONTACTS_DRAWER, Screen.CONTACT_DETAIL -> "Contacts"
                        Screen.USAGE -> "Usage Stats"
                        Screen.SETTINGS_ROOT, Screen.SETTINGS_HOME_SCREEN, Screen.SETTINGS_ICON_PACK, Screen.SETTINGS_STATUS_BAR, Screen.SETTINGS_WEATHER, Screen.SETTINGS_TIME_DATE, Screen.SETTINGS_T9_SEARCH, Screen.SETTINGS_PINNED_APPS -> "Settings"
                        else -> "Launcher"
                    }
                    viewModel.logInternalAppUsage(appName, durationMs)
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                screenStartTime = System.currentTimeMillis()
            }
        }
        usageLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            val now = System.currentTimeMillis()
            val durationMs = now - screenStartTime
            if (durationMs > 0) {
                val appName = when(currentScreen) {
                    Screen.WEATHER_FORECAST -> "Weather"
                    Screen.WALLET, Screen.WALLET_PASS_DETAIL -> "Wallet"
                    Screen.CLOCK, Screen.WORLD_CLOCK_SEARCH, Screen.WORLD_CLOCK_OPTIONS, Screen.ALARM_EDIT, Screen.ALARM_OPTIONS -> "Clock"
                    Screen.CALCULATOR -> "Calculator"
                    Screen.AGENDA -> "Calendar"
                    Screen.CONTACTS_DRAWER, Screen.CONTACT_DETAIL -> "Contacts"
                    Screen.USAGE -> "Usage Stats"
                    Screen.SETTINGS_ROOT, Screen.SETTINGS_HOME_SCREEN, Screen.SETTINGS_ICON_PACK, Screen.SETTINGS_STATUS_BAR, Screen.SETTINGS_WEATHER, Screen.SETTINGS_TIME_DATE, Screen.SETTINGS_T9_SEARCH, Screen.SETTINGS_PINNED_APPS -> "Settings"
                    else -> "Launcher"
                }
                viewModel.logInternalAppUsage(appName, durationMs)
            }
            usageLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    BackHandler(enabled = state.t9Query.isNotEmpty()) {
        viewModel.backspaceT9()
    }
    
    BackHandler(enabled = state.t9Query.isEmpty() && currentScreen != Screen.MAIN) {
        navigateBack()
    }

    val batteryInfo = rememberBatteryInfo(context)
    val signalLevel = rememberSignalLevel(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Only reload if something was actually newly granted.
        if (results.values.any { it }) viewModel.refreshAll()
    }
    
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, state.showAndroidStatusBar, view) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadCalendarData()
                SymbianNotificationService.ensureConnected(context)
                val window = (context as? ComponentActivity)?.window
                if (window != null) {
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (state.showAndroidStatusBar) {
                        insetsController.show(WindowInsetsCompat.Type.statusBars())
                    } else {
                        insetsController.hide(WindowInsetsCompat.Type.statusBars())
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // Initial run
        val window = (context as? ComponentActivity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (state.showAndroidStatusBar) {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.ANSWER_PHONE_CALLS,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            perms.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        // Ask only for what is still missing; avoids a redundant full reload on every start.
        val missing = perms.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val leftSoftKeyFocusRequester = remember { FocusRequester() }
    val rightSoftKeyFocusRequester = remember { FocusRequester() }

    val isSearching = state.t9Query.isNotEmpty()

    val leftSoftKeyText: String
    val onLeftSoftKeyClick: () -> Unit

    val rightSoftKeyText: String
    val onRightSoftKeyClick: () -> Unit

    when (currentScreen) {
        Screen.MAIN -> {
            if (state.isExtremeBatterySaver) {
                leftSoftKeyText = "Menu"
                onLeftSoftKeyClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    navigateTo(Screen.MENU)
                }
                rightSoftKeyText = "Turn Off"
                onRightSoftKeyClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.setExtremeBatterySaver(false)
                }
            } else if (isSearching) {
                val isApps = state.t9SearchMode == T9SearchMode.APPS
                leftSoftKeyText = if (isApps) "Contacts" else "Apps"
                onLeftSoftKeyClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val nextMode = if (isApps) T9SearchMode.CONTACTS else T9SearchMode.APPS
                    viewModel.switchT9Mode(nextMode, keepQuery = true)
                }
                rightSoftKeyText = "Menu"
                onRightSoftKeyClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    navigateTo(Screen.MENU)
                }
            } else {
                leftSoftKeyText = "Wallet"
                onLeftSoftKeyClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    navigateTo(Screen.WALLET)
                }
                rightSoftKeyText = "Menu"
                onRightSoftKeyClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    navigateTo(Screen.MENU)
                }
            }
        }
        Screen.APP_DRAWER -> {
            if (isSearching) {
                val isApps = state.t9SearchMode == T9SearchMode.APPS
                leftSoftKeyText = if (isApps) "Contacts" else "Apps"
                onLeftSoftKeyClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val nextMode = if (isApps) T9SearchMode.CONTACTS else T9SearchMode.APPS
                    viewModel.switchT9Mode(nextMode, keepQuery = true)
                }
            } else {
                leftSoftKeyText = ""
                onLeftSoftKeyClick = {}
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.CONTACTS_DRAWER -> {
            if (isSearching) {
                val isContacts = state.t9SearchMode == T9SearchMode.CONTACTS
                leftSoftKeyText = if (isContacts) "Apps" else "Contacts"
                onLeftSoftKeyClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val nextMode = if (isContacts) T9SearchMode.APPS else T9SearchMode.CONTACTS
                    viewModel.switchT9Mode(nextMode, keepQuery = true)
                }
            } else {
                leftSoftKeyText = "Add Contact"
                onLeftSoftKeyClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        type = android.provider.ContactsContract.RawContacts.CONTENT_TYPE
                    }
                    try { context.startActivity(intent) } catch(e: Exception) { e.printStackTrace() }
                }
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.WEATHER_FORECAST -> {
            val tabLabel = when (state.weatherTab) {
                0 -> "Today"
                1 -> "Hourly"
                2 -> "Daily"
                else -> "Faves"
            }
            leftSoftKeyText = "Tab: $tabLabel"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.cycleWeatherTab()
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.AGENDA -> {
            leftSoftKeyText = "Add Event"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val intent = Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
                try { context.startActivity(intent) } catch(e: Exception) { e.printStackTrace() }
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.CONTACT_DETAIL -> {
            leftSoftKeyText = "Edit"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                selectedContactForDetail?.let { contact ->
                    val uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_URI, contact.id)
                    val intent = Intent(Intent.ACTION_EDIT).apply {
                        setDataAndType(uri, android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                    }
                    try { context.startActivity(intent) } catch(e: Exception) { e.printStackTrace() }
                }
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.WALLET -> {
            val catLabel = when (state.walletCategoryTab) {
                1 -> "Loyalty"
                2 -> "Flights"
                3 -> "Tickets"
                else -> "All"
            }
            leftSoftKeyText = "Category: $catLabel"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.cycleWalletCategory()
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.CALCULATOR -> {
            leftSoftKeyText = ""
            onLeftSoftKeyClick = {}
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.CLOCK -> {
            val tabLabel = when (state.clockTab) {
                0 -> "Clock"
                1 -> "Alarm"
                2 -> "Stopwatch"
                else -> "Timer"
            }
            leftSoftKeyText = "Tab: $tabLabel"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.cycleClockTab()
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.WALLET_PASS_DETAIL -> {
            val isLoyalty = selectedWalletPass?.type == PassType.LOYALTY_CARD
            leftSoftKeyText = "Next Pass"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val passes = state.walletPasses
                if (passes.isNotEmpty()) {
                    val currentIndex = passes.indexOfFirst { it.id == selectedWalletPass?.id }
                    val nextIndex = if (currentIndex != -1) (currentIndex + 1) % passes.size else 0
                    selectedWalletPass = passes[nextIndex]
                }
            }
            rightSoftKeyText = if (showFullscreenBarcode && !isLoyalty) "Close" else "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (showFullscreenBarcode && !isLoyalty) {
                    showFullscreenBarcode = false
                } else {
                    navigateBack()
                    showFullscreenBarcode = false
                }
            }
        }
        Screen.USAGE -> {
            val tabLabel = when (state.usageTab) {
                0 -> "Daily"
                1 -> "Weekly"
                2 -> "Monthly"
                else -> "About"
            }
            leftSoftKeyText = "Tab: $tabLabel"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.cycleUsageTab()
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.ALARM_EDIT, Screen.ALARM_OPTIONS, Screen.WORLD_CLOCK_SEARCH, Screen.WORLD_CLOCK_OPTIONS -> {
            leftSoftKeyText = ""
            onLeftSoftKeyClick = {}
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.NOTIFICATIONS -> {
            leftSoftKeyText = "Clear All"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                SymbianNotificationService.instance?.clearAll()
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.MESSAGES -> {
            val messagingApps = state.messagingApps
            val msgNotifs = state.activeNotifications.filter { isValidUserNotification(it) && MessagingUtils.isMessageNotification(it, messagingApps) }
            leftSoftKeyText = if (msgNotifs.isNotEmpty()) "Clear All" else "Settings"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (msgNotifs.isNotEmpty()) {
                    msgNotifs.forEach { sbn ->
                        SymbianNotificationService.instance?.clearNotification(sbn.key)
                    }
                } else {
                    navigateTo(Screen.SETTINGS_MESSAGES)
                }
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.EMAILS -> {
            val emailApps = state.emailApps
            val emailNotifs = state.activeNotifications.filter { isValidUserNotification(it) && MessagingUtils.isEmailNotification(it, emailApps) }
            leftSoftKeyText = if (emailNotifs.isNotEmpty()) "Clear All" else "Settings"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (emailNotifs.isNotEmpty()) {
                    emailNotifs.forEach { sbn ->
                        SymbianNotificationService.instance?.clearNotification(sbn.key)
                    }
                } else {
                    navigateTo(Screen.SETTINGS_EMAIL)
                }
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        Screen.CONTACT_DETAIL -> {
            leftSoftKeyText = "Edit"
            onLeftSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                selectedContactForDetail?.let { c ->
                    try {
                        val contactId = c.id.toLongOrNull()
                        if (contactId != null) {
                            val uri = android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, contactId)
                            val intent = Intent(Intent.ACTION_EDIT).apply {
                                setDataAndType(uri, android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e2: Exception) {}
                    }
                }
            }
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
        else -> {
            leftSoftKeyText = ""
            onLeftSoftKeyClick = {}
            rightSoftKeyText = "Back"
            onRightSoftKeyClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                navigateBack()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (currentScreen == Screen.CALCULATOR) {
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    val keyCode = event.key.keyCode
                    val nativeKeyCode = event.nativeKeyEvent.keyCode
                    val unicodeChar = event.nativeKeyEvent.unicodeChar
                    val utf16 = event.utf16CodePoint

                    val isStarKey = nativeKeyCode == android.view.KeyEvent.KEYCODE_STAR ||
                        nativeKeyCode == android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY ||
                        utf16 == '*'.code ||
                        unicodeChar == '*'.code

                    val isPoundKey = nativeKeyCode == android.view.KeyEvent.KEYCODE_POUND ||
                        utf16 == '#'.code ||
                        unicodeChar == '#'.code

                    if (isStarKey) {
                        onLeftSoftKeyClick()
                        return@onPreviewKeyEvent true
                    } else if (isPoundKey) {
                        onRightSoftKeyClick()
                        return@onPreviewKeyEvent true
                    }

                    if (keyCode == Key.DirectionUp.keyCode ||
                        keyCode == Key.DirectionDown.keyCode ||
                        keyCode == Key.DirectionLeft.keyCode ||
                        keyCode == Key.DirectionRight.keyCode ||
                        keyCode == Key.Enter.keyCode ||
                        keyCode == Key.NumPadEnter.keyCode) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    
                    if (keyCode == Key.DirectionLeft.keyCode) {
                        val moved = focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)
                        if (!moved) {
                            try { leftSoftKeyFocusRequester.requestFocus() } catch (e: Exception) {}
                        }
                        return@onPreviewKeyEvent true
                    } else if (keyCode == Key.DirectionRight.keyCode) {
                        val moved = focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)
                        if (!moved) {
                            try { rightSoftKeyFocusRequester.requestFocus() } catch (e: Exception) {}
                        }
                        return@onPreviewKeyEvent true
                    }

                    if (keyCode in Key.Zero.keyCode..Key.Nine.keyCode) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (currentScreen == Screen.APP_DRAWER || currentScreen == Screen.CONTACTS_DRAWER) {
                            viewModel.appendT9(event.utf16CodePoint.toChar())
                            return@onPreviewKeyEvent true
                        }
                        
                        if (currentScreen != Screen.MAIN) {
                            navigateTo(Screen.MAIN)
                        }
                        val char = event.utf16CodePoint.toChar()
                        viewModel.appendT9(char)
                        return@onPreviewKeyEvent true
                    } else if (keyCode == Key.Backspace.keyCode || keyCode == Key.Delete.keyCode) {
                        if (state.t9Query.isNotEmpty()) {
                            viewModel.backspaceT9()
                            return@onPreviewKeyEvent true
                        }
                    } else if (keyCode == Key.Back.keyCode || keyCode == Key.Escape.keyCode) {
                        if (state.t9Query.isNotEmpty()) {
                            viewModel.backspaceT9()
                            return@onPreviewKeyEvent true
                        } else if (currentScreen != Screen.MAIN) {
                            navigateBack()
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            },
        bottomBar = {
            SymbianBottomBar(
                leftText = leftSoftKeyText,
                rightText = rightSoftKeyText,
                onLeftClick = onLeftSoftKeyClick,
                onRightClick = onRightSoftKeyClick,
                leftModifier = Modifier.focusRequester(leftSoftKeyFocusRequester),
                rightModifier = Modifier.focusRequester(rightSoftKeyFocusRequester),
                isExtremeBatterySaver = state.isExtremeBatterySaver && currentScreen == Screen.MAIN
            )
        }
    ) { innerPadding ->
        val isExtremeMain = state.isExtremeBatterySaver && currentScreen == Screen.MAIN
        val isFullScreenPage = isExtremeMain || currentScreen == Screen.AGENDA || currentScreen == Screen.CONTACT_DETAIL || currentScreen == Screen.USAGE || currentScreen == Screen.WALLET || currentScreen == Screen.WALLET_PASS_DETAIL || currentScreen == Screen.CLOCK || currentScreen == Screen.CALCULATOR
        val topPadding = if (isFullScreenPage) 0.dp else innerPadding.calculateTopPadding()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding(), top = topPadding)
                .background(if (state.isExtremeBatterySaver) Color.Black else if (state.showWallpaper) MaterialTheme.colorScheme.background.copy(alpha = state.wallpaperTransparency) else MaterialTheme.colorScheme.background)
        ) {
            if (!isExtremeMain && currentScreen != Screen.AGENDA && currentScreen != Screen.CONTACT_DETAIL && currentScreen != Screen.USAGE && currentScreen != Screen.WEATHER_FORECAST && currentScreen != Screen.WALLET && currentScreen != Screen.WALLET_PASS_DETAIL && currentScreen != Screen.CLOCK && currentScreen != Screen.CALCULATOR) {
                SymbianTopBar(state, batteryInfo, signalLevel, onWeatherClick = { navigateTo(Screen.WEATHER_FORECAST) }, onClockClick = { navigateTo(Screen.CLOCK) })
            }
            
            val animationScale = remember(context) {
                try {
                    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
                } catch (e: Exception) {
                    1f
                }
            }
            val animationsEnabled = animationScale > 0f
            
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (animationsEnabled) {
                        (slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(animationSpec = tween(300))).togetherWith(
                            slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut(animationSpec = tween(300)))
                    } else {
                        fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
                    }
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    Screen.MAIN -> {
                        if (state.isExtremeBatterySaver) {
                            ExtremeBatterySaverScreen(
                                state = state,
                                viewModel = viewModel,
                                haptic = haptic,
                                onExitSaver = {
                                    viewModel.setExtremeBatterySaver(false)
                                },
                                onNavigate = { screen ->
                                    navigateTo(screen)
                                }
                            )
                        } else {
                        LaunchedEffect(Unit) {
                            try { focusRequester.requestFocus() } catch (e: Exception) {}
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (state.t9Query.isEmpty() && state.clockType == ClockType.ANALOGUE) {
                                ClassicClockWidget(state)
                            }
                            // Pinned Apps Row (Only visible if not searching)
                            if (state.pinnedApps.isNotEmpty() && state.t9Query.isEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (state.showWallpaper) MaterialTheme.colorScheme.background.copy(alpha = 0.4f) else MaterialTheme.colorScheme.background)
                                    .background(if (state.showWallpaper) Color.Transparent else Color.White.copy(alpha = 0.12f))
                                    .padding(vertical = 6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.pinnedApps) { app ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isFocused by interactionSource.collectIsFocusedAsState()
                                    Box(
                                        modifier = Modifier
                                            .symbianHighlight(isFocused)
                                            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { viewModel.launchApp(app) }
                                            .padding(4.dp)
                                    ) {
                                        Image(
                                            bitmap = app.icon.toCachedImageBitmap(),
                                            contentDescription = app.name,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                            }
                            DashedDivider()
                        }
    
                        if (state.t9Query.isNotEmpty()) {
                            T9SearchView(state, viewModel, haptic, onContactSelected = { contact ->
                                selectedContactForDetail = contact
                                navigateTo(Screen.CONTACT_DETAIL)
                            })
                        } else {
                            HubView(
                                state = state, 
                                focusRequester = focusRequester,
                                onNavigateToNotifications = { navigateTo(Screen.NOTIFICATIONS) },
                                onNavigateToMessages = { navigateTo(Screen.MESSAGES) },
                                onNavigateToEmails = { navigateTo(Screen.EMAILS) },
                                onNavigateToApps = { navigateTo(Screen.APP_DRAWER) },
                                onNavigateToContacts = { navigateTo(Screen.CONTACTS_DRAWER) },
                                onNavigateToAgenda = { navigateTo(Screen.AGENDA) }
                            )
                        }
                        }
                        }
                    }
                Screen.MENU -> {
                    MenuView(state, viewModel, haptic) { screen ->
                        navigateTo(screen)
                    }
                }
                Screen.WALLET -> {
                    WalletScreen(
                        state = state,
                        viewModel = viewModel,
                        haptic = haptic,
                        onNavigateToPassDetail = { pass ->
                            selectedWalletPass = pass
                            navigateTo(Screen.WALLET_PASS_DETAIL)
                        },
                        onBack = { navigateBack() }
                    )
                }
                Screen.WALLET_PASS_DETAIL -> {
                    selectedWalletPass?.let { pass ->
                        WalletPassDetailScreen(
                            pass = pass,
                            state = state,
                            viewModel = viewModel,
                            haptic = haptic,
                            showFullscreenBarcode = showFullscreenBarcode,
                            onToggleFullscreenBarcode = { showFullscreenBarcode = it },
                            onBack = { navigateBack() },
                            onCycleNext = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                val passes = state.walletPasses
                                if (passes.isNotEmpty()) {
                                    val currentIndex = passes.indexOfFirst { it.id == selectedWalletPass?.id }
                                    val nextIndex = if (currentIndex != -1) (currentIndex + 1) % passes.size else 0
                                    selectedWalletPass = passes[nextIndex]
                                }
                            }
                        )
                    }
                }
                Screen.CALCULATOR -> {
                    CalculatorScreen(
                        state = state,
                        haptic = haptic,
                        onBack = { navigateBack() }
                    )
                }
                Screen.USAGE -> {
                    UsageStatsScreen(state, viewModel, haptic)
                }
                Screen.WEATHER_FORECAST -> {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        viewModel.loadWeather()
                    }
                    WeatherForecastScreen(
                        state = state,
                        viewModel = viewModel,
                        haptic = haptic,
                        onSettingsClick = { navigateTo(Screen.SETTINGS_WEATHER) },
                        onRefresh = { viewModel.loadWeather() }
                    )
                }
                Screen.NOTIFICATIONS -> {
                    val messagingApps = state.messagingApps
                    val emailApps = state.emailApps
                    NotificationsView(state, haptic, filter = { isValidUserNotification(it) && !MessagingUtils.isMessageNotification(it, messagingApps) && !MessagingUtils.isEmailNotification(it, emailApps) })
                }
                Screen.MESSAGES -> {
                    val messagingApps = state.messagingApps
                    val detectedApps = remember(state.allApps, context) {
                        MessagingUtils.detectInstalledMessagingApps(state.allApps, context)
                    }
                    val activeMessagingApps = remember(state.allApps, messagingApps, detectedApps) {
                        val filtered = state.allApps.filter { messagingApps.contains(it.packageName) }
                            .sortedBy { messagingApps.indexOf(it.packageName) }
                        if (filtered.isNotEmpty()) filtered else detectedApps
                    }
                    NotificationsView(
                        state = state,
                        haptic = haptic,
                        pinnedApps = activeMessagingApps,
                        onAppClick = { viewModel.launchApp(it) },
                        onSettingsClick = { navigateTo(Screen.SETTINGS_MESSAGES) },
                        emptyText = "No unread messages or missed calls",
                        emptyIcon = Icons.Default.Chat,
                        filter = { isValidUserNotification(it) && MessagingUtils.isMessageNotification(it, messagingApps) }
                    )
                }
                Screen.EMAILS -> {
                    val emailApps = state.emailApps
                    val detectedApps = remember(state.allApps, context) {
                        MessagingUtils.detectInstalledEmailApps(state.allApps, context)
                    }
                    val activeEmailApps = remember(state.allApps, emailApps, detectedApps) {
                        val filtered = state.allApps.filter { emailApps.contains(it.packageName) }
                            .sortedBy { emailApps.indexOf(it.packageName) }
                        if (filtered.isNotEmpty()) filtered else detectedApps
                    }
                    NotificationsView(
                        state = state,
                        haptic = haptic,
                        pinnedApps = activeEmailApps,
                        onAppClick = { viewModel.launchApp(it) },
                        onSettingsClick = { navigateTo(Screen.SETTINGS_EMAIL) },
                        emptyText = "No unread emails",
                        emptyIcon = Icons.Default.Email,
                        filter = { isValidUserNotification(it) && MessagingUtils.isEmailNotification(it, emailApps) }
                    )
                }
                Screen.SETTINGS_ROOT -> {
                    SettingsRootView(state, haptic) { navigateTo(it) }
                }
                Screen.SETTINGS_HOME_SCREEN -> {
                    SettingsHomeScreen(state, haptic) { navigateTo(it) }
                }
                Screen.SETTINGS_STATUS_BAR -> {
                    SettingsStatusBar(state, haptic) { navigateTo(it) }
                }
                Screen.SETTINGS_WEATHER -> {
                    SettingsWeather(state, viewModel, haptic)
                }
                Screen.SETTINGS_TIME_DATE -> {
                    SettingsTimeDate(state, viewModel, haptic)
                }
                Screen.SETTINGS_HOME_CONTENT -> {
                    SettingsHomeContent(state, viewModel, haptic) { navigateTo(it) }
                }
                Screen.SETTINGS_T9_SEARCH -> {
                    SettingsT9Search(state, viewModel, haptic)
                }
                Screen.SETTINGS_PINNED_APPS -> {
                    SettingsPinnedApps(state, viewModel, haptic) { navigateTo(it) }
                }
                Screen.SETTINGS_REORDER_PINNED -> {
                    SettingsReorderPinned(state, viewModel, haptic)
                }
                Screen.SETTINGS_MESSAGES -> {
                    SettingsMessages(state, viewModel, haptic) { navigateTo(it) }
                }
                Screen.SETTINGS_REORDER_MESSAGES -> {
                    SettingsReorderMessages(state, viewModel, haptic)
                }
                Screen.SETTINGS_EMAIL -> {
                    SettingsEmail(state, viewModel, haptic) { navigateTo(it) }
                }
                Screen.SETTINGS_REORDER_EMAIL -> {
                    SettingsReorderEmail(state, viewModel, haptic)
                }
                Screen.SETTINGS_CALENDAR -> {
                    SettingsCalendar(state, viewModel, haptic)
                }
                Screen.SETTINGS_GLOBAL -> {
                    SettingsGlobal(state, viewModel, haptic) { navigateTo(it) }
                }
                Screen.SETTINGS_CUSTOM_THEME -> {
                    SettingsCustomTheme(state, viewModel, haptic)
                }
                Screen.SETTINGS_ICON_PACK -> {
                    SettingsIconPack(state, viewModel, haptic) { navigateTo(it) }
                }
                Screen.SETTINGS_QUICK_INFO, Screen.SETTINGS_MANAGE_APPS, Screen.SETTINGS_MESSAGING_APPS, Screen.SETTINGS_LOOK_AND_FEEL -> {
                    SettingsRootView(state, haptic) { navigateTo(it) }
                }
                Screen.APP_DRAWER -> {
                    AppDrawer(state, viewModel, haptic, onContactSelected = { contact ->
                        selectedContactForDetail = contact
                        navigateTo(Screen.CONTACT_DETAIL)
                    })
                }
                Screen.CONTACTS_DRAWER -> {
                    ContactsDrawer(state, viewModel, haptic, onContactSelected = { contact ->
                        selectedContactForDetail = contact
                        navigateTo(Screen.CONTACT_DETAIL)
                    })
                }
                Screen.AGENDA -> {
                    AgendaScreen(events = state.todayEvents, is24HourFormat = state.is24HourFormat, haptic = haptic, onBack = { navigateBack() })
                }
                Screen.CONTACT_DETAIL -> {
                    if (selectedContactForDetail != null) {
                        ContactDetailScreen(
                            state = state,
                            viewModel = viewModel,
                            contact = selectedContactForDetail!!,
                            haptic = haptic,
                            onBack = { navigateBack() }
                        )
                    } else {
                        navigateTo(Screen.MAIN)
                    }
                }
                Screen.CLOCK -> {
                    ClockAppScreen(
                        state = state,
                        viewModel = viewModel,
                        haptic = haptic,
                        onBack = { navigateBack() },
                        onAddAlarm = { navigateTo(Screen.ALARM_EDIT); selectedAlarm = null },
                        onAlarmOptions = { selectedAlarm = it; navigateTo(Screen.ALARM_OPTIONS) },
                        onAddWorldClock = { navigateTo(Screen.WORLD_CLOCK_SEARCH) },
                        onWorldClockOptions = { selectedWorldClock = it; navigateTo(Screen.WORLD_CLOCK_OPTIONS) }
                    )
                }
                Screen.ALARM_EDIT -> {
                    AlarmEditScreen(
                        state = state,
                        alarm = selectedAlarm,
                        haptic = haptic,
                        onSave = { 
                            if (selectedAlarm == null) {
                                viewModel.addAlarm(context, it)
                            } else {
                                viewModel.updateAlarm(context, it)
                            }
                            navigateTo(Screen.CLOCK)
                        }
                    )
                }
                Screen.ALARM_OPTIONS -> {
                    if (selectedAlarm != null) {
                        AlarmOptionsScreen(
                            state = state,
                            alarm = selectedAlarm!!,
                            haptic = haptic,
                            onEdit = { navigateTo(Screen.ALARM_EDIT) },
                            onDelete = { 
                                viewModel.deleteAlarm(context, selectedAlarm!!.id)
                                navigateTo(Screen.CLOCK)
                            }
                        )
                    } else {
                        navigateTo(Screen.CLOCK)
                    }
                }
                Screen.WORLD_CLOCK_SEARCH -> {
                    WorldClockSearchScreen(
                        state = state,
                        haptic = haptic,
                        onSelect = {
                            if (selectedWorldClock != null) {
                                viewModel.deleteWorldClock(selectedWorldClock!!.id)
                            }
                            viewModel.addWorldClock(it)
                            selectedWorldClock = null
                            navigateTo(Screen.CLOCK)
                        }
                    )
                }
                Screen.WORLD_CLOCK_OPTIONS -> {
                    if (selectedWorldClock != null) {
                        WorldClockOptionsScreen(
                            state = state,
                            haptic = haptic,
                            onEdit = { navigateTo(Screen.WORLD_CLOCK_SEARCH) },
                            onDelete = {
                                viewModel.deleteWorldClock(selectedWorldClock!!.id)
                                navigateTo(Screen.CLOCK)
                            }
                        )
                    } else {
                        navigateTo(Screen.CLOCK)
                    }
                }
            }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

@Composable
fun AppDrawer(
    state: LauncherState, 
    viewModel: LauncherViewModel, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onContactSelected: ((ContactInfo) -> Unit)? = null
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    if (selectedApp != null) {
        ManageAppDialogHandler(selectedApp, state, viewModel) { selectedApp = null }
        return
    }
    if (state.t9Query.isNotEmpty()) {
        T9SearchView(state, viewModel, haptic, onContactSelected = onContactSelected)
    } else {
        val firstItemFocusRequester = remember { FocusRequester() }
        LaunchedEffect(state.allApps) {
            if (state.allApps.isNotEmpty()) {
                try { firstItemFocusRequester.requestFocus() } catch(e: Exception) {}
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.allApps) { index, app ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                            .symbianHighlight(isFocused)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.launchApp(app)
                                }
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = app.icon.toCachedImageBitmap(),
                            contentDescription = app.name,
                            modifier = Modifier.size(36.dp * state.iconScale)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = app.name,
                            color = if (isFocused) Color.Black else Color.White,
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
                        )
                    }
                    val moreInteractionSource = remember { MutableInteractionSource() }
                    val isMoreFocused by moreInteractionSource.collectIsFocusedAsState()
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedApp = app
                        },
                        interactionSource = moreInteractionSource,
                        modifier = Modifier.symbianHighlight(isMoreFocused)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.MoreVert,
                            contentDescription = "More Options",
                            tint = if (isMoreFocused) Color.Black else Color.White
                        )
                    }
                }
                DashedDivider()
            }
        }
    }
}


@Composable
fun AgendaScreen(
    events: List<CalendarEvent>,
    is24HourFormat: Boolean = false,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }
    val currentTimeMs = rememberTickingTime(60_000L)
    LaunchedEffect(events) {
        if (events.isNotEmpty()) {
            try { firstItemFocusRequester.requestFocus() } catch(e: Exception) {}
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            
    ) {
        // Retro Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            Text(
                text = "Agenda",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 2f))
            )
        }
        
        DashedDivider()
        
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (events.isEmpty()) {
                item {
                    Text(
                        text = "No upcoming events.",
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 18.sp
                    )
                }
            } else {
                itemsIndexed(events) { index, event ->
                    val intSource = remember { MutableInteractionSource() }
                    val isFocused by intSource.collectIsFocusedAsState()
                    val now = currentTimeMs
                    val isOngoing = event.isOngoing(now)
                    val timeFmt = if (is24HourFormat) "HH:mm" else "h:mm a"
                    val timeFormat = Formatters.get(timeFmt)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                            .symbianHighlight(isFocused)
                            .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = android.provider.CalendarContract.Events.CONTENT_URI.buildUpon().appendPath(event.id.toString()).build()
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isOngoing) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF76FF03), shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    event.title,
                                    color = if (isFocused) Color.Black else Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            val timeStr = if (isOngoing) {
                                val endStr = timeFormat.format(java.util.Date(event.getEffectiveEndTime()))
                                val remaining = event.getRemainingTimeFormatted(now)
                                "now-ends at $endStr (in $remaining)"
                            } else {
                                val startsIn = event.getStartsInFormatted(now)
                                if (android.text.format.DateUtils.isToday(event.dtStart)) {
                                    val startStr = timeFormat.format(java.util.Date(event.dtStart))
                                    "$startStr • $startsIn"
                                } else {
                                    val format = Formatters.get("MMM d, $timeFmt")
                                    val startStr = format.format(java.util.Date(event.dtStart))
                                    "$startStr • $startsIn"
                                }
                            }
                            
                            Text(
                                timeStr,
                                color = if (isFocused) {
                                    if (isOngoing) Color(0xFF006400) else Color.DarkGray
                                } else {
                                    if (isOngoing) Color(0xFF76FF03) else Color.LightGray
                                },
                                fontSize = 16.sp,
                                fontWeight = if (isOngoing) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                    DashedDivider()
                }
            }
        }
    }
}


@Composable
fun ContactDetailScreen(
    state: LauncherState,
    viewModel: LauncherViewModel,
    contact: ContactInfo,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var callLogs by remember(contact.id) { mutableStateOf<List<CallLogEntry>?>(null) }
    var showFullPhotoDialog by remember { mutableStateOf(false) }

    val isScrolledDown by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 80
        }
    }

    LaunchedEffect(contact.id) {
        callLogs = viewModel.getCallLogsForContact(contact)
    }

    if (showFullPhotoDialog && contact.photoUri != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullPhotoDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F172A))
                    .border(2.dp, Color(0xFF64B5F6), RoundedCornerShape(24.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = contact.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1
                        )
                        IconButton(
                            onClick = { showFullPhotoDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AsyncImage(
                        model = contact.photoUri,
                        contentDescription = contact.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A).copy(alpha = 0.65f))
    ) {
        // Retro Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isScrolledDown) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                kotlinx.coroutines.CoroutineScope(coroutineScope.coroutineContext).launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        }
                ) {
                    val backSource = remember { MutableInteractionSource() }
                    val isBackFocused by backSource.collectIsFocusedAsState()
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                        interactionSource = backSource,
                        modifier = Modifier
                            .size(36.dp)
                            .symbianHighlight(isBackFocused, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isBackFocused) Color.Black else Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    AnimatedContent(
                        targetState = isScrolledDown,
                        label = "topBarContent"
                    ) { scrolled ->
                        if (scrolled) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (contact.photoUri != null) {
                                    AsyncImage(
                                        model = contact.photoUri,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, Color(0xFF64B5F6), CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E293B))
                                            .border(1.dp, Color(0xFF64B5F6), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color(0xFF90CAF9),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = contact.name.ifBlank { "Contact Details" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = Color(0xFF64B5F6),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Tap to view photo",
                                            color = Color(0xFF64B5F6),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Contact Details",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f))
                            )
                        }
                    }
                }

                val editSource = remember { MutableInteractionSource() }
                val isEditFocused by editSource.collectIsFocusedAsState()
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        try {
                            val contactId = contact.id.toLongOrNull()
                            if (contactId != null) {
                                val uri = android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, contactId)
                                val intent = Intent(Intent.ACTION_EDIT).apply {
                                    setDataAndType(uri, android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e2: Exception) {}
                        }
                    },
                    interactionSource = editSource,
                    modifier = Modifier
                        .size(36.dp)
                        .symbianHighlight(isEditFocused, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Contact",
                        tint = if (isEditFocused) Color.Black else Color.White
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                // Header with photo and name
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val photoSource = remember { MutableInteractionSource() }
                        val isPhotoFocused by photoSource.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .size(144.dp)
                                .symbianHighlight(isPhotoFocused, RoundedCornerShape(22.dp))
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF1E293B))
                                .border(
                                    BorderStroke(2.5.dp, if (isPhotoFocused) Color.Yellow else Color(0xFF64B5F6)),
                                    RoundedCornerShape(22.dp)
                                )
                                .clickable(
                                    interactionSource = photoSource,
                                    indication = LocalIndication.current
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (contact.photoUri != null) {
                                        showFullPhotoDialog = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (contact.photoUri != null) {
                                AsyncImage(
                                    model = contact.photoUri,
                                    contentDescription = contact.name,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color(0xFF90CAF9),
                                        modifier = Modifier.size(76.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = contact.name.ifBlank { "Unknown Contact" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1.5f, 1.5f), blurRadius = 3f))
                        )

                        if (contact.nicknames.isNotEmpty()) {
                            Text(
                                text = "\"${contact.nicknames.first()}\"",
                                color = Color(0xFF81D4FA),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        contact.organization?.let { org ->
                            val orgText = buildString {
                                if (org.title.isNotBlank()) append(org.title)
                                if (org.title.isNotBlank() && org.company.isNotBlank()) append(" • ")
                                if (org.company.isNotBlank()) append(org.company)
                            }
                            if (orgText.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = null,
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = orgText,
                                        color = Color.LightGray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        val primaryPhone = contact.phoneNumbers.firstOrNull()
                        if (primaryPhone != null) {
                            // Quick Call
                            val callSource = remember { MutableInteractionSource() }
                            val isCallFocused by callSource.collectIsFocusedAsState()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .symbianHighlight(isCallFocused, RoundedCornerShape(8.dp))
                                    .clickable(interactionSource = callSource, indication = LocalIndication.current) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${primaryPhone.number}"))
                                        context.startActivity(intent)
                                    }
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF2E7D32), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Call", color = Color.White, fontSize = 12.sp)
                            }

                            // Quick SMS
                            val smsSource = remember { MutableInteractionSource() }
                            val isSmsFocused by smsSource.collectIsFocusedAsState()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .symbianHighlight(isSmsFocused, RoundedCornerShape(8.dp))
                                    .clickable(interactionSource = smsSource, indication = LocalIndication.current) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${primaryPhone.number}"))
                                        try { context.startActivity(intent) } catch (e: Exception) {}
                                    }
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF0288D1), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Message, contentDescription = "Message", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Message", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        val primaryEmail = contact.emails.firstOrNull()
                        if (primaryEmail != null) {
                            val emailSource = remember { MutableInteractionSource() }
                            val isEmailFocused by emailSource.collectIsFocusedAsState()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .symbianHighlight(isEmailFocused, RoundedCornerShape(8.dp))
                                    .clickable(interactionSource = emailSource, indication = LocalIndication.current) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${primaryEmail.address}"))
                                        try { context.startActivity(intent) } catch (e: Exception) {}
                                    }
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFFF57C00), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = "Email", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Email", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        // Share
                        val shareSource = remember { MutableInteractionSource() }
                        val isShareFocused by shareSource.collectIsFocusedAsState()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .symbianHighlight(isShareFocused, RoundedCornerShape(8.dp))
                                .clickable(interactionSource = shareSource, indication = LocalIndication.current) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val shareText = buildString {
                                        append("Contact: ${contact.name}\n")
                                        contact.phoneNumbers.forEach { append("${it.label}: ${it.number}\n") }
                                        contact.emails.forEach { append("${it.label}: ${it.address}\n") }
                                    }.trim()
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Contact"))
                                    } catch (e: Exception) {}
                                }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF455A64), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Share", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Phone Numbers Section
            if (contact.phoneNumbers.isNotEmpty()) {
                item {
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Phone Numbers",
                            color = Color(0xFF90CAF9),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contact.phoneNumbers) { phone ->
                    val rowSource = remember { MutableInteractionSource() }
                    val isRowFocused by rowSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isRowFocused, RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = phone.number,
                                color = if (isRowFocused) Color.Black else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = phone.label.ifBlank { "Mobile" },
                                color = if (isRowFocused) Color.DarkGray else Color.LightGray,
                                fontSize = 13.sp
                            )
                        }

                        // Call button
                        val callIntSource = remember { MutableInteractionSource() }
                        val isCallFocused by callIntSource.collectIsFocusedAsState()
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${phone.number}"))
                                context.startActivity(intent)
                            },
                            interactionSource = callIntSource,
                            modifier = Modifier
                                .size(40.dp)
                                .symbianHighlight(isCallFocused, CircleShape)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF4CAF50))
                        }

                        // SMS button
                        val smsIntSource = remember { MutableInteractionSource() }
                        val isSmsFocused by smsIntSource.collectIsFocusedAsState()
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${phone.number}"))
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            },
                            interactionSource = smsIntSource,
                            modifier = Modifier
                                .size(40.dp)
                                .symbianHighlight(isSmsFocused, CircleShape)
                        ) {
                            Icon(Icons.Default.Message, contentDescription = "SMS", tint = Color(0xFF29B6F6))
                        }

                        // Copy button
                        val copySource = remember { MutableInteractionSource() }
                        val isCopyFocused by copySource.collectIsFocusedAsState()
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(phone.number))
                                android.widget.Toast.makeText(context, "Number copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            interactionSource = copySource,
                            modifier = Modifier
                                .size(40.dp)
                                .symbianHighlight(isCopyFocused, CircleShape)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Number", tint = Color.LightGray)
                        }
                    }
                }
            }

            // Emails Section
            if (contact.emails.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Email Addresses",
                            color = Color(0xFFFFCC80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contact.emails) { email ->
                    val emailSource = remember { MutableInteractionSource() }
                    val isEmailFocused by emailSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isEmailFocused, RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .clickable(interactionSource = emailSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${email.address}"))
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = if (isEmailFocused) Color.Black else Color(0xFFFFB74D),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = email.address,
                                color = if (isEmailFocused) Color.Black else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = email.label.ifBlank { "Email" },
                                color = if (isEmailFocused) Color.DarkGray else Color.LightGray,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(email.address))
                                android.widget.Toast.makeText(context, "Email copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp * state.iconScale)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Email", tint = Color.LightGray)
                        }
                    }
                }
            }

            // Birthday & Dates Section
            if (contact.birthday != null || contact.events.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cake, contentDescription = null, tint = Color(0xFFF48FB1), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Birthday & Important Dates",
                            color = Color(0xFFF8BBD0),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (contact.birthday != null) {
                    item {
                        val bdayInfo = remember(contact.birthday) { formatContactDate(contact.birthday, isBirthday = true) }
                        val bdaySource = remember { MutableInteractionSource() }
                        val isBdayFocused by bdaySource.collectIsFocusedAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .symbianHighlight(isBdayFocused, RoundedCornerShape(8.dp))
                                .background(Color(0xFF880E4F).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFF48FB1).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cake, contentDescription = "Birthday", tint = Color(0xFFF48FB1), modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bdayInfo?.naturalDate ?: contact.birthday,
                                    color = if (isBdayFocused) Color.Black else Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = bdayInfo?.countdownText ?: "Birthday",
                                        color = if (isBdayFocused) Color.DarkGray else Color(0xFFFF80AB),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    bdayInfo?.ageText?.let { age ->
                                        Text(
                                            text = " • $age",
                                            color = if (isBdayFocused) Color.DarkGray else Color(0xFFF8BBD0),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(contact.events.filter { it.type != android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY }) { event ->
                    val evtInfo = remember(event.date) { formatContactDate(event.date, isBirthday = false) }
                    val evtSource = remember { MutableInteractionSource() }
                    val isEvtFocused by evtSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isEvtFocused, RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFFCE93D8), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = evtInfo?.naturalDate ?: event.date,
                                color = if (isEvtFocused) Color.Black else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = event.label.ifBlank { "Special Event" },
                                    color = if (isEvtFocused) Color.DarkGray else Color.LightGray,
                                    fontSize = 13.sp
                                )
                                if (evtInfo != null && evtInfo.daysRemaining >= 0) {
                                    Text(
                                        text = " • ${evtInfo.countdownText}",
                                        color = if (isEvtFocused) Color.DarkGray else Color(0xFFE1BEE7),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Notes Section
            if (contact.notes.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFFFE082), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notes",
                            color = Color(0xFFFFECB3),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contact.notes) { note ->
                    val noteSource = remember { MutableInteractionSource() }
                    val isNoteFocused by noteSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isNoteFocused, RoundedCornerShape(6.dp))
                            .background(Color(0xFF3E2723).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = note,
                            color = if (isNoteFocused) Color.Black else Color(0xFFFFF8E1),
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            lineHeight = 20.sp
                        )
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(note))
                                android.widget.Toast.makeText(context, "Note copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Note", tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Addresses Section
            if (contact.addresses.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF80CBC4), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Addresses",
                            color = Color(0xFFB2DFDB),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contact.addresses) { addr ->
                    val addrSource = remember { MutableInteractionSource() }
                    val isAddrFocused by addrSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isAddrFocused, RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .clickable(interactionSource = addrSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val uri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(addr.formattedAddress)}")
                                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                try { context.startActivity(mapIntent) } catch (e: Exception) {}
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF80CBC4), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = addr.formattedAddress,
                                color = if (isAddrFocused) Color.Black else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = addr.label.ifBlank { "Address" },
                                color = if (isAddrFocused) Color.DarkGray else Color.LightGray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Websites Section
            if (contact.websites.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF80DEEA), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Websites",
                            color = Color(0xFFB2EBF2),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contact.websites) { site ->
                    val siteSource = remember { MutableInteractionSource() }
                    val isSiteFocused by siteSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isSiteFocused, RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .clickable(interactionSource = siteSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val url = if (!site.startsWith("http://") && !site.startsWith("https://")) "https://$site" else site
                                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                try { context.startActivity(browserIntent) } catch (e: Exception) {}
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF80DEEA), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = site,
                            color = if (isSiteFocused) Color.Black else Color(0xFF80DEEA),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Call History Section (Underneath all that)
            item {
                Spacer(modifier = Modifier.height(12.dp))
                DashedDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFFA7FFEB),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Call History",
                            color = Color(0xFFA7FFEB),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    if (callLogs != null) {
                        Text(
                            text = "${callLogs?.size ?: 0} calls",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            val logs = callLogs
            if (logs == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFFA7FFEB),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Loading call logs...",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else if (logs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneCallback,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No call history with this contact",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                items(logs) { entry ->
                    val isIncoming = entry.type == android.provider.CallLog.Calls.INCOMING_TYPE
                    val isOutgoing = entry.type == android.provider.CallLog.Calls.OUTGOING_TYPE
                    val isMissed = entry.type == android.provider.CallLog.Calls.MISSED_TYPE
                    val isRejected = entry.type == android.provider.CallLog.Calls.REJECTED_TYPE
                    val isBlocked = entry.type == android.provider.CallLog.Calls.BLOCKED_TYPE
                    val isVoicemail = entry.type == android.provider.CallLog.Calls.VOICEMAIL_TYPE

                    val icon = when {
                        isIncoming -> Icons.Default.CallReceived
                        isOutgoing -> Icons.Default.CallMade
                        isMissed -> Icons.Default.CallMissed
                        isRejected -> Icons.Default.CallEnd
                        isVoicemail -> Icons.Default.Voicemail
                        isBlocked -> Icons.Default.Block
                        else -> Icons.Default.Call
                    }

                    val badgeColor = when {
                        isIncoming -> Color(0xFF4CAF50)
                        isOutgoing -> Color(0xFF29B6F6)
                        isMissed -> Color(0xFFE53935)
                        isRejected -> Color(0xFFFF7043)
                        isVoicemail -> Color(0xFFFFB300)
                        isBlocked -> Color(0xFF9E9E9E)
                        else -> Color(0xFFB0BEC5)
                    }

                    val typeLabel = when {
                        isIncoming -> "Incoming Call"
                        isOutgoing -> "Outgoing Call"
                        isMissed -> "Missed Call"
                        isRejected -> "Declined Call"
                        isVoicemail -> "Voicemail"
                        isBlocked -> "Blocked Call"
                        else -> "Call"
                    }

                    val logSource = remember { MutableInteractionSource() }
                    val isLogFocused by logSource.collectIsFocusedAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .symbianHighlight(isLogFocused, RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
                            .clickable(interactionSource = logSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${entry.number}"))
                                context.startActivity(intent)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(badgeColor.copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, badgeColor.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = typeLabel,
                                tint = badgeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = typeLabel,
                                    color = if (isLogFocused) Color.Black else if (isMissed) Color(0xFFFF8A80) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isMissed) FontWeight.Bold else FontWeight.SemiBold
                                )
                                if (entry.number.isNotBlank()) {
                                    Text(
                                        text = " (${entry.number})",
                                        color = if (isLogFocused) Color.DarkGray else Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                text = entry.getFormattedDate(),
                                color = if (isLogFocused) Color.DarkGray else Color.LightGray,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = entry.getFormattedDuration(),
                            color = if (isLogFocused) Color.Black else if (isMissed || isRejected) Color(0xFFFF8A80) else Color(0xFF81C784),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

@Composable
fun ContactsDrawer(
    state: LauncherState, 
    viewModel: LauncherViewModel, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onContactSelected: (ContactInfo) -> Unit
) {
    if (state.t9Query.isNotEmpty()) {
        T9SearchView(state, viewModel, haptic, onContactSelected = onContactSelected)
    } else {
        val context = LocalContext.current
        val firstItemFocusRequester = remember { FocusRequester() }
        LaunchedEffect(state.contacts) {
            if (state.contacts.isNotEmpty()) {
                try { firstItemFocusRequester.requestFocus() } catch(e: Exception) {}
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.contacts) { index, contact ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .symbianHighlight(isFocused)
                        .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onContactSelected(contact)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (contact.photoUri != null) {
                        AsyncImage(
                            model = contact.photoUri,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp * state.iconScale).clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isFocused) Color.Black else Color.White,
                            modifier = Modifier.size(36.dp * state.iconScale)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = contact.name,
                            color = if (isFocused) Color.Black else Color.White,
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
                        )
                        if (contact.primaryPhoneNumber.isNotEmpty()) {
                            Text(
                                text = contact.primaryPhoneNumber,
                                color = if (isFocused) Color.DarkGray else Color.LightGray,
                                fontFamily = state.currentFont.toFontFamily(),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                DashedDivider()
            }
        }
    }
}

@Composable
fun MenuView(
    state: LauncherState, 
    viewModel: LauncherViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, 
    onNavigate: (Screen) -> Unit
) {
    var showBatterySaverConfirmDialog by remember { mutableStateOf(false) }
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { firstItemFocusRequester.requestFocus() } catch(e: Exception) {}
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MenuIcon("Settings", haptic, { SkeuomorphicSettingsIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f).focusRequester(firstItemFocusRequester), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.SETTINGS_ROOT) }
            MenuIcon("Apps", haptic, { SkeuomorphicAppsIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.APP_DRAWER) }
            MenuIcon("Contacts", haptic, { SkeuomorphicContactsIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.CONTACTS_DRAWER) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MenuIcon("Messages", haptic, { SkeuomorphicMessageIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.MESSAGES) }
            MenuIcon("Agenda", haptic, { SkeuomorphicCalendarIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.AGENDA) }
            MenuIcon("Clock", haptic, { SkeuomorphicClockIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.CLOCK) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MenuIcon("Calculator", haptic, { SkeuomorphicCalculatorIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.CALCULATOR) }
            MenuIcon("Wallet", haptic, { SkeuomorphicWalletIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.WALLET) }
            MenuIcon("Usage", haptic, { SkeuomorphicUsageIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.USAGE) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MenuIcon(
                label = if (state.isExtremeBatterySaver) "Exit Saver" else "Battery Saver", 
                haptic = haptic, 
                icon = { SkeuomorphicBatterySaverIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, 
                modifier = Modifier.weight(1f), 
                fontFamily = state.currentFont.toFontFamily()
            ) { 
                if (state.isExtremeBatterySaver) {
                    viewModel.setExtremeBatterySaver(false)
                    onNavigate(Screen.MAIN)
                } else {
                    showBatterySaverConfirmDialog = true
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    if (showBatterySaverConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBatterySaverConfirmDialog = false },
            title = {
                Text("Extreme Battery Saver", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Switch to Extreme Battery Saver? The screen will turn AMOLED black and show only essential apps (Phone, SMS, Calendar, Camera, and two custom apps) with a clock to maximize battery life.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatterySaverConfirmDialog = false
                        viewModel.setExtremeBatterySaver(true)
                        onNavigate(Screen.MAIN)
                    }
                ) {
                    Text("Turn On")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatterySaverConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
fun MenuIcon(
    label: String,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .symbianHighlight(isFocused)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontFamily = fontFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
        )
    }
}

fun isPersistentNotification(sbn: android.service.notification.StatusBarNotification): Boolean {
    val notif = sbn.notification ?: return true
    val flags = notif.flags
    
    // Group summary notifications (container headers)
    if ((flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return true
    
    // Ongoing, non-clearable, or foreground service notifications
    if (sbn.isOngoing || (flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0) return true
    if (!sbn.isClearable || (flags and android.app.Notification.FLAG_NO_CLEAR) != 0) return true
    if ((flags and android.app.Notification.FLAG_FOREGROUND_SERVICE) != 0) return true
    
    // System persistent categories
    val category = notif.category
    if (category == android.app.Notification.CATEGORY_SERVICE ||
        category == android.app.Notification.CATEGORY_SYSTEM ||
        category == android.app.Notification.CATEGORY_NAVIGATION ||
        category == android.app.Notification.CATEGORY_LOCATION_SHARING) {
        return true
    }
    
    // Persistent system packages / connected device indicators (USB, charging, tethering, debugging)
    val pkg = sbn.packageName.lowercase()
    if (pkg == "android" || pkg == "com.android.systemui" || pkg.startsWith("com.android.providers.media")) {
        val title = (notif.extras.getString(android.app.Notification.EXTRA_TITLE) 
            ?: notif.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: "").lowercase()
        val text = (notif.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: "").lowercase()
        val channelId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) notif.channelId?.lowercase() ?: "" else ""
        
        if (title.contains("usb") || text.contains("usb") || channelId.contains("usb") ||
            title.contains("debugging") || text.contains("debugging") ||
            title.contains("connected device") || text.contains("connected device") ||
            title.contains("charging") || text.contains("charging") ||
            title.contains("tethering") || text.contains("tethering") ||
            title.contains("system ui") || text.contains("system ui") ||
            title.contains("bluetooth") || text.contains("bluetooth")) {
            return true
        }
    }
    
    return false
}

fun isValidUserNotification(sbn: android.service.notification.StatusBarNotification): Boolean {
    return !isPersistentNotification(sbn)
}

@OptIn(ExperimentalFoundationApi::class)

@Composable
fun NotificationsView(
    state: LauncherState, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    pinnedApps: List<AppInfo>? = null,
    onAppClick: ((AppInfo) -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    emptyText: String = "No active notifications",
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.NotificationsNone,
    filter: (android.service.notification.StatusBarNotification) -> Boolean = { true }
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { firstItemFocusRequester.requestFocus() } catch(e: Exception) {}
    }
    
    val displayNotifications = remember(state.activeNotifications, filter) {
        state.activeNotifications.filter { 
            isValidUserNotification(it) && filter(it)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (pinnedApps != null && pinnedApps.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (state.showWallpaper) MaterialTheme.colorScheme.background.copy(alpha = 0.4f) else MaterialTheme.colorScheme.background)
                            .background(if (state.showWallpaper) Color.Transparent else Color.White.copy(alpha = 0.12f))
                            .padding(vertical = 6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(pinnedApps, key = { _, app -> app.packageName }) { index, app ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused by interactionSource.collectIsFocusedAsState()
                            Box(
                                modifier = Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                                    .symbianHighlight(isFocused)
                                    .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (onAppClick != null) {
                                            onAppClick(app)
                                        } else {
                                            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            intent?.let { context.startActivity(it) }
                                        }
                                    }
                                    .padding(4.dp)
                            ) {
                                Image(
                                    bitmap = app.icon.toCachedImageBitmap(),
                                    contentDescription = app.name,
                                    modifier = Modifier.size(40.dp * state.iconScale)
                                )
                            }
                        }
                        if (onSettingsClick != null) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSettingsClick()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Manage shortcuts",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Add",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    DashedDivider()
                }
            }
        } else if (onSettingsClick != null) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (state.showWallpaper) MaterialTheme.colorScheme.background.copy(alpha = 0.4f) else MaterialTheme.colorScheme.background)
                            .background(if (state.showWallpaper) Color.Transparent else Color.White.copy(alpha = 0.12f))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSettingsClick()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Add app shortcuts to this tab",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DashedDivider()
                }
            }
        }

        if (displayNotifications.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (pinnedApps.isNullOrEmpty()) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .padding(vertical = 40.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = emptyIcon,
                            contentDescription = null,
                            tint = Color.LightGray.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = emptyText,
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 15.sp,
                            color = Color.LightGray.copy(alpha = 0.8f)
                        )
                        if (onSettingsClick != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSettingsClick()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Configure Shortcuts & Settings", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        
        itemsIndexed(displayNotifications, key = { _, it -> it.key }) { index, sbn ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            
            val appInfo = remember(sbn.packageName, state.allApps) {
                state.allApps.find { it.packageName == sbn.packageName }
            }
            val appName = remember(sbn.packageName, appInfo) {
                appInfo?.name ?: try {
                    val ai = context.packageManager.getApplicationInfo(sbn.packageName, 0)
                    context.packageManager.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    sbn.packageName
                }
            }
            
            val appIconBitmap = remember(sbn.packageName, appInfo) {
                try {
                    if (appInfo != null) {
                        appInfo.icon.toCachedImageBitmap()
                    } else {
                        val drawable = context.packageManager.getApplicationIcon(sbn.packageName)
                        drawable.toCachedImageBitmap()
                    }
                } catch (e: Exception) {
                    null
                }
            }
            
            val title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE)
                ?: sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                ?: appName
            val text = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                ?: sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                    .symbianHighlight(isFocused)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            MessagingUtils.launchNotification(sbn, context)
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            SymbianNotificationService.instance?.clearNotification(sbn.key)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                // App Icon Box
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(38.dp)
                        .background(Color(0xFF1E293B).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .border(
                            BorderStroke(
                                1.dp,
                                if (isFocused) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
                            ),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = appName,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = if (isFocused) Color.Black else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = appName.uppercase(),
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFocused) Color(0xFF1E3A8A) else Color(0xFF93C5FD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        if (sbn.postTime > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val timeStr = remember(sbn.postTime) {
                                Formatters.get("HH:mm").format(java.util.Date(sbn.postTime))
                            }
                            Text(
                                text = timeStr,
                                fontFamily = state.currentFont.toFontFamily(),
                                fontSize = 11.sp,
                                color = if (isFocused) Color.DarkGray else Color.LightGray
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Text(
                        text = title, 
                        fontFamily = state.currentFont.toFontFamily(),
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp,
                        color = if (isFocused) Color.Black else Color.White,
                        style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f)),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = text, 
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 13.sp,
                            color = if (isFocused) Color.DarkGray else Color.LightGray,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            DashedDivider()
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

@Composable
fun T9SearchView(
    state: LauncherState, 
    viewModel: LauncherViewModel, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, 
    forceMode: T9SearchMode? = null,
    onContactSelected: ((ContactInfo) -> Unit)? = null
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    if (selectedApp != null) {
        ManageAppDialogHandler(selectedApp, state, viewModel) { selectedApp = null }
        return
    }

    val context = LocalContext.current
    val effectiveMode = forceMode ?: state.t9SearchMode
    val firstItemFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)) {
            Text(
                "Searching (${if (effectiveMode == T9SearchMode.CONTACTS) "Contacts" else "Apps"}): ${state.t9Query}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, offset = androidx.compose.ui.geometry.Offset(1.5f, 1.5f), blurRadius = 2f))
            )
        }
        
        // T9 digit strings are computed once per list, not on every key press.
        val contactKeys = remember(state.contacts) { state.contacts.map { it to it.name.toT9() } }
        val appKeys = remember(state.allApps) { state.allApps.map { it to it.name.toT9() } }
        val t9Results = remember(effectiveMode, contactKeys, appKeys, state.t9Query) {
            if (effectiveMode == T9SearchMode.CONTACTS) {
                contactKeys.filter { it.second.contains(state.t9Query) }.map { it.first }.map { 
                    T9Result(it.name, it.primaryPhoneNumber, contactInfo = it) 
                }
            } else {
                appKeys.filter { it.second.contains(state.t9Query) }.map { it.first }.map { 
                    T9Result(it.name, "", appInfo = it) 
                }
            }
        }
        
        androidx.compose.runtime.LaunchedEffect(t9Results) {
            if (t9Results.isNotEmpty()) {
                try {
                    firstItemFocusRequester.requestFocus()
                } catch (e: Exception) {}
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(t9Results) { index, result ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                            .symbianHighlight(isFocused)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (result.appInfo != null) {
                                        viewModel.launchApp(result.appInfo)
                                    } else if (result.contactInfo != null) {
                                        if (onContactSelected != null) {
                                            onContactSelected(result.contactInfo)
                                        }
                                    }
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (result.appInfo != null) {
                            Image(
                                bitmap = result.appInfo.icon.toCachedImageBitmap(),
                                contentDescription = result.name,
                                modifier = Modifier.size(36.dp * state.iconScale)
                            )
                        } else if (result.contactInfo?.photoUri != null) {
                            AsyncImage(
                                model = result.contactInfo.photoUri,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp * state.iconScale).clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = if (state.t9SearchMode == T9SearchMode.CONTACTS) Icons.Default.Person else Icons.Default.Apps,
                                contentDescription = null,
                                tint = if (isFocused) Color.Black else Color.White,
                                modifier = Modifier.size(36.dp * state.iconScale)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = result.name,
                                color = if (isFocused) Color.Black else Color.White,
                                fontFamily = state.currentFont.toFontFamily(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
                            )
                            if (result.detail.isNotEmpty()) {
                                Text(
                                    text = result.detail,
                                    color = if (isFocused) Color.DarkGray else Color.LightGray,
                                    fontFamily = state.currentFont.toFontFamily(),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    if (result.appInfo != null) {
                        val moreInteractionSource = remember { MutableInteractionSource() }
                        val isMoreFocused by moreInteractionSource.collectIsFocusedAsState()
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedApp = result.appInfo
                            },
                            interactionSource = moreInteractionSource,
                            modifier = Modifier.symbianHighlight(isMoreFocused)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.MoreVert,
                                contentDescription = "More Options",
                                tint = if (isMoreFocused) Color.Black else Color.White
                            )
                        }
                    }
                }
                DashedDivider()
            }
        }
    }
}


@Composable
fun HubView(
    state: LauncherState, 
    focusRequester: FocusRequester, 
    onNavigateToNotifications: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToEmails: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToAgenda: () -> Unit
) {
    val context = LocalContext.current
    
    val msgAppIcon = remember(state.useIconPackForHub, state.currentIconPack, state.allApps, state.messagingApps) {
        if (state.useIconPackForHub && state.currentIconPack != null) {
            state.allApps.find { state.messagingApps.contains(it.packageName) }?.icon
        } else null
    }
    
    val emailApps = state.emailApps
    
    val emailAppIcon = remember(state.useIconPackForHub, state.currentIconPack, state.allApps, state.emailApps) {
        if (state.useIconPackForHub && state.currentIconPack != null) {
            state.allApps.find { state.emailApps.contains(it.packageName) }?.icon
        } else null
    }

    val calendarAppIcon = remember(state.useIconPackForHub, state.currentIconPack, state.allApps) {
        if (state.useIconPackForHub && state.currentIconPack != null) {
            state.allApps.find { it.packageName.contains("calendar") }?.icon
        } else null
    }

    val notifAppIcon = remember(state.useIconPackForHub, state.currentIconPack, state.allApps) {
        if (state.useIconPackForHub && state.currentIconPack != null) {
            state.allApps.find { it.packageName.contains("settings") }?.icon
        } else null
    }
    
    val contactsAppIcon = remember(state.useIconPackForHub, state.currentIconPack, state.allApps) {
        if (state.useIconPackForHub && state.currentIconPack != null) {
            state.allApps.find { it.packageName.contains("contacts") }?.icon
        } else null
    }

    val appsAppIcon = remember(state.useIconPackForHub, state.currentIconPack, state.allApps) {
        if (state.useIconPackForHub && state.currentIconPack != null) {
            state.allApps.find { it.packageName.contains("launcher") || it.packageName.contains("home") }?.icon ?: notifAppIcon
        } else null
    }
    
    val messagingApps = state.messagingApps
    
    val displayNotifs = state.activeNotifications.filter { 
        isValidUserNotification(it) 
    }
    val msgNotifs = displayNotifs.filter { MessagingUtils.isMessageNotification(it, messagingApps) }
    val emailNotifs = displayNotifs.filter { MessagingUtils.isEmailNotification(it, emailApps) }
    val generalNotifs = displayNotifs.filter { !MessagingUtils.isMessageNotification(it, messagingApps) && !MessagingUtils.isEmailNotification(it, emailApps) }
    
    val currentTimeMs = rememberTickingTime(60_000L)

    val firstEvent = state.todayEvents.firstOrNull()
    val now = currentTimeMs
    val timeFmt = if (state.is24HourFormat) "HH:mm" else "h:mm a"
    val timeFormat = Formatters.get(timeFmt)
    
    val eventText = if (firstEvent == null) {
        "No more entries today"
    } else {
        if (firstEvent.isOngoing(now)) {
            val endStr = timeFormat.format(java.util.Date(firstEvent.getEffectiveEndTime()))
            val remaining = firstEvent.getRemainingTimeFormatted(now)
            "now-ends at $endStr (in $remaining) — ${firstEvent.title}"
        } else if (android.text.format.DateUtils.isToday(firstEvent.dtStart)) {
            val timeStr = timeFormat.format(java.util.Date(firstEvent.dtStart))
            "$timeStr — ${firstEvent.title}"
        } else {
            val timeStr = timeFormat.format(java.util.Date(firstEvent.dtStart))
            
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = now
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val tomorrowStart = calendar.clone() as java.util.Calendar
            tomorrowStart.set(java.util.Calendar.HOUR_OF_DAY, 0)
            tomorrowStart.set(java.util.Calendar.MINUTE, 0)
            tomorrowStart.set(java.util.Calendar.SECOND, 0)
            
            val tomorrowEnd = calendar.clone() as java.util.Calendar
            tomorrowEnd.set(java.util.Calendar.HOUR_OF_DAY, 23)
            tomorrowEnd.set(java.util.Calendar.MINUTE, 59)
            tomorrowEnd.set(java.util.Calendar.SECOND, 59)
            
            val dateStr = if (firstEvent.dtStart in tomorrowStart.timeInMillis..tomorrowEnd.timeInMillis) {
                "Tomorrow"
            } else {
                Formatters.get("MMM d").format(java.util.Date(firstEvent.dtStart))
            }
            "No more entries todaynNext: $dateStr, $timeStr — ${firstEvent.title}"
        }
    }
    
    val isNotificationAccessEnabled = remember(context) { 
        try {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) 
        } catch (e: Exception) {
            false
        }
    }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (!isNotificationAccessEnabled) {
            HubRow(
                icon = Icons.Default.Warning,
                text = "Enable Notification Access",
                font = state.currentFont.toFontFamily(),
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            )
            DashedDivider()
        }

        if (state.showMessagesSection) {
            val msgCount = msgNotifs.size
            val msgText = if (msgCount > 0) {
                if (msgCount == 1) "1 unread message" else "$msgCount unread messages"
            } else {
                "No new messages"
            }
            HubRow(
                text = msgText,
                font = state.currentFont.toFontFamily(),
                onClick = { 
                    onNavigateToMessages()
                },
                modifier = Modifier.focusRequester(focusRequester),
                iconComponent = { 
                    if (msgAppIcon != null) {
                        Image(bitmap = msgAppIcon.toCachedImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp * state.iconScale).clip(RoundedCornerShape(4.dp)))
                    } else {
                        SkeuomorphicMessageIcon(modifier = Modifier.size(24.dp * state.iconScale)) 
                    }
                }
            )
            DashedDivider()
        }
        
        HubRow(
            text = if (generalNotifs.isNotEmpty()) "${generalNotifs.size} unread notifications" else "No unread notifications",
            font = state.currentFont.toFontFamily(),
            onClick = { if (generalNotifs.isNotEmpty()) onNavigateToNotifications() },
            iconComponent = { 
                if (notifAppIcon != null) {
                    Image(bitmap = notifAppIcon.toCachedImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp * state.iconScale).clip(RoundedCornerShape(4.dp)))
                } else {
                    SkeuomorphicNotificationsIcon(modifier = Modifier.size(24.dp * state.iconScale)) 
                }
            },
            modifier = if (!state.showMessagesSection) Modifier.focusRequester(focusRequester) else Modifier
        )
        DashedDivider()
        
        if (state.showCalendarSection) {
            HubRow(
                text = eventText,
                font = state.currentFont.toFontFamily(),
                onClick = {
                    onNavigateToAgenda()
                },
                iconComponent = { 
                    if (calendarAppIcon != null) {
                        Image(bitmap = calendarAppIcon.toCachedImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp * state.iconScale).clip(RoundedCornerShape(4.dp)))
                    } else {
                        SkeuomorphicCalendarIcon(modifier = Modifier.size(24.dp * state.iconScale)) 
                    }
                }
            )
            DashedDivider()
        }
        
        if (state.showEmailSection) {
            val emailCount = emailNotifs.size
            val emailText = if (emailCount > 0) {
                if (emailCount == 1) "1 unread email" else "$emailCount unread emails"
            } else {
                "Email"
            }
            HubRow(
                text = emailText,
                font = state.currentFont.toFontFamily(),
                onClick = onNavigateToEmails,
                iconComponent = { 
                    if (emailAppIcon != null) {
                        Image(bitmap = emailAppIcon.toCachedImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp * state.iconScale).clip(RoundedCornerShape(4.dp)))
                    } else {
                        SkeuomorphicEmailIcon(modifier = Modifier.size(24.dp * state.iconScale)) 
                    }
                }
            )
            DashedDivider()
        }

        if (state.t9SearchMode == T9SearchMode.CONTACTS) {
            HubRow(
                text = "Apps",
                font = state.currentFont.toFontFamily(),
                onClick = onNavigateToApps,
                iconComponent = { 
                    if (appsAppIcon != null) {
                        Image(bitmap = appsAppIcon.toCachedImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp * state.iconScale).clip(RoundedCornerShape(4.dp)))
                    } else {
                        SkeuomorphicAppsIcon(modifier = Modifier.size(24.dp * state.iconScale)) 
                    }
                }
            )
        } else {
            HubRow(
                text = "Contacts",
                font = state.currentFont.toFontFamily(),
                onClick = onNavigateToContacts,
                iconComponent = { 
                    if (contactsAppIcon != null) {
                        Image(bitmap = contactsAppIcon.toCachedImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp * state.iconScale).clip(RoundedCornerShape(4.dp)))
                    } else {
                        SkeuomorphicContactsIcon(modifier = Modifier.size(24.dp * state.iconScale)) 
                    }
                }
            )
        }
        DashedDivider()
    }
}


@Composable
fun HubRow(icon: ImageVector, text: String, font: FontFamily, onClick: () -> Unit, modifier: Modifier = Modifier) {
    HubRow(
        text = text,
        font = font,
        onClick = onClick,
        modifier = modifier,
        iconComponent = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(24.dp))
            }
        }
    )
}


@Composable
fun HubRow(text: String, font: FontFamily, onClick: () -> Unit, modifier: Modifier = Modifier, iconComponent: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .symbianHighlight(isFocused)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        iconComponent()
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White,
            fontFamily = font,
            fontSize = 16.sp,
            maxLines = 2,
            lineHeight = 20.sp,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f)),
            modifier = Modifier.weight(1f)
        )
    }
}


@Composable
fun DashedDivider() {
    val dividerColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = dividerColor,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
    }
}


@Composable
fun SymbianTopBar(state: LauncherState, batteryInfo: BatteryInfo, signalLevel: Int, onWeatherClick: () -> Unit, onClockClick: () -> Unit = {}) {
    if (!state.showLauncherStatusBar) return
    
    // Tick each second only when seconds are displayed; otherwise once a minute.
    val now = rememberTickingTime(if (state.showTime && state.showSeconds) 1_000L else 60_000L)
    val timePattern = remember(state.is24HourFormat, state.showSeconds) {
        val secondsPattern = if (state.showSeconds) ":ss" else ""
        if (state.is24HourFormat) "HH:mm$secondsPattern" else "hh:mm$secondsPattern a"
    }
    val datePattern = remember(state.dateFormat, state.showWeekday, state.weekdayFormat) {
        var dFmt = state.dateFormat.pattern
        if (dFmt.isEmpty()) {
            // System Default (short)
            val fmt = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy")
            dFmt = fmt.replace(Regex("[^dMy]"), " ").trim().replace(Regex("s+"), "/") // Rough approximation
            if (dFmt.isEmpty()) dFmt = "dd/MM/yyyy"
        }
        if (state.showWeekday) {
            val wFmt = if (state.weekdayFormat == WeekdayFormat.FULL) "EEEE" else "EEE"
            dFmt = "$wFmt $dFmt"
        }
        dFmt.trim()
    }
    val currentTime = if (state.showTime) Formatters.format(timePattern, now) else ""
    val currentDate = if (state.showDate) Formatters.format(datePattern, now) else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left: Signal
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
            Canvas(modifier = Modifier.width(10.dp).height(36.dp)) {
                val maxBars = 7
                val barHeight = 2.5f.dp.toPx()
                val gap = 2.5f.dp.toPx()
                val startY = size.height - barHeight
                val activeBars = (signalLevel / 4f * maxBars).toInt().coerceIn(0, maxBars)
                for (i in 0 until maxBars) {
                    val y = startY - i * (barHeight + gap)
                    drawRect(
                        color = if (i < activeBars) Color.White else Color.White.copy(alpha = 0.3f),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, barHeight)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Canvas(modifier = Modifier.width(14.dp).height(18.dp)) {
                val color = Color.White
                drawLine(color, Offset(size.width/2, size.height), Offset(size.width/2, 4.dp.toPx()), strokeWidth = 2f)
                drawCircle(color, 2.dp.toPx(), Offset(size.width/2, 2.dp.toPx()))
                drawArc(color, -135f, 90f, false, topLeft = Offset(size.width/2 - 6.dp.toPx(), -2.dp.toPx()), size = Size(12.dp.toPx(), 12.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
        }
        
        // Center: Date & Time or Media Controls
        val context = LocalContext.current
        var mediaController by remember { mutableStateOf<android.media.session.MediaController?>(null) }
        var playbackState by remember { mutableStateOf<android.media.session.PlaybackState?>(null) }
        
        DisposableEffect(context) {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
            val componentName = ComponentName(context, SymbianNotificationService::class.java)
            
            val updateController = {
                try {
                    val controllers = mediaSessionManager.getActiveSessions(componentName)
                    val activeController = controllers.firstOrNull { 
                        it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING || 
                        it.playbackState?.state == android.media.session.PlaybackState.STATE_PAUSED
                    } ?: controllers.firstOrNull()
                    mediaController = activeController
                    playbackState = activeController?.playbackState
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            updateController()
            
            val listener = android.media.session.MediaSessionManager.OnActiveSessionsChangedListener { updateController() }
            try {
                mediaSessionManager.addOnActiveSessionsChangedListener(listener, componentName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            onDispose {
                try {
                    mediaSessionManager.removeOnActiveSessionsChangedListener(listener)
                } catch(e: Exception) {}
            }
        }
        
        DisposableEffect(mediaController) {
            val callback = object : android.media.session.MediaController.Callback() {
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    playbackState = state
                }
                override fun onSessionDestroyed() {
                    playbackState = null
                    mediaController = null
                }
            }
            mediaController?.registerCallback(callback)
            onDispose {
                mediaController?.unregisterCallback(callback)
            }
        }
        
        val isPlaying = playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        val isPaused = playbackState?.state == android.media.session.PlaybackState.STATE_PAUSED
        val hasMedia = isPlaying || isPaused
        
        val focusClock = remember { androidx.compose.ui.focus.FocusRequester() }
        val focusWeather = remember { androidx.compose.ui.focus.FocusRequester() }
        val focusPrev = remember { androidx.compose.ui.focus.FocusRequester() }
        val focusPlay = remember { androidx.compose.ui.focus.FocusRequester() }
        val focusNext = remember { androidx.compose.ui.focus.FocusRequester() }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            val intSrcClock = remember { MutableInteractionSource() }
            val isFocClock by intSrcClock.collectIsFocusedAsState()
            
            val intSrcWeather = remember { MutableInteractionSource() }
            val isFocWeather by intSrcWeather.collectIsFocusedAsState()
            
            // CLOCK LAYER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Clock & Date
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .focusRequester(focusClock)
                        .symbianHighlight(isFocClock)
                        .clickable(interactionSource = intSrcClock, indication = LocalIndication.current) {
                            onClockClick()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentTime, 
                        color = Color.White, 
                        fontSize = 20.sp, 
                        fontFamily = state.currentFont.toFontFamily(),
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 2f))
                    )
                    if (state.showDate) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentDate, 
                            color = Color.White, 
                            fontSize = 12.sp,
                            fontFamily = state.currentFont.toFontFamily(),
                            style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 2f))
                        )
                    }
                }
                
                // Right: Weather
                if (state.showWeather && state.weather != null) {
                    val parts = state.weather.split(" ", limit = 2)
                    val temp = parts.getOrNull(0) ?: ""
                    val desc = parts.getOrNull(1) ?: ""
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .focusRequester(focusWeather)
                            .symbianHighlight(isFocWeather)
                            .clickable(interactionSource = intSrcWeather, indication = LocalIndication.current) {
                                onWeatherClick()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val weatherSymbol = state.weatherCurrentSymbol.ifEmpty { desc }
                            RealisticWeatherIcon(
                                symbolCode = weatherSymbol,
                                size = 22.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = temp, 
                                color = Color.White, 
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = state.currentFont.toFontFamily(),
                                style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 2f))
                            )
                        }
                        if (desc.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc, 
                                color = Color.White, 
                                fontSize = 12.sp,
                                fontFamily = state.currentFont.toFontFamily(),
                                style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 2f))
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp)) // keeps SpaceBetween effective
                }
            }
            
            // DYNAMIC LAYER (Call or Media)
            val activeCall = rememberActiveCallState(context, state.contacts)
            val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager }
            val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
            
            if (activeCall.isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Image & Name
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (activeCall.photoUri != null) {
                            coil.compose.AsyncImage(
                                model = activeCall.photoUri,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp * state.iconScale).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Gray),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.size(36.dp * state.iconScale).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) {
                                Icon(androidx.compose.material.icons.Icons.Default.Person, contentDescription = null, tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = activeCall.contactName ?: "Unknown", 
                                color = Color.White, 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 14.sp,
                                fontFamily = state.currentFont.toFontFamily(),
                                style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 2f)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val callNow = rememberTickingTime(1_000L)
                            val elapsedSeconds = ((callNow - activeCall.startTime) / 1000).coerceAtLeast(0)
                            val durationStr = String.format(java.util.Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
                            Text(
                                text = durationStr, 
                                color = Color.White, 
                                fontSize = 12.sp,
                                fontFamily = state.currentFont.toFontFamily(),
                                style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 2f))
                            )
                        }
                    }
                    
                    // Right: Speaker & Hangup
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val intSrcSpk = remember { MutableInteractionSource() }
                        val isFocSpk by intSrcSpk.collectIsFocusedAsState()
                        Icon(
                            imageVector = if (activeCall.isSpeakerOn) androidx.compose.material.icons.Icons.Default.VolumeUp else androidx.compose.material.icons.Icons.Default.VolumeDown,
                            contentDescription = "Speaker",
                            tint = if (isFocSpk) Color.Black else Color.White,
                            modifier = Modifier
                                .size(32.dp)
                                .symbianHighlight(isFocSpk)
                                .clickable(interactionSource = intSrcSpk, indication = LocalIndication.current) { 
                                    SpeakerUtils.toggleSpeaker(context)
                                }
                                .padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val intSrcHang = remember { MutableInteractionSource() }
                        val isFocHang by intSrcHang.collectIsFocusedAsState()
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.CallEnd,
                            contentDescription = "Hangup",
                            tint = Color.Red,
                            modifier = Modifier
                                .size(36.dp)
                                .symbianHighlight(isFocHang)
                                .clickable(interactionSource = intSrcHang, indication = LocalIndication.current) { 
                                    try {
                                        // 1. Try Notification Action
                                        if (activeCall.hangUpIntent != null) {
                                            activeCall.hangUpIntent.send()
                                        }
                                        
                                        // 2. Try Media Headset Hook (Universal hangup for VoIP apps)
                                        val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                                        audioMan.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_HEADSETHOOK))
                                        audioMan.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_HEADSETHOOK))
                                        
                                        // 3. Try Telecom Manager
                                        if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
@android.annotation.SuppressLint("MissingPermission", "NewApi")
                                            telecomManager.endCall()
                                        }
                                    } catch (e: Exception) {}
                                }
                                .padding(4.dp)
                        )
                    }
                }
            } else if (hasMedia) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val intSrcPrev = remember { MutableInteractionSource() }
                        val intSrcPlay = remember { MutableInteractionSource() }
                        val intSrcNext = remember { MutableInteractionSource() }
                        val isFocPrev by intSrcPrev.collectIsFocusedAsState()
                        val isFocPlay by intSrcPlay.collectIsFocusedAsState()
                        val isFocNext by intSrcNext.collectIsFocusedAsState()
                        
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = if (isFocPrev) Color.Black else Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .focusRequester(focusPrev)
                                .symbianHighlight(isFocPrev)
                                .clickable(interactionSource = intSrcPrev, indication = LocalIndication.current) { mediaController?.transportControls?.skipToPrevious() }
                                .padding(4.dp)
                        )
                        Icon(
                            imageVector = if (isPlaying) androidx.compose.material.icons.Icons.Default.Pause else androidx.compose.material.icons.Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = if (isFocPlay) Color.Black else Color.White,
                            modifier = Modifier
                                .size(32.dp)
                                .focusRequester(focusPlay)
                                .symbianHighlight(isFocPlay)
                                .clickable(interactionSource = intSrcPlay, indication = LocalIndication.current) {
                                     if (isPlaying) mediaController?.transportControls?.pause()
                                     else mediaController?.transportControls?.play()
                                 }
                                .padding(4.dp)
                        )
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = if (isFocNext) Color.Black else Color.White,
                            modifier = Modifier
                                .size(28.dp)
                                .focusRequester(focusNext)
                                .symbianHighlight(isFocNext)
                                .clickable(interactionSource = intSrcNext, indication = LocalIndication.current) { mediaController?.transportControls?.skipToNext() }
                                .padding(4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    val title = mediaController?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
                    val artist = mediaController?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    val mediaText = if (title.isNotEmpty() && artist.isNotEmpty()) "$title - $artist" else if (title.isNotEmpty()) title else artist
                    
                    if (mediaText.isNotEmpty()) {
                        Text(
                            text = mediaText, 
                            color = Color.White, 
                            fontSize = 10.sp,
                            fontFamily = state.currentFont.toFontFamily(),
                            maxLines = 1,
                            style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 2f)),
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }
        }
        
        // Right: Battery
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
            Canvas(modifier = Modifier.width(10.dp).height(36.dp)) {
                val maxBars = 7
                val barHeight = 2.5f.dp.toPx()
                val gap = 2.5f.dp.toPx()
                val startY = size.height - barHeight
                val activeBars = (batteryInfo.percentage * maxBars).toInt().coerceIn(0, maxBars)
                for (i in 0 until maxBars) {
                    val y = startY - i * (barHeight + gap)
                    val isActive = if (batteryInfo.percentage <= 0.1f && !batteryInfo.isCharging) (i == 0) else i < activeBars
                    val color = if (isActive) {
                        if (batteryInfo.percentage <= 0.15f && !batteryInfo.isCharging) Color.Red else Color.White
                    } else {
                        Color.White.copy(alpha = 0.3f)
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(0f, y),
                        size = Size(size.width, barHeight)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Canvas(modifier = Modifier.width(14.dp).height(18.dp)) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2f)
                drawRect(Color.White, topLeft = Offset(0f, 2.dp.toPx()), size = Size(size.width, size.height - 2.dp.toPx()), style = stroke)
                drawRect(Color.White, topLeft = Offset(size.width / 4, 0f), size = Size(size.width / 2, 2.dp.toPx()))
                if (batteryInfo.isCharging) {
                    val boltPath = Path().apply {
                        moveTo(size.width / 2, 4.dp.toPx())
                        lineTo(2.dp.toPx(), 10.dp.toPx())
                        lineTo(size.width / 2 + 1.dp.toPx(), 10.dp.toPx())
                        lineTo(size.width / 2 - 1.dp.toPx(), size.height - 2.dp.toPx())
                        lineTo(size.width - 2.dp.toPx(), 6.dp.toPx())
                        lineTo(size.width / 2 - 1.dp.toPx(), 6.dp.toPx())
                        close()
                    }
                    drawPath(boltPath, Color.White)
                }
            }
        }
    }
}


@Composable
fun SymbianBottomBar(
    leftText: String, 
    rightText: String, 
    onLeftClick: () -> Unit, 
    onRightClick: () -> Unit,
    leftModifier: Modifier = Modifier,
    rightModifier: Modifier = Modifier,
    isExtremeBatterySaver: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isExtremeBatterySaver) Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BottomBarButton(leftText, onLeftClick, leftModifier)
        BottomBarButton(rightText, onRightClick, rightModifier)
    }
}


@Composable
fun BottomBarButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (text.isEmpty()) {
        Spacer(modifier = Modifier.width(1.dp))
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Text(
        text = text, 
        color = if (isFocused) Color.Black else Color.White, 
        fontWeight = FontWeight.Bold, 
        fontSize = 16.sp,
        style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 2f)),
        modifier = modifier
            .symbianHighlight(isFocused)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}





@Composable
fun UsageStatsScreen(
    state: LauncherState, 
    viewModel: LauncherViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onBack: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val usageStatsManager = remember { context.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager }
    val packageManager = context.packageManager
    
    var hasPermission by remember { 
        mutableStateOf(
            (context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager)
            .checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
        ) 
    }
    
    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Usage Access Required",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = state.currentFont.toFontFamily()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "To view your total daily and weekly app usage, please grant the Usage Access permission in system settings.",
                color = Color.White.copy(alpha = 0.9f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontFamily = state.currentFont.toFontFamily()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (e: Exception) {
                        try {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
                        } catch (e2: Exception) {}
                    }
                }
            ) {
                Text("Grant Permission", fontFamily = state.currentFont.toFontFamily())
            }
        }
        return
    }

    val tabs = listOf("Daily", "Weekly", "Monthly", "About")
    val selectedTab = state.usageTab.coerceIn(0, 3)
    val isAboutTab = selectedTab == 3
    
    val timeRange = when (selectedTab) {
        1 -> UsageTimeRange.WEEKLY
        2 -> UsageTimeRange.MONTHLY
        else -> UsageTimeRange.DAILY
    }
    
    data class AppUsageEntry(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable?,
        val screenOnTimeMs: Long,
        val screenOffMediaTimeMs: Long,
        val callTimeMs: Long,
        val totalTimeMs: Long
    )

    var usageEntries by remember { mutableStateOf<List<AppUsageEntry>>(emptyList()) }
    var totalScreenOnMs by remember { mutableStateOf(0L) }
    var totalScreenOffMediaMs by remember { mutableStateOf(0L) }
    var totalCallMs by remember { mutableStateOf(0L) }
    
    fun formatUsageDuration(timeMs: Long): String {
        val totalMinutes = timeMs / 1000 / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            timeMs > 0 -> "< 1m"
            else -> "0m"
        }
    }
    
    LaunchedEffect(timeRange, selectedTab) {
        if (isAboutTab) return@LaunchedEffect
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        if (timeRange == UsageTimeRange.WEEKLY) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -6)
        } else if (timeRange == UsageTimeRange.MONTHLY) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -29)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        val appScreenOnMap = mutableMapOf<String, Long>()
        val appScreenOffMediaMap = mutableMapOf<String, Long>()
        val appCallMap = mutableMapOf<String, Long>()
        
        // Excluded system / internal packages
        val excludedPackages = setOf(
            context.packageName,
            "com.android.systemui",
            "android",
            "com.google.android.inputmethod.latin",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher"
        )
        
        // Helper to check if an app is a calling / VoIP communication app
        fun isCallPackage(pkg: String): Boolean {
            val pkgLower = pkg.lowercase()
            val callKeywords = listOf(
                "signal", "whatsapp", "snapchat", "telegram", "discord", "skype",
                "teams", "zoom", "meet", "duo", "viber", "dialer", "telecom",
                "phone", "incallui", "messenger", "line", "kakao"
            )
            return callKeywords.any { pkgLower.contains(it) }
        }

        // Helper to check if an app is a genuine audio/media app
        fun isMediaPackage(pkg: String): Boolean {
            // If it's a known call app, don't categorize as music/media
            if (isCallPackage(pkg)) return false
            return try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO) {
                        return true
                    }
                }
                val pkgLower = pkg.lowercase()
                val knownMediaKeywords = listOf("music", "audio", "spotify", "podcast", "radio", "deezer", "tidal", "soundcloud", "pandora", "audible", "youtube.music", "player", "walkman", "qobuz", "apple.android.music")
                knownMediaKeywords.any { pkgLower.contains(it) }
            } catch (e: Exception) {
                false
            }
        }
        
        // Track intervals where screen was physically off
        val screenOffIntervals = mutableListOf<Pair<Long, Long>>()
        
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = android.app.usage.UsageEvents.Event()
            
            val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            var isScreenInteractive = powerManager?.isInteractive ?: true
            var screenStateChangeTime = startTime
            var currentForegroundApp: String? = null
            var appForegroundStartTime = startTime
            
            // Map of currently running foreground services: pkg -> startTime
            val activeServices = mutableMapOf<String, Long>()
            // Completed foreground service intervals per package: pkg -> list of (start, end)
            val serviceIntervals = mutableMapOf<String, MutableList<Pair<Long, Long>>>()
            
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                val time = event.timeStamp.coerceIn(startTime, endTime)
                
                when (event.eventType) {
                    // SCREEN_INTERACTIVE (15)
                    15 -> {
                        if (!isScreenInteractive) {
                            if (time > screenStateChangeTime) {
                                screenOffIntervals.add(Pair(screenStateChangeTime, time))
                            }
                        }
                        isScreenInteractive = true
                        screenStateChangeTime = time
                    }
                    // SCREEN_NON_INTERACTIVE (16)
                    16 -> {
                        if (isScreenInteractive) {
                            // Any app that was foregrounded accumulated screen-on time while screen was on
                            if (currentForegroundApp != null && !excludedPackages.contains(currentForegroundApp)) {
                                val duration = (time - appForegroundStartTime).coerceAtLeast(0L)
                                if (duration > 0) {
                                    appScreenOnMap[currentForegroundApp!!] = (appScreenOnMap[currentForegroundApp!!] ?: 0L) + duration
                                }
                                appForegroundStartTime = time
                            }
                        }
                        isScreenInteractive = false
                        screenStateChangeTime = time
                    }
                    // ACTIVITY_RESUMED (1) / MOVE_TO_FOREGROUND (1)
                    1 -> {
                        if (currentForegroundApp != null && currentForegroundApp != pkg) {
                            if (!excludedPackages.contains(currentForegroundApp)) {
                                val duration = (time - appForegroundStartTime).coerceAtLeast(0L)
                                if (duration > 0) {
                                    appScreenOnMap[currentForegroundApp!!] = (appScreenOnMap[currentForegroundApp!!] ?: 0L) + duration
                                }
                            }
                        }
                        currentForegroundApp = pkg
                        appForegroundStartTime = time
                    }
                    // ACTIVITY_PAUSED (2) / ACTIVITY_STOPPED (23) / MOVE_TO_BACKGROUND (2)
                    2, 23 -> {
                        if (currentForegroundApp == pkg) {
                            if (!excludedPackages.contains(pkg)) {
                                val duration = (time - appForegroundStartTime).coerceAtLeast(0L)
                                if (duration > 0) {
                                    appScreenOnMap[pkg] = (appScreenOnMap[pkg] ?: 0L) + duration
                                }
                            }
                            currentForegroundApp = null
                            appForegroundStartTime = time
                        }
                    }
                    // FOREGROUND_SERVICE_START (19)
                    19 -> {
                        activeServices[pkg] = time
                    }
                    // FOREGROUND_SERVICE_STOP (20)
                    20 -> {
                        val sStart = activeServices.remove(pkg)
                        if (sStart != null && time > sStart) {
                            serviceIntervals.getOrPut(pkg) { mutableListOf() }.add(Pair(sStart, time))
                        }
                    }
                }
            }
            
            // Tail end processing up to endTime
            if (!isScreenInteractive && endTime > screenStateChangeTime) {
                screenOffIntervals.add(Pair(screenStateChangeTime, endTime))
            }
            if (currentForegroundApp != null && !excludedPackages.contains(currentForegroundApp)) {
                val duration = (endTime - appForegroundStartTime).coerceAtLeast(0L)
                if (duration > 0) {
                    appScreenOnMap[currentForegroundApp!!] = (appScreenOnMap[currentForegroundApp!!] ?: 0L) + duration
                }
            }
            for ((pkg, sStart) in activeServices) {
                if (endTime > sStart) {
                    serviceIntervals.getOrPut(pkg) { mutableListOf() }.add(Pair(sStart, endTime))
                }
            }
            
            // Calculate screen-off media time vs. active call time
            for ((pkg, intervals) in serviceIntervals) {
                if (!excludedPackages.contains(pkg)) {
                    var totalOffOverlap = 0L
                    var totalServiceDuration = 0L
                    for ((sStart, sEnd) in intervals) {
                        totalServiceDuration += (sEnd - sStart)
                        for ((offStart, offEnd) in screenOffIntervals) {
                            val overlapStart = maxOf(sStart, offStart)
                            val overlapEnd = minOf(sEnd, offEnd)
                            if (overlapEnd > overlapStart) {
                                totalOffOverlap += (overlapEnd - overlapStart)
                            }
                        }
                    }
                    
                    if (isCallPackage(pkg)) {
                        // For calling apps, foreground services represent active VoIP calls (e.g. Phone, Signal, WhatsApp, Discord call)
                        if (totalServiceDuration > 0) {
                            appCallMap[pkg] = totalServiceDuration
                        }
                    } else if (isMediaPackage(pkg)) {
                        if (totalOffOverlap > 0) {
                            appScreenOffMediaMap[pkg] = totalOffOverlap
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        
        // Fetch standard aggregated stats
        val statsMap = try {
            usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        } catch (e: Exception) {
            emptyMap()
        }
        
        // Consolidate package list
        val allRelevantPackages = (statsMap.keys + appScreenOnMap.keys + appScreenOffMediaMap.keys + appCallMap.keys)
            .filter { !excludedPackages.contains(it) }
            .toSet()
            
        val entries = allRelevantPackages.mapNotNull { pkg ->
            val eventScreenOn = appScreenOnMap[pkg] ?: 0L
            val aggregatedForeground = statsMap[pkg]?.totalTimeInForeground ?: 0L
            
            // Screen-on time: When the app is opened or active while user is on device
            val screenOnTime = if (eventScreenOn > 0) eventScreenOn else aggregatedForeground
            
            // Screen-off media for verified media players only
            val screenOffMediaTime = if (isMediaPackage(pkg)) (appScreenOffMediaMap[pkg] ?: 0L) else 0L
            
            // Active VoIP / Phone calls
            val callTime = if (isCallPackage(pkg)) (appCallMap[pkg] ?: 0L) else 0L
            
            val totalTime = screenOnTime + screenOffMediaTime + callTime
            if (totalTime <= 0) return@mapNotNull null
            
            try {
                val appInfo = state.allApps.find { it.packageName == pkg }
                val name = appInfo?.name ?: try {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    packageManager.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    pkg.substringAfterLast('.')
                }
                val icon = appInfo?.icon ?: try {
                    packageManager.getApplicationIcon(pkg)
                } catch (e: Exception) {
                    null
                }
                AppUsageEntry(
                    packageName = pkg,
                    appName = name,
                    icon = icon,
                    screenOnTimeMs = screenOnTime,
                    screenOffMediaTimeMs = screenOffMediaTime,
                    callTimeMs = callTime,
                    totalTimeMs = totalTime
                )
            } catch (e: Exception) {
                null
            }
        }
        
        val internalAppNames = listOf("Weather", "Wallet", "Clock", "Calculator", "Calendar", "Contacts", "Usage Stats", "Settings", "Launcher")
        val daysBack = when(timeRange) {
            UsageTimeRange.WEEKLY -> 6
            UsageTimeRange.MONTHLY -> 29
            else -> 0
        }
        val internalEntries = internalAppNames.mapNotNull { internalAppName ->
            val totalTime = viewModel.getInternalAppUsage(internalAppName, daysBack)
            if (totalTime > 0) {
                AppUsageEntry(
                    packageName = "com.example.internal.$internalAppName",
                    appName = internalAppName,
                    icon = null,
                    screenOnTimeMs = totalTime,
                    screenOffMediaTimeMs = 0L,
                    callTimeMs = 0L,
                    totalTimeMs = totalTime
                )
            } else null
        }
        
        val allEntries = (entries + internalEntries).sortedByDescending { it.screenOnTimeMs + it.callTimeMs }

        totalScreenOnMs = allEntries.sumOf { it.screenOnTimeMs }
        totalScreenOffMediaMs = allEntries.sumOf { it.screenOffMediaTimeMs }
        totalCallMs = allEntries.sumOf { it.callTimeMs }
        usageEntries = allEntries
    }
    
    val listState = rememberLazyListState()
    
    LaunchedEffect(selectedTab) {
        try {
            listState.animateScrollToItem(0)
        } catch (e: Exception) {}
    }
    
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Device Usage",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = state.currentFont.toFontFamily(),
                style = TextStyle(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Tabs matching Clock & Weather style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tabName ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val isSelected = state.usageTab == index
                    
                    Text(
                        text = tabName,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = state.currentFont.toFontFamily(),
                        modifier = Modifier
                            .symbianHighlight(isFocused)
                            .focusable(interactionSource = interactionSource)
                            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setUsageTab(index)
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
            }
        }
        
        if (isAboutTab) {
            // 1. Overview Hero Card
            item {
                val heroSource = remember { MutableInteractionSource() }
                val isHeroFocused by heroSource.collectIsFocusedAsState()
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .focusable(interactionSource = heroSource)
                        .background(
                            Brush.verticalGradient(
                                colors = if (isHeroFocused) listOf(
                                    Color(0xFF37474F).copy(alpha = 0.98f),
                                    Color(0xFF1C2830).copy(alpha = 0.99f)
                                ) else listOf(
                                    Color(0xFF263238).copy(alpha = 0.94f),
                                    Color(0xFF10171B).copy(alpha = 0.98f)
                                )
                            )
                        )
                        .border(
                            width = if (isHeroFocused) 2.dp else 1.dp,
                            brush = if (isHeroFocused) {
                                Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color(0xFF0091EA)))
                            } else {
                                Brush.verticalGradient(listOf(Color(0xFF546E7A), Color(0xFF1E282D)))
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF1E282D), shape = RoundedCornerShape(10.dp))
                                .border(1.dp, if (isHeroFocused) Color(0xFF00E5FF) else Color(0xFF455A64), shape = RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            SkeuomorphicUsageIcon(modifier = Modifier.size(36.dp * state.iconScale))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "HOW USAGE WORKS",
                                fontFamily = state.currentFont.toFontFamily(),
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Device Intelligence",
                                fontFamily = state.currentFont.toFontFamily(),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Real-time, accurate breakdown of your digital balance.",
                                fontFamily = state.currentFont.toFontFamily(),
                                color = Color(0xFFB0BEC5),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            
            // 2. Section: Midnight-to-Midnight Sync
            item {
                val midnightSource = remember { MutableInteractionSource() }
                val isMidnightFocused by midnightSource.collectIsFocusedAsState()
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .focusable(interactionSource = midnightSource)
                        .background(if (isMidnightFocused) Color(0xFF1E2C33) else Color(0xFF141D22))
                        .border(
                            width = if (isMidnightFocused) 2.dp else 1.dp,
                            color = if (isMidnightFocused) Color(0xFF00E5FF) else Color(0xFF263238),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🕒",
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Midnight Reset & Google Parity",
                            color = Color(0xFF80D8FF),
                            fontWeight = FontWeight.Bold,
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 14.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Daily usage resets at 12:00:00 AM midnight every single day. This anchors all today stats strictly from midnight to right now, guaranteeing parity with Google Digital Wellbeing and standard Android system stats.",
                        color = Color(0xFFCFD8DC),
                        fontFamily = state.currentFont.toFontFamily(),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            
            // 3. Header: Metrics Explained
            item {
                Text(
                    text = "Metrics & Icons Explained",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = state.currentFont.toFontFamily(),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            
            // 4. Screen-On Time Card
            item {
                val screenOnSource = remember { MutableInteractionSource() }
                val isScreenOnFocused by screenOnSource.collectIsFocusedAsState()
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .focusable(interactionSource = screenOnSource)
                        .background(if (isScreenOnFocused) Color(0xFF1E2C33) else Color(0xFF141D22))
                        .border(
                            width = if (isScreenOnFocused) 2.dp else 1.dp,
                            color = if (isScreenOnFocused) Color(0xFF00E5FF) else Color(0xFF263238),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF00E5FF), shape = RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Screen-On Time (Active Usage)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 13.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tracks active interactive engagement while your phone's display is illuminated and focused on an app. Launchers, lockscreens, and keyboards are filtered out to represent true application usage.",
                        color = Color(0xFFB0BEC5),
                        fontFamily = state.currentFont.toFontFamily(),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 5. Screen-Off Audio Card
            item {
                val audioSource = remember { MutableInteractionSource() }
                val isAudioFocused by audioSource.collectIsFocusedAsState()
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .focusable(interactionSource = audioSource)
                        .background(if (isAudioFocused) Color(0xFF1E2C33) else Color(0xFF141D22))
                        .border(
                            width = if (isAudioFocused) 2.dp else 1.dp,
                            color = if (isAudioFocused) Color(0xFFFFCC80) else Color(0xFF263238),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎧", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Screen-Off Audio (Media Playback)",
                            color = Color(0xFFFFCC80),
                            fontWeight = FontWeight.Bold,
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 13.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tracks continuous foreground media playback from verified audio and podcast apps (Spotify, YouTube Music, Podcasts, Audible, Walkman, etc.) while your screen is physically off or locked.",
                        color = Color(0xFFB0BEC5),
                        fontFamily = state.currentFont.toFontFamily(),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 6. Call Time Card
            item {
                val callSource = remember { MutableInteractionSource() }
                val isCallFocused by callSource.collectIsFocusedAsState()
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .focusable(interactionSource = callSource)
                        .background(if (isCallFocused) Color(0xFF1E2C33) else Color(0xFF141D22))
                        .border(
                            width = if (isCallFocused) 2.dp else 1.dp,
                            color = if (isCallFocused) Color(0xFFA5D6A7) else Color(0xFF263238),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📞", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Call Time (VoIP & Phone)",
                            color = Color(0xFFA5D6A7),
                            fontWeight = FontWeight.Bold,
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 13.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tracks active voice and video communication services (Phone, Signal, WhatsApp, Snapchat, Telegram, Discord), including when the proximity sensor switches off the screen against your ear.",
                        color = Color(0xFFB0BEC5),
                        fontFamily = state.currentFont.toFontFamily(),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            
            // 7. Privacy & On-Device Security Card
            item {
                val privacySource = remember { MutableInteractionSource() }
                val isPrivacyFocused by privacySource.collectIsFocusedAsState()
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .focusable(interactionSource = privacySource)
                        .background(if (isPrivacyFocused) Color(0xFF162D38) else Color(0xFF102027).copy(alpha = 0.8f))
                        .border(
                            width = if (isPrivacyFocused) 2.dp else 1.dp,
                            color = if (isPrivacyFocused) Color(0xFF00E5FF) else Color(0xFF1E3942),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔒", fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "100% On-Device & Private",
                            color = Color(0xFF80D8FF),
                            fontWeight = FontWeight.Bold,
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All metrics are computed locally via Android's local UsageStats API. No analytics, tracking, or network requests are ever made.",
                        color = Color(0xFF90A4AE),
                        fontFamily = state.currentFont.toFontFamily(),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(36.dp))
            }
        } else {
            // Total Usage Hero Card
            item {
                Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF263238).copy(alpha = 0.94f),
                                Color(0xFF10171B).copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF546E7A), Color(0xFF1E282D))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF1E282D), shape = RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF455A64), shape = RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            SkeuomorphicUsageIcon(modifier = Modifier.size(36.dp * state.iconScale))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (timeRange) {
                                    UsageTimeRange.DAILY -> "SCREEN-ON TIME (TODAY)"
                                    UsageTimeRange.WEEKLY -> "SCREEN-ON TIME (PAST 7 DAYS)"
                                    UsageTimeRange.MONTHLY -> "SCREEN-ON TIME (PAST 30 DAYS)"
                                },
                                fontFamily = state.currentFont.toFontFamily(),
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatUsageDuration(totalScreenOnMs),
                                fontFamily = state.currentFont.toFontFamily(),
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                style = TextStyle(
                                    shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 3f)
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Dual Breakdown Metrics (Screen On vs Screen Off Audio vs Calls)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF121B20))
                            .border(1.dp, Color(0xFF263238), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Screen On
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E5FF), shape = RoundedCornerShape(2.dp)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Screen On",
                                    fontFamily = state.currentFont.toFontFamily(),
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatUsageDuration(totalScreenOnMs),
                                fontFamily = state.currentFont.toFontFamily(),
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(26.dp)
                                .background(Color(0xFF37474F))
                        )
                        
                        // Screen Off Audio
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFB74D), shape = RoundedCornerShape(2.dp)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Audio",
                                    fontFamily = state.currentFont.toFontFamily(),
                                    color = Color(0xFFB0BEC5),
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatUsageDuration(totalScreenOffMediaMs),
                                fontFamily = state.currentFont.toFontFamily(),
                                color = Color(0xFFFFCC80),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (totalCallMs > 0) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(Color(0xFF37474F))
                            )
                            
                            // Calls
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF81C784), shape = RoundedCornerShape(2.dp)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Calls",
                                        fontFamily = state.currentFont.toFontFamily(),
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatUsageDuration(totalCallMs),
                                    fontFamily = state.currentFont.toFontFamily(),
                                    color = Color(0xFFA5D6A7),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    androidx.compose.material3.HorizontalDivider(
                        color = Color(0xFF37474F).copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (timeRange != UsageTimeRange.DAILY) {
                            val days = if (timeRange == UsageTimeRange.WEEKLY) 7L else 30L
                            val dailyAvgMs = totalScreenOnMs / days
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Daily Screen Avg: ",
                                    fontFamily = state.currentFont.toFontFamily(),
                                    color = Color(0xFF90A4AE),
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = formatUsageDuration(dailyAvgMs),
                                    fontFamily = state.currentFont.toFontFamily(),
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "Midnight to Now (Digital Wellbeing sync)",
                                fontFamily = state.currentFont.toFontFamily(),
                                color = Color(0xFF90A4AE),
                                fontSize = 11.sp
                            )
                        }
                        
                        Text(
                            text = "${usageEntries.size} ${if (usageEntries.size == 1) "app" else "apps"}",
                            fontFamily = state.currentFont.toFontFamily(),
                            color = Color(0xFF00E5FF),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            
            if (usageEntries.isNotEmpty()) {
                Text(
                    text = "App Breakdown",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = state.currentFont.toFontFamily(),
                    style = TextStyle(
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f)
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        if (usageEntries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No usage data recorded for this period.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = state.currentFont.toFontFamily(),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            val maxItemScreenTime = usageEntries.maxOfOrNull { it.screenOnTimeMs } ?: 1L
            items(usageEntries, key = { it.packageName }) { entry ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                val screenOnPercentage = if (totalScreenOnMs > 0) ((entry.screenOnTimeMs * 100f) / totalScreenOnMs).toInt() else 0
                val ratio = if (maxItemScreenTime > 0) (entry.screenOnTimeMs.toFloat() / maxItemScreenTime.toFloat()).coerceIn(0.03f, 1f) else 0f
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .focusable(interactionSource = interactionSource)
                        .symbianHighlight(isFocused, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (entry.icon != null) {
                            val bitmap = remember(entry.icon) {
                                try {
                                    entry.icon.toCachedImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp * state.iconScale)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.appName, 
                                color = if (isFocused) Color.Black else Color.White, 
                                fontWeight = FontWeight.Bold,
                                fontFamily = state.currentFont.toFontFamily(),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = TextStyle(
                                    shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f)
                                )
                            )
                            if (entry.screenOffMediaTimeMs > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "🎧 ${formatUsageDuration(entry.screenOffMediaTimeMs)} audio",
                                    fontFamily = state.currentFont.toFontFamily(),
                                    color = if (isFocused) Color(0xFF37474F) else Color(0xFFFFCC80),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (entry.callTimeMs > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "📞 ${formatUsageDuration(entry.callTimeMs)} call time",
                                    fontFamily = state.currentFont.toFontFamily(),
                                    color = if (isFocused) Color(0xFF37474F) else Color(0xFFA5D6A7),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "${formatUsageDuration(entry.screenOnTimeMs)} (${screenOnPercentage}%)",
                            color = if (isFocused) Color.Black else Color(0xFF80D8FF),
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 13.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Usage Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = ratio)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF0099CC),
                                            Color(0xFF33B5E5),
                                            Color(0xFF80D8FF)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
}



@Composable
fun ManageAppDialogHandler(
    selectedApp: AppInfo?,
    state: LauncherState,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    if (selectedApp != null) {
        var showIconPackSelector by remember { mutableStateOf(false) }
        var selectedPackForIcons by remember { mutableStateOf<IconPackInfo?>(null) }
        var iconSearchQuery by remember { mutableStateOf("") }
        
        val context = LocalContext.current
        val isPinned = state.pinnedApps.any { it.packageName == selectedApp.packageName }
        
        val haptic = androidx.compose.ui.platform.LocalView.current.let { view ->
            remember { object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }}
        }

        BackHandler(enabled = true) {
            if (selectedPackForIcons != null) {
                selectedPackForIcons = null
                iconSearchQuery = ""
            } else if (showIconPackSelector) {
                showIconPackSelector = false
            } else {
                onDismiss()
            }
        }

        if (selectedPackForIcons != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = "Icons: ${selectedPackForIcons!!.name}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f))
                    )
                }
                DashedDivider()
                
                // Search bar for icons
                var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(iconSearchQuery)) }
                val focusRequester = remember { FocusRequester() }
                
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = textFieldValue,
                        onValueChange = { 
                            textFieldValue = it
                            iconSearchQuery = it.text
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Black),
                        decorationBox = { innerTextField ->
                            if (textFieldValue.text.isEmpty()) {
                                Text("Search icons...", color = Color.Gray, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                    )
                }
                DashedDivider()
                
                var iconList by remember { mutableStateOf<List<String>>(emptyList()) }
                LaunchedEffect(selectedPackForIcons) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val list = viewModel.getIconList(selectedPackForIcons!!.packageName)
                        iconList = list
                    }
                }
                
                val filteredIcons = remember(iconList, iconSearchQuery) {
                    if (iconSearchQuery.isEmpty()) iconList else iconList.filter { it.contains(iconSearchQuery, ignoreCase = true) }
                }
                
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 64.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                        val intSource = remember { MutableInteractionSource() }
                        val isFocused by intSource.collectIsFocusedAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .symbianHighlight(isFocused)
                                .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.setIndividualIconPack(selectedApp.packageName, selectedPackForIcons!!.packageName)
                                    selectedPackForIcons = null
                                    showIconPackSelector = false
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Use Pack Default",
                                color = if (isFocused) Color.Black else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
                            )
                        }
                    }
                    
                    items(filteredIcons.size, key = { filteredIcons[it] }) { index ->
                        val drawableName = filteredIcons[index]
                        val intSource = remember { MutableInteractionSource() }
                        val isFocused by intSource.collectIsFocusedAsState()
                        
                        var drawable by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
                        
                        LaunchedEffect(drawableName) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val d = viewModel.getDrawableByName(selectedPackForIcons!!.packageName, drawableName)
                                drawable = d
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .symbianHighlight(isFocused)
                                .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.setIndividualIconPack(selectedApp.packageName, "${selectedPackForIcons!!.packageName}|${drawableName}")
                                    selectedPackForIcons = null
                                    showIconPackSelector = false
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (drawable != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = drawable!!.toCachedImageBitmap(),
                                    contentDescription = drawableName,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (showIconPackSelector) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = "Select Icon for ${selectedApp.name}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f))
                    )
                }
                DashedDivider()
                
                val listFocusRequester = remember { FocusRequester() }
                
                LaunchedEffect(Unit) {
                    try { listFocusRequester.requestFocus() } catch(e: Exception) {}
                }

                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    item {
                        SettingsMenuItem(
                            title = "Default Icon",
                            haptic = haptic,
                            isFirst = true,
                            onClick = {
                                viewModel.clearIndividualIconPack(selectedApp.packageName)
                                showIconPackSelector = false
                                onDismiss()
                            }
                        )
                    }
                    itemsIndexed(state.availableIconPacks) { index, pack ->
                        val intSource = remember { MutableInteractionSource() }
                        val isFocused by intSource.collectIsFocusedAsState()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .symbianHighlight(isFocused)
                                .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    selectedPackForIcons = pack
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = pack.icon.toCachedImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp * state.iconScale)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = pack.name,
                                color = if (isFocused) Color.Black else Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
                            )
                        }
                        DashedDivider()
                    }
                }
            }
        } else {
            val haptic = androidx.compose.ui.platform.LocalView.current.let { view ->
                remember { object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }}
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Retro Top Bar
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = selectedApp.icon.toCachedImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = selectedApp.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f))
                        )
                    }
                }
                
                DashedDivider()
                
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        SettingsMenuItem(
                            title = if (isPinned) "Unpin App" else "Pin App",
                            icon = if (isPinned) androidx.compose.material.icons.Icons.Filled.Star else androidx.compose.material.icons.Icons.Filled.Star,
                            haptic = haptic,
                            isFirst = true,
                            onClick = {
                                viewModel.togglePinned(selectedApp)
                                onDismiss()
                            }
                        )
                        SettingsMenuItem(
                            title = "App Info",
                            icon = androidx.compose.material.icons.Icons.Filled.Info,
                            haptic = haptic,
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${selectedApp.packageName}")
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                onDismiss()
                            }
                        )
                        if (state.availableIconPacks.isNotEmpty()) {
                            SettingsMenuItem(
                                title = "Change Icon",
                                icon = androidx.compose.material.icons.Icons.Filled.Palette,
                                haptic = haptic,
                                onClick = {
                                    showIconPackSelector = true
                                }
                            )
                        }
                        SettingsMenuItem(
                            title = "Uninstall",
                            icon = androidx.compose.material.icons.Icons.Filled.Delete,
                            haptic = haptic,
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                                        data = android.net.Uri.parse("package:${selectedApp.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatDailyDateLabel(dateMillis: Long?, timeStr: String, isToday: Boolean, isNorwegian: Boolean): String {
    val date = if (dateMillis != null && dateMillis > 0) {
        java.util.Date(dateMillis)
    } else {
        runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(timeStr)
        }.getOrNull() ?: java.util.Date()
    }
    
    val cal = java.util.Calendar.getInstance().apply { time = date }
    val dayNum = cal.get(java.util.Calendar.DAY_OF_MONTH)
    
    val norwegianMonths = listOf("jan", "feb", "mar", "apr", "mai", "jun", "jul", "aug", "sep", "okt", "nov", "des")
    val norwegianDays = listOf("Søn", "Man", "Tir", "Ons", "Tor", "Fre", "Lør")
    
    val englishMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val englishDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    
    val monthIdx = cal.get(java.util.Calendar.MONTH).coerceIn(0, 11)
    val dayOfWeekIdx = (cal.get(java.util.Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
    
    return if (isNorwegian) {
        val monthStr = norwegianMonths[monthIdx]
        if (isToday) {
            "I dag $dayNum. $monthStr"
        } else {
            val dayName = norwegianDays[dayOfWeekIdx]
            "$dayName $dayNum. $monthStr"
        }
    } else {
        val monthStr = englishMonths[monthIdx]
        if (isToday) {
            "Today $dayNum $monthStr"
        } else {
            val dayName = englishDays[dayOfWeekIdx]
            "$dayName $dayNum $monthStr"
        }
    }
}

private fun formatDailyPrecipitation(precipMm: Double?, isNorwegian: Boolean): String {
    if (precipMm == null || precipMm < 0.1) return ""
    return if (precipMm >= 9.95) {
        "${kotlin.math.round(precipMm).toInt()} mm"
    } else {
        val formatted = String.format(java.util.Locale.US, "%.1f", precipMm)
        val withComma = if (isNorwegian) formatted.replace('.', ',') else formatted
        "$withComma mm"
    }
}

private fun formatDailyWindSpeed(windSpeedVal: Double?, unit: WindSpeedUnit): String {
    if (windSpeedVal == null) return "--"
    return when (unit) {
        WindSpeedUnit.MPH -> "${kotlin.math.round(windSpeedVal * 2.23694).toInt()} mph"
        WindSpeedUnit.KMH -> "${kotlin.math.round(windSpeedVal * 3.6).toInt()} km/h"
        WindSpeedUnit.MS -> "${kotlin.math.round(windSpeedVal).toInt()} m/s"
    }
}

@Composable
fun WeatherForecastScreen(
    state: LauncherState, 
    viewModel: LauncherViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onSettingsClick: () -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val tabs = listOf("Today", "Hourly", "Daily", "Faves")
    val currentTab = tabs.getOrElse(state.weatherTab) { "Today" }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
    ) {
        // Top row with Tabs and Settings
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tabs
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val isSelected = state.weatherTab == index
                    
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = state.currentFont.toFontFamily(),
                        modifier = Modifier
                            .symbianHighlight(isFocused)
                            .focusable(interactionSource = interactionSource)
                            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setWeatherTab(index)
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
            }
            
            val intSource = remember { MutableInteractionSource() }
            val isFoc by intSource.collectIsFocusedAsState()
            
            androidx.compose.material3.IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .symbianHighlight(isFoc)
                    .focusable(interactionSource = intSource)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                    contentDescription = "Weather Settings",
                    tint = Color.White
                )
            }
        }

        // Last updated underneath the tabs (with language toggle for Daily view)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val lastUpdated = state.weatherLastUpdated
            if (lastUpdated != null) {
                val diff = (System.currentTimeMillis() - lastUpdated) / 60000
                val timeText = if (diff < 1) "Just now" else "$diff min ago"
                Text(
                    text = "Last updated: $timeText",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = state.currentFont.toFontFamily()
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            
            if (currentTab == "Daily") {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.clickable {
                        val next = if (state.weatherDailyLanguage == WeatherDailyLanguage.NORWEGIAN) WeatherDailyLanguage.ENGLISH else WeatherDailyLanguage.NORWEGIAN
                        viewModel.updateState { it.copy(weatherDailyLanguage = next) }
                    }
                ) {
                    Text(
                        text = if (state.weatherDailyLanguage == WeatherDailyLanguage.NORWEGIAN) "🇳🇴 Norsk (Yr)" else "🇬🇧 English",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        
        Text(
            text = state.weatherLocationName ?: "Unknown Location",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = state.currentFont.toFontFamily(),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        
        when (currentTab) {
            "Today" -> {
                val tempUnit = if (state.tempUnit == TempUnit.FAHRENHEIT) "°F" else "°C"
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                    RealisticWeatherIcon(
                        symbolCode = state.weatherCurrentSymbol,
                        size = 72.dp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Column {
                        Text(
                            text = "${kotlin.math.round(state.weatherCurrentTemp).toInt()}$tempUnit",
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = state.currentFont.toFontFamily()
                        )
                        Text(
                            text = state.weatherCurrentSymbol.ifEmpty { "Unknown" },
                            color = Color.White,
                            fontSize = 20.sp,
                            fontFamily = state.currentFont.toFontFamily()
                        )
                    }
                }
                
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp), color = Color.White.copy(alpha = 0.3f))
                
                val feelsLikeUnit = if (state.tempUnit == TempUnit.FAHRENHEIT) "°F" else "°C"
                val feelsLikeText = state.weatherFeelsLike?.let { "${kotlin.math.round(it).toInt()}$feelsLikeUnit" } ?: "--"
                val humidityText = state.weatherHumidity?.let { "${it.toInt()}%" } ?: "--"
                val precipText = state.weatherPrecipitation?.let { "$it mm" } ?: "0 mm"
                
                val windSpeedVal = state.weatherWindSpeed
                val windText = if (windSpeedVal != null) {
                    when (state.windSpeedUnit) {
                        WindSpeedUnit.MPH -> "${(windSpeedVal * 2.23694).toInt()} mph"
                        WindSpeedUnit.KMH -> "${(windSpeedVal * 3.6).toInt()} km/h"
                        WindSpeedUnit.MS -> "${String.format(java.util.Locale.US, "%.1f", windSpeedVal)} m/s"
                    }
                } else "--"
                
                val details = listOf(
                    "Feels Like" to feelsLikeText,
                    "Humidity" to humidityText,
                    "Precipitation" to precipText,
                    "Wind" to windText
                )
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    details.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = label, color = Color.White, fontSize = 18.sp, fontFamily = state.currentFont.toFontFamily())
                            Text(text = value, color = Color.White, fontSize = 18.sp, fontFamily = state.currentFont.toFontFamily())
                        }
                    }
                }
            }
            "Hourly" -> {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(state.hourlyForecast) { forecast ->
                        val intSource = remember { MutableInteractionSource() }
                        val isFoc by intSource.collectIsFocusedAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .symbianHighlight(isFoc)
                                .focusable(interactionSource = intSource)
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = forecast.time, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            
                            RealisticWeatherIcon(
                                symbolCode = forecast.symbolCode,
                                size = 30.dp,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            
                            Text(text = "${forecast.temperature.toInt()}°", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp).padding(start = 8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(64.dp), horizontalArrangement = Arrangement.End) {
                                Text(text = "${forecast.humidity?.toInt() ?: 0}%", color = Color.White.copy(alpha=0.8f), fontSize = 16.sp)
                                Icon(imageVector = androidx.compose.material.icons.Icons.Default.WaterDrop, contentDescription = null, tint = Color.White.copy(alpha=0.8f), modifier = Modifier.size(16.dp).padding(start = 2.dp))
                            }
                        }
                        androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
                    }
                }
            }
            "Daily" -> {
                val isNorwegian = state.weatherDailyLanguage == WeatherDailyLanguage.NORWEGIAN
                val nightLabel = "00"
                val morningLabel = "06"
                val afternoonLabel = "12"
                val eveningLabel = "18"
                val tempLabel = if (isNorwegian) "Temp" else "Temp"
                val precipLabel = if (isNorwegian) "Nedb" else "Rain"
                
                val windLabel = if (isNorwegian) "Vind" else "Wind"

                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(text = nightLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(text = morningLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(text = afternoonLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(text = eveningLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(text = tempLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.width(38.dp), contentAlignment = Alignment.Center) {
                            Text(text = precipLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.width(42.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(text = windLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 10.sp)
                        }
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(state.dailyForecast) { index, forecast ->
                            val intSource = remember { MutableInteractionSource() }
                            val isFoc by intSource.collectIsFocusedAsState()
                            
                            var isExpanded by remember { androidx.compose.runtime.mutableStateOf(false) }
                            
                            val dayLabel = formatDailyDateLabel(
                                dateMillis = forecast.dateMillis,
                                timeStr = forecast.time,
                                isToday = forecast.isToday,
                                isNorwegian = isNorwegian
                            )
                            val precipText = formatDailyPrecipitation(
                                precipMm = forecast.precipitation,
                                isNorwegian = isNorwegian
                            )
                            val windUnitStr = when (state.windSpeedUnit) {
                                WindSpeedUnit.MPH -> "mph"
                                WindSpeedUnit.KMH -> "km/h"
                                WindSpeedUnit.MS -> "m/s"
                            }
                            val windText = if (forecast.windSpeed == null) "--" else when (state.windSpeedUnit) {
                                WindSpeedUnit.MPH -> "${kotlin.math.round(forecast.windSpeed * 2.23694).toInt()} $windUnitStr"
                                WindSpeedUnit.KMH -> "${kotlin.math.round(forecast.windSpeed * 3.6).toInt()} $windUnitStr"
                                WindSpeedUnit.MS -> "${kotlin.math.round(forecast.windSpeed).toInt()} $windUnitStr"
                            }

                            val primaryTextColor = Color.White
                            val secondaryTextColor = Color.White.copy(alpha = 0.85f)

                            val prevForecast = state.dailyForecast.getOrNull(index - 1)
                            val highTemp = forecast.highTemp?.toInt() ?: 0
                            val lowTemp = forecast.lowTemp?.toInt() ?: 0
                            val prevHigh = prevForecast?.highTemp?.toInt()
                            val prevLow = prevForecast?.lowTemp?.toInt()

                            val highIcon = when {
                                prevHigh == null -> null
                                highTemp > prevHigh -> androidx.compose.material.icons.Icons.Default.KeyboardArrowUp
                                highTemp < prevHigh -> androidx.compose.material.icons.Icons.Default.KeyboardArrowDown
                                else -> null
                            }

                            val lowIcon = when {
                                prevLow == null -> null
                                lowTemp > prevLow -> androidx.compose.material.icons.Icons.Default.KeyboardArrowUp
                                lowTemp < prevLow -> androidx.compose.material.icons.Icons.Default.KeyboardArrowDown
                                else -> null
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .symbianHighlight(isFoc)
                                    .focusable(interactionSource = intSource)
                                    .clickable { isExpanded = !isExpanded }
                            ) {
                                Text(
                                    text = dayLabel,
                                    color = primaryTextColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                        if (!forecast.nightSymbol.isNullOrEmpty()) {
                                            RealisticWeatherIcon(symbolCode = forecast.nightSymbol, size = 28.dp)
                                        } else {
                                            Spacer(modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                        if (!forecast.morningSymbol.isNullOrEmpty()) {
                                            RealisticWeatherIcon(symbolCode = forecast.morningSymbol, size = 28.dp)
                                        } else {
                                            Spacer(modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                        if (!forecast.afternoonSymbol.isNullOrEmpty()) {
                                            RealisticWeatherIcon(symbolCode = forecast.afternoonSymbol, size = 28.dp)
                                        } else {
                                            Spacer(modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                        if (!forecast.eveningSymbol.isNullOrEmpty()) {
                                            RealisticWeatherIcon(symbolCode = forecast.eveningSymbol, size = 28.dp)
                                        } else {
                                            Spacer(modifier = Modifier.size(28.dp))
                                        }
                                    }

                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "${highTemp}°", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                if (highIcon != null) {
                                                    Icon(imageVector = highIcon, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = "${lowTemp}°", color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                if (lowIcon != null) {
                                                    Icon(imageVector = lowIcon, contentDescription = null, tint = Color(0xFF42A5F5), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }

                                    Box(modifier = Modifier.width(38.dp), contentAlignment = Alignment.Center) {
                                        if (precipText.isNotEmpty()) {
                                            Text(text = precipText, color = Color(0xFF29B6F6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }

                                    Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.CenterEnd) {
                                        Text(
                                            text = windText,
                                            color = secondaryTextColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                            lineHeight = 12.sp,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                }
                                
                                androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                    ) {
                                        val dayHourly = state.hourlyForecast.filter { 
                                            val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis ?: 0L }
                                            val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = forecast.dateMillis ?: 0L }
                                            cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR) &&
                                            cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR)
                                        }
                                        
                                        if (dayHourly.isNotEmpty()) {
                                            val tidLabel = if (isNorwegian) "Tid" else "Time"
                                            val varLabel = if (isNorwegian) "Vær" else "Weather"
                                            val temLabel = if (isNorwegian) "Temp." else "Temp"
                                            val nedLabel = if (isNorwegian) "Nedbør mm" else "Precip mm"
                                            val vinLabel = if (isNorwegian) "Vind m/s" else "Wind m/s"
                                            
                                            // Header
                                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(text = tidLabel, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, modifier = Modifier.width(40.dp))
                                                Text(text = varLabel, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                Text(text = temLabel, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                Text(text = nedLabel, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                Text(text = vinLabel, color = Color.White.copy(alpha=0.6f), fontSize = 10.sp, modifier = Modifier.width(50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                            }
                                            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(bottom = 4.dp))
                                            
                                            val intervals = listOf(
                                                Triple("00-06", forecast.nightSymbol, dayHourly.filter { java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis ?: 0L }.get(java.util.Calendar.HOUR_OF_DAY) in 0..5 }),
                                                Triple("06-12", forecast.morningSymbol, dayHourly.filter { java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis ?: 0L }.get(java.util.Calendar.HOUR_OF_DAY) in 6..11 }),
                                                Triple("12-18", forecast.afternoonSymbol, dayHourly.filter { java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis ?: 0L }.get(java.util.Calendar.HOUR_OF_DAY) in 12..17 }),
                                                Triple("18-00", forecast.eveningSymbol, dayHourly.filter { java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis ?: 0L }.get(java.util.Calendar.HOUR_OF_DAY) in 18..23 })
                                            )
                                            
                                            intervals.forEach { (timeStr, symbol, hours) ->
                                                if (hours.isNotEmpty()) {
                                                    val maxT = hours.maxOfOrNull { it.temperature }?.toInt() ?: 0
                                                    val sumP = hours.sumOf { it.precipitation ?: 0.0 }
                                                    val avgW = hours.mapNotNull { it.windSpeed }.average().takeIf { !it.isNaN() } ?: 0.0
                                                    
                                                    val pText = if (sumP > 0) String.format("%.1f", sumP) else "0"
                                                    val wText = when (state.windSpeedUnit) {
                                                        WindSpeedUnit.MPH -> "${kotlin.math.round(avgW * 2.23694).toInt()}"
                                                        WindSpeedUnit.KMH -> "${kotlin.math.round(avgW * 3.6).toInt()}"
                                                        WindSpeedUnit.MS -> "${kotlin.math.round(avgW).toInt()}"
                                                    }
                                                    
                                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Text(text = timeStr, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                                                        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                                                            if (!symbol.isNullOrEmpty()) {
                                                                RealisticWeatherIcon(symbolCode = symbol, size = 24.dp)
                                                            }
                                                        }
                                                        Text(text = "${maxT}°", color = Color(0xFFEF5350), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                        
                                                        // Bar chart for temp instead of just numbers? The user requested "using a small bar chart for temperatures"
                                                        // Let's add a tiny Row to draw the bar chart!
                                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(text = pText, color = Color(0xFF29B6F6), fontSize = 12.sp, modifier = Modifier.padding(end=4.dp))
                                                                // Tiny bar visualization
                                                                Box(modifier = Modifier.width(30.dp).height(4.dp).background(Color.White.copy(alpha=0.2f), androidx.compose.foundation.shape.CircleShape)) {
                                                                    val fraction = (maxT.toFloat() + 20f) / 50f // Arbitrary scale from -20 to +30
                                                                    Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFFEF5350), androidx.compose.foundation.shape.CircleShape))
                                                                }
                                                            }
                                                        }
                                                        
                                                        Row(modifier = Modifier.width(50.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                                            Text(text = wText, color = Color.White, fontSize = 12.sp)
                                                            Icon(imageVector = androidx.compose.material.icons.Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp).padding(start = 2.dp))
                                                        }
                                                    }
                                                    androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                                                }
                                            }
                                        } else {
                                            Text(text = if (isNorwegian) "Ingen timevarsel tilgjengelig" else "No hourly forecast available", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                        }
                                    }
                                }
                                
                                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                            }
                        }
                    }
                }
            }
            "Faves" -> {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    if (state.favoriteLocations.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(text = "No favorite locations", color = Color.White.copy(alpha=0.7f), fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(state.favoriteLocations) { loc ->
                            Text(text = loc, color = Color.White, modifier = Modifier.padding(16.dp))
                            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }
    }
}
