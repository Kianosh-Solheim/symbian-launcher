package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

/** Sharing a contact: as a QR code to scan with another phone, or as a vCard through Android's share menu. */
object ContactShare {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    fun vCard(c: ContactInfo): String = buildString {
        append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
        append("FN:${esc(c.name)}\r\n")
        val parts = c.name.trim().split(Regex("\\s+"))
        val last = if (parts.size > 1) parts.last() else ""
        val first = if (parts.size > 1) parts.dropLast(1).joinToString(" ") else c.name.trim()
        append("N:${esc(last)};${esc(first)};;;\r\n")
        c.phoneNumbers.forEach { p ->
            val type = when (p.label.lowercase()) { "mobile", "mobil" -> "CELL"; "work", "jobb" -> "WORK"; "home", "hjem" -> "HOME"; else -> "VOICE" }
            append("TEL;TYPE=$type:${p.number.replace(" ", "")}\r\n")
        }
        c.emails.forEach { append("EMAIL:${esc(it.address)}\r\n") }
        c.organization?.let { o ->
            if (o.company.isNotBlank()) append("ORG:${esc(o.company)}\r\n")
            if (o.title.isNotBlank()) append("TITLE:${esc(o.title)}\r\n")
        }
        c.addresses.forEach { append("ADR:;;${esc(it.formattedAddress.replace("\n", ", "))};;;;\r\n") }
        c.websites.forEach { append("URL:${esc(it)}\r\n") }
        c.birthday?.takeIf { it.isNotBlank() }?.let { append("BDAY:$it\r\n") }
        append("END:VCARD\r\n")
    }

    fun qr(text: String, size: Int = 640): Bitmap? = try {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2))
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    } catch (_: Exception) { null }

    /** Android's own share menu with the contact as a .vcf card (Messages, Gmail, Bluetooth, Drive …). */
    fun shareMore(ctx: Context, c: ContactInfo) {
        val card = vCard(c)
        val text = buildString {
            append(c.name)
            c.phoneNumbers.forEach { append("\n${it.label}: ${it.number}") }
            c.emails.forEach { append("\n${it.label}: ${it.address}") }
        }
        val i = Intent(Intent.ACTION_SEND)
        try {
            val dir = File(ctx.cacheDir, "contacts").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val safe = c.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "contact" }
            val f = File(dir, "$safe.vcf").apply { writeText(card) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.mmsfiles", f)
            i.type = "text/x-vcard"
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.clipData = android.content.ClipData.newRawUri(c.name, uri)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            i.type = "text/plain"
        }
        i.putExtra(Intent.EXTRA_TEXT, text)
        i.putExtra(Intent.EXTRA_SUBJECT, c.name)
        try { ctx.startActivity(Intent.createChooser(i, "Share ${c.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
    }
}

@Composable
private fun ShareRow(text: String, sub: String, focus: FocusRequester? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val foc by src.collectIsFocusedAsState()
    Column(
        Modifier.fillMaxWidth()
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .symbianHighlight(foc)
            .focusable(interactionSource = src)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold, color = if (foc) Color.Black else LocalContentColor.current)
        Text(sub, fontSize = 12.sp, color = if (foc) Color.DarkGray else LocalContentColor.current.copy(alpha = 0.7f))
    }
}

/** Share → "Show QR code" or "More options…" (the normal Android share menu). */
@Composable
fun ContactShareDialog(contact: ContactInfo, onDone: () -> Unit) {
    val context = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    if (!showQr) {
        LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) { } }
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text("Share ${contact.name}") },
            text = {
                Column {
                    ShareRow("Show QR code", "Scan it with another phone's camera to save the contact", first) { showQr = true }
                    ShareRow("More options…", "Messages, email, Bluetooth and other apps") { onDone(); ContactShare.shareMore(context, contact) }
                }
            },
            confirmButton = { TextButton(onClick = onDone) { Text("Cancel") } }
        )
    } else {
        val bmp = remember(contact) { ContactShare.qr(ContactShare.vCard(contact)) }
        // Full brightness while the code is shown, so it scans easily.
        DisposableEffect(Unit) {
            val w = (context as? android.app.Activity)?.window
            val prev = w?.attributes?.screenBrightness
            w?.let { it.attributes = it.attributes.apply { screenBrightness = 1f } }
            onDispose { w?.let { it.attributes = it.attributes.apply { screenBrightness = prev ?: -1f } } }
        }
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(contact.name) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (bmp != null) Box(Modifier.background(Color.White).padding(6.dp)) {
                        Image(bmp.asImageBitmap(), contentDescription = "QR code for ${contact.name}",
                            filterQuality = FilterQuality.None, modifier = Modifier.size(240.dp))
                    } else Text("This contact has too much information for one QR code.")
                    Spacer(Modifier.height(6.dp))
                    Text("Point another phone's camera at the code", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = onDone) { Text("Done") } }
        )
    }
}
