package com.example.blocks

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

private val LcdBg = Color(0xFF9CB86A)
private val LcdInk = Color(0xFF26331A)
private val LcdDim = Color(0xFF8AA55A)
private val LcdGhost = Color(0xFF7E9A52)

private const val W = 10
private const val H = 20

private enum class Phase { MENU, PLAYING, PAUSED, OVER }

/** The seven pieces, each as its four rotations of (x, y) cells inside a 4×4 box. */
private val SHAPES: List<List<List<Pair<Int, Int>>>> = listOf(
    "....|####|....|....", // I
    "##..|##..|....|....", // O
    ".#..|###.|....|....", // T
    ".##.|##..|....|....", // S
    "##..|.##.|....|....", // Z
    "#...|###.|....|....", // J
    "..#.|###.|....|...."  // L
).mapIndexed { idx, s ->
    val rows = s.split('|')
    val base = buildList { for (y in 0 until 4) for (x in 0 until 4) if (rows[y][x] == '#') add(x to y) }
    val n = if (idx == 0) 4 else if (idx == 1) 2 else 3   // rotation box size
    val rots = mutableListOf(base)
    repeat(3) { val prev = rots.last(); rots.add(prev.map { (x, y) -> (n - 1 - y) to x }) }
    if (idx == 1) List(4) { base } else rots
}

private data class Piece(val kind: Int, val rot: Int, val x: Int, val y: Int) {
    fun cells() = SHAPES[kind][rot].map { (cx, cy) -> (x + cx) to (y + cy) }
}

/**
 * Falling-blocks puzzle in the old-phone LCD style.
 * D-pad ◀▶ / 4 6 move, ▲ / 5 / OK rotate, ▼ / 8 soft drop, # or right soft key hard drop,
 * * or left soft key pause. Touch: tap rotates, swipe sideways moves, swipe down drops.
 */
