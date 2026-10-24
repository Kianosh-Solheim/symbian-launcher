package com.example
import androidx.compose.foundation.focusable
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.itemsIndexed

@Composable
fun SettingsMenuItem(
    title: String,
    icon: ImageVector? = null,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    isFirst: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val win95 = LocalWin95.current
    
    LaunchedEffect(Unit) {
        if (isFirst) {
            try { focusRequester.requestFocus() } catch (e: Exception) {}
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isFirst) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (win95) Modifier.win95ListHighlight(isFocused) else Modifier.symbianHighlight(isFocused))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = if (win95) (if (isFocused) Color.White else Color.Black) else if (isFocused) Color.Black else Color.White,
            fontSize = 20.sp,
            fontWeight = if (win95) FontWeight.Normal else FontWeight.Bold,
            modifier = Modifier.weight(1f),
            style = TextStyle(shadow = if (isFocused || win95) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
        )
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (win95) (if (isFocused) Color.White else Color.Black) else if (isFocused) Color.Black else Color.White
            )
        }
    }
    DashedDivider()
}


@Composable
fun SettingsSliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            )
            Text(
                text = "${(value * 100).toInt()}%",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = {
                onValueChange(it)
            },
            onValueChangeFinished = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    checked: Boolean,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    isFirst: Boolean = false,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val win95 = LocalWin95.current
    
    LaunchedEffect(Unit) {
        if (isFirst) {
            try { focusRequester.requestFocus() } catch (e: Exception) {}
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isFirst) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (win95) Modifier.win95ListHighlight(isFocused) else Modifier.symbianHighlight(isFocused))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (win95) (if (isFocused) Color.White else Color.Black) else if (isFocused) Color.Black else Color.White,
                fontSize = 20.sp,
                fontWeight = if (win95) FontWeight.Normal else FontWeight.Bold,
                style = TextStyle(shadow = if (isFocused || win95) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
            )
            if (subtitle != null && subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = if (win95) (if (isFocused) Color(0xFFDFDFDF) else Color(0xFF404040)) else if (isFocused) Color(0xFF333333) else Color(0xFFAAAAAA),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (win95) Win95Checkbox(checked) else Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.Gray
            )
        )
    }
    DashedDivider()
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = if (LocalWin95.current) Color(0xFF000080) else Color(0xFFAAAAAA),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsRootView(state: LauncherState, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsMenuItem("Global Settings", Icons.Default.ChevronRight, haptic, isFirst = true) { onNavigate(Screen.SETTINGS_GLOBAL) }
            SettingsMenuItem("Home Screen", Icons.Default.ChevronRight, haptic) { onNavigate(Screen.SETTINGS_HOME_SCREEN) }
            SettingsMenuItem("Desktop Mode", Icons.Default.ChevronRight, haptic) { onNavigate(Screen.SETTINGS_DESKTOP) }
            SettingsMenuItem("Backup & Restore", Icons.Default.ChevronRight, haptic) { onNavigate(Screen.SETTINGS_BACKUP) }
            SettingsMenuItem("Android System Settings", Icons.Default.Settings, haptic) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }
}

@Composable
fun SettingsHomeScreen(state: LauncherState, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, onNavigate: (Screen) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsMenuItem("Status Bar", Icons.Default.ChevronRight, haptic, isFirst = true) { onNavigate(Screen.SETTINGS_STATUS_BAR) }
            SettingsMenuItem("Home Screen Content", Icons.Default.ChevronRight, haptic) { onNavigate(Screen.SETTINGS_HOME_CONTENT) }
        }
    }
}

@Composable
fun SettingsStatusBar(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, onNavigate: (Screen) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsToggleItem("Show Android status bar", state.showAndroidStatusBar, haptic, isFirst = true) { viewModel.toggleShowAndroidStatusBar() }
            SettingsToggleItem("Show launcher status bar", state.showLauncherStatusBar, haptic) { viewModel.toggleShowLauncherStatusBar() }
            SettingsMenuItem("Weather", Icons.Default.ChevronRight, haptic) { onNavigate(Screen.SETTINGS_WEATHER) }
            SettingsMenuItem("Time & Date", Icons.Default.ChevronRight, haptic) { onNavigate(Screen.SETTINGS_TIME_DATE) }
        }
    }
}

