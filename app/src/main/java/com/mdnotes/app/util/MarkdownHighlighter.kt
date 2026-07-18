package com.mdnotes.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Colours the tokens that make up markdown syntax. Font size never changes:
 * structure is communicated only through colour and weight, per the spec.
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

    fun highlight(text: String, colors: SyntaxColors): AnnotatedString = buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            appendLine(line, colors)
            if (index != lines.lastIndex) append("\n")
        }
    }

    private fun AnnotatedString.Builder.appendLine(line: String, c: SyntaxColors) {
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
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
        val ulMatch = Regex("^([-*+])\\s+").find(trimmed)
        val olMatch = Regex("^(\\d+[.)])\\s+").find(trimmed)
        if (ulMatch != null) {
            withStyle(SpanStyle(color = c.listMarker, fontWeight = FontWeight.Bold)) {
                append(ulMatch.value)
            }
            appendInline(trimmed.substring(ulMatch.value.length), c)
            return
        }
        if (olMatch != null) {
            withStyle(SpanStyle(color = c.listMarker, fontWeight = FontWeight.Bold)) {
                append(olMatch.value)
            }
            appendInline(trimmed.substring(olMatch.value.length), c)
            return
        }

        // Fenced code fence line
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            withStyle(SpanStyle(color = c.code)) { append(trimmed) }
            return
        }

        appendInline(trimmed, c)
    }

    // Handles inline spans: `code`, **bold**, *italic*, [text](url).
    private fun AnnotatedString.Builder.appendInline(text: String, c: SyntaxColors) {
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
                ch == '*' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(color = c.emphasis, fontWeight = FontWeight.Bold)) {
                            append(text.substring(i, end + 2))
                        }
                        i = end + 2
                    } else { withStyle(SpanStyle(color = c.base)) { append(ch) }; i++ }
                }
                (ch == '*' || ch == '_') -> {
                    val end = text.indexOf(ch, i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(color = c.emphasis, fontStyle = FontStyle.Italic)) {
                            append(text.substring(i, end + 1))
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
}
