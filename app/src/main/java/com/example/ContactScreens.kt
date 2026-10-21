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
fun ContactDetailScreen(
    state: LauncherState,
    viewModel: LauncherViewModel,
    contact: ContactInfo,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var callLogs by remember(contact.id) { mutableStateOf<List<CallLogEntry>?>(null) }
    var showFullPhotoDialog by remember { mutableStateOf(false) }

    val isScrolledDown by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 80
        }
    }

    LaunchedEffect(contact.id) {
        callLogs = viewModel.getCallLogsForContact(contact)
    }

    if (showFullPhotoDialog && contact.photoUri != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullPhotoDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0F172A))
                    .border(2.dp, Color(0xFF64B5F6), RoundedCornerShape(24.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = contact.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1
                        )
                        IconButton(
                            onClick = { showFullPhotoDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AsyncImage(
                        model = contact.photoUri,
                        contentDescription = contact.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A).copy(alpha = 0.65f))
    ) {
        // Retro Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .padding(vertical = 8.dp, horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isScrolledDown) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                kotlinx.coroutines.CoroutineScope(coroutineScope.coroutineContext).launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        }
                ) {
                    val backSource = remember { MutableInteractionSource() }
                    val isBackFocused by backSource.collectIsFocusedAsState()
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                        interactionSource = backSource,
                        modifier = Modifier
                            .size(36.dp)
                            .symbianHighlight(isBackFocused, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isBackFocused) Color.Black else Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    AnimatedContent(
                        targetState = isScrolledDown,
                        label = "topBarContent"
                    ) { scrolled ->
                        if (scrolled) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (contact.photoUri != null) {
                                    AsyncImage(
                                        model = contact.photoUri,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, Color(0xFF64B5F6), CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E293B))
                                            .border(1.dp, Color(0xFF64B5F6), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color(0xFF90CAF9),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = contact.name.ifBlank { "Contact Details" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = Color(0xFF64B5F6),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Tap to view photo",
                                            color = Color(0xFF64B5F6),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Contact Details",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f))
                            )
                        }
                    }
                }

                val editSource = remember { MutableInteractionSource() }
                val isEditFocused by editSource.collectIsFocusedAsState()
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        try {
                            val contactId = contact.id.toLongOrNull()
                            if (contactId != null) {
                                val uri = android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, contactId)
                                val intent = Intent(Intent.ACTION_EDIT).apply {
                                    setDataAndType(uri, android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e2: Exception) {}
                        }
                    },
                    interactionSource = editSource,
                    modifier = Modifier
                        .size(36.dp)
                        .symbianHighlight(isEditFocused, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Contact",
                        tint = if (isEditFocused) Color.Black else Color.White
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp)
            ) {
                // Header with photo and name
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val photoSource = remember { MutableInteractionSource() }
                        val isPhotoFocused by photoSource.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .size(144.dp)
                                .symbianHighlight(isPhotoFocused, RoundedCornerShape(22.dp))
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF1E293B))
                                .border(
                                    BorderStroke(2.5.dp, if (isPhotoFocused) Color.Yellow else Color(0xFF64B5F6)),
                                    RoundedCornerShape(22.dp)
                                )
                                .clickable(
                                    interactionSource = photoSource,
                                    indication = LocalIndication.current
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (contact.photoUri != null) {
                                        showFullPhotoDialog = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (contact.photoUri != null) {
                                AsyncImage(
                                    model = contact.photoUri,
                                    contentDescription = contact.name,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color(0xFF90CAF9),
                                        modifier = Modifier.size(76.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = contact.name.ifBlank { "Unknown Contact" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(1.5f, 1.5f), blurRadius = 3f))
                        )

                        if (contact.nicknames.isNotEmpty()) {
                            Text(
                                text = "\"${contact.nicknames.first()}\"",
                                color = Color(0xFF81D4FA),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        contact.organization?.let { org ->
                            val orgText = buildString {
                                if (org.title.isNotBlank()) append(org.title)
                                if (org.title.isNotBlank() && org.company.isNotBlank()) append(" • ")
                                if (org.company.isNotBlank()) append(org.company)
                            }
                            if (orgText.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Business,
                                        contentDescription = null,
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = orgText,
                                        color = Color.LightGray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        val primaryPhone = contact.phoneNumbers.firstOrNull()
                        if (primaryPhone != null) {
                            // Quick Call
                            val callSource = remember { MutableInteractionSource() }
                            val isCallFocused by callSource.collectIsFocusedAsState()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .symbianHighlight(isCallFocused, RoundedCornerShape(8.dp))
                                    .clickable(interactionSource = callSource, indication = LocalIndication.current) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${primaryPhone.number}"))
                                        context.startActivity(intent)
                                    }
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF2E7D32), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Call", color = Color.White, fontSize = 12.sp)
                            }

                            // Quick SMS
                            val smsSource = remember { MutableInteractionSource() }
                            val isSmsFocused by smsSource.collectIsFocusedAsState()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .symbianHighlight(isSmsFocused, RoundedCornerShape(8.dp))
                                    .clickable(interactionSource = smsSource, indication = LocalIndication.current) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${primaryPhone.number}"))
                                        try { context.startActivity(intent) } catch (e: Exception) {}
                                    }
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFF0288D1), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Message, contentDescription = "Message", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Message", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        val primaryEmail = contact.emails.firstOrNull()
                        if (primaryEmail != null) {
                            val emailSource = remember { MutableInteractionSource() }
                            val isEmailFocused by emailSource.collectIsFocusedAsState()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .symbianHighlight(isEmailFocused, RoundedCornerShape(8.dp))
                                    .clickable(interactionSource = emailSource, indication = LocalIndication.current) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${primaryEmail.address}"))
                                        try { context.startActivity(intent) } catch (e: Exception) {}
                                    }
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color(0xFFF57C00), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = "Email", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Email", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        // Share
                        val shareSource = remember { MutableInteractionSource() }
                        val isShareFocused by shareSource.collectIsFocusedAsState()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .symbianHighlight(isShareFocused, RoundedCornerShape(8.dp))
                                .clickable(interactionSource = shareSource, indication = LocalIndication.current) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val shareText = buildString {
                                        append("Contact: ${contact.name}\n")
                                        contact.phoneNumbers.forEach { append("${it.label}: ${it.number}\n") }
                                        contact.emails.forEach { append("${it.label}: ${it.address}\n") }
                                    }.trim()
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Contact"))
                                    } catch (e: Exception) {}
                                }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF455A64), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Share", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Phone Numbers Section
            if (contact.phoneNumbers.isNotEmpty()) {
                item {
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Phone Numbers",
                            color = Color(0xFF90CAF9),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contact.phoneNumbers) { phone ->
                    val rowSource = remember { MutableInteractionSource() }
                    val isRowFocused by rowSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isRowFocused, RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = phone.number,
                                color = if (isRowFocused) Color.Black else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = phone.label.ifBlank { "Mobile" },
                                color = if (isRowFocused) Color.DarkGray else Color.LightGray,
                                fontSize = 13.sp
                            )
                        }

                        // Call button
                        val callIntSource = remember { MutableInteractionSource() }
                        val isCallFocused by callIntSource.collectIsFocusedAsState()
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${phone.number}"))
                                context.startActivity(intent)
                            },
                            interactionSource = callIntSource,
                            modifier = Modifier
                                .size(40.dp)
                                .symbianHighlight(isCallFocused, CircleShape)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF4CAF50))
                        }

                        // SMS button
                        val smsIntSource = remember { MutableInteractionSource() }
                        val isSmsFocused by smsIntSource.collectIsFocusedAsState()
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${phone.number}"))
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            },
                            interactionSource = smsIntSource,
                            modifier = Modifier
                                .size(40.dp)
                                .symbianHighlight(isSmsFocused, CircleShape)
                        ) {
                            Icon(Icons.Default.Message, contentDescription = "SMS", tint = Color(0xFF29B6F6))
                        }

                        // Copy button
                        val copySource = remember { MutableInteractionSource() }
                        val isCopyFocused by copySource.collectIsFocusedAsState()
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(phone.number))
                                android.widget.Toast.makeText(context, "Number copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            interactionSource = copySource,
                            modifier = Modifier
                                .size(40.dp)
                                .symbianHighlight(isCopyFocused, CircleShape)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Number", tint = Color.LightGray)
                        }
                    }
                }
            }

            // Emails Section
            if (contact.emails.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Email Addresses",
                            color = Color(0xFFFFCC80),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contact.emails) { email ->
                    val emailSource = remember { MutableInteractionSource() }
                    val isEmailFocused by emailSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isEmailFocused, RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .clickable(interactionSource = emailSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:${email.address}"))
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = if (isEmailFocused) Color.Black else Color(0xFFFFB74D),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = email.address,
                                color = if (isEmailFocused) Color.Black else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = email.label.ifBlank { "Email" },
                                color = if (isEmailFocused) Color.DarkGray else Color.LightGray,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(email.address))
                                android.widget.Toast.makeText(context, "Email copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp * state.iconScale)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Email", tint = Color.LightGray)
                        }
                    }
                }
            }

            // Birthday & Dates Section
            if (contact.birthday != null || contact.events.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Cake, contentDescription = null, tint = Color(0xFFF48FB1), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Birthday & Important Dates",
                            color = Color(0xFFF8BBD0),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (contact.birthday != null) {
                    item {
                        val bdayInfo = remember(contact.birthday) { formatContactDate(contact.birthday, isBirthday = true) }
                        val bdaySource = remember { MutableInteractionSource() }
                        val isBdayFocused by bdaySource.collectIsFocusedAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .symbianHighlight(isBdayFocused, RoundedCornerShape(8.dp))
                                .background(Color(0xFF880E4F).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFF48FB1).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cake, contentDescription = "Birthday", tint = Color(0xFFF48FB1), modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bdayInfo?.naturalDate ?: contact.birthday,
                                    color = if (isBdayFocused) Color.Black else Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = bdayInfo?.countdownText ?: "Birthday",
                                        color = if (isBdayFocused) Color.DarkGray else Color(0xFFFF80AB),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    bdayInfo?.ageText?.let { age ->
                                        Text(
                                            text = " • $age",
                                            color = if (isBdayFocused) Color.DarkGray else Color(0xFFF8BBD0),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(contact.events.filter { it.type != android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY }) { event ->
                    val evtInfo = remember(event.date) { formatContactDate(event.date, isBirthday = false) }
                    val evtSource = remember { MutableInteractionSource() }
                    val isEvtFocused by evtSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isEvtFocused, RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFFCE93D8), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = evtInfo?.naturalDate ?: event.date,
                                color = if (isEvtFocused) Color.Black else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = event.label.ifBlank { "Special Event" },
                                    color = if (isEvtFocused) Color.DarkGray else Color.LightGray,
                                    fontSize = 13.sp
                                )
                                if (evtInfo != null && evtInfo.daysRemaining >= 0) {
                                    Text(
                                        text = " • ${evtInfo.countdownText}",
                                        color = if (isEvtFocused) Color.DarkGray else Color(0xFFE1BEE7),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Notes Section
            if (contact.notes.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFFFE082), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notes",
                            color = Color(0xFFFFECB3),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contact.notes) { note ->
                    val noteSource = remember { MutableInteractionSource() }
                    val isNoteFocused by noteSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isNoteFocused, RoundedCornerShape(6.dp))
                            .background(Color(0xFF3E2723).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = note,
                            color = if (isNoteFocused) Color.Black else Color(0xFFFFF8E1),
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            lineHeight = 20.sp
                        )
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(note))
                                android.widget.Toast.makeText(context, "Note copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Note", tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Addresses Section
            if (contact.addresses.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF80CBC4), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Addresses",
                            color = Color(0xFFB2DFDB),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contact.addresses) { addr ->
                    val addrSource = remember { MutableInteractionSource() }
                    val isAddrFocused by addrSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isAddrFocused, RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .clickable(interactionSource = addrSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val uri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(addr.formattedAddress)}")
                                val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                try { context.startActivity(mapIntent) } catch (e: Exception) {}
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF80CBC4), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = addr.formattedAddress,
                                color = if (isAddrFocused) Color.Black else Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = addr.label.ifBlank { "Address" },
                                color = if (isAddrFocused) Color.DarkGray else Color.LightGray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Websites Section
            if (contact.websites.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    DashedDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF80DEEA), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Websites",
                            color = Color(0xFFB2EBF2),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                items(contact.websites) { site ->
                    val siteSource = remember { MutableInteractionSource() }
                    val isSiteFocused by siteSource.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .symbianHighlight(isSiteFocused, RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .clickable(interactionSource = siteSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val url = if (!site.startsWith("http://") && !site.startsWith("https://")) "https://$site" else site
                                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                try { context.startActivity(browserIntent) } catch (e: Exception) {}
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF80DEEA), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = site,
                            color = if (isSiteFocused) Color.Black else Color(0xFF80DEEA),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Call History Section (Underneath all that)
            item {
                Spacer(modifier = Modifier.height(12.dp))
                DashedDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFFA7FFEB),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Call History",
                            color = Color(0xFFA7FFEB),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    if (callLogs != null) {
                        Text(
                            text = "${callLogs?.size ?: 0} calls",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            val logs = callLogs
            if (logs == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFFA7FFEB),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Loading call logs...",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else if (logs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneCallback,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No call history with this contact",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                items(logs) { entry ->
                    val isIncoming = entry.type == android.provider.CallLog.Calls.INCOMING_TYPE
                    val isOutgoing = entry.type == android.provider.CallLog.Calls.OUTGOING_TYPE
                    val isMissed = entry.type == android.provider.CallLog.Calls.MISSED_TYPE
                    val isRejected = entry.type == android.provider.CallLog.Calls.REJECTED_TYPE
                    val isBlocked = entry.type == android.provider.CallLog.Calls.BLOCKED_TYPE
                    val isVoicemail = entry.type == android.provider.CallLog.Calls.VOICEMAIL_TYPE

                    val icon = when {
                        isIncoming -> Icons.Default.CallReceived
                        isOutgoing -> Icons.Default.CallMade
                        isMissed -> Icons.Default.CallMissed
                        isRejected -> Icons.Default.CallEnd
                        isVoicemail -> Icons.Default.Voicemail
                        isBlocked -> Icons.Default.Block
                        else -> Icons.Default.Call
                    }

                    val badgeColor = when {
                        isIncoming -> Color(0xFF4CAF50)
                        isOutgoing -> Color(0xFF29B6F6)
                        isMissed -> Color(0xFFE53935)
                        isRejected -> Color(0xFFFF7043)
                        isVoicemail -> Color(0xFFFFB300)
                        isBlocked -> Color(0xFF9E9E9E)
                        else -> Color(0xFFB0BEC5)
                    }

                    val typeLabel = when {
                        isIncoming -> "Incoming Call"
                        isOutgoing -> "Outgoing Call"
                        isMissed -> "Missed Call"
                        isRejected -> "Declined Call"
                        isVoicemail -> "Voicemail"
                        isBlocked -> "Blocked Call"
                        else -> "Call"
                    }

                    val logSource = remember { MutableInteractionSource() }
                    val isLogFocused by logSource.collectIsFocusedAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .symbianHighlight(isLogFocused, RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
                            .clickable(interactionSource = logSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${entry.number}"))
                                context.startActivity(intent)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(badgeColor.copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, badgeColor.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = typeLabel,
                                tint = badgeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = typeLabel,
                                    color = if (isLogFocused) Color.Black else if (isMissed) Color(0xFFFF8A80) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isMissed) FontWeight.Bold else FontWeight.SemiBold
                                )
                                if (entry.number.isNotBlank()) {
                                    Text(
                                        text = " (${entry.number})",
                                        color = if (isLogFocused) Color.DarkGray else Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                text = entry.getFormattedDate(),
                                color = if (isLogFocused) Color.DarkGray else Color.LightGray,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = entry.getFormattedDuration(),
                            color = if (isLogFocused) Color.Black else if (isMissed || isRejected) Color(0xFFFF8A80) else Color(0xFF81C784),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

@Composable
fun ContactsDrawer(
    state: LauncherState, 
    viewModel: LauncherViewModel, 
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onContactSelected: (ContactInfo) -> Unit
) {
    if (state.t9Query.isNotEmpty()) {
        T9SearchView(state, viewModel, haptic, onContactSelected = onContactSelected)
    } else {
        val context = LocalContext.current
        val firstItemFocusRequester = remember { FocusRequester() }
        LaunchedEffect(state.contacts) {
            if (state.contacts.isNotEmpty()) {
                try { firstItemFocusRequester.requestFocus() } catch(e: Exception) {}
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(state.contacts) { index, contact ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .symbianHighlight(isFocused)
                        .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onContactSelected(contact)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (contact.photoUri != null) {
                        AsyncImage(
                            model = contact.photoUri,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp * state.iconScale).clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isFocused) Color.Black else Color.White,
                            modifier = Modifier.size(36.dp * state.iconScale)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = contact.name,
                            color = if (isFocused) Color.Black else Color.White,
                            fontFamily = state.currentFont.toFontFamily(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(shadow = if (isFocused) Shadow.None else Shadow(color = Color.Black.copy(alpha=0.8f), offset = Offset(1.5f, 1.5f), blurRadius = 2f))
                        )
                        if (contact.primaryPhoneNumber.isNotEmpty()) {
                            Text(
                                text = contact.primaryPhoneNumber,
                                color = if (isFocused) Color.DarkGray else Color.LightGray,
                                fontFamily = state.currentFont.toFontFamily(),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                DashedDivider()
            }
        }
    }
}
