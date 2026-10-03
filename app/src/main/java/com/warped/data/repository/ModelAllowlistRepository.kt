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
 * + app code paths) covers text/vision/audio + speculative decoding; per-model
 * function calling, MTP, and extended context stay false until device-verified (tool
 * wiring itself belongs to Phase 47).
 *
 * gemma-4-E2B-it exception (2026-09-28): vision/audio/supportsThinking are
 * docs-verified from the Google official Gemma 4 model card + LiteRT-LM docs
 * (device confirmation pending — user validates on hardware). Function-calling
 * stays false: tool execution was removed in v2.2 Phase 49 DEL-01, so a Tools
 * badge would promise a missing feature. supportsThinking=true enables the
 * toggle + badge + think-tag parse/display only — ThinkingConfig engine
 * wiring is a recorded tech-debt follow-up.
 *
 * gemma-4-E4B-it (2026-09-28): same docs-verified treatment as E2B —
 * text/vision/audio/supportsThinking true, supportsFunctionCalling false per
 * Phase 49 DEL-01. Speculative decoding true (Gemma 4 docs). File size
 * verified via Hugging Face CDN HEAD (3659530240 bytes).
 *
 * 56-01 FLAG DECISION (2026-09-29): supportsFunctionCalling flips to true
 * for the gemma-4 pair ONLY, on the same docs-evidence standard the user
 * approved for thinking flags — Gemma 4 model card documents built-in
 * function calling, the E4B chat_template.jinja ships <|tool|> blocks,
 * LiteRT-LM docs list Gemma 4 under models with tool support. Device
 * confirmation is PENDING (plan 02 on-device smoke must see real ToolCall
 * emission; if a Gemma 4 model never emits ToolCalls, its flag reverts).
 * Both 3n entries stay false — no evidence either way, verified-only
 * defaults closed. The loop executes only when the active model's flag is
 * true (capability gate in plan 02).
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
    val taskTypes: List<String> = emptyList(),
    /**
     * Full Hugging Face repo slug including org, e.g.
     * `warped-community/gemma-4-E2B-it-litert-lm`.
     *
     * Back-compat rule: explicit `repo` wins; when absent or blank (old
     * assets, tests constructing the model by hand), the legacy
     * `warped-community/${name}` slug applies. The `name` is a
     * short-stable display/lookup key — never the URL source.
     */
    val repo: String? = null,
    /**
     * Optional Spanish RAM guidance + short purpose blurb for the catalog card
     * expanded view (quick plan 2026-09-28).
     *
     * Back-compat: absent or blank → null → expanded section hidden (no
     * expand affordance, no crash). Never invent RAM guidance for entries
     * lacking these fields.
     */
    val ramNote: String? = null,
    val blurb: String? = null,
    /**
     * Coming-soon flag: the entry is listed for discovery but cannot be
     * downloaded or used yet — its pipeline doesn't exist in the app
     * (on-device STT/TTS, semantic search, image generation, OCR tooling).
     * [comingSoonNote] names the missing piece (shown under the badge).
     *
     * Back-compat: absent → false (existing entries behave unchanged).
     */
    val comingSoon: Boolean = false,
    val comingSoonNote: String? = null,
    /**
     * Spanish twin of [comingSoonNote]. The app is fully EN+ES: UI copy
     * always resolves through resources or locale-paired fields, never a
     * hardcoded language. See [localizedComingSoonNote].
     */
    val comingSoonNoteEs: String? = null,
    /**
     * Curated pick: the most capable + usable models for the catalog's
     * Recommended section (verified flags + family track record — see
     * model_allowlist.json). Display-only curation, no behavior change.
     *
     * Back-compat: absent → false.
     */
    val recommended: Boolean = false
) {
    /**
     * Locale-aware coming-soon reason: Spanish note on es locales,
     * English otherwise (falling back across when one side is absent).
     */
    fun localizedComingSoonNote(): String? {
        val spanish = try {
            java.util.Locale.getDefault().language.startsWith("es")
        } catch (_: Exception) {
            false
        }
        return if (spanish) {
            comingSoonNoteEs ?: comingSoonNote
        } else {
            comingSoonNote ?: comingSoonNoteEs
        }
    }
    /**
     * Effective repo slug for download URL construction.
     * Explicit [repo] wins; legacy `warped-community/$name` applies ONLY
     * when the field is absent/blank.
     */
    val repoSlug: String
        get() = repo?.takeIf { it.isNotBlank() } ?: "warped-community/$name"
}

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

    /**
     * Reverse download-id lookup (`repoSlug/modelFile`, see
     * [CatalogViewModel.downloadId]) — resolves the worker's display name
     * so saved rows carry "SmolLM3 3B" instead of the quant-suffixed file
     * stem. Null for imports (no catalog entry).
     */
    fun findByDownloadId(downloadId: String): AllowlistedModel? =
        models.firstOrNull { "${it.repoSlug}/${it.modelFile}".equals(downloadId, ignoreCase = true) }

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
     * show the Thinking badge. gemma-4-E2B-it is docs-verified (Google official
     * Gemma 4 docs, 2026-09-28; device confirmation pending). Other flags fall
     * back to the model's stored capabilities when unlisted.
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
