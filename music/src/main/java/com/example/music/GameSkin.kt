package com.example.music

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The launcher's current theme, read from its LookProvider. Falls back to the green LCD look. */
data class GameLook(val style: String = "lcd", val bg: Int = 0, val surface: Int = 0, val text: Int = 0, val accent: Int = 0, val highlight: Int = 0) {
    companion object {
        fun load(ctx: Context): GameLook = try {
            ctx.contentResolver.query(Uri.parse("content://no.kianosh.symbianlauncher.look"), null, null, null, null)?.use { c ->
                if (c.moveToFirst()) GameLook(c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5)) else null
            } ?: GameLook()
        } catch (_: Exception) { GameLook() }
    }
}

enum class SkinKind { LCD, WIN95, S60, SYMBIAN }

class Skin(
    val kind: SkinKind,
    val screen: Brush,
    val field: Color,
    val ink: Color,          // default block / snake colour
    val text: Color,
    val dim: Color,          // grid dots, ghost piece
    val overlay: Color,
    val overlayText: Color,
    val barBg: Brush,
    val barText: Color,
    val font: FontFamily,
    val accent: Color,
    val food: Color,
) {
    val colored get() = kind != SkinKind.LCD
}

val PixelFont = FontFamily(Font(R.font.pixelify_sans_regular), Font(R.font.pixelify_sans_bold, FontWeight.Bold))

val LcdBgC = Color(0xFF9CB86A)
val LcdInkC = Color(0xFF26331A)
val LcdDimC = Color(0xFF8AA55A)
val Win95Grey = Color(0xFFC0C0C0)
val Win95Dark = Color(0xFF808080)
val Win95Navy = Color(0xFF000080)

fun skinFor(l: GameLook): Skin = when (l.style) {
    "win95" -> Skin(SkinKind.WIN95, Brush.verticalGradient(listOf(Win95Grey, Win95Grey)), Color.Black, Color(0xFF00C000), Color.Black,
        Color(0xFF303030), Color.Black.copy(alpha = 0.82f), Color.White, Brush.verticalGradient(listOf(Win95Grey, Win95Grey)), Color.Black,
        FontFamily.SansSerif, Win95Navy, Color(0xFFFF0000))
    "s60" -> {
        val bg = Color(l.bg); val sf = Color(l.surface); val ac = Color(l.accent)
        Skin(SkinKind.S60, Brush.verticalGradient(listOf(lerp(sf, Color.White, 0.35f), bg)), Color.White.copy(alpha = 0.55f), ac, Color(l.text),
            Color(l.text).copy(alpha = 0.18f), Color.White.copy(alpha = 0.86f), Color(l.text),
            Brush.verticalGradient(listOf(lerp(ac, Color.White, 0.35f), ac)), Color.White, FontFamily.SansSerif, ac, Color(0xFFE53935))
    }
    "symbian" -> {
        val bg = Color(l.bg); val sf = Color(l.surface); val tx = Color(l.text); val ac = Color(l.accent)
        Skin(SkinKind.SYMBIAN, Brush.verticalGradient(listOf(bg, bg)), sf, ac, tx, tx.copy(alpha = 0.16f), bg.copy(alpha = 0.88f), tx,
            Brush.verticalGradient(listOf(sf, sf)), tx, FontFamily.SansSerif, ac, Color(0xFFEF5350))
    }
    else -> Skin(SkinKind.LCD, Brush.verticalGradient(listOf(LcdBgC, LcdBgC)), LcdBgC, LcdInkC, LcdInkC, LcdDimC, LcdBgC.copy(alpha = 0.9f), LcdInkC,
        Brush.verticalGradient(listOf(LcdDimC, LcdDimC)), LcdInkC, PixelFont, LcdInkC, LcdInkC)
}

/** One square cell of a block or snake segment, drawn in the skin's style. */
fun DrawScope.skinCell(skin: Skin, l: Float, t: Float, s: Float, color: Color) {
    when (skin.kind) {
        SkinKind.LCD -> {
            drawRect(skin.ink, Offset(l, t), Size(s, s))
            drawRect(skin.field, Offset(l + s * 0.22f, t + s * 0.22f), Size(s * 0.56f, s * 0.56f))
            drawRect(skin.ink, Offset(l + s * 0.36f, t + s * 0.36f), Size(s * 0.28f, s * 0.28f))
        }
        SkinKind.WIN95 -> {
            // Raised bevel: light top-left, dark bottom-right.
            val b = (s * 0.16f).coerceAtLeast(1.5f)
            drawRect(color, Offset(l, t), Size(s, s))
            val hi = lerp(color, Color.White, 0.6f); val lo = lerp(color, Color.Black, 0.5f)
            drawRect(hi, Offset(l, t), Size(s, b)); drawRect(hi, Offset(l, t), Size(b, s))
            drawRect(lo, Offset(l, t + s - b), Size(s, b)); drawRect(lo, Offset(l + s - b, t), Size(b, s))
        }
        SkinKind.S60 -> {
            val r = CornerRadius(s * 0.22f)
            drawRoundRect(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.45f), color, lerp(color, Color.Black, 0.25f)), t, t + s), Offset(l, t), Size(s, s), r)
            drawRoundRect(Color.White.copy(alpha = 0.38f), Offset(l + s * 0.1f, t + s * 0.06f), Size(s * 0.8f, s * 0.4f), CornerRadius(s * 0.18f))
        }
        SkinKind.SYMBIAN -> {
            drawRoundRect(color, Offset(l, t), Size(s, s), CornerRadius(s * 0.15f))
            drawRoundRect(lerp(color, Color.White, 0.3f), Offset(l + s * 0.12f, t + s * 0.12f), Size(s * 0.76f, s * 0.18f), CornerRadius(s * 0.08f))
        }
    }
}

