package com.example.music

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent as AKey
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

sealed class Screen(val key: String) {
    data object Home : Screen("home")
    data object Artists : Screen("artists")
    data class ArtistAlbums(val artist: Artist) : Screen("artist:${artist.name}")
    data object Albums : Screen("albums")
    data class AlbumTracks(val album: Album) : Screen("album:${album.id}")
    data object Songs : Screen("songs")
    data object Books : Screen("books")
    data class BookDetail(val book: Book) : Screen("book:${book.id}")
    data object NowPlaying : Screen("now")
    data object Bookmarks : Screen("marks")
    data object Other : Screen("other")
    data object Folders : Screen("folders")
}

class MusicActivity : ComponentActivity() {
    private var look by mutableStateOf(GameLook())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var connected by mutableStateOf(false)
    /** Hardware keys are routed to the current screen first. */
    var keyHandler: ((Int) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MusicApp(skinFor(look), connected) }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { f ->
            f.addListener({ connected = Engine.player != null }, ContextCompat.getMainExecutor(this))
        }
    }

    override fun onResume() { super.onResume(); look = GameLook.load(this) }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        Engine.saveBook()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: AKey): Boolean {
        if (event.action == AKey.ACTION_DOWN && keyHandler?.invoke(event.keyCode) == true) return true
        return super.dispatchKeyEvent(event)
    }
}

// ---------------------------------------------------------------- palette

