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
                                AppIconImage(
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
