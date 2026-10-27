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
    private var look by mutableStateOf(GameLook())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("blocks", Context.MODE_PRIVATE)
        game.best = prefs.getInt("best", 0)
        game.startLevel = prefs.getInt("level", 1)
        game.onSave = { prefs.edit().putInt("best", game.best).putInt("level", game.startLevel).apply() }
        game.onBuzz = { ms -> buzz(ms) }
        setContent { BlocksScreen(game, skinFor(look)) }
    }

    override fun onResume() { super.onResume(); look = GameLook.load(this) }

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
        for ((x, y) in p.cells()) { if (y < 0) { piece = null; board = b; over(); return }; b[y][x] = p.kind + 1 }
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

/** Classic piece colours: I, O, T, S, Z, J, L. */
private val PieceColors = listOf(0xFF00E5E5, 0xFFE5E500, 0xFFD000D0, 0xFF00C000, 0xFFE00000, 0xFF2040FF, 0xFFFF8C00).map { Color(it) }

@Composable
private fun BlocksScreen(g: Game, skin: Skin) {
    LaunchedEffect(g.phase, g.level) {
        while (g.phase == Phase.PLAYING) { delay(g.stepMs); g.step() }
    }
    LaunchedEffect(g.flash) {
        if (g.flash.isNotEmpty()) { delay(260); g.clearFlashed() }
    }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var stepped by remember { mutableIntStateOf(0) }
    fun colorOf(kind: Int) = if (skin.colored) PieceColors[kind] else skin.ink
    val menu = g.phase == Phase.MENU || g.phase == Phase.OVER
    GameFrame(skin, "Blocks for Symbian", listOf("Game", if (g.phase == Phase.PAUSED) "Resume" else "Pause", "?")) {
    Column(
        Modifier.fillMaxWidth().weight(1f)
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
                val cell = minOf((maxWidth - 8.dp) / W, (maxHeight - 8.dp) / H)
                FieldBox(skin, cell * W, cell * H) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = size.width / W
                        if (!skin.colored || skin.kind == SkinKind.S60)
                            for (y in 0 until H) for (x in 0 until W) drawRect(skin.dim, Offset(x * c + c * 0.45f, y * c + c * 0.45f), Size(c * 0.1f, c * 0.1f))
                        val gap = if (skin.kind == SkinKind.WIN95) 0f else c * 0.06f
                        fun cellAt(x: Int, y: Int, col: Color) = skinCell(skin, x * c + gap, y * c + gap, c - 2 * gap, col)
                        for (y in 0 until H) {
                            if (y in g.flash) { drawRect(if (skin.colored) Color.White else skin.ink, Offset(0f, y * c + c * 0.3f), Size(c * W, c * 0.4f)); continue }
                            for (x in 0 until W) { val v = g.board[y][x]; if (v != 0) cellAt(x, y, colorOf(v - 1)) }
                        }
                        g.ghost()?.let { gp -> if (gp != g.piece) gp.cells().forEach { (x, y) -> if (y >= 0) {
                            val gg = c * 0.12f
                            drawRect(if (skin.colored) colorOf(gp.kind).copy(alpha = 0.55f) else skin.dim, Offset(x * c + gg, y * c + gg), Size(c - 2 * gg, c - 2 * gg), style = Stroke(width = c * 0.1f))
                        } } }
                        g.piece?.let { p -> p.cells().forEach { (x, y) -> if (y >= 0) cellAt(x, y, colorOf(p.kind)) } }
                    }
                    if (g.phase != Phase.PLAYING) Overlay(g, skin)
                }
            }
            Spacer(Modifier.width(8.dp))
            // Side panel
            Column(Modifier.width(100.dp).fillMaxHeight(), horizontalAlignment = if (skin.kind == SkinKind.WIN95) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val win = skin.kind == SkinKind.WIN95
                Counter(skin, if (win) "Current score" else "Score", g.score)
                Counter(skin, if (win) "Best score" else "Hi", g.best)
                Counter(skin, if (win) "Completed lines" else "Lines", g.lines, 4)
                Counter(skin, "Level", if (menu) g.startLevel else g.level, 2, big = false)
                Spacer(Modifier.height(4.dp))
                if (win) SkinText(skin, "Next block", 13.sp, true, Win95Navy) else SkinText(skin, "NEXT", 12.sp, color = skin.text, center = false)
                val nextMod = if (win) Modifier.win95Sunken().background(Color.Black) else Modifier
                Box(nextMod.size(80.dp, 52.dp)) {
                    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                        if (g.phase == Phase.MENU) return@Canvas
                        val cells = SHAPES[g.next][0]
                        val c = size.height / 2.6f
                        val minX = cells.minOf { it.first }; val maxX = cells.maxOf { it.first }
                        val minY = cells.minOf { it.second }; val maxY = cells.maxOf { it.second }
                        val ox = (size.width - (maxX - minX + 1) * c) / 2 - minX * c
                        val oy = (size.height - (maxY - minY + 1) * c) / 2 - minY * c
                        val gap = if (skin.kind == SkinKind.WIN95) 0f else c * 0.06f
                        cells.forEach { (x, y) -> skinCell(skin, ox + x * c + gap, oy + y * c + gap, c - 2 * gap, colorOf(g.next)) }
                    }
                }
            }
        }
        SoftKeys(skin, when { menu -> "Level -"; g.phase == Phase.PAUSED -> "Resume"; else -> "Pause" }, if (menu) "Level +" else "Drop")
    }
    }
}

@Composable
private fun Overlay(g: Game, skin: Skin) {
    Column(Modifier.fillMaxSize().background(skin.overlay).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val title = when (g.phase) { Phase.MENU -> "BLOCKS"; Phase.PAUSED -> "PAUSED"; else -> "GAME\nOVER" }
        SkinText(skin, title, 30.sp, bold = true)
        Spacer(Modifier.height(10.dp))
        if (g.phase == Phase.OVER) SkinText(skin, "Score ${g.score}" + if (g.score >= g.best && g.score > 0) "\nNEW BEST!" else "", 16.sp)
        if (g.phase != Phase.PAUSED) SkinText(skin, "Start level ${g.startLevel}", 15.sp)
        Spacer(Modifier.height(12.dp))
        SkinText(skin,
            if (g.phase == Phase.PAUSED) "OK to continue\nBack for menu"
            else "OK to start\n◀▶ level\n\n◀▶ move\n▲ / OK rotate\n▼ soft drop\n# hard drop\n* pause", 13.sp)
    }
}
