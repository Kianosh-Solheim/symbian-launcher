package com.example

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * Skeuomorphic Analogue Clock with authentic watch craftsmanship:
 * - Dynamic Day/Night dial face (Pearlescent White for daytime, Deep Obsidian Black for nighttime)
 * - Multi-layer metallic chrome/brushed stainless steel bezel ring
 * - Recessed dial with drop shadow depth
 * - Classic typography numerals (1-12) & fine railway minute ticks
 * - 3D beveled sword hands with realistic drop shadows
 * - Vermilion sweep second hand with circular counterweight
 * - Center jeweled pinion hub
 * - Curved watch crystal specular glass reflection arc
 */
@Composable
fun SkeuomorphicAnalogueClock(
    modifier: Modifier = Modifier,
    timeMillis: Long = System.currentTimeMillis(),
    timeZoneId: String? = null,
    clockSize: Dp = 120.dp,
    showSeconds: Boolean = true,
    showNumerals: Boolean = true
) {
    val calendar = remember(timeMillis, timeZoneId) {
        if (!timeZoneId.isNullOrEmpty()) {
            val tz = TimeZone.getTimeZone(timeZoneId)
            Calendar.getInstance(tz).apply { this.timeInMillis = timeMillis }
        } else {
            Calendar.getInstance().apply { this.timeInMillis = timeMillis }
        }
    }

    val hour24 = calendar.get(Calendar.HOUR_OF_DAY)
    val hour12 = calendar.get(Calendar.HOUR)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)

    // Day/Night detection (Night is before 6:00 AM or 6:00 PM onwards)
    val isNight = hour24 < 6 || hour24 >= 18

    Box(modifier = modifier.size(clockSize)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawSkeuomorphicBezel(center, radius)
            drawClockDialFace(center, radius, isNight, showNumerals)
            drawClockHands(center, radius, hour12, minutes, seconds, isNight, showSeconds)
            drawWatchCrystalReflection(center, radius)
        }
    }
}

/**
 * Outer Metallic Bezel with multi-stop chrome gradients and recessed inner shadow
 */
private fun DrawScope.drawSkeuomorphicBezel(center: Offset, radius: Float) {
    val bezelThickness = radius * 0.08f

    // 1. Soft ambient outer drop shadow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x55000000), Color(0x00000000)),
            center = center,
            radius = radius * 1.05f
        ),
        radius = radius * 1.05f,
        center = center
    )

    // 2. Metallic Chrome / Brushed Stainless Steel outer rim
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFF0F4F8), // Top-left specular gleam
                Color(0xFFCFD8DC),
                Color(0xFF90A4AE),
                Color(0xFF455A64), // Bottom-right shadow
                Color(0xFF78909C),
                Color(0xFFCFD8DC)
            ),
            start = Offset(center.x - radius, center.y - radius),
            end = Offset(center.x + radius, center.y + radius)
        ),
        radius = radius,
        center = center
    )

    // 3. Inner metallic bevel ring / step
    val innerBezelRadius = radius - bezelThickness * 0.45f
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF37474F), // Top dark inner rim
                Color(0xFF78909C),
                Color(0xFFECEFF1)  // Bottom light inner rim
            ),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius)
        ),
        radius = innerBezelRadius,
        center = center,
        style = Stroke(width = bezelThickness * 0.4f)
    )
}

/**
 * Dial Face:
 * Switches between pearlescent daylight white and deep midnight black.
 */
