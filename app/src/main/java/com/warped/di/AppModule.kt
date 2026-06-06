package com.warped.di

import com.warped.util.lifecycle.AppLifecycleProvider
import com.warped.util.lifecycle.ProcessLifecycleAppLifecycleProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindAppLifecycleProvider(impl: ProcessLifecycleAppLifecycleProvider): AppLifecycleProvider
}
