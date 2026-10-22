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
fun MenuView(
    state: LauncherState, 
    viewModel: LauncherViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, 
    onNavigate: (Screen) -> Unit
) {
    var showBatterySaverConfirmDialog by remember { mutableStateOf(false) }
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { firstItemFocusRequester.requestFocus() } catch(e: Exception) {}
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MenuIcon("Settings", haptic, { SkeuomorphicSettingsIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f).focusRequester(firstItemFocusRequester), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.SETTINGS_ROOT) }
            MenuIcon("Apps", haptic, { SkeuomorphicAppsIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.APP_DRAWER) }
            MenuIcon("Contacts", haptic, { SkeuomorphicContactsIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.CONTACTS_DRAWER) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MenuIcon("Messages", haptic, { SkeuomorphicMessageIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.MESSAGES) }
            MenuIcon("Agenda", haptic, { SkeuomorphicCalendarIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.AGENDA) }
            MenuIcon("Clock", haptic, { SkeuomorphicClockIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.CLOCK) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MenuIcon("Calculator", haptic, { SkeuomorphicCalculatorIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.CALCULATOR) }
            MenuIcon("Wallet", haptic, { SkeuomorphicWalletIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.WALLET) }
            MenuIcon("Usage", haptic, { SkeuomorphicUsageIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, modifier = Modifier.weight(1f), fontFamily = state.currentFont.toFontFamily()) { onNavigate(Screen.USAGE) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MenuIcon(
                label = if (state.isExtremeBatterySaver) "Exit Saver" else "Battery Saver", 
                haptic = haptic, 
                icon = { SkeuomorphicBatterySaverIcon(modifier = Modifier.size(40.dp * state.iconScale)) }, 
                modifier = Modifier.weight(1f), 
                fontFamily = state.currentFont.toFontFamily()
            ) { 
                if (state.isExtremeBatterySaver) {
                    viewModel.setExtremeBatterySaver(false)
                    onNavigate(Screen.MAIN)
                } else {
                    showBatterySaverConfirmDialog = true
                }
            }
            val desktopOn by BridgeState.running.collectAsState()
            MenuIcon(
                label = "Desktop",
                haptic = haptic,
                icon = { SkeuomorphicDesktopIcon(modifier = Modifier.size(40.dp * state.iconScale), active = desktopOn) },
                modifier = Modifier.weight(1f),
                fontFamily = state.currentFont.toFontFamily()
            ) { onNavigate(Screen.SETTINGS_DESKTOP) }
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    if (showBatterySaverConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBatterySaverConfirmDialog = false },
            title = {
                Text("Extreme Battery Saver", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Switch to Extreme Battery Saver? The screen will turn AMOLED black and show only essential apps (Phone, SMS, Calendar, Camera, and two custom apps) with a clock to maximize battery life.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatterySaverConfirmDialog = false
                        viewModel.setExtremeBatterySaver(true)
                        onNavigate(Screen.MAIN)
                    }
                ) {
                    Text("Turn On")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatterySaverConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
fun MenuIcon(
    label: String,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .symbianHighlight(isFocused)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontFamily = fontFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
        )
    }
}
