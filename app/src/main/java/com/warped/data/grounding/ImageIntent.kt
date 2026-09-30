package com.warped.data.grounding

/**
 * Quick-task (image-grid): intent-gated image search.
 *
 * Returns true when the query asks for pictures. The VM sets
 * `include_images=true` on the pre-search call only when this fires —
 * non-image turns stay byte-identical to today (no extra payload).
 *
 * Matching is case-insensitive over letter-tokens (split on non-letters),
 * so "IMÁGENES?" matches but "imaginación" (a single token) does NOT.
 * Multi-word phrases match on token-subsequences, so "show me pictures
 * of cats" matches via "show me" + "pictures". Pure Kotlin — JVM-testable.
 */
object ImageIntent {

    /**
     * Single-token keywords (ES+EN). Matched as whole tokens — never
     * substrings.
     */
    internal val WORDS = setOf(
        // ES
        "imagen",
        "imágenes",
        "foto",
        "fotos",
        "fotografía",
        "fotografias",
        "fotografías",
        "muéstrame",
        "muestrame",
        "enséñame",
        "ensenarme",
        "ver",
        "dibujo",
        "dibujos",
        // EN
        "image",
        "images",
        "picture",
        "pictures",
        "photo",
        "photos",
        "diagram",
        "diagrams",
    )

    /** Multi-word phrases (ES+EN), matched as token subsequences. */
    internal val PHRASES = listOf(
        listOf("show", "me"),
        listOf("picture", "of"),
        listOf("image", "of"),
        listOf("photo", "of"),
        listOf("muéstrame", "una"),
        listOf("muestrame", "una"),
        listOf("enséñame", "una"),
    )

    fun hasImageIntent(query: String): Boolean {
        val tokens = query
            .lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        if (tokens.any { it in WORDS }) return true
        return PHRASES.any { phrase -> containsSubsequence(tokens, phrase) }
    }

    private fun containsSubsequence(tokens: List<String>, phrase: List<String>): Boolean {
        if (phrase.size > tokens.size) return false
        return (0..tokens.size - phrase.size).any { start ->
            phrase.indices.all { i -> tokens[start + i] == phrase[i] }
        }
    }
}
