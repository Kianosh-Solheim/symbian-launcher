package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Normalized weather condition categories for realistic rendering
 */
enum class WeatherConditionType {
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY_DAY,
    PARTLY_CLOUDY_NIGHT,
    CLOUDY,
    OVERCAST,
    LIGHT_RAIN,
    RAIN,
    HEAVY_RAIN,
    THUNDERSTORM,
    SNOW,
    SLEET,
    FOG,
    WIND
}

/**
 * Parse any raw symbol code string (from Met Norway, Open-Meteo, or text summary)
 * into a WeatherConditionType.
 */
fun parseWeatherCondition(symbolCode: String): WeatherConditionType {
    val s = symbolCode.lowercase()
    val isNight = s.contains("night") || s.contains("polartwilight")
    
    return when {
        s.contains("thunder") || s.contains("lightning") || s.contains("storm") -> WeatherConditionType.THUNDERSTORM
        s.contains("sleet") -> WeatherConditionType.SLEET
        s.contains("snow") || s.contains("blizzard") || s.contains("flurr") -> WeatherConditionType.SNOW
        s.contains("heavyrain") || s.contains("heavyshower") || s.contains("torrential") -> WeatherConditionType.HEAVY_RAIN
        s.contains("lightrain") || s.contains("drizzle") || s.contains("light_rain") -> WeatherConditionType.LIGHT_RAIN
        s.contains("rain") || s.contains("shower") || s.contains("water") -> WeatherConditionType.RAIN
        s.contains("fog") || s.contains("mist") || s.contains("haze") || s.contains("smoke") -> WeatherConditionType.FOG
        s.contains("wind") || s.contains("breeze") || s.contains("gale") -> WeatherConditionType.WIND
        s.contains("overcast") -> WeatherConditionType.OVERCAST
        s.contains("partly") || s.contains("fair") || s.contains("scattered") || s.contains("few") -> {
            if (isNight) WeatherConditionType.PARTLY_CLOUDY_NIGHT else WeatherConditionType.PARTLY_CLOUDY_DAY
        }
        s.contains("cloud") -> WeatherConditionType.CLOUDY
        s.contains("clear") || s.contains("sun") || s.contains("sunny") -> {
            if (isNight) WeatherConditionType.CLEAR_NIGHT else WeatherConditionType.CLEAR_DAY
        }
        else -> if (isNight) WeatherConditionType.CLEAR_NIGHT else WeatherConditionType.CLEAR_DAY
    }
}

/**
 * High-fidelity Realistic Weather Icon Composable.
 * Implements rich skeuomorphic lighting, gradients, 3D sunbeams, volumetric clouds,
 * glossy raindrops, glowing lightning, and textured details inspired by classic iOS & Symbian designs.
 */
@Composable
fun RealisticWeatherIcon(
    symbolCode: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val condition = parseWeatherCondition(symbolCode)
    if (LocalLcd.current) {
        val name = lcdWeatherName(condition)
        val bmp = androidx.compose.runtime.remember(name) { LcdPixelizer.builtIn(name) }
        if (bmp != null) {
            androidx.compose.foundation.Image(bmp, contentDescription = null,
                modifier = modifier.size(size).lcdInvertOnSelection(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
            return
        }
    }
    if (LocalWin95.current) {
        // Retro look: draw the icon at 24×24 and blow it up without smoothing.
        val bmp = androidx.compose.runtime.remember(condition) {
            val px = 24
            val img = androidx.compose.ui.graphics.ImageBitmap(px, px)
            androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
                androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(img), Size(px.toFloat(), px.toFloat())
            ) { drawWeather(condition) }
            img
        }
        androidx.compose.foundation.Image(bmp, contentDescription = null, modifier = modifier.size(size),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
        return
    }
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.matchParentSize()) { drawWeather(condition) }
    }
}

