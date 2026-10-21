package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ClockAppScreen(
    state: LauncherState,
    viewModel: LauncherViewModel,
    haptic: HapticFeedback,
    onBack: () -> Unit,
    onAddAlarm: () -> Unit = {},
    onAlarmOptions: (AlarmItem) -> Unit = {},
    onAddWorldClock: () -> Unit = {},
    onWorldClockOptions: (WorldClockItem) -> Unit = {}
) {
    val tabs = listOf("Clock", "Alarm", "Stopwatch", "Timer")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // Top Tabs
        ClockTopTabs(
            tabs = tabs,
            currentTab = state.clockTab,
            onSelectTab = { index -> 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.setClockTab(index)
            },
            state = state
        )
        
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.clockTab) {
                0 -> CombinedClockTab(state, haptic, onAddWorldClock, onWorldClockOptions)
                1 -> AlarmTab(state, haptic, viewModel, onAddAlarm, onAlarmOptions)
                2 -> StopwatchTab(state, haptic)
                3 -> TimerTab(state, haptic)
            }
        }
    }
}

@Composable
fun CombinedClockTab(
    state: LauncherState,
    haptic: HapticFeedback,
    onAddWorldClock: () -> Unit,
    onWorldClockOptions: (WorldClockItem) -> Unit
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }
    
    val localTz = remember { java.util.TimeZone.getDefault() }
    val localCityName = remember {
        val id = localTz.id
        val shortName = id.substringAfterLast("/").replace("_", " ")
        if (shortName.isNotEmpty()) shortName else "Local Time"
    }

    val sdfTime = remember(state.is24HourFormat) {
        val pattern = buildString {
            append(if (state.is24HourFormat) "HH:mm" else "hh:mm")
            append(":ss")
            if (!state.is24HourFormat) append(" a")
        }
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
    }

    val dateStr = remember(state.dateFormat, state.showWeekday, state.weekdayFormat, currentTime) {
        val datePattern = when(state.dateFormat) {
            DateFormatOption.DD_MM_YYYY -> "dd.MM.yyyy"
            DateFormatOption.D_MMM -> "d MMM"
            DateFormatOption.D_MM -> "d/MM"
            DateFormatOption.SYSTEM_DEFAULT -> "MMM d, yyyy"
        }
        val dayPattern = if (state.showWeekday) {
            if (state.weekdayFormat == WeekdayFormat.SHORT) "E, " else "EEEE, "
        } else ""
        java.text.SimpleDateFormat(dayPattern + datePattern, java.util.Locale.getDefault()).format(java.util.Date(currentTime))
    }

    val getFormattedTime: (String) -> String = { tzId ->
        val tz = java.util.TimeZone.getTimeZone(tzId)
        val sdf = java.text.SimpleDateFormat(if (state.is24HourFormat) "HH:mm" else "hh:mm a", java.util.Locale.getDefault())
        sdf.timeZone = tz
        sdf.format(java.util.Date(currentTime))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP SECTION: Big Skeuomorphic Analogue Clock & Local Time
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Big Skeuomorphic Analogue Clock (Switches to deep black at night)
                SkeuomorphicAnalogueClock(
                    timeMillis = currentTime,
                    clockSize = 160.dp,
                    showSeconds = true,
                    showNumerals = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Digital Time
                Text(
                    text = sdfTime.format(java.util.Date(currentTime)),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            offset = Offset(1.5f, 1.5f),
                            blurRadius = 3f
                        )
                    )
                )
                
                Spacer(modifier = Modifier.height(3.dp))
                
                // Date & Local City Name
                Text(
                    text = "$dateStr • $localCityName",
                    color = Color.LightGray,
                    fontSize = 15.sp,
                    fontFamily = state.currentFont.toFontFamily(),
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Section Header for World Clocks
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "World Clocks",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = state.currentFont.toFontFamily()
                )
                
                val addIntSrc = remember { MutableInteractionSource() }
                val isAddFoc by addIntSrc.collectIsFocusedAsState()
                Row(
                    modifier = Modifier
                        .symbianHighlight(isAddFoc)
                        .focusable(interactionSource = addIntSrc)
                        .clickable(interactionSource = addIntSrc, indication = LocalIndication.current) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAddWorldClock()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add City",
                        tint = if (isAddFoc) Color.Black else Color(0xFF64B5F6),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add City",
                        color = if (isAddFoc) Color.Black else Color(0xFF64B5F6),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = state.currentFont.toFontFamily()
                    )
                }
            }
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
        }

        // GRID OF WORLD CLOCKS (2 columns with analogue and digital underneath)
        val chunks = state.worldClocks.chunked(2)
        items(chunks) { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (item in rowItems) {
                    Box(modifier = Modifier.weight(1f)) {
                        WorldClockGridCard(
                            item = item,
                            currentTime = currentTime,
                            formattedTime = getFormattedTime(item.timeZoneId),
                            state = state,
                            haptic = haptic,
                            onOptions = { onWorldClockOptions(item) }
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // ADD WORLD CLOCK BUTTON at bottom
        item {
            Spacer(modifier = Modifier.height(10.dp))
            val intSource = remember { MutableInteractionSource() }
            val isFoc by intSource.collectIsFocusedAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .symbianHighlight(isFoc)
                    .focusable(interactionSource = intSource)
                    .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAddWorldClock()
                    }
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    tint = if (isFoc) Color.Black else Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add World Clock",
                    color = if (isFoc) Color.Black else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = state.currentFont.toFontFamily()
                )
            }
        }
    }
}

