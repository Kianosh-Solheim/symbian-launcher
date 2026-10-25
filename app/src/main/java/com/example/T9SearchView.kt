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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

@Composable
fun T9SearchView(
    state: LauncherState, 
    viewModel: LauncherViewModel, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback, 
    forceMode: T9SearchMode? = null,
    onContactSelected: ((ContactInfo) -> Unit)? = null
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    if (selectedApp != null) {
        ManageAppDialogHandler(selectedApp, state, viewModel) { selectedApp = null }
        return
    }

    val context = LocalContext.current
    val effectiveMode = forceMode ?: state.t9SearchMode
    val firstItemFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)) {
            Text(
                "Searching (${if (effectiveMode == T9SearchMode.CONTACTS) "Contacts" else "Apps"}): ${state.t9Query}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, offset = androidx.compose.ui.geometry.Offset(1.5f, 1.5f), blurRadius = 2f))
            )
        }
        
        // T9 digit strings are computed once per list, not on every key press.
        val contactKeys = remember(state.contacts) { state.contacts.map { it to it.name.toT9() } }
        val appKeys = remember(state.allApps) { state.allApps.map { it to it.name.toT9() } }
        val t9Results = remember(effectiveMode, contactKeys, appKeys, state.t9Query) {
            if (effectiveMode == T9SearchMode.CONTACTS) {
                contactKeys.filter { it.second.contains(state.t9Query) }.map { it.first }.map { 
                    T9Result(it.name, it.primaryPhoneNumber, contactInfo = it) 
                }
            } else {
                appKeys.filter { it.second.contains(state.t9Query) }.map { it.first }.map { 
                    T9Result(it.name, "", appInfo = it) 
                }
            }
        }
        
        androidx.compose.runtime.LaunchedEffect(t9Results) {
            if (t9Results.isNotEmpty()) {
                try {
                    firstItemFocusRequester.requestFocus()
                } catch (e: Exception) {}
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(t9Results) { index, result ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                            .symbianHighlight(isFocused)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (result.appInfo != null) {
                                        viewModel.launchApp(result.appInfo)
                                    } else if (result.contactInfo != null) {
                                        if (onContactSelected != null) {
                                            onContactSelected(result.contactInfo)
                                        }
                                    }
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (result.appInfo != null) {
                            AppIconImage(
                                bitmap = result.appInfo.icon.toCachedImageBitmap(),
                                contentDescription = result.name,
                                modifier = Modifier.size(36.dp * state.iconScale)
                            )
                        } else if (result.contactInfo?.photoUri != null) {
                            AsyncImage(
                                model = result.contactInfo.photoUri,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp * state.iconScale).clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = if (state.t9SearchMode == T9SearchMode.CONTACTS) Icons.Default.Person else Icons.Default.Apps,
                                contentDescription = null,
                                tint = if (isFocused) Color.Black else Color.White,
                                modifier = Modifier.size(36.dp * state.iconScale)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = result.name,
                                color = if (isFocused) Color.Black else Color.White,
                                fontFamily = state.currentFont.toFontFamily(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
                            )
                            if (result.detail.isNotEmpty()) {
                                Text(
                                    text = result.detail,
                                    color = if (isFocused) Color.DarkGray else Color.LightGray,
                                    fontFamily = state.currentFont.toFontFamily(),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    if (result.appInfo != null) {
                        val moreInteractionSource = remember { MutableInteractionSource() }
                        val isMoreFocused by moreInteractionSource.collectIsFocusedAsState()
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedApp = result.appInfo
                            },
                            interactionSource = moreInteractionSource,
                            modifier = Modifier.symbianHighlight(isMoreFocused)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.MoreVert,
                                contentDescription = "More Options",
                                tint = if (isMoreFocused) Color.Black else Color.White
                            )
                        }
                    }
                }
                DashedDivider()
            }
        }
    }
}
