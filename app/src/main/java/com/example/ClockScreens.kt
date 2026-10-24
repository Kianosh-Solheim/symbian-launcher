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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

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
    // Back must always leave the clock, even if nothing on the page has keypad focus.
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }
    
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
    val currentTime = rememberTickingTime(1_000L)
    
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
        Formatters.format(dayPattern + datePattern, currentTime)
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
    // Give the page keypad focus on entry; otherwise the soft keys (* and #, incl. "Back") can't reach it.
    // The Alarm/Stopwatch/Timer tabs move focus to their first row afterwards.
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { tabFocus.requestFocus() } catch (_: Exception) { } }
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
                    .then(if (isSelected) Modifier.focusRequester(tabFocus) else Modifier)
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

// ───────────── Shared D-pad rows ─────────────

@Composable
private fun clockFg(focused: Boolean) = if (focused) Color.Black else win95Fg()

@Composable
fun ClockRow(
    title: String,
    state: LauncherState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    big: Boolean = false,
    focusRequester: FocusRequester? = null,
    onKey: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val foc by src.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (onKey != null) Modifier.onKeyEvent { if (it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) onKey(it) else false } else Modifier)
            .symbianHighlight(foc)
            .focusable(interactionSource = src)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(vertical = if (big) 10.dp else 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = clockFg(foc), fontSize = if (big) 34.sp else 18.sp, fontWeight = FontWeight.Bold,
                fontFamily = if (big) FontFamily.Monospace else state.currentFont.toFontFamily())
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = if (foc) Color.DarkGray else win95Fg().copy(alpha = 0.7f), fontSize = 13.sp,
                fontFamily = state.currentFont.toFontFamily())
        }
        if (value != null) Text(value, color = clockFg(foc), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
    }
    HorizontalDivider(color = win95Fg().copy(alpha = 0.2f), thickness = 1.dp)
}

private fun digitOf(e: androidx.compose.ui.input.key.KeyEvent): Int? {
    val c = e.nativeKeyEvent.keyCode
    return when (c) {
        in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> c - android.view.KeyEvent.KEYCODE_0
        in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> c - android.view.KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }
}

// ───────────── Alarms ─────────────

@Composable
fun AlarmTab(state: LauncherState, haptic: HapticFeedback, viewModel: LauncherViewModel, onAddAlarm: () -> Unit, onAlarmOptions: (AlarmItem) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val now = rememberTickingTime(30_000L)
    val next = remember(state.alarms, now) { AlarmStore.next(now) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) { } }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                next?.let { (a, at) -> "Next: ${a.label.ifBlank { "Alarm" }} ${untilText(at, now)}" } ?: "No alarms on",
                color = win95Fg().copy(alpha = 0.8f), fontSize = 14.sp, fontFamily = state.currentFont.toFontFamily(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            ClockRow("+  New alarm", state, focusRequester = first) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onAddAlarm()
            }
        }
        items(state.alarms, key = { it.id }) { alarm ->
            val sub = buildString {
                append(alarm.label.ifBlank { "Alarm" }); append(" · "); append(alarm.daysLabel())
                if (alarm.enabled) { append(" · "); append(untilText(alarm.nextTrigger(now), now)) }
                if (alarm.snoozeUntil > now) append(" (snoozed)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ClockRow(alarm.toDisplayString(), state, subtitle = sub, value = if (alarm.enabled) "On" else "Off", big = true) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updateAlarm(context, alarm.copy(enabled = !alarm.enabled))
                    }
                }
                val moreSrc = remember { MutableInteractionSource() }
                val moreFoc by moreSrc.collectIsFocusedAsState()
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onAlarmOptions(alarm) },
                    interactionSource = moreSrc, modifier = Modifier.symbianHighlight(moreFoc)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = clockFg(moreFoc))
                }
            }
        }
        item {
            Text("OK switches an alarm on/off · ⋮ to edit. Alarms ring even when the phone is locked or silent.",
                color = win95Fg().copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun AlarmOptionsScreen(state: LauncherState, alarm: AlarmItem, haptic: HapticFeedback, onEdit: () -> Unit, onDelete: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) { } }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
        Text("${alarm.toDisplayString()} · ${alarm.label}", color = win95Fg(), fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp))
        ClockRow("Edit", state, focusRequester = first) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onEdit() }
        ClockRow("Delete", state) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDelete() }
    }
}

