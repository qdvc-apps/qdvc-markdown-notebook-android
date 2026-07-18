package qdvc.markdownnotebook.android.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import qdvc.markdownnotebook.android.app.model.AppFont
import qdvc.markdownnotebook.android.app.model.DarkStyle
import qdvc.markdownnotebook.android.app.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Sage,
    onPrimary = Color_White,
    secondary = Clay,
    onSecondary = Color_White,
    background = PaperBg,
    onBackground = PaperOnBg,
    surface = PaperSurface,
    onSurface = PaperOnBg,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperOnSurfaceVariant,
    outline = PaperOutline,
    error = DangerRed,
)

private val RegularDarkColors = darkColorScheme(
    primary = SageDark,
    onPrimary = Color_Black,
    secondary = ClayDark,
    onSecondary = Color_Black,
    background = DarkBg,
    onBackground = DarkOnBg,
    surface = DarkSurface,
    onSurface = DarkOnBg,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DangerRedDark,
)

private val PureBlackColors = darkColorScheme(
    primary = SageDark,
    onPrimary = Color_Black,
    secondary = ClayDark,
    onSecondary = Color_Black,
    background = BlackBg,
    onBackground = BlackOnBg,
    surface = BlackSurface,
    onSurface = BlackOnBg,
    surfaceVariant = BlackSurfaceVariant,
    onSurfaceVariant = BlackOnSurfaceVariant,
    outline = BlackOutline,
    error = DangerRedDark,
)

private val AppTypography = Typography()

@Composable
fun MarkdownNotesTheme(
    themeMode: ThemeMode,
    darkStyle: DarkStyle,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.AUTOMATIC -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colors = when {
        !useDark -> LightColors
        darkStyle == DarkStyle.PURE_BLACK -> PureBlackColors
        else -> RegularDarkColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}

/** Maps a user font choice to a concrete system font family. */
fun AppFont.toFontFamily(): FontFamily = when (this) {
    AppFont.MONOSPACE -> FontFamily.Monospace
    AppFont.SANS_SERIF -> FontFamily.SansSerif
    AppFont.SERIF -> FontFamily.Serif
    AppFont.CURSIVE -> FontFamily.Cursive
}
