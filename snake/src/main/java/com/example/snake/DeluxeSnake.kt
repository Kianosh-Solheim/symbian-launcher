package com.example.snake

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Palette for the forest look used with the Classic S60 Sky theme.
private val ForestDark = Color(0xFF16351A)
private val Grass = Color(0xFF4E9A30)
private val GrassLight = Color(0xFF5DAE3A)
private val SnakeYellow = Color(0xFFF7D533)
private val SnakeShade = Color(0xFFE0A81C)
private val SnakeEdge = Color(0xFF7A4A0C)
private val AppleRed = Color(0xFFE02C22)
private val Cream = Color(0xFFFFF6D6)
private val Brown = Color(0xFF5A3A12)
private val Parchment = Color(0xFFEFDDA8)
private val Gold = Color(0xFFFFC72A)
private val Orange = Color(0xFFF08A1C)

/**
 * Snake dressed as a cartoon forest game for the Classic S60 Sky theme:
 * a grass clearing framed by trees, a round yellow snake with eyes, apples to eat,
 * a cream score bar, a parchment high-score scroll and a night-sky title menu.
 */
@Composable
internal fun DeluxeSnake(g: Game) {
    LaunchedEffect(g.phase, g.level) {
        while (g.phase == Phase.PLAYING) { delay(g.stepMs); g.step() }
    }
    var drag by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().background(ForestDark).systemBarsPadding()
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { drag = Offset.Zero }, onDragEnd = {
                    if (drag.getDistance() > 30f) g.steer(if (abs(drag.x) > abs(drag.y)) (if (drag.x > 0) Dir.RIGHT else Dir.LEFT) else (if (drag.y > 0) Dir.DOWN else Dir.UP))
                }) { _, d -> drag += d }
            }
            .pointerInput(Unit) { detectTapGestures { g.ok() } }
    ) {
        if (g.phase == Phase.MENU) { TitleScreen(g); return@Box }
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val margin = 16.dp
                val cell = minOf((maxWidth - margin * 2) / 20, (maxHeight - margin * 2) / 12)
                g.cols = 20
                g.rows = ((maxHeight - margin * 2) / cell).toInt().coerceIn(12, 40)
                val fieldW = cell * g.cols; val fieldH = cell * g.rows
                // Trees all round the clearing.
                Canvas(Modifier.fillMaxSize()) { drawForest(this.size.width, this.size.height) }
                Box(Modifier.align(Alignment.Center).size(fieldW + 8.dp, fieldH + 8.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFF2E6A1E)).padding(4.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = size.width / g.cols
                        drawClearing(g.cols, g.rows, c)
                        drawApple(g.food.x * c + c / 2, g.food.y * c + c / 2, c * 0.46f)
                        g.bonus?.let { b -> if (g.tick % 2 == 0 || g.bonusLeft > 8) drawGoldenApple(b.x * c + c / 2, b.y * c + c / 2, c * 0.5f, g.tick) }
                        drawSnake(g.snake, g.dirNow, c, g.cols, g.rows)
                    }
                }
                // Pause button, top right.
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF5A4A), Color(0xFFB0140C)))).border(1.dp, Color(0xFF6A0A06), RoundedCornerShape(6.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { g.ok() }, contentAlignment = Alignment.Center) {
                    Text(if (g.phase == Phase.PLAYING) "II" else "▶", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
            Hud(g)
        }
        if (g.phase == Phase.PAUSED || g.phase == Phase.OVER) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Scroll(Modifier.padding(24.dp).widthIn(max = 280.dp)) {
                Outlined(if (g.phase == Phase.PAUSED) "Paused" else "Game over", 26.sp)
                Spacer(Modifier.height(6.dp))
                if (g.phase == Phase.OVER) ScrollText("Score ${g.score}" + if (g.score >= g.best && g.score > 0) " — new best!" else "", 16.sp, bold = true)
                Spacer(Modifier.height(8.dp))
                HighScores(g)
                Spacer(Modifier.height(10.dp))
                ScrollText(if (g.phase == Phase.PAUSED) "OK to continue · Back for menu" else "OK to play again · Back for menu", 12.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ HUD

@Composable
private fun Hud(g: Game) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Color.White, Cream)))
            .border(2.dp, Brown, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            // Progress dots towards the next golden apple.
            Row { repeat(5) { i -> Box(Modifier.padding(end = 2.dp).size(6.dp).clip(CircleShape).background(if (i < g.eaten % 5) AppleRed else Brown.copy(alpha = 0.25f))) } }
            Text(g.score.toString(), color = Brown, fontWeight = FontWeight.Black, fontSize = 20.sp, fontFamily = FontFamily.SansSerif)
        }
        HudItem("SPEED", "⚡${g.level}")
        HudItem("APPLES", "● ${g.eaten}", AppleRed)
        HudItem("BONUS", if (g.bonus != null) "${g.bonusLeft}" else "–", Orange)
    }
}

