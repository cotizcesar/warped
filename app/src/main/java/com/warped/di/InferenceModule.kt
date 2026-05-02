package com.warped.di

import android.content.Context
import com.warped.data.local.inference.LlamaEngine
import com.warped.data.local.inference.LocalLlmProvider
import com.warped.data.local.inference.BackendDetector
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.LiteRTLmEngine
import com.warped.data.local.inference.MemoryChecker
import com.warped.data.local.inference.ModelImportManager
import com.warped.data.repository.LocalModelRepositoryImpl
import com.warped.domain.repository.LocalModelRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {

    @Provides
    @Singleton
    fun provideLlamaEngine(): LlamaEngine = LlamaEngine()

    @Provides
    @Singleton
    fun provideLocalLlmProvider(llamaEngine: LlamaEngine): LocalLlmProvider =
        LocalLlmProvider(llamaEngine)

    @Provides
    @Singleton
    fun provideMemoryChecker(
        @ApplicationContext context: Context
    ): MemoryChecker = MemoryChecker(context)

    @Provides
    @Singleton
    fun provideBackendDetector(): BackendDetector = BackendDetector()

    @Provides
    @Singleton
    fun provideLiteRTLmEngine(): LiteRTLmEngine = LiteRTLmEngine()

    @Provides
    @Singleton
    fun provideEngineManager(
        llamaEngine: LlamaEngine,
        liteRTLmEngine: LiteRTLmEngine,
        backendDetector: BackendDetector
    ): EngineManager = EngineManager(llamaEngine, liteRTLmEngine, backendDetector)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalModelRepositoryBinder {
    @Binds
    @Singleton
    abstract fun bindLocalModelRepository(impl: LocalModelRepositoryImpl): LocalModelRepository
}
