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
 * only colour/weight change. The base [fontFamily] matches the user's choice.
 *
 * Note: hanging indents (per-line ParagraphStyle) are intentionally NOT applied
 * here. In Compose a ParagraphStyle separates paragraphs "as if it had line
 * feeds at the beginning and end", which is incompatible with also keeping the
 * literal '\n' characters the editor needs for a 1:1 identity offset mapping —
 * doing both double-spaces every line. The read-only View tab, which needs no
 * offset mapping, is where hanging indents are applied instead.
 */
class MarkdownVisualTransformation(
    private val colors: SyntaxColors,
    private val fontFamily: FontFamily,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = MarkdownHighlighter.highlight(
            text.text,
            colors,
            fontFamily,
            render = false,
            hangingIndentFontSizeSp = null,
        )
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
