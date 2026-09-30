package com.warped.data.grounding

import java.text.Normalizer

/**
 * Quick-task (needs-web-gate): deterministic social/identity gate for the
 * always-on pre-search.
 *
 * Returns FALSE when the query is a social/identity/capability-about-self
 * message (greeting, thanks, farewell, "who are you", "what can you do") —
 * those turns skip the pre-search branch entirely (no socket, no credit).
 * Returns TRUE for everything else (fail-open: factual, short, or empty
 * queries search exactly as today).
 *
 * Matching mirrors [ImageIntent]: NFD-normalize + strip combining marks
 * (diacritic-insensitive: `quién` == `quien`) THEN lowercase THEN split on
 * non-letters into letter-tokens. Whole-token matching for WORDS,
 * token-subsequence matching for PHRASES — never substrings (so `holanda`
 * as a single token does NOT match `hola`). Pure Kotlin — JVM-testable.
 */
object NeedsWeb {

    /**
     * Single-token social keywords (ES+EN, stored WITHOUT diacritics —
     * normalization makes `adiós` match `adios`). Matched as whole
     * tokens — never substrings.
     */
    internal val WORDS = setOf(
        // ES
        "hola",
        "buenas",
        "dias",
        "tardes",
        "noches",
        "saludos",
        "gracias",
        "adios",
        "chao",
        "ayuda",
        // EN
        "hi",
        "hello",
        "hey",
        "thanks",
        "thank",
        "bye",
        "goodbye",
        "help",
    )

    /** Multi-word social phrases (ES+EN, no diacritics), token subsequences. */
    internal val PHRASES = listOf(
        listOf("buenos", "dias"),
        listOf("buenas", "tardes"),
        listOf("buenas", "noches"),
        listOf("que", "tal"),
        listOf("como", "estas"),
        listOf("thank", "you"),
        listOf("hasta", "luego"),
        listOf("nos", "vemos"),
        listOf("good", "bye"),
        listOf("quien", "eres"),
        listOf("tu", "nombre"),
        listOf("como", "te", "llamas"),
        listOf("cual", "es", "tu", "nombre"),
        listOf("que", "modelo", "eres"),
        listOf("que", "puedes", "hacer"),
        listOf("que", "sabes", "hacer"),
        listOf("who", "are", "you"),
        listOf("your", "name"),
        listOf("what", "is", "your", "name"),
        listOf("what", "model", "are", "you"),
        listOf("what", "can", "you", "do"),
    )

    fun needsWeb(query: String): Boolean {
        val tokens = normalize(query)
        if (tokens.isEmpty()) return true
        if (tokens.any { it in WORDS }) return false
        if (PHRASES.any { phrase -> containsSubsequence(tokens, phrase) }) return false
        return true
    }

    private fun normalize(query: String): List<String> {
        val stripped = Normalizer.normalize(query, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.isNotEmpty() }
    }

    private fun containsSubsequence(tokens: List<String>, phrase: List<String>): Boolean {
        if (phrase.size > tokens.size) return false
        return (0..tokens.size - phrase.size).any { start ->
            phrase.indices.all { i -> tokens[start + i] == phrase[i] }
        }
    }
}
