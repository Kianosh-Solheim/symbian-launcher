package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar

@Composable
fun SkeuomorphicMessageIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("msg", modifier); return }
    if (LocalS60.current) { S60Icons.Icon("msg", modifier); return }
    if (LocalLcd.current) { LcdIcon("msg", modifier); return }
    Canvas(modifier = modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        // Envelope base (Yellow-Orange gradient)
        val envelopeGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFEE99), Color(0xFFFFB700))
        )
        drawRoundRect(
            brush = envelopeGradient,
            size = Size(w, h * 0.7f),
            topLeft = Offset(0f, h * 0.15f),
            cornerRadius = corner,
            style = Fill
        )
        
        // Inner shadow / Edge highlight
        drawRoundRect(
            color = Color(0xFFFFF7CC),
            size = Size(w, h * 0.7f),
            topLeft = Offset(0f, h * 0.15f),
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx())
        )

        // Top flap
        val flapPath = Path().apply {
            moveTo(0f, h * 0.15f)
            lineTo(w / 2f, h * 0.5f)
            lineTo(w, h * 0.15f)
            close()
        }
        
        // Flap shadow
        drawPath(
            path = flapPath,
            color = Color(0x44000000),
            style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
        )

        // Flap fill
        val flapGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFF2B2), Color(0xFFFFCC33))
        )
        drawPath(
            path = flapPath,
            brush = flapGradient,
            style = Fill
        )

        // Flap edge
        drawPath(
            path = flapPath,
            color = Color(0xFFFFF9E6),
            style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round)
        )
    }
}

@Composable
fun SkeuomorphicEmailIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("mail", modifier); return }
    if (LocalS60.current) { S60Icons.Icon("mail", modifier); return }
    if (LocalLcd.current) { LcdIcon("mail", modifier); return }
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        // Envelope base (White-Grey gradient)
        val envelopeGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFD4D4D4))
        )
        drawRoundRect(
            brush = envelopeGradient,
            size = Size(w, h * 0.7f),
            topLeft = Offset(0f, h * 0.15f),
            cornerRadius = corner,
            style = Fill
        )
        
        // Edge highlight
        drawRoundRect(
            color = Color(0xFFFFFFFF),
            size = Size(w, h * 0.7f),
            topLeft = Offset(0f, h * 0.15f),
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx())
        )

        // Top flap
        val flapPath = Path().apply {
            moveTo(0f, h * 0.15f)
            lineTo(w / 2f, h * 0.5f)
            lineTo(w, h * 0.15f)
            close()
        }
        
        // Flap shadow
        drawPath(
            path = flapPath,
            color = Color(0x33000000),
            style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
        )

        // Flap fill
        val flapGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFE5E5E5))
        )
        drawPath(
            path = flapPath,
            brush = flapGradient,
            style = Fill
        )

        // "@" symbol in the middle
        val textLayoutResult = textMeasurer.measure(
            text = "@",
            style = TextStyle(
                color = Color(0xFF1E88E5), // Blue
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                (w - textLayoutResult.size.width) / 2f,
                (h * 0.85f - textLayoutResult.size.height) / 2f + 4.dp.toPx()
            )
        )
    }
}

