package com.warped.domain.model

data class SyntaxTheme(
    val key: String,
    val label: String,
    val darkVariant: Map<TokenType, SyntaxColor>,
    val lightVariant: Map<TokenType, SyntaxColor>,
) {
    companion object {
        val MONOKAI = SyntaxTheme(
            key = "monokai",
            label = "Monokai",
            darkVariant = mapOf(
                TokenType.BACKGROUND to SyntaxColor(0xFF272822.toInt()),
                TokenType.KEYWORD to SyntaxColor(0xFFF92672.toInt()),
                TokenType.STRING to SyntaxColor(0xFFE6DB74.toInt()),
                TokenType.COMMENT to SyntaxColor(0xFF75715E.toInt()),
                TokenType.NUMBER to SyntaxColor(0xFFAE81FF.toInt()),
                TokenType.FUNCTION to SyntaxColor(0xFFA6E22E.toInt()),
                TokenType.TYPE to SyntaxColor(0xFF66D9EF.toInt()),
                TokenType.OPERATOR to SyntaxColor(0xFFF92672.toInt()),
                TokenType.PROPERTY to SyntaxColor(0xFFA6E22E.toInt()),
                TokenType.CONSTANT to SyntaxColor(0xFFAE81FF.toInt()),
                TokenType.PUNCTUATION to SyntaxColor(0xFFF8F8F2.toInt()),
                TokenType.PLAIN to SyntaxColor(0xFFF8F8F2.toInt()),
                TokenType.TAG to SyntaxColor(0xFFF92672.toInt()),
            ),
            lightVariant = mapOf(
                TokenType.BACKGROUND to SyntaxColor(0xFFF8F8F2.toInt()),
                TokenType.KEYWORD to SyntaxColor(0xFFE62D6B.toInt()),
                TokenType.STRING to SyntaxColor(0xFF8B8C1A.toInt()),
                TokenType.COMMENT to SyntaxColor(0xFF73705E.toInt()),
                TokenType.NUMBER to SyntaxColor(0xFF8C6BC8.toInt()),
                TokenType.FUNCTION to SyntaxColor(0xFF5A8116.toInt()),
                TokenType.TYPE to SyntaxColor(0xFF3E9FB8.toInt()),
                TokenType.OPERATOR to SyntaxColor(0xFFE62D6B.toInt()),
                TokenType.PROPERTY to SyntaxColor(0xFF5A8116.toInt()),
                TokenType.CONSTANT to SyntaxColor(0xFF8C6BC8.toInt()),
                TokenType.PUNCTUATION to SyntaxColor(0xFF1A1A1A.toInt()),
                TokenType.PLAIN to SyntaxColor(0xFF1A1A1A.toInt()),
                TokenType.TAG to SyntaxColor(0xFFE62D6B.toInt()),
            ),
        )

        val ONE_DARK = SyntaxTheme(
            key = "one_dark",
            label = "One Dark",
            darkVariant = mapOf(
                TokenType.BACKGROUND to SyntaxColor(0xFF282C34.toInt()),
                TokenType.KEYWORD to SyntaxColor(0xFFC678DD.toInt()),
                TokenType.STRING to SyntaxColor(0xFF98C379.toInt()),
                TokenType.COMMENT to SyntaxColor(0xFF5C6370.toInt()),
                TokenType.NUMBER to SyntaxColor(0xFFD19A66.toInt()),
                TokenType.FUNCTION to SyntaxColor(0xFF61AFEF.toInt()),
                TokenType.TYPE to SyntaxColor(0xFFE5C07B.toInt()),
                TokenType.OPERATOR to SyntaxColor(0xFF56B6C2.toInt()),
                TokenType.PROPERTY to SyntaxColor(0xFFE06C75.toInt()),
                TokenType.CONSTANT to SyntaxColor(0xFFD19A66.toInt()),
                TokenType.PUNCTUATION to SyntaxColor(0xFFABB2BF.toInt()),
                TokenType.PLAIN to SyntaxColor(0xFFABB2BF.toInt()),
                TokenType.TAG to SyntaxColor(0xFFE06C75.toInt()),
            ),
            lightVariant = mapOf(
                TokenType.BACKGROUND to SyntaxColor(0xFFFAFAFA.toInt()),
                TokenType.KEYWORD to SyntaxColor(0xFFA626A4.toInt()),
                TokenType.STRING to SyntaxColor(0xFF50A14F.toInt()),
                TokenType.COMMENT to SyntaxColor(0xFFA0A1A7.toInt()),
                TokenType.NUMBER to SyntaxColor(0xFF986801.toInt()),
                TokenType.FUNCTION to SyntaxColor(0xFF4078F2.toInt()),
                TokenType.TYPE to SyntaxColor(0xFFC18401.toInt()),
                TokenType.OPERATOR to SyntaxColor(0xFF0184BC.toInt()),
                TokenType.PROPERTY to SyntaxColor(0xFFE45649.toInt()),
                TokenType.CONSTANT to SyntaxColor(0xFF986801.toInt()),
                TokenType.PUNCTUATION to SyntaxColor(0xFF383A42.toInt()),
                TokenType.PLAIN to SyntaxColor(0xFF383A42.toInt()),
                TokenType.TAG to SyntaxColor(0xFFE45649.toInt()),
            ),
        )

        val GITHUB = SyntaxTheme(
            key = "github",
            label = "GitHub",
            darkVariant = mapOf(
                TokenType.BACKGROUND to SyntaxColor(0xFF0D1117.toInt()),
                TokenType.KEYWORD to SyntaxColor(0xFFFF7B72.toInt()),
                TokenType.STRING to SyntaxColor(0xFFA5D6FF.toInt()),
                TokenType.COMMENT to SyntaxColor(0xFF8B949E.toInt()),
                TokenType.NUMBER to SyntaxColor(0xFF79C0FF.toInt()),
                TokenType.FUNCTION to SyntaxColor(0xFFD2A8FF.toInt()),
                TokenType.TYPE to SyntaxColor(0xFF79C0FF.toInt()),
                TokenType.OPERATOR to SyntaxColor(0xFFFF7B72.toInt()),
                TokenType.PROPERTY to SyntaxColor(0xFF79C0FF.toInt()),
                TokenType.CONSTANT to SyntaxColor(0xFF79C0FF.toInt()),
                TokenType.PUNCTUATION to SyntaxColor(0xFFC9D1D9.toInt()),
                TokenType.PLAIN to SyntaxColor(0xFFC9D1D9.toInt()),
                TokenType.TAG to SyntaxColor(0xFF7EE787.toInt()),
            ),
            lightVariant = mapOf(
                TokenType.BACKGROUND to SyntaxColor(0xFFFFFFFF.toInt()),
                TokenType.KEYWORD to SyntaxColor(0xFFCF222E.toInt()),
                TokenType.STRING to SyntaxColor(0xFF0A3069.toInt()),
                TokenType.COMMENT to SyntaxColor(0xFF6E7781.toInt()),
                TokenType.NUMBER to SyntaxColor(0xFF0550AE.toInt()),
                TokenType.FUNCTION to SyntaxColor(0xFF8250DF.toInt()),
                TokenType.TYPE to SyntaxColor(0xFF0550AE.toInt()),
                TokenType.OPERATOR to SyntaxColor(0xFFCF222E.toInt()),
                TokenType.PROPERTY to SyntaxColor(0xFF0550AE.toInt()),
                TokenType.CONSTANT to SyntaxColor(0xFF0550AE.toInt()),
                TokenType.PUNCTUATION to SyntaxColor(0xFF24292F.toInt()),
                TokenType.PLAIN to SyntaxColor(0xFF24292F.toInt()),
                TokenType.TAG to SyntaxColor(0xFF116329.toInt()),
            ),
        )

        val DRACULA = SyntaxTheme(
            key = "dracula",
            label = "Dracula",
            darkVariant = mapOf(
                TokenType.BACKGROUND to SyntaxColor(0xFF282A36.toInt()),
                TokenType.KEYWORD to SyntaxColor(0xFFFF79C6.toInt()),
                TokenType.STRING to SyntaxColor(0xFFF1FA8C.toInt()),
                TokenType.COMMENT to SyntaxColor(0xFF6272A4.toInt()),
                TokenType.NUMBER to SyntaxColor(0xFFBD93F9.toInt()),
                TokenType.FUNCTION to SyntaxColor(0xFF50FA7B.toInt()),
                TokenType.TYPE to SyntaxColor(0xFF8BE9FD.toInt()),
                TokenType.OPERATOR to SyntaxColor(0xFFFF79C6.toInt()),
                TokenType.PROPERTY to SyntaxColor(0xFF50FA7B.toInt()),
                TokenType.CONSTANT to SyntaxColor(0xFFBD93F9.toInt()),
                TokenType.PUNCTUATION to SyntaxColor(0xFFF8F8F2.toInt()),
                TokenType.PLAIN to SyntaxColor(0xFFF8F8F2.toInt()),
                TokenType.TAG to SyntaxColor(0xFFFF79C6.toInt()),
            ),
            lightVariant = mapOf(
                TokenType.BACKGROUND to SyntaxColor(0xFFF8F8F2.toInt()),
                TokenType.KEYWORD to SyntaxColor(0xFFE62D6B.toInt()),
                TokenType.STRING to SyntaxColor(0xFF8B8C1A.toInt()),
                TokenType.COMMENT to SyntaxColor(0xFF7B8CB3.toInt()),
                TokenType.NUMBER to SyntaxColor(0xFF9C7CD7.toInt()),
                TokenType.FUNCTION to SyntaxColor(0xFF3DC95E.toInt()),
                TokenType.TYPE to SyntaxColor(0xFF5CCAE8.toInt()),
                TokenType.OPERATOR to SyntaxColor(0xFFE62D6B.toInt()),
                TokenType.PROPERTY to SyntaxColor(0xFF3DC95E.toInt()),
                TokenType.CONSTANT to SyntaxColor(0xFF9C7CD7.toInt()),
                TokenType.PUNCTUATION to SyntaxColor(0xFF1A1A1A.toInt()),
                TokenType.PLAIN to SyntaxColor(0xFF1A1A1A.toInt()),
                TokenType.TAG to SyntaxColor(0xFFE62D6B.toInt()),
            ),
        )

        fun all(): List<SyntaxTheme> = listOf(MONOKAI, ONE_DARK, GITHUB, DRACULA)

        fun fromKey(key: String): SyntaxTheme =
            all().find { it.key == key } ?: MONOKAI
    }
}
