package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.util.UUID

enum class ScanIntent {
    LOYALTY_CARD,
    BOARDING_PASS_OR_TICKET
}

/**
 * Symbian Glossy Button with top highlight and bevel for authentic S60 feel.
 */
@Composable
fun SymbianGlossyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    baseColor: Color = Color(0xFFFFD54F),
    contentColor: Color = Color.Black,
    borderColor: Color = Color.White.copy(alpha = 0.5f),
    content: @Composable RowScope.() -> Unit
) {
    val topColor = androidx.compose.ui.graphics.lerp(baseColor, Color.White, 0.45f)
    val buttonBrush = Brush.verticalGradient(
        0.0f to topColor,
        0.49f to topColor,
        0.50f to baseColor,
        1.0f to baseColor
    )
    val shape = RoundedCornerShape(6.dp)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (enabled) borderColor else Color.Gray.copy(alpha = 0.3f)),
        modifier = modifier
            .shadow(if (enabled) 3.dp else 0.dp, shape)
            .background(if (enabled) buttonBrush else Brush.verticalGradient(listOf(Color.Gray, Color.DarkGray)), shape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides if (enabled) contentColor else Color.LightGray) {
                content()
            }
        }
    }
}

/**
 * Skeuomorphic EMV Gold Chip for cards
 */
@Composable
fun SkeuomorphicEmvChip(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 24.dp, height = 18.dp)) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        // Gold base gradient
        val goldBrush = Brush.verticalGradient(
            listOf(Color(0xFFFFECB3), Color(0xFFFFC107), Color(0xFFFFA000))
        )
        drawRoundRect(
            brush = goldBrush,
            size = Size(w, h),
            cornerRadius = corner,
            style = Fill
        )
        // Chip border
        drawRoundRect(
            color = Color(0xFFB78103),
            size = Size(w, h),
            cornerRadius = corner,
            style = Stroke(width = 0.8.dp.toPx())
        )
        // Microchip circuit lines
        val lineCol = Color(0xFFB78103)
        val strokeW = 0.75.dp.toPx()
        drawLine(lineCol, Offset(w * 0.35f, 0f), Offset(w * 0.35f, h), strokeWidth = strokeW)
        drawLine(lineCol, Offset(w * 0.65f, 0f), Offset(w * 0.65f, h), strokeWidth = strokeW)
        drawLine(lineCol, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = strokeW)
        drawLine(lineCol, Offset(w * 0.35f, h * 0.25f), Offset(w * 0.65f, h * 0.25f), strokeWidth = strokeW)
        drawLine(lineCol, Offset(w * 0.35f, h * 0.75f), Offset(w * 0.65f, h * 0.75f), strokeWidth = strokeW)
    }
}

