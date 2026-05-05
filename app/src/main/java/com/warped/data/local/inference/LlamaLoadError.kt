package com.warped.data.local.inference

sealed class LlamaLoadError(
    message: String
) : Exception(message) {
    class OutOfMemory : LlamaLoadError("Out of memory — try a smaller quantization")
    class CorruptedFile : LlamaLoadError("Corrupted model file — please re-download")
    class UnsupportedArchitecture : LlamaLoadError("Unsupported architecture — this model requires ARM64")
    class Unknown(message: String) : LlamaLoadError("Failed to load model: $message")

    val userMessage: String get() = message ?: "Unknown error"

    companion object {
        fun fromNative(errorString: String): LlamaLoadError {
            val lower = errorString.lowercase()
            return when {
                lower.contains("out of memory") -> OutOfMemory()
                lower.contains("corrupt") || lower.contains("not a valid") -> CorruptedFile()
                lower.contains("unsupported") || lower.contains("architecture") -> UnsupportedArchitecture()
                else -> Unknown(errorString)
            }
        }
    }
}
