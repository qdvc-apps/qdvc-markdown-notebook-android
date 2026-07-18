package qdvc.markdownnotebook.android.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import qdvc.markdownnotebook.android.app.model.DarkStyle
import qdvc.markdownnotebook.android.app.model.LightStyle
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

private val EverforestLightColors = lightColorScheme(
    primary = EverforestLightPrimary,
    onPrimary = Color_White,
    secondary = EverforestLightSecondary,
    onSecondary = Color_White,
    background = EverforestLightBg,
    onBackground = EverforestLightOnBg,
    surface = EverforestLightSurface,
    onSurface = EverforestLightOnBg,
    surfaceVariant = EverforestLightSurfaceVariant,
    onSurfaceVariant = EverforestLightOnSurfaceVariant,
    outline = EverforestLightOutline,
    error = EverforestLightError,
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

private val EverforestDarkColors = darkColorScheme(
    primary = EverforestDarkPrimary,
    onPrimary = Color_Black,
    secondary = EverforestDarkSecondary,
    onSecondary = Color_Black,
    background = EverforestDarkBg,
    onBackground = EverforestDarkOnBg,
    surface = EverforestDarkSurface,
    onSurface = EverforestDarkOnBg,
    surfaceVariant = EverforestDarkSurfaceVariant,
    onSurfaceVariant = EverforestDarkOnSurfaceVariant,
    outline = EverforestDarkOutline,
    error = EverforestDarkError,
)

private val AppTypography = Typography()

@Composable
fun MarkdownNotesTheme(
    themeMode: ThemeMode,
    lightStyle: LightStyle,
    darkStyle: DarkStyle,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.AUTOMATIC -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colors = if (useDark) {
        when (darkStyle) {
            DarkStyle.PURE_BLACK -> PureBlackColors
            DarkStyle.EVERFOREST -> EverforestDarkColors
            DarkStyle.REGULAR -> RegularDarkColors
        }
    } else {
        when (lightStyle) {
            LightStyle.EVERFOREST -> EverforestLightColors
            LightStyle.REGULAR -> LightColors
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