private fun lcdWeatherName(c: WeatherConditionType) = when (c) {
    WeatherConditionType.CLEAR_DAY -> "wx_sun"
    WeatherConditionType.CLEAR_NIGHT -> "wx_moon"
    WeatherConditionType.PARTLY_CLOUDY_DAY -> "wx_partsun"
    WeatherConditionType.PARTLY_CLOUDY_NIGHT -> "wx_partmoon"
    WeatherConditionType.CLOUDY -> "wx_cloud"
    WeatherConditionType.OVERCAST -> "wx_overcast"
    WeatherConditionType.LIGHT_RAIN -> "wx_lightrain"
    WeatherConditionType.RAIN -> "wx_rain"
    WeatherConditionType.HEAVY_RAIN -> "wx_heavyrain"
    WeatherConditionType.THUNDERSTORM -> "wx_thunder"
    WeatherConditionType.SNOW -> "wx_snow"
    WeatherConditionType.SLEET -> "wx_sleet"
    WeatherConditionType.FOG -> "wx_fog"
    WeatherConditionType.WIND -> "wx_wind"
}

private fun DrawScope.drawWeather(condition: WeatherConditionType) {
            when (condition) {
                WeatherConditionType.CLEAR_DAY -> drawRealisticSun(isFull = true)
                WeatherConditionType.CLEAR_NIGHT -> drawRealisticMoon(withStars = true)
                WeatherConditionType.PARTLY_CLOUDY_DAY -> {
                    drawRealisticSunBehindCloud()
                    drawRealisticCloud(offsetX = 0.12f, offsetY = 0.18f, scale = 0.78f, isOvercast = false)
                }
                WeatherConditionType.PARTLY_CLOUDY_NIGHT -> {
                    drawRealisticMoonBehindCloud()
                    drawRealisticCloud(offsetX = 0.12f, offsetY = 0.18f, scale = 0.78f, isOvercast = false)
                }
                WeatherConditionType.CLOUDY -> {
                    // Back cloud layer
                    drawRealisticCloud(offsetX = -0.10f, offsetY = -0.08f, scale = 0.70f, isOvercast = false, alpha = 0.85f)
                    // Front volumetric cloud layer
                    drawRealisticCloud(offsetX = 0.05f, offsetY = 0.10f, scale = 0.82f, isOvercast = false)
                }
                WeatherConditionType.OVERCAST -> {
                    drawRealisticCloud(offsetX = -0.08f, offsetY = -0.06f, scale = 0.75f, isOvercast = true, alpha = 0.9f)
                    drawRealisticCloud(offsetX = 0.04f, offsetY = 0.08f, scale = 0.82f, isOvercast = true)
                }
                WeatherConditionType.LIGHT_RAIN -> {
                    drawRealisticCloud(offsetX = 0.0f, offsetY = -0.08f, scale = 0.78f, isOvercast = false)
                    drawRaindrops(dropCount = 3, isHeavy = false)
                }
                WeatherConditionType.RAIN -> {
                    drawRealisticSunBehindCloud(alpha = 0.45f)
                    drawRealisticCloud(offsetX = 0.0f, offsetY = -0.08f, scale = 0.80f, isOvercast = true)
                    drawRaindrops(dropCount = 5, isHeavy = false)
                }
                WeatherConditionType.HEAVY_RAIN -> {
                    drawRealisticCloud(offsetX = -0.08f, offsetY = -0.16f, scale = 0.76f, isOvercast = true, alpha = 0.95f)
                    drawRealisticCloud(offsetX = 0.02f, offsetY = -0.06f, scale = 0.82f, isOvercast = true)
                    drawRaindrops(dropCount = 7, isHeavy = true)
                }
                WeatherConditionType.THUNDERSTORM -> {
                    drawRealisticCloud(offsetX = 0.0f, offsetY = -0.12f, scale = 0.82f, isOvercast = true)
                    drawLightningBolt()
                    drawRaindrops(dropCount = 4, isHeavy = true)
                }
                WeatherConditionType.SNOW -> {
                    drawRealisticCloud(offsetX = 0.0f, offsetY = -0.10f, scale = 0.80f, isOvercast = false)
                    drawSnowflakes(flakeCount = 4)
                }
                WeatherConditionType.SLEET -> {
                    drawRealisticCloud(offsetX = 0.0f, offsetY = -0.10f, scale = 0.80f, isOvercast = true)
                    drawRaindrops(dropCount = 3, isHeavy = false)
                    drawSnowflakes(flakeCount = 2, offsetYShift = 0.15f)
                }
                WeatherConditionType.FOG -> {
                    drawRealisticCloud(offsetX = 0.0f, offsetY = -0.18f, scale = 0.70f, isOvercast = false, alpha = 0.7f)
                    drawFogLayers()
                }
                WeatherConditionType.WIND -> {
                    drawRealisticCloud(offsetX = 0.0f, offsetY = -0.10f, scale = 0.75f, isOvercast = false)
                    drawWindTrails()
                }
            }
}