@Composable
fun WalletScreen(
    state: LauncherState,
    viewModel: LauncherViewModel,
    haptic: HapticFeedback,
    onNavigateToPassDetail: (WalletPass) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val selectedTab = state.walletCategoryTab
    var showImportDialog by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var showLoyaltyCardDialog by remember { mutableStateOf(false) }
    var scanChooserIntent by remember { mutableStateOf<ScanIntent?>(null) }
    var activeScanIntent by remember { mutableStateOf(ScanIntent.LOYALTY_CARD) }
    var loyaltyScannedCode by remember { mutableStateOf("") }
    var loyaltyScannedFormat by remember { mutableStateOf(BarcodeFormatType.QR_CODE) }

    // File picker for .pkpass files
    val pkpassFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importPkPass(uri) { pass ->
                if (pass != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    Toast.makeText(context, "Imported: ${pass.organizationName} (${pass.type.displayName})", Toast.LENGTH_SHORT).show()
                    onNavigateToPassDetail(pass)
                } else {
                    Toast.makeText(context, "Could not parse .pkpass archive. Check file format.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Camera photo capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            when (activeScanIntent) {
                ScanIntent.LOYALTY_CARD -> {
                    viewModel.decodeBarcodeFromBitmap(bitmap) { result ->
                        if (result != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            loyaltyScannedCode = result.rawText
                            loyaltyScannedFormat = result.format
                            showLoyaltyCardDialog = true
                            Toast.makeText(context, "Scanned: ${result.rawText.take(24)}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No QR or barcode detected. Try snapping closer in bright lighting.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                ScanIntent.BOARDING_PASS_OR_TICKET -> {
                    viewModel.importPassFromBitmap(bitmap, "Scanned Pass") { pass ->
                        if (pass != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, "Scanned: ${pass.organizationName}", Toast.LENGTH_SHORT).show()
                            onNavigateToPassDetail(pass)
                        } else {
                            Toast.makeText(context, "No readable barcode detected in photo. Try again with good lighting.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to scan passes", Toast.LENGTH_SHORT).show()
        }
    }

    fun startCameraScan(intent: ScanIntent) {
        activeScanIntent = intent
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Gallery / Photo picker launcher
    val galleryImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            when (activeScanIntent) {
                ScanIntent.LOYALTY_CARD -> {
                    viewModel.decodeBarcodeFromUri(uri) { result ->
                        if (result != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            loyaltyScannedCode = result.rawText
                            loyaltyScannedFormat = result.format
                            showLoyaltyCardDialog = true
                            Toast.makeText(context, "Scanned: ${result.rawText.take(24)}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No readable QR code or barcode found in photo.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                ScanIntent.BOARDING_PASS_OR_TICKET -> {
                    viewModel.importPassFromImage(uri, cardTitle = "Scanned Ticket") { pass ->
                        if (pass != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, "Scanned: ${pass.organizationName}", Toast.LENGTH_SHORT).show()
                            onNavigateToPassDetail(pass)
                        } else {
                            Toast.makeText(context, "No readable barcode detected in image.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    fun startGalleryScan(intent: ScanIntent) {
        activeScanIntent = intent
        galleryImagePicker.launch("image/*")
    }

    val filteredPasses = remember(state.walletPasses, selectedTab) {
        when (selectedTab) {
            1 -> state.walletPasses.filter { it.type == PassType.LOYALTY_CARD }
            2 -> state.walletPasses.filter { it.type == PassType.BOARDING_PASS }
            3 -> state.walletPasses.filter { it.type == PassType.EVENT_TICKET || it.type == PassType.COUPON || it.type == PassType.GENERIC }
            else -> state.walletPasses
        }
    }

    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { firstItemFocusRequester.requestFocus() } catch (e: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top row with Tabs and Add Button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                "All",
                "Loyalty",
                "Flights",
                "Tickets"
            )
            // Tabs
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val isSelected = selectedTab == index
                    
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = state.currentFont.toFontFamily(),
                        modifier = Modifier
                            .symbianHighlight(isFocused)
                            .focusable(interactionSource = interactionSource)
                            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setWalletCategoryTab(index)
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
            }
            
            // Add Button
            val addIntSource = remember { MutableInteractionSource() }
            val isAddFoc by addIntSource.collectIsFocusedAsState()
            
            androidx.compose.material3.IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showImportDialog = true
                },
                modifier = Modifier
                    .symbianHighlight(isAddFoc)
                    .focusable(interactionSource = addIntSource)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Pass",
                    tint = Color.White
                )
            }
        }

        if (filteredPasses.isEmpty()) {
            // Symbian Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.25f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), CircleShape)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SkeuomorphicWalletIcon(modifier = Modifier.size(56.dp))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (state.walletPasses.isEmpty()) "Your Wallet is Empty" else "No Passes in this category",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = state.currentFont.toFontFamily(),
                        style = TextStyle(
                            shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(1f, 1f), blurRadius = 2f)
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Store loyalty rewards cards, Apple Wallet passes (.pkpass), and boarding tickets.",
                        color = Color(0xFFD1D5DB),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SymbianGlossyButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scanChooserIntent = ScanIntent.LOYALTY_CARD
                            },
                            baseColor = Color(0xFFFFD54F),
                            contentColor = Color.Black
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan QR Card", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        SymbianGlossyButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showImportDialog = true
                            },
                            baseColor = Color(0xFF455A64),
                            contentColor = Color.White,
                            borderColor = Color.White.copy(alpha = 0.3f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Pass", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredPasses, key = { it.id }) { pass ->
                    SymbianWalletPassCardItem(
                        pass = pass,
                        state = state,
                        haptic = haptic,
                        onClick = {
                            onNavigateToPassDetail(pass)
                        }
                    )
                }
            }
        }
    }

    // Symbian Styled Import Options Dialog
    if (showImportDialog) {
        SymbianDialogFrame(onDismissRequest = { showImportDialog = false }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    SkeuomorphicWalletIcon(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ADD TO WALLET",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        fontFamily = state.currentFont.toFontFamily(),
                        style = TextStyle(
                            shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(1f, 1f), blurRadius = 2f)
                        )
                    )
                }

                DashedDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Option 1: Scan QR Code for Loyalty Card (Camera or Photo)
                SymbianImportOptionItem(
                    icon = Icons.Default.QrCodeScanner,
                    title = "Scan Loyalty Card QR Code",
                    subtitle = "Scan with Camera or choose from Photos",
                    accentColor = Color(0xFFFFD54F)
                ) {
                    showImportDialog = false
                    scanChooserIntent = ScanIntent.LOYALTY_CARD
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2: Add Loyalty Card Manually
                SymbianImportOptionItem(
                    icon = Icons.Default.Loyalty,
                    title = "Add Loyalty Card (Manual Entry)",
                    subtitle = "Create custom loyalty / rewards card with barcode",
                    accentColor = Color(0xFF81C784)
                ) {
                    showImportDialog = false
                    loyaltyScannedCode = ""
                    loyaltyScannedFormat = BarcodeFormatType.QR_CODE
                    showLoyaltyCardDialog = true
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option 3: Import .pkpass
                SymbianImportOptionItem(
                    icon = Icons.Default.FileOpen,
                    title = "Import Apple Wallet (.pkpass)",
                    subtitle = "Select a .pkpass archive from airlines or venues",
                    accentColor = Color(0xFF64B5F6)
                ) {
                    showImportDialog = false
                    try {
                        pkpassFilePicker.launch(arrayOf("*/*", "application/vnd.apple.pkpass", "application/zip"))
                    } catch (e: Exception) {
                        pkpassFilePicker.launch(arrayOf("*/*"))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option 4: Scan Boarding Pass / Ticket Barcode (Camera or Photo)
                SymbianImportOptionItem(
                    icon = Icons.Default.CameraAlt,
                    title = "Scan Boarding Pass / Ticket",
                    subtitle = "Extract barcode with Camera or from Photos",
                    accentColor = Color(0xFFFF8A65)
                ) {
                    showImportDialog = false
                    scanChooserIntent = ScanIntent.BOARDING_PASS_OR_TICKET
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option 5: Manual Flight Pass Entry
                SymbianImportOptionItem(
                    icon = Icons.Default.FlightTakeoff,
                    title = "Enter Flight Pass Manually",
                    subtitle = "Create airline boarding pass with auto-generated barcode",
                    accentColor = Color(0xFFBA68C8)
                ) {
                    showImportDialog = false
                    showManualAddDialog = true
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SymbianGlossyButton(
                        onClick = { showImportDialog = false },
                        baseColor = Color(0xFF37474F),
                        contentColor = Color.White,
                        borderColor = Color.White.copy(alpha = 0.3f)
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Scan Source Chooser Dialog (Camera vs Photo)
    scanChooserIntent?.let { intent ->
        val title = when (intent) {
            ScanIntent.LOYALTY_CARD -> "Scan Loyalty Card"
            ScanIntent.BOARDING_PASS_OR_TICKET -> "Scan Pass or Ticket"
        }
        SymbianScanSourceChooserDialog(
            title = title,
            onDismiss = { scanChooserIntent = null },
            onCameraSelect = {
                scanChooserIntent = null
                startCameraScan(intent)
            },
            onPhotoSelect = {
                scanChooserIntent = null
                startGalleryScan(intent)
            }
        )
    }

    // Add / Edit Loyalty Card Dialog
    if (showLoyaltyCardDialog) {
        SymbianAddLoyaltyCardDialog(
            initialCode = loyaltyScannedCode,
            initialFormat = loyaltyScannedFormat,
            fontFamily = state.currentFont.toFontFamily(),
            onDismiss = { showLoyaltyCardDialog = false },
            onScanQrClick = {
                scanChooserIntent = ScanIntent.LOYALTY_CARD
            },
            onAddPass = { pass ->
                viewModel.addWalletPass(pass)
                showLoyaltyCardDialog = false
                Toast.makeText(context, "Added ${pass.organizationName} Loyalty Card!", Toast.LENGTH_SHORT).show()
                onNavigateToPassDetail(pass)
            }
        )
    }

    // Manual Flight Add Dialog
    if (showManualAddDialog) {
        SymbianManualAddFlightDialog(
            fontFamily = state.currentFont.toFontFamily(),
            onDismiss = { showManualAddDialog = false },
            onAddPass = { newPass ->
                viewModel.addWalletPass(newPass)
                showManualAddDialog = false
                Toast.makeText(context, "Added Boarding Pass!", Toast.LENGTH_SHORT).show()
                onNavigateToPassDetail(newPass)
            }
        )
    }
}

/**
 * Skeuomorphic & Symbian styled Wallet Card item.
 */
@Composable
fun SymbianWalletPassCardItem(
    pass: WalletPass,
    state: LauncherState,
    haptic: HapticFeedback,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .symbianHighlight(isFocused, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(pass.backgroundColor), RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            val logo = rememberPassLogo(pass.logoBitmapBase64)
            if (logo != null) {
                androidx.compose.foundation.Image(bitmap = logo, contentDescription = null, modifier = Modifier.padding(5.dp).fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            } else {
            Icon(
                imageVector = when (pass.type) {
                    PassType.BOARDING_PASS -> Icons.Default.FlightTakeoff
                    PassType.LOYALTY_CARD -> Icons.Default.Loyalty
                    PassType.EVENT_TICKET -> Icons.Default.ConfirmationNumber
                    PassType.COUPON -> Icons.Default.Percent
                    PassType.GENERIC -> Icons.Default.CreditCard
                },
                contentDescription = null,
                tint = Color(pass.foregroundColor),
                modifier = Modifier.size(24.dp)
            )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Text Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pass.organizationName.uppercase(),
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = state.currentFont.toFontFamily(),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            
            Text(
                text = pass.description,
                color = if (isFocused) Color.Black.copy(alpha=0.7f) else Color.White.copy(alpha=0.7f),
                fontSize = 13.sp,
                fontFamily = state.currentFont.toFontFamily(),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Symbian Pass Detail Screen with pristine skeuomorphic airline ticket / membership presentation.
 */
@Composable
fun WalletPassDetailScreen(
    pass: WalletPass,
    state: LauncherState,
    viewModel: LauncherViewModel,
    haptic: HapticFeedback,
    showFullscreenBarcode: Boolean,
    onToggleFullscreenBarcode: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCycleNext: () -> Unit = {}
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Full screen brightness while a barcode is on screen, so scanners read it first time.
    if (pass.barcode != null) {
        DisposableEffect(pass.id) {
            val window = (context as? android.app.Activity)?.window
            val previous = window?.attributes?.screenBrightness
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
            onDispose {
                window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = previous ?: -1f } }
            }
        }
    }

    val cardBg = Color(pass.backgroundColor)
    val cardFg = Color(pass.foregroundColor)
    val cardLabel = Color(pass.labelColor)

    // Generate Barcode Bitmap
    val barcodeBitmap = remember(pass.barcode) {
        if (pass.barcode != null) {
            BarcodeUtils.generateBarcodeBitmap(
                message = pass.barcode.message,
                format = pass.barcode.format,
                width = 700,
                height = if (pass.barcode.format == BarcodeFormatType.QR_CODE || pass.barcode.format == BarcodeFormatType.AZTEC) 500 else 240
            )
        } else null
    }

    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Main Boarding Pass / Card Container
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)), RoundedCornerShape(10.dp))
                    .shadow(8.dp, RoundedCornerShape(10.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                cardBg,
                                cardBg.copy(alpha = 0.95f),
                                Color((cardBg.red * 0.65f), (cardBg.green * 0.65f), (cardBg.blue * 0.65f), 1.0f)
                            )
                        )
                    )
            ) {
                // Glass sheen overlay on top half
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header Brand
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val headerLogo = rememberPassLogo(pass.logoBitmapBase64)
                            if (headerLogo != null) {
                                androidx.compose.foundation.Image(bitmap = headerLogo, contentDescription = null,
                                    modifier = Modifier.padding(end = 10.dp).height(32.dp).widthIn(max = 96.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                            } else if (pass.type == PassType.LOYALTY_CARD) {
                                SkeuomorphicEmvChip(modifier = Modifier.padding(end = 10.dp))
                            }
                            Column {
                                Text(
                                    text = pass.organizationName.uppercase(),
                                    color = cardFg,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.sp,
                                    fontFamily = state.currentFont.toFontFamily(),
                                    style = TextStyle(
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(1f, 1f), blurRadius = 2f)
                                    )
                                )
                                Text(
                                    text = pass.description,
                                    color = cardLabel,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = when (pass.type) {
                                PassType.BOARDING_PASS -> Icons.Default.Flight
                                PassType.LOYALTY_CARD -> Icons.Default.Loyalty
                                PassType.EVENT_TICKET -> Icons.Default.ConfirmationNumber
                                else -> Icons.Default.CreditCard
                            },
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (pass.type == PassType.BOARDING_PASS) {
                        // Route Section
                        val origin = pass.originAirport ?: "DEP"
                        val dest = pass.destinationAirport ?: "ARR"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(origin, color = cardFg, fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                Text(pass.originCity ?: "Origin Airport", color = cardLabel, fontSize = 11.sp)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(24.dp)
                                )
                                if (!pass.flightNumber.isNullOrEmpty()) {
                                    Text(pass.flightNumber, color = cardFg, fontWeight = FontWeight.Black, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(dest, color = cardFg, fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                Text(pass.destinationCity ?: "Destination Airport", color = cardLabel, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        DashedDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Flight Info Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SymbianPassDetailField("PASSENGER", pass.passengerName ?: "PASSENGER", cardLabel, cardFg)
                            SymbianPassDetailField("DATE", pass.departureDate ?: "21 AUG", cardLabel, Color(0xFFFFD54F), alignEnd = true)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SymbianPassDetailField("BOARDING", pass.boardingTime ?: "09:40", cardLabel, cardFg)
                            SymbianPassDetailField("GATE", pass.gate ?: "B34", cardLabel, Color(0xFFFFD54F))
                            SymbianPassDetailField("SEAT", pass.seat ?: "04K", cardLabel, cardFg, isHighlighted = true)
                            SymbianPassDetailField("CLASS", pass.flightClass ?: "Economy", cardLabel, cardFg, alignEnd = true)
                        }

                        if (!pass.pnrCode.isNullOrEmpty() || !pass.sequenceNumber.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                if (!pass.pnrCode.isNullOrEmpty()) {
                                    SymbianPassDetailField("BOOKING REF", pass.pnrCode, cardLabel, cardFg)
                                }
                                if (!pass.sequenceNumber.isNullOrEmpty()) {
                                    SymbianPassDetailField("SEQUENCE", pass.sequenceNumber, cardLabel, cardFg, alignEnd = true)
                                }
                            }
                        }
                    } else {
                        // Generic / Loyalty Fields
                        pass.primaryFields.forEach { f ->
                            SymbianPassDetailField(f.label.uppercase(), f.value, cardLabel, cardFg)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            pass.secondaryFields.forEach { f ->
                                SymbianPassDetailField(f.label.uppercase(), f.value, cardLabel, cardFg)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Barcode Box Section (High-Contrast White Plate for Gate Scanners)
                    if (pass.barcode != null && barcodeBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFB0BEC5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleFullscreenBarcode(true)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    bitmap = barcodeBitmap.asImageBitmap(),
                                    contentDescription = "Boarding Pass Barcode",
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(if (pass.barcode.format == BarcodeFormatType.QR_CODE || pass.barcode.format == BarcodeFormatType.AZTEC) 160.dp else 85.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = pass.barcode.altText ?: pass.barcode.message.take(30),
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tap to enlarge for scanner",
                                    color = Color(0xFF546E7A),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Back Fields / Additional Information Section
        if (pass.backFields.isNotEmpty() || pass.auxiliaryFields.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "PASS DETAILS & INFORMATION",
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = state.currentFont.toFontFamily()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        DashedDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        pass.auxiliaryFields.forEach { f ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(f.label, color = Color.LightGray, fontSize = 12.sp)
                                Text(f.value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        pass.backFields.forEach { f ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(f.label, color = Color(0xFF90CAF9), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(f.value, color = Color.White, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }

        // Actions: Delete Pass
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SymbianGlossyButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDeleteConfirm = true
                },
                baseColor = Color(0xFFC62828),
                contentColor = Color.White,
                borderColor = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete Pass from Wallet", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Fullscreen Barcode Modal (for scanning through airport scanners)
    // Fullscreen Barcode Modal (for scanning through airport scanners)
    if ((showFullscreenBarcode || pass.type == PassType.LOYALTY_CARD) && barcodeBitmap != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (pass.type != PassType.LOYALTY_CARD) onToggleFullscreenBarcode(false) }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = pass.organizationName.uppercase(),
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = 1.sp
            )
            if (!pass.flightNumber.isNullOrEmpty()) {
                Text(
                    text = "${pass.flightNumber} • Seat ${pass.seat ?: ""}",
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(26.dp))
            Image(
                bitmap = barcodeBitmap.asImageBitmap(),
                contentDescription = "Full Barcode",
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(if (pass.barcode?.format == BarcodeFormatType.QR_CODE || pass.barcode?.format == BarcodeFormatType.AZTEC) 280.dp else 160.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = pass.barcode?.altText ?: pass.barcode?.message ?: "",
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    // Confirm Delete Dialog
    if (showDeleteConfirm) {
        SymbianDialogFrame(onDismissRequest = { showDeleteConfirm = false }) {
            Column {
                Text(
                    text = "DELETE PASS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFFFF5252),
                    fontFamily = state.currentFont.toFontFamily()
                )
                Spacer(modifier = Modifier.height(8.dp))
                DashedDivider()
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Are you sure you want to remove ${pass.organizationName} (${pass.description}) from your Wallet?",
                    color = Color.White,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    SymbianGlossyButton(
                        onClick = { showDeleteConfirm = false },
                        baseColor = Color(0xFF37474F),
                        contentColor = Color.White
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    SymbianGlossyButton(
                        onClick = {
                            viewModel.deleteWalletPass(pass.id)
                            showDeleteConfirm = false
                            Toast.makeText(context, "Pass deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        baseColor = Color(0xFFC62828),
                        contentColor = Color.White
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
    }

@Composable
fun SymbianPassDetailField(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    alignEnd: Boolean = false,
    isHighlighted: Boolean = false
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, color = labelColor, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        if (isHighlighted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        } else {
            Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Reusable Symbian Dialog Frame container with classic S60 slate background and top highlight.
 */
@Composable
fun SymbianDialogFrame(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = RoundedCornerShape(10.dp)
        Surface(
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .shadow(12.dp, shape)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF263238),
                                Color(0xFF1E272C),
                                Color(0xFF12171A)
                            )
                        )
                    )
            ) {
                // Top gloss line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.6f))
                )

                Column(
                    modifier = Modifier.padding(16.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * Symbian import option row item with hover/focus support.
 */
@Composable
fun SymbianImportOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .symbianHighlight(isFocused, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (isFocused) Color.White else Color.White.copy(alpha = 0.12f)
                ),
                RoundedCornerShape(6.dp)
            )
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accentColor.copy(alpha = 0.2f), CircleShape)
                .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = subtitle,
                color = if (isFocused) Color.DarkGray else Color.LightGray,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Scan Source Chooser Dialog (Camera vs Photo) in Symbian aesthetic.
 */
@Composable
fun SymbianScanSourceChooserDialog(
    title: String,
    onDismiss: () -> Unit,
    onCameraSelect: () -> Unit,
    onPhotoSelect: () -> Unit
) {
    SymbianDialogFrame(onDismissRequest = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        DashedDivider()
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Choose whether to scan with your camera or select a photo from gallery:",
            fontSize = 12.sp,
            color = Color.LightGray
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Camera Option
        SymbianImportOptionItem(
            icon = Icons.Default.CameraAlt,
            title = "Use Camera",
            subtitle = "Take a photo of QR or barcode",
            accentColor = Color(0xFFFFD54F),
            onClick = onCameraSelect
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Photos Option
        SymbianImportOptionItem(
            icon = Icons.Default.PhotoLibrary,
            title = "Choose from Photos",
            subtitle = "Select image or screenshot",
            accentColor = Color(0xFF64B5F6),
            onClick = onPhotoSelect
        )

        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SymbianGlossyButton(
                onClick = onDismiss,
                baseColor = Color(0xFF37474F),
                contentColor = Color.White
            ) {
                Text("Cancel", fontSize = 13.sp)
            }
        }
    }
}

/**
 * Add / Edit Loyalty Card Dialog in Symbian UI.
 */
@Composable
fun SymbianAddLoyaltyCardDialog(
    initialCode: String,
    initialFormat: BarcodeFormatType,
    fontFamily: FontFamily,
    onDismiss: () -> Unit,
    onScanQrClick: () -> Unit,
    onAddPass: (WalletPass) -> Unit
) {
    var brandName by remember { mutableStateOf("") }
    var memberId by remember(initialCode) { mutableStateOf(initialCode) }
    var memberName by remember { mutableStateOf("") }
    var tier by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("") }
    var selectedFormat by remember(initialFormat) { mutableStateOf(initialFormat) }

    val colorPresets = listOf(
        Pair("Emerald", 0xFF006241L),
        Pair("Navy", 0xFF003087L),
        Pair("IKEA Blue", 0xFF0058A3L),
        Pair("Ruby", 0xFF8B0000L),
        Pair("Amber", 0xFF996515L),
        Pair("Midnight", 0xFF212121L),
        Pair("Purple", 0xFF4A148CL)
    )
    var selectedColor by remember { mutableStateOf(0xFF006241L) }

    val quickBrands = listOf("Tesco", "Starbucks", "IKEA", "Nectar", "Costa", "Sephora", "Target", "Subway")

    // Live preview barcode bitmap
    val previewBarcodeBitmap = remember(memberId, selectedFormat) {
        val codeToRender = memberId.ifBlank { "0123456789" }
        BarcodeUtils.generateBarcodeBitmap(
            message = codeToRender,
            format = selectedFormat,
            width = 400,
            height = if (selectedFormat == BarcodeFormatType.QR_CODE || selectedFormat == BarcodeFormatType.AZTEC) 300 else 140
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = RoundedCornerShape(10.dp)
        Surface(
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 8.dp)
                .shadow(12.dp, shape)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF263238), Color(0xFF1E272C), Color(0xFF12171A))
                        )
                    )
            ) {
                // Top gloss line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.6f))
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SkeuomorphicEmvChip()
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ADD LOYALTY CARD", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, fontFamily = fontFamily)
                            }

                            SymbianGlossyButton(
                                onClick = onScanQrClick,
                                baseColor = Color(0xFFFFD54F),
                                contentColor = Color.Black
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        DashedDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Live Card Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(selectedColor),
                                            Color(selectedColor).copy(alpha = 0.9f),
                                            Color((Color(selectedColor).red * 0.7f), (Color(selectedColor).green * 0.7f), (Color(selectedColor).blue * 0.7f), 1f)
                                        )
                                    )
                                )
                        ) {
                            // Gloss overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)))
                            )

                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        SkeuomorphicEmvChip()
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = brandName.ifBlank { "STORE / REWARDS" }.uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    if (tier.isNotBlank()) {
                                        Text(tier, color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text("MEMBER ID", color = Color(0xFFFFD54F), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = memberId.ifBlank { "Scan QR or enter number..." },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                if (memberName.isNotBlank() || points.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (memberName.isNotBlank()) {
                                            Text(memberName, color = Color.LightGray, fontSize = 10.sp)
                                        }
                                        if (points.isNotBlank()) {
                                            Text(points, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        }
                                    }
                                }

                                if (previewBarcodeBitmap != null && memberId.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.White,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Image(
                                                bitmap = previewBarcodeBitmap.asImageBitmap(),
                                                contentDescription = "Barcode Preview",
                                                modifier = Modifier
                                                    .fillMaxWidth(0.9f)
                                                    .height(if (selectedFormat == BarcodeFormatType.QR_CODE || selectedFormat == BarcodeFormatType.AZTEC) 100.dp else 45.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = selectedFormat.name,
                                                color = Color.Black,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Quick Brand Suggestions
                    item {
                        Text("Quick Brand Suggestions:", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(quickBrands) { b ->
                                val isSel = brandName == b
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.2f)
                                            ),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .background(if (isSel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f))
                                        .clickable {
                                            brandName = b
                                            when (b) {
                                                "Starbucks" -> selectedColor = 0xFF006241L
                                                "Tesco" -> selectedColor = 0xFF003087L
                                                "IKEA" -> selectedColor = 0xFF0058A3L
                                                "Costa" -> selectedColor = 0xFF8B0000L
                                                "Nectar" -> selectedColor = 0xFF4A148CL
                                                "Sephora" -> selectedColor = 0xFF212121L
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(b, color = if (isSel) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Form Inputs
                    item {
                        OutlinedTextField(
                            value = brandName,
                            onValueChange = { brandName = it },
                            label = { Text("Store / Card Name *") },
                            placeholder = { Text("e.g. Tesco Clubcard, Starbucks") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = memberId,
                            onValueChange = { memberId = it },
                            label = { Text("Membership / QR Code Payload *") },
                            placeholder = { Text("Scan QR or enter number") },
                            trailingIcon = {
                                IconButton(onClick = onScanQrClick) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = Color(0xFFFFD54F))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = memberName,
                                onValueChange = { memberName = it },
                                label = { Text("Member Name (Opt)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = tier,
                                onValueChange = { tier = it },
                                label = { Text("Tier (e.g. Gold)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = points,
                            onValueChange = { points = it },
                            label = { Text("Points / Balance (Optional)") },
                            placeholder = { Text("e.g. 450 Stars, £12.00") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Color Presets
                        Text("Card Color Theme:", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(colorPresets) { (_, colorVal) ->
                                val isSelected = selectedColor == colorVal
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorVal))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = colorVal },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Barcode Format Selector
                        Text("Barcode Format:", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                BarcodeFormatType.QR_CODE to "QR Code",
                                BarcodeFormatType.CODE_128 to "Code 128",
                                BarcodeFormatType.AZTEC to "Aztec",
                                BarcodeFormatType.PDF_417 to "PDF417"
                            ).forEach { (format, label) ->
                                val isSel = selectedFormat == format
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (isSel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.2f)
                                            ),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .background(if (isSel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f))
                                        .clickable { selectedFormat = format }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            SymbianGlossyButton(
                                onClick = onDismiss,
                                baseColor = Color(0xFF37474F),
                                contentColor = Color.White
                            ) {
                                Text("Cancel", fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            SymbianGlossyButton(
                                onClick = {
                                    if (brandName.isBlank() || memberId.isBlank()) return@SymbianGlossyButton
                                    val newPass = WalletPass(
                                        id = UUID.randomUUID().toString(),
                                        type = PassType.LOYALTY_CARD,
                                        organizationName = brandName.trim(),
                                        description = if (tier.isNotBlank()) "${tier.trim()} Membership" else "Loyalty Card",
                                        logoText = brandName.trim().uppercase(),
                                        backgroundColor = selectedColor,
                                        foregroundColor = 0xFFFFFFFF,
                                        labelColor = 0xFFFFD54F,
                                        membershipTier = tier.ifBlank { null },
                                        balanceOrPoints = points.ifBlank { null },
                                        headerFields = if (points.isNotBlank()) listOf(PassField("points", "POINTS / BAL", points)) else emptyList(),
                                        primaryFields = listOf(PassField("card_num", "MEMBER ID", memberId.trim())),
                                        secondaryFields = if (memberName.isNotBlank()) listOf(
                                            PassField("member_name", "MEMBER", memberName.trim())
                                        ) else emptyList(),
                                        barcode = PassBarcode(
                                            message = memberId.trim(),
                                            format = selectedFormat,
                                            altText = memberId.trim()
                                        )
                                    )
                                    onAddPass(newPass)
                                },
                                enabled = brandName.isNotBlank() && memberId.isNotBlank(),
                                baseColor = Color(0xFFFFD54F),
                                contentColor = Color.Black
                            ) {
                                Text("Save Card", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Manual Flight Pass Entry Dialog in Symbian aesthetic.
 */
@Composable
fun SymbianManualAddFlightDialog(
    fontFamily: FontFamily,
    onDismiss: () -> Unit,
    onAddPass: (WalletPass) -> Unit
) {
    var airline by remember { mutableStateOf("British Airways") }
    var flightNumber by remember { mutableStateOf("BA 178") }
    var passenger by remember { mutableStateOf("ALEX JOHNSON") }
    var origin by remember { mutableStateOf("LHR") }
    var destination by remember { mutableStateOf("JFK") }
    var gate by remember { mutableStateOf("B34") }
    var seat by remember { mutableStateOf("04K") }
    var boardingTime by remember { mutableStateOf("09:40") }
    var flightDate by remember { mutableStateOf("24 OCT 2026") }

    SymbianDialogFrame(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FlightTakeoff, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CREATE BOARDING PASS", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, fontFamily = fontFamily)
            }
            Spacer(modifier = Modifier.height(6.dp))
            DashedDivider()
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = airline,
                onValueChange = { airline = it },
                label = { Text("Airline") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = origin,
                    onValueChange = { origin = it.uppercase() },
                    label = { Text("From (e.g. LHR)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it.uppercase() },
                    label = { Text("To (e.g. JFK)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = flightNumber,
                    onValueChange = { flightNumber = it.uppercase() },
                    label = { Text("Flight #") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = passenger,
                    onValueChange = { passenger = it.uppercase() },
                    label = { Text("Passenger") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = gate,
                    onValueChange = { gate = it.uppercase() },
                    label = { Text("Gate") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = seat,
                    onValueChange = { seat = it.uppercase() },
                    label = { Text("Seat") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = boardingTime,
                    onValueChange = { boardingTime = it },
                    label = { Text("Boarding") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SymbianGlossyButton(
                    onClick = onDismiss,
                    baseColor = Color(0xFF37474F),
                    contentColor = Color.White
                ) {
                    Text("Cancel", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                SymbianGlossyButton(
                    onClick = {
                        val pnr = "BK" + (1000..9999).random()
                        val pass = WalletPass(
                            id = UUID.randomUUID().toString(),
                            type = PassType.BOARDING_PASS,
                            organizationName = airline.ifBlank { "Airline" },
                            description = "$origin to $destination",
                            backgroundColor = 0xFF0B2545,
                            foregroundColor = 0xFFFFFFFF,
                            labelColor = 0xFF90CAF9,
                            transitType = TransitType.AIR,
                            passengerName = passenger.ifBlank { "PASSENGER" },
                            flightNumber = flightNumber.ifBlank { "FL 100" },
                            originAirport = origin.ifBlank { "DEP" },
                            destinationAirport = destination.ifBlank { "ARR" },
                            departureDate = flightDate,
                            boardingTime = boardingTime,
                            gate = gate,
                            seat = seat,
                            pnrCode = pnr,
                            barcode = PassBarcode(
                                message = "M1${passenger.padEnd(20, ' ')}E${pnr.padEnd(7, ' ')}${origin.padEnd(3, ' ')}${destination.padEnd(3, ' ')}${flightNumber.replace(" ", "").padEnd(7, ' ')}297Y${seat.padStart(4, '0')}0042 100",
                                format = BarcodeFormatType.PDF_417,
                                altText = "$pnr - ETKT"
                            )
                        )
                        onAddPass(pass)
                    },
                    baseColor = Color(0xFFFFD54F),
                    contentColor = Color.Black
                ) {
                    Text("Create Pass", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}


/** Decodes a pass logo (base64 PNG) once and caches it for recompositions. */
@Composable
fun rememberPassLogo(base64: String?): androidx.compose.ui.graphics.ImageBitmap? = remember(base64) {
    if (base64.isNullOrBlank()) null else try {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) { null }
}
