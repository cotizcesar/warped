package com.warped.di

import com.warped.data.local.inference.LiteRtLlmHelper
import com.warped.data.remote.provider.LmStudioHelper
import com.warped.domain.llm.LlmModelHelper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt bindings for the unified [LlmModelHelper] surface (Phase 40 RUNTIME-04).
 *
 * The two concrete helpers are exposed as named singletons so [com.warped.data.remote.provider.ProviderRouter]
 * can inject them by qualifier and dispatch to the right one based on the active
 * `ProviderType` and `Endpoint`.
 *
 * Singleton scope matches Gallery's runtime pattern: a helper is a stateful runtime,
 * not a stateless service. Reusing the same instance lets the conversation context
 * survive across chat() calls without re-loading the model.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LlmHelperModule {

    @Binds
    @Singleton
    @Named(LlmHelperQualifiers.LITE_RT_LM)
    abstract fun bindLiteRtLlmHelper(impl: LiteRtLlmHelper): LlmModelHelper

    @Binds
    @Singleton
    @Named(LlmHelperQualifiers.LM_STUDIO)
    abstract fun bindLmStudioHelper(impl: LmStudioHelper): LlmModelHelper
}

object LlmHelperQualifiers {
    const val LITE_RT_LM = "liteRtLmHelper"
    const val LM_STUDIO = "lmStudioHelper"
}