// -------------------------------------------------------------
// Canvas Drawing Implementation for Classic Skeuomorphic Weather
// -------------------------------------------------------------

/**
 * 3D Glowing Sun with Corona, Radial Beams & Luminous Highlights
 */
private fun DrawScope.drawRealisticSun(isFull: Boolean = true) {
    val center = Offset(size.width * 0.5f, size.height * 0.5f)
    val radius = size.minDimension * 0.28f

    // 1. Outer ambient glow / corona halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x80FFB300),
                Color(0x35FF9100),
                Color(0x00FF6D00)
            ),
            center = center,
            radius = radius * 2.1f
        ),
        center = center,
        radius = radius * 2.1f
    )

    // 2. Radiant Sun Beams / Rays (12 rays with smooth gradient falloff)
    val numRays = 12
    val innerRayDist = radius * 1.15f
    val outerRayDist = radius * 1.85f
    for (i in 0 until numRays) {
        val angleDeg = i * (360f / numRays)
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()

        val start = Offset(center.x + cosA * innerRayDist, center.y + sinA * innerRayDist)
        val end = Offset(center.x + cosA * outerRayDist, center.y + sinA * outerRayDist)

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFEA00).copy(alpha = 0.95f),
                    Color(0xFFFF9100).copy(alpha = 0.50f),
                    Color(0xFFFF6D00).copy(alpha = 0.0f)
                ),
                start = start,
                end = end
            ),
            start = start,
            end = end,
            strokeWidth = size.minDimension * 0.045f,
            cap = StrokeCap.Round
        )
    }

    // 3. Spherical 3D Sun Body (Radial gradient offset to upper left)
    val lightOffset = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFFFFFFE0), // Ultra bright specular core
                0.25f to Color(0xFFFFF176), // Soft bright yellow
                0.55f to Color(0xFFFFD600), // Rich solar yellow
                0.82f to Color(0xFFFF9100), // Warm vibrant orange
                1.00f to Color(0xFFFF6D00)  // Deep rim orange
            ),
            center = lightOffset,
            radius = radius * 1.35f
        ),
        center = center,
        radius = radius
    )

    // 4. Subtle inner glossy rim highlight
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x90FFFFFF),
                Color(0x00FFFFFF)
            ),
            center = lightOffset,
            radius = radius * 0.65f
        ),
        center = lightOffset,
        radius = radius * 0.60f
    )

    // 5. Classic semi-transparent wispy clouds across the sun face (as in user reference image 1)
    drawWispySunCloudStreaks(center, radius)
}

/**
 * Sun positioned in top-left or background for partly cloudy/rain conditions
 */
private fun DrawScope.drawRealisticSunBehindCloud(alpha: Float = 1.0f) {
    val center = Offset(size.width * 0.36f, size.height * 0.38f)
    val radius = size.minDimension * 0.24f

    // Ambient glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x60FFB300).copy(alpha = 0.45f * alpha),
                Color(0x00FF6D00)
            ),
            center = center,
            radius = radius * 1.8f
        ),
        center = center,
        radius = radius * 1.8f
    )

    // Sun Rays top/left
    val numRays = 8
    val innerRayDist = radius * 1.15f
    val outerRayDist = radius * 1.70f
    for (i in 0 until numRays) {
        val angleDeg = 160f + i * 25f
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()

        val start = Offset(center.x + cosA * innerRayDist, center.y + sinA * innerRayDist)
        val end = Offset(center.x + cosA * outerRayDist, center.y + sinA * outerRayDist)

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFEE55).copy(alpha = 0.9f * alpha),
                    Color(0xFFFF9100).copy(alpha = 0.2f * alpha),
                    Color(0x00FF6D00)
                ),
                start = start,
                end = end
            ),
            start = start,
            end = end,
            strokeWidth = size.minDimension * 0.04f,
            cap = StrokeCap.Round
        )
    }

    // Sun body
    val lightOffset = Offset(center.x - radius * 0.32f, center.y - radius * 0.32f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFFFFFFE0).copy(alpha = alpha),
                0.30f to Color(0xFFFFF176).copy(alpha = alpha),
                0.65f to Color(0xFFFFD600).copy(alpha = alpha),
                1.00f to Color(0xFFFF9100).copy(alpha = alpha)
            ),
            center = lightOffset,
            radius = radius * 1.3f
        ),
        center = center,
        radius = radius
    )
}

