package com.warped.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {
    // ProviderRouter is provided by its @Inject constructor (takes ApiKeyStore).
    // Individual LlmProvider implementations (OpenAIProvider, OllamaProvider, etc.)
    // are instantiated per-endpoint via ProviderRouter.resolve() with runtime parameters
    // and do not benefit from DI singleton provisioning.
}
