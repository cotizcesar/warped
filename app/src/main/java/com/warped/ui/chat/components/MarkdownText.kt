package com.warped.ui.chat.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warped.domain.highlighting.LanguageDetector
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.model.TokenType
import com.warped.domain.model.MarkdownBlock
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// ─────────────────────────────────────────────────────────────
// Hilt EntryPoint — provides LanguageDetector to composables
// ─────────────────────────────────────────────────────────────

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MarkdownEntryPoint {
    fun languageDetector(): LanguageDetector
}

// ─────────────────────────────────────────────────────────────
// Public composable
// ─────────────────────────────────────────────────────────────

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.Unspecified,
    fontSize: Float? = null,
    fontStyle: FontStyle? = null,
    maxLines: Int = Int.MAX_VALUE,
    codeTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    languageDetector: LanguageDetector? = null,
    isStreaming: Boolean = false,
    codeFontScale: Float = 1.0f,
) {
    if (text.isBlank()) {
        Text(text, modifier = modifier, color = baseColor, maxLines = maxLines)
        return
    }

    val appContext = LocalContext.current.applicationContext
    val detector = languageDetector ?: remember {
        EntryPoints.get(appContext, MarkdownEntryPoint::class.java).languageDetector()
    }

    val blocks = remember(text, detector) {
        parseMarkdown(text, detector)
    }

    val isDark = isSystemInDarkTheme()
    val variant = if (isDark) codeTheme.darkVariant else codeTheme.lightVariant
    val bgCodeColor = Color(variant.getValue(TokenType.BACKGROUND).argb)
    val inlineCodeBgColor = bgCodeColor.copy(alpha = 0.25f)

    val baseStyle = SpanStyle(
        fontStyle = fontStyle ?: FontStyle.Normal,
    ).let { if (fontSize != null) it.copy(fontSize = fontSize.sp) else it }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.TextBlock -> {
                    val annotated = parseInlineMarkdownAsAnnotatedString(
                        text = block.text,
                        baseStyle = baseStyle,
                        inlineCodeBgColor = inlineCodeBgColor,
                    )
                    Text(annotated, color = baseColor)
                }

                is MarkdownBlock.HeaderBlock -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = block.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = style.fontSize,
                    )
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlock(
                        language = block.language,
                        code = block.code,
                        syntaxTheme = codeTheme,
                        codeFontScale = codeFontScale,
                        isStreaming = isStreaming,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is MarkdownBlock.InlineCodeBlock -> {
                    // Standalone inline code block (entire line is `code`)
                    Surface(
                        color = inlineCodeBgColor,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                is MarkdownBlock.ListItemBlock -> {
                    Column {
                        block.items.forEachIndexed { i, item ->
                            val prefix = if (block.ordered) "${i + 1}. " else "\u2022  "
                            Text("$prefix$item")
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Inline markdown parser
// ─────────────────────────────────────────────────────────────

/**
 * Parses inline markdown styling markers (**bold**, *italic*, `code`)
 * and returns an [AnnotatedString] with appropriate [SpanStyle] spans.
 *
 * Inline code background color is derived from the active [SyntaxTheme]'s
 * BACKGROUND token at 25% alpha, passed in by the caller.
 */
internal fun parseInlineMarkdownAsAnnotatedString(
    text: String,
    baseStyle: SpanStyle,
    inlineCodeBgColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        parseInlineStyles(text, baseStyle, inlineCodeBgColor)
    }
}

private fun AnnotatedString.Builder.parseInlineStyles(
    text: String,
    baseStyle: SpanStyle,
    inlineCodeBgColor: Color,
) {
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
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(baseStyle.copy(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        baseStyle.copy(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBgColor,
                        ),
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Deprecated CodeTheme — kept for backward-compat reference
// ─────────────────────────────────────────────────────────────

@Deprecated("Use SyntaxTheme instead", ReplaceWith("SyntaxTheme"))
enum class CodeTheme(val bgCode: Color, val bgInline: Color, val label: String) {
    MONOKAI(Color(0xFF272822), Color(0xFF3E3D32), "Monokai"),
    DRACULA(Color(0xFF282A36), Color(0xFF3B3D4E), "Dracula"),
    NORD(Color(0xFF2E3440), Color(0xFF3B4252), "Nord"),
    ONE_DARK(Color(0xFF282C34), Color(0xFF3A3E4A), "One Dark"),
    GITHUB(Color(0xFFF6F8FA), Color(0xFFEAEEF2), "GitHub"),
    SOLARIZED_DARK(Color(0xFF002B36), Color(0xFF073642), "Solarized Dark"),
}