@Composable
fun SkeuomorphicCalendarIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("cal", modifier); return }
    if (LocalS60.current) { S60Icons.Icon("cal", modifier); return }
    if (LocalLcd.current) { LcdIcon("cal", modifier); return }
    val textMeasurer = rememberTextMeasurer()
    val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
    
    Canvas(modifier = modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        // Calendar Base (White)
        val baseGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFDCDCDC))
        )
        drawRoundRect(
            brush = baseGradient,
            size = Size(w * 0.9f, h * 0.8f),
            topLeft = Offset(w * 0.05f, h * 0.15f),
            cornerRadius = corner,
            style = Fill
        )
        
        // Calendar Shadow
        drawRoundRect(
            color = Color(0x44000000),
            size = Size(w * 0.9f, h * 0.8f),
            topLeft = Offset(w * 0.05f, h * 0.15f),
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx())
        )

        // Top Header (Dark grey/black binding area)
        val headerGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF666666), Color(0xFF333333))
        )
        drawRoundRect(
            brush = headerGradient,
            size = Size(w * 0.9f, h * 0.25f),
            topLeft = Offset(w * 0.05f, h * 0.15f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Fill
        )

        // Binding Rings (Silver/White)
        val ringColor = Color(0xFFE0E0E0)
        val ringShadow = Color(0xFF000000)
        for (i in 1..3) {
            val cx = w * 0.05f + (w * 0.9f) * (i / 4f)
            // Ring shadow
            drawRoundRect(
                color = ringShadow,
                size = Size(w * 0.08f, h * 0.15f),
                topLeft = Offset(cx - (w * 0.04f) + 1f, h * 0.08f + 1f),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
            // Ring main
            drawRoundRect(
                color = ringColor,
                size = Size(w * 0.08f, h * 0.15f),
                topLeft = Offset(cx - (w * 0.04f), h * 0.08f),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }

        // Date text
        val textLayoutResult = textMeasurer.measure(
            text = dayOfMonth,
            style = TextStyle(
                color = Color(0xFFE65100), // Orange-Red
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                (w - textLayoutResult.size.width) / 2f,
                (h * 0.95f - textLayoutResult.size.height + h * 0.25f) / 2f
            )
        )
    }
}

@Composable
fun SkeuomorphicContactsIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("person", modifier.size(32.dp)); return }
    if (LocalS60.current) { S60Icons.Icon("person", modifier.size(32.dp)); return }
    if (LocalLcd.current) { LcdIcon("person", modifier.size(32.dp)); return }
    Canvas(modifier = modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

        // Book Base (Blue)
        val bookGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF42A5F5), Color(0xFF1565C0))
        )
        drawRoundRect(
            brush = bookGradient,
            size = Size(w * 0.8f, h * 0.9f),
            topLeft = Offset(w * 0.1f, h * 0.05f),
            cornerRadius = corner,
            style = Fill
        )

        // Spine (Darker Blue)
        val spineGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFF1E88E5), Color(0xFF0D47A1))
        )
        drawRoundRect(
            brush = spineGradient,
            size = Size(w * 0.15f, h * 0.9f),
            topLeft = Offset(w * 0.1f, h * 0.05f),
            cornerRadius = CornerRadius(3.dp.toPx(), 0f),
            style = Fill
        )
        
        // Edge highlights
        drawRoundRect(
            color = Color(0x66FFFFFF),
            size = Size(w * 0.8f, h * 0.9f),
            topLeft = Offset(w * 0.1f, h * 0.05f),
            cornerRadius = corner,
            style = Stroke(width = 1.dp.toPx())
        )

        // Pages sticking out on right
        drawRoundRect(
            color = Color(0xFFE0E0E0),
            size = Size(w * 0.05f, h * 0.8f),
            topLeft = Offset(w * 0.9f - 1f, h * 0.1f),
            cornerRadius = CornerRadius(1.dp.toPx()),
            style = Fill
        )

        // Silhouette
        val headRadius = w * 0.12f
        val headCenter = Offset(w * 0.55f, h * 0.35f)
        drawCircle(
            color = Color.White,
            radius = headRadius,
            center = headCenter
        )

        val bodyPath = Path().apply {
            moveTo(headCenter.x - headRadius * 1.5f, h * 0.8f)
            quadraticBezierTo(
                headCenter.x, h * 0.45f,
                headCenter.x + headRadius * 1.5f, h * 0.8f
            )
            close()
        }
        drawPath(
            path = bodyPath,
            color = Color.White
        )
    }
}