class Pal(val skin: Skin) {
    val win = skin.kind == SkinKind.WIN95
    val lcd = skin.kind == SkinKind.LCD
    val listBg: Color = when (skin.kind) { SkinKind.WIN95 -> Color.White; SkinKind.S60 -> Color.White.copy(alpha = 0.5f); else -> Color.Transparent }
    val text: Color = if (win) Color.Black else skin.text
    val sub: Color = text.copy(alpha = 0.65f)
    val hlBg: Brush = when (skin.kind) {
        SkinKind.LCD -> Brush.verticalGradient(listOf(skin.ink, skin.ink))
        SkinKind.WIN95 -> Brush.verticalGradient(listOf(Win95Navy, Win95Navy))
        SkinKind.S60 -> Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(skin.accent, Color.White, 0.3f), skin.accent))
        SkinKind.SYMBIAN -> Brush.verticalGradient(listOf(skin.accent, skin.accent))
    }
    val hlText: Color = if (lcd) LcdBgC else Color.White
    val accent: Color = when (skin.kind) { SkinKind.LCD -> skin.ink; SkinKind.WIN95 -> Win95Navy; else -> skin.accent }
    val track: Color = text.copy(alpha = 0.18f)
    /** Covers in the LCD theme become green duotone so they sit on the screen. */
    val coverFilter: ColorFilter? = if (!lcd) null else {
        val ink = skin.ink; val bg = LcdBgC
        fun row(i: Float, b: Float): FloatArray { val k = (b - i); return floatArrayOf(k * 0.3f, k * 0.59f, k * 0.11f, 0f, i * 255f) }
        ColorFilter.colorMatrix(ColorMatrix(row(ink.red, bg.red) + row(ink.green, bg.green) + row(ink.blue, bg.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
    }
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
fun fmtLong(ms: Long): String { val m = ms / 60000; return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }

// ---------------------------------------------------------------- app

data class Opt(val label: String, val action: () -> Unit)

@Composable
fun MusicApp(skin: Skin, connected: Boolean) {
    val ctx = LocalContext.current
    val act = ctx as MusicActivity
    val pal = remember(skin) { Pal(skin) }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    val askPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it[perm] == true }
    LaunchedEffect(Unit) {
        if (!granted) askPerm.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(perm, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(perm))
    }
    var lib by remember { mutableStateOf(Library()) }
    var scanTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(granted, scanTick) { if (granted) lib = withContext(Dispatchers.IO) { LibraryScanner.scan(ctx) } }

    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val screen = stack.last()
    val focusMemory = remember { mutableStateMapOf<String, Int>() }
    fun go(s: Screen) { stack.add(s) }
    fun back(): Boolean { if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }; return false }

    var options by remember { mutableStateOf<List<Opt>?>(null) }
    var markDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = options != null || stack.size > 1) { if (options != null) options = null else back() }

    // Options for the current screen (left soft key / Menu).
    val q = Engine.queue
    fun sleepOpts() = listOf(Opt("Off") { Engine.setSleep(0) }) + listOf(5, 10, 15, 30, 45, 60, 90).map { m -> Opt("$m minutes") { Engine.setSleep(m) } } +
        listOf(Opt(if (q is Queue.Audiobook) "End of chapter" else "End of track") { Engine.setSleep(-1) })
    fun speedOpts() = listOf(0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f).map { s -> Opt("${"%.2f".format(s).trimEnd('0').trimEnd('.')}×" + if (s == Engine.speed) "  ✓" else "") { Engine.setSpeedTo(s) } }
    val screenOptions: List<Opt> = when (screen) {
        Screen.NowPlaying -> if (q is Queue.Audiobook) listOf(
            Opt("Chapters") { stack.add(Screen.BookDetail(q.book)) },
            Opt("Add bookmark") { markDialog = true },
            Opt("Bookmarks") { go(Screen.Bookmarks) },
            Opt("Speed…") { options = speedOpts() },
            Opt("Sleep timer…") { options = sleepOpts() },
        ) else listOf(
            Opt(if (Engine.shuffle) "Shuffle: on" else "Shuffle: off") { Engine.toggleShuffle() },
            Opt("Repeat: " + when (Engine.repeat) { Player.REPEAT_MODE_ALL -> "all"; Player.REPEAT_MODE_ONE -> "one"; else -> "off" }) { Engine.cycleRepeat() },
            Opt("Sleep timer…") { options = sleepOpts() },
            Opt("Go to album") { Engine.currentTrack()?.let { t -> lib.albums.firstOrNull { it.id == t.albumId }?.let { go(Screen.AlbumTracks(it)) } } },
        )
        is Screen.AlbumTracks -> listOf(Opt("Play album") { Engine.playMusic(screen.album.tracks, 0, screen.album.title); go(Screen.NowPlaying) },
            Opt("Shuffle album") { Engine.playMusic(screen.album.tracks, 0, screen.album.title, true); go(Screen.NowPlaying) })
        is Screen.ArtistAlbums -> listOf(Opt("Play all") { val t = screen.artist.albums.flatMap { it.tracks }; Engine.playMusic(t, 0, screen.artist.name); go(Screen.NowPlaying) },
            Opt("Shuffle all") { val t = screen.artist.albums.flatMap { it.tracks }; Engine.playMusic(t, 0, screen.artist.name, true); go(Screen.NowPlaying) })
        Screen.Songs -> listOf(Opt("Shuffle all songs") { Engine.playMusic(lib.tracks, 0, "All songs", true); go(Screen.NowPlaying) })
        else -> listOf(Opt("Rescan library") { scanTick++ }, Opt("Folders…") { go(Screen.Folders) })
    }

    // Keys: soft keys everywhere; transport keys on the player screen.
    act.keyHandler = handler@{ code ->
        if (options != null || markDialog) return@handler false
        when (code) {
            AKey.KEYCODE_SOFT_LEFT, AKey.KEYCODE_MENU -> { options = screenOptions; return@handler true }
            AKey.KEYCODE_SOFT_RIGHT -> { if (!back()) act.moveTaskToBack(true); return@handler true }
        }
        if (screen != Screen.NowPlaying) return@handler false
        val book = Engine.queue is Queue.Audiobook
        when (code) {
            AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_5 -> Engine.toggle()
            AKey.KEYCODE_DPAD_LEFT, AKey.KEYCODE_4 -> if (book) Engine.seekBy(-30_000) else Engine.prev()
            AKey.KEYCODE_DPAD_RIGHT, AKey.KEYCODE_6 -> if (book) Engine.seekBy(30_000) else Engine.next()
            AKey.KEYCODE_DPAD_UP, AKey.KEYCODE_2 -> if (book) Engine.prev() else Engine.seekBy(-10_000)
            AKey.KEYCODE_DPAD_DOWN, AKey.KEYCODE_8 -> if (book) Engine.next() else Engine.seekBy(10_000)
            AKey.KEYCODE_1 -> Engine.seekBy(-10_000)
            AKey.KEYCODE_3 -> Engine.seekBy(10_000)
            AKey.KEYCODE_7 -> if (book) markDialog = true else Engine.toggleShuffle()
            AKey.KEYCODE_9 -> if (book) go(Screen.Bookmarks) else Engine.cycleRepeat()
            AKey.KEYCODE_STAR -> options = speedOpts()
            AKey.KEYCODE_POUND -> options = sleepOpts()
            AKey.KEYCODE_0 -> if (book) (Engine.queue as? Queue.Audiobook)?.let { go(Screen.BookDetail(it.book)) }
            else -> return@handler false
        }
        true
    }

    val title = when (screen) {
        Screen.Home -> "Music"; Screen.Artists -> "Artists"; is Screen.ArtistAlbums -> screen.artist.name; Screen.Albums -> "Albums"
        is Screen.AlbumTracks -> screen.album.title; Screen.Songs -> "Songs"; Screen.Books -> "Audiobooks"; is Screen.BookDetail -> screen.book.title
        Screen.NowPlaying -> "Now playing"; Screen.Bookmarks -> "Bookmarks"; Screen.Other -> "Other apps"; Screen.Folders -> "Folders"
    }

    GameFrame(skin, "Music Player", listOf("File", "View", "Play", "?")) {
        // Title strip
        if (!pal.win) Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = pal.text, fontFamily = skin.font, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (Engine.sleepAt != 0L) SleepBadge(pal)
        } else Text(title, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (pal.win) 6.dp else 0.dp)) {
            val body = Modifier.fillMaxSize().then(if (pal.win) Modifier.win95Sunken().background(Color.White) else Modifier.background(pal.listBg))
            Box(body) {
                when {
                    !granted -> MenuList(pal, screen.key, focusMemory, listOf(Item("Allow access to music", "Needed to find your songs and audiobooks", Icons.Default.LockOpen) { askPerm.launch(arrayOf(perm)) }))
                    else -> when (screen) {
                        Screen.Home -> HomeScreen(pal, lib, focusMemory, ::go)
                        Screen.Artists -> MenuList(pal, screen.key, focusMemory, lib.artists.map { a -> Item(a.name, "${a.albums.size} albums · ${a.trackCount} songs", Icons.Default.Person, cover = a.albums.first().tracks.first()) { go(Screen.ArtistAlbums(a)) } })
                        is Screen.ArtistAlbums -> MenuList(pal, screen.key, focusMemory, screen.artist.albums.map { al -> Item(al.title, "${al.tracks.size} songs", Icons.Default.Album, cover = al.tracks.first()) { go(Screen.AlbumTracks(al)) } })
                        Screen.Albums -> MenuList(pal, screen.key, focusMemory, lib.albums.map { al -> Item(al.title, al.artist, Icons.Default.Album, cover = al.tracks.first()) { go(Screen.AlbumTracks(al)) } })
                        is Screen.AlbumTracks -> AlbumScreen(pal, screen.album, focusMemory) { i -> Engine.playMusic(screen.album.tracks, i, screen.album.title); go(Screen.NowPlaying) }
                        Screen.Songs -> MenuList(pal, screen.key, focusMemory, lib.tracks.mapIndexed { i, t -> Item(t.title, "${t.artist} · ${fmt(t.durationMs)}", Icons.Default.MusicNote, cover = t) { Engine.playMusic(lib.tracks, i, "All songs"); go(Screen.NowPlaying) } })
                        Screen.Books -> BooksScreen(pal, lib, focusMemory, ::go)
                        is Screen.BookDetail -> BookScreen(pal, screen.book, focusMemory) { go(Screen.NowPlaying) }
                        Screen.NowPlaying -> NowPlaying(pal)
                        Screen.Bookmarks -> BookmarksScreen(pal, focusMemory) { stack.removeAt(stack.lastIndex) }
                        Screen.Other -> OtherScreen(pal, focusMemory)
                        Screen.Folders -> FoldersScreen(pal, lib, focusMemory) { scanTick++ }
                    }
                }
            }
            options?.let { opts -> OptionsPopup(pal, opts) { options = null } }
        }
        if (screen != Screen.NowPlaying && Engine.queue != null) MiniPlayer(pal) { go(Screen.NowPlaying) }
        SoftKeys(skin, "Options", if (stack.size > 1) "Back" else "Exit")
    }

    if (markDialog) {
        val qa = Engine.queue as? Queue.Audiobook
        var note by remember { mutableStateOf("") }
        val p = Engine.player
        if (qa == null || p == null) markDialog = false else AlertDialog(
            onDismissRequest = { markDialog = false },
            title = { Text("Bookmark at ${fmt(p.currentPosition)}") },
            text = { OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true) },
            confirmButton = { TextButton({ BookStore.addMark(ctx, qa.book.id, BookStore.Mark(p.currentMediaItemIndex, p.currentPosition, note, System.currentTimeMillis())); markDialog = false }) { Text("Save") } },
            dismissButton = { TextButton({ markDialog = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- generic list

data class Item(val title: String, val sub: String? = null, val icon: ImageVector? = null, val trailing: String? = null, val cover: Track? = null, val onKey: ((Int) -> Boolean)? = null, val onClick: () -> Unit)

@Composable
fun MenuList(pal: Pal, key: String, memory: MutableMap<String, Int>, rows: List<Item>, header: (@Composable () -> Unit)? = null) {
    val state = rememberLazyListState()
    val target = (memory[key] ?: 0).coerceIn(0, maxOf(0, rows.lastIndex))
    val fr = remember(key) { FocusRequester() }
    LaunchedEffect(key, rows.size) {
        if (rows.isEmpty()) return@LaunchedEffect
        state.scrollToItem(target + if (header != null) 1 else 0)
        delay(30); runCatching { fr.requestFocus() }
    }
    if (rows.isEmpty() && header == null) { Empty(pal, "Nothing here yet"); return }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        header?.let { h -> item { h() } }
        itemsIndexed(rows, key = { i, r -> "$i:${r.title}" }) { i, r ->
            ListRow(pal, r, if (i == target) fr else null) { memory[key] = i }
        }
        if (rows.isEmpty()) item { Empty(pal, "Nothing here yet") }
    }
}

@Composable
fun Empty(pal: Pal, text: String) = Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
    Text(text, color = pal.sub, fontFamily = pal.skin.font)
}

@Composable
fun ListRow(pal: Pal, r: Item, fr: FocusRequester?, onFocus: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val fg = if (focused) pal.hlText else pal.text
    Row(
        Modifier.fillMaxWidth()
            .then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown) r.onKey?.invoke(e.nativeKeyEvent.keyCode) == true else false }
            .clickable(interactionSource = src, indication = null) { r.onClick() }
            .then(if (focused) Modifier.background(pal.hlBg) else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            r.cover != null -> CoverImage(pal, r.cover, 40.dp, r.icon ?: Icons.Default.MusicNote, focused)
            r.icon != null -> Icon(r.icon, null, tint = fg, modifier = Modifier.size(26.dp))
        }
        if (r.cover != null || r.icon != null) Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, color = fg, fontFamily = pal.skin.font, fontSize = 15.sp, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            r.sub?.let { Text(it, color = fg.copy(alpha = 0.7f), fontFamily = pal.skin.font, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        r.trailing?.let { Text(it, color = fg.copy(alpha = 0.8f), fontFamily = pal.skin.font, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp)) }
    }
}

@Composable
fun CoverImage(pal: Pal, t: Track, size: Dp, fallback: ImageVector, focused: Boolean = false) {
    val ctx = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }.coerceAtLeast(64)
    val bmp by produceState<android.graphics.Bitmap?>(null, t.albumId, t.folder, px) { value = Covers.load(ctx, t, px) }
    val shape = RoundedCornerShape(if (pal.win || pal.lcd) 0.dp else 4.dp)
    Box(Modifier.size(size).clip(shape).background(pal.track), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = pal.coverFilter,
            filterQuality = if (pal.lcd) FilterQuality.None else FilterQuality.Low)
        else Icon(fallback, null, tint = if (focused) pal.hlText else pal.sub, modifier = Modifier.fillMaxSize(0.6f))
    }
}

