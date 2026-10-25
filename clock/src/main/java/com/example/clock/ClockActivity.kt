package com.example.clock

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClockViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(ClockUiState())
    val state: StateFlow<ClockUiState> = _state

    init {
        AlarmStore.load(app)
        _state.update { it.copy(worldClocks = WorldClockStore.load(app)) }
        viewModelScope.launch { AlarmStore.alarms.collect { list -> _state.update { it.copy(alarms = list) } } }
    }

    fun applyLook(l: ClockLook) = _state.update {
        it.copy(is24HourFormat = l.is24, dateFormat = l.dateFormat, showWeekday = l.showWeekday, weekdayFormat = l.weekdayFormat, currentFont = l.font)
    }
    fun setClockTab(tab: Int) = _state.update { it.copy(clockTab = tab) }
    fun cycleClockTab() = _state.update { it.copy(clockTab = (it.clockTab + 1) % 4) }
    fun addAlarm(ctx: Context, a: AlarmItem) = AlarmStore.upsert(ctx, a.copy(enabled = true))
    fun updateAlarm(ctx: Context, a: AlarmItem) = AlarmStore.upsert(ctx, a)
    fun deleteAlarm(ctx: Context, id: String) = AlarmStore.delete(ctx, id)
    fun addWorldClock(w: WorldClockItem) { val l = _state.value.worldClocks + w; WorldClockStore.save(getApplication(), l); _state.update { it.copy(worldClocks = l) } }
    fun deleteWorldClock(id: String) { val l = _state.value.worldClocks.filterNot { it.id == id }; WorldClockStore.save(getApplication(), l); _state.update { it.copy(worldClocks = l) } }
    fun importWorldClocks(json: String) {
        val have = _state.value.worldClocks.map { it.timeZoneId }.toSet()
        val add = WorldClockStore.parse(json).filter { it.timeZoneId !in have }
        if (add.isNotEmpty()) { val l = _state.value.worldClocks + add; WorldClockStore.save(getApplication(), l); _state.update { it.copy(worldClocks = l) } }
    }
}

private enum class Page { CLOCK, ALARM_EDIT, ALARM_OPTIONS, WORLD_SEARCH, WORLD_OPTIONS }

/** Symbian Clock: clock, world clocks, alarms, stopwatch and timer, D-pad and soft-key driven. */
class ClockActivity : ComponentActivity() {
    companion object { const val ACTION_OPEN_CLOCK = "no.kianosh.symbianclock.OPEN_ALARMS" }

