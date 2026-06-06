package com.warped.data.local.inference

/**
 * HF-06 / future GGUF-style metadata parser hardening utility.
 *
 * LiteRT-LM and similar inference engines may eventually read binary metadata
 * (e.g. GGUF) that includes variable-length strings. A corrupted or malicious
 * input can declare a `keyLen` or `strLen` in the hundreds of GB and force
 * an OOM allocation.
 *
 * Use these helpers at the read boundary to clamp string/buffer sizes to a
 * reasonable upper bound before allocating `ByteArray`. Defaults are tuned
 * for GGUF / Hugging Face metadata:
 *   - keyLen: ≤ 256 bytes (longest documented key is ~64 bytes; 4× safety)
 *   - strLen: ≤ 1 MiB (description, license, etc.)
 *   - arrayLen: ≤ 4 GiB (weight matrices, etc.)
 */
object MetadataBounds {

    const val DEFAULT_MAX_KEY_BYTES: Int = 256
    const val DEFAULT_MAX_STRING_BYTES: Int = 1 * 1024 * 1024       // 1 MiB
    const val DEFAULT_MAX_ARRAY_BYTES: Long = 4L * 1024 * 1024 * 1024 // 4 GiB

    /**
     * Validate an Int-typed key/str length against a max bound.
     * @return true if `length` is positive and ≤ `maxBytes`.
     */
    fun isValidIntLength(length: Int, maxBytes: Int = DEFAULT_MAX_STRING_BYTES): Boolean =
        length in 1..maxBytes

    /**
     * Validate a Long-typed length against a max bound.
     * @return true if `length` is positive and ≤ `maxBytes`.
     */
    fun isValidLongLength(length: Long, maxBytes: Long = DEFAULT_MAX_ARRAY_BYTES): Boolean =
        length in 1L..maxBytes

    /**
     * Compute a safe allocation size, or return -1 if the requested length
     * exceeds the bound. Callers should treat -1 as "skip this field" rather
     * than allocating.
     */
    fun safeIntAllocation(requested: Int, maxBytes: Int = DEFAULT_MAX_STRING_BYTES): Int =
        if (isValidIntLength(requested, maxBytes)) requested else -1

    fun safeLongAllocation(requested: Long, maxBytes: Long = DEFAULT_MAX_ARRAY_BYTES): Long =
        if (isValidLongLength(requested, maxBytes)) requested else -1
}