@Composable
private fun HudItem(label: String, value: String, tint: Color = Brown) = Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, color = Brown.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    Text(value, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Black)
}

// ------------------------------------------------------------------ title menu

@Composable
private fun TitleScreen(g: Game) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawNight(size.width, size.height) }
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.rotate(-4f)) { Outlined("SNAKE", 54.sp) }
            Outlined("in the forest", 18.sp, italic = true)
            Spacer(Modifier.height(22.dp))
            Column(horizontalAlignment = Alignment.Start) {
                MenuLine(1, "Play") { g.newGame() }
                MenuLine(2, "Speed  ${"●".repeat(g.level)}${"○".repeat(9 - g.level)}") { g.level = (g.level % 9) + 1; g.onSave() }
                MenuLine(3, "Maze: ${if (g.walls) "walls" else "open"}") { g.walls = !g.walls; g.onSave() }
            }
            Spacer(Modifier.height(14.dp))
            Scroll(Modifier.widthIn(max = 220.dp)) {
                ScrollText("Hi-scores", 15.sp, bold = true)
                HighScores(g)
            }
            Spacer(Modifier.weight(1f))
            Text("OK or 1 to play · ▲▼ speed · ◀▶ maze", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
        }
        // A friendly snake mascot of our own, bottom left.
        Canvas(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 26.dp).size(110.dp)) { drawMascot() }
    }
}

@Composable
private fun MenuLine(n: Int, text: String, onClick: () -> Unit) {
    Row(Modifier.padding(vertical = 4.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFF6A5A), Color(0xFFB0140C)))).border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Outlined(text, 20.sp)
    }
}

/** Big yellow-orange lettering with a dark outline, like old game titles. */
@Composable
private fun Outlined(text: String, size: TextUnit, italic: Boolean = false) {
    Box {
        Text(text, style = TextStyle(color = Color(0xFF3A1E04), fontSize = size, fontWeight = FontWeight.Black, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            drawStyle = Stroke(width = 6f), fontFamily = FontFamily.SansSerif))
        Text(text, style = TextStyle(brush = Brush.verticalGradient(listOf(Color(0xFFFFF27A), Gold, Orange)), fontSize = size, fontWeight = FontWeight.Black,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, fontFamily = FontFamily.SansSerif))
    }
}

// ------------------------------------------------------------------ parchment

@Composable
private fun Scroll(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ScrollRoll()
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp).background(Brush.horizontalGradient(listOf(Color(0xFFDCC48A), Parchment, Color(0xFFF6E8BE), Parchment, Color(0xFFDCC48A))))
            .padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
        ScrollRoll()
    }
}

@Composable
private fun ScrollRoll() = Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
    .background(Brush.verticalGradient(listOf(Color(0xFFF3E3B2), Color(0xFFC9A863), Color(0xFF8C6A2E)))))

@Composable
private fun ScrollText(t: String, size: TextUnit, bold: Boolean = false) =
    Text(t, color = Brown, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)