@Composable
fun SettingsWeather(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsToggleItem("Show weather", state.showWeather, haptic, isFirst = true) { checked -> viewModel.updateState { it.copy(showWeather = checked) } }
            if (state.showWeather) {
                SettingsSectionHeader("Location")
                var showPlaceDialog by remember { mutableStateOf(false) }
                val methodLabel = when (state.locationMethod) {
                    LocationMethod.GPS -> "Phone location"
                    LocationMethod.IP -> "Approximate (internet)"
                    LocationMethod.MANUAL -> "Fixed place"
                }
                SettingsMenuItem("Location method: $methodLabel", null, haptic) { 
                    val next = LocationMethod.values()[(state.locationMethod.ordinal + 1) % LocationMethod.values().size]
                    viewModel.updateState { it.copy(locationMethod = next) }
                    if (next == LocationMethod.MANUAL && state.manualLocationSearch.isBlank()) showPlaceDialog = true else viewModel.loadWeather()
                }
                if (state.locationMethod == LocationMethod.MANUAL) {
                    SettingsMenuItem("Place: ${state.manualLocationSearch.ifBlank { "not set" }}", null, haptic) { showPlaceDialog = true }
                }
                if (showPlaceDialog) {
                    var text by remember { mutableStateOf(state.manualLocationSearch) }
                    val fieldFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { try { fieldFocus.requestFocus() } catch (_: Exception) { } }
                    AlertDialog(
                        onDismissRequest = { showPlaceDialog = false },
                        title = { Text("Weather place") },
                        text = {
                            Column {
                                Text("Town or city, e.g. Bergen or Voss", fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = text, onValueChange = { text = it }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus)
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.updateState { it.copy(manualLocationSearch = text.trim(), locationMethod = LocationMethod.MANUAL) }
                                viewModel.loadWeather()
                                showPlaceDialog = false
                            }) { Text("Save") }
                        },
                        dismissButton = { TextButton(onClick = { showPlaceDialog = false }) { Text("Cancel") } }
                    )
                }
                val locName = state.weatherLocationName ?: "Unknown"
                SettingsMenuItem("Current weather location: $locName", null, haptic) {
                    // Read only for now or refresh
                    viewModel.loadWeather()
                }
                SettingsToggleItem("Update only when clicked", state.weatherUpdateOnlyWhenClicked, haptic) { checked -> 
                    viewModel.updateState { it.copy(weatherUpdateOnlyWhenClicked = checked) }
                    viewModel.startWeatherLoop()
                }
                
                if (!state.weatherUpdateOnlyWhenClicked) {
                    SettingsMenuItem("Update interval: ${state.weatherUpdateInterval.label}", null, haptic) {
                        val next = WeatherUpdateInterval.values()[(state.weatherUpdateInterval.ordinal + 1) % WeatherUpdateInterval.values().size]
                        viewModel.updateState { it.copy(weatherUpdateInterval = next) }
                        viewModel.startWeatherLoop()
                    }
                }
                
                SettingsSectionHeader("Provider & Unit")
                SettingsMenuItem("Weather provider: ${if(state.weatherProvider == WeatherProvider.MET_NORWAY) "MET Norway" else "Open-Meteo"}", null, haptic) { 
                    val next = WeatherProvider.values()[(state.weatherProvider.ordinal + 1) % WeatherProvider.values().size]
                    viewModel.updateState { it.copy(weatherProvider = next) }
                    viewModel.loadWeather()
                }
                SettingsMenuItem("Temperature unit: ${if(state.tempUnit == TempUnit.CELSIUS) "Celsius" else "Fahrenheit"}", null, haptic) { 
                    val next = if(state.tempUnit == TempUnit.CELSIUS) TempUnit.FAHRENHEIT else TempUnit.CELSIUS
                    viewModel.updateState { it.copy(tempUnit = next) }
                    viewModel.loadWeather()
                }
                SettingsMenuItem("Wind unit: ${state.windSpeedUnit.name}", null, haptic) { 
                    val next = WindSpeedUnit.values()[(state.windSpeedUnit.ordinal + 1) % WindSpeedUnit.values().size]
                    viewModel.updateState { it.copy(windSpeedUnit = next) }
                }
                SettingsMenuItem("Daily view language: ${state.weatherDailyLanguage.label}", null, haptic) { 
                    val next = WeatherDailyLanguage.values()[(state.weatherDailyLanguage.ordinal + 1) % WeatherDailyLanguage.values().size]
                    viewModel.updateState { it.copy(weatherDailyLanguage = next) }
                }
            }
        }
    }
}

