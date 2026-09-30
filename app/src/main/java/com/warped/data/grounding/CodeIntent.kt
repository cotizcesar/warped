package com.warped.data.grounding

import java.text.Normalizer

/**
 * Quick-task (code-intent-gate): deterministic code-generation gate for the
 * always-on pre-search.
 *
 * Returns TRUE when the turn is a code-generation request the model handles
 * better alone (caller SKIPS the pre-search branch entirely — no socket, no
 * credit, no notice). Returns FALSE for everything else (fail-open: factual,
 * social, short, or empty queries search exactly as today — mirroring
 * [NeedsWeb]).
 *
 * Matching mirrors [NeedsWeb]: NFD-normalize + strip combining marks
 * (diacritic-insensitive: `código` == `codigo`) THEN lowercase THEN split on
 * non-letters into letter-tokens. Whole-token matching only, never
 * substrings — with ONE deliberate token-scoped exception (see below).
 *
 * Skip rule (conjunction + escape):
 * ```
 * skip = (noun AND (verb OR language)) OR (verb AND language)
 * ```
 * - `noun AND (verb OR language)`: "escribe una funcion en javascript"
 *   (verb + noun), "depura mi script python" (noun + language).
 * - `verb AND language`: "write a quicksort in python" — verb + language
 *   with no listed noun still implies code intent (quicksort is not a listed
 *   noun; requiring one would miss verb+language code requests).
 *
 * Rationale for the conjunction: bare language names still SEARCH ("Kotlin
 * 2.3 new features" — language alone, no verb or noun arm — is a
 * versioned-facts query the model may genuinely need the web for, and the
 * armed agentic loop remains its escape hatch mid-turn), and bare verbs
 * still SEARCH ("write an email" — verb without a code noun or language —
 * is not a code turn).
 *
 * Typo tolerance: NO fuzzy matching beyond the NFD + token-boundary
 * pipeline, EXCEPT one deliberate token-scoped exception for long language
 * names: a single token CONTAINING one of `avascript`, `ython`, `ypescript`,
 * `otlin` counts as that language. This catches doubled/transposed-letter
 * typos in long names (the on-device evidence "Dame un ejemplo de codigo
 * simple en jsavascript" — token `jsavascript` contains `avascript`) while
 * whole-token matching stays for everything else. The exception is scoped to
 * these four long fragments so short names (`go`, `c`, `sql`) can never
 * over-fire. Documented limitation: `c++`/`c#` tokenize to `c`, which does
 * NOT match `cpp`/`csharp` — those turns search as today (fail-open).
 *
 * The armed agentic loop is untouched by this gate: the model can still
 * `web_search` mid-turn for versioned/fresh API facts.
 */
object CodeIntent {

    /**
     * Code nouns (ES+EN, stored WITHOUT diacritics — normalization makes
     * `código` match `codigo`). Matched as whole tokens — never substrings
     * (so `codigos` does NOT match `codigo`).
     */
    internal val NOUNS = setOf(
        // ES
        "codigo",
        "script",
        "funcion",
        "programa",
        "clase",
        "metodo",
        "bug",
        "error",
        // EN
        "code",
        "function",
        "program",
        "class",
        "method",
    )

    /**
     * Action verbs (ES+EN, stored WITHOUT diacritics). Matched as whole
     * tokens — never substrings (so `explicame` does NOT match `explica`,
     * keeping "explícame la fotosíntesis" searching).
     */
    internal val VERBS = setOf(
        // ES
        "ejemplo",
        "escribe",
        "genera",
        "crea",
        "haz",
        "implementa",
        "corrige",
        "depura",
        "explica",
        // EN
        "example",
        "write",
        "generate",
        "create",
        "make",
        "implement",
        "fix",
        "debug",
    )

    /**
     * Language names (lowercase normalized single tokens). Matched as whole
     * tokens, plus the single token-scoped typo exception documented above.
     * `go` is listed as `golang` and `c` is intentionally absent to avoid
     * single-letter over-fire.
     */
    internal val LANGUAGES = setOf(
        "javascript",
        "typescript",
        "python",
        "java",
        "kotlin",
        "swift",
        "golang",
        "rust",
        "php",
        "ruby",
        "sql",
        "html",
        "css",
        "dart",
        "cpp",
        "csharp",
    )

    /**
     * Token-scoped typo fragments for long language names. A single token
     * CONTAINING one of these counts as a language hit (see KDoc). Never
     * matched across tokens.
     */
    internal val TYPO_FRAGMENTS = setOf(
        "avascript",
        "ython",
        "ypescript",
        "otlin",
    )

    fun isCodeTurn(query: String): Boolean {
        val tokens = normalize(query)
        if (tokens.isEmpty()) return false
        val hasNoun = tokens.any { it in NOUNS }
        val hasVerb = tokens.any { it in VERBS }
        val hasLanguage = tokens.any { it in LANGUAGES || TYPO_FRAGMENTS.any { frag -> it.contains(frag) } }
        return (hasNoun && (hasVerb || hasLanguage)) || (hasVerb && hasLanguage)
    }

    private fun normalize(query: String): List<String> {
        val stripped = Normalizer.normalize(query, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.isNotEmpty() }
    }
}