/**
 * Delicate horizontal wispy clouds across the sun surface (User Image 1 style)
 */
private fun DrawScope.drawWispySunCloudStreaks(center: Offset, radius: Float) {
    val streakBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x00FFFFFF),
            Color(0x66FFFFFF),
            Color(0xAAFFFFFF),
            Color(0x66FFFFFF),
            Color(0x00FFFFFF)
        ),
        start = Offset(center.x - radius * 1.3f, center.y),
        end = Offset(center.x + radius * 1.3f, center.y)
    )

    // Streak 1 (middle-upper)
    drawOval(
        brush = streakBrush,
        topLeft = Offset(center.x - radius * 1.1f, center.y - radius * 0.12f),
        size = Size(radius * 2.2f, radius * 0.24f)
    )

    // Streak 2 (middle-lower)
    drawOval(
        brush = streakBrush,
        topLeft = Offset(center.x - radius * 0.9f, center.y + radius * 0.18f),
        size = Size(radius * 1.8f, radius * 0.20f)
    )
}

/**
 * 3D Glowing Moon with craters & twinkle stars for clear night conditions
 */
private fun DrawScope.drawRealisticMoon(withStars: Boolean = true) {
    val center = Offset(size.width * 0.48f, size.height * 0.48f)
    val radius = size.minDimension * 0.28f

    // 1. Soft Lunar Glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x55FFF9C4),
                Color(0x20FFE082),
                Color(0x00FFD54F)
            ),
            center = center,
            radius = radius * 1.8f
        ),
        center = center,
        radius = radius * 1.8f
    )

    // 2. Crescent Moon Path
    val moonPath = Path().apply {
        // Outer arc
        moveTo(center.x + radius * 0.1f, center.y - radius * 0.95f)
        cubicTo(
            center.x + radius * 1.1f, center.y - radius * 0.6f,
            center.x + radius * 1.1f, center.y + radius * 0.6f,
            center.x + radius * 0.1f, center.y + radius * 0.95f
        )
        // Inner cutout arc
        cubicTo(
            center.x + radius * 0.65f, center.y + radius * 0.5f,
            center.x + radius * 0.65f, center.y - radius * 0.5f,
            center.x + radius * 0.1f, center.y - radius * 0.95f
        )
        close()
    }

    // Moon body gradient
    drawPath(
        path = moonPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFFDE7), // Top light
                Color(0xFFFFF59D), // Warm body
                Color(0xFFFFE082), // Soft golden shadow
                Color(0xFFFFCA28)
            ),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x + radius, center.y + radius)
        )
    )

    // Moon highlight edge
    drawPath(
        path = moonPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0x88FFE082)
            ),
            start = Offset(center.x + radius, center.y - radius),
            end = Offset(center.x + radius, center.y + radius)
        ),
        style = Stroke(width = size.minDimension * 0.02f)
    )

    // 3. Stars around the moon
    if (withStars) {
        drawStar(Offset(size.width * 0.22f, size.height * 0.28f), size.minDimension * 0.07f)
        drawStar(Offset(size.width * 0.78f, size.height * 0.25f), size.minDimension * 0.09f)
        drawStar(Offset(size.width * 0.82f, size.height * 0.68f), size.minDimension * 0.06f)
    }
}

private fun DrawScope.drawRealisticMoonBehindCloud() {
    val center = Offset(size.width * 0.38f, size.height * 0.36f)
    val radius = size.minDimension * 0.22f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x40FFF9C4), Color(0x00FFD54F)),
            center = center,
            radius = radius * 1.6f
        ),
        center = center,
        radius = radius * 1.6f
    )

    val moonPath = Path().apply {
        moveTo(center.x, center.y - radius * 0.9f)
        cubicTo(
            center.x + radius * 0.95f, center.y - radius * 0.55f,
            center.x + radius * 0.95f, center.y + radius * 0.55f,
            center.x, center.y + radius * 0.9f
        )
        cubicTo(
            center.x + radius * 0.55f, center.y + radius * 0.45f,
            center.x + radius * 0.55f, center.y - radius * 0.45f,
            center.x, center.y - radius * 0.9f
        )
        close()
    }

    drawPath(
        path = moonPath,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFFDE7), Color(0xFFFFE082), Color(0xFFFFCA28)),
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x + radius, center.y + radius)
        )
    )
}

