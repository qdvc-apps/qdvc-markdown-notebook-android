package qdvc.markdownnotebook.android.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import qdvc.markdownnotebook.android.app.util.SyntaxColors

@Composable
fun rememberSyntaxColors(): SyntaxColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        SyntaxColors(
            base = scheme.onSurface,
            heading = scheme.primary,
            emphasis = scheme.onSurface,
            code = scheme.secondary,
            link = scheme.primary,
            listMarker = scheme.secondary,
            blockquote = scheme.onSurfaceVariant,
            punctuation = scheme.onSurfaceVariant,
        )
    }
}