@Composable
fun AlarmEditScreen(state: LauncherState, alarm: AlarmItem?, haptic: HapticFeedback, onSave: (AlarmItem) -> Unit) {
    val base = alarm ?: AlarmItem(hour = 7, minute = 0)
    var hour by remember { mutableStateOf(base.hour) }
    var minute by remember { mutableStateOf(base.minute) }
    var label by remember { mutableStateOf(base.label) }
    var days by remember { mutableStateOf(base.days) }
    var vibrate by remember { mutableStateOf(base.vibrate) }
    var snooze by remember { mutableStateOf(base.snoozeMin) }
    var typed by remember { mutableStateOf("") }
    val draft = base.copy(hour = hour, minute = minute, label = label, days = days, vibrate = vibrate, snoozeMin = snooze, snoozeUntil = 0)
    val now = rememberTickingTime(30_000L)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) { } }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            val shown = if (typed.isEmpty()) draft.toDisplayString()
                else typed.padEnd(4, '_').let { "${it.take(2)}:${it.drop(2)}" }
            ClockRow(shown, state, subtitle = "Type the time on the keypad, e.g. 0730", big = true, focusRequester = first,
                onKey = { e ->
                    val d = digitOf(e) ?: return@ClockRow false
                    val t = typed + d
                    typed = if (t.length >= 4) {
                        val h = t.take(2).toInt(); val m = t.substring(2, 4).toInt()
                        if (h < 24 && m < 60) { hour = h; minute = m }
                        ""
                    } else t
                    true
                }) { typed = "" }
            ClockRow("Hour", state, value = hour.toString().padStart(2, '0'), subtitle = "OK = +1") { hour = (hour + 1) % 24 }
            ClockRow("Minute", state, value = minute.toString().padStart(2, '0'), subtitle = "OK = +5") { minute = (minute / 5 * 5 + 5) % 60 }
            val presets = listOf(0, 0b1111111, 0b0011111, 0b1100000)
            ClockRow("Repeat", state, value = draft.daysLabel(), subtitle = "OK = once / every day / weekdays / weekends") {
                days = presets[(presets.indexOf(days) + 1).let { if (it <= 0 && days !in presets) 0 else it % presets.size }]
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                AlarmItem.DAY_SHORT.forEachIndexed { i, d ->
                    val src = remember { MutableInteractionSource() }
                    val foc by src.collectIsFocusedAsState()
                    val on = days and (1 shl i) != 0
                    Box(
                        Modifier.weight(1f).padding(2.dp)
                            .symbianHighlight(foc)
                            .border(1.dp, if (on) win95Fg() else win95Fg().copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .focusable(interactionSource = src)
                            .clickable(interactionSource = src, indication = LocalIndication.current) { days = days xor (1 shl i) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(d.take(2), color = if (foc) Color.Black else if (on) win95Fg() else win95Fg().copy(alpha = 0.4f),
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                    }
                }
            }
            OutlinedTextField(
                value = label, onValueChange = { label = it.take(40) }, singleLine = true,
                label = { Text("Name") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = win95Fg(), unfocusedTextColor = win95Fg(),
                    focusedLabelColor = win95Fg(), unfocusedLabelColor = win95Fg().copy(alpha = 0.6f),
                    focusedBorderColor = win95Fg(), unfocusedBorderColor = win95Fg().copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
            ClockRow("Snooze", state, value = "$snooze min") { snooze = listOf(5, 10, 15, 20, 30).let { it[(it.indexOf(snooze) + 1) % it.size] } }
            ClockRow("Vibrate", state, value = if (vibrate) "On" else "Off") { vibrate = !vibrate }
            ClockRow("Save", state, subtitle = "Rings ${untilText(draft.nextTrigger(now), now)}") {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSave(draft.copy(enabled = true))
            }
        }
    }
}

// ───────────── Stopwatch (keeps running while you use other screens) ─────────────

object StopwatchState {
    var running by mutableStateOf(false)
    var startedAt by mutableStateOf(0L)
    var banked by mutableStateOf(0L)
    val laps = mutableStateListOf<Long>()
    fun elapsed(now: Long = System.currentTimeMillis()) = banked + if (running) now - startedAt else 0L
}

private fun fmtStopwatch(ms: Long): String {
    val h = ms / 3_600_000; val m = (ms / 60_000) % 60; val s = (ms / 1000) % 60; val cs = (ms % 1000) / 10
    val core = "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.${cs.toString().padStart(2, '0')}"
    return if (h > 0) "$h:$core" else core
}

@Composable
fun StopwatchTab(state: LauncherState, haptic: HapticFeedback) {
    val sw = StopwatchState
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sw.running) {
        while (sw.running) { androidx.compose.runtime.withFrameMillis { }; now = System.currentTimeMillis() }
        now = System.currentTimeMillis()
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) { } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Text(fmtStopwatch(sw.elapsed(now)), color = win95Fg(), fontSize = 44.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 16.dp))
            ClockRow(if (sw.running) "Pause" else if (sw.elapsed(now) > 0) "Resume" else "Start", state, focusRequester = first) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val t = System.currentTimeMillis()
                if (sw.running) { sw.banked += t - sw.startedAt; sw.running = false } else { sw.startedAt = t; sw.running = true }
            }
            ClockRow(if (sw.running) "Lap" else "Reset", state) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (sw.running) sw.laps.add(0, sw.elapsed()) else { sw.banked = 0; sw.laps.clear(); now = System.currentTimeMillis() }
            }
        }
        items(sw.laps.size) { i ->
            val total = sw.laps[i]
            val prev = sw.laps.getOrNull(i + 1) ?: 0L
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text("Lap ${sw.laps.size - i}", color = win95Fg().copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                Text("+" + fmtStopwatch(total - prev), color = win95Fg(), fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(12.dp))
                Text(fmtStopwatch(total), color = win95Fg().copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ───────────── Timer (rings through the alarm service, even when the screen is off) ─────────────

private fun fmtTimer(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600; val m = (total / 60) % 60; val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

@Composable
fun TimerTab(state: LauncherState, haptic: HapticFeedback) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { AlarmStore.load(context) }
    val t by AlarmStore.timer.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(t.running) { while (t.running) { now = System.currentTimeMillis(); delay(250) }; now = System.currentTimeMillis() }
    var typed by remember { mutableStateOf("") }
    val left = t.left(now)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) { } }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            val fg = win95Fg()
            Box(Modifier.padding(vertical = 10.dp).size(150.dp), contentAlignment = Alignment.Center) {
                val frac = if (t.length > 0) (left.toFloat() / t.length).coerceIn(0f, 1f) else 0f
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(color = fg.copy(alpha = 0.2f), style = Stroke(width = 5.dp.toPx()))
                    drawArc(color = fg, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Text(
                    if (typed.isNotEmpty()) typed.padStart(4, '0').let { "${it.dropLast(2)}:${it.takeLast(2)}" } else fmtTimer(left),
                    color = fg, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
            }
            ClockRow(if (t.running) "Pause" else if (t.pausedLeft > 0) "Resume" else "Start", state,
                subtitle = if (!t.running && t.pausedLeft == 0L) "Type minutes and seconds, e.g. 1500 = 15 min" else null,
                focusRequester = first,
                onKey = { e ->
                    if (t.running) return@ClockRow false
                    val d = digitOf(e) ?: return@ClockRow false
                    typed = (typed + d).takeLast(6).trimStart('0')
                    val v = typed.padStart(6, '0')
                    val ms = (v.take(2).toLong() * 3600 + v.substring(2, 4).toLong() * 60 + v.takeLast(2).toLong()) * 1000
                    if (ms > 0) AlarmStore.setTimerLength(context, ms)
                    true
                }) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                typed = ""
                if (t.running) AlarmStore.pauseTimer(context) else AlarmStore.startTimer(context)
            }
            ClockRow("Reset", state) { typed = ""; AlarmStore.resetTimer(context) }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(1, 3, 5, 10, 15, 30).forEach { min ->
                    val src = remember { MutableInteractionSource() }
                    val foc by src.collectIsFocusedAsState()
                    Box(
                        Modifier.weight(1f).padding(2.dp).symbianHighlight(foc)
                            .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .focusable(interactionSource = src)
                            .clickable(interactionSource = src, indication = LocalIndication.current) {
                                typed = ""; AlarmStore.resetTimer(context); AlarmStore.setTimerLength(context, min * 60_000L)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("${min}m", color = if (foc) Color.Black else fg, fontSize = 13.sp) }
                }
            }
            Text("The timer rings even if you leave this screen or the phone locks.", color = fg.copy(alpha = 0.55f), fontSize = 12.sp,
                modifier = Modifier.padding(8.dp))
        }
    }
}
