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
import com.example.ThemedAsyncImage as AsyncImage
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

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
            .then(if (LocalS60.current) Modifier.background(S60BarBrush) else Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.surface))
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
                            state.weatherWarnings.firstOrNull()?.let { w ->
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("⚠", color = warningColor(w.color), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                    style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f)))
                            }
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
    val win95 = LocalWin95.current && !isExtremeBatterySaver
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (win95) Modifier.win95Bevel()
                else if (LocalS60.current && !isExtremeBatterySaver) Modifier.background(S60BarBrush)
                else Modifier.background(if (isExtremeBatterySaver) Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.surface))
            .padding(horizontal = if (win95) 4.dp else 12.dp, vertical = 4.dp),
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
    if (LocalWin95.current) {
        // Taskbar-style push button: raised, and pressed in while focused.
        Text(
            text = text, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = modifier
                .win95Bevel(sunken = isFocused)
                .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onClick() }
                .padding(horizontal = 14.dp, vertical = 5.dp)
        )
        return
    }
    Text(
        text = text, 
        color = if (isFocused) Color.Black else Color.White, 
        fontWeight = FontWeight.Bold, 
        fontSize = 16.sp,
        fontFamily = if (LocalLcd.current) PixelFont else null,
        style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black, offset = Offset(1f,1f), blurRadius = 2f)),
        modifier = modifier
            .symbianHighlight(isFocused)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}


/** MET Norway warning level colour. */
fun warningColor(level: String): Color = when (level.lowercase()) {
    "red" -> Color(0xFFFF3B30)
    "orange" -> Color(0xFFFF9500)
    else -> Color(0xFFFFD60A)
}