private fun DrawScope.drawStar(center: Offset, starRadius: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - starRadius)
        lineTo(center.x + starRadius * 0.25f, center.y - starRadius * 0.25f)
        lineTo(center.x + starRadius, center.y)
        lineTo(center.x + starRadius * 0.25f, center.y + starRadius * 0.25f)
        lineTo(center.x, center.y + starRadius)
        lineTo(center.x - starRadius * 0.25f, center.y + starRadius * 0.25f)
        lineTo(center.x - starRadius, center.y)
        lineTo(center.x - starRadius * 0.25f, center.y - starRadius * 0.25f)
        close()
    }

    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xAAFFF9C4), Color(0x00FFE082)),
            center = center,
            radius = starRadius * 1.2f
        )
    )
}

/**
 * Volumetric, Multi-Layered Fluffy Cumulus Cloud (User Image 2 style).
 * Features realistic top-left illumination, silver rim light, and volumetric bottom shading.
 */
private fun DrawScope.drawRealisticCloud(
    offsetX: Float = 0.0f,
    offsetY: Float = 0.0f,
    scale: Float = 0.8f,
    isOvercast: Boolean = false,
    alpha: Float = 1.0f
) {
    val w = size.width * scale
    val h = size.height * scale
    val baseCenterX = size.width * (0.50f + offsetX)
    val baseCenterY = size.height * (0.54f + offsetY)

    // Palette based on conditions
    val baseShadowColor = if (isOvercast) Color(0xFF37474F).copy(alpha = 0.85f * alpha) else Color(0xFF78909C).copy(alpha = 0.60f * alpha)
    val bodyMidColor = if (isOvercast) Color(0xFF78909C).copy(alpha = alpha) else Color(0xFFCFD8DC).copy(alpha = alpha)
    val bodyTopColor = if (isOvercast) Color(0xFFB0BEC5).copy(alpha = alpha) else Color(0xFFFFFFFF).copy(alpha = alpha)
    val highlightColor = if (isOvercast) Color(0xFFECEFF1).copy(alpha = alpha) else Color(0xFFFFFFFF).copy(alpha = alpha)

    // Cloud Lobe Centers relative to base
    val lobe1 = Offset(baseCenterX - w * 0.26f, baseCenterY + h * 0.06f) // Left bottom puff
    val r1 = w * 0.20f

    val lobe2 = Offset(baseCenterX - w * 0.12f, baseCenterY - h * 0.12f) // Mid-left top puff
    val r2 = w * 0.26f

    val lobe3 = Offset(baseCenterX + w * 0.14f, baseCenterY - h * 0.06f) // Mid-right high puff
    val r3 = w * 0.23f

    val lobe4 = Offset(baseCenterX + w * 0.28f, baseCenterY + h * 0.08f) // Right bottom puff
    val r4 = w * 0.18f

    val basePillTopLeft = Offset(baseCenterX - w * 0.28f, baseCenterY - h * 0.02f)
    val basePillSize = Size(w * 0.58f, h * 0.26f)

    // 1. Ambient soft drop shadow underneath
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0x40000000).copy(alpha = 0.35f * alpha),
                Color(0x00000000)
            ),
            center = Offset(baseCenterX, baseCenterY + h * 0.16f),
            radius = w * 0.45f
        ),
        topLeft = Offset(baseCenterX - w * 0.40f, baseCenterY + h * 0.04f),
        size = Size(w * 0.80f, h * 0.26f)
    )

    // 2. Base volumetric shadow gradient (gives depth to the bottom of the cloud)
    val lobes = listOf(
        Triple(lobe1, r1, 1.0f),
        Triple(lobe2, r2, 1.0f),
        Triple(lobe3, r3, 1.0f),
        Triple(lobe4, r4, 1.0f)
    )

    // Draw base bottom lobes with shading
    lobes.forEach { (pos, r, _) ->
        drawCircle(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to bodyTopColor,
                    0.5f to bodyMidColor,
                    1.0f to baseShadowColor
                ),
                startY = pos.y - r,
                endY = pos.y + r
            ),
            center = pos,
            radius = r
        )
    }

    // Connect cloud base smoothly
    drawRoundRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to bodyTopColor,
                0.6f to bodyMidColor,
                1.0f to baseShadowColor
            ),
            startY = basePillTopLeft.y,
            endY = basePillTopLeft.y + basePillSize.height
        ),
        topLeft = basePillTopLeft,
        size = basePillSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(basePillSize.height * 0.5f)
    )

    // 3. Volumetric 3D specular highlight puffs (upper-left lighting on each lobe)
    lobes.forEach { (pos, r, _) ->
        val hlCenter = Offset(pos.x - r * 0.28f, pos.y - r * 0.28f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to highlightColor,
                    0.45f to highlightColor.copy(alpha = 0.75f * alpha),
                    1.0f to Color.Transparent
                ),
                center = hlCenter,
                radius = r * 0.85f
            ),
            center = hlCenter,
            radius = r * 0.85f
        )
    }

    // 4. Crisp silver lining / rim highlight along upper cloud contours
    val rimPath = Path().apply {
        // Upper contour curve
        moveTo(lobe1.x - r1 * 0.9f, lobe1.y)
        cubicTo(
            lobe1.x - r1 * 0.8f, lobe1.y - r1 * 0.8f,
            lobe2.x - r2 * 0.8f, lobe2.y - r2 * 0.8f,
            lobe2.x, lobe2.y - r2
        )
        cubicTo(
            lobe2.x + r2 * 0.6f, lobe2.y - r2 * 0.9f,
            lobe3.x - r3 * 0.6f, lobe3.y - r3 * 0.9f,
            lobe3.x, lobe3.y - r3
        )
        cubicTo(
            lobe3.x + r3 * 0.7f, lobe3.y - r3 * 0.7f,
            lobe4.x + r4 * 0.6f, lobe4.y - r4 * 0.5f,
            lobe4.x + r4 * 0.9f, lobe4.y + r4 * 0.3f
        )
    }

    drawPath(
        path = rimPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0x99FFFFFF).copy(alpha = alpha),
                Color(0xFFFFFFFF).copy(alpha = alpha),
                Color(0x88FFFFFF).copy(alpha = alpha)
            ),
            start = Offset(baseCenterX - w * 0.3f, baseCenterY - h * 0.2f),
            end = Offset(baseCenterX + w * 0.3f, baseCenterY + h * 0.1f)
        ),
        style = Stroke(
            width = size.minDimension * 0.022f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/**
 * Glossy teardrop raindrops with specular highlight glints
 */
private fun DrawScope.drawRaindrops(dropCount: Int = 4, isHeavy: Boolean = false) {
    val dropWidth = size.minDimension * (if (isHeavy) 0.055f else 0.045f)
    val dropHeight = size.minDimension * (if (isHeavy) 0.16f else 0.13f)

    val xPositions = when (dropCount) {
        3 -> listOf(0.35f, 0.50f, 0.65f)
        4 -> listOf(0.30f, 0.44f, 0.58f, 0.72f)
        5 -> listOf(0.26f, 0.38f, 0.50f, 0.62f, 0.74f)
        else -> listOf(0.24f, 0.34f, 0.44f, 0.54f, 0.64f, 0.74f, 0.82f)
    }

    val yStagger = listOf(0.0f, 0.08f, -0.04f, 0.06f, -0.02f, 0.07f, 0.01f)

    xPositions.forEachIndexed { idx, xRatio ->
        val yRatio = 0.72f + yStagger[idx % yStagger.size]
        val center = Offset(size.width * xRatio, size.height * yRatio)

        // Angled falling droplet path
        val dropPath = Path().apply {
            moveTo(center.x + dropWidth * 0.35f, center.y - dropHeight * 0.5f) // Top pointed tip
            cubicTo(
                center.x + dropWidth * 0.6f, center.y,
                center.x + dropWidth * 0.6f, center.y + dropHeight * 0.5f,
                center.x, center.y + dropHeight * 0.5f // Rounded bottom
            )
            cubicTo(
                center.x - dropWidth * 0.6f, center.y + dropHeight * 0.5f,
                center.x - dropWidth * 0.6f, center.y,
                center.x + dropWidth * 0.35f, center.y - dropHeight * 0.5f
            )
            close()
        }

        // Drop gradient
        drawPath(
            path = dropPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFE0F7FA), // Bright cyan top
                    Color(0xFF4FC3F7), // Sky blue body
                    Color(0xFF0288D1), // Deep blue base
                    Color(0xFF01579B)
                ),
                start = Offset(center.x, center.y - dropHeight * 0.5f),
                end = Offset(center.x, center.y + dropHeight * 0.5f)
            )
        )

        // Specular glint on droplet
        drawCircle(
            color = Color(0xCCFFFFFF),
            center = Offset(center.x - dropWidth * 0.2f, center.y + dropHeight * 0.15f),
            radius = dropWidth * 0.25f
        )
    }
}

