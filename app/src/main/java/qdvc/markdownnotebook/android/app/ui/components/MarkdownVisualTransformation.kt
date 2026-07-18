package qdvc.markdownnotebook.android.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import qdvc.markdownnotebook.android.app.util.MarkdownHighlighter
import qdvc.markdownnotebook.android.app.util.SyntaxColors

/**
 * Recolours the editor text with markdown syntax highlighting without changing
 * any characters, so the offset mapping is identity. Font size is untouched;
 * only colour/weight and paragraph indentation change. The base [fontFamily]
 * matches the user's choice, and [fontSizeSp] sizes the hanging indent applied
 * to wrapped list lines (identity mapping is preserved because no characters
 * are added or removed).
 */
class MarkdownVisualTransformation(
    private val colors: SyntaxColors,
    private val fontFamily: FontFamily,
    private val fontSizeSp: Float,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = MarkdownHighlighter.highlight(
            text.text,
            colors,
            fontFamily,
            render = false,
            hangingIndentFontSizeSp = fontSizeSp,
        )
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