@Composable
private fun HighScores(g: Game) {
    val tops = g.tops
    if (tops.isEmpty()) { ScrollText("No scores yet", 13.sp); return }
    tops.forEachIndexed { i, s ->
        Row(Modifier.width(150.dp)) {
            Text("${i + 1}.", color = Brown, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(26.dp))
            Text("$s", color = Brown, fontFamily = FontFamily.Serif, fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

// ------------------------------------------------------------------ drawing

private fun DrawScope.tree(x: Float, y: Float, r: Float, seed: Int) {
    val rnd = Random(seed)
    val base = lerp(Color(0xFF1E5A22), Color(0xFF2F7A2C), rnd.nextFloat())
    drawCircle(Color.Black.copy(alpha = 0.28f), r * 1.02f, Offset(x + r * 0.12f, y + r * 0.16f))
    // Canopy made of overlapping puffs.
    repeat(5) { i ->
        val a = i * 1.256f + rnd.nextFloat()
        drawCircle(base, r * 0.55f, Offset(x + cos(a) * r * 0.42f, y + sin(a) * r * 0.42f))
    }
    drawCircle(base, r * 0.62f, Offset(x, y))
    drawCircle(lerp(base, Color(0xFFB8E86A), 0.35f), r * 0.32f, Offset(x - r * 0.25f, y - r * 0.28f))
    drawCircle(lerp(base, Color.White, 0.5f).copy(alpha = 0.35f), r * 0.12f, Offset(x - r * 0.32f, y - r * 0.36f))
}

private fun DrawScope.drawForest(w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B4420), ForestDark)))
    val r = minOf(w, h) * 0.075f
    var seed = 7
    // Along the four edges, a little jittered.
    var x = 0f
    while (x < w + r) { tree(x, r * 0.5f, r, seed++); tree(x + r * 0.6f, h - r * 0.4f, r, seed++); x += r * 1.2f }
    var y = r
    while (y < h) { tree(r * 0.35f, y, r * 0.9f, seed++); tree(w - r * 0.35f, y + r * 0.5f, r * 0.9f, seed++); y += r * 1.15f }
}

private fun DrawScope.drawClearing(cols: Int, rows: Int, c: Float) {
    drawRect(Grass)
    // Mown stripes and little tufts/flowers in fixed spots.
    for (i in 0 until cols step 2) drawRect(GrassLight.copy(alpha = 0.45f), Offset(i * c, 0f), Size(c, size.height))
    val rnd = Random(42)
    repeat(cols * rows / 6) {
        val px = rnd.nextFloat() * size.width; val py = rnd.nextFloat() * size.height
        when (rnd.nextInt(6)) {
            0 -> { drawCircle(Color.White, c * 0.09f, Offset(px, py)); drawCircle(Color(0xFFFFE04A), c * 0.04f, Offset(px, py)) }
            1 -> drawCircle(Color(0xFFFFE04A), c * 0.07f, Offset(px, py))
            else -> {
                val t = Color(0xFF3B7E22)
                drawLine(t, Offset(px, py), Offset(px - c * 0.12f, py - c * 0.22f), c * 0.06f, StrokeCap.Round)
                drawLine(t, Offset(px, py), Offset(px + c * 0.02f, py - c * 0.28f), c * 0.06f, StrokeCap.Round)
                drawLine(t, Offset(px, py), Offset(px + c * 0.14f, py - c * 0.2f), c * 0.06f, StrokeCap.Round)
            }
        }
    }
    // Soft inner shadow along the edge.
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), 0f, c), size = Size(size.width, c))
}

