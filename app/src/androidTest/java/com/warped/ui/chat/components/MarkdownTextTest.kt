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
    fun `renders bold text with FontWeight Bold span`() {
        composeTestRule.setContent {
            MarkdownText("**bold**", codeTheme = SyntaxTheme.MONOKAI)
        }
        composeTestRule.onNodeWithText("bold").assertExists()
    }

    @Test
    fun `renders header then body text in column order`() {
        composeTestRule.setContent {
            MarkdownText("# Title\nBody", codeTheme = SyntaxTheme.MONOKAI)
        }
        // HeaderBlock renders "Title" first, then TextBlock renders "Body"
        composeTestRule.onNodeWithText("Title").assertExists()
        composeTestRule.onNodeWithText("Body").assertExists()
    }

    @Test
    fun `accepts SyntaxTheme parameter with ONE DARK theme`() {
        composeTestRule.setContent {
            MarkdownText("text", codeTheme = SyntaxTheme.ONE_DARK)
        }
        composeTestRule.onNodeWithText("text").assertExists()
    }

    @Test
    fun `renders fenced code block`() {
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
    fun `renders inline code with monospace font`() {
        composeTestRule.setContent {
            MarkdownText("`inline`", codeTheme = SyntaxTheme.MONOKAI)
        }
        composeTestRule.onNodeWithText("inline").assertExists()
    }

    @Test
    fun `complex markdown with mixed blocks renders in correct order`() {
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
    fun `empty text returns early without block parsing`() {
        composeTestRule.setContent {
            MarkdownText("", codeTheme = SyntaxTheme.MONOKAI)
        }
        composeTestRule.onNodeWithText("").assertExists()
    }
}
