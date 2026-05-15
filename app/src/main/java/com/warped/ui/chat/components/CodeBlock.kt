package com.warped.ui.chat.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
// FastOutSlowInEasing used for copy button Crossfade (no separate FastOutLinearSlowInEasing in Compose)
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.warped.domain.highlighting.SyntaxHighlighter
import com.warped.domain.model.SyntaxTheme
import com.warped.domain.model.SyntaxToken
import com.warped.domain.model.TokenType
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.window.Popup
import timber.log.Timber

// ─────────────────────────────────────────────────────────────
// Hilt EntryPoint — provides SyntaxHighlighter to composables
// ─────────────────────────────────────────────────────────────

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyntaxHighlightingEntryPoint {
    fun syntaxHighlighter(): SyntaxHighlighter
}

// ─────────────────────────────────────────────────────────────
// CodeBlock composable
// ─────────────────────────────────────────────────────────────

/**
 * Renders a syntax-highlighted code block with a language header bar,
 * copy button, line numbers, and expand/collapse for large blocks.
 *
 * @param language The detected or declared programming language (e.g. "python", "javascript").
 *                "plaintext" is supported — all tokens map to PLAIN type, rendering flat monospace.
 * @param code The raw source code string to display and highlight.
 * @param syntaxTheme The active [SyntaxTheme] for token coloring. Defaults to Monokai.
 * @param codeFontScale Scaling factor for code font size (from Settings). Defaults to 1.0f.
 * @param isStreaming When `true`, renders flat monospace without syntax colors.
 * @param modifier Optional [Modifier] applied to the root [Column].
 */
