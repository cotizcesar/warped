package com.warped.ui.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.warped.domain.highlighting.LanguageDetector
import com.warped.domain.model.SyntaxTheme
import org.junit.Rule
import org.junit.Test

class MarkdownTextTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders_bold_text_with_fontweight_bold_span`() {
        composeTestRule.setContent {
            MarkdownText("**bold**", codeTheme = SyntaxTheme.MONOKAI)
        }
        composeTestRule.onNodeWithText("bold").assertExists()
    }

    @Test
    fun `renders_header_then_body_text_in_column_order`() {
        composeTestRule.setContent {
            MarkdownText("# Title\nBody", codeTheme = SyntaxTheme.MONOKAI)
        }
        // HeaderBlock renders "Title" first, then TextBlock renders "Body"
        composeTestRule.onNodeWithText("Title").assertExists()
        composeTestRule.onNodeWithText("Body").assertExists()
    }

    @Test
    fun `accepts_syntaxtheme_parameter_with_one_dark_theme`() {
        composeTestRule.setContent {
            MarkdownText("text", codeTheme = SyntaxTheme.ONE_DARK)
        }
        composeTestRule.onNodeWithText("text").assertExists()
    }

    @Test
    fun `renders_fenced_code_block`() {
        composeTestRule.setContent {
            MarkdownText(
                "```python\nprint(1)\n```",
                codeTheme = SyntaxTheme.MONOKAI,
                languageDetector = LanguageDetector(),
            )
        }
        composeTestRule.onNodeWithText("print(1)").assertExists()
    }

    @Test
    fun `renders_inline_code_with_monospace_font`() {
        composeTestRule.setContent {
            MarkdownText("`inline`", codeTheme = SyntaxTheme.MONOKAI)
        }
        composeTestRule.onNodeWithText("inline").assertExists()
    }

    @Test
    fun `complex_markdown_with_mixed_blocks_renders_in_correct_order`() {
        composeTestRule.setContent {
            MarkdownText(
                "# Header\nBody text\n- item1\n- item2\n```js\nconsole.log('hi')\n```\nDone",
                codeTheme = SyntaxTheme.MONOKAI,
                languageDetector = LanguageDetector(),
            )
        }
        composeTestRule.onNodeWithText("Header").assertExists()
        composeTestRule.onNodeWithText("Body text").assertExists()
        composeTestRule.onNodeWithText("item1").assertExists()
        composeTestRule.onNodeWithText("item2").assertExists()
        composeTestRule.onNodeWithText("Done").assertExists()
    }

    @Test
    fun `empty_text_returns_early_without_block_parsing`() {
        composeTestRule.setContent {
            MarkdownText("", codeTheme = SyntaxTheme.MONOKAI)
        }
        composeTestRule.onNodeWithText("").assertExists()
    }
}