@Composable
fun WorldClockGridCard(
    item: WorldClockItem,
    currentTime: Long,
    formattedTime: String,
    state: LauncherState,
    haptic: HapticFeedback,
    onOptions: () -> Unit
) {
    val intSource = remember { MutableInteractionSource() }
    val isFoc by intSource.collectIsFocusedAsState()
    val offsetStr = remember(item.timeZoneId, currentTime) {
        formatRelativeOffset(item.timeZoneId, currentTime)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .symbianHighlight(isFoc)
            .focusable(interactionSource = intSource)
            .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOptions()
            }
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skeuomorphic mini analogue clock (changes to black at night in that city's timezone)
            SkeuomorphicAnalogueClock(
                timeMillis = currentTime,
                timeZoneId = item.timeZoneId,
                clockSize = 84.dp,
                showSeconds = true,
                showNumerals = false
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // City Name
            Text(
                text = item.city,
                color = if (isFoc) Color.Black else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = state.currentFont.toFontFamily(),
                maxLines = 1
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            // Digital Time Underneath
            Text(
                text = formattedTime,
                color = if (isFoc) Color.Black else Color.White,
                fontSize = 17.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            
            if (offsetStr.isNotEmpty()) {
                Text(
                    text = offsetStr,
                    color = if (isFoc) Color.DarkGray else Color.LightGray,
                    fontSize = 12.sp,
                    fontFamily = state.currentFont.toFontFamily(),
                    maxLines = 1
                )
            }
        }
        
        // 3-dot options button
        val moreIntSource = remember { MutableInteractionSource() }
        val moreFoc by moreIntSource.collectIsFocusedAsState()
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOptions()
            },
            interactionSource = moreIntSource,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(26.dp)
                .symbianHighlight(moreFoc)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = if (moreFoc || isFoc) Color.Black else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun formatRelativeOffset(timeZoneId: String, currentTime: Long): String {
    return try {
        val targetTz = java.util.TimeZone.getTimeZone(timeZoneId)
        val localTz = java.util.TimeZone.getDefault()
        
        val targetCal = java.util.Calendar.getInstance(targetTz).apply { timeInMillis = currentTime }
        val localCal = java.util.Calendar.getInstance(localTz).apply { timeInMillis = currentTime }
        
        val offsetDiffMillis = targetTz.getOffset(currentTime) - localTz.getOffset(currentTime)
        val offsetDiffHours = (offsetDiffMillis / (1000.0 * 60 * 60)).toFloat()
        
        val targetDay = targetCal.get(java.util.Calendar.DAY_OF_YEAR)
        val localDay = localCal.get(java.util.Calendar.DAY_OF_YEAR)
        val dayDiff = targetDay - localDay
        
        val dayPrefix = when (dayDiff) {
            1, -364, -365 -> "Tomorrow, "
            -1, 364, 365 -> "Yesterday, "
            else -> "Today, "
        }
        
        val hrStr = if (offsetDiffHours == 0f) {
            "Same time"
        } else if (offsetDiffHours % 1 == 0f) {
            val sign = if (offsetDiffHours > 0) "+" else ""
            "${sign}${offsetDiffHours.toInt()}HRS"
        } else {
            val sign = if (offsetDiffHours > 0) "+" else ""
            "${sign}${String.format(java.util.Locale.US, "%.1f", offsetDiffHours)}HRS"
        }
        
        dayPrefix + hrStr
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun ClockTopTabs(
    tabs: List<String>,
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    state: LauncherState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == currentTab
            val intSource = remember { MutableInteractionSource() }
            val isFoc by intSource.collectIsFocusedAsState()
            
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                fontSize = 18.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = state.currentFont.toFontFamily(),
                modifier = Modifier
                    .symbianHighlight(isFoc)
                    .focusable(interactionSource = intSource)
                    .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                        onSelectTab(index)
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }
    }
}

@Composable
fun AlarmTab(state: LauncherState, haptic: HapticFeedback, viewModel: LauncherViewModel, onAddAlarm: () -> Unit, onAlarmOptions: (AlarmItem) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(state.alarms) { alarm ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val intSource = remember { MutableInteractionSource() }
                val isFoc by intSource.collectIsFocusedAsState()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .symbianHighlight(isFoc)
                        .focusable(interactionSource = intSource)
                        .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.updateAlarm(context, alarm.copy(enabled = !alarm.enabled))
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(alarm.toDisplayString(), color = if (isFoc) Color.Black else Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text(alarm.label, color = if (isFoc) Color.DarkGray else Color.LightGray, fontSize = 14.sp, fontFamily = state.currentFont.toFontFamily())
                    }
                    Text(
                        text = if (alarm.enabled) "On" else "Off",
                        color = if (isFoc) Color.Black else Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = state.currentFont.toFontFamily()
                    )
                }
                
                val moreIntSource = remember { MutableInteractionSource() }
                val moreFoc by moreIntSource.collectIsFocusedAsState()
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAlarmOptions(alarm)
                    },
                    interactionSource = moreIntSource,
                    modifier = Modifier.symbianHighlight(moreFoc)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = if (moreFoc) Color.Black else Color.White
                    )
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
        }
        
        item {
            val intSource = remember { MutableInteractionSource() }
            val isFoc by intSource.collectIsFocusedAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianHighlight(isFoc)
                    .focusable(interactionSource = intSource)
                    .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAddAlarm()
                    }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = if (isFoc) Color.Black else Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Alarm", color = if (isFoc) Color.Black else Color.White, fontSize = 18.sp, fontFamily = state.currentFont.toFontFamily())
            }
            HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
        }
    }
}