class BlocksActivity : ComponentActivity() {
    private val game = Game()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("blocks", Context.MODE_PRIVATE)
        game.best = prefs.getInt("best", 0)
        game.startLevel = prefs.getInt("level", 1)
        game.onSave = { prefs.edit().putInt("best", game.best).putInt("level", game.startLevel).apply() }
        game.onBuzz = { ms -> buzz(ms) }
        setContent { BlocksScreen(game) }
    }

    override fun onPause() { super.onPause(); if (game.phase == Phase.PLAYING) game.phase = Phase.PAUSED }

    @Suppress("DEPRECATION")
    private fun buzz(ms: Long) {
        try { (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) { }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_4 -> game.move(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_6 -> game.move(1)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_2 -> game.up()
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_8 -> game.down()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_ENTER -> game.ok()
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_7 -> game.rotate(-1)
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_9 -> game.rotate(1)
            KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_SOFT_RIGHT -> game.hardDrop()
            KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU -> game.pause()
            KeyEvent.KEYCODE_BACK -> { if (!game.back()) finish() }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}

private class Game {
    var phase by mutableStateOf(Phase.MENU)
    var board by mutableStateOf(Array(H) { IntArray(W) })   // 0 empty, 1 filled
    var piece by mutableStateOf<Piece?>(null)
    var next by mutableIntStateOf(0)
    var score by mutableIntStateOf(0)
    var best by mutableIntStateOf(0)
    var lines by mutableIntStateOf(0)
    var level by mutableIntStateOf(1)
    var startLevel by mutableIntStateOf(1)
    var flash by mutableStateOf<Set<Int>>(emptySet())
    var onSave: () -> Unit = {}
    var onBuzz: (Long) -> Unit = {}
    private val bag = ArrayDeque<Int>()

    /** Gravity: about 0.8 s per row at level 1, down to ~60 ms. */
    val stepMs get() = (850 * Math.pow(0.8, (level - 1).toDouble())).toLong().coerceAtLeast(60)

    private fun draw(): Int {
        if (bag.isEmpty()) bag.addAll((0 until 7).shuffled())
        return bag.removeFirst()
    }

    fun newGame() {
        board = Array(H) { IntArray(W) }
        score = 0; lines = 0; level = startLevel; bag.clear(); flash = emptySet()
        next = draw(); spawn(); phase = Phase.PLAYING
    }

    private fun spawn() {
        val k = next; next = draw()
        val p = Piece(k, 0, if (k == 1) 4 else 3, if (k == 0) -1 else 0)
        if (!fits(p)) { piece = p; over(); return }
        piece = p
    }

    private fun fits(p: Piece) = p.cells().all { (x, y) -> x in 0 until W && y < H && (y < 0 || board[y][x] == 0) }

    fun move(dx: Int) {
        if (phase == Phase.MENU || phase == Phase.OVER) { changeLevel(dx); return }
        if (phase != Phase.PLAYING) return
        val p = piece ?: return
        val m = p.copy(x = p.x + dx)
        if (fits(m)) piece = m
    }

    fun rotate(dir: Int) {
        if (phase != Phase.PLAYING) return
        val p = piece ?: return
        val r = p.copy(rot = (p.rot + dir + 4) % 4)
        // Simple wall kicks: try in place, then nudges sideways and up.
        for ((kx, ky) in listOf(0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1)) {
            val k = r.copy(x = r.x + kx, y = r.y + ky)
            if (fits(k)) { piece = k; return }
        }
    }

    fun up() { if (phase == Phase.MENU || phase == Phase.OVER) changeLevel(1) else rotate(1) }
    fun down() {
        if (phase == Phase.MENU || phase == Phase.OVER) { changeLevel(-1); return }
        if (phase != Phase.PLAYING) return
        if (fall()) score += 1
    }

    fun ok() = when (phase) {
        Phase.MENU, Phase.OVER -> newGame()
        Phase.PLAYING -> rotate(1)
        Phase.PAUSED -> phase = Phase.PLAYING
    }

    fun pause() { when (phase) { Phase.PLAYING -> phase = Phase.PAUSED; Phase.PAUSED -> phase = Phase.PLAYING; else -> changeLevel(-1) } }

    fun hardDrop() {
        if (phase == Phase.MENU || phase == Phase.OVER) { changeLevel(1); return }
        if (phase != Phase.PLAYING) return
        var p = piece ?: return
        var n = 0
        while (fits(p.copy(y = p.y + 1))) { p = p.copy(y = p.y + 1); n++ }
        piece = p; score += 2 * n; lock()
    }

    private fun changeLevel(d: Int) { startLevel = ((startLevel - 1 + d + 15) % 15) + 1; onSave() }

    fun back(): Boolean = when (phase) {
        Phase.PLAYING -> { phase = Phase.PAUSED; true }
        Phase.PAUSED, Phase.OVER -> { phase = Phase.MENU; true }
        Phase.MENU -> false
    }

    fun ghost(): Piece? {
        var p = piece ?: return null
        while (fits(p.copy(y = p.y + 1))) p = p.copy(y = p.y + 1)
        return p
    }

    /** One row down; locks the piece when it lands. Returns true if it moved. */
    private fun fall(): Boolean {
        val p = piece ?: return false
        val d = p.copy(y = p.y + 1)
        return if (fits(d)) { piece = d; true } else { lock(); false }
    }

    fun step() { if (phase == Phase.PLAYING && flash.isEmpty()) fall() }

    private fun lock() {
        val p = piece ?: return
        val b = Array(H) { board[it].clone() }
        for ((x, y) in p.cells()) { if (y < 0) { piece = null; board = b; over(); return }; b[y][x] = 1 }
        board = b; piece = null
        val full = (0 until H).filter { y -> b[y].all { it != 0 } }.toSet()
        if (full.isNotEmpty()) { flash = full; onBuzz(if (full.size == 4) 120 else 40) } else { onBuzz(8); spawn() }
    }

    /** Called after the line-clear blink. */
    fun clearFlashed() {
        val full = flash; if (full.isEmpty()) return
        val keep = (0 until H).filter { it !in full }.map { board[it] }
        board = (List(full.size) { IntArray(W) } + keep).toTypedArray()
        val n = full.size
        score += intArrayOf(0, 100, 300, 500, 800)[n] * level
        lines += n
        level = maxOf(level, startLevel + lines / 10)
        flash = emptySet()
        spawn()
    }

    private fun over() {
        phase = Phase.OVER; onBuzz(250)
        if (score > best) best = score
        onSave()
    }
}

@Composable
private fun BlocksScreen(g: Game) {
    LaunchedEffect(g.phase, g.level) {
        while (g.phase == Phase.PLAYING) { delay(g.stepMs); g.step() }
    }
    LaunchedEffect(g.flash) {
        if (g.flash.isNotEmpty()) { delay(260); g.clearFlashed() }
    }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var stepped by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(LcdBg).systemBarsPadding()
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { drag = Offset.Zero; stepped = 0 }, onDragEnd = {
                    if (drag.y > 120f && abs(drag.y) > abs(drag.x) * 1.5f) g.hardDrop()
                }) { _, d ->
                    drag += d
                    // Sideways swipes move one column per ~40 px so a long swipe slides the piece.
                    val cols = (drag.x / 40f).toInt()
                    while (stepped < cols) { g.move(1); stepped++ }
                    while (stepped > cols) { g.move(-1); stepped-- }
                }
            }
            .pointerInput(Unit) { detectTapGestures { g.ok() } }
    ) {
        Row(Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
            // Playfield
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val cell = minOf((maxWidth - 6.dp) / W, (maxHeight - 6.dp) / H)
                Box(Modifier.size(cell * W + 6.dp, cell * H + 6.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = (size.width - 6.dp.toPx()) / W
                        val o = 3.dp.toPx()
                        drawRect(LcdInk, style = Stroke(width = 2.dp.toPx()), topLeft = Offset(1f, 1f), size = Size(size.width - 2f, size.height - 2f))
                        // Faint dot grid like an unlit LCD.
                        for (y in 0 until H) for (x in 0 until W) drawRect(LcdDim, Offset(o + x * c + c * 0.45f, o + y * c + c * 0.45f), Size(c * 0.1f, c * 0.1f))
                        for (y in 0 until H) {
                            if (y in g.flash) { drawRect(LcdInk, Offset(o, o + y * c + c * 0.4f), Size(c * W, c * 0.2f)); continue }
                            for (x in 0 until W) if (g.board[y][x] != 0) block(o, c, x, y, LcdInk)
                        }
                        g.ghost()?.let { gp -> if (gp != g.piece) gp.cells().forEach { (x, y) -> if (y >= 0) outline(o, c, x, y) } }
                        g.piece?.cells()?.forEach { (x, y) -> if (y >= 0) block(o, c, x, y, LcdInk) }
                    }
                    if (g.phase != Phase.PLAYING) Overlay(g)
                }
            }
            Spacer(Modifier.width(8.dp))
            // Side panel
            Column(Modifier.width(92.dp)) {
                Label("SCORE"); Value(g.score.toString())
                Spacer(Modifier.height(8.dp))
                Label("HI"); Value(g.best.toString())
                Spacer(Modifier.height(8.dp))
                Label("LINES"); Value(g.lines.toString())
                Spacer(Modifier.height(8.dp))
                Label("LEVEL"); Value((if (g.phase == Phase.MENU || g.phase == Phase.OVER) g.startLevel else g.level).toString())
                Spacer(Modifier.height(12.dp))
                Label("NEXT")
                Canvas(Modifier.size(80.dp, 48.dp)) {
                    if (g.phase == Phase.MENU) return@Canvas
                    val cells = SHAPES[g.next][0]
                    val c = size.height / 3f
                    val minX = cells.minOf { it.first }; val maxX = cells.maxOf { it.first }
                    val minY = cells.minOf { it.second }; val maxY = cells.maxOf { it.second }
                    val ox = (size.width - (maxX - minX + 1) * c) / 2 - minX * c
                    val oy = (size.height - (maxY - minY + 1) * c) / 2 - minY * c
                    cells.forEach { (x, y) -> block(0f, c, x, y, LcdInk, ox, oy) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(LcdDim).padding(horizontal = 12.dp, vertical = 6.dp)) {
            val menu = g.phase == Phase.MENU || g.phase == Phase.OVER
            Text(when { menu -> "Level -"; g.phase == Phase.PAUSED -> "Resume"; else -> "Pause" }, color = LcdInk, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text(if (menu) "Level +" else "Drop", color = LcdInk, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun DrawScope.block(o: Float, c: Float, x: Int, y: Int, color: Color, ox: Float = o, oy: Float = o) {
    val gap = c * 0.08f
    val l = ox + x * c + gap; val t = oy + y * c + gap; val s = c - 2 * gap
    drawRect(color, Offset(l, t), Size(s, s))
    // Hollow centre gives the classic bevelled LCD brick.
    drawRect(LcdBg, Offset(l + s * 0.22f, t + s * 0.22f), Size(s * 0.56f, s * 0.56f))
    drawRect(color, Offset(l + s * 0.36f, t + s * 0.36f), Size(s * 0.28f, s * 0.28f))
}

private fun DrawScope.outline(o: Float, c: Float, x: Int, y: Int) {
    val gap = c * 0.12f
    drawRect(LcdGhost, Offset(o + x * c + gap, o + y * c + gap), Size(c - 2 * gap, c - 2 * gap), style = Stroke(width = c * 0.1f))
}

@Composable private fun Label(t: String) = Text(t, color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
@Composable private fun Value(t: String) = Text(t, color = LcdInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)

@Composable
private fun Overlay(g: Game) {
    Column(Modifier.fillMaxSize().background(LcdBg.copy(alpha = 0.9f)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val title = when (g.phase) { Phase.MENU -> "BLOCKS"; Phase.PAUSED -> "PAUSED"; else -> "GAME\nOVER" }
        Text(title, color = LcdInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 30.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        if (g.phase == Phase.OVER) Text("Score ${g.score}" + if (g.score >= g.best && g.score > 0) "\nNEW BEST!" else "", color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 16.sp, textAlign = TextAlign.Center)
        if (g.phase != Phase.PAUSED) Text("Start level ${g.startLevel}", color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (g.phase == Phase.PAUSED) "OK to continue\nBack for menu"
            else "OK to start\n◀▶ level\n\n◀▶ move\n▲ / OK rotate\n▼ soft drop\n# hard drop\n* pause",
            color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 13.sp, textAlign = TextAlign.Center
        )
    }
}