@Composable
fun OptionsPopup(pal: Pal, opts: List<Opt>, close: () -> Unit) {
    val fr = remember(opts) { FocusRequester() }
    LaunchedEffect(opts) { delay(20); runCatching { fr.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }, contentAlignment = Alignment.BottomStart) {
        val bg = when (pal.skin.kind) { SkinKind.WIN95 -> Win95Grey; SkinKind.LCD -> LcdBgC; else -> Color(0xFFF4F6FA) }
        val border = if (pal.lcd) pal.skin.ink else Color.Black.copy(alpha = 0.4f)
        Column(Modifier.padding(8.dp).fillMaxWidth(0.8f).then(if (pal.win) Modifier.win95Raised() else Modifier.clip(RoundedCornerShape(6.dp)).background(bg).border(1.dp, border, RoundedCornerShape(6.dp))).padding(4.dp)) {
            val itemPal = if (pal.win || pal.lcd) pal else Pal(Skin(pal.skin.kind, pal.skin.screen, pal.skin.field, pal.skin.ink, Color(0xFF15202B), pal.skin.dim, pal.skin.overlay, pal.skin.overlayText, pal.skin.barBg, pal.skin.barText, pal.skin.font, pal.skin.accent, pal.skin.food))
            opts.forEachIndexed { i, o -> ListRow(itemPal, Item(o.label) { close(); o.action() }, if (i == 0) fr else null) {} }
        }
    }
}