@Composable
fun StopwatchTab(state: LauncherState, haptic: HapticFeedback) {
    var isRunning by remember { mutableStateOf(false) }
    var timeMillis by remember { mutableStateOf(0L) }
    
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val start = System.currentTimeMillis() - timeMillis
            while (isRunning) {
                timeMillis = System.currentTimeMillis() - start
                delay(10)
            }
        }
    }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val maxH = maxHeight
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val minutes = (timeMillis / 60000).toString().padStart(2, '0')
            val seconds = ((timeMillis % 60000) / 1000).toString().padStart(2, '0')
            val fraction = ((timeMillis % 1000) / 10).toString().padStart(2, '0')
            
            val timeFontSize = minOf(maxW * 0.12f, 48.dp)
            Text(
                text = "$minutes:$seconds.$fraction",
                color = Color.White,
                fontSize = timeFontSize.value.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            
            Spacer(modifier = Modifier.height(maxH * 0.1f))
            
            HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
            
            val btnSource1 = remember { MutableInteractionSource() }
            val btnFoc1 by btnSource1.collectIsFocusedAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianHighlight(btnFoc1)
                    .focusable(interactionSource = btnSource1)
                    .clickable(interactionSource = btnSource1, indication = LocalIndication.current) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isRunning = !isRunning
                    }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(if (isRunning) "Stop" else "Start", color = if (btnFoc1) Color.Black else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
            }
            
            HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
            
            val btnSource2 = remember { MutableInteractionSource() }
            val btnFoc2 by btnSource2.collectIsFocusedAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianHighlight(btnFoc2)
                    .focusable(interactionSource = btnSource2)
                    .clickable(interactionSource = btnSource2, indication = LocalIndication.current) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!isRunning) timeMillis = 0L
                    }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Reset", color = if (btnFoc2) Color.Black else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
            }
            
            HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
        }
    }
}