/**
 * Electric glowing lightning bolt with neon outer halo and crisp yellow-white core
 */
private fun DrawScope.drawLightningBolt() {
    val startX = size.width * 0.48f
    val startY = size.height * 0.42f
    val boltW = size.width * 0.28f
    val boltH = size.height * 0.52f

    val boltPath = Path().apply {
        moveTo(startX, startY)
        lineTo(startX - boltW * 0.35f, startY + boltH * 0.42f)
        lineTo(startX - boltW * 0.05f, startY + boltH * 0.42f)
        lineTo(startX - boltW * 0.45f, startY + boltH)
        lineTo(startX + boltW * 0.35f, startY + boltH * 0.48f)
        lineTo(startX + boltW * 0.05f, startY + boltH * 0.48f)
        close()
    }

    // 1. Electric Yellow Glow Halo
    drawPath(
        path = boltPath,
        color = Color(0x66FFEA00),
        style = Stroke(width = size.minDimension * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // 2. Vibrant Yellow Body
    drawPath(
        path = boltPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFF8D),
                Color(0xFFFFEA00),
                Color(0xFFFFD600),
                Color(0xFFFFAB00)
            ),
            startY = startY,
            endY = startY + boltH
        )
    )

    // 3. Crisp White Center Core Line
    val corePath = Path().apply {
        moveTo(startX, startY + boltH * 0.08f)
        lineTo(startX - boltW * 0.22f, startY + boltH * 0.42f)
        lineTo(startX - boltW * 0.05f, startY + boltH * 0.42f)
        lineTo(startX - boltW * 0.28f, startY + boltH * 0.88f)
    }
    drawPath(
        path = corePath,
        color = Color.White.copy(alpha = 0.9f),
        style = Stroke(width = size.minDimension * 0.025f, cap = StrokeCap.Round)
    )
}

