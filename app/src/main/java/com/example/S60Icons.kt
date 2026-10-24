package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Fill of one icon layer: a flat colour, or a top-to-bottom gloss gradient across the layer. */
sealed class S60Fill {
    data class Solid(val c: Color) : S60Fill()
    data class Vertical(val top: Color, val bottom: Color) : S60Fill()
}

private class L(val d: String, val fill: S60Fill?, val stroke: Color?, val strokeW: Float, val dx: Float, val dy: Float) {
    val path: Path by lazy { PathParser().parsePathString(d).toPath() }
}

/**
 * Glossy S60-style icons for the Classic S60 Sky theme, drawn as vectors on a 48×48 grid
 * (bold colours, soft drop shadow, white gloss on top) so they stay sharp at any size.
 */
object S60Icons {
    private val icons: Map<String, List<L>> = mapOf(
    "posten" to listOf(
        L("M2.5,24.5A21.5,21.5 0 1 0 45.5,24.5A21.5,21.5 0 1 0 2.5,24.5Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M2.5,24.5A21.5,21.5 0 1 0 45.5,24.5A21.5,21.5 0 1 0 2.5,24.5Z", S60Fill.Vertical(Color(0xFFFF4A3A), Color(0xFFC40F0F)), Color(0xFF7A0606), 1f, 0f, 0f),
        L("M23.1,3.6H24.9V5.4H26.7V7.2H24.9V9H23.1V7.2H21.3V5.4H23.1Z", S60Fill.Vertical(Color(0xFFFFF07A), Color(0xFFF2B705)), Color(0xFF8A4B00), 0.8f, 0f, 0f),
        L("M22.1,10.6A1.9,1.9 0 1 0 25.9,10.6A1.9,1.9 0 1 0 22.1,10.6Z", S60Fill.Vertical(Color(0xFFFFF07A), Color(0xFFF2B705)), Color(0xFF8A4B00), 0.8f, 0f, 0f),
        L("M15.2,20L14,12.6C16,14.6 18.2,14.4 19.2,12.4C20.6,10.6 22.4,11 24,12.8C25.6,11 27.4,10.6 28.8,12.4C29.8,14.4 32,14.6 34,12.6L32.8,20Z", S60Fill.Vertical(Color(0xFFFFF07A), Color(0xFFF2B705)), Color(0xFF8A4B00), 0.8f, 0f, 0f),
        L("M17.7,17A1.3,1.3 0 1 0 20.3,17A1.3,1.3 0 1 0 17.7,17Z", S60Fill.Solid(Color(0xFFC40F0F)), null, 0f, 0f, 0f),
        L("M22.7,16.4A1.3,1.3 0 1 0 25.3,16.4A1.3,1.3 0 1 0 22.7,16.4Z", S60Fill.Solid(Color(0xFFC40F0F)), null, 0f, 0f, 0f),
        L("M27.7,17A1.3,1.3 0 1 0 30.3,17A1.3,1.3 0 1 0 27.7,17Z", S60Fill.Solid(Color(0xFFC40F0F)), null, 0f, 0f, 0f),
        L("M12.9,12.4A1.1,1.1 0 1 0 15.1,12.4A1.1,1.1 0 1 0 12.9,12.4Z", S60Fill.Vertical(Color(0xFFFFF07A), Color(0xFFF2B705)), Color(0xFF8A4B00), 0.8f, 0f, 0f),
        L("M32.9,12.4A1.1,1.1 0 1 0 35.1,12.4A1.1,1.1 0 1 0 32.9,12.4Z", S60Fill.Vertical(Color(0xFFFFF07A), Color(0xFFF2B705)), Color(0xFF8A4B00), 0.8f, 0f, 0f),
        L("M15.6,19.2H32.4A1.4,1.4 0 0 1 33.8,20.599999999999998V21.400000000000002A1.4,1.4 0 0 1 32.4,22.8H15.6A1.4,1.4 0 0 1 14.2,21.400000000000002V20.599999999999998A1.4,1.4 0 0 1 15.6,19.2Z", S60Fill.Vertical(Color(0xFFFFF07A), Color(0xFFF2B705)), Color(0xFF8A4B00), 0.8f, 0f, 0f),
        L("M19.8,40.6C13.6,40 8.4,35.2 6,27.8L11,26.6C12.4,31.4 15.2,34.8 18.6,35.8Z", S60Fill.Vertical(Color(0xFFFFF07A), Color(0xFFF2B705)), Color(0xFF8A4B00), 0.8f, 0f, 0f),
        L("M28.6,35.9C32.4,34.9 35.4,31.8 36.9,27.6L41.4,28.5C39.6,34.6 35,39.4 29.6,40.5Z", S60Fill.Vertical(Color(0xFFFFF07A), Color(0xFFF2B705)), Color(0xFF8A4B00), 0.8f, 0f, 0f),
        L("M15.6,33A8.4,8.4 0 1 0 32.4,33A8.4,8.4 0 1 0 15.6,33ZM19.6,33A4.4,4.4 0 1 1 28.4,33A4.4,4.4 0 1 1 19.6,33Z", S60Fill.Vertical(Color(0xFFFFF07A), Color(0xFFF2B705)), Color(0xFF8A4B00), 0.8f, 0f, 0f),
        L("M6,19Q24,3 42,19Q24,14 6,19Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "msg" to listOf(
        L("M10,8H38A6,6 0 0 1 44,14V28A6,6 0 0 1 38,34H22L13,42V34H10A6,6 0 0 1 4,28V14A6,6 0 0 1 10,8Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M10,8H38A6,6 0 0 1 44,14V28A6,6 0 0 1 38,34H22L13,42V34H10A6,6 0 0 1 4,28V14A6,6 0 0 1 10,8Z", S60Fill.Vertical(Color(0xFF8BE36A), Color(0xFF27962C)), Color(0xFF1B6B1F), 1.2f, 0f, 0f),
        L("M12,21A3,3 0 1 0 18,21A3,3 0 1 0 12,21Z", S60Fill.Solid(Color(0xFFFFFFFF)), null, 0f, 0f, 0f),
        L("M21,21A3,3 0 1 0 27,21A3,3 0 1 0 21,21Z", S60Fill.Solid(Color(0xFFFFFFFF)), null, 0f, 0f, 0f),
        L("M30,21A3,3 0 1 0 36,21A3,3 0 1 0 30,21Z", S60Fill.Solid(Color(0xFFFFFFFF)), null, 0f, 0f, 0f),
        L("M10,9.5H38A4.5,4.5 0 0 1 42.5,14V17Q24,23 5.5,17V14A4.5,4.5 0 0 1 10,9.5Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "mail" to listOf(
        L("M7,11H41A3,3 0 0 1 44,14V36A3,3 0 0 1 41,39H7A3,3 0 0 1 4,36V14A3,3 0 0 1 7,11Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M7,11H41A3,3 0 0 1 44,14V36A3,3 0 0 1 41,39H7A3,3 0 0 1 4,36V14A3,3 0 0 1 7,11Z", S60Fill.Vertical(Color(0xFFFFFFFF), Color(0xFFD6DEE8)), Color(0xFF3A5F9E), 1.4f, 0f, 0f),
        L("M5,13L24,28L43,13", null, Color(0xFF3A5F9E), 1.6f, 0f, 0f),
        L("M5,38L18,25M43,38L30,25", null, Color(0xFF7F95BA), 1.2f, 0f, 0f),
        L("M34,14H40A1,1 0 0 1 41,15V20A1,1 0 0 1 40,21H34A1,1 0 0 1 33,20V15A1,1 0 0 1 34,14Z", S60Fill.Vertical(Color(0xFFFF6B5B), Color(0xFFC9241A)), null, 0f, 0f, 0f)
    ),
    "cal" to listOf(
        L("M10,7H38A4,4 0 0 1 42,11V39A4,4 0 0 1 38,43H10A4,4 0 0 1 6,39V11A4,4 0 0 1 10,7Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M10,7H38A4,4 0 0 1 42,11V39A4,4 0 0 1 38,43H10A4,4 0 0 1 6,39V11A4,4 0 0 1 10,7Z", S60Fill.Vertical(Color(0xFFFFFFFF), Color(0xFFE4E8EE)), Color(0xFF8A96A8), 1f, 0f, 0f),
        L("M6,11A4,4 0 0 1 10,7H38A4,4 0 0 1 42,11V17H6Z", S60Fill.Vertical(Color(0xFFFF6A5C), Color(0xFFC81E1E)), null, 0f, 0f, 0f),
        L("M15,4H15A2,2 0 0 1 17,6V10A2,2 0 0 1 15,12H15A2,2 0 0 1 13,10V6A2,2 0 0 1 15,4Z", S60Fill.Vertical(Color(0xFFF2F2F2), Color(0xFF9AA3AF)), null, 0f, 0f, 0f),
        L("M33,4H33A2,2 0 0 1 35,6V10A2,2 0 0 1 33,12H33A2,2 0 0 1 31,10V6A2,2 0 0 1 33,4Z", S60Fill.Vertical(Color(0xFFF2F2F2), Color(0xFF9AA3AF)), null, 0f, 0f, 0f),
        L("M16,25L22,22V38H18V27.5L16,28.5ZM26,22H34V25L29,38H25L30,25H26Z", S60Fill.Solid(Color(0xFF2A3550)), null, 0f, 0f, 0f),
        L("M8,9H40V14Q24,17 8,14Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "person" to listOf(
        L("M13,5H35A4,4 0 0 1 39,9V39A4,4 0 0 1 35,43H13A4,4 0 0 1 9,39V9A4,4 0 0 1 13,5Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M13,5H35A4,4 0 0 1 39,9V39A4,4 0 0 1 35,43H13A4,4 0 0 1 9,39V9A4,4 0 0 1 13,5Z", S60Fill.Vertical(Color(0xFFFFC24A), Color(0xFFE07B10)), Color(0xFF9A4E05), 1f, 0f, 0f),
        L("M8,10H10A2,2 0 0 1 12,12V12A2,2 0 0 1 10,14H8A2,2 0 0 1 6,12V12A2,2 0 0 1 8,10Z", S60Fill.Solid(Color(0xFF7A3E05)), null, 0f, 0f, 0f),
        L("M8,22H10A2,2 0 0 1 12,24V24A2,2 0 0 1 10,26H8A2,2 0 0 1 6,24V24A2,2 0 0 1 8,22Z", S60Fill.Solid(Color(0xFF7A3E05)), null, 0f, 0f, 0f),
        L("M8,34H10A2,2 0 0 1 12,36V36A2,2 0 0 1 10,38H8A2,2 0 0 1 6,36V36A2,2 0 0 1 8,34Z", S60Fill.Solid(Color(0xFF7A3E05)), null, 0f, 0f, 0f),
        L("M19,19A6,6 0 1 0 31,19A6,6 0 1 0 19,19Z", S60Fill.Vertical(Color(0xFFFFFFFF), Color(0xFFF3E2C4)), null, 0f, 0f, 0f),
        L("M14,36C14,29 19,26 25,26C31,26 36,29 36,36Z", S60Fill.Vertical(Color(0xFFFFFFFF), Color(0xFFF3E2C4)), null, 0f, 0f, 0f),
        L("M11,7H37V14Q24,19 11,14Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "bell" to listOf(
        L("M24,6C17,6 13,11 13,18V27L9,33V35H39V33L35,27V18C35,11 31,6 24,6Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M24,6C17,6 13,11 13,18V27L9,33V35H39V33L35,27V18C35,11 31,6 24,6Z", S60Fill.Vertical(Color(0xFFFFF09A), Color(0xFFE3A10C)), Color(0xFF9C6A05), 1.2f, 0f, 0f),
        L("M19,38A5,4 0 1 0 29,38A5,4 0 1 0 19,38Z", S60Fill.Vertical(Color(0xFFE3A10C), Color(0xFF9C6A05)), null, 0f, 0f, 0f),
        L("M24,8C19,8 15.5,11 15,17Q20,15 24,15Q20,11 24,8Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "apps" to listOf(
        L("M9,5H18A4,4 0 0 1 22,9V18A4,4 0 0 1 18,22H9A4,4 0 0 1 5,18V9A4,4 0 0 1 9,5Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M30,5H39A4,4 0 0 1 43,9V18A4,4 0 0 1 39,22H30A4,4 0 0 1 26,18V9A4,4 0 0 1 30,5Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M9,26H18A4,4 0 0 1 22,30V39A4,4 0 0 1 18,43H9A4,4 0 0 1 5,39V30A4,4 0 0 1 9,26Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M30,26H39A4,4 0 0 1 43,30V39A4,4 0 0 1 39,43H30A4,4 0 0 1 26,39V30A4,4 0 0 1 30,26Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M9,5H18A4,4 0 0 1 22,9V18A4,4 0 0 1 18,22H9A4,4 0 0 1 5,18V9A4,4 0 0 1 9,5Z", S60Fill.Vertical(Color(0xFFFF8A7A), Color(0xFFD4281C)), null, 0f, 0f, 0f),
        L("M30,5H39A4,4 0 0 1 43,9V18A4,4 0 0 1 39,22H30A4,4 0 0 1 26,18V9A4,4 0 0 1 30,5Z", S60Fill.Vertical(Color(0xFF8BE36A), Color(0xFF27962C)), null, 0f, 0f, 0f),
        L("M9,26H18A4,4 0 0 1 22,30V39A4,4 0 0 1 18,43H9A4,4 0 0 1 5,39V30A4,4 0 0 1 9,26Z", S60Fill.Vertical(Color(0xFF7FC4FF), Color(0xFF1F5FCF)), null, 0f, 0f, 0f),
        L("M30,26H39A4,4 0 0 1 43,30V39A4,4 0 0 1 39,43H30A4,4 0 0 1 26,39V30A4,4 0 0 1 30,26Z", S60Fill.Vertical(Color(0xFFFFE57A), Color(0xFFE0A00C)), null, 0f, 0f, 0f),
        L("M7,7H20V12Q13,14 7,12Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f),
        L("M28,7H41V12Q34,14 28,12Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f),
        L("M7,28H20V33Q13,35 7,33Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f),
        L("M28,28H41V33Q34,35 28,33Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "settings" to listOf(
        L("M42.9,21.7L42.9,26.3L38.0,27.9L36.6,31.1L38.9,35.7L35.7,38.9L31.1,36.6L27.9,38.0L26.3,42.9L21.7,42.9L20.1,38.0L16.9,36.6L12.3,38.9L9.1,35.7L11.4,31.1L10.0,27.9L5.1,26.3L5.1,21.7L10.0,20.1L11.4,16.9L9.1,12.3L12.3,9.1L16.9,11.4L20.1,10.0L21.7,5.1L26.3,5.1L27.9,10.0L31.1,11.4L35.7,9.1L38.9,12.3L36.6,16.9L38.0,20.1Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M42.9,21.7L42.9,26.3L38.0,27.9L36.6,31.1L38.9,35.7L35.7,38.9L31.1,36.6L27.9,38.0L26.3,42.9L21.7,42.9L20.1,38.0L16.9,36.6L12.3,38.9L9.1,35.7L11.4,31.1L10.0,27.9L5.1,26.3L5.1,21.7L10.0,20.1L11.4,16.9L9.1,12.3L12.3,9.1L16.9,11.4L20.1,10.0L21.7,5.1L26.3,5.1L27.9,10.0L31.1,11.4L35.7,9.1L38.9,12.3L36.6,16.9L38.0,20.1Z", S60Fill.Vertical(Color(0xFFF4F6F8), Color(0xFF8C97A5)), Color(0xFF59636F), 1f, 0f, 0f),
        L("M16,24A8,8 0 1 0 32,24A8,8 0 1 0 16,24Z", S60Fill.Vertical(Color(0xFF7FC4FF), Color(0xFF1F5FCF)), Color(0xFF123F8F), 1f, 0f, 0f),
        L("M14,12Q24,6 34,12Q24,15 14,12Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "usage" to listOf(
        L("M9,6H39A4,4 0 0 1 43,10V38A4,4 0 0 1 39,42H9A4,4 0 0 1 5,38V10A4,4 0 0 1 9,6Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M9,6H39A4,4 0 0 1 43,10V38A4,4 0 0 1 39,42H9A4,4 0 0 1 5,38V10A4,4 0 0 1 9,6Z", S60Fill.Vertical(Color(0xFFFFFFFF), Color(0xFFDCE3EC)), Color(0xFF8A96A8), 1f, 0f, 0f),
        L("M11,26H15A1,1 0 0 1 16,27V37A1,1 0 0 1 15,38H11A1,1 0 0 1 10,37V27A1,1 0 0 1 11,26Z", S60Fill.Vertical(Color(0xFF8BE36A), Color(0xFF27962C)), null, 0f, 0f, 0f),
        L("M20,18H24A1,1 0 0 1 25,19V37A1,1 0 0 1 24,38H20A1,1 0 0 1 19,37V19A1,1 0 0 1 20,18Z", S60Fill.Vertical(Color(0xFF7FC4FF), Color(0xFF1F5FCF)), null, 0f, 0f, 0f),
        L("M29,12H33A1,1 0 0 1 34,13V37A1,1 0 0 1 33,38H29A1,1 0 0 1 28,37V13A1,1 0 0 1 29,12Z", S60Fill.Vertical(Color(0xFFFFB34D), Color(0xFFE0700C)), null, 0f, 0f, 0f),
        L("M9,38.5H39", null, Color(0xFF59636F), 1.2f, 0f, 0f)
    ),
    "card" to listOf(
        L("M9,13H39A5,5 0 0 1 44,18V36A5,5 0 0 1 39,41H9A5,5 0 0 1 4,36V18A5,5 0 0 1 9,13Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M10,7H32A2,2 0 0 1 34,9V21A2,2 0 0 1 32,23H10A2,2 0 0 1 8,21V9A2,2 0 0 1 10,7Z", S60Fill.Vertical(Color(0xFF7FC4FF), Color(0xFF1F5FCF)), null, 0f, 0f, 0f),
        L("M9,13H39A5,5 0 0 1 44,18V36A5,5 0 0 1 39,41H9A5,5 0 0 1 4,36V18A5,5 0 0 1 9,13Z", S60Fill.Vertical(Color(0xFFB0703A), Color(0xFF6E3F18)), Color(0xFF4A2A0E), 1f, 0f, 0f),
        L("M33,22H41A3,3 0 0 1 44,25V29A3,3 0 0 1 41,32H33A3,3 0 0 1 30,29V25A3,3 0 0 1 33,22Z", S60Fill.Vertical(Color(0xFF8A5326), Color(0xFF5A3212)), null, 0f, 0f, 0f),
        L("M34,27A2,2 0 1 0 38,27A2,2 0 1 0 34,27Z", S60Fill.Solid(Color(0xFFF2C14A)), null, 0f, 0f, 0f),
        L("M7,15H41V20Q24,24 7,20Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "calc" to listOf(
        L("M13,4H35A4,4 0 0 1 39,8V40A4,4 0 0 1 35,44H13A4,4 0 0 1 9,40V8A4,4 0 0 1 13,4Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M13,4H35A4,4 0 0 1 39,8V40A4,4 0 0 1 35,44H13A4,4 0 0 1 9,40V8A4,4 0 0 1 13,4Z", S60Fill.Vertical(Color(0xFF59636F), Color(0xFF262C33)), Color(0xFF14181C), 1f, 0f, 0f),
        L("M14,8H34A2,2 0 0 1 36,10V15A2,2 0 0 1 34,17H14A2,2 0 0 1 12,15V10A2,2 0 0 1 14,8Z", S60Fill.Vertical(Color(0xFFD9F0C8), Color(0xFFA9CF8C)), null, 0f, 0f, 0f),
        L("M13.2,21.0H17.3A1.2,1.2 0 0 1 18.5,22.2V25.3A1.2,1.2 0 0 1 17.3,26.5H13.2A1.2,1.2 0 0 1 12.0,25.3V22.2A1.2,1.2 0 0 1 13.2,21.0Z", S60Fill.Vertical(Color(0xFFF4F6F8), Color(0xFFA7B0BC)), null, 0f, 0f, 0f),
        L("M21.7,21.0H25.8A1.2,1.2 0 0 1 27.0,22.2V25.3A1.2,1.2 0 0 1 25.8,26.5H21.7A1.2,1.2 0 0 1 20.5,25.3V22.2A1.2,1.2 0 0 1 21.7,21.0Z", S60Fill.Vertical(Color(0xFFF4F6F8), Color(0xFFA7B0BC)), null, 0f, 0f, 0f),
        L("M30.2,21.0H34.3A1.2,1.2 0 0 1 35.5,22.2V25.3A1.2,1.2 0 0 1 34.3,26.5H30.2A1.2,1.2 0 0 1 29.0,25.3V22.2A1.2,1.2 0 0 1 30.2,21.0Z", S60Fill.Vertical(Color(0xFFFFB34D), Color(0xFFE0700C)), null, 0f, 0f, 0f),
        L("M13.2,28.3H17.3A1.2,1.2 0 0 1 18.5,29.5V32.599999999999994A1.2,1.2 0 0 1 17.3,33.8H13.2A1.2,1.2 0 0 1 12.0,32.599999999999994V29.5A1.2,1.2 0 0 1 13.2,28.3Z", S60Fill.Vertical(Color(0xFFF4F6F8), Color(0xFFA7B0BC)), null, 0f, 0f, 0f),
        L("M21.7,28.3H25.8A1.2,1.2 0 0 1 27.0,29.5V32.599999999999994A1.2,1.2 0 0 1 25.8,33.8H21.7A1.2,1.2 0 0 1 20.5,32.599999999999994V29.5A1.2,1.2 0 0 1 21.7,28.3Z", S60Fill.Vertical(Color(0xFFF4F6F8), Color(0xFFA7B0BC)), null, 0f, 0f, 0f),
        L("M30.2,28.3H34.3A1.2,1.2 0 0 1 35.5,29.5V32.599999999999994A1.2,1.2 0 0 1 34.3,33.8H30.2A1.2,1.2 0 0 1 29.0,32.599999999999994V29.5A1.2,1.2 0 0 1 30.2,28.3Z", S60Fill.Vertical(Color(0xFFFFB34D), Color(0xFFE0700C)), null, 0f, 0f, 0f),
        L("M13.2,35.6H17.3A1.2,1.2 0 0 1 18.5,36.800000000000004V39.9A1.2,1.2 0 0 1 17.3,41.1H13.2A1.2,1.2 0 0 1 12.0,39.9V36.800000000000004A1.2,1.2 0 0 1 13.2,35.6Z", S60Fill.Vertical(Color(0xFFF4F6F8), Color(0xFFA7B0BC)), null, 0f, 0f, 0f),
        L("M21.7,35.6H25.8A1.2,1.2 0 0 1 27.0,36.800000000000004V39.9A1.2,1.2 0 0 1 25.8,41.1H21.7A1.2,1.2 0 0 1 20.5,39.9V36.800000000000004A1.2,1.2 0 0 1 21.7,35.6Z", S60Fill.Vertical(Color(0xFFF4F6F8), Color(0xFFA7B0BC)), null, 0f, 0f, 0f),
        L("M30.2,35.6H34.3A1.2,1.2 0 0 1 35.5,36.800000000000004V39.9A1.2,1.2 0 0 1 34.3,41.1H30.2A1.2,1.2 0 0 1 29.0,39.9V36.800000000000004A1.2,1.2 0 0 1 30.2,35.6Z", S60Fill.Vertical(Color(0xFFFFB34D), Color(0xFFE0700C)), null, 0f, 0f, 0f)
    ),
    "clock" to listOf(
        L("M5,24A19,19 0 1 0 43,24A19,19 0 1 0 5,24Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M5,24A19,19 0 1 0 43,24A19,19 0 1 0 5,24Z", S60Fill.Vertical(Color(0xFF7FC4FF), Color(0xFF1F5FCF)), Color(0xFF123F8F), 1f, 0f, 0f),
        L("M9,24A15,15 0 1 0 39,24A15,15 0 1 0 9,24Z", S60Fill.Vertical(Color(0xFFFFFFFF), Color(0xFFE4E8EE)), null, 0f, 0f, 0f),
        L("M24,24V13", null, Color(0xFF2A3550), 2.4f, 0f, 0f),
        L("M24,24L31,28", null, Color(0xFF2A3550), 2.2f, 0f, 0f),
        L("M24,26V11", null, Color(0xFFD4281C), 1f, 0f, 0f),
        L("M22,24A2,2 0 1 0 26,24A2,2 0 1 0 22,24Z", S60Fill.Solid(Color(0xFF2A3550)), null, 0f, 0f, 0f),
        L("M12,15Q24,5 36,15Q24,12 12,15Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "battery" to listOf(
        L("M12,12H34A4,4 0 0 1 38,16V30A4,4 0 0 1 34,34H12A4,4 0 0 1 8,30V16A4,4 0 0 1 12,12Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M12,12H34A4,4 0 0 1 38,16V30A4,4 0 0 1 34,34H12A4,4 0 0 1 8,30V16A4,4 0 0 1 12,12Z", S60Fill.Vertical(Color(0xFFF4F6F8), Color(0xFFA7B0BC)), Color(0xFF59636F), 1.2f, 0f, 0f),
        L("M39.5,19H40.5A1.5,1.5 0 0 1 42,20.5V25.5A1.5,1.5 0 0 1 40.5,27H39.5A1.5,1.5 0 0 1 38,25.5V20.5A1.5,1.5 0 0 1 39.5,19Z", S60Fill.Solid(Color(0xFF59636F)), null, 0f, 0f, 0f),
        L("M13,15H33A2,2 0 0 1 35,17V29A2,2 0 0 1 33,31H13A2,2 0 0 1 11,29V17A2,2 0 0 1 13,15Z", S60Fill.Vertical(Color(0xFF8BE36A), Color(0xFF27962C)), null, 0f, 0f, 0f),
        L("M25,16L18,24H23L21,30L28,22H23Z", S60Fill.Solid(Color(0xFFFFFFFF)), null, 0f, 0f, 0f),
        L("M10,14H36V19Q23,22 10,19Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "call" to listOf(
        L("M5,24A19,19 0 1 0 43,24A19,19 0 1 0 5,24Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M5,24A19,19 0 1 0 43,24A19,19 0 1 0 5,24Z", S60Fill.Vertical(Color(0xFF8BE36A), Color(0xFF27962C)), Color(0xFF1B6B1F), 1f, 0f, 0f),
        L("M15,14C17,12 19,12 20,14L22,18C23,20 22,21 21,22C22,25 24,27 27,28C28,27 29,26 31,27L35,29C37,30 37,32 35,34C33,36 29,37 24,34C19,31 17,28 15,24C13,20 13,16 15,14Z", S60Fill.Solid(Color(0xFFFFFFFF)), null, 0f, 0f, 0f),
        L("M11,17Q24,5 37,17Q24,14 11,17Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "camera" to listOf(
        L("M9,13H39A5,5 0 0 1 44,18V35A5,5 0 0 1 39,40H9A5,5 0 0 1 4,35V18A5,5 0 0 1 9,13Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M17,8H27A2,2 0 0 1 29,10V14A2,2 0 0 1 27,16H17A2,2 0 0 1 15,14V10A2,2 0 0 1 17,8Z", S60Fill.Vertical(Color(0xFF59636F), Color(0xFF262C33)), null, 0f, 0f, 0f),
        L("M9,13H39A5,5 0 0 1 44,18V35A5,5 0 0 1 39,40H9A5,5 0 0 1 4,35V18A5,5 0 0 1 9,13Z", S60Fill.Vertical(Color(0xFF59636F), Color(0xFF262C33)), Color(0xFF14181C), 1f, 0f, 0f),
        L("M14.5,27A9.5,9.5 0 1 0 33.5,27A9.5,9.5 0 1 0 14.5,27Z", S60Fill.Vertical(Color(0xFFD9DEE5), Color(0xFF7A8594)), null, 0f, 0f, 0f),
        L("M17,27A7,7 0 1 0 31,27A7,7 0 1 0 17,27Z", S60Fill.Vertical(Color(0xFF5AB0FF), Color(0xFF123F8F)), null, 0f, 0f, 0f),
        L("M19.3,24.5A2.2,2.2 0 1 0 23.7,24.5A2.2,2.2 0 1 0 19.3,24.5Z", S60Fill.Solid(Color(0xCCFFFFFF)), null, 0f, 0f, 0f),
        L("M36,16H39A1,1 0 0 1 40,17V18A1,1 0 0 1 39,19H36A1,1 0 0 1 35,18V17A1,1 0 0 1 36,16Z", S60Fill.Solid(Color(0xFFFFB34D)), null, 0f, 0f, 0f),
        L("M7,15H41V19Q24,23 7,19Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    ),
    "desktop" to listOf(
        L("M7,7H41A3,3 0 0 1 44,10V32A3,3 0 0 1 41,35H7A3,3 0 0 1 4,32V10A3,3 0 0 1 7,7Z", S60Fill.Solid(Color(0x3F000000)), null, 0f, 1.2f, 1.6f),
        L("M7,7H41A3,3 0 0 1 44,10V32A3,3 0 0 1 41,35H7A3,3 0 0 1 4,32V10A3,3 0 0 1 7,7Z", S60Fill.Vertical(Color(0xFF59636F), Color(0xFF262C33)), Color(0xFF14181C), 1f, 0f, 0f),
        L("M8.5,10H39.5A1.5,1.5 0 0 1 41,11.5V30.5A1.5,1.5 0 0 1 39.5,32H8.5A1.5,1.5 0 0 1 7,30.5V11.5A1.5,1.5 0 0 1 8.5,10Z", S60Fill.Vertical(Color(0xFF7FC4FF), Color(0xFF1F5FCF)), null, 0f, 0f, 0f),
        L("M20,35H28A1,1 0 0 1 29,36V38A1,1 0 0 1 28,39H20A1,1 0 0 1 19,38V36A1,1 0 0 1 20,35Z", S60Fill.Solid(Color(0xFF59636F)), null, 0f, 0f, 0f),
        L("M14.5,39H33.5A1.5,1.5 0 0 1 35,40.5V40.5A1.5,1.5 0 0 1 33.5,42H14.5A1.5,1.5 0 0 1 13,40.5V40.5A1.5,1.5 0 0 1 14.5,39Z", S60Fill.Vertical(Color(0xFFA7B0BC), Color(0xFF59636F)), null, 0f, 0f, 0f),
        L("M7,10H41V17Q24,21 7,17Z", S60Fill.Vertical(Color(0x99FFFFFF), Color(0x00FFFFFF)), null, 0f, 0f, 0f)
    )
    )

    fun has(name: String) = name in icons

    /** Renders an icon into a bitmap (used to replace app icons for built-in phone functions). */
    fun bitmap(name: String, px: Int): android.graphics.Bitmap? {
        val layers = icons[name] ?: return null
        val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val k = px / 48f
        c.scale(k, k)
        layers.forEach { l ->
            c.save(); c.translate(l.dx, l.dy)
            val p = l.path.asAndroidPath()
            when (val f = l.fill) {
                is S60Fill.Solid -> c.drawPath(p, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = f.c.toArgb() })
                is S60Fill.Vertical -> {
                    val b = android.graphics.RectF(); p.computeBounds(b, true)
                    c.drawPath(p, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        shader = android.graphics.LinearGradient(0f, b.top, 0f, b.bottom, f.top.toArgb(), f.bottom.toArgb(), android.graphics.Shader.TileMode.CLAMP)
                    })
                }
                null -> {}
            }
            l.stroke?.let { s ->
                c.drawPath(p, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = s.toArgb(); style = android.graphics.Paint.Style.STROKE; strokeWidth = l.strokeW
                    strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
                })
            }
            c.restore()
        }
        return bmp
    }

    @Composable
    fun Icon(name: String, modifier: Modifier = Modifier) {
        val layers = remember(name) { icons[name] } ?: return
        Canvas(modifier.size(32.dp)) {
            val k = minOf(size.width, size.height) / 48f
            translate((size.width - 48f * k) / 2f, (size.height - 48f * k) / 2f) {
                scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    layers.forEach { l ->
                        translate(l.dx, l.dy) {
                            when (val f = l.fill) {
                                is S60Fill.Solid -> drawPath(l.path, f.c)
                                is S60Fill.Vertical -> {
                                    val b = l.path.getBounds()
                                    drawPath(l.path, Brush.verticalGradient(listOf(f.top, f.bottom), startY = b.top, endY = b.bottom))
                                }
                                null -> {}
                            }
                            l.stroke?.let { drawPath(l.path, it, style = Stroke(width = l.strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                        }
                    }
                }
            }
        }
    }
}
