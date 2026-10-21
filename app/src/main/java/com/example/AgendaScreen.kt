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