/**
 * Intricate 6-branch Ice Crystals and Snowflakes with blue/white glow
 */
private fun DrawScope.drawSnowflakes(flakeCount: Int = 3, offsetYShift: Float = 0.0f) {
    val flakePositions = when (flakeCount) {
        2 -> listOf(Offset(size.width * 0.38f, size.height * (0.75f + offsetYShift)), Offset(size.width * 0.65f, size.height * (0.78f + offsetYShift)))
        else -> listOf(
            Offset(size.width * 0.30f, size.height * (0.72f + offsetYShift)),
            Offset(size.width * 0.50f, size.height * (0.80f + offsetYShift)),
            Offset(size.width * 0.70f, size.height * (0.74f + offsetYShift)),
            Offset(size.width * 0.85f, size.height * (0.82f + offsetYShift))
        )
    }

    flakePositions.forEachIndexed { i, center ->
        val flakeR = size.minDimension * (if (i % 2 == 0) 0.09f else 0.075f)
        drawSingleSnowflake(center, flakeR)
    }
}

private fun DrawScope.drawSingleSnowflake(center: Offset, radius: Float) {
    // 6-branch star
    for (i in 0 until 6) {
        val angleDeg = i * 60f
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()

        val armEnd = Offset(center.x + cosA * radius, center.y + sinA * radius)

        // Main branch
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFB3E5FC)),
                start = center,
                end = armEnd
            ),
            start = center,
            end = armEnd,
            strokeWidth = size.minDimension * 0.025f,
            cap = StrokeCap.Round
        )

        // Branch barbules
        val barbDist = radius * 0.65f
        val barbLen = radius * 0.35f
        val barbCenter = Offset(center.x + cosA * barbDist, center.y + sinA * barbDist)

        val barbAngle1 = Math.toRadians((angleDeg + 45f).toDouble())
        val barbEnd1 = Offset(barbCenter.x + cos(barbAngle1).toFloat() * barbLen, barbCenter.y + sin(barbAngle1).toFloat() * barbLen)
        drawLine(
            color = Color(0xFFE1F5FE),
            start = barbCenter,
            end = barbEnd1,
            strokeWidth = size.minDimension * 0.018f,
            cap = StrokeCap.Round
        )

        val barbAngle2 = Math.toRadians((angleDeg - 45f).toDouble())
        val barbEnd2 = Offset(barbCenter.x + cos(barbAngle2).toFloat() * barbLen, barbCenter.y + sin(barbAngle2).toFloat() * barbLen)
        drawLine(
            color = Color(0xFFE1F5FE),
            start = barbCenter,
            end = barbEnd2,
            strokeWidth = size.minDimension * 0.018f,
            cap = StrokeCap.Round
        )
    }

    // Ice crystal center dot
    drawCircle(
        color = Color.White,
        center = center,
        radius = radius * 0.22f
    )
}

