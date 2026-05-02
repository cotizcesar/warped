package com.warped.data.remote.provider

import com.warped.data.local.inference.LocalLlmProvider
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
    private val localLlmProvider: dagger.Lazy<LocalLlmProvider>
) {
    fun resolve(endpoint: Endpoint, modelId: String): LlmProvider {
        return when (endpoint.apiType) {
            ProviderType.OPENAI -> OpenAIProvider(
                baseUrl = endpoint.url,
                modelId = modelId,
                endpointId = endpoint.id
            )
            // NOTE: OpenAIProvider creates its own Retrofit without passing the global
            // OkHttpClient from NetworkModule, so it doesn't use AuthInterceptor. The
            // apiKey should be passed through the constructor and injected via x-api-key
            // or Bearer header internally, similar to how AnthropicProvider handles auth.
            ProviderType.ANTHROPIC -> {
                val key = apiKeyStore.getKey(endpoint.id)
                val keyStr = if (key != null && key.isNotEmpty()) String(key).also { key.fill('0') } else null
                AnthropicProvider(baseUrl = endpoint.url, modelId = modelId, apiKey = keyStr)
            }
            ProviderType.OLLAMA -> OllamaProvider(
                baseUrl = endpoint.url,
                modelId = modelId
            )
            ProviderType.LM_STUDIO -> LMStudioProvider(
                baseUrl = endpoint.url,
                modelId = modelId
            )
            ProviderType.CUSTOM -> CustomProvider(
                baseUrl = endpoint.url,
                modelId = modelId
            )
            ProviderType.LOCAL -> localLlmProvider.get().configure(modelId)
            ProviderType.LITE_RT_LM -> localLlmProvider.get().configure(modelId) // TODO: Route to LiteRTLmProvider in Phase 07-02
        }
    }
}