private fun DrawScope.drawClockDialFace(
    center: Offset,
    radius: Float,
    isNight: Boolean,
    showNumerals: Boolean
) {
    val dialRadius = radius * 0.90f

    // 1. Dial Background with lighting
    if (isNight) {
        // Deep Obsidian / Carbon Black dial face
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFF21252B), // Subtle center illumination
                    0.5f to Color(0xFF14171C),
                    0.85f to Color(0xFF0C0E11),
                    1.0f to Color(0xFF050608)  // Deep perimeter
                ),
                center = center,
                radius = dialRadius
            ),
            radius = dialRadius,
            center = center
        )
    } else {
        // Pearlescent / Brushed Silver-White daylight dial face
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFFFFFFFF),
                    0.6f to Color(0xFFF8F9FA),
                    0.85f to Color(0xFFE9ECEF),
                    1.0f to Color(0xFFDEE2E6)
                ),
                center = Offset(center.x - dialRadius * 0.2f, center.y - dialRadius * 0.2f),
                radius = dialRadius * 1.2f
            ),
            radius = dialRadius,
            center = center
        )
    }

    // 2. Inner Dial Recessed Rim Shadow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                if (isNight) Color(0x66000000) else Color(0x33000000)
            ),
            center = center,
            radius = dialRadius
        ),
        radius = dialRadius,
        center = center
    )

    // 3. Ticks and Numerals Colors
    val markerColor = if (isNight) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val subTickColor = if (isNight) Color(0xFF64748B) else Color(0xFF94A3B8)
    val textColor = if (isNight) android.graphics.Color.WHITE else android.graphics.Color.rgb(30, 41, 59)

    // Paint for hour numbers
    val paint = Paint().apply {
        color = textColor
        textSize = radius * 0.23f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
        if (isNight) {
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.argb(120, 255, 255, 255))
        } else {
            setShadowLayer(1.5f, 0.5f, 0.5f, android.graphics.Color.argb(50, 0, 0, 0))
        }
    }

    val textRadius = dialRadius * 0.72f

    // Draw 1..12 Numerals
    if (showNumerals && radius > 35.dp.toPx()) {
        val yOffsetFix = (paint.descent() + paint.ascent()) / 2f
        for (i in 1..12) {
            val angle = Math.toRadians(i * 30.0)
            val x = center.x + textRadius * sin(angle).toFloat()
            val y = center.y - textRadius * cos(angle).toFloat() - yOffsetFix
            drawContext.canvas.nativeCanvas.drawText(i.toString(), x, y, paint)
        }
    }

    // Draw 60 Minute & Hour Ticks
    for (i in 0 until 60) {
        val isMajor = (i % 5 == 0)
        val angleRad = Math.toRadians(i * 6.0)
        val sinA = sin(angleRad).toFloat()
        val cosA = cos(angleRad).toFloat()

        val tickLen = if (isMajor) dialRadius * 0.12f else dialRadius * 0.06f
        val strokeW = if (isMajor) radius * 0.026f else radius * 0.012f
        val color = if (isMajor) markerColor else subTickColor

        val startR = dialRadius - tickLen - (if (isMajor) radius * 0.01f else 0f)
        val endR = dialRadius - radius * 0.02f

        val start = Offset(center.x + startR * sinA, center.y - startR * cosA)
        val end = Offset(center.x + endR * sinA, center.y - endR * cosA)

        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 3D Structured Watch Hands with Drop Shadows & Sweep Second Hand
 */
private fun DrawScope.drawClockHands(
    center: Offset,
    radius: Float,
    hours: Int,
    minutes: Int,
    seconds: Int,
    isNight: Boolean,
    showSeconds: Boolean
) {
    val dialRadius = radius * 0.90f
    val shadowOffset = Offset(radius * 0.025f, radius * 0.035f)

    // Angles
    val hourAngle = Math.toRadians((hours + minutes / 60.0 + seconds / 3600.0) * 30.0)
    val minuteAngle = Math.toRadians((minutes + seconds / 60.0) * 6.0)
    val secondAngle = Math.toRadians(seconds * 6.0)

    val hourLen = dialRadius * 0.52f
    val minuteLen = dialRadius * 0.78f
    val secondLen = dialRadius * 0.85f

    // 1. Hands Drop Shadow Layer (Gives genuine depth above dial)
    val shadowPaintColor = Color(0x40000000)
    
    // Hour hand shadow
    drawHandShape(
        center = center + shadowOffset,
        length = hourLen,
        width = radius * 0.06f,
        tailLength = radius * 0.12f,
        angleRad = hourAngle,
        fillColor = shadowPaintColor
    )

    // Minute hand shadow
    drawHandShape(
        center = center + shadowOffset,
        length = minuteLen,
        width = radius * 0.045f,
        tailLength = radius * 0.14f,
        angleRad = minuteAngle,
        fillColor = shadowPaintColor
    )

    // 2. Hour Hand (3D Beveled Sword Hand)
    val handBodyColor = if (isNight) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val handBevelLight = if (isNight) Color(0xFFFFFFFF) else Color(0xFF475569)
    val handBevelDark = if (isNight) Color(0xFF94A3B8) else Color(0xFF0F172A)

    draw3DHand(
        center = center,
        length = hourLen,
        width = radius * 0.062f,
        tailLength = radius * 0.12f,
        angleRad = hourAngle,
        lightColor = handBevelLight,
        darkColor = handBevelDark,
        inlayColor = if (isNight) Color(0xFF81D4FA) else Color(0xFFE2E8F0)
    )

    // 3. Minute Hand (3D Beveled Sword Hand)
    draw3DHand(
        center = center,
        length = minuteLen,
        width = radius * 0.048f,
        tailLength = radius * 0.14f,
        angleRad = minuteAngle,
        lightColor = handBevelLight,
        darkColor = handBevelDark,
        inlayColor = if (isNight) Color(0xFF81D4FA) else Color(0xFFE2E8F0)
    )

    // 4. Second Hand (Vermilion / Crimson Red with circular counterweight)
    if (showSeconds) {
        val redColor = Color(0xFFFF3B30)
        val darkRed = Color(0xFFD32F2F)
        val sinS = sin(secondAngle).toFloat()
        val cosS = cos(secondAngle).toFloat()

        // Second hand shadow
        val secShadowCenter = center + shadowOffset * 1.2f
        val secShadowTip = Offset(secShadowCenter.x + secondLen * sinS, secShadowCenter.y - secondLen * cosS)
        val secShadowTail = Offset(secShadowCenter.x - radius * 0.22f * sinS, secShadowCenter.y + radius * 0.22f * cosS)
        drawLine(
            color = shadowPaintColor,
            start = secShadowTail,
            end = secShadowTip,
            strokeWidth = radius * 0.02f,
            cap = StrokeCap.Round
        )

        // Second Hand Line
        val tip = Offset(center.x + secondLen * sinS, center.y - secondLen * cosS)
        val tail = Offset(center.x - radius * 0.22f * sinS, center.y + radius * 0.22f * cosS)
        
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(darkRed, redColor, redColor),
                start = tail,
                end = tip
            ),
            start = tail,
            end = tip,
            strokeWidth = radius * 0.022f,
            cap = StrokeCap.Round
        )

        // Circular Counterweight at tail
        val counterCenter = Offset(center.x - radius * 0.14f * sinS, center.y + radius * 0.14f * cosS)
        drawCircle(
            color = redColor,
            radius = radius * 0.052f,
            center = counterCenter
        )
    }

    // 5. Center Pinion Cap (Polished Chrome & Jewel Rivet)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFCFD8DC),
                Color(0xFF37474F)
            ),
            center = Offset(center.x - radius * 0.015f, center.y - radius * 0.015f),
            radius = radius * 0.065f
        ),
        radius = radius * 0.065f,
        center = center
    )
    // Pinion center ruby / core dot
    drawCircle(
        color = if (showSeconds) Color(0xFFFF3B30) else (if (isNight) Color(0xFF81D4FA) else Color(0xFF1E293B)),
        radius = radius * 0.032f,
        center = center
    )
}

