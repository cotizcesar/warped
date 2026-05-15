package com.warped.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MarkdownBlockTest {

    @Test
    fun `TextBlock stores text property`() {
        val block = MarkdownBlock.TextBlock("Hello")
        assertThat(block.text).isEqualTo("Hello")
    }

    @Test
    fun `HeaderBlock stores level and text properties`() {
        val block = MarkdownBlock.HeaderBlock(2, "Title")
        assertThat(block.level).isEqualTo(2)
        assertThat(block.text).isEqualTo("Title")
    }

    @Test
    fun `CodeBlock stores language and code properties`() {
        val block = MarkdownBlock.CodeBlock("python", "print(1)")
        assertThat(block.language).isEqualTo("python")
        assertThat(block.code).isEqualTo("print(1)")
    }

    @Test
    fun `InlineCodeBlock stores code property`() {
        val block = MarkdownBlock.InlineCodeBlock("var")
        assertThat(block.code).isEqualTo("var")
    }

    @Test
    fun `ListItemBlock with unordered list stores items`() {
        val block = MarkdownBlock.ListItemBlock(ordered = false, items = listOf("a", "b"))
        assertThat(block.ordered).isFalse()
        assertThat(block.items).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `MarkdownBlock is a sealed class with five variants`() {
        val blocks: List<MarkdownBlock> = listOf(
            MarkdownBlock.TextBlock("text"),
            MarkdownBlock.HeaderBlock(1, "header"),
            MarkdownBlock.CodeBlock("lang", "code"),
            MarkdownBlock.InlineCodeBlock("inline"),
            MarkdownBlock.ListItemBlock(ordered = true, items = listOf("item")),
        )
        assertThat(blocks).hasSize(5)
        val variantNames = blocks.map { it::class.simpleName }
        assertThat(variantNames).containsExactly(
            "TextBlock", "HeaderBlock", "CodeBlock", "InlineCodeBlock", "ListItemBlock",
        )
    }
}