@Composable
fun SkeuomorphicNotificationsIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("bell", modifier); return }
    if (LocalS60.current) { S60Icons.Icon("bell", modifier); return }
    if (LocalLcd.current) { LcdIcon("bell", modifier); return }
    Canvas(modifier = modifier.size(32.dp)) {
        val w = size.width
        val h = size.height

        // Clapper (gold/yellow)
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = w * 0.1f,
            center = Offset(w / 2f, h * 0.85f)
        )
        
        // Bell body (Red)
        val bellGradient = Brush.radialGradient(
            colors = listOf(Color(0xFFFF5252), Color(0xFFB71C1C)),
            center = Offset(w * 0.35f, h * 0.35f),
            radius = w * 0.6f
        )
        
        val bellPath = Path().apply {
            moveTo(w * 0.5f, h * 0.15f) // Top center
            // Right curve
            cubicTo(
                w * 0.75f, h * 0.15f,
                w * 0.8f, h * 0.5f,
                w * 0.9f, h * 0.75f
            )
            // Bottom line
            lineTo(w * 0.1f, h * 0.75f)
            // Left curve
            cubicTo(
                w * 0.2f, h * 0.5f,
                w * 0.25f, h * 0.15f,
                w * 0.5f, h * 0.15f
            )
            close()
        }
        
        drawPath(
            path = bellPath,
            brush = bellGradient,
            style = Fill
        )
        
        // Highlight
        val highlightPath = Path().apply {
            moveTo(w * 0.5f, h * 0.17f)
            cubicTo(
                w * 0.35f, h * 0.17f,
                w * 0.3f, h * 0.4f,
                w * 0.25f, h * 0.6f
            )
        }
        drawPath(
            path = highlightPath,
            color = Color(0x66FFFFFF),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Top ring
        drawRoundRect(
            color = Color(0xFFB71C1C),
            size = Size(w * 0.15f, h * 0.1f),
            topLeft = Offset(w * 0.425f, h * 0.08f),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}

@Composable
fun SkeuomorphicAppsIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("apps", modifier.size(32.dp)); return }
    if (LocalS60.current) { S60Icons.Icon("apps", modifier.size(32.dp)); return }
    if (LocalLcd.current) { LcdIcon("apps", modifier.size(32.dp)); return }
    Canvas(modifier = modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

        // 4 cubes
        val cubeColors = listOf(
            Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))),
            Brush.verticalGradient(listOf(Color(0xFF81C784), Color(0xFF388E3C))),
            Brush.verticalGradient(listOf(Color(0xFFFFB74D), Color(0xFFF57C00))),
            Brush.verticalGradient(listOf(Color(0xFFE57373), Color(0xFFD32F2F)))
        )

        val padding = w * 0.1f
        val cubeSize = (w - padding * 3) / 2f

        for (i in 0..1) {
            for (j in 0..1) {
                val idx = i * 2 + j
                val x = padding + j * (cubeSize + padding)
                val y = padding + i * (cubeSize + padding)
                
                // Shadow
                drawRoundRect(
                    color = Color(0x44000000),
                    size = Size(cubeSize, cubeSize),
                    topLeft = Offset(x + 1f, y + 1f),
                    cornerRadius = corner,
                    style = Fill
                )
                
                // Cube
                drawRoundRect(
                    brush = cubeColors[idx],
                    size = Size(cubeSize, cubeSize),
                    topLeft = Offset(x, y),
                    cornerRadius = corner,
                    style = Fill
                )
                
                // Highlight
                drawRoundRect(
                    color = Color(0x66FFFFFF),
                    size = Size(cubeSize, cubeSize),
                    topLeft = Offset(x, y),
                    cornerRadius = corner,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun SkeuomorphicSettingsIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("settings", modifier); return }
    if (LocalS60.current) { S60Icons.Icon("settings", modifier); return }
    if (LocalLcd.current) { LcdIcon("settings", modifier); return }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2, h / 2)
        val outerRadius = w * 0.4f
        val innerRadius = w * 0.2f
        val gearTeeth = 8
        
        val gearGradient = Brush.radialGradient(
            colors = listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF616161)),
            center = center,
            radius = outerRadius
        )
        
        val gearPath = Path().apply {
            val angleStep = (2 * Math.PI) / (gearTeeth * 2)
            for (i in 0 until gearTeeth * 2) {
                val r = if (i % 2 == 0) outerRadius else outerRadius * 0.75f
                val angle = i * angleStep
                val x = center.x + (r * kotlin.math.cos(angle)).toFloat()
                val y = center.y + (r * kotlin.math.sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        
        drawPath(
            path = gearPath,
            brush = gearGradient,
            style = Fill
        )
        
        // Inner hole
        drawCircle(
            color = Color(0xFF303030),
            radius = innerRadius,
            center = center
        )
        // Inner rim highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = innerRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // 3D Shadow/Bevel on gear
        drawPath(
            path = gearPath,
            color = Color.White.copy(alpha = 0.4f),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
fun SkeuomorphicUsageIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("usage", modifier); return }
    if (LocalS60.current) { S60Icons.Icon("usage", modifier); return }
    if (LocalLcd.current) { LcdIcon("usage", modifier); return }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        
        val bgGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFFE0F7FA), Color(0xFFB2EBF2), Color(0xFF80DEEA))
        )
        
        // Background board
        drawRoundRect(
            brush = bgGradient,
            size = Size(w, h),
            cornerRadius = corner
        )
        
        // Border
        drawRoundRect(
            color = Color(0xFF006064),
            size = Size(w, h),
            cornerRadius = corner,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Bars
        val barColors = listOf(Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF448AFF))
        val barWidth = w * 0.15f
        val gap = (w - (barWidth * 4)) / 5
        val barHeights = listOf(h * 0.4f, h * 0.7f, h * 0.5f, h * 0.8f)
        
        for (i in 0..3) {
            val left = gap + i * (barWidth + gap)
            val top = h - barHeights[i] - h * 0.1f // 10% bottom padding
            
            // 3D Bar Gradient
            val barGradient = Brush.horizontalGradient(
                colors = listOf(barColors[i].copy(alpha = 0.7f), barColors[i], barColors[i].copy(alpha = 0.5f)),
                startX = left,
                endX = left + barWidth
            )
            
            drawRoundRect(
                brush = barGradient,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeights[i]),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            
            // Top highlight for 3D effect
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(left, top),
                end = Offset(left + barWidth, top),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
fun SkeuomorphicWalletIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("card", modifier); return }
    if (LocalS60.current) { S60Icons.Icon("card", modifier); return }
    if (LocalLcd.current) { LcdIcon("card", modifier); return }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

        // Background ticket / card sticking out
        val card1Brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
        )
        drawRoundRect(
            brush = card1Brush,
            topLeft = Offset(w * 0.15f, h * 0.12f),
            size = Size(w * 0.7f, h * 0.45f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        // Card strip
        drawRect(
            color = Color(0xFFFFD54F),
            topLeft = Offset(w * 0.22f, h * 0.20f),
            size = Size(w * 0.22f, h * 0.08f)
        )

        // Main Leather Wallet Body
        val leatherGradient = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF8D6E63),
                Color(0xFF5D4037),
                Color(0xFF3E2723)
            )
        )
        drawRoundRect(
            brush = leatherGradient,
            topLeft = Offset(w * 0.08f, h * 0.32f),
            size = Size(w * 0.84f, h * 0.62f),
            cornerRadius = corner
        )

        // Leather Top Flap
        val flapGradient = Brush.verticalGradient(
            colors = listOf(Color(0xFFA1887F), Color(0xFF6D4C41), Color(0xFF4E342E))
        )
        drawRoundRect(
            brush = flapGradient,
            topLeft = Offset(w * 0.08f, h * 0.32f),
            size = Size(w * 0.84f, h * 0.28f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        // Stitching line (dotted/dashed)
        val stitchColor = Color(0xFFFFE082).copy(alpha = 0.8f)
        drawRoundRect(
            color = stitchColor,
            topLeft = Offset(w * 0.12f, h * 0.36f),
            size = Size(w * 0.76f, h * 0.54f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
            )
        )

        // Gold / Metallic Clasp
        val goldBrush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFF8F00)),
            center = Offset(w * 0.5f, h * 0.58f),
            radius = w * 0.12f
        )
        drawCircle(
            brush = goldBrush,
            radius = w * 0.09f,
            center = Offset(w * 0.5f, h * 0.58f)
        )
        drawCircle(
            color = Color(0xFF3E2723),
            radius = w * 0.035f,
            center = Offset(w * 0.5f, h * 0.58f)
        )

        // Shadow & Outer border
        drawRoundRect(
            color = Color(0xFF271C19),
            topLeft = Offset(w * 0.08f, h * 0.32f),
            size = Size(w * 0.84f, h * 0.62f),
            cornerRadius = corner,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
fun SkeuomorphicCalculatorIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("calc", modifier); return }
    if (LocalS60.current) { S60Icons.Icon("calc", modifier); return }
    if (LocalLcd.current) { LcdIcon("calc", modifier); return }
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

        // Main Body Shadow
        drawRoundRect(
            color = Color(0x66000000),
            topLeft = Offset(w * 0.08f, h * 0.08f),
            size = Size(w * 0.84f, h * 0.88f),
            cornerRadius = corner
        )

        // Main Body Metallic / Blue Gradient (Nokia S60 Blue-Grey)
        val bodyBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF37474F),
                Color(0xFF263238),
                Color(0xFF1E272C)
            )
        )
        drawRoundRect(
            brush = bodyBrush,
            topLeft = Offset(w * 0.08f, h * 0.06f),
            size = Size(w * 0.84f, h * 0.88f),
            cornerRadius = corner
        )

        // Top Chrome Bevel Highlight
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF90A4AE), Color(0x0090A4AE))
            ),
            topLeft = Offset(w * 0.08f, h * 0.06f),
            size = Size(w * 0.84f, h * 0.44f),
            cornerRadius = corner,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // LCD Display Bezel / Inset
        val lcdBezel = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        drawRoundRect(
            color = Color(0xFF101416),
            topLeft = Offset(w * 0.16f, h * 0.14f),
            size = Size(w * 0.68f, h * 0.22f),
            cornerRadius = lcdBezel
        )

        // LCD Glass Greenish-Grey screen
        val lcdBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFF9EA792), Color(0xFF8B957E), Color(0xFF7A846C))
        )
        drawRoundRect(
            brush = lcdBrush,
            topLeft = Offset(w * 0.18f, h * 0.16f),
            size = Size(w * 0.64f, h * 0.18f),
            cornerRadius = lcdBezel
        )

        // LCD Numbers "8 0 5" or "1 2 3" text
        val measured = textMeasurer.measure(
            text = "123.4",
            style = TextStyle(
                color = Color(0xFF202A1A),
                fontSize = (w * 0.085f).sp,
                fontWeight = FontWeight.Bold
            )
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(w * 0.80f - measured.size.width, h * 0.17f)
        )

        // Button Grid Layout: 3 rows x 4 cols
        val buttonColors = listOf(
            // Row 1: C, ( ), %, ÷
            listOf(Color(0xFFE53935), Color(0xFF455A64), Color(0xFF455A64), Color(0xFFFF8F00)),
            // Row 2: 7, 8, 9, ×
            listOf(Color(0xFF546E7A), Color(0xFF546E7A), Color(0xFF546E7A), Color(0xFFFF8F00)),
            // Row 3: 4, 5, 6, -
            listOf(Color(0xFF546E7A), Color(0xFF546E7A), Color(0xFF546E7A), Color(0xFFFF8F00)),
            // Row 4: 1, 2, 3, +
            listOf(Color(0xFF546E7A), Color(0xFF546E7A), Color(0xFF546E7A), Color(0xFFFF8F00)),
            // Row 5: 0, ., =
            listOf(Color(0xFF546E7A), Color(0xFF546E7A), Color(0xFF00ACC1), Color(0xFF00ACC1))
        )

        val btnW = w * 0.135f
        val btnH = h * 0.075f
        val startX = w * 0.16f
        val startY = h * 0.42f
        val gapX = w * 0.045f
        val gapY = h * 0.035f

        // Draw sample key pads (4 rows x 4 cols)
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                val bx = startX + c * (btnW + gapX)
                val by = startY + r * (btnH + gapY)
                val isOp = c == 3
                val isEq = r == 3 && c == 2
                val isClear = r == 0 && c == 0

                val btnColor = when {
                    isClear -> Color(0xFFE53935)
                    isOp -> Color(0xFFFF9800)
                    isEq -> Color(0xFF43A047)
                    else -> Color(0xFF607D8B)
                }

                // Button Shadow
                drawRoundRect(
                    color = Color(0x88000000),
                    topLeft = Offset(bx, by + 1.dp.toPx()),
                    size = Size(btnW, btnH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Button Face
                val btnGrad = Brush.verticalGradient(
                    colors = listOf(btnColor.copy(alpha = 0.9f), btnColor.copy(red = btnColor.red * 0.7f, green = btnColor.green * 0.7f, blue = btnColor.blue * 0.7f))
                )
                drawRoundRect(
                    brush = btnGrad,
                    topLeft = Offset(bx, by),
                    size = Size(btnW, btnH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Button Top Edge Highlight
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(bx, by),
                    size = Size(btnW, btnH * 0.45f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Fill
                )
            }
        }

        // Glass Reflection Glare Across Top Half
        val glarePath = Path().apply {
            moveTo(w * 0.08f, h * 0.06f)
            lineTo(w * 0.92f, h * 0.06f)
            lineTo(w * 0.92f, h * 0.28f)
            lineTo(w * 0.08f, h * 0.48f)
            close()
        }
        drawPath(
            path = glarePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.02f))
            )
        )
    }
}

