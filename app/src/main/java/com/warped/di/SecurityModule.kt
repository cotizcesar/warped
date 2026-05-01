package com.warped.di

import android.content.Context
import com.warped.data.local.security.ApiKeyStore
import com.warped.data.local.security.KeystoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideKeystoreManager(@ApplicationContext context: Context): KeystoreManager =
        KeystoreManager(context)

    @Provides
    @Singleton
    fun provideApiKeyStore(keystoreManager: KeystoreManager): ApiKeyStore =
        ApiKeyStore(keystoreManager)
}
