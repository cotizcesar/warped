package com.warped.di

import android.content.Context
import com.warped.data.local.inference.InputSanitizer
import com.warped.data.local.inference.LiteRTLmProvider
import com.warped.data.local.inference.BackendDetector
import com.warped.data.local.inference.EngineManager
import com.warped.data.local.inference.LiteRTLmEngine
import com.warped.data.local.inference.LiteRtLmCacheManager
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
    fun provideBackendDetector(): BackendDetector = BackendDetector()

    @Provides
    @Singleton
    fun provideEngineManager(
        liteRTLmEngine: LiteRTLmEngine,
        backendDetector: BackendDetector,
        @ApplicationContext context: Context,
        cacheManager: LiteRtLmCacheManager,
    ): EngineManager = EngineManager(liteRTLmEngine, backendDetector, context, cacheManager)

    @Provides
    @Singleton
    fun provideInputSanitizer(): InputSanitizer = InputSanitizer()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalModelRepositoryBinder {
    @Binds
    @Singleton
    abstract fun bindLocalModelRepository(impl: LocalModelRepositoryImpl): LocalModelRepository
}