@Composable
fun SkeuomorphicClockIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("clock", modifier); return }
    if (LocalS60.current) { S60Icons.Icon("clock", modifier); return }
    if (LocalLcd.current) { LcdIcon("clock", modifier); return }
    val currentTimeMillis = rememberTickingTime(1_000L)

    val calendar = remember(currentTimeMillis) {
        Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    }
    val hours = calendar.get(Calendar.HOUR)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)

    Canvas(modifier = modifier.size(32.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = (w.coerceAtMost(h) / 2f) * 0.92f

        // Outer Metallic Rim Shadow
        drawCircle(
            color = Color(0x66000000),
            center = Offset(cx, cy + 2.dp.toPx()),
            radius = radius
        )

        // Outer Metallic Bezel (Silver Chrome gradient)
        val rimGradient = Brush.linearGradient(
            colors = listOf(Color(0xFFE0E5EB), Color(0xFF88929E), Color(0xFFD6DBE2), Color(0xFF5A626E)),
            start = Offset(cx - radius, cy - radius),
            end = Offset(cx + radius, cy + radius)
        )
        drawCircle(
            brush = rimGradient,
            center = Offset(cx, cy),
            radius = radius
        )

        // Inner Bezel Inset
        drawCircle(
            color = Color(0xFF37474F),
            center = Offset(cx, cy),
            radius = radius * 0.88f,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Clock Dial Face (White Pearlescent gradient)
        val faceGradient = Brush.radialGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFECEFF1), Color(0xFFCFD8DC)),
            center = Offset(cx, cy - radius * 0.1f),
            radius = radius * 0.85f
        )
        drawCircle(
            brush = faceGradient,
            center = Offset(cx, cy),
            radius = radius * 0.85f
        )

        // Hour Ticks (12, 3, 6, 9 main markers, and smaller secondary ticks)
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30.0) - 90.0)
            val isMajor = i % 3 == 0
            val tickLen = if (isMajor) radius * 0.18f else radius * 0.10f
            val startDist = radius * 0.78f - tickLen
            val endDist = radius * 0.78f

            val sx = cx + (startDist * Math.cos(angle)).toFloat()
            val sy = cy + (startDist * Math.sin(angle)).toFloat()
            val ex = cx + (endDist * Math.cos(angle)).toFloat()
            val ey = cy + (endDist * Math.sin(angle)).toFloat()

            drawLine(
                color = if (isMajor) Color(0xFF263238) else Color(0xFF78909C),
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Live Hour Hand (smoothly incorporates minutes)
        val hourAngle = Math.toRadians(((hours % 12 + minutes / 60.0 + seconds / 3600.0) * 30.0) - 90.0)
        val hourLen = radius * 0.45f
        val hx = cx + (hourLen * Math.cos(hourAngle)).toFloat()
        val hy = cy + (hourLen * Math.sin(hourAngle)).toFloat()
        // Hour hand shadow
        drawLine(
            color = Color(0x55000000),
            start = Offset(cx + 1f, cy + 1f),
            end = Offset(hx + 1f, hy + 1f),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF212121),
            start = Offset(cx, cy),
            end = Offset(hx, hy),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Live Minute Hand (smoothly incorporates seconds)
        val minAngle = Math.toRadians(((minutes + seconds / 60.0) * 6.0) - 90.0)
        val minLen = radius * 0.65f
        val mx = cx + (minLen * Math.cos(minAngle)).toFloat()
        val my = cy + (minLen * Math.sin(minAngle)).toFloat()
        // Minute hand shadow
        drawLine(
            color = Color(0x55000000),
            start = Offset(cx + 1f, cy + 1f),
            end = Offset(mx + 1f, my + 1f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF37474F),
            start = Offset(cx, cy),
            end = Offset(mx, my),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Live Second Hand (Red ticking second hand)
        val secAngle = Math.toRadians((seconds * 6.0) - 90.0)
        val secLen = radius * 0.72f
        val secTailLen = radius * 0.2f
        val sx = cx + (secLen * Math.cos(secAngle)).toFloat()
        val sy = cy + (secLen * Math.sin(secAngle)).toFloat()
        val tailX = cx - (secTailLen * Math.cos(secAngle)).toFloat()
        val tailY = cy - (secTailLen * Math.sin(secAngle)).toFloat()
        drawLine(
            color = Color(0xFFD32F2F),
            start = Offset(tailX, tailY),
            end = Offset(sx, sy),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Center Pin Cap (Metallic with red dot)
        drawCircle(
            color = Color(0xFF212121),
            center = Offset(cx, cy),
            radius = radius * 0.10f
        )
        drawCircle(
            color = Color(0xFFD32F2F),
            center = Offset(cx, cy),
            radius = radius * 0.05f
        )

        // Glass Glare / Gloss highlight
        val glassPath = Path().apply {
            moveTo(cx - radius * 0.75f, cy - radius * 0.2f)
            cubicTo(
                cx - radius * 0.5f, cy - radius * 0.8f,
                cx + radius * 0.5f, cy - radius * 0.8f,
                cx + radius * 0.75f, cy - radius * 0.2f
            )
            cubicTo(
                cx + radius * 0.3f, cy - radius * 0.4f,
                cx - radius * 0.3f, cy - radius * 0.4f,
                cx - radius * 0.75f, cy - radius * 0.2f
            )
            close()
        }
        drawPath(
            path = glassPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.05f))
            )
        )
    }
}

