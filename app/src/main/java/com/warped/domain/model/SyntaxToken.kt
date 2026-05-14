package com.warped.domain.model

data class SyntaxToken(
    val start: Int,
    val end: Int,
    val type: TokenType,
    val text: String,
)
