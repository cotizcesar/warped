package com.warped.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Static capability metadata for known-good `.task`/`.litertlm` models.
 *
 * 45-02 LRT-09 (RESEARCH Pitfall 1): this asset did not exist in the tree — this file
 * plus `assets/model_allowlist.json` are a creation, not an update. Schema is the Gallery
 * subset (`name`, `displayName`, `modelFile`, `sizeInBytes`, `capabilities`,
 * `llmPromptTemplates`, `taskTypes`).
 *
 * Verified-only rule (D-allowlist, T-45-06): a flag is true ONLY if the feature was
 * actually verified against 0.17.x on Android. Engine-surface verification (AAR bytecode
 * + app code paths) covers text/vision/audio + speculative decoding; per-model thinking,
 * function calling, MTP, and extended context stay false until device-verified (tool
 * wiring itself belongs to Phase 47).
 */
@Serializable
data class AllowlistCapabilities(
    val text: Boolean = false,
    val vision: Boolean = false,
    val audio: Boolean = false,
    val supportsThinking: Boolean = false,
    val supportsFunctionCalling: Boolean = false,
    val speculativeDecoding: Boolean = false,
    val extendedContext: Boolean = false,
    val mtpSupport: Boolean = false
)

@Serializable
data class AllowlistedModel(
    val name: String,
    val displayName: String,
    val modelFile: String,
    val sizeInBytes: Long,
    val capabilities: AllowlistCapabilities = AllowlistCapabilities(),
    val llmPromptTemplates: Map<String, String> = emptyMap(),
    val taskTypes: List<String> = emptyList()
)

@Serializable
private data class ModelAllowlistFile(
    val models: List<AllowlistedModel> = emptyList()
)

/**
 * Pure (Android-free) parser for the allowlist asset — unit-testable on the JVM.
 * Unknown keys are ignored so future Gallery-schema additions don't break parsing.
 */
fun parseModelAllowlist(raw: String): List<AllowlistedModel> {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    return json.decodeFromString<ModelAllowlistFile>(raw).models
}

@Singleton
class ModelAllowlistRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** All allowlisted models, loaded once from assets. Empty if the asset is missing. */
    val models: List<AllowlistedModel> by lazy {
        try {
            context.assets.open("model_allowlist.json").bufferedReader().use { reader ->
                parseModelAllowlist(reader.readText())
            }
        } catch (e: Exception) {
            Timber.w(e, "ModelAllowlistRepository: failed to load model_allowlist.json")
            emptyList()
        }
    }

    fun findByName(name: String): AllowlistedModel? =
        models.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun findByModelFile(fileName: String): AllowlistedModel? =
        models.firstOrNull { it.modelFile.equals(fileName, ignoreCase = true) }

    /** True only if thinking was verified for this model on 0.17.x Android. */
    fun supportsThinking(name: String): Boolean =
        findByName(name)?.capabilities?.supportsThinking == true

    /** True only if tool calling was verified for this model on 0.17.x Android. */
    fun supportsFunctionCalling(name: String): Boolean =
        findByName(name)?.capabilities?.supportsFunctionCalling == true

    fun supportsSpeculativeDecoding(name: String): Boolean =
        findByName(name)?.capabilities?.speculativeDecoding == true

    fun supportsExtendedContext(name: String): Boolean =
        findByName(name)?.capabilities?.extendedContext == true

    fun supportsMtp(name: String): Boolean =
        findByName(name)?.capabilities?.mtpSupport == true

    /** Modality query: "text", "vision", or "audio" (case-insensitive). */
    fun supportsModality(name: String, modality: String): Boolean {
        val caps = findByName(name)?.capabilities ?: return false
        return when (modality.lowercase()) {
            "text" -> caps.text
            "vision" -> caps.vision
            "audio" -> caps.audio
            else -> false
        }
    }

    /**
     * Effective UI capabilities for a downloaded model. The allowlist
     * (verified-only) wins when it contains the model — matched by file name,
     * then by display name.
     *
     * Thinking is allowlist opt-in: only models with verified thought output
     * show the Thinking badge (device-proven 2026-09-28 that untagged models
     * like gemma-4-E2B-it never emit thinking). Other flags fall back to the
     * model's stored capabilities when unlisted.
     */
    fun effectiveCapabilities(model: com.warped.domain.model.LocalModel): com.warped.domain.model.ModelCapabilities {
        val fileName = model.filePath.substringAfterLast("/")
        val entry = findByModelFile(fileName) ?: findByName(model.name)
        if (entry != null) {
            return com.warped.domain.model.ModelCapabilities(
                vision = entry.capabilities.vision,
                reasoning = entry.capabilities.supportsThinking,
                tools = entry.capabilities.supportsFunctionCalling,
                audio = entry.capabilities.audio
            )
        }
        return model.capabilities.copy(reasoning = false)
    }
}
