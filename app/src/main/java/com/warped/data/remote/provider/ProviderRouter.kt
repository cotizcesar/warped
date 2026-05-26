package com.warped.data.remote.provider

import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.inference.LiteRTLmProvider
import com.warped.data.local.security.ApiKeyStore
import com.warped.domain.model.Endpoint
import com.warped.domain.model.ProviderType
import com.warped.domain.provider.LlmProvider
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRouter @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val inputSanitizer: InputSanitizer,
    private val liteRTLmProvider: dagger.Lazy<LiteRTLmProvider>
) {
    fun resolve(endpoint: Endpoint, modelId: String): LlmProvider {
        val key = apiKeyStore.getKey(endpoint.id)
        val keyStr = if (key != null && key.isNotEmpty()) String(key).also { key.fill('0') } else null
        return when (endpoint.apiType) {
            ProviderType.OPENAI -> OpenAIProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                endpointId = endpoint.id,
                apiKey = keyStr,
                inputSanitizer = inputSanitizer
            )
            ProviderType.ANTHROPIC -> AnthropicProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                apiKey = keyStr,
                inputSanitizer = inputSanitizer
            )
            ProviderType.OLLAMA -> OllamaProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                inputSanitizer = inputSanitizer
            )
            ProviderType.LM_STUDIO -> LMStudioProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                apiKey = keyStr,
                inputSanitizer = inputSanitizer
            )
            ProviderType.CUSTOM -> CustomProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                apiKey = keyStr
            )
            ProviderType.LOCAL, ProviderType.LITE_RT_LM -> liteRTLmProvider.get()
        }
    }

    fun resolveLocal(providerType: ProviderType, modelId: String): LlmProvider {
        return when (providerType) {
            ProviderType.LOCAL, ProviderType.LITE_RT_LM -> liteRTLmProvider.get()
            else -> error("Provider $providerType is not local")
        }
    }
}
