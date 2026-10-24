package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.AppTheme
import com.example.LauncherState
import androidx.compose.ui.graphics.Color

private val DefaultScheme = lightColorScheme(
    background = DefaultBackground,
    surface = DefaultSurface,
    onBackground = DefaultText,
    onSurface = DefaultText,
    primary = DefaultAccent,
    secondary = DefaultHighlight
)

private val GreyScheme = lightColorScheme(
    background = GreyBackground,
    surface = GreySurface,
    onBackground = GreyText,
    onSurface = GreyText,
    primary = GreyAccent,
    secondary = GreyHighlight
)

private val BlueScheme = lightColorScheme(
    background = BlueBackground,
    surface = BlueSurface,
    onBackground = BlueText,
    onSurface = BlueText,
    primary = BlueAccent,
    secondary = BlueHighlight
)

private val RedScheme = lightColorScheme(
    background = RedBackground,
    surface = RedSurface,
    onBackground = RedText,
    onSurface = RedText,
    primary = RedAccent,
    secondary = RedHighlight
)

private val GreenScheme = lightColorScheme(
    background = GreenBackground,
    surface = GreenSurface,
    onBackground = GreenText,
    onSurface = GreenText,
    primary = GreenAccent,
    secondary = GreenHighlight
)

private val YellowScheme = lightColorScheme(
    background = YellowBackground,
    surface = YellowSurface,
    onBackground = YellowText,
    onSurface = YellowText,
    primary = YellowAccent,
    secondary = YellowHighlight
)

private val PinkScheme = lightColorScheme(
    background = PinkBackground,
    surface = PinkSurface,
    onBackground = PinkText,
    onSurface = PinkText,
    primary = PinkAccent,
    secondary = PinkHighlight
)

/** Classic S60 "sky" look: navy bars, blue-to-turquoise backdrop with soft light waves, yellow selection. */
private val S60Scheme = lightColorScheme(
    background = Color(0xFF1E7FD8),
    surface = Color(0xFF111C5E),
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color(0xFF15367F),
    secondary = Color(0xFFF0C93A)
)

/** Classic 1995 desktop: teal background, navy title bars, grey 3-D chrome. */
private val Win95Scheme = lightColorScheme(
    background = Color(0xFF008080),
    surface = Color(0xFF000080),
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color(0xFFC0C0C0),
    secondary = Color(0xFFC0C0C0)
)

@Composable
fun MyApplicationTheme(
    state: LauncherState,
    content: @Composable () -> Unit
) {
    val baseScheme = when (state.currentTheme) {
        AppTheme.DEFAULT -> DefaultScheme
        AppTheme.GREY -> GreyScheme
        AppTheme.BLUE -> BlueScheme
        AppTheme.RED -> RedScheme
        AppTheme.GREEN -> GreenScheme
        AppTheme.YELLOW -> YellowScheme
        AppTheme.PINK -> PinkScheme
        AppTheme.WIN95 -> Win95Scheme
        AppTheme.S60 -> S60Scheme
        AppTheme.CUSTOM -> lightColorScheme(
            background = state.customBgColor?.let { Color(it) } ?: DefaultBackground,
            surface = state.customBarColor?.let { Color(it) } ?: state.customBgColor?.let { Color(it).copy(alpha=0.7f) } ?: DefaultSurface,
            onBackground = state.customTextColor?.let { Color(it) } ?: DefaultText,
            onSurface = state.customTextColor?.let { Color(it) } ?: DefaultText,
            primary = state.customAccentColor?.let { Color(it) } ?: DefaultAccent,
            secondary = state.customHighlightColor?.let { Color(it) } ?: DefaultHighlight
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (state.showWallpaper) android.graphics.Color.TRANSPARENT else baseScheme.background.toArgb()
            window.navigationBarColor = if (state.showWallpaper) android.graphics.Color.TRANSPARENT else baseScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = baseScheme,
        typography = Typography,
        content = content
    )
}
