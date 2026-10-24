package com.example

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live QR and barcode scanner: point the camera at a code and it is read by itself — no photo to
 * take. Reads QR, Aztec, PDF417 (boarding passes), Data Matrix and all common 1D barcodes in any
 * direction. Keys: OK = torch, * = pick a photo instead, # / Back = cancel.
 */
@Composable
fun WalletScannerDialog(
    title: String = "Scan code",
    onResult: (BarcodeUtils.ScannedBarcodeResult) -> Unit,
    onPickPhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; if (!ok) onDismiss() }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val done = remember { AtomicBoolean(false) }
    val focus = remember { FocusRequester() }

    fun toggleTorch() { torch = !torch; try { camera?.cameraControl?.enableTorch(torch) } catch (_: Exception) { } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val n = e.nativeKeyEvent.keyCode
                    when {
                        e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter -> { toggleTorch(); true }
                        n == android.view.KeyEvent.KEYCODE_STAR || n == android.view.KeyEvent.KEYCODE_SOFT_LEFT -> { onPickPhoto(); true }
                        n == android.view.KeyEvent.KEYCODE_POUND || n == android.view.KeyEvent.KEYCODE_SOFT_RIGHT -> { onDismiss(); true }
                        else -> false
                    }
                }
        ) {
            LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) { } }
            if (granted) CameraPreview(onCamera = { camera = it }) { result ->
                if (done.compareAndSet(false, true)) {
                    try {
                        @Suppress("DEPRECATION")
                        (context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator)
                            .vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } catch (_: Exception) { }
                    onResult(result)
                }
            }
            // Darkened surround with a clear window in the middle.
            Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                val w = size.width * 0.78f; val h = minOf(w, size.height * 0.45f)
                val tl = Offset((size.width - w) / 2, (size.height - h) / 2 - size.height * 0.04f)
                drawRect(Color.Black.copy(alpha = 0.55f))
                drawRoundRect(Color.Transparent, tl, Size(w, h), CornerRadius(18.dp.toPx()), blendMode = BlendMode.Clear)
                drawRoundRect(Color(0xFFFFD54F), tl, Size(w, h), CornerRadius(18.dp.toPx()), style = Stroke(3.dp.toPx()))
            }
            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Hold the code inside the frame — it's read by itself", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScanButton("Photo (*)", Modifier.weight(1f)) { onPickPhoto() }
                ScanButton(if (torch) "Light off (OK)" else "Light (OK)", Modifier.weight(1f)) { toggleTorch() }
                ScanButton("Cancel (#)", Modifier.weight(1f)) { onDismiss() }
            }
        }
    }
}

@Composable
private fun ScanButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun CameraPreview(onCamera: (Camera) -> Unit, onFound: (BarcodeUtils.ScannedBarcodeResult) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get(); provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                val decoder = FrameDecoder()
                analysis.setAnalyzer(executor) { img ->
                    try { decoder.decode(img)?.let { r -> view.post { onFound(r) } } } catch (_: Exception) { } finally { img.close() }
                }
                p.unbindAll()
                onCamera(p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
            } catch (e: Exception) { android.util.Log.w("WalletScanner", "Camera failed", e) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { try { provider?.unbindAll() } catch (_: Exception) { }; executor.shutdown() }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}

/** Reads codes from camera frames; every other frame is turned 90° so bars work in any direction. */
private class FrameDecoder {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(
            DecodeHintType.POSSIBLE_FORMATS to com.google.zxing.BarcodeFormat.values().filter { it != com.google.zxing.BarcodeFormat.MAXICODE && it != com.google.zxing.BarcodeFormat.RSS_EXPANDED && it != com.google.zxing.BarcodeFormat.UPC_EAN_EXTENSION },
            DecodeHintType.TRY_HARDER to true
        ))
    }
    private var frame = 0
    private var buf = ByteArray(0)
    private var rot = ByteArray(0)

    fun decode(img: androidx.camera.core.ImageProxy): BarcodeUtils.ScannedBarcodeResult? {
        val plane = img.planes[0]
        val w = img.width; val h = img.height; val stride = plane.rowStride
        val bb = plane.buffer
        if (buf.size != stride * h) buf = ByteArray(stride * h)
        bb.rewind(); bb.get(buf, 0, minOf(bb.remaining(), buf.size))
        // Look at the centre 80 % (where the frame is) — faster and fewer false reads.
        val cw = (w * 0.8f).toInt(); val ch = (h * 0.8f).toInt(); val cx = (w - cw) / 2; val cy = (h - ch) / 2
        frame++
        val src = if (frame % 2 == 0) PlanarYUVLuminanceSource(buf, stride, h, cx, cy, cw, ch, false)
        else {
            // Rotate the centre crop 90° clockwise.
            if (rot.size != cw * ch) rot = ByteArray(cw * ch)
            for (y in 0 until ch) { val row = (cy + y) * stride + cx; for (x in 0 until cw) rot[x * ch + (ch - 1 - y)] = buf[row + x] }
            PlanarYUVLuminanceSource(rot, ch, cw, 0, 0, ch, cw, false)
        }
        val r = try { reader.decodeWithState(BinaryBitmap(HybridBinarizer(src))) } catch (_: Exception) { null } finally { reader.reset() }
        return r?.let { BarcodeUtils.ScannedBarcodeResult(it.text, BarcodeUtils.fromZxing(it.barcodeFormat)) }
    }
}