@Composable
fun CodeBlock(
    language: String,
    code: String,
    syntaxTheme: SyntaxTheme = SyntaxTheme.MONOKAI,
    codeFontScale: Float = 1.0f,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // ── Syntax highlighting state ────────────────────────────
    var tokens by remember { mutableStateOf<List<SyntaxToken>>(emptyList()) }

    val isDark = isSystemInDarkTheme()
    val variant = if (isDark) syntaxTheme.darkVariant else syntaxTheme.lightVariant
    val bgCode = Color(variant[TokenType.BACKGROUND]?.argb ?: 0xFF1E1E1E.toInt())

    // Code font sizing — scale wired from AdvancedPreferences
    val codeFontSize = (16f * codeFontScale).sp
    val codeLineHeight = (20f * codeFontScale).sp

    // ── Resolve Hilt entry point at composable scope ──────────
    val appContext = LocalContext.current.applicationContext
    val highlighterEntryPoint = remember {
        EntryPoints.get(appContext, SyntaxHighlightingEntryPoint::class.java)
    }
    val highlighter = remember { highlighterEntryPoint.syntaxHighlighter() }

    // ── Launch async highlighting ────────────────────────────
    LaunchedEffect(code, language, isStreaming) {
        tokens = emptyList()
        if (code.isBlank()) return@LaunchedEffect
        if (code.length > 500_000) {
            Timber.w("CodeBlock: skipping highlighting — code exceeds 500KB safety cap (${code.length} bytes)")
            return@LaunchedEffect
        }
        if (isStreaming) {
            // Flat monospace during stream; Phase 30 handles transition
            return@LaunchedEffect
        }
        try {
            val result = withContext(Dispatchers.Default) {
                highlighter.highlight(code, language)
            }
            tokens = result
        } catch (e: Exception) {
            Timber.w("CodeBlock: highlighting failed for '$language' — ${e.message}")
        }
    }

    // ── Resolve target colors per token type ──────────────────
    val plainArgb = variant[TokenType.PLAIN]?.argb ?: 0xFFFFFFFF.toInt()
    val plainColor = Color(plainArgb)

    val keywordTarget = Color(variant[TokenType.KEYWORD]?.argb ?: plainArgb)
    val stringTarget = Color(variant[TokenType.STRING]?.argb ?: plainArgb)
    val commentTarget = Color(variant[TokenType.COMMENT]?.argb ?: plainArgb)
    val numberTarget = Color(variant[TokenType.NUMBER]?.argb ?: plainArgb)
    val functionTarget = Color(variant[TokenType.FUNCTION]?.argb ?: plainArgb)
    val typeTarget = Color(variant[TokenType.TYPE]?.argb ?: plainArgb)
    val operatorTarget = Color(variant[TokenType.OPERATOR]?.argb ?: plainArgb)
    val propertyTarget = Color(variant[TokenType.PROPERTY]?.argb ?: plainArgb)
    val constantTarget = Color(variant[TokenType.CONSTANT]?.argb ?: plainArgb)
    val punctuationTarget = Color(variant[TokenType.PUNCTUATION]?.argb ?: plainArgb)
    val tagTarget = Color(variant[TokenType.TAG]?.argb ?: plainArgb)

    // ── Animate each token type's color ───────────────────────
    val keywordColor by animateColorAsState(keywordTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val stringColor by animateColorAsState(stringTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val commentColor by animateColorAsState(commentTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val numberColor by animateColorAsState(numberTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val functionColor by animateColorAsState(functionTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val typeColor by animateColorAsState(typeTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val operatorColor by animateColorAsState(operatorTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val propertyColor by animateColorAsState(propertyTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val constantColor by animateColorAsState(constantTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val punctuationColor by animateColorAsState(punctuationTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val tagColor by animateColorAsState(tagTarget, animationSpec = tween(400, easing = FastOutSlowInEasing))
    val plainAnimatedColor by animateColorAsState(plainColor, animationSpec = tween(400, easing = FastOutSlowInEasing))

    val tokenColorMap: Map<TokenType, Color> = mapOf(
        TokenType.KEYWORD to keywordColor,
        TokenType.STRING to stringColor,
        TokenType.COMMENT to commentColor,
        TokenType.NUMBER to numberColor,
        TokenType.FUNCTION to functionColor,
        TokenType.TYPE to typeColor,
        TokenType.OPERATOR to operatorColor,
        TokenType.PROPERTY to propertyColor,
        TokenType.CONSTANT to constantColor,
        TokenType.PUNCTUATION to punctuationColor,
        TokenType.TAG to tagColor,
        TokenType.PLAIN to plainAnimatedColor,
        TokenType.BACKGROUND to bgCode,
    )

    // ── Build annotated string from tokens ───────────────────
    val annotated = remember(tokens, tokenColorMap, codeFontSize, code) {
        buildAnnotatedString {
            if (tokens.isEmpty() && code.isNotBlank()) {
                // Flat monospace fallback while loading / on failure
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = codeFontSize,
                        color = Color.White,
                    ),
                ) {
                    append(code)
                }
            } else {
                tokens.forEach { token ->
                    val color = tokenColorMap[token.type] ?: Color.White
                    withStyle(
                        SpanStyle(
                            color = color,
                            fontFamily = FontFamily.Monospace,
                            fontSize = codeFontSize,
                        ),
                    ) {
                        append(token.text)
                    }
                }
            }
        }
    }

    // ── Rendering ────────────────────────────────────────────
    val lineCount = code.lines().size
    val needsCollapse = lineCount >= 200
    var expanded by remember { mutableStateOf(false) }

    // ── Detect syntax issues ──────────────────────────────────
    val syntaxIssue = remember(code) { detectSyntaxIssues(code) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header bar
        CodeHeaderBar(
            language = language,
            code = code,
            bgCode = bgCode,
            isStreaming = isStreaming,
            syntaxIssue = syntaxIssue,
        )

        // Code area with optional collapse
        val horizontalScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .then(
                    if (needsCollapse && !expanded) {
                        Modifier
                            .heightIn(max = 150.dp)
                            .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing))
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (expanded) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        },
                    )
                    .horizontalScroll(horizontalScrollState),
            ) {
                // Line number gutter
                if (code.isNotBlank()) {
                    LineNumberGutter(
                        code = code,
                        lineCount = lineCount,
                        bgCode = bgCode,
                        lineHeight = codeLineHeight,
                    )

                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color.Gray.copy(alpha = 0.2f)),
                    )
                }

                // Code text area
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = annotated,
                            fontFamily = FontFamily.Monospace,
                            fontSize = codeFontSize,
                            lineHeight = codeLineHeight,
                            color = Color.White,
                        )
                    }
                }
            }

            // Expand overlay for collapsed blocks
            if (needsCollapse && !expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, bgCode.copy(alpha = 0.95f)),
                                startY = 0f,
                                endY = 8f,
                            ),
                        )
                        .background(bgCode.copy(alpha = 0.95f))
                        .clickable { expanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Show all $lineCount lines",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Syntax issue detection
// ─────────────────────────────────────────────────────────────

/**
 * Detects common syntax issues for the warning indicator.
 * Returns a human-readable description string, or `null` if no issues found.
 */
private fun detectSyntaxIssues(code: String): String? {
    if (code.isBlank()) return null

    // Check for unclosed string literals (odd number of quotes)
    // Simple heuristic: count double and single quotes
    val doubleQuoteCount = code.count { it == '"' }
    val singleQuoteCount = code.count { it == '\'' }
    if (doubleQuoteCount % 2 != 0 || singleQuoteCount % 2 != 0) {
        return "Unclosed string literal"
    }

    // Check for bracket mismatch
    val openBrackets = code.count { it == '(' || it == '[' || it == '{' }
    val closeBrackets = code.count { it == ')' || it == ']' || it == '}' }
    if (openBrackets != closeBrackets) {
        return "Possible bracket mismatch"
    }

    return null
}

// ─────────────────────────────────────────────────────────────
// CodeHeaderBar composable
// ─────────────────────────────────────────────────────────────

/**
 * Header bar showing the resolved language name, optional warning indicator,
 * and copy button. Height: 28dp. Background: bgCode darkened by 8%.
 */
@Composable
private fun CodeHeaderBar(
    language: String,
    code: String,
    bgCode: Color,
    isStreaming: Boolean,
    syntaxIssue: String?,
) {
    val headerBg = Color(
        red = bgCode.red * 0.92f,
        green = bgCode.green * 0.92f,
        blue = bgCode.blue * 0.92f,
        alpha = 1f,
    )

    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    // Reset copied state after 2 seconds
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(headerBg)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Language label
        Text(
            text = language.replaceFirstChar { it.uppercase() },
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )

        // Warning indicator for syntax issues
        if (syntaxIssue != null) {
            var showTooltip by remember { mutableStateOf(false) }

            Spacer(Modifier.width(6.dp))

            IconButton(
                onClick = { showTooltip = true },
                modifier = Modifier.size(16.dp),
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = "Syntax issue",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFE6A817),
                )
            }

            if (showTooltip) {
                Popup(
                    onDismissRequest = { showTooltip = false },
                    offset = IntOffset(0, -40),
                ) {
                    Surface(
                        color = Color(0xFF424242),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = syntaxIssue,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // Empty state
        if (code.isBlank()) {
            Text(
                text = "(empty)",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }

        // Copy button
        IconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(code))
                copied = true
            },
            enabled = code.isNotBlank() && !isStreaming,
            modifier = Modifier.size(32.dp),
        ) {
            Crossfade(
                targetState = copied,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
            ) { isCopied ->
                if (isCopied) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "Copied",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF4CAF50),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Copied!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50),
                            lineHeight = 16.sp,
                        )
                    }
                } else {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// LineNumberGutter composable
// ─────────────────────────────────────────────────────────────

/**
 * Line number gutter — 32dp wide column with right-aligned line numbers
 * in muted gray text, separated from code by a 1dp vertical divider.
 */
@Composable
private fun LineNumberGutter(
    code: String,
    lineCount: Int,
    bgCode: Color,
    lineHeight: androidx.compose.ui.unit.TextUnit,
) {
    if (code.isBlank()) return

    Column(
        modifier = Modifier
            .width(32.dp)
            .background(bgCode)
            .padding(end = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        code.lines().forEachIndexed { index, _ ->
            Text(
                text = "${index + 1}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                lineHeight = lineHeight,
                textAlign = TextAlign.End,
            )
        }
    }
}
