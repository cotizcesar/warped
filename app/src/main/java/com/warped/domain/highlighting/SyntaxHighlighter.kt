package com.warped.domain.highlighting

import com.warped.domain.model.SyntaxToken

interface SyntaxHighlighter {
    suspend fun highlight(code: String, language: String): List<SyntaxToken>
}
