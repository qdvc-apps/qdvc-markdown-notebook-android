package qdvc.markdownnotebook.android.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import qdvc.markdownnotebook.android.app.data.ThemeRepository
import qdvc.markdownnotebook.android.app.model.ThemeMode
import qdvc.markdownnotebook.android.app.model.ThemeSpec

private val AppTypography = Typography()

/**
 * Applies the app theme. The visible colours come from a [ThemeSpec] loaded
 * from JSON (see assets/themes/): [lightTheme] is used in light mode and
 * [darkTheme] in dark mode. Nulls fall back to Material defaults so the app
 * still renders if theme assets are somehow missing.
 */
@Composable
fun MarkdownNotesTheme(
    themeMode: ThemeMode,
    lightTheme: ThemeSpec?,
    darkTheme: ThemeSpec?,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.AUTOMATIC -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val spec = if (useDark) darkTheme else lightTheme
    val colors = when {
        spec != null -> ThemeRepository.colorScheme(spec)
        useDark -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
