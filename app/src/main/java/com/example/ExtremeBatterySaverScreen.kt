package com.example

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExtremeBatterySaverScreen(
    state: LauncherState,
    viewModel: LauncherViewModel,
    haptic: HapticFeedback,
    onExitSaver: () -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activePickerSlot by remember { mutableStateOf<Int?>(null) }
    var activeSlotOptions by remember { mutableStateOf<Int?>(null) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    // Live clock state
    val now = rememberTickingTime(if (state.showSeconds) 1_000L else 60_000L)
    val timePattern = remember(state.is24HourFormat, state.showSeconds) {
        val secondsPattern = if (state.showSeconds) ":ss" else ""
        if (state.is24HourFormat) "HH:mm$secondsPattern" else "hh:mm$secondsPattern a"
    }
    val datePattern = remember(state.dateFormat, state.showWeekday, state.weekdayFormat) {
        var dFmt = state.dateFormat.pattern
        if (dFmt.isEmpty()) {
            val fmt = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy")
            dFmt = fmt.replace(Regex("[^dMy]"), " ").trim().replace(Regex("\\s+"), "/")
            if (dFmt.isEmpty()) dFmt = "dd/MM/yyyy"
        }
        if (state.showWeekday) {
            val wFmt = if (state.weekdayFormat == WeekdayFormat.FULL) "EEEE" else "EEE"
            dFmt = "$wFmt, $dFmt"
        }
        dFmt.trim()
    }
    val currentTimeString = Formatters.format(timePattern, now)
    val currentDateString = Formatters.format(datePattern, now)

    val batteryInfo = rememberBatteryInfo(context)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // CLOCK SECTION ABOVE
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigate(Screen.CLOCK)
                    }
            ) {
                // Digital Clock
                Text(
                    text = currentTimeString.ifEmpty { "00:00" },
                    color = Color.White,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = state.currentFont.toFontFamily(),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Date
                Text(
                    text = currentDateString,
                    color = Color(0xFFB0BEC5),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = state.currentFont.toFontFamily()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Minimal Battery Saver Status pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(Color(0xFF1E261E), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF2E7D32), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${(batteryInfo.percentage * 100).toInt()}% • Extreme Battery Saver",
                        color = Color(0xFFA5D6A7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 3 BY 3 GRID
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ROW 1: Phone, SMS, Calendar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 1. Phone App
                    ExtremeGridItem(
                        label = "Phone",
                        fontFamily = state.currentFont.toFontFamily(),
                        icon = { SkeuomorphicPhoneIcon(modifier = Modifier.size(46.dp)) },
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            try {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val dialerApp = state.allApps.find {
                                    it.packageName.contains("dialer", ignoreCase = true) ||
                                            it.name.contains("phone", ignoreCase = true)
                                }
                                if (dialerApp != null) viewModel.launchApp(dialerApp)
                            }
                        }
                    )

                    // 2. SMS App
                    ExtremeGridItem(
                        label = "Messages",
                        fontFamily = state.currentFont.toFontFamily(),
                        icon = { SkeuomorphicMessageIcon(modifier = Modifier.size(46.dp)) },
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            try {
                                val defaultSms = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
                                val intent = if (defaultSms != null) {
                                    context.packageManager.getLaunchIntentForPackage(defaultSms)?.apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                } else {
                                    Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_APP_MESSAGING)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                }
                                if (intent != null && intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    onNavigate(Screen.MESSAGES)
                                }
                            } catch (e: Exception) {
                                onNavigate(Screen.MESSAGES)
                            }
                        }
                    )

                    // 3. Calendar App
                    ExtremeGridItem(
                        label = "Calendar",
                        fontFamily = state.currentFont.toFontFamily(),
                        icon = { SkeuomorphicCalendarIcon(modifier = Modifier.size(46.dp)) },
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            try {
                                val intent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    onNavigate(Screen.AGENDA)
                                }
                            } catch (e: Exception) {
                                onNavigate(Screen.AGENDA)
                            }
                        }
                    )
                }

                // ROW 2: Camera, Extra App 1, Extra App 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 4. Camera App
                    ExtremeGridItem(
                        label = "Camera",
                        fontFamily = state.currentFont.toFontFamily(),
                        icon = { SkeuomorphicCameraIcon(modifier = Modifier.size(46.dp)) },
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            try {
                                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val cameraApp = state.allApps.find {
                                    it.packageName.contains("camera", ignoreCase = true) ||
                                            it.name.contains("camera", ignoreCase = true)
                                }
                                if (cameraApp != null) viewModel.launchApp(cameraApp)
                            }
                        }
                    )

                    // 5. Extra App Slot 1
                    val customApp1 = state.allApps.find { it.packageName == state.extremeBatterySaverApp1 }
                    if (customApp1 != null) {
                        ExtremeGridItem(
                            label = customApp1.name,
                            fontFamily = state.currentFont.toFontFamily(),
                            icon = {
                                AppIconImage(
                                    bitmap = customApp1.icon.toCachedImageBitmap(),
                                    contentDescription = customApp1.name,
                                    modifier = Modifier.size(46.dp)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.launchApp(customApp1)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                activeSlotOptions = 1
                            }
                        )
                    } else {
                        ExtremeAddAppSlot(
                            modifier = Modifier.weight(1f),
                            fontFamily = state.currentFont.toFontFamily(),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activePickerSlot = 1
                            }
                        )
                    }

                    // 6. Extra App Slot 2
                    val customApp2 = state.allApps.find { it.packageName == state.extremeBatterySaverApp2 }
                    if (customApp2 != null) {
                        ExtremeGridItem(
                            label = customApp2.name,
                            fontFamily = state.currentFont.toFontFamily(),
                            icon = {
                                AppIconImage(
                                    bitmap = customApp2.icon.toCachedImageBitmap(),
                                    contentDescription = customApp2.name,
                                    modifier = Modifier.size(46.dp)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.launchApp(customApp2)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                activeSlotOptions = 2
                            }
                        )
                    } else {
                        ExtremeAddAppSlot(
                            modifier = Modifier.weight(1f),
                            fontFamily = state.currentFont.toFontFamily(),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activePickerSlot = 2
                            }
                        )
                    }
                }

                // ROW 3: Turn Off Saver + Balance Placeholders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 7. Exit Battery Saver Button
                    ExtremeGridItem(
                        label = "Turn Off",
                        fontFamily = state.currentFont.toFontFamily(),
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFF2C1010), CircleShape)
                                    .border(1.dp, Color(0xFFEF5350), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Turn off battery saver",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showExitConfirmDialog = true
                        }
                    )

                    // 8. Empty Slot Placeholder
                    Box(modifier = Modifier.weight(1f))

                    // 9. Empty Slot Placeholder
                    Box(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // App Picker Dialog
    if (activePickerSlot != null) {
        AppPickerDialog(
            apps = state.allApps,
            fontFamily = state.currentFont.toFontFamily(),
            onDismiss = { activePickerSlot = null },
            onSelect = { app ->
                viewModel.setExtremeBatterySaverApp(activePickerSlot!!, app.packageName)
                activePickerSlot = null
            }
        )
    }

    // Slot Long-Press Options Dialog
    if (activeSlotOptions != null) {
        val slot = activeSlotOptions!!
        val appName = if (slot == 1) {
            state.allApps.find { it.packageName == state.extremeBatterySaverApp1 }?.name ?: "App"
        } else {
            state.allApps.find { it.packageName == state.extremeBatterySaverApp2 }?.name ?: "App"
        }

        AlertDialog(
            onDismissRequest = { activeSlotOptions = null },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text(appName, color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            val targetSlot = slot
                            activeSlotOptions = null
                            activePickerSlot = targetSlot
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change App", color = Color(0xFF64B5F6), fontSize = 16.sp)
                    }
                    TextButton(
                        onClick = {
                            viewModel.setExtremeBatterySaverApp(slot, null)
                            activeSlotOptions = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove App", color = Color(0xFFFF5252), fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { activeSlotOptions = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // Exit Saver Confirm Dialog
    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            containerColor = Color(0xFF1C1C1E),
            title = {
                Text("Exit Battery Saver?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Turn off Extreme Battery Saver and restore standard launcher features?",
                    color = Color(0xFFCFD8DC),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onExitSaver()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Turn Off", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Stay in Saver", color = Color.Gray)
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ExtremeGridItem(
    label: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF222222) else Color.Transparent)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = fontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ExtremeAddAppSlot(
    modifier: Modifier = Modifier,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF222222) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(Color(0xFF141414), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add application",
                tint = Color(0xFFAAAAAA),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Add app",
            color = Color(0xFF888888),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = fontFamily,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AppPickerDialog(
    apps: List<AppInfo>,
    fontFamily: androidx.compose.ui.text.font.FontFamily?,
    onDismiss: () -> Unit,
    onSelect: (AppInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps.sortedBy { it.name.lowercase() }
        } else {
            apps.filter { it.name.contains(searchQuery, ignoreCase = true) }
                .sortedBy { it.name.lowercase() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A1A),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select App",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF64B5F6),
                        unfocusedBorderColor = Color(0xFF424242),
                        focusedContainerColor = Color(0xFF262626),
                        unfocusedContainerColor = Color(0xFF262626)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Apps list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(app) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(
                                bitmap = app.icon.toCachedImageBitmap(),
                                contentDescription = app.name,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = app.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = fontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
