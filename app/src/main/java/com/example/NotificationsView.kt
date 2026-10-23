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
        val title = (notif.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() 
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
    
    val liveNotifs by NotificationStore.notifications.collectAsState()
    val displayNotifications = remember(liveNotifs, filter) {
        liveNotifs.filter { 
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
            
            val title = sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
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