@Composable
fun SkeuomorphicBatterySaverIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("battery", modifier.size(36.dp)); return }
    if (LocalS60.current) { S60Icons.Icon("battery", modifier.size(36.dp)); return }
    if (LocalLcd.current) { LcdIcon("battery", modifier.size(36.dp)); return }
    Canvas(modifier = modifier.size(36.dp)) {
        val w = size.width
        val h = size.height

        // Battery outer terminal cap (top)
        val capW = w * 0.32f
        val capH = h * 0.10f
        val capX = (w - capW) / 2
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFBDBDBD), Color(0xFFFFFFFF), Color(0xFF757575))
            ),
            topLeft = Offset(capX, 0f),
            size = Size(capW, capH),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // Battery main casing (body)
        val bodyY = capH * 0.8f
        val bodyH = h - bodyY
        val bodyW = w * 0.72f
        val bodyX = (w - bodyW) / 2
        val corner = CornerRadius(5.dp.toPx(), 5.dp.toPx())

        // Outer dark metallic casing
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF455A64), Color(0xFF263238))
            ),
            topLeft = Offset(bodyX, bodyY),
            size = Size(bodyW, bodyH),
            cornerRadius = corner
        )

        // Inner glowing green charge area
        val pad = 3.dp.toPx()
        val innerX = bodyX + pad
        val innerY = bodyY + pad
        val innerW = bodyW - (pad * 2)
        val innerH = bodyH - (pad * 2)

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF81C784), Color(0xFF4CAF50), Color(0xFF2E7D32))
            ),
            topLeft = Offset(innerX, innerY),
            size = Size(innerW, innerH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // Lightning bolt / Power glyph in center
        val boltPath = Path().apply {
            val cx = w / 2
            val cy = bodyY + bodyH / 2
            val s = w * 0.32f
            moveTo(cx + s * 0.1f, cy - s * 0.6f)
            lineTo(cx - s * 0.4f, cy + s * 0.05f)
            lineTo(cx - s * 0.05f, cy + s * 0.05f)
            lineTo(cx - s * 0.15f, cy + s * 0.65f)
            lineTo(cx + s * 0.45f, cy - s * 0.02f)
            lineTo(cx + s * 0.08f, cy - s * 0.02f)
            close()
        }
        // Bolt shadow
        drawPath(
            path = boltPath,
            color = Color(0x66000000)
        )
        // Bolt gradient (golden bright)
        drawPath(
            path = boltPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFFB300))
            )
        )

        // Gloss glare on the casing
        val glarePath = Path().apply {
            moveTo(innerX, innerY)
            lineTo(innerX + innerW, innerY)
            lineTo(innerX + innerW, innerY + innerH * 0.45f)
            quadraticTo(
                innerX + innerW * 0.5f, innerY + innerH * 0.3f,
                innerX, innerY + innerH * 0.5f
            )
            close()
        }
        drawPath(
            path = glarePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))
            )
        )
    }
}

