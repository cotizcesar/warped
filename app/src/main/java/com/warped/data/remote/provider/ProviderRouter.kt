package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.inference.LiteRTLmProvider
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.grounding.DuckDuckGoSearchRepository
import com.warped.data.grounding.MultiUrlFetcher
import com.warped.data.grounding.WebPageFetcher
import com.warped.data.local.preferences.AdvancedPreferences
import com.warped.di.LlmHelperQualifiers
import com.warped.domain.llm.LlmModelHelper
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.provider.LlmProvider
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ProviderRouter @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val inputSanitizer: InputSanitizer,
    private val liteRTLmProvider: dagger.Lazy<LiteRTLmProvider>,
    @Named(LlmHelperQualifiers.LITE_RT_LM)
    private val liteRtLmHelper: dagger.Lazy<LlmModelHelper>,
    @Named(LlmHelperQualifiers.LM_STUDIO)
    private val lmStudioHelper: dagger.Lazy<LmStudioHelper>,
    /**
     * Phase 57 (57-02, CR-01 fix): tool-loop collaborators (Phase 55/52
     * singletons) so `resolve()` constructs ARMED providers — the loop
     * actually executes in production instead of staying dead code behind
     * null collaborators. Nullable with null defaults so legacy manual
     * call sites keep compiling; Hilt always provides real bindings.
     *
     * Quick-task (DDG-default): the search collaborator is the DDG-only
     * repository.
     */
    private val ddg: DuckDuckGoSearchRepository? = null,
    private val multiUrlFetcher: MultiUrlFetcher? = null,
    private val webPageFetcher: WebPageFetcher? = null,
    private val advancedPreferences: AdvancedPreferences? = null,
) {
    /**
     * Legacy RPC path: returns the per-endpoint [LlmProvider] used for
     * `listModels()` / `testConnection()` calls. Kept for backward compatibility —
     * the chat flow now goes through [resolveHelper] / [resolveLocalHelper].
     *
     * The `LOCAL` branch below is a mandatory exhaustive reference to the
     * deprecated legacy entry (persisted endpoints may still carry it).
     */
    @Suppress("DEPRECATION")
    fun resolve(endpoint: Endpoint, modelId: String): LlmProvider {
        val key = apiKeyStore.getKey(endpoint.id)
        val keyStr = if (key != null && key.isNotEmpty()) String(key).also { key.fill('0') } else null
        return when (endpoint.apiType) {
            ProviderType.OPENAI -> OpenAIProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                endpointId = endpoint.id,
                apiKey = keyStr,
                inputSanitizer = inputSanitizer,
                ddg = ddg,
                multiUrlFetcher = multiUrlFetcher,
                webPageFetcher = webPageFetcher,
                advancedPreferences = advancedPreferences
            )
            ProviderType.ANTHROPIC -> AnthropicProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                apiKey = keyStr,
                inputSanitizer = inputSanitizer,
                ddg = ddg,
                multiUrlFetcher = multiUrlFetcher,
                webPageFetcher = webPageFetcher,
                advancedPreferences = advancedPreferences
            )
            ProviderType.OLLAMA -> OllamaProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                inputSanitizer = inputSanitizer,
                ddg = ddg,
                multiUrlFetcher = multiUrlFetcher,
                webPageFetcher = webPageFetcher,
                advancedPreferences = advancedPreferences
            )
            ProviderType.LM_STUDIO -> LMStudioProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                apiKey = keyStr,
                inputSanitizer = inputSanitizer,
                ddg = ddg,
                multiUrlFetcher = multiUrlFetcher,
                webPageFetcher = webPageFetcher,
                advancedPreferences = advancedPreferences
            )
            ProviderType.CUSTOM -> CustomProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                apiKey = keyStr,
                inputSanitizer = inputSanitizer,
                ddg = ddg,
                multiUrlFetcher = multiUrlFetcher,
                webPageFetcher = webPageFetcher,
                advancedPreferences = advancedPreferences
            )
            ProviderType.LOCAL, ProviderType.LITE_RT_LM -> liteRTLmProvider.get()
        }
    }

    // The `LOCAL` branch below is a mandatory exhaustive reference to the
    // deprecated legacy entry (persisted rows may still carry it).
    @Suppress("DEPRECATION")
    fun resolveLocal(providerType: ProviderType, modelId: String): LlmProvider {
        return when (providerType) {
            ProviderType.LOCAL, ProviderType.LITE_RT_LM -> liteRTLmProvider.get()
            else -> error("Provider $providerType is not local")
        }
    }

    // --- Phase 40 unified chat path (RUNTIME-04) ---

    /**
     * Resolve the [LlmModelHelper] for a remote endpoint. The endpoint is bound to
     * the LM Studio helper before returning so subsequent `initialize` / `runInference`
     * calls are addressed to the right backend.
     *
     * Currently only LM Studio is wired in. Other remote providers (OpenAI, Anthropic,
     * Ollama, Custom) were removed in v1.8 (ENDPT-04) and the LlmModelHelper surface
     * is not yet extended for them. They still work via [resolve] for `listModels` /
     * `testConnection`.
     *
     * The `LOCAL` branch below is a mandatory exhaustive reference to the
     * deprecated legacy entry (persisted endpoints may still carry it).
     */
    @Suppress("DEPRECATION")
    fun resolveHelper(endpoint: Endpoint, modelId: String): LlmModelHelper {
        return when (endpoint.apiType) {
            ProviderType.LM_STUDIO -> {
                val helper = lmStudioHelper.get()
                helper.setEndpoint(endpoint)
                helper
            }
            ProviderType.LOCAL, ProviderType.LITE_RT_LM -> liteRtLmHelper.get()
            else -> error(
                "ProviderRouter.resolveHelper: ${endpoint.apiType} is not yet " +
                    "supported via the LlmModelHelper surface. Use resolve() for the " +
                    "legacy LlmProvider path."
            )
        }
    }

    /**
     * Resolve the [LlmModelHelper] for a local provider (LiteRT-LM only). The
     * `modelId` is informational here — the helper loads via [LlmModelHelper.initialize].
     *
     * The `LOCAL` branch below is a mandatory exhaustive reference to the
     * deprecated legacy entry (persisted rows may still carry it).
     */
    @Suppress("DEPRECATION")
    fun resolveLocalHelper(providerType: ProviderType, modelId: String): LlmModelHelper {
        return when (providerType) {
            ProviderType.LOCAL, ProviderType.LITE_RT_LM -> liteRtLmHelper.get()
            else -> error("Provider $providerType is not local")
        }
    }
}
