package com.example

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Windows 95 look: pixel icons, window frames, list selection, check boxes and message boxes. */
object Win95 {
    val Navy = Color(0xFF000080)
    val Face = Color(0xFFC0C0C0)
    val Shadow = Color(0xFF808080)

    private val palette = mapOf(
        'K' to 0xFF000000.toInt(), 'W' to 0xFFFFFFFF.toInt(), 'G' to 0xFFC0C0C0.toInt(), 'D' to 0xFF808080.toInt(),
        'Y' to 0xFFFFFF00.toInt(), 'O' to 0xFF808000.toInt(), 'B' to 0xFF0000FF.toInt(), 'N' to 0xFF000080.toInt(),
        'R' to 0xFFFF0000.toInt(), 'M' to 0xFF800000.toInt(), 'C' to 0xFF00FFFF.toInt(), 'T' to 0xFF008080.toInt(),
        'L' to 0xFF00FF00.toInt(), 'E' to 0xFF008000.toInt()
    )

    /** 16×16 icons in the 16-colour palette (same art as the desktop sidebar). */
    private val icons = mapOf(
    "mail" to listOf(
        "................",
        "................",
        "................",
        "KKKKKKKKKKKKKKK.",
        "KWKWWWWWWWWWKWKD",
        "KWWKWWWWWWWKWWWD",
        "KWWWKWWWWWKWWWKD",
        "KWWWWKWWWKWWWWKD",
        "KWWWWWKWKWWWWWKD",
        "KWWWWKWKWKWWWWKD",
        "KWWWKWWWWWKWWWKD",
        "KWWKWWWWWWWKWWKD",
        "KWKWWWWWWWWWKWKD",
        "KKKKKKKKKKKKKKKD",
        ".DDDDDDDDDDDDDDD",
        "................"
    ),
    "msg" to listOf(
        "................",
        "...KKKKKKKKKK...",
        "..KWWWWWWWWWWK..",
        ".KWWWWWWWWWWWWK.",
        "KWWNNNNNNNNNWWWK",
        "KWWWWWWWWWWWWWWK",
        "KWWNNNNNNNWWWWWK",
        "KWWWWWWWWWWWWWWK",
        "KWWNNNNNNNNNNWWK",
        ".KWWWWWWWWWWWWKD",
        "..KKWWWKKKKKKKD.",
        "...KWWKDDDDDDD..",
        "...KWKD.........",
        "...KKD..........",
        "...KD...........",
        "................"
    ),
    "chat" to listOf(
        "................",
        "KKKKKKKKKK......",
        "KWWWWWWWWK......",
        "KWNNNNNNWK......",
        "KWWWWWWWWK......",
        "KWNNNNWKKKKKKKKK",
        "KWWWWWWKCCCCCCCK",
        "KKWKKKKKCTTTTTCK",
        ".KKK...KCCCCCCCK",
        ".KK....KCTTTTCCK",
        ".K.....KCCCCCCCK",
        ".......KKKKKKCKK",
        "............KKK.",
        ".............KK.",
        "..............K.",
        "................"
    ),
    "bell" to listOf(
        "................",
        ".KKKKKKKKKKKKK..",
        "KYYYYYYYYYYYYYK.",
        "KYYYYYKKKYYYYYKD",
        "KYYYYYKKKYYYYYKD",
        "KYYYYYKKKYYYYYKD",
        "KYYYYYKKKYYYYYKD",
        "KYYYYYYKYYYYYYKD",
        "KYYYYYYYYYYYYYKD",
        "KYYYYYKKKYYYYYKD",
        "KYYYYYKKKYYYYYKD",
        ".KKKYYKKKKKKKKKD",
        "...KYKDDDDDDDDD.",
        "...KKD..........",
        "...KD...........",
        "................"
    ),
    "call" to listOf(
        "................",
        "...KKKKKKKKKK...",
        "..KDDDDDDDDDDK..",
        ".KDKKKKKKKKKKDK.",
        ".KDK.KKKKKK.KDK.",
        "..K.KGGGGGGK.K..",
        "...KGGGGGGGGK...",
        "..KGGWKWKWKGGK..",
        "..KGGWWWWWWWGGK.",
        ".KGGGWKWKWKGGGK.",
        ".KGGGWWWWWWWGGK.",
        ".KGGGWKWKWKGGGK.",
        "KGGGGGGGGGGGGGGK",
        "KDDDDDDDDDDDDDDK",
        ".KKKKKKKKKKKKKK.",
        "................"
    ),
    "cal" to listOf(
        "................",
        "KKKKKKKKKKKKKKK.",
        "KRRWRRRRRRRWRRKD",
        "KRRRRRRRRRRRRRKD",
        "KKKKKKKKKKKKKKKD",
        "KWWWWWWWWWWWWWKD",
        "KWDWDWDWDWDWDWKD",
        "KWWWWWWWWWWWWWKD",
        "KWDWDWDWDWNWDWKD",
        "KWWWWWWWWWWWWWKD",
        "KWDWDWDWDWDWDWKD",
        "KWWWWWWWWWWWWWKD",
        "KWDWDWDWWWWWWWKD",
        "KWWWWWWWWWWWWWKD",
        "KKKKKKKKKKKKKKKD",
        ".DDDDDDDDDDDDDDD"
    ),
    "person" to listOf(
        "................",
        "......KKKK......",
        ".....KWWWWK.....",
        "....KWWWWWWK....",
        "....KWKWWKWK....",
        "....KWWWWWWK....",
        "....KWWKKWWK....",
        ".....KWWWWK.....",
        "......KKKK......",
        "....KKNNNNKK....",
        "...KNNNWWNNNK...",
        "..KNNNNWWNNNNK..",
        "..KNNNNWWNNNNK..",
        "..KNNNNNNNNNNK..",
        "..KKKKKKKKKKKK..",
        "................"
    ),
    "apps" to listOf(
        "................",
        ".KKKKKK..KKKKKK.",
        ".KNNNNKD.KNNNNKD",
        ".KWWWWKD.KWWWWKD",
        ".KWTTWKD.KWRRWKD",
        ".KWTTWKD.KWRRWKD",
        ".KKKKKKD.KKKKKKD",
        "..DDDDDD..DDDDDD",
        "................",
        ".KKKKKK..KKKKKK.",
        ".KNNNNKD.KNNNNKD",
        ".KWWWWKD.KWWWWKD",
        ".KWYYWKD.KWEEWKD",
        ".KWYYWKD.KWEEWKD",
        ".KKKKKKD.KKKKKKD",
        "..DDDDDD..DDDDDD"
    ),
    "settings" to listOf(
        "................",
        "......KKKK......",
        "...KK.KGGK.KK...",
        "..KGGKKGGKKGGK..",
        "..KGGGGGGGGGGK..",
        "...KGGWDDDGGK...",
        ".KKGGWK..DGGGKK.",
        ".KGGGD....DGGGK.",
        ".KGGGD....WGGGK.",
        ".KKGGGD..KWGGKK.",
        "...KGGDWWWWGK...",
        "..KGGGGGGGGGGK..",
        "..KGGKKGGKKGGK..",
        "...KK.KGGK.KK...",
        "......KKKK......",
        "................"
    ),
    "usage" to listOf(
        "................",
        "K...............",
        "K..........KK...",
        "K..........KRK..",
        "K......KK..KRKD.",
        "K......KBK.KRKD.",
        "K......KBKDKRKD.",
        "K..KK..KBKDKRKD.",
        "K..KEK.KBKDKRKD.",
        "K..KEKDKBKDKRKD.",
        "K..KEKDKBKDKRKD.",
        "K..KEKDKBKDKRKD.",
        "K..KEKDKBKDKRKD.",
        "KKKKKKKKKKKKKKKK",
        "................",
        "................"
    ),
    "card" to listOf(
        "................",
        "................",
        ".KKKKKKKKKKKKKK.",
        "KEEEEEEEEEEEEEEK",
        "KEEEEEEEEEEEEEEK",
        "KKKKKKKKKKKKKKKK",
        "KKKKKKKKKKKKKKKK",
        "KEEEEEEEEEEEEEEK",
        "KEYYYEEEEEEEEEEK",
        "KEYOYEEEEEEEEEEK",
        "KEYYYEEEEEEEEEEK",
        "KEEEEEEEEEEEEEEK",
        "KEWWEWWEWWEWWEEK",
        ".KKKKKKKKKKKKKKD",
        "..DDDDDDDDDDDDDD",
        "................"
    ),
    "calc" to listOf(
        "..KKKKKKKKKKKK..",
        "..KGGGGGGGGGGKD.",
        "..KGKKKKKKKKGKD.",
        "..KGKLLLLLLKGKD.",
        "..KGKKKKKKKKGKD.",
        "..KGGGGGGGGGGKD.",
        "..KGWDWDWDRDGKD.",
        "..KGGGGGGGGGGKD.",
        "..KGWDWDWDWDGKD.",
        "..KGGGGGGGGGGKD.",
        "..KGWDWDWDWDGKD.",
        "..KGGGGGGGGGGKD.",
        "..KGWDWDWDWDGKD.",
        "..KGGGGGGGGGGKD.",
        "..KKKKKKKKKKKKD.",
        "...DDDDDDDDDDDD."
    ),
    "clock" to listOf(
        "................",
        ".....KKKKKK.....",
        "...KKWWWWWWKK...",
        "..KWWWWKWWWWWK..",
        "..KWWWWKWWWWWKD.",
        ".KWKWWWKWWWWKWKD",
        ".KWWWWWKWWWWWWKD",
        ".KWWWWWKWWWWWWKD",
        ".KKWWWWKKKKWWKKD",
        ".KWWWWWWWWWWWWKD",
        ".KWWWWWWWWWWWWKD",
        ".KWKWWWWWWWWKWKD",
        "..KWWWWWKWWWWKD.",
        "...KKWWWWWWKKDD.",
        ".....KKKKKKDD...",
        ".......DDDD....."
    ),
    "battery" to listOf(
        "................",
        "................",
        "................",
        ".KKKKKKKKKKKKK..",
        ".KWWWWWWWWWWWKK.",
        ".KWEEEEEEEWWWKKK",
        ".KWELLLLLEWWWKGK",
        ".KWELLLLLEWWWKGK",
        ".KWELLLLLEWWWKGK",
        ".KWEEEEEEEWWWKKK",
        ".KWWWWWWWWWWWKKD",
        ".KKKKKKKKKKKKKDD",
        "..DDDDDDDDDDDDD.",
        "................",
        "................",
        "................"
    ),
    "camera" to listOf(
        "................",
        "................",
        "....KKKKK.......",
        "...KDDDDDK......",
        ".KKKKKKKKKKKKKK.",
        "KGGGGGGGGGGGGRGK",
        "KGGGGKKKKKGGGGGK",
        "KKKKKBBBBBKKKKKK",
        "KDDDKBNNNBKDDDDK",
        "KDDKBNCNNNBKDDDK",
        "KDDKBNNNNNBKDDDK",
        "KDDDKBNNNBKDDDDK",
        "KDDDDKKKKKDDDDDK",
        ".KKKKKKKKKKKKKKD",
        "..DDDDDDDDDDDDDD",
        "................"
    ),
    "desktop" to listOf(
        "................",
        ".KKKKKKKKKKKKKK.",
        ".KGGGGGGGGGGGGKD",
        ".KGKKKKKKKKKKGKD",
        ".KGKTTTTTTTTKGKD",
        ".KGKTTTTTTTTKGKD",
        ".KGKTTTTTTTTKGKD",
        ".KGKTTTTTTTTKGKD",
        ".KGKKKKKKKKKKGKD",
        ".KGGGGGGGGGLGGKD",
        ".KKKKKKKKKKKKKKD",
        "..DDDDKGGKDDDDDD",
        ".....KGGGGK.....",
        "...KKKKKKKKKK...",
        "...KGGGGGGGGKD..",
        "...KKKKKKKKKKD.."
    ),
    "home" to listOf(
        "................",
        ".......KK..KK...",
        "......KRRK.KK...",
        ".....KRRRRKKK...",
        "....KRRRRRRKK...",
        "...KRRRRRRRRK...",
        "..KRRRRRRRRRRK..",
        ".KKKKKKKKKKKKKK.",
        "..KWWWWWWWWWWK..",
        "..KWKKKWWKKKWK..",
        "..KWKCKWWKCKWK..",
        "..KWKKKWWKKKWK..",
        "..KWWWKKKWWWWK..",
        "..KWWWKOKWWWWK..",
        "..KWWWKOKWWWWKD.",
        "..KKKKKKKKKKKKD."
    ),
    "cake" to listOf(
        "................",
        "...R...R...R....",
        "..RY..RY..RY....",
        "...K...K...K....",
        "...W...W...W....",
        "...W...W...W....",
        ".KKKKKKKKKKKKK..",
        ".KWWWWWWWWWWWKD.",
        ".KMWMWMWMWMWMKD.",
        ".KMMMMMMMMMMMKD.",
        ".KYYYYYYYYYYYKD.",
        ".KMMMMMMMMMMMKD.",
        ".KMMMMMMMMMMMKD.",
        "KKKKKKKKKKKKKKKD",
        "KGGGGGGGGGGGGGKD",
        "KKKKKKKKKKKKKKKD"
    ),
    "photo" to listOf(
        "................",
        "KKKKKKKKKKKKKKKK",
        "KOOOOOOOOOOOOOOK",
        "KOKKKKKKKKKKKKOK",
        "KOKCCCCCCCCYYKOK",
        "KOKCCCCCCCCYYKOK",
        "KOKCCCCKCCCCCKOK",
        "KOKCCCKEKCCCCKOK",
        "KOKCCKEEEKCKCKOK",
        "KOKCKEEEEEKEKKOK",
        "KOKKEEEEEEEEEKOK",
        "KOKEEEEEEEEEEKOK",
        "KOKKKKKKKKKKKKOK",
        "KOOOOOOOOOOOOOOK",
        "KKKKKKKKKKKKKKKK",
        "................"
    ),
    "clip" to listOf(
        ".....KKKKKK.....",
        "....KDGGGGDK....",
        "..KKKGKKKKGKKK..",
        "..KOKKKKKKKKOK..",
        "..KOWWWWWWWWOKD.",
        "..KOWKKKKKKWOKD.",
        "..KOWWWWWWWWOKD.",
        "..KOWKKKKKWWOKD.",
        "..KOWWWWWWWWOKD.",
        "..KOWKKKKKKWOKD.",
        "..KOWWWWWWWWOKD.",
        "..KOWKKKKWWWOKD.",
        "..KOWWWWWWWWOKD.",
        "..KOOOOOOOOOOKD.",
        "..KKKKKKKKKKKKD.",
        "...DDDDDDDDDDDD."
    ),
    "tool" to listOf(
        "................",
        "...........KKK..",
        "..........KGGK..",
        ".........KGGK...",
        "...KK...KGGK.KK.",
        "...KGK..KGGKKGK.",
        "....KGKKGGGGGGK.",
        ".....KGKGGGGGK..",
        "......KKGGKKK...",
        ".....KGGGK......",
        "....KGGGK.......",
        "...KGGGK........",
        "..KGGGK.........",
        ".KGGGK..........",
        ".KGGK...........",
        "..KK............"
    )
    )