private fun DrawScope.drawApple(x: Float, y: Float, r: Float) {
    drawCircle(Color.Black.copy(alpha = 0.25f), r, Offset(x + r * 0.15f, y + r * 0.25f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF7A5C), AppleRed, Color(0xFF8E0E08)), Offset(x - r * 0.35f, y - r * 0.35f), r * 1.5f), r, Offset(x, y))
    drawCircle(Color.White.copy(alpha = 0.7f), r * 0.2f, Offset(x - r * 0.4f, y - r * 0.38f))
    drawLine(Color(0xFF5A3410), Offset(x, y - r * 0.8f), Offset(x + r * 0.15f, y - r * 1.25f), r * 0.16f, StrokeCap.Round)
    val leaf = Path().apply { moveTo(x + r * 0.15f, y - r * 1.05f); quadraticTo(x + r * 0.75f, y - r * 1.45f, x + r * 0.95f, y - r * 0.95f); quadraticTo(x + r * 0.5f, y - r * 0.75f, x + r * 0.15f, y - r * 1.05f) }
    drawPath(leaf, Color(0xFF3FA52A))
}

private fun DrawScope.drawGoldenApple(x: Float, y: Float, r: Float, tick: Int) {
    drawCircle(Gold.copy(alpha = 0.35f), r * 1.5f, Offset(x, y))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF6A0), Gold, Color(0xFFB07800)), Offset(x - r * 0.35f, y - r * 0.35f), r * 1.5f), r, Offset(x, y))
    drawCircle(Color.White.copy(alpha = 0.8f), r * 0.2f, Offset(x - r * 0.4f, y - r * 0.38f))
    // Twinkle
    rotate(tick * 20f, Offset(x, y)) {
        drawLine(Color.White, Offset(x - r * 1.3f, y), Offset(x - r * 1.05f, y), r * 0.1f, StrokeCap.Round)
        drawLine(Color.White, Offset(x + r * 1.05f, y), Offset(x + r * 1.3f, y), r * 0.1f, StrokeCap.Round)
    }
}

/** Round yellow snake: a tube through the segment centres, spots, tapered tail and a head with eyes. */
private fun DrawScope.drawSnake(body: List<P>, dir: Dir, c: Float, cols: Int, rows: Int) {
    if (body.isEmpty()) return
    fun ctr(p: P) = Offset(p.x * c + c / 2, p.y * c + c / 2)
    val n = body.size
    fun radius(i: Int) = c * (0.40f - 0.16f * ((i - (n - 4)).coerceAtLeast(0) / 4f))
    // Shadow, outline, fill, in that order; joints skip wrap-around jumps.
    for (pass in 0..2) {
        for (i in n - 1 downTo 0) {
            val p = ctr(body[i]); val r = radius(i)
            val shadow = Offset(c * 0.08f, c * 0.12f)
            if (i > 0) {
                val q = body[i - 1]
                if (abs(q.x - body[i].x) + abs(q.y - body[i].y) == 1) {
                    val a = ctr(q)
                    when (pass) {
                        0 -> drawLine(Color.Black.copy(alpha = 0.22f), p + shadow, a + shadow, r * 2f, StrokeCap.Round)
                        1 -> drawLine(SnakeEdge, p, a, r * 2f + c * 0.12f, StrokeCap.Round)
                        2 -> drawLine(SnakeYellow, p, a, r * 2f, StrokeCap.Round)
                    }
                }
            }
            when (pass) {
                0 -> drawCircle(Color.Black.copy(alpha = 0.22f), r, p + shadow)
                1 -> drawCircle(SnakeEdge, r + c * 0.06f, p)
                2 -> {
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF59A), SnakeYellow, SnakeShade), p - Offset(r * 0.3f, r * 0.3f), r * 1.4f), r, p)
                    if (i > 0 && i % 2 == 0) drawCircle(Color(0xFFC77A10).copy(alpha = 0.7f), r * 0.32f, p)
                }
            }
        }
    }
    // Head
    val h = ctr(body[0]); val hr = c * 0.5f
    drawCircle(SnakeEdge, hr + c * 0.06f, h)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF8B0), SnakeYellow, SnakeShade), h - Offset(hr * 0.3f, hr * 0.4f), hr * 1.4f), hr, h)
    val fx = dir.dx.toFloat(); val fy = dir.dy.toFloat()
    val sx = -fy; val sy = fx // sideways
    for (s in listOf(-1f, 1f)) {
        val e = h + Offset(fx * hr * 0.25f + sx * s * hr * 0.42f, fy * hr * 0.25f + sy * s * hr * 0.42f)
        drawCircle(Color.White, hr * 0.3f, e)
        drawCircle(SnakeEdge, hr * 0.3f, e, style = Stroke(c * 0.04f))
        drawCircle(Color.Black, hr * 0.15f, e + Offset(fx * hr * 0.1f, fy * hr * 0.1f))
        drawCircle(Color.White, hr * 0.05f, e + Offset(-hr * 0.05f, -hr * 0.07f))
    }
    // Tongue flick
    val tip = h + Offset(fx * hr * 1.25f, fy * hr * 1.25f)
    drawLine(Color(0xFFD8222A), h + Offset(fx * hr * 0.85f, fy * hr * 0.85f), tip, c * 0.06f, StrokeCap.Round)
}