@Composable
fun SleepBadge(pal: Pal) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val at = Engine.sleepAt
    val text = if (at == -1L) "⏾ end" else "⏾ " + fmt(at - now)
    Text(text, color = pal.sub, fontFamily = pal.skin.font, fontSize = 12.sp)
}

@Composable
fun MiniPlayer(pal: Pal, open: () -> Unit) {
    val t = Engine.currentTrack() ?: return
    val q = Engine.queue
    Row(Modifier.fillMaxWidth().background(pal.track).clickable { open() }.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (Engine.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = pal.text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        val label = if (q is Queue.Audiobook) q.book.title else "${t.title} – ${t.artist}"
        Text(label, color = pal.text, fontFamily = pal.skin.font, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(fmt(Engine.positionMs), color = pal.sub, fontFamily = pal.skin.font, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------- screens

@Composable
fun HomeScreen(pal: Pal, lib: Library, mem: MutableMap<String, Int>, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val rows = buildList {
        if (Engine.queue != null) add(Item("Now playing", Engine.currentTrack()?.title, Icons.Default.PlayCircle) { go(Screen.NowPlaying) })
        // Quick resume of the most recent audiobook.
        lib.books.maxByOrNull { BookStore.lastPlayed(ctx, it.id) }?.takeIf { BookStore.lastPlayed(ctx, it.id) > 0 && (Engine.queue as? Queue.Audiobook)?.book?.id != it.id }?.let { b ->
            add(Item("Continue: ${b.title}", b.author, Icons.Default.Headphones, cover = b.cover) { Engine.playBook(b); go(Screen.NowPlaying) })
        }
        add(Item("Artists", "${lib.artists.size}", Icons.Default.Person) { go(Screen.Artists) })
        add(Item("Albums", "${lib.albums.size}", Icons.Default.Album) { go(Screen.Albums) })
        add(Item("Songs", "${lib.tracks.size}", Icons.Default.MusicNote) { go(Screen.Songs) })
        add(Item("Audiobooks", "${lib.books.size}", Icons.Default.MenuBook) { go(Screen.Books) })
        add(Item("Other apps", "Spotify and other players", Icons.Default.Cast) { go(Screen.Other) })
        add(Item("Folders", "Choose what is music and what is audiobooks", Icons.Default.Folder) { go(Screen.Folders) })
    }
    MenuList(pal, "home", mem, rows)
}

@Composable
fun AlbumScreen(pal: Pal, album: Album, mem: MutableMap<String, Int>, play: (Int) -> Unit) {
    MenuList(pal, "album:${album.id}", mem, album.tracks.mapIndexed { i, t ->
        Item((if (t.trackNo > 0) "${t.trackNo}. " else "") + t.title, if (t.artist != album.artist) t.artist else null, trailing = fmt(t.durationMs)) { play(i) }
    }) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(pal, album.tracks.first(), 96.dp, Icons.Default.Album)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(album.title, color = pal.text, fontFamily = pal.skin.font, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                Text(album.artist, color = pal.sub, fontFamily = pal.skin.font, fontSize = 13.sp)
                Text("${album.tracks.size} songs · ${fmtLong(album.tracks.sumOf { it.durationMs })}", color = pal.sub, fontFamily = pal.skin.font, fontSize = 12.sp)
            }
        }
    }
}

fun bookProgress(ctx: android.content.Context, b: Book): Pair<Long, Long> {
    val pos = BookStore.position(ctx, b.id) ?: return 0L to b.durationMs
    val done = b.files.take(pos.index).sumOf { it.durationMs } + pos.ms
    return done to b.durationMs
}

@Composable
fun BooksScreen(pal: Pal, lib: Library, mem: MutableMap<String, Int>, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    if (lib.loaded && lib.books.isEmpty()) { Empty(pal, "No audiobooks found.\nPut them in a folder called Audiobooks,\nor mark a folder under Folders."); return }
    MenuList(pal, "books", mem, lib.books.map { b ->
        val (done, total) = bookProgress(ctx, b)
        val pct = if (total > 0) (done * 100 / total).toInt() else 0
        val status = when { done == 0L -> fmtLong(total); pct >= 98 -> "Finished"; else -> "$pct% · ${fmtLong(total - done)} left" }
        Item(b.title, "${b.author} · $status", Icons.Default.MenuBook, cover = b.cover) { go(Screen.BookDetail(b)) }
    })
}

@Composable
fun BookScreen(pal: Pal, book: Book, mem: MutableMap<String, Int>, playing: () -> Unit) {
    val ctx = LocalContext.current
    val chapters by produceState(emptyList<Chapter>(), book.id) {
        value = if (book.singleFile) withContext(Dispatchers.IO) { Chapters.read(ctx, book.files[0].uri, book.id) } else emptyList()
    }
    val (done, total) = bookProgress(ctx, book)
    val rows = buildList {
        add(Item(if (done > 0) "Resume" else "Start listening", if (done > 0) "at ${fmt(done)} of ${fmt(total)}" else fmtLong(total), Icons.Default.PlayArrow) { Engine.playBook(book); playing() })
        if (done > 0) add(Item("Start from the beginning", null, Icons.Default.Replay) { Engine.playBook(book, BookStore.Pos(0, 3000)); playing() })
        if (chapters.isNotEmpty()) chapters.forEachIndexed { i, c ->
            val end = chapters.getOrNull(i + 1)?.startMs ?: book.durationMs
            add(Item(c.title, null, trailing = fmt(end - c.startMs)) { Engine.playBook(book, BookStore.Pos(0, c.startMs + 3000)); playing() })
        } else if (!book.singleFile) book.files.forEachIndexed { i, t ->
            add(Item(t.title.ifBlank { t.fileName }, null, trailing = fmt(t.durationMs)) { Engine.playBook(book, BookStore.Pos(i, 3000)); playing() })
        }
    }
    MenuList(pal, "book:${book.id}", mem, rows) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(pal, book.cover, 96.dp, Icons.Default.MenuBook)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(book.title, color = pal.text, fontFamily = pal.skin.font, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                Text(book.author, color = pal.sub, fontFamily = pal.skin.font, fontSize = 13.sp)
                Text("${fmtLong(total)} · ${if (chapters.isNotEmpty()) chapters.size else book.files.size} chapters", color = pal.sub, fontFamily = pal.skin.font, fontSize = 12.sp)
                if (total > 0 && done > 0) Bar(pal, done.toFloat() / total, Modifier.padding(top = 6.dp).width(140.dp))
            }
        }
    }
}

@Composable
fun BookmarksScreen(pal: Pal, mem: MutableMap<String, Int>, close: () -> Unit) {
    val ctx = LocalContext.current
    val q = Engine.queue as? Queue.Audiobook ?: run { Empty(pal, "No book playing"); return }
    var tick by remember { mutableIntStateOf(0) }
    val marks = remember(tick) { BookStore.marks(ctx, q.book.id) }
    if (marks.isEmpty()) { Empty(pal, "No bookmarks yet.\nPress 7 while listening to add one."); return }
    MenuList(pal, "marks:${q.book.id}:$tick", mem, marks.map { m ->
        val chapter = if (q.chapters.isNotEmpty()) q.chapters.lastOrNull { it.startMs <= m.ms }?.title else q.book.files.getOrNull(m.index)?.title
        Item(m.note.ifBlank { chapter ?: "Bookmark" }, "${fmt(m.ms)}${if (m.note.isNotBlank() && chapter != null) " · $chapter" else ""} · 7 deletes", Icons.Default.Bookmark,
            onKey = { k -> if (k == AKey.KEYCODE_7 || k == AKey.KEYCODE_DEL) { BookStore.removeMark(ctx, q.book.id, m); tick++; true } else false }) {
            Engine.jumpTo(m.index, m.ms); close()
        }
    })
}

@Composable
fun Bar(pal: Pal, frac: Float, modifier: Modifier = Modifier) {
    val f = frac.coerceIn(0f, 1f)
    if (pal.win || pal.lcd) {
        // Segmented bar like old progress meters.
        Row(modifier.height(10.dp).then(if (pal.win) Modifier.win95Sunken().background(Color.White) else Modifier.border(1.dp, pal.skin.ink)).padding(1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            val n = 20
            repeat(n) { i -> Box(Modifier.weight(1f).fillMaxHeight().background(if (i < (f * n).toInt()) pal.accent else Color.Transparent)) }
        }
    } else Box(modifier.height(5.dp).clip(RoundedCornerShape(3.dp)).background(pal.track)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(pal.accent))
    }
}

@Composable
fun NowPlaying(pal: Pal) {
    val q = Engine.queue
    val t = Engine.currentTrack()
    if (q == null || t == null) { Empty(pal, "Nothing playing"); return }
    // Keep a focus target so keys arrive here.
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BoxWithConstraints(Modifier.fillMaxSize().focusRequester(fr).focusable().padding(12.dp)) {
        val coverSize = minOf(maxWidth, maxHeight * 0.48f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(if (pal.win) Modifier.win95Sunken() else Modifier) { CoverImage(pal, t, coverSize, if (q is Queue.Audiobook) Icons.Default.MenuBook else Icons.Default.MusicNote) }
            Spacer(Modifier.height(10.dp))
            val pos = Engine.positionMs; val dur = Engine.durationMs
            when (q) {
                is Queue.Music -> {
                    Text(t.title, color = pal.text, fontFamily = pal.skin.font, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.artist} — ${t.album}", color = pal.sub, fontFamily = pal.skin.font, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Bar(pal, if (dur > 0) pos.toFloat() / dur else 0f, Modifier.fillMaxWidth())
                    TimeRow(pal, fmt(pos), "${Engine.index + 1} / ${q.tracks.size}", fmt(dur))
                }
                is Queue.Audiobook -> {
                    val ci = Engine.chapterIndex()
                    val chTitle: String; val chStart: Long; val chEnd: Long; val chCount: Int
                    if (q.chapters.isNotEmpty()) {
                        chTitle = q.chapters[ci].title; chStart = q.chapters[ci].startMs; chEnd = q.chapters.getOrNull(ci + 1)?.startMs ?: dur; chCount = q.chapters.size
                    } else { chTitle = t.title; chStart = 0; chEnd = dur; chCount = q.book.files.size }
                    Text(q.book.title, color = pal.text, fontFamily = pal.skin.font, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chTitle, color = pal.sub, fontFamily = pal.skin.font, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    val chLen = (chEnd - chStart).coerceAtLeast(1)
                    Bar(pal, (pos - chStart).toFloat() / chLen, Modifier.fillMaxWidth())
                    TimeRow(pal, fmt(pos - chStart), "Chapter ${ci + 1}/$chCount", "-" + fmt(chEnd - pos))
                    val before = q.book.files.take(Engine.index).sumOf { it.durationMs }
                    val total = q.book.durationMs
                    val left = ((total - before - pos) / Engine.speed).toLong()
                    Text("${fmtLong(left)} left in book · ${"%.2f".format(Engine.speed).trimEnd('0').trimEnd('.')}×", color = pal.sub, fontFamily = pal.skin.font, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            val book = q is Queue.Audiobook
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Ctrl(pal, if (book) Icons.Default.Replay30 else Icons.Default.SkipPrevious, 34.dp) { if (book) Engine.seekBy(-30_000) else Engine.prev() }
                Ctrl(pal, if (Engine.isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, 54.dp) { Engine.toggle() }
                Ctrl(pal, if (book) Icons.Default.Forward30 else Icons.Default.SkipNext, 34.dp) { if (book) Engine.seekBy(30_000) else Engine.next() }
            }
            Spacer(Modifier.weight(1f))
            val status = buildList {
                if (!book && Engine.shuffle) add("Shuffle")
                if (!book && Engine.repeat == Player.REPEAT_MODE_ALL) add("Repeat all")
                if (!book && Engine.repeat == Player.REPEAT_MODE_ONE) add("Repeat one")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status.isNotEmpty()) Text(status.joinToString(" · ") + "   ", color = pal.sub, fontFamily = pal.skin.font, fontSize = 11.sp)
                if (Engine.sleepAt != 0L) SleepBadge(pal)
            }
            Text(if (book) "◀▶ 30 s · ▲▼ chapter · 7 bookmark · * speed · # sleep" else "◀▶ track · ▲▼ seek · 7 shuffle · 9 repeat · # sleep",
                color = pal.sub, fontFamily = pal.skin.font, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TimeRow(pal: Pal, a: String, mid: String, b: String) = Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
    Text(a, color = pal.sub, fontFamily = pal.skin.font, fontSize = 12.sp)
    Text(mid, color = pal.sub, fontFamily = pal.skin.font, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Text(b, color = pal.sub, fontFamily = pal.skin.font, fontSize = 12.sp)
}

@Composable
fun Ctrl(pal: Pal, icon: ImageVector, size: Dp, onClick: () -> Unit) =
    Icon(icon, null, tint = pal.accent, modifier = Modifier.size(size).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() })

@Composable
fun OtherScreen(pal: Pal, mem: MutableMap<String, Int>) {
    val ctx = LocalContext.current
    var allowed by remember { mutableStateOf(OtherApps.allowed(ctx)) }
    var players by remember { mutableStateOf(emptyList<OtherPlayer>()) }
    LaunchedEffect(Unit) { while (true) { allowed = OtherApps.allowed(ctx); players = OtherApps.players(ctx); delay(1000) } }
    val spotify = remember { ctx.packageManager.getLaunchIntentForPackage("com.spotify.music") }
    val rows = buildList {
        if (!allowed) add(Item("Allow control of other players", "Turn on “Music” in the list that opens", Icons.Default.LockOpen) {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        players.forEach { p ->
            add(Item(p.title, listOf(p.artist, p.appName).filter { it.isNotBlank() }.joinToString(" · "), if (p.playing) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                trailing = if (p.durationMs > 0) fmt(p.durationMs) else null,
                onKey = { k -> when (k) { AKey.KEYCODE_DPAD_LEFT, AKey.KEYCODE_4 -> { OtherApps.prev(p); true }; AKey.KEYCODE_DPAD_RIGHT, AKey.KEYCODE_6 -> { OtherApps.next(p); true }; else -> false } }) {
                OtherApps.toggle(p)
            })
        }
        if (spotify != null) add(Item("Open Spotify", null, Icons.Default.OpenInNew) { ctx.startActivity(spotify.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
    }
    MenuList(pal, "other", mem, rows) {
        val art = players.firstOrNull { it.playing }?.art ?: players.firstOrNull()?.art
        if (art != null) Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
            Image(art.asImageBitmap(), null, Modifier.size(140.dp).clip(RoundedCornerShape(if (pal.lcd || pal.win) 0.dp else 6.dp)), contentScale = ContentScale.Crop, colorFilter = pal.coverFilter)
        }
        if (allowed && players.isEmpty()) Empty(pal, "No other player is active.\nStart something in Spotify first.")
        if (allowed && players.isNotEmpty()) Text("OK play/pause · ◀▶ previous/next", color = pal.sub, fontFamily = pal.skin.font, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
    }
}

@Composable
fun FoldersScreen(pal: Pal, lib: Library, mem: MutableMap<String, Int>, rescan: () -> Unit) {
    val ctx = LocalContext.current
    var roles by remember(lib) { mutableStateOf(lib.folders) }
    val base = remember { android.os.Environment.getExternalStorageDirectory().path }
    MenuList(pal, "folders", mem, roles.keys.sorted().map { f ->
        val role = roles[f] ?: FolderRole.MUSIC
        Item(f.removePrefix(base).ifBlank { "/" }, null, when (role) { FolderRole.MUSIC -> Icons.Default.MusicNote; FolderRole.BOOKS -> Icons.Default.MenuBook; FolderRole.HIDDEN -> Icons.Default.VisibilityOff },
            trailing = when (role) { FolderRole.MUSIC -> "Music"; FolderRole.BOOKS -> "Audiobooks"; FolderRole.HIDDEN -> "Hidden" }) {
            val next = FolderRole.entries[(role.ordinal + 1) % FolderRole.entries.size]
            LibraryScanner.setRole(ctx, f, next)
            roles = roles + (f to next)
            rescan()
        }
    }) {
        Text("OK switches a folder between Music, Audiobooks and Hidden.", color = pal.sub, fontFamily = pal.skin.font, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
    }
}
