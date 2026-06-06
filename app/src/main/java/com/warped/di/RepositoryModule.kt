package com.warped.di

import com.warped.data.repository.ChatRepositoryImpl
import com.warped.data.repository.EndpointRepositoryImpl
import com.warped.data.repository.ModelAllowlistRepositoryImpl
import com.warped.data.repository.ModelRepositoryImpl
import com.warped.data.repository.PresetRepositoryImpl
import com.warped.domain.repository.ChatRepository
import com.warped.domain.repository.EndpointRepository
import com.warped.domain.repository.ModelAllowlistRepository
import com.warped.domain.repository.ModelRepository
import com.warped.domain.repository.PresetRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindEndpointRepository(impl: EndpointRepositoryImpl): EndpointRepository

    @Binds
    @Singleton
    abstract fun bindModelRepository(impl: ModelRepositoryImpl): ModelRepository

    @Binds
    @Singleton
    abstract fun bindPresetRepository(impl: PresetRepositoryImpl): PresetRepository

    @Binds
    @Singleton
    abstract fun bindModelAllowlistRepository(impl: ModelAllowlistRepositoryImpl): ModelAllowlistRepository
}