@Composable
fun TimerTab(state: LauncherState, haptic: HapticFeedback) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val maxH = maxHeight
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val isLandscape = maxW > maxH
            val circleSize = minOf(maxW, maxH) * if (isLandscape) 0.5f else 0.4f
            
            Box(
                modifier = Modifier.size(circleSize),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        style = Stroke(width = 4.dp.toPx())
                    )
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
                
                val timeFontSize = minOf(circleSize * 0.2f, 32.dp)
                Text(
                    text = "15:00",
                    color = Color.White,
                    fontSize = timeFontSize.value.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.height(maxH * 0.08f))
            
            HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
            
            val btnSource = remember { MutableInteractionSource() }
            val btnFoc by btnSource.collectIsFocusedAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianHighlight(btnFoc)
                    .focusable(interactionSource = btnSource)
                    .clickable(interactionSource = btnSource, indication = LocalIndication.current) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Start", color = if (btnFoc) Color.Black else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
            }
            
            HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
        }
    }
}

@Composable
fun AlarmOptionsScreen(state: LauncherState, alarm: AlarmItem, haptic: HapticFeedback, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
        val intSource1 = remember { MutableInteractionSource() }
        val isFoc1 by intSource1.collectIsFocusedAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianHighlight(isFoc1)
                .focusable(interactionSource = intSource1)
                .clickable(interactionSource = intSource1, indication = LocalIndication.current) { onEdit() }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Edit", color = if (isFoc1) Color.Black else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
        }
        HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
        
        val intSource2 = remember { MutableInteractionSource() }
        val isFoc2 by intSource2.collectIsFocusedAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianHighlight(isFoc2)
                .focusable(interactionSource = intSource2)
                .clickable(interactionSource = intSource2, indication = LocalIndication.current) { onDelete() }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Edit", color = if (isFoc2) Color.Black else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
        }
        HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
        
        val intSourceDel = remember { MutableInteractionSource() }
        val isFocDel by intSourceDel.collectIsFocusedAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianHighlight(isFocDel)
                .focusable(interactionSource = intSourceDel)
                .clickable(interactionSource = intSourceDel, indication = LocalIndication.current) { onDelete() }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Delete", color = if (isFoc2) Color.Black else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
        }
        HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
    }
}

@Composable
fun WorldClockOptionsScreen(state: LauncherState, haptic: HapticFeedback, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
        val intSource = remember { MutableInteractionSource() }
        val isFoc by intSource.collectIsFocusedAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianHighlight(isFoc)
                .focusable(interactionSource = intSource)
                .clickable(interactionSource = intSource, indication = LocalIndication.current) { onDelete() }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Edit", color = if (isFoc) Color.Black else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
        }
        HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
        
        val intSourceDel = remember { MutableInteractionSource() }
        val isFocDel by intSourceDel.collectIsFocusedAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianHighlight(isFocDel)
                .focusable(interactionSource = intSourceDel)
                .clickable(interactionSource = intSourceDel, indication = LocalIndication.current) { onDelete() }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Delete", color = if (isFoc) Color.Black else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
        }
        HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
    }
}