/**
 * 3D Sword Hand with center ridge line separating light and dark facets
 */
private fun DrawScope.draw3DHand(
    center: Offset,
    length: Float,
    width: Float,
    tailLength: Float,
    angleRad: Double,
    lightColor: Color,
    darkColor: Color,
    inlayColor: Color
) {
    val sinA = sin(angleRad).toFloat()
    val cosA = cos(angleRad).toFloat()

    // Normal vector perpendicular to hand
    val normX = cosA
    val normY = sinA

    val tip = Offset(center.x + length * sinA, center.y - length * cosA)
    val tail = Offset(center.x - tailLength * sinA, center.y + tailLength * cosA)
    val leftW = Offset(center.x - width * 0.5f * normX, center.y - width * 0.5f * normY)
    val rightW = Offset(center.x + width * 0.5f * normX, center.y + width * 0.5f * normY)

    // Left Facet (Light)
    val leftPath = Path().apply {
        moveTo(tail.x, tail.y)
        lineTo(leftW.x, leftW.y)
        lineTo(tip.x, tip.y)
        close()
    }
    drawPath(path = leftPath, color = lightColor)

    // Right Facet (Dark)
    val rightPath = Path().apply {
        moveTo(tail.x, tail.y)
        lineTo(rightW.x, rightW.y)
        lineTo(tip.x, tip.y)
        close()
    }
    drawPath(path = rightPath, color = darkColor)

    // Luminous Inlay Channel down the center
    val inlayStart = Offset(center.x + length * 0.15f * sinA, center.y - length * 0.15f * cosA)
    val inlayEnd = Offset(center.x + length * 0.82f * sinA, center.y - length * 0.82f * cosA)
    drawLine(
        color = inlayColor,
        start = inlayStart,
        end = inlayEnd,
        strokeWidth = width * 0.28f,
        cap = StrokeCap.Round
    )
}