    private val cache = HashMap<String, ImageBitmap>()

    fun bitmap(name: String): ImageBitmap? = cache[name] ?: icons[name]?.let { rows ->
        val px = IntArray(256) { i -> palette[rows[i / 16][i % 16]] ?: 0 }
        android.graphics.Bitmap.createBitmap(px, 16, 16, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap().also { cache[name] = it }
    }
}

/** A crisp, upscaled 16×16 Windows 95 icon. */
@Composable
fun Win95PixelIcon(name: String, modifier: Modifier = Modifier) {
    val bmp = remember(name) { Win95.bitmap(name) } ?: return
    Image(bmp, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
}

/** White sunken field (text boxes, check boxes, list panes). */
fun Modifier.win95Field(): Modifier = this.drawBehind {
    val px = 1.dp.toPx().coerceAtLeast(1f); val w = size.width; val h = size.height
    drawRect(Color.White)
    drawRect(Win95.Shadow, Offset.Zero, Size(w, px)); drawRect(Win95.Shadow, Offset.Zero, Size(px, h))
    drawRect(Color.Black, Offset(px, px), Size(w - 2 * px, px)); drawRect(Color.Black, Offset(px, px), Size(px, h - 2 * px))
    drawRect(Color.White, Offset(0f, h - px), Size(w, px)); drawRect(Color.White, Offset(w - px, 0f), Size(px, h))
    drawRect(Win95.Face, Offset(px, h - 2 * px), Size(w - 2 * px, px)); drawRect(Win95.Face, Offset(w - 2 * px, px), Size(px, h - 2 * px))
}

/** List-style focus: navy bar (Windows 95 selected item); keeps the focus click sound. */
fun Modifier.win95ListHighlight(isFocused: Boolean): Modifier = composed {
    val sounds = LocalSymbianSoundsEnabled.current
    LaunchedEffect(isFocused) { if (isFocused && sounds) SymbianSoundManager.playClick() }
    if (isFocused) background(Win95.Navy) else this
}

@Composable
fun Win95Checkbox(checked: Boolean) {
    Box(Modifier.size(20.dp).win95Field(), contentAlignment = Alignment.Center) {
        if (checked) Text("✓", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CaptionButton(label: String, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.size(width = 18.dp, height = 16.dp).win95Bevel()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp) }
}

@Composable
fun Win95TitleBar(title: String, onClose: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(Win95.Navy).padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (onClose == null) { CaptionButton("_"); Spacer(Modifier.width(2.dp)); CaptionButton("□"); Spacer(Modifier.width(2.dp)) }
        CaptionButton("×", onClose)
    }
}

/** Grey application window with a navy title bar; content gets black-on-grey theme colours. */
@Composable
fun Win95Window(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme.copy(
        background = Win95.Face, surface = Win95.Face, onBackground = Color.Black, onSurface = Color.Black,
        primary = Win95.Navy, secondary = Win95.Navy
    )
    Column(modifier.fillMaxSize().padding(3.dp).win95Bevel().padding(3.dp)) {
        Win95TitleBar(title)
        Spacer(Modifier.height(2.dp))
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides Color.Black) {
                Box(Modifier.fillMaxWidth().weight(1f).background(Win95.Face)) { content() }
            }
        }
    }
}

