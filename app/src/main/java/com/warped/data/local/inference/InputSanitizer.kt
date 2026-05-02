package com.warped.data.local.inference

import timber.log.Timber
import javax.inject.Inject

class InputSanitizer @Inject constructor() {

    fun sanitize(input: String): String {
        var result = input

        // 1. LaTeX delimiters
        result = result.replace(Regex("""\$\$|\\[\(\)\[\]]"""), "")

        // 2. Unicode math blocks
        result = result.replace(Regex("""[\u2200-\u22FF\u27C0-\u27EF]"""), "")

        // 3. Control characters (except \n, \t)
        result = result.replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""), "")

        // 4. Surrogate characters
        result = result.replace(Regex("""[\uD800-\uDFFF]"""), "")

        // 5. Zero-width characters
        result = result.replace(Regex("""[\u200B\u200C\u200D\uFEFF]"""), "")

        if (result != input) {
            Timber.d("InputSanitizer: sanitized input (${input.length} → ${result.length} chars)")
        }

        return result
    }
}