/**
 * Helper for drawing hand silhouette (for shadows)
 */
private fun DrawScope.drawHandShape(
    center: Offset,
    length: Float,
    width: Float,
    tailLength: Float,
    angleRad: Double,
    fillColor: Color
) {
    val sinA = sin(angleRad).toFloat()
    val cosA = cos(angleRad).toFloat()
    val normX = cosA
    val normY = sinA

    val tip = Offset(center.x + length * sinA, center.y - length * cosA)
    val tail = Offset(center.x - tailLength * sinA, center.y + tailLength * cosA)
    val leftW = Offset(center.x - width * 0.5f * normX, center.y - width * 0.5f * normY)
    val rightW = Offset(center.x + width * 0.5f * normX, center.y + width * 0.5f * normY)

    val path = Path().apply {
        moveTo(tail.x, tail.y)
        lineTo(leftW.x, leftW.y)
        lineTo(tip.x, tip.y)
        lineTo(rightW.x, rightW.y)
        close()
    }
    drawPath(path = path, color = fillColor)
}

/**
 * Watch Crystal Specular Highlight (Curved glossy dome sheen)
 */
private fun DrawScope.drawWatchCrystalReflection(center: Offset, radius: Float) {
    val glassRadius = radius * 0.88f

    // Crescent/elliptical specular arc across upper portion of glass
    val glassPath = Path().apply {
        val arcRect = androidx.compose.ui.geometry.Rect(
            center.x - glassRadius,
            center.y - glassRadius,
            center.x + glassRadius,
            center.y + glassRadius
        )
        arcTo(arcRect, 180f, 180f, false)
        cubicTo(
            center.x + glassRadius * 0.7f, center.y - glassRadius * 0.2f,
            center.x - glassRadius * 0.7f, center.y - glassRadius * 0.2f,
            center.x - glassRadius, center.y
        )
        close()
    }

    drawPath(
        path = glassPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.24f),
                Color.White.copy(alpha = 0.08f),
                Color.Transparent
            ),
            startY = center.y - glassRadius,
            endY = center.y
        )
    )
}