@Composable
fun SkeuomorphicPhoneIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("call", modifier.size(36.dp)); return }
    if (LocalS60.current) { S60Icons.Icon("call", modifier.size(36.dp)); return }
    if (LocalLcd.current) { LcdIcon("call", modifier.size(36.dp)); return }
    Canvas(modifier = modifier.size(36.dp)) {
        val w = size.width
        val h = size.height

        // Base rounded badge (rich green squircle)
        val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32), Color(0xFF1B5E20))
            ),
            size = Size(w, h),
            cornerRadius = corner
        )

        // Gloss highlight on upper half
        val glossPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h * 0.5f)
            cubicTo(w * 0.7f, h * 0.45f, w * 0.3f, h * 0.55f, 0f, h * 0.5f)
            close()
        }
        drawPath(
            path = glossPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.05f))
            )
        )

        // Telephone handset path (classic angled receiver)
        val handsetPath = Path().apply {
            val cx = w * 0.5f
            val cy = h * 0.5f
            val scale = w / 40f

            // Curved phone receiver
            moveTo(cx - 7 * scale, cy + 9 * scale)
            cubicTo(cx - 10 * scale, cy + 6 * scale, cx - 10 * scale, cy - 2 * scale, cx - 6 * scale, cy - 7 * scale)
            cubicTo(cx - 4 * scale, cy - 9 * scale, cx - 1 * scale, cy - 10 * scale, cx + 2 * scale, cy - 8 * scale)
            lineTo(cx + 5 * scale, cy - 5 * scale)
            cubicTo(cx + 6 * scale, cy - 4 * scale, cx + 5 * scale, cy - 2 * scale, cx + 3 * scale, cy - 1 * scale)
            lineTo(cx + 1 * scale, cy + 1 * scale)
            cubicTo(cx + 1 * scale, cy + 2 * scale, cx + 3 * scale, cy + 5 * scale, cx + 6 * scale, cy + 6 * scale)
            lineTo(cx + 8 * scale, cy + 4 * scale)
            cubicTo(cx + 9 * scale, cy + 3 * scale, cx + 11 * scale, cy + 3 * scale, cx + 12 * scale, cy + 5 * scale)
            lineTo(cx + 14 * scale, cy + 8 * scale)
            cubicTo(cx + 15 * scale, cy + 10 * scale, cx + 13 * scale, cy + 12 * scale, cx + 10 * scale, cy + 13 * scale)
            cubicTo(cx + 5 * scale, cy + 14 * scale, cx - 2 * scale, cy + 13 * scale, cx - 7 * scale, cy + 9 * scale)
            close()
        }

        // Handset shadow
        drawPath(
            path = handsetPath,
            color = Color(0x55000000)
        )

        // Handset white/cream fill
        drawPath(
            path = handsetPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFE8F5E9), Color(0xFFC8E6C9))
            )
        )
    }
}

