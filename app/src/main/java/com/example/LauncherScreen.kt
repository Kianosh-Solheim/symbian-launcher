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
                        Screen.SETTINGS_ROOT, Screen.SETTINGS_HOME_SCREEN, Screen.SETTINGS_ICON_PACK, Screen.SETTINGS_STATUS_BAR, Screen.SETTINGS_WEATHER, Screen.SETTINGS_DESKTOP, Screen.SETTINGS_BACKUP, Screen.SETTINGS_TIME_DATE, Screen.SETTINGS_T9_SEARCH, Screen.SETTINGS_PINNED_APPS -> "Settings"
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
                    Screen.SETTINGS_ROOT, Screen.SETTINGS_HOME_SCREEN, Screen.SETTINGS_ICON_PACK, Screen.SETTINGS_STATUS_BAR, Screen.SETTINGS_WEATHER, Screen.SETTINGS_DESKTOP, Screen.SETTINGS_BACKUP, Screen.SETTINGS_TIME_DATE, Screen.SETTINGS_T9_SEARCH, Screen.SETTINGS_PINNED_APPS -> "Settings"
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
                viewModel.refreshWeatherIfStale()
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
                    SettingsStatusBar(state, viewModel, haptic) { navigateTo(it) }
                }
                Screen.SETTINGS_WEATHER -> {
                    SettingsWeather(state, viewModel, haptic)
                }
                Screen.SETTINGS_DESKTOP -> {
                    SettingsDesktopMode(state, haptic)
                }
                Screen.SETTINGS_BACKUP -> {
                    SettingsBackupScreen(haptic)
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