@Composable
fun AlarmEditScreen(state: LauncherState, alarm: AlarmItem?, haptic: HapticFeedback, onSave: (AlarmItem) -> Unit) {
    var hour by remember { mutableStateOf(alarm?.hour ?: 8) }
    var minute by remember { mutableStateOf(alarm?.minute ?: 0) }
    var label by remember { mutableStateOf(alarm?.label ?: "Alarm") }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { try { focusRequester.requestFocus() } catch (e: Exception) {} }
        
        Text("Hour: $hour", color = Color.White, fontSize = 24.sp, fontFamily = state.currentFont.toFontFamily())
        val intSourceH = remember { MutableInteractionSource() }
        val isFocH by intSourceH.collectIsFocusedAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (alarm == null) Modifier.focusRequester(focusRequester) else Modifier)
                .symbianHighlight(isFocH)
                .focusable(interactionSource = intSourceH)
                .clickable(interactionSource = intSourceH, indication = LocalIndication.current) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    hour = (hour + 1) % 24
                }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Next Hour", color = if (isFocH) Color.Black else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Minute: $minute", color = Color.White, fontSize = 24.sp, fontFamily = state.currentFont.toFontFamily())
        val intSourceM = remember { MutableInteractionSource() }
        val isFocM by intSourceM.collectIsFocusedAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianHighlight(isFocM)
                .focusable(interactionSource = intSourceM)
                .clickable(interactionSource = intSourceM, indication = LocalIndication.current) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    minute = (minute + 5) % 60
                }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Next Minute (+5)", color = if (isFocM) Color.Black else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        val intSourceSave = remember { MutableInteractionSource() }
        val isFocSave by intSourceSave.collectIsFocusedAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianHighlight(isFocSave)
                .focusable(interactionSource = intSourceSave)
                .clickable(interactionSource = intSourceSave, indication = LocalIndication.current) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSave(alarm?.copy(hour = hour, minute = minute, label = label) ?: AlarmItem(hour = hour, minute = minute, label = label))
                }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Save", color = if (isFocSave) Color.Black else Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
        }
    }
}

@Composable
fun WorldClockSearchScreen(state: LauncherState, haptic: HapticFeedback, onSelect: (WorldClockItem) -> Unit) {
    val commonCities = listOf(
        Pair("London", "Europe/London"),
        Pair("New York", "America/New_York"),
        Pair("Tokyo", "Asia/Tokyo"),
        Pair("Paris", "Europe/Paris"),
        Pair("Berlin", "Europe/Berlin"),
        Pair("Sydney", "Australia/Sydney"),
        Pair("Dubai", "Asia/Dubai"),
        Pair("Singapore", "Asia/Singapore"),
        Pair("Los Angeles", "America/Los_Angeles"),
        Pair("Chicago", "America/Chicago"),
        Pair("Toronto", "America/Toronto"),
        Pair("Mumbai", "Asia/Kolkata"),
        Pair("Hong Kong", "Asia/Hong_Kong")
    )
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        item {
            Text("Select City", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        }
        
        items(commonCities) { city ->
            val intSource = remember { MutableInteractionSource() }
            val isFoc by intSource.collectIsFocusedAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianHighlight(isFoc)
                    .focusable(interactionSource = intSource)
                    .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(WorldClockItem(city = city.first, timeZoneId = city.second))
                    }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(city.first, color = if (isFoc) Color.Black else Color.White, fontSize = 20.sp, fontFamily = state.currentFont.toFontFamily())
            }
            HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
        }
    }
}
