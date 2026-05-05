package com.warped.data.local.inference

object GgufQuantizationParser {

    private val quantRegex = Regex(
        """(Q[2-8]_[KMS]|Q[2-8]_0|Q[2-8]_1|Q[1-8]K_[MS]|Q[1-8]K|"""
                + """F16|F32|FP16|FP32|BF16|"""
                + """IQ[1-4]_[MNLXS]|IQ[1-4]_[KNLMSX]+|IQ[1-4]_[A-Z0-9_]+|"""
                + """TQ[1-2]_0|Q[4-8]_0_4_4|Q[4-8]_0_4_8|Q[4-8]_0_8_8)""",
        RegexOption.IGNORE_CASE
    )

    fun parseQuantization(filename: String): String? {
        val name = filename.substringAfterLast("/")
        return quantRegex.find(name)?.value
    }

    fun estimateRamBytes(fileSizeBytes: Long): Long = (fileSizeBytes * 1.3).toLong()

    fun formatRamBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024L * 1024 * 1024))
            bytes >= 1024L * 1024 -> "%.0f MB".format(bytes.toDouble() / (1024L * 1024))
            else -> "${bytes / 1024} KB"
        }
    }
}
