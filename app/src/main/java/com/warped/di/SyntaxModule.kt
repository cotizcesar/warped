package com.warped.di

import com.warped.data.highlighting.SyntaxHighlighterImpl
import com.warped.domain.highlighting.SyntaxHighlighter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyntaxModule {

    @Binds
    @Singleton
    abstract fun bindSyntaxHighlighter(
        impl: SyntaxHighlighterImpl,
    ): SyntaxHighlighter
}
