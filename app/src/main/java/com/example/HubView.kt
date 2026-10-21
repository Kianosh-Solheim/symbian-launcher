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