@Composable
fun SettingsTimeDate(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSectionHeader("Time")
            SettingsToggleItem("Show time", state.showTime, haptic, isFirst = true) { checked -> viewModel.updateState { it.copy(showTime = checked) } }
            if (state.showTime) {
                SettingsMenuItem("Clock type: ${if(state.clockType == ClockType.DIGITAL) "Digital" else "Analogue"}", null, haptic) {
                    val next = if(state.clockType == ClockType.DIGITAL) ClockType.ANALOGUE else ClockType.DIGITAL
                    viewModel.updateState { it.copy(clockType = next) }
                }
                SettingsMenuItem("Time format: ${if(state.is24HourFormat) "24-hour" else "12-hour"}", null, haptic) {
                    viewModel.updateState { it.copy(is24HourFormat = !it.is24HourFormat) }
                }
                SettingsToggleItem("Show seconds", state.showSeconds, haptic) { checked ->
                    viewModel.updateState { it.copy(showSeconds = checked) }
                }
            }
            
            SettingsMenuItem("Clock app: ${ClockApps.label(LocalContext.current, ClockApps.chosen(LocalContext.current))}", null, haptic) {
                ClockApps.askNeeded.value = true
            }

            SettingsSectionHeader("Date")
            SettingsToggleItem("Show date", state.showDate, haptic) { checked -> viewModel.updateState { it.copy(showDate = checked) } }
            if (state.showDate) {
                SettingsMenuItem("Date format: ${state.dateFormat.label}", null, haptic) {
                    val next = DateFormatOption.values()[(state.dateFormat.ordinal + 1) % DateFormatOption.values().size]
                    viewModel.updateState { it.copy(dateFormat = next) }
                }
                SettingsToggleItem("Show weekday", state.showWeekday, haptic) { checked -> viewModel.updateState { it.copy(showWeekday = checked) } }
                if (state.showWeekday) {
                    SettingsMenuItem("Weekday format: ${if(state.weekdayFormat == WeekdayFormat.FULL) "Friday" else "Fri"}", null, haptic) {
                        val next = if(state.weekdayFormat == WeekdayFormat.FULL) WeekdayFormat.SHORT else WeekdayFormat.FULL
                        viewModel.updateState { it.copy(weekdayFormat = next) }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsHomeContent(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, onNavigate: (Screen) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsMenuItem("T9 Quick Search", Icons.Default.ChevronRight, haptic, isFirst = true) { onNavigate(Screen.SETTINGS_T9_SEARCH) }
            SettingsMenuItem("Pinned Apps", Icons.Default.ChevronRight, haptic) { onNavigate(Screen.SETTINGS_PINNED_APPS) }
            SettingsMenuItem("Messages", Icons.Default.ChevronRight, haptic) { onNavigate(Screen.SETTINGS_MESSAGES) }
            SettingsMenuItem("Email", Icons.Default.ChevronRight, haptic) { onNavigate(Screen.SETTINGS_EMAIL) }
            SettingsMenuItem("Calendar", Icons.Default.ChevronRight, haptic) { onNavigate(Screen.SETTINGS_CALENDAR) }
        }
    }
}

@Composable
fun SettingsT9Search(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsToggleItem("Enable", state.enableT9Search, haptic, isFirst = true) { checked -> viewModel.updateState { it.copy(enableT9Search = checked) } }
            if (state.enableT9Search) {
                SettingsSectionHeader("Search")
                SettingsToggleItem("Apps", state.t9SearchApps, haptic) { checked -> viewModel.updateState { it.copy(t9SearchApps = checked) } }
                SettingsToggleItem("Contacts", state.t9SearchContacts, haptic) { checked -> viewModel.updateState { it.copy(t9SearchContacts = checked) } }
            }
        }
    }
}

