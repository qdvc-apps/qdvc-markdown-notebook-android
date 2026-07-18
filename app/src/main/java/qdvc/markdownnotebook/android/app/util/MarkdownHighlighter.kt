package qdvc.markdownnotebook.android.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Colours the tokens that make up markdown syntax. Font size never changes:
 * structure is communicated only through colour and weight, per the spec.
 * Every run is forced to [fontFamily] so bold/italic runs cannot fall back to
 * a different face; the caller supplies whichever family the user has chosen.
 */
data class SyntaxColors(
    val base: Color,
    val heading: Color,
    val emphasis: Color,
    val code: Color,
    val link: Color,
    val listMarker: Color,
    val blockquote: Color,
    val punctuation: Color,
)

object MarkdownHighlighter {

    // Approximate width of one character as a fraction of the font size. Exact
    // for monospace fonts (the default); a reasonable estimate for others. Used
    // only to align wrapped list lines under their content in render mode.
    private const val CHAR_ADVANCE_EM = 0.6f

    /**
     * @param render when true (the View tab), inline emphasis is *rendered*:
     * bold text is bold, italics slant, strikethrough is struck through, and the
     * surrounding markers are dimmed. When false (the Edit tab), markers keep
     * full colour so the raw source stays clear for editing.
     * @param hangingIndentFontSizeSp when non-null (render mode), wrapped lines
     * of a list item are indented to align under the item's text, using this
     * font size to size the indent. Null disables hanging indents (Edit tab),
     * so raw source stays character-accurate for editing.
     */
    fun highlight(
        text: String,
        colors: SyntaxColors,
        fontFamily: FontFamily,
        render: Boolean = false,
        hangingIndentFontSizeSp: Float? = null,
    ): AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = fontFamily)) {
            val lines = text.split("\n")
            if (hangingIndentFontSizeSp != null) {
                // Each source line becomes its own paragraph so continuation
                // (wrapped) lines can be indented independently. Paragraph
                // boundaries provide the line breaks, so we don't append "\n".
                lines.forEach { line ->
                    appendLine(line, colors, render, hangingIndentFontSizeSp)
                }
            } else {
                lines.forEachIndexed { index, line ->
                    appendLine(line, colors, render, null)
                    if (index != lines.lastIndex) append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.appendLine(
        line: String,
        c: SyntaxColors,
        render: Boolean,
        hangingFontSizeSp: Float?,
    ) {
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length

        // Work out how far wrapped continuation lines should be indented so they
        // align under the content of a list item (a "hanging indent").
        val ulMatch = Regex("^([-*+])\\s+").find(trimmed)
        val olMatch = Regex("^(\\d+[.)])\\s+").find(trimmed)
        val markerWidth = ulMatch?.value?.length ?: olMatch?.value?.length ?: 0
        val hangingChars = indent + markerWidth

        // In hanging mode every source line is its own paragraph (so wrapped
        // lines can be indented). List items get a rest-line indent that aligns
        // continuation lines under the item text; other lines get zero.
        if (hangingFontSizeSp != null) {
            val restIndent: TextUnit =
                if (hangingChars > 0) (hangingFontSizeSp * CHAR_ADVANCE_EM * hangingChars).sp
                else 0.sp
            pushStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = restIndent)))
        }

        appendLineBody(line, trimmed, indent, c, render, ulMatch, olMatch)

        if (hangingFontSizeSp != null) pop()
    }

    private fun AnnotatedString.Builder.appendLineBody(
        line: String,
        trimmed: String,
        indent: Int,
        c: SyntaxColors,
        render: Boolean,
        ulMatch: MatchResult?,
        olMatch: MatchResult?,
    ) {
        if (indent > 0) append(line.substring(0, indent))

        // ATX headings: # .. ######
        val headingMatch = Regex("^(#{1,6})\\s+(.*)$").find(trimmed)
        if (headingMatch != null) {
            withStyle(SpanStyle(color = c.punctuation)) {
                append(headingMatch.groupValues[1] + " ")
            }
            withStyle(SpanStyle(color = c.heading, fontWeight = FontWeight.Bold)) {
                append(headingMatch.groupValues[2])
            }
            return
        }

        // Blockquote
        if (trimmed.startsWith(">")) {
            withStyle(SpanStyle(color = c.blockquote, fontStyle = FontStyle.Italic)) {
                append(trimmed)
            }
            return
        }

        // Unordered / ordered list markers
        if (ulMatch != null) {
            withStyle(SpanStyle(color = c.listMarker, fontWeight = FontWeight.Bold)) {
                append(ulMatch.value)
            }
            appendInline(trimmed.substring(ulMatch.value.length), c, render)
            return
        }
        if (olMatch != null) {
            withStyle(SpanStyle(color = c.listMarker, fontWeight = FontWeight.Bold)) {
                append(olMatch.value)
            }
            appendInline(trimmed.substring(olMatch.value.length), c, render)
            return
        }

        // Fenced code fence line
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            withStyle(SpanStyle(color = c.code)) { append(trimmed) }
            return
        }

        appendInline(trimmed, c, render)
    }

    // Handles inline spans: `code`, **bold**, *italic*/_italic_, ~~strike~~,
    // [text](url). In render mode the markers are dimmed and the inner text
    // carries the real style; otherwise markers keep full colour.
    private fun AnnotatedString.Builder.appendInline(
        text: String,
        c: SyntaxColors,
        render: Boolean,
    ) {
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            when {
                ch == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(color = c.code)) { append(text.substring(i, end + 1)) }
                        i = end + 1
                    } else {
                        withStyle(SpanStyle(color = c.base)) { append(ch) }; i++
                    }
                }
                ch == '~' && i + 1 < n && text[i + 1] == '~' -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i) {
                        val inner = text.substring(i + 2, end)
                        if (render) {
                            marker(c, "~~")
                            withStyle(
                                SpanStyle(
                                    color = c.base,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            ) { append(inner) }
                            marker(c, "~~")
                        } else {
                            withStyle(
                                SpanStyle(
                                    color = c.emphasis,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            ) { append(text.substring(i, end + 2)) }
                        }
                        i = end + 2
                    } else { withStyle(SpanStyle(color = c.base)) { append(ch) }; i++ }
                }
                ch == '*' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        val inner = text.substring(i + 2, end)
                        if (render) {
                            marker(c, "**")
                            withStyle(SpanStyle(color = c.base, fontWeight = FontWeight.Bold)) {
                                append(inner)
                            }
                            marker(c, "**")
                        } else {
                            withStyle(SpanStyle(color = c.emphasis, fontWeight = FontWeight.Bold)) {
                                append(text.substring(i, end + 2))
                            }
                        }
                        i = end + 2
                    } else { withStyle(SpanStyle(color = c.base)) { append(ch) }; i++ }
                }
                (ch == '*' || ch == '_') -> {
                    val end = text.indexOf(ch, i + 1)
                    if (end > i) {
                        val inner = text.substring(i + 1, end)
                        if (render) {
                            marker(c, ch.toString())
                            withStyle(SpanStyle(color = c.base, fontStyle = FontStyle.Italic)) {
                                append(inner)
                            }
                            marker(c, ch.toString())
                        } else {
                            withStyle(SpanStyle(color = c.emphasis, fontStyle = FontStyle.Italic)) {
                                append(text.substring(i, end + 1))
                            }
                        }
                        i = end + 1
                    } else { withStyle(SpanStyle(color = c.base)) { append(ch) }; i++ }
                }
                ch == '[' -> {
                    val linkMatch = Regex("^\\[([^\\]]*)\\]\\(([^)]*)\\)").find(text.substring(i))
                    if (linkMatch != null) {
                        withStyle(SpanStyle(color = c.punctuation)) { append("[") }
                        withStyle(SpanStyle(color = c.link)) { append(linkMatch.groupValues[1]) }
                        withStyle(SpanStyle(color = c.punctuation)) { append("](") }
                        withStyle(SpanStyle(color = c.link)) { append(linkMatch.groupValues[2]) }
                        withStyle(SpanStyle(color = c.punctuation)) { append(")") }
                        i += linkMatch.value.length
                    } else { withStyle(SpanStyle(color = c.base)) { append(ch) }; i++ }
                }
                else -> {
                    withStyle(SpanStyle(color = c.base)) { append(ch) }
                    i++
                }
            }
        }
    }

    private fun AnnotatedString.Builder.marker(c: SyntaxColors, text: String) {
        withStyle(SpanStyle(color = c.punctuation)) { append(text) }
    }
}
