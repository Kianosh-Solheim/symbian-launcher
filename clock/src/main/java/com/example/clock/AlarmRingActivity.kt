package com.example.clock

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen ring screen, shown over the lock screen. Keys: OK / left soft key (*) = Stop,
 * right soft key (#) / Back / volume = Snooze (timers only stop).
 */
class AlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        setContent {
            val ringing by AlarmRingService.state.collectAsState()
            LaunchedEffect(ringing) { if (ringing == null) finish() }
            val r = ringing
            val now = rememberTickingTime(1_000L)
            if (r != null) RingScreen(r, now, onStop = { stop() }, onSnooze = { snooze() })
        }
    }

    private fun stop() { AlarmRingService.send(this, AlarmRingService.ACTION_STOP); finish() }
    private fun snooze() {
        val r = AlarmRingService.state.value
        if (r?.isTimer == true) stop() else { AlarmRingService.send(this, AlarmRingService.ACTION_SNOOZE); finish() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_ENDCALL -> { stop(); return true }
            KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_CAMERA -> { snooze(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Back snoozes")
    override fun onBackPressed() { snooze() }
}

@Composable
private fun RingScreen(r: AlarmRingService.Ringing, now: Long, onStop: () -> Unit, onSnooze: () -> Unit) {
    val pulse = ((now / 1000) % 2 == 0L)
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1D3A), Color(0xFF02060F)))),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text("⏰", fontSize = 44.sp, color = if (pulse) Color.White else Color(0xFF9FC4FF))
        Spacer(Modifier.height(8.dp))
        Text(Formatters.get("HH:mm").format(java.util.Date(now)), color = Color.White, fontSize = 64.sp,
            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(r.label, color = Color(0xFFDDE8FF), fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RingButton("Stop", "OK / *", Color(0xFFB3261E), Modifier.weight(1f), onStop)
            if (!r.isTimer) RingButton("Snooze", "# / Back", Color(0xFF2E5AAC), Modifier.weight(1f), onSnooze)
        }
    }
}

@Composable
private fun RingButton(text: String, hint: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.background(color, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.4f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(hint, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
    }
}