@Composable
fun SettingsCalendar(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    LaunchedEffect(Unit) {
        viewModel.loadCalendarData(force = true)
    }

    val allSelected = state.selectedCalendarIds == null
    val availableCount = state.availableCalendars.size
    val selectedCount = if (allSelected) availableCount else state.availableCalendars.count { state.selectedCalendarIds?.contains(it.id) == true }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsToggleItem("Show Calendar section", state.showCalendarSection, haptic, isFirst = true) { checked -> 
                viewModel.updateState { it.copy(showCalendarSection = checked) } 
            }
            SettingsSectionHeader("Calendars for Agenda")
        }

        if (state.availableCalendars.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No calendars found on device",
                        color = win95Fg(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ensure calendar permissions are granted and accounts are synced in system settings.",
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    SettingsMenuItem("Refresh Calendars", Icons.Default.Refresh, haptic) {
                        viewModel.loadCalendarData(force = true)
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$selectedCount of $availableCount selected",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp
                    )
                    Row {
                        if (!allSelected || selectedCount < availableCount) {
                            Text(
                                text = "Select All",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.selectAllCalendars()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (selectedCount > 0) {
                            Text(
                                text = "Deselect All",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.deselectAllCalendars()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                DashedDivider()
            }

            itemsIndexed(state.availableCalendars) { _, cal ->
                val isSelected = state.selectedCalendarIds == null || state.selectedCalendarIds.contains(cal.id)
                val calColor = cal.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

                SettingsToggleItem(
                    title = cal.name,
                    subtitle = cal.accountName.ifBlank { null },
                    checked = isSelected,
                    haptic = haptic,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(calColor, shape = CircleShape)
                        )
                    }
                ) {
                    viewModel.toggleCalendarSelection(cal.id)
                }
            }
        }
    }
}

// Ensure the old SettingsManageApps / SettingsPinnedApps / SettingsMessagingApps are available or re-implemented below if they were wiped

@Composable
fun SettingsPinnedApps(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, onNavigate: (Screen) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsToggleItem("Show pinned apps", state.showPinnedAppsSection, haptic, isFirst = true) { checked -> 
                viewModel.updateState { it.copy(showPinnedAppsSection = checked) } 
            }
        }
        if (state.showPinnedAppsSection) {
            item {
                SettingsMenuItem("Reorder pinned apps", null, haptic) {
                    onNavigate(Screen.SETTINGS_REORDER_PINNED)
                }
                SettingsSectionHeader("Choose pinned apps")
            }
            itemsIndexed(state.allApps) { index, app ->
                val isPinned = state.pinnedApps.any { it.packageName == app.packageName }
                SettingsToggleItem(
                    title = app.name, 
                    checked = isPinned, 
                    haptic = haptic,
                    icon = {
                        app.icon?.let { iconDrawable ->
                            androidx.compose.foundation.Image(
                                bitmap = iconDrawable.toCachedImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp * state.iconScale)
                            )
                        } ?: Spacer(modifier = Modifier.size(32.dp * state.iconScale))
                    }
                ) { checked ->
                    viewModel.togglePinned(app)
                }
            }
        }
    }
}

@Composable
fun SettingsReorderPinned(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSectionHeader("Reorder pinned apps")
        }
        itemsIndexed(state.pinnedApps) { index, app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    app.icon?.let { iconDrawable ->
                        androidx.compose.foundation.Image(
                            bitmap = iconDrawable.toCachedImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp * state.iconScale)
                        )
                    } ?: Spacer(modifier = Modifier.size(32.dp * state.iconScale))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = app.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                }
                Row {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            viewModel.movePinnedAppUp(app)
                        },
                        enabled = index > 0,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Move Up"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            viewModel.movePinnedAppDown(app)
                        },
                        enabled = index < state.pinnedApps.size - 1,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move Down"
                        )
                    }
                }
            }
            DashedDivider()
        }
    }
}

