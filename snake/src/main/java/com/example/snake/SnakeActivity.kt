package com.example.snake

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val LcdBg = Color(0xFF9CB86A)
private val LcdInk = Color(0xFF26331A)
private val LcdDim = Color(0xFF8AA55A)

private enum class Dir(val dx: Int, val dy: Int) { UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0) }
private data class P(val x: Int, val y: Int)
private enum class Phase { MENU, PLAYING, PAUSED, OVER }

/**
 * Snake in the style of the old mobile phone games: black-green LCD, blocky snake, bonus bugs.
 * D-pad or 2/4/6/8 to steer, OK/5 to pause, * and # change speed in the menu. Swipes work too.
 */
class SnakeActivity : ComponentActivity() {
    private val game = Game()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("snake", Context.MODE_PRIVATE)
        game.best = prefs.getInt("best", 0)
        game.level = prefs.getInt("level", 5)
        game.walls = prefs.getBoolean("walls", false)
        game.onSave = { prefs.edit().putInt("best", game.best).putInt("level", game.level).putBoolean("walls", game.walls).apply() }
        game.onBuzz = { ms -> buzz(ms) }
        setContent { SnakeScreen(game) }
    }

    override fun onPause() { super.onPause(); if (game.phase == Phase.PLAYING) game.phase = Phase.PAUSED }

    @Suppress("DEPRECATION")
    private fun buzz(ms: Long) {
        try { (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) { }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_2 -> game.steer(Dir.UP)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_8 -> game.steer(Dir.DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_4 -> game.steer(Dir.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_6 -> game.steer(Dir.RIGHT)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_ENTER -> game.ok()
            KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU -> game.left()
            KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_SOFT_RIGHT -> game.right()
            KeyEvent.KEYCODE_BACK -> { if (!game.back()) finish() }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}

private class Game {
    var cols = 20; var rows = 24
    var phase by mutableStateOf(Phase.MENU)
    var snake by mutableStateOf(listOf<P>())
    var food by mutableStateOf(P(0, 0))
    var bonus by mutableStateOf<P?>(null)
    var bonusLeft by mutableIntStateOf(0)
    var score by mutableIntStateOf(0)
    var best by mutableIntStateOf(0)
    var level by mutableIntStateOf(5)
    var walls by mutableStateOf(false)
    var tick by mutableIntStateOf(0)
    private var dir = Dir.RIGHT
    private val queue = ArrayDeque<Dir>()
    private var eaten = 0
    var onSave: () -> Unit = {}
    var onBuzz: (Long) -> Unit = {}

    val stepMs get() = (330 - level * 30).toLong().coerceAtLeast(45)

    fun newGame() {
        val cy = rows / 2
        snake = (0 until 4).map { P(5 - it, cy) }
        dir = Dir.RIGHT; queue.clear(); score = 0; eaten = 0; bonus = null
        food = freeCell(); phase = Phase.PLAYING
    }

    private fun freeCell(): P {
        val taken = snake.toHashSet() + listOfNotNull(bonus, food)
        repeat(500) { val p = P(Random.nextInt(cols), Random.nextInt(rows)); if (p !in taken) return p }
        return P(0, 0)
    }

    fun steer(d: Dir) {
        if (phase == Phase.MENU) { if (d == Dir.UP) level = (level % 9) + 1 else if (d == Dir.DOWN) level = if (level == 1) 9 else level - 1 else walls = !walls; onSave(); return }
        if (phase != Phase.PLAYING) return
        val last = queue.lastOrNull() ?: dir
        if (d == last || (d.dx == -last.dx && d.dy == -last.dy)) return
        if (queue.size < 3) queue.addLast(d)
    }

    fun ok() = when (phase) {
        Phase.MENU, Phase.OVER -> newGame()
        Phase.PLAYING -> phase = Phase.PAUSED
        Phase.PAUSED -> phase = Phase.PLAYING
    }
    fun left() { if (phase == Phase.MENU || phase == Phase.OVER) { level = if (level == 1) 9 else level - 1; onSave() } else ok() }
    fun right() { if (phase == Phase.MENU || phase == Phase.OVER) { level = (level % 9) + 1; onSave() } else if (!back()) Unit }
    /** Back: playing → pause, paused/over → menu; false = leave the app. */
    fun back(): Boolean = when (phase) {
        Phase.PLAYING -> { phase = Phase.PAUSED; true }
        Phase.PAUSED, Phase.OVER -> { phase = Phase.MENU; true }
        Phase.MENU -> false
    }

    fun step() {
        if (phase != Phase.PLAYING) return
        queue.removeFirstOrNull()?.let { dir = it }
        val head = snake.first()
        var nx = head.x + dir.dx; var ny = head.y + dir.dy
        if (walls) {
            if (nx !in 0 until cols || ny !in 0 until rows) return over()
        } else { nx = (nx + cols) % cols; ny = (ny + rows) % rows }
        val nh = P(nx, ny)
        val grow = nh == food
        val body = if (grow) snake else snake.dropLast(1)
        if (nh in body) return over()
        snake = listOf(nh) + body
        if (grow) {
            score += level; eaten++; food = freeCell(); onBuzz(15)
            if (eaten % 5 == 0) { bonus = freeCell(); bonusLeft = 25 }
        }
        bonus?.let { b ->
            if (nh == b) { score += bonusLeft * level / 2 + 5 * level; bonus = null; onBuzz(40); snake = snake + snake.last() }
            else if (--bonusLeft <= 0) bonus = null
        }
        tick++
    }

    private fun over() {
        phase = Phase.OVER; onBuzz(250)
        if (score > best) { best = score }
        onSave()
    }
}

@Composable
private fun SnakeScreen(g: Game) {
    LaunchedEffect(g.phase, g.level) {
        while (g.phase == Phase.PLAYING) { delay(g.stepMs); g.step() }
    }
    var drag by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier.fillMaxSize().background(LcdBg).systemBarsPadding()
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { drag = Offset.Zero }, onDragEnd = {
                    if (drag.getDistance() > 30f) g.steer(if (kotlin.math.abs(drag.x) > kotlin.math.abs(drag.y)) (if (drag.x > 0) Dir.RIGHT else Dir.LEFT) else (if (drag.y > 0) Dir.DOWN else Dir.UP))
                }) { _, d -> drag += d }
            }
            .pointerInput(Unit) { detectTapGestures { g.ok() } }
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(g.score.toString().padStart(4, '0'), color = LcdInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            if (g.bonus != null) Text("✱ ${g.bonusLeft}", color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 18.sp, modifier = Modifier.padding(end = 12.dp))
            Text("HI ${g.best}", color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            val cell = minOf(maxWidth / 20, maxHeight / 24)
            g.cols = 20
            g.rows = (maxHeight / cell).toInt().coerceIn(12, 40)
            Box(Modifier.size(cell * g.cols + 6.dp, cell * g.rows + 6.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = (size.width - 6.dp.toPx()) / g.cols
                    val o = 3.dp.toPx()
                    drawRect(LcdInk, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()), topLeft = Offset(1f, 1f), size = Size(size.width - 2f, size.height - 2f))
                    val gap = c * 0.12f
                    fun block(p: P, color: Color = LcdInk) = drawRect(color, Offset(o + p.x * c + gap, o + p.y * c + gap), Size(c - 2 * gap, c - 2 * gap))
                    g.snake.forEachIndexed { i, p -> block(p); if (i == 0) drawRect(LcdBg, Offset(o + p.x * c + c * 0.42f, o + p.y * c + c * 0.42f), Size(c * 0.16f, c * 0.16f)) }
                    // Food: a small diamond of four dots.
                    val f = g.food; val fx = o + f.x * c; val fy = o + f.y * c; val q = c / 3
                    drawRect(LcdInk, Offset(fx + q, fy), Size(q, q)); drawRect(LcdInk, Offset(fx, fy + q), Size(q, q))
                    drawRect(LcdInk, Offset(fx + 2 * q, fy + q), Size(q, q)); drawRect(LcdInk, Offset(fx + q, fy + 2 * q), Size(q, q))
                    g.bonus?.let { b -> if (g.tick % 2 == 0 || g.bonusLeft > 8) { block(b); drawRect(LcdBg, Offset(o + b.x * c + c * 0.35f, o + b.y * c + c * 0.35f), Size(c * 0.3f, c * 0.3f)) } }
                }
                if (g.phase != Phase.PLAYING) {
                    Column(Modifier.fillMaxSize().background(LcdBg.copy(alpha = 0.88f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        val title = when (g.phase) { Phase.MENU -> "SNAKE"; Phase.PAUSED -> "PAUSED"; else -> "GAME OVER" }
                        Text(title, color = LcdInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                        Spacer(Modifier.height(10.dp))
                        if (g.phase == Phase.OVER) Text("Score ${g.score}" + if (g.score >= g.best && g.score > 0) "  NEW BEST!" else "", color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                        if (g.phase != Phase.PAUSED) {
                            Text("Level ${g.level}  ${"▮".repeat(g.level)}${"▯".repeat(9 - g.level)}", color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                            Text("Maze: ${if (g.walls) "walls" else "open"}", color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            when (g.phase) {
                                Phase.PAUSED -> "OK to continue\nBack for menu"
                                else -> "OK to start\n▲▼ or * # speed\n◀▶ walls on/off\nSteer: D-pad or 2 4 6 8"
                            },
                            color = LcdInk, fontFamily = FontFamily.Monospace, fontSize = 14.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(LcdDim).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(if (g.phase == Phase.PLAYING) "Pause" else if (g.phase == Phase.PAUSED) "Resume" else "Slower", color = LcdInk, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            Text(if (g.phase == Phase.MENU || g.phase == Phase.OVER) "Faster" else "Back", color = LcdInk, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
