package com.warped.ui.chat.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.Unspecified,
    fontSize: Float? = null,
    fontStyle: FontStyle? = null
) {
    if (text.isBlank()) {
        Text(text, modifier = modifier, color = baseColor)
        return
    }

    val annotated = buildAnnotatedString {
        val lines = text.lines()
        var inCodeBlock = false
        var codeBlockContent = StringBuilder()

        val baseStyle = SpanStyle(
            fontStyle = fontStyle ?: androidx.compose.ui.text.font.FontStyle.Normal
        ).let { if (fontSize != null) it.copy(fontSize = fontSize.sp) else it }

        for (line in lines) {
            if (line.trimStart().startsWith("```")) {
                if (inCodeBlock) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        background = Color(0xFF1E1E1E)
                    )) {
                        append(codeBlockContent.toString().trimEnd())
                    }
                    append("\n")
                    codeBlockContent.clear()
                }
                inCodeBlock = !inCodeBlock
                continue
            }

            if (inCodeBlock) {
                codeBlockContent.append(line).append("\n")
                continue
            }

            parseInlineMarkdown(line, baseStyle)
            append("\n")
        }
    }

    Text(annotated, modifier = modifier, color = baseColor)
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.parseInlineMarkdown(line: String, baseStyle: SpanStyle) {
    val trimmed = line.trimStart()
    val indent = line.length - trimmed.length

    when {
        trimmed.startsWith("### ") -> {
            append(" ".repeat(indent))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
                append(trimmed.removePrefix("### "))
            }
        }
        trimmed.startsWith("## ") -> {
            append(" ".repeat(indent))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                append(trimmed.removePrefix("## "))
            }
        }
        trimmed.startsWith("# ") -> {
            append(" ".repeat(indent))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp)) {
                append(trimmed.removePrefix("# "))
            }
        }
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
            append(" ".repeat(indent))
            append("  •  ")
            parseInlineStyles(trimmed.removePrefix("- ").removePrefix("* "), baseStyle)
        }
        trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
            append(" ".repeat(indent))
            val num = trimmed.substringBefore(".")
            append("$num. ")
            parseInlineStyles(trimmed.substringAfter(". "), baseStyle)
        }
        else -> {
            append(" ".repeat(indent))
            parseInlineStyles(trimmed, baseStyle)
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.parseInlineStyles(text: String, baseStyle: SpanStyle) {
    var i = 0
    while (i < text.length) {
        when {
            i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(baseStyle.copy(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            text[i] == '*' -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(baseStyle.copy(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            text[i] == '`' -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, background = Color(0xFF2D2D2D))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}
