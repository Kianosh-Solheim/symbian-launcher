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
fun ManageAppDialogHandler(
    selectedApp: AppInfo?,
    state: LauncherState,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    if (selectedApp != null) {
        var showIconPackSelector by remember { mutableStateOf(false) }
        var selectedPackForIcons by remember { mutableStateOf<IconPackInfo?>(null) }
        var iconSearchQuery by remember { mutableStateOf("") }
        
        val context = LocalContext.current
        val isPinned = state.pinnedApps.any { it.packageName == selectedApp.packageName }
        // Your own picture as this app's icon.
        val picturePicker = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                val bytes = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (_: Exception) { null }
                val ok = bytes != null && viewModel.setCustomIcon(context, selectedApp.packageName, bytes)
                android.widget.Toast.makeText(context, if (ok) "Icon changed" else "That picture can't be used", android.widget.Toast.LENGTH_SHORT).show()
                showIconPackSelector = false
                onDismiss()
            }
        }
        
        val haptic = androidx.compose.ui.platform.LocalView.current.let { view ->
            remember { object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }}
        }

        BackHandler(enabled = true) {
            if (selectedPackForIcons != null) {
                selectedPackForIcons = null
                iconSearchQuery = ""
            } else if (showIconPackSelector) {
                showIconPackSelector = false
            } else {
                onDismiss()
            }
        }

        if (selectedPackForIcons != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = "Icons: ${selectedPackForIcons!!.name}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f))
                    )
                }
                DashedDivider()
                
                // Search bar for icons
                var textFieldValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(iconSearchQuery)) }
                val focusRequester = remember { FocusRequester() }
                
                Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = textFieldValue,
                        onValueChange = { 
                            textFieldValue = it
                            iconSearchQuery = it.text
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Black),
                        decorationBox = { innerTextField ->
                            if (textFieldValue.text.isEmpty()) {
                                Text("Search icons...", color = Color.Gray, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                    )
                }
                DashedDivider()
                
                var iconList by remember { mutableStateOf<List<String>>(emptyList()) }
                LaunchedEffect(selectedPackForIcons) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val list = viewModel.getIconList(selectedPackForIcons!!.packageName)
                        iconList = list
                    }
                }
                
                val filteredIcons = remember(iconList, iconSearchQuery) {
                    if (iconSearchQuery.isEmpty()) iconList else iconList.filter { it.contains(iconSearchQuery, ignoreCase = true) }
                }
                
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 64.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(this.maxLineSpan) }) {
                        val intSource = remember { MutableInteractionSource() }
                        val isFocused by intSource.collectIsFocusedAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .symbianHighlight(isFocused)
                                .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.setIndividualIconPack(selectedApp.packageName, selectedPackForIcons!!.packageName)
                                    selectedPackForIcons = null
                                    showIconPackSelector = false
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Use Pack Default",
                                color = if (isFocused) Color.Black else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
                            )
                        }
                    }
                    
                    items(filteredIcons.size, key = { filteredIcons[it] }) { index ->
                        val drawableName = filteredIcons[index]
                        val intSource = remember { MutableInteractionSource() }
                        val isFocused by intSource.collectIsFocusedAsState()
                        
                        var drawable by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
                        
                        LaunchedEffect(drawableName) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val d = viewModel.getDrawableByName(selectedPackForIcons!!.packageName, drawableName)
                                drawable = d
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .symbianHighlight(isFocused)
                                .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.setIndividualIconPack(selectedApp.packageName, "${selectedPackForIcons!!.packageName}|${drawableName}")
                                    selectedPackForIcons = null
                                    showIconPackSelector = false
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (drawable != null) {
                                AppIconImage(
                                    bitmap = drawable!!.toCachedImageBitmap(),
                                    contentDescription = drawableName,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (showIconPackSelector) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = "Select Icon for ${selectedApp.name}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f))
                    )
                }
                DashedDivider()
                
                val listFocusRequester = remember { FocusRequester() }
                
                LaunchedEffect(Unit) {
                    try { listFocusRequester.requestFocus() } catch(e: Exception) {}
                }

                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    item {
                        SettingsMenuItem(
                            title = "Default Icon",
                            haptic = haptic,
                            isFirst = true,
                            onClick = {
                                viewModel.clearIndividualIconPack(selectedApp.packageName)
                                showIconPackSelector = false
                                onDismiss()
                            }
                        )
                    }
                    item {
                        SettingsMenuItem(
                            title = "Picture from gallery…",
                            haptic = haptic,
                            onClick = {
                                picturePicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                    }
                    itemsIndexed(state.availableIconPacks) { index, pack ->
                        val intSource = remember { MutableInteractionSource() }
                        val isFocused by intSource.collectIsFocusedAsState()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .symbianHighlight(isFocused)
                                .clickable(interactionSource = intSource, indication = LocalIndication.current) {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    selectedPackForIcons = pack
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(
                                bitmap = pack.icon.toCachedImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp * state.iconScale)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = pack.name,
                                color = if (isFocused) Color.Black else Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
                            )
                        }
                        DashedDivider()
                    }
                }
            }
        } else {
            val haptic = androidx.compose.ui.platform.LocalView.current.let { view ->
                remember { object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }}
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Retro Top Bar
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconImage(
                            bitmap = selectedApp.icon.toCachedImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = selectedApp.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f))
                        )
                    }
                }
                
                DashedDivider()
                
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        SettingsMenuItem(
                            title = if (isPinned) "Unpin App" else "Pin App",
                            icon = if (isPinned) androidx.compose.material.icons.Icons.Filled.Star else androidx.compose.material.icons.Icons.Filled.Star,
                            haptic = haptic,
                            isFirst = true,
                            onClick = {
                                viewModel.togglePinned(selectedApp)
                                onDismiss()
                            }
                        )
                        SettingsMenuItem(
                            title = "App Info",
                            icon = androidx.compose.material.icons.Icons.Filled.Info,
                            haptic = haptic,
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${selectedApp.packageName}")
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                onDismiss()
                            }
                        )
                        if (state.availableIconPacks.isNotEmpty()) {
                            SettingsMenuItem(
                                title = "Change Icon",
                                icon = androidx.compose.material.icons.Icons.Filled.Palette,
                                haptic = haptic,
                                onClick = {
                                    showIconPackSelector = true
                                }
                            )
                        }
                        SettingsMenuItem(
                            title = "Uninstall",
                            icon = androidx.compose.material.icons.Icons.Filled.Delete,
                            haptic = haptic,
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                                        data = android.net.Uri.parse("package:${selectedApp.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}
