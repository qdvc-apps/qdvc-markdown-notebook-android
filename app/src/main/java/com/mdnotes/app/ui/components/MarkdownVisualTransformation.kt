package com.mdnotes.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.mdnotes.app.util.MarkdownHighlighter
import com.mdnotes.app.util.SyntaxColors

/**
 * Recolours the editor text with markdown syntax highlighting without changing
 * any characters, so the offset mapping is identity. Font size is untouched;
 * only colour/weight change.
 */
class MarkdownVisualTransformation(
    private val colors: SyntaxColors,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = MarkdownHighlighter.highlight(text.text, colors)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