@Composable
fun SkeuomorphicCameraIcon(modifier: Modifier = Modifier) {
    if (LocalWin95.current) { Win95PixelIcon("camera", modifier.size(36.dp)); return }
    if (LocalS60.current) { S60Icons.Icon("camera", modifier.size(36.dp)); return }
    if (LocalLcd.current) { LcdIcon("camera", modifier.size(36.dp)); return }
    Canvas(modifier = modifier.size(36.dp)) {
        val w = size.width
        val h = size.height

        // Camera body
        val bodyH = h * 0.72f
        val bodyY = h * 0.24f
        val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

        // Top flash / viewfinder bump
        val bumpW = w * 0.35f
        val bumpH = h * 0.18f
        val bumpX = w * 0.18f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF78909C), Color(0xFF455A64))
            ),
            topLeft = Offset(bumpX, h * 0.10f),
            size = Size(bumpW, bumpH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // Main camera body gradient
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF607D8B), Color(0xFF37474F), Color(0xFF263238))
            ),
            topLeft = Offset(0f, bodyY),
            size = Size(w, bodyH),
            cornerRadius = corner
        )

        // Top chrome strip
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFCFD8DC), Color(0xFF90A4AE))
            ),
            topLeft = Offset(0f, bodyY),
            size = Size(w, bodyH * 0.28f),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )

        // Shutter button (red accent dot)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF5252), Color(0xFFD32F2F))
            ),
            center = Offset(w * 0.82f, bodyY + bodyH * 0.14f),
            radius = w * 0.065f
        )

        // Outer lens rim (metallic ring)
        val lensCenter = Offset(w * 0.5f, bodyY + bodyH * 0.56f)
        val lensRadius = w * 0.26f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFECEFF1), Color(0xFF78909C), Color(0xFF37474F))
            ),
            center = lensCenter,
            radius = lensRadius
        )

        // Inner lens glass (deep dark blue/cyan with reflection)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0277BD), Color(0xFF01579B), Color(0xFF000000))
            ),
            center = lensCenter,
            radius = lensRadius * 0.75f
        )

        // Lens specular glare
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            center = Offset(lensCenter.x - lensRadius * 0.28f, lensCenter.y - lensRadius * 0.28f),
            radius = lensRadius * 0.20f
        )
    }
}




