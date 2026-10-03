package com.warped.domain.model

/**
 * Human display names from engine file names.
 *
 * Catalog downloads use the allowlist `displayName`; this is the fallback
 * for imports and anything unlisted: strip the container extension, drop
 * quant/packing tokens (q4, int8, block32, ekv4096, …) and join the rest
 * with spaces. "SmolLM3-3B_q4_block32_ekv4096" → "SmolLM3 3B".
 * Pure — unit-tested.
 */
private val QUANT_TOKEN = Regex(
    "^(q[348]|int[48]|block\\d+|ekv\\d+|afp\\d+|wi\\d+b\\d+|mixed|dynamic|prefill|seq|multi|nothink|gpu|fp\\d+|q8_ekv\\d+)$",
    RegexOption.IGNORE_CASE,
)

fun prettyModelName(fileName: String): String {
    // Strip the container extension first so version dots ("1.5B")
    // survive — only the extension dot is removed.
    val stem = fileName.substringAfterLast("/").substringBeforeLast(".")
    val kept = stem.split('_', '-', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !QUANT_TOKEN.matches(it) }
    return kept.joinToString(" ").ifBlank { stem }
}