    private val vm: ClockViewModel by viewModels()
    private val look = mutableStateOf(ClockLook())
    private val softLeft = mutableStateOf<(() -> Unit)?>(null)
    private val softRight = mutableStateOf<(() -> Unit)?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlarmStore.load(this)
        AlarmRingService.ensureChannels(this)
        handle(intent)
        AlarmStore.scheduleAll(this)
        setContent { ClockRoot() }
    }

    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: android.content.Intent?) {
        val l = ClockLook.fromIntent(i, ClockLook.load(this))
        ClockLook.save(this, l)
        look.value = l
        vm.applyLook(l)
        i?.getStringExtra("legacy_alarms")?.let { AlarmStore.importFrom(this, it) }
        i?.getStringExtra("legacy_world_clocks")?.let { vm.importWorldClocks(it) }
        if (i?.action == ACTION_OPEN_CLOCK || i?.getBooleanExtra("open_alarms", false) == true) vm.setClockTab(1)
    }

    // * and # (or real soft keys) act as the two soft keys, like the launcher.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU -> softLeft.value?.let { it(); return true }
                KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_SOFT_RIGHT -> softRight.value?.let { it(); return true }
            }
        } else if (event.action == KeyEvent.ACTION_UP && event.keyCode in setOf(KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND,
                KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_MENU)) return true
        return super.dispatchKeyEvent(event)
    }

    @Composable
    private fun ClockRoot() {
        val l by look
        val state by vm.state.collectAsState()
        val haptic = LocalHapticFeedback.current
        var page by remember { mutableStateOf(Page.CLOCK) }
        var selectedAlarm by remember { mutableStateOf<AlarmItem?>(null) }
        var selectedWorld by remember { mutableStateOf<WorldClockItem?>(null) }
        val back: () -> Unit = { if (page == Page.CLOCK) finish() else page = Page.CLOCK }
        BackHandler { back() }

        val scheme = if (l.win95) lightColorScheme(background = Color(0xFFC0C0C0), surface = Color(0xFFC0C0C0), onBackground = Color.Black,
                onSurface = Color.Black, primary = Color(0xFF000080), secondary = Color(0xFF000080))
            else darkColorScheme(background = Color(l.background), surface = Color(l.surface), onBackground = Color(l.text),
                onSurface = Color(l.text), primary = Color(l.accent), secondary = Color(l.highlight))
        val tabName = listOf("Clock", "Alarm", "Stopwatch", "Timer")[state.clockTab]
        val (leftText, rightText) = when (page) {
            Page.CLOCK -> "Tab: $tabName" to "Exit"
            else -> "" to "Back"
        }
        SideEffect {
            softLeft.value = if (page == Page.CLOCK) ({ vm.cycleClockTab() }) else null
            softRight.value = back
        }

        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalWin95 provides l.win95, LocalLcd provides l.lcd) {
                Column(Modifier.fillMaxSize().background(if (l.win95) Color(0xFF008080) else Color(l.background)).systemBarsPadding()) {
                    Box(Modifier.weight(1f).fillMaxWidth().then(if (l.win95) Modifier.padding(3.dp).win95Bevel().padding(3.dp) else Modifier)) {
                        Column(Modifier.fillMaxSize()) {
                            if (l.win95) Row(Modifier.fillMaxWidth().background(Color(0xFF000080)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                                Text("Clock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (page) {
                                    Page.CLOCK -> ClockAppScreen(state, vm, haptic, onBack = back,
                                        onAddAlarm = { selectedAlarm = null; page = Page.ALARM_EDIT },
                                        onAlarmOptions = { selectedAlarm = it; page = Page.ALARM_OPTIONS },
                                        onAddWorldClock = { selectedWorld = null; page = Page.WORLD_SEARCH },
                                        onWorldClockOptions = { selectedWorld = it; page = Page.WORLD_OPTIONS })
                                    Page.ALARM_EDIT -> AlarmEditScreen(state, selectedAlarm, haptic) {
                                        if (selectedAlarm == null) vm.addAlarm(this@ClockActivity, it) else vm.updateAlarm(this@ClockActivity, it)
                                        page = Page.CLOCK
                                    }
                                    Page.ALARM_OPTIONS -> selectedAlarm?.let { a ->
                                        AlarmOptionsScreen(state, a, haptic, onEdit = { page = Page.ALARM_EDIT },
                                            onDelete = { vm.deleteAlarm(this@ClockActivity, a.id); page = Page.CLOCK })
                                    } ?: run { page = Page.CLOCK }
                                    Page.WORLD_SEARCH -> WorldClockSearchScreen(state, haptic) {
                                        selectedWorld?.let { w -> vm.deleteWorldClock(w.id) }
                                        vm.addWorldClock(it); selectedWorld = null; page = Page.CLOCK
                                    }
                                    Page.WORLD_OPTIONS -> selectedWorld?.let { w ->
                                        WorldClockOptionsScreen(state, haptic, onEdit = { page = Page.WORLD_SEARCH },
                                            onDelete = { vm.deleteWorldClock(w.id); selectedWorld = null; page = Page.CLOCK })
                                    } ?: run { page = Page.CLOCK }
                                }
                            }
                        }
                    }
                    SoftKeyBar(leftText, rightText, l.win95, { softLeft.value?.invoke() }, back)
                }
            }
        }
    }
}

@Composable
private fun SoftKeyBar(left: String, right: String, win95: Boolean, onLeft: () -> Unit, onRight: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (win95) Modifier.win95Bevel() else Modifier.background(MaterialTheme.colorScheme.surface))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (win95) Color.Black else MaterialTheme.colorScheme.onSurface
        Text(left, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f).clickable(enabled = left.isNotEmpty(), onClick = onLeft))
        Text(right, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onRight))
    }
}