/** Win95 "sunken" 3D edge around a box. */
fun Modifier.win95Sunken(): Modifier = this.drawBehind {
    val w = 2.dp.toPx()
    drawRect(Win95Dark, Offset.Zero, Size(size.width, w)); drawRect(Win95Dark, Offset.Zero, Size(w, size.height))
    drawRect(Color.White, Offset(0f, size.height - w), Size(size.width, w)); drawRect(Color.White, Offset(size.width - w, 0f), Size(w, size.height))
}.padding(2.dp)

fun Modifier.win95Raised(): Modifier = this.background(Win95Grey).drawBehind {
    val w = 2.dp.toPx()
    drawRect(Color.White, Offset.Zero, Size(size.width, w)); drawRect(Color.White, Offset.Zero, Size(w, size.height))
    drawRect(Win95Dark, Offset(0f, size.height - w), Size(size.width, w)); drawRect(Win95Dark, Offset(size.width - w, 0f), Size(w, size.height))
}

/** The whole-screen frame: a Win95 window with title and menu bar, or plain themed background. */
@Composable
fun GameFrame(skin: Skin, title: String, menu: List<String>, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(skin.screen).systemBarsPadding()) {
        if (skin.kind == SkinKind.WIN95) {
            Row(Modifier.fillMaxWidth().padding(2.dp).background(Brush.horizontalGradient(listOf(Win95Navy, Color(0xFF1084D0)))).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(Color(0xFF00C000)).border(1.dp, Color.Black))
                Spacer(Modifier.width(5.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.SansSerif, modifier = Modifier.weight(1f))
                listOf("_", "□", "×").forEach {
                    Box(Modifier.padding(start = 2.dp).size(16.dp, 14.dp).win95Raised(), contentAlignment = Alignment.Center) {
                        Text(it, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                menu.forEach { Text(it, color = Color.Black, fontSize = 13.sp, fontFamily = FontFamily.SansSerif, modifier = Modifier.padding(end = 14.dp)) }
            }
        }
        content()
    }
}

/** Score-type readout: LED digits in a sunken black box on Win95, plain text elsewhere. */
@Composable
fun Counter(skin: Skin, label: String, value: Int, digits: Int = 6, big: Boolean = true) {
    when (skin.kind) {
        SkinKind.WIN95 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Win95Navy, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Serif)
            Box(Modifier.win95Sunken().background(Color.Black).padding(horizontal = 4.dp, vertical = 1.dp)) {
                Text(value.toString().padStart(digits, '0').takeLast(digits), color = Color(0xFFE8E8E8), fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = if (big) 20.sp else 16.sp, letterSpacing = 2.sp)
            }
        }
        else -> Column {
            Text(label.uppercase(), color = skin.text.copy(alpha = if (skin.colored) 0.7f else 1f), fontFamily = skin.font, fontSize = 12.sp)
            Text(value.toString(), color = skin.text, fontFamily = skin.font, fontWeight = FontWeight.Bold, fontSize = if (big) 20.sp else 16.sp)
        }
    }
}

/** Container for the playfield: sunken black well on Win95, rounded panel on S60/Symbian, ink frame on LCD. */
@Composable
fun FieldBox(skin: Skin, w: Dp, h: Dp, content: @Composable BoxScope.() -> Unit) {
    val m = when (skin.kind) {
        SkinKind.WIN95 -> Modifier.win95Sunken().background(skin.field)
        SkinKind.LCD -> Modifier.border(2.dp, skin.ink).padding(2.dp).background(skin.field)
        else -> Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, skin.accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).background(skin.field).padding(2.dp)
    }
    Box(m.size(w, h), content = content)
}

/** Soft-key labels at the bottom (left / right), as raised buttons on Win95. */
@Composable
fun SoftKeys(skin: Skin, left: String, right: String) {
    if (skin.kind == SkinKind.WIN95) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(left, right).forEach {
                Box(Modifier.width(96.dp).win95Raised().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Text(it, color = Color.Black, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().background(skin.barBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(left, color = skin.barText, fontWeight = FontWeight.Bold, fontFamily = skin.font, modifier = Modifier.weight(1f))
            Text(right, color = skin.barText, fontWeight = FontWeight.Bold, fontFamily = skin.font)
        }
    }
}

@Composable
fun SkinText(skin: Skin, t: String, size: TextUnit, bold: Boolean = false, color: Color = skin.overlayText, center: Boolean = true) =
    Text(t, color = color, fontFamily = skin.font, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size,
        textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center else null, lineHeight = size * 1.25f)