/**
 * Layered Atmospheric Fog and Mist Bands
 */
private fun DrawScope.drawFogLayers() {
    val fogWidth = size.width * 0.85f
    val fogHeight = size.height * 0.08f

    val yOffsets = listOf(0.55f, 0.68f, 0.81f)
    val opacities = listOf(0.85f, 0.70f, 0.55f)

    yOffsets.forEachIndexed { i, yRel ->
        val yPos = size.height * yRel
        val shiftX = if (i % 2 == 0) size.width * 0.08f else size.width * 0.04f

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x00ECEFF1),
                    Color(0xCCE0E0E0).copy(alpha = opacities[i]),
                    Color(0xFFECEFF1).copy(alpha = opacities[i]),
                    Color(0xCCE0E0E0).copy(alpha = opacities[i]),
                    Color(0x00ECEFF1)
                ),
                start = Offset(shiftX, yPos),
                end = Offset(shiftX + fogWidth, yPos)
            ),
            topLeft = Offset(shiftX, yPos - fogHeight * 0.5f),
            size = Size(fogWidth, fogHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(fogHeight * 0.5f)
        )
    }
}

/**
 * Curved wind gust swooshes
 */
private fun DrawScope.drawWindTrails() {
    val windPath1 = Path().apply {
        moveTo(size.width * 0.15f, size.height * 0.65f)
        cubicTo(
            size.width * 0.45f, size.height * 0.62f,
            size.width * 0.70f, size.height * 0.68f,
            size.width * 0.85f, size.height * 0.65f
        )
        cubicTo(
            size.width * 0.92f, size.height * 0.62f,
            size.width * 0.90f, size.height * 0.52f,
            size.width * 0.82f, size.height * 0.54f
        )
    }

    val windPath2 = Path().apply {
        moveTo(size.width * 0.25f, size.height * 0.78f)
        cubicTo(
            size.width * 0.50f, size.height * 0.76f,
            size.width * 0.72f, size.height * 0.82f,
            size.width * 0.80f, size.height * 0.78f
        )
    }

    drawPath(
        path = windPath1,
        brush = Brush.linearGradient(
            colors = listOf(Color(0x00B0BEC5), Color(0xCCECEFF1), Color(0xFFFFFFFF), Color(0x44B0BEC5)),
            start = Offset(size.width * 0.15f, size.height * 0.65f),
            end = Offset(size.width * 0.92f, size.height * 0.54f)
        ),
        style = Stroke(width = size.minDimension * 0.035f, cap = StrokeCap.Round)
    )

    drawPath(
        path = windPath2,
        brush = Brush.linearGradient(
            colors = listOf(Color(0x00B0BEC5), Color(0xAAECEFF1), Color(0x00ECEFF1)),
            start = Offset(size.width * 0.25f, size.height * 0.78f),
            end = Offset(size.width * 0.80f, size.height * 0.78f)
        ),
        style = Stroke(width = size.minDimension * 0.03f, cap = StrokeCap.Round)
    )
}