@Composable
fun SettingsMessages(
    state: LauncherState, 
    viewModel: LauncherViewModel, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onNavigate: (Screen) -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val detectedApps = remember(state.allApps) {
        state.allApps.filter { MessagingUtils.isMessagingApp(it, context) }
    }
    val detectedPackageNames = remember(detectedApps) {
        detectedApps.map { it.packageName }.toSet()
    }
    val otherApps = remember(state.allApps, detectedPackageNames) {
        state.allApps.filter { !detectedPackageNames.contains(it.packageName) }
    }
    
    val filteredDetectedApps = remember(detectedApps, searchQuery) {
        if (searchQuery.isBlank()) detectedApps
        else detectedApps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }
    
    val filteredOtherApps = remember(otherApps, searchQuery) {
        if (searchQuery.isBlank()) otherApps
        else otherApps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsToggleItem("Show Messages section", state.showMessagesSection, haptic, isFirst = true) { checked -> 
                viewModel.updateState { it.copy(showMessagesSection = checked) } 
            }
        }

        if (state.showMessagesSection) {
            item {
                SettingsSectionHeader("Quick Actions")
                SettingsMenuItem("Reorder messaging apps", Icons.Default.Reorder, haptic) {
                    onNavigate(Screen.SETTINGS_REORDER_MESSAGES)
                }
                SettingsMenuItem("Auto-Detect & Select All", Icons.Default.AutoFixHigh, haptic) {
                    val allDetectedPkgs = detectedApps.map { it.packageName }
                    val newSet = (state.messagingApps + allDetectedPkgs).distinct()
                    viewModel.setMessagingApps(newSet)
                }
                SettingsMenuItem("Clear All Selections", Icons.Default.ClearAll, haptic) {
                    viewModel.setMessagingApps(emptyList())
                }
            }

            // Search filter field
            item {
                SettingsSectionHeader("Search Apps")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter installed apps...", color = Color.Gray, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.LightGray) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.LightGray)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = win95Fg(),
                            unfocusedTextColor = win95Fg(),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Auto-detected apps section
            item {
                SettingsSectionHeader("Auto-Detected Messaging & Phone Apps (${detectedApps.size})")
            }

            if (filteredDetectedApps.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "No messaging apps auto-detected. Select from installed apps below." else "No matching detected apps.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                itemsIndexed(filteredDetectedApps) { index, app ->
                    val isSelected = state.messagingApps.contains(app.packageName)
                    val isPhone = MessagingUtils.isPhoneOrDialerApp(app, context)
                    val title = if (isPhone) "${app.name} (Phone / Missed calls)" else app.name
                    val subtitle = if (isPhone) "Missed calls will show in Messages" else "Detected as messaging app"

                    SettingsToggleItem(
                        title = title,
                        checked = isSelected,
                        haptic = haptic,
                        subtitle = subtitle,
                        icon = {
                            app.icon?.let { iconDrawable ->
                                androidx.compose.foundation.Image(
                                    bitmap = iconDrawable.toCachedImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp * state.iconScale)
                                )
                            } ?: Spacer(modifier = Modifier.size(32.dp * state.iconScale))
                        }
                    ) {
                        viewModel.toggleMessagingApp(app.packageName)
                    }
                }
            }

            // All Other Installed Apps
            item {
                SettingsSectionHeader("All Other Installed Apps (${otherApps.size})")
            }

            if (filteredOtherApps.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "No other apps found." else "No matching apps found.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                itemsIndexed(filteredOtherApps) { index, app ->
                    val isSelected = state.messagingApps.contains(app.packageName)
                    SettingsToggleItem(
                        title = app.name,
                        checked = isSelected,
                        haptic = haptic,
                        subtitle = app.packageName,
                        icon = {
                            app.icon?.let { iconDrawable ->
                                androidx.compose.foundation.Image(
                                    bitmap = iconDrawable.toCachedImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp * state.iconScale)
                                )
                            } ?: Spacer(modifier = Modifier.size(32.dp * state.iconScale))
                        }
                    ) {
                        viewModel.toggleMessagingApp(app.packageName)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsReorderMessages(
    state: LauncherState, 
    viewModel: LauncherViewModel, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val messagingAppList = remember(state.messagingApps, state.allApps) {
        state.allApps.filter { state.messagingApps.contains(it.packageName) }
            .sortedBy { state.messagingApps.indexOf(it.packageName) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSectionHeader("Reorder messaging apps (${messagingAppList.size})")
        }
        if (messagingAppList.isEmpty()) {
            item {
                Text(
                    text = "No messaging apps selected. Enable some messaging apps first in the Messages settings.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else {
            itemsIndexed(messagingAppList, key = { _, app -> app.packageName }) { index, app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        app.icon?.let { iconDrawable ->
                            androidx.compose.foundation.Image(
                                bitmap = iconDrawable.toCachedImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp * state.iconScale)
                            )
                        } ?: Spacer(modifier = Modifier.size(32.dp * state.iconScale))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = app.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Text(
                                text = app.packageName,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Row {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                viewModel.moveMessagingAppUp(app)
                            },
                            enabled = index > 0,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Move Up"
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                viewModel.moveMessagingAppDown(app)
                            },
                            enabled = index < messagingAppList.size - 1,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move Down"
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
fun SettingsEmail(
    state: LauncherState, 
    viewModel: LauncherViewModel, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onNavigate: (Screen) -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val detectedApps = remember(state.allApps) {
        state.allApps.filter { MessagingUtils.isEmailApp(it, context) }
    }
    val detectedPackageNames = remember(detectedApps) {
        detectedApps.map { it.packageName }.toSet()
    }
    val otherApps = remember(state.allApps, detectedPackageNames) {
        state.allApps.filter { !detectedPackageNames.contains(it.packageName) }
    }
    
    val filteredDetectedApps = remember(detectedApps, searchQuery) {
        if (searchQuery.isBlank()) detectedApps
        else detectedApps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }
    
    val filteredOtherApps = remember(otherApps, searchQuery) {
        if (searchQuery.isBlank()) otherApps
        else otherApps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsToggleItem("Show Email section", state.showEmailSection, haptic, isFirst = true) { checked -> 
                viewModel.updateState { it.copy(showEmailSection = checked) } 
            }
        }

        if (state.showEmailSection) {
            item {
                SettingsSectionHeader("Quick Actions")
                SettingsMenuItem("Reorder email shortcuts", Icons.Default.Reorder, haptic) {
                    onNavigate(Screen.SETTINGS_REORDER_EMAIL)
                }
                SettingsMenuItem("Auto-Detect & Select All", Icons.Default.AutoFixHigh, haptic) {
                    val allDetectedPkgs = detectedApps.map { it.packageName }
                    val newSet = (state.emailApps + allDetectedPkgs).distinct()
                    viewModel.setEmailApps(newSet)
                }
                SettingsMenuItem("Clear All Selections", Icons.Default.ClearAll, haptic) {
                    viewModel.setEmailApps(emptyList())
                }
            }

            // Search filter field
            item {
                SettingsSectionHeader("Search Apps")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter email & installed apps...", color = Color.Gray, fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.LightGray) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.LightGray)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Detected Email Apps
            item {
                SettingsSectionHeader("Detected Email Apps (${detectedApps.size})")
            }

            if (filteredDetectedApps.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "No email apps auto-detected (e.g. Thunderbird, Proton Mail, Gmail, Outlook). Select from all installed apps below." else "No matching detected email apps.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                itemsIndexed(filteredDetectedApps) { index, app ->
                    val isSelected = state.emailApps.contains(app.packageName)

                    SettingsToggleItem(
                        title = app.name,
                        checked = isSelected,
                        haptic = haptic,
                        subtitle = "Email app shortcut • ${app.packageName}",
                        icon = {
                            app.icon?.let { iconDrawable ->
                                androidx.compose.foundation.Image(
                                    bitmap = iconDrawable.toCachedImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp * state.iconScale)
                                )
                            } ?: Spacer(modifier = Modifier.size(32.dp * state.iconScale))
                        }
                    ) {
                        viewModel.toggleEmailApp(app.packageName)
                    }
                }
            }

            // All Other Installed Apps
            item {
                SettingsSectionHeader("All Other Installed Apps (${otherApps.size})")
            }

            if (filteredOtherApps.isEmpty()) {
                item {
                    Text(
                        text = if (searchQuery.isBlank()) "No other apps found." else "No matching apps found.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                itemsIndexed(filteredOtherApps) { index, app ->
                    val isSelected = state.emailApps.contains(app.packageName)
                    SettingsToggleItem(
                        title = app.name,
                        checked = isSelected,
                        haptic = haptic,
                        subtitle = app.packageName,
                        icon = {
                            app.icon?.let { iconDrawable ->
                                androidx.compose.foundation.Image(
                                    bitmap = iconDrawable.toCachedImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp * state.iconScale)
                                )
                            } ?: Spacer(modifier = Modifier.size(32.dp * state.iconScale))
                        }
                    ) {
                        viewModel.toggleEmailApp(app.packageName)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsReorderEmail(
    state: LauncherState, 
    viewModel: LauncherViewModel, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val emailAppList = remember(state.emailApps, state.allApps) {
        state.allApps.filter { state.emailApps.contains(it.packageName) }
            .sortedBy { state.emailApps.indexOf(it.packageName) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSectionHeader("Reorder email shortcuts (${emailAppList.size})")
        }
        if (emailAppList.isEmpty()) {
            item {
                Text(
                    text = "No email apps selected. Enable some email apps first in Email settings.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        } else {
            itemsIndexed(emailAppList, key = { _, app -> app.packageName }) { index, app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        app.icon?.let { iconDrawable ->
                            androidx.compose.foundation.Image(
                                bitmap = iconDrawable.toCachedImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp * state.iconScale)
                            )
                        } ?: Spacer(modifier = Modifier.size(32.dp * state.iconScale))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = app.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Text(
                                text = app.packageName,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Row {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                viewModel.moveEmailAppUp(app)
                            },
                            enabled = index > 0,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Move Up"
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                viewModel.moveEmailAppDown(app)
                            },
                            enabled = index < emailAppList.size - 1,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move Down"
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
fun SettingsGlobal(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, onNavigate: (Screen) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSectionHeader("Appearance")
            SettingsMenuItem("Theme: ${state.currentTheme.displayName}", null, haptic, isFirst = true) {
                val values = AppTheme.values()
                val nextIndex = (state.currentTheme.ordinal + 1) % values.size
                viewModel.setTheme(values[nextIndex])
            }
            if (state.currentTheme == AppTheme.CUSTOM) {
                SettingsMenuItem("Customize Colors...", null, haptic) {
                    onNavigate(Screen.SETTINGS_CUSTOM_THEME)
                }
            }
            
            SettingsMenuItem("Font: ${state.currentFont.displayName}", null, haptic) {
                val fonts = AppFont.values()
                val nextIndex = (state.currentFont.ordinal + 1) % fonts.size
                viewModel.setFont(fonts[nextIndex])
            }
            
            val currentPackName = state.availableIconPacks.find { it.packageName == state.currentIconPack }?.name ?: "Default"
            SettingsMenuItem("Icon Pack: $currentPackName", null, haptic) {
                onNavigate(Screen.SETTINGS_ICON_PACK)
            }
            if (state.currentIconPack != null) {
                SettingsToggleItem("Use pack on Home Screen", state.useIconPackForHub, haptic) {
                    viewModel.setUseIconPackForHub(it)
                }
            }
            
            SettingsMenuItem("Text Scale: ${state.textScale}x", null, haptic) {
                val scales = listOf(0.8f, 1.0f, 1.2f, 1.5f)
                val currentIndex = scales.indexOf(state.textScale).takeIf { it >= 0 } ?: 1
                val nextIndex = (currentIndex + 1) % scales.size
                viewModel.setTextScale(scales[nextIndex])
            }
            
            SettingsMenuItem("Icon Scale: ${state.iconScale}x", null, haptic) {
                val scales = listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f)
                val currentIndex = scales.indexOf(state.iconScale).takeIf { it >= 0 } ?: 1
                val nextIndex = (currentIndex + 1) % scales.size
                viewModel.setIconScale(scales[nextIndex])
            }
            
            SettingsSectionHeader("Haptics & Sounds")
            SettingsToggleItem("Haptic Feedback", state.hapticFeedbackEnabled, haptic, isFirst = true) {
                viewModel.toggleHapticFeedback()
            }
            SettingsToggleItem("Symbian UI Sounds", state.symbianSoundsEnabled, haptic) {
                viewModel.toggleSymbianSounds()
            }

            SettingsSectionHeader("Background & Wallpaper")
            SettingsToggleItem("Show system wallpaper", state.showWallpaper, haptic, isFirst = true) {
                viewModel.setShowWallpaper(it)
            }
            if (state.showWallpaper) {
                SettingsSliderItem(
                    title = "Wallpaper Transparency",
                    value = state.wallpaperTransparency,
                    onValueChange = { viewModel.setWallpaperTransparency(it) },
                    haptic = haptic
                )
            }
        }
    }
}


@Composable
fun SettingsCustomTheme(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSectionHeader("Custom Theme Colors")
            
            // Background Color
            val bgColors = listOf(0xFF121212, 0xFF3E2723, 0xFF1A237E, 0xFF004D40, 0xFF212121, 0xFFFFFFFF, 0xFFF5F5F5, 0xFFE8F5E9, 0xFFFFF8E1, 0xFFFCE4EC, 0xFFE0F7FA, 0xFF263238)
            val bgCurrent = state.customBgColor ?: 0xFF121212
            ColorPickerRow("Background Color", bgColors, bgCurrent, haptic) { 
                viewModel.setCustomColor("customBgColor", it) 
            }
            
            // Surface / Bar Color
            val surfaceColors = listOf(0xFF1E1E1E, 0xFF4E342E, 0xFF283593, 0xFF00695C, 0xFF424242, 0xFFE0E0E0, 0xFFEEEEEE, 0xFFC8E6C9, 0xFFFFECB3, 0xFFF8BBD0, 0xFFB2EBF2, 0xFF37474F)
            val surfaceCurrent = state.customBarColor ?: 0xFF1E1E1E
            ColorPickerRow("Surface Color", surfaceColors, surfaceCurrent, haptic) {
                viewModel.setCustomColor("customBarColor", it)
            }
            
            // Text Color
            val textColors = listOf(0xFFFFFFFF, 0xFFFFCC80, 0xFF81D4FA, 0xFFA5D6A7, 0xFFEEEEEE, 0xFF000000, 0xFF212121, 0xFF424242, 0xFF1B5E20, 0xFFB71C1C, 0xFF0D47A1, 0xFFF57F17)
            val textCurrent = state.customTextColor ?: 0xFFFFFFFF
            ColorPickerRow("Text Color", textColors, textCurrent, haptic) {
                viewModel.setCustomColor("customTextColor", it)
            }
            
            // Accent Color
            val accentColors = listOf(0xFFBB86FC, 0xFFFF7043, 0xFF29B6F6, 0xFF26A69A, 0xFFFFCA28, 0xFFD32F2F, 0xFFE91E63, 0xFF9C27B0, 0xFF3F51B5, 0xFF00BCD4, 0xFF4CAF50, 0xFFFF9800)
            val accentCurrent = state.customAccentColor ?: 0xFFBB86FC
            ColorPickerRow("Accent Color", accentColors, accentCurrent, haptic) {
                viewModel.setCustomColor("customAccentColor", it)
            }
            
            // Highlight Color
            val highlightColors = listOf(0xFF3700B3, 0xFFD84315, 0xFF0277BD, 0xFF00695C, 0xFFFF8F00, 0xFFC2185B, 0xFF880E4F, 0xFF4A148C, 0xFF1A237E, 0xFF006064, 0xFF1B5E20, 0xFFE65100)
            val highlightCurrent = state.customHighlightColor ?: 0xFF3700B3
            ColorPickerRow("Highlight Color", highlightColors, highlightCurrent, haptic) {
                viewModel.setCustomColor("customHighlightColor", it)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ColorPickerRow(
    title: String,
    colors: List<Long>,
    selectedColor: Long,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onColorSelected: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(colors.size) { index ->
                val colorLong = colors[index]
                val isSelected = colorLong == selectedColor
                val color = Color(colorLong)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                onColorSelected(colorLong)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary, 
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsIconPack(state: LauncherState, viewModel: LauncherViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, onNavigate: (Screen) -> Unit) {
    var previewPack by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(state.currentIconPack) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val packManager = androidx.compose.runtime.remember { com.example.IconPackManager(context) }
    
    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SettingsSectionHeader("Icon Pack")
            
            // Preview Section
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.2f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Visual Preview", color = win95Fg(), fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = state.currentFont.toFontFamily())
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                    
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val previewApps = androidx.compose.runtime.remember(state.allApps) { state.allApps.shuffled().take(4) }
                        previewApps.forEach { appInfo ->
                            val previewIcon = androidx.compose.runtime.remember(previewPack) {
                                packManager.getIcon(previewPack, appInfo.packageName, appInfo.activityName, appInfo.icon)
                            }
                            
                            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.foundation.Image(
                                    bitmap = previewIcon.toCachedImageBitmap(),
                                    contentDescription = appInfo.name,
                                    modifier = Modifier.size(48.dp * state.iconScale)
                                )
                            }
                        }
                    }
                }
            }
            
            SettingsSectionHeader("Select Pack")
            val packs = listOf<String?>(null) + state.availableIconPacks.map { it.packageName }
            packs.forEachIndexed { index, packPkg ->
                val packName = if (packPkg == null) "Default" else state.availableIconPacks.find { it.packageName == packPkg }?.name ?: "Unknown"
                
                val intSource = androidx.compose.runtime.remember { MutableInteractionSource() }
                val isFoc by intSource.collectIsFocusedAsState()
                
                val isSelected = (previewPack == packPkg)
                
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .symbianHighlight(isFoc)
                        .focusable(interactionSource = intSource)
                        .clickable(interactionSource = intSource, indication = androidx.compose.foundation.LocalIndication.current) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            previewPack = packPkg
                        }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        packName, 
                        color = if (isFoc) Color.Black else win95Fg(),
                        fontFamily = state.currentFont.toFontFamily(),
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (isFoc) Color.Black else win95Fg(),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                if (index < packs.size - 1) {
                    androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha=0.2f), thickness = 1.dp)
                }
            }
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
            
            // Apply Button
            val applyIntSource = androidx.compose.runtime.remember { MutableInteractionSource() }
            val applyFoc by applyIntSource.collectIsFocusedAsState()
            
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .symbianHighlight(applyFoc)
                    .focusable(interactionSource = applyIntSource)
                    .clickable(interactionSource = applyIntSource, indication = androidx.compose.foundation.LocalIndication.current) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        viewModel.setGlobalIconPack(previewPack)
                        onNavigate(Screen.SETTINGS_GLOBAL)
                    }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "Apply Settings", 
                    color = if (applyFoc) Color.Black else win95Fg(), 
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFamily = state.currentFont.toFontFamily()
                )
            }
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