private fun DrawScope.drawNight(w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF07122A), Color(0xFF1C3A63), Color(0xFF3D6A8E))))
    val rnd = Random(3)
    repeat(70) { drawCircle(Color.White.copy(alpha = 0.3f + rnd.nextFloat() * 0.7f), 0.8f + rnd.nextFloat() * 1.8f, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.6f)) }
    drawCircle(Color(0xFFFFF7D0), w * 0.07f, Offset(w * 0.82f, h * 0.14f))
    drawCircle(Color(0xFFFFF7D0).copy(alpha = 0.15f), w * 0.12f, Offset(w * 0.82f, h * 0.14f))
    // Rolling bushes along the bottom.
    var x = -w * 0.05f; var i = 0
    while (x < w * 1.1f) {
        val r = w * (0.09f + (i % 3) * 0.025f)
        drawCircle(Color(0xFF1F5A24), r, Offset(x, h * 0.86f + (i % 2) * r * 0.25f))
        drawCircle(Color(0xFF3B8A33), r * 0.55f, Offset(x - r * 0.25f, h * 0.83f + (i % 2) * r * 0.25f))
        x += r * 1.3f; i++
    }
    drawRect(Color(0xFF1A4A1E), Offset(0f, h * 0.9f), Size(w, h * 0.1f))
}

/** Our own cartoon snake: an S-curve with a big head, holding an apple. */
private fun DrawScope.drawMascot() {
    val w = size.width; val hh = size.height
    val path = Path().apply {
        moveTo(w * 0.15f, hh * 0.95f)
        cubicTo(w * 0.6f, hh * 0.98f, w * 0.75f, hh * 0.75f, w * 0.45f, hh * 0.65f)
        cubicTo(w * 0.2f, hh * 0.55f, w * 0.3f, hh * 0.35f, w * 0.5f, hh * 0.32f)
    }
    drawPath(path, SnakeEdge, style = Stroke(w * 0.2f, cap = StrokeCap.Round))
    drawPath(path, SnakeYellow, style = Stroke(w * 0.16f, cap = StrokeCap.Round))
    drawPath(path, Color(0xFFFFF59A).copy(alpha = 0.6f), style = Stroke(w * 0.05f, cap = StrokeCap.Round))
    val h = Offset(w * 0.55f, hh * 0.24f); val r = w * 0.17f
    drawCircle(SnakeEdge, r * 1.08f, h)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF8B0), SnakeYellow, SnakeShade), h - Offset(r * 0.3f, r * 0.4f), r * 1.4f), r, h)
    for (s in listOf(-1f, 1f)) {
        val e = h + Offset(s * r * 0.4f, -r * 0.2f)
        drawCircle(Color.White, r * 0.3f, e); drawCircle(SnakeEdge, r * 0.3f, e, style = Stroke(2f)); drawCircle(Color.Black, r * 0.14f, e + Offset(r * 0.06f, 0f))
    }
    drawArc(SnakeEdge, 20f, 140f, false, h - Offset(r * 0.45f, r * 0.15f), Size(r * 0.9f, r * 0.7f), style = Stroke(3f, cap = StrokeCap.Round))
    drawApple(w * 0.82f, hh * 0.48f, w * 0.1f)
}