/**
 * Same-package stand-in for Material's AlertDialog (it wins over the material3.* star imports), so every
 * existing dialog becomes a classic message box in the Windows 95 theme and stays unchanged otherwise.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = Color.Unspecified,
) {
    // Dialogs with their own colour (battery saver) keep their look.
    if (!LocalWin95.current || containerColor.isSpecified) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
            dismissButton = dismissButton, icon = icon, title = title, text = text,
            containerColor = if (containerColor.isSpecified) containerColor else AlertDialogDefaults.containerColor
        )
        return
    }
    Dialog(onDismissRequest = onDismissRequest) {
        val scheme = MaterialTheme.colorScheme.copy(
            background = Win95.Face, surface = Color.White, onBackground = Color.Black, onSurface = Color.Black,
            primary = Color.Black, secondary = Win95.Navy
        )
        MaterialTheme(colorScheme = scheme) {
            Column(modifier.widthIn(min = 240.dp, max = 340.dp).win95Bevel().padding(3.dp)) {
                Row(
                    Modifier.fillMaxWidth().background(Win95.Navy).padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)) {
                                title?.invoke() ?: Text("Symbian", color = Color.White)
                            }
                        }
                    }
                    CaptionButton("×", onDismissRequest)
                }
                CompositionLocalProvider(LocalContentColor provides Color.Black) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        if (icon != null) { icon(); Spacer(Modifier.width(10.dp)) }
                        else { Win95PixelIcon("bell", Modifier.size(32.dp)); Spacer(Modifier.width(10.dp)) }
                        Box(Modifier.weight(1f)) {
                            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = Color.Black)) { text?.invoke() }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                        Box(Modifier.win95Bevel()) { confirmButton() }
                        if (dismissButton != null) { Spacer(Modifier.width(8.dp)); Box(Modifier.win95Bevel()) { dismissButton() } }
                    }
                }
            }
        }
    }
}

/** Foreground for text that is white on the wallpaper themes but must be black on Windows 95 grey. */
@Composable
fun win95Fg(): Color = if (LocalWin95.current) Color.Black else Color.White

/** Wraps a screen in a Windows 95 window when [title] is set; otherwise shows it unchanged. */
@Composable
fun Win95Frame(title: String?, content: @Composable () -> Unit) {
    if (title == null) content() else Win95Window(title) { content() }
}
