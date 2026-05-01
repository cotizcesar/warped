package com.warped.di

import com.warped.data.repository.HuggingFaceRepositoryImpl
import com.warped.domain.repository.HuggingFaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HuggingFaceModule {
    @Binds
    @Singleton
    abstract fun bindHuggingFaceRepository(
        impl: HuggingFaceRepositoryImpl
    ): HuggingFaceRepository
}