/** Desktop Mode: a glossy monitor; a green light shows when Desktop Mode is running. */
@Composable
fun SkeuomorphicDesktopIcon(modifier: Modifier = Modifier, active: Boolean = false) {
    if (LocalWin95.current) { Win95PixelIcon("desktop", modifier); return }
    if (LocalS60.current) { S60Icons.Icon("desktop", modifier); return }
    if (LocalLcd.current) { LcdIcon("desktop", modifier); return }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bezelTop = h * 0.08f
        val bezelH = h * 0.66f
        // Stand
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFBDBDBD), Color(0xFF757575)), startY = h * 0.72f, endY = h * 0.84f),
            topLeft = Offset(w * 0.42f, h * 0.72f), size = Size(w * 0.16f, h * 0.13f)
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFE0E0E0), Color(0xFF616161)), startY = h * 0.84f, endY = h * 0.94f),
            topLeft = Offset(w * 0.24f, h * 0.84f), size = Size(w * 0.52f, h * 0.09f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
        // Bezel
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFF5F5F5), Color(0xFF9E9E9E), Color(0xFF616161)), startY = bezelTop, endY = bezelTop + bezelH),
            topLeft = Offset(0f, bezelTop), size = Size(w, bezelH),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF424242), topLeft = Offset(0f, bezelTop), size = Size(w, bezelH),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(width = 1.5.dp.toPx())
        )
        // Screen
        val pad = w * 0.08f
        val sTop = bezelTop + pad
        val sW = w - pad * 2
        val sH = bezelH - pad * 2
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0277BD), Color(0xFF01579B)), startY = sTop, endY = sTop + sH),
            topLeft = Offset(pad, sTop), size = Size(sW, sH), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        // Little window panes on the screen
        val paneC = Color.White.copy(alpha = 0.85f)
        drawRect(paneC, topLeft = Offset(pad + sW * 0.1f, sTop + sH * 0.18f), size = Size(sW * 0.22f, sH * 0.64f))
        drawRect(paneC.copy(alpha = 0.6f), topLeft = Offset(pad + sW * 0.38f, sTop + sH * 0.18f), size = Size(sW * 0.52f, sH * 0.28f))
        drawRect(paneC.copy(alpha = 0.6f), topLeft = Offset(pad + sW * 0.38f, sTop + sH * 0.54f), size = Size(sW * 0.52f, sH * 0.28f))
        // Gloss
        val gloss = Path().apply {
            moveTo(pad, sTop); lineTo(pad + sW, sTop); lineTo(pad + sW, sTop + sH * 0.35f)
            quadraticTo(pad + sW * 0.5f, sTop + sH * 0.55f, pad, sTop + sH * 0.45f); close()
        }
        drawPath(gloss, Color.White.copy(alpha = 0.22f))
        // Power light
        drawCircle(
            color = if (active) Color(0xFF69F0AE) else Color(0xFF9E9E9E),
            radius = w * 0.035f, center = Offset(w - pad * 1.6f, bezelTop + bezelH - pad * 0.5f)
        )
    }
}
