package com.warped.ui.chat.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
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
    /**
     * Quick-task (citation taps): when non-null, `[N]` / `[1, 5]` markers
     * in body text become taps reporting the 1-based source number. The
     * caller resolves N against its source list (out-of-range → ignore).
     * Null renders byte-identical plain output (no annotations, no style).
     * Never set for reasoning/user bubbles.
     */
    onCitationClick: ((Int) -> Unit)? = null,
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
    val defaultFontSize = fontSize?.sp ?: MaterialTheme.typography.bodyMedium.fontSize

    val baseStyle = SpanStyle(
        fontStyle = fontStyle ?: FontStyle.Normal,
        fontSize = defaultFontSize,
    )

    // Citation affordance: MaterialTheme primary resolves to the app coral
    // (PrimaryLight/PrimaryDark = 0xFFD97757 in theme/Color.kt), so the
    // theme token is used rather than a literal. Marker glyphs stay
    // byte-identical — only an underline + tint is added.
    val citationStyle: SpanStyle? = onCitationClick?.let {
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.TextBlock -> {
                    val annotated = parseInlineMarkdownAsAnnotatedString(
                        text = block.text,
                        baseStyle = baseStyle,
                        inlineCodeBgColor = inlineCodeBgColor,
                        onCitationClick = onCitationClick,
                        citationStyle = citationStyle
                            ?: SpanStyle(textDecoration = TextDecoration.Underline),
                    )
                    CitationAwareText(
                        annotated = annotated,
                        baseColor = baseColor,
                        fontSize = defaultFontSize,
                        onCitationClick = onCitationClick,
                    )
                }

                is MarkdownBlock.HeaderBlock -> {
                    Text(
                        text = block.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = defaultFontSize,
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
                            fontSize = defaultFontSize,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                is MarkdownBlock.ListItemBlock -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        block.items.forEachIndexed { i, item ->
                            val prefix = if (block.ordered) "${i + 1}. " else "\u2022  "
                            Row {
                                Text(prefix, fontSize = defaultFontSize)
                                val annotated = parseInlineMarkdownAsAnnotatedString(
                                    text = item,
                                    baseStyle = baseStyle,
                                    inlineCodeBgColor = inlineCodeBgColor,
                                    onCitationClick = onCitationClick,
                                    citationStyle = citationStyle
                                        ?: SpanStyle(textDecoration = TextDecoration.Underline),
                                )
                                CitationAwareText(
                                    annotated = annotated,
                                    baseColor = baseColor,
                                    fontSize = defaultFontSize,
                                    onCitationClick = onCitationClick,
                                )
                            }
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
 * String-annotation tag marking tappable citation numbers. Each annotation
 * covers exactly one number's digits inside a `[N]` / `[1, 5]` marker;
 * brackets, commas, and whitespace are never annotated.
 */
internal const val CITATION_TAG = "citation"

/** Matches a full citation marker starting at the match position. */
private val CITATION_REGEX = Regex("""\[(\d+(?:\s*,\s*\d+)*)\]""")

/** Matches individual numbers inside a citation marker. */
private val CITATION_NUMBER_REGEX = Regex("""\d+""")

/**
 * Parses inline markdown styling markers (**bold**, *italic*, `code`)
 * and returns an [AnnotatedString] with appropriate [SpanStyle] spans.
 *
 * Inline code background color is derived from the active [SyntaxTheme]'s
 * BACKGROUND token at 25% alpha, passed in by the caller.
 *
 * Citation markers (`[N]`, `[1, 5]`) produce one `"citation"` string
 * annotation per number plus [citationStyle] ONLY when [onCitationClick]
 * is non-null; a null callback renders byte-identical plain output (no
 * annotations, no style change). The lambda value itself is never invoked
 * here — the renderer reports parsed ints and the caller resolves them.
 * Matches inside `` `code` `` spans are skipped by the char loop (the
 * backtick branch consumes whole spans first); fenced `CodeBlock`s never
 * reach this parser.
 */
internal fun parseInlineMarkdownAsAnnotatedString(
    text: String,
    baseStyle: SpanStyle,
    inlineCodeBgColor: Color,
    onCitationClick: ((Int) -> Unit)? = null,
    citationStyle: SpanStyle = SpanStyle(textDecoration = TextDecoration.Underline),
): AnnotatedString {
    return buildAnnotatedString {
        parseInlineStyles(
            text = text,
            baseStyle = baseStyle,
            inlineCodeBgColor = inlineCodeBgColor,
            citationStyle = if (onCitationClick != null) citationStyle else null,
        )
    }
}

private fun AnnotatedString.Builder.parseInlineStyles(
    text: String,
    baseStyle: SpanStyle,
    inlineCodeBgColor: Color,
    citationStyle: SpanStyle? = null,
) {
    var i = 0
    while (i < text.length) {
        when {
            citationStyle != null && text[i] == '[' -> {
                val match = CITATION_REGEX.matchAt(text, i)
                if (match == null) {
                    append(text[i]); i++
                } else {
                    // Visible text appended verbatim; only the digits get
                    // annotations + affordance style.
                    val full = match.value
                    var pos = 0
                    CITATION_NUMBER_REGEX.findAll(full).forEach { num ->
                        if (num.range.first > pos) {
                            append(full.substring(pos, num.range.first))
                        }
                        pushStringAnnotation(CITATION_TAG, num.value)
                        withStyle(citationStyle) {
                            append(num.value)
                        }
                        pop()
                        pos = num.range.last + 1
                    }
                    if (pos < full.length) {
                        append(full.substring(pos))
                    }
                    i = match.range.last + 1
                }
            }
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
// Citation tap resolution
// ─────────────────────────────────────────────────────────────

/**
 * Quick-task (citation taps): renders parsed body text, resolving single
 * taps on `"citation"` annotations to [onCitationClick]. A null callback
 * renders a plain [Text] (byte-identical to the pre-citation path).
 *
 * Tap (not click) detection via `pointerInput` + `TextLayoutResult` keeps
 * the parent `SelectionContainer` long-press-copy behavior intact — a
 * `ClickableText` here would break text selection.
 */
@Composable
private fun CitationAwareText(
    annotated: AnnotatedString,
    baseColor: Color,
    fontSize: TextUnit,
    onCitationClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onCitationClick == null) {
        Text(annotated, color = baseColor, fontSize = fontSize, modifier = modifier)
        return
    }
    var layoutResult by remember(annotated) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        color = baseColor,
        fontSize = fontSize,
        modifier = modifier.pointerInput(annotated, onCitationClick) {
            detectTapGestures { pos ->
                val layout = layoutResult ?: return@detectTapGestures
                val offset = layout.getOffsetForPosition(pos)
                annotated.getStringAnnotations(CITATION_TAG, offset, offset)
                    .firstOrNull()?.item?.toIntOrNull()?.let(onCitationClick)
            }
        },
        onTextLayout = { layoutResult = it },
    )
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
