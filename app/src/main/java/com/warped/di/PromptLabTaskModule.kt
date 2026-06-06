package com.warped.di

import com.warped.domain.prompt.PromptTemplate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import javax.inject.Named

@Module
@InstallIn(ViewModelComponent::class)
object PromptLabTaskModule {

    @Provides
    @Named("promptTemplate")
    fun provideAllTemplates(
        set: Set<@JvmSuppressWildcards PromptTemplate>,
    ): Set<PromptTemplate> = set
}
