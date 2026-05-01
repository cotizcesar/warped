package com.warped.data.remote.network;

import com.warped.data.local.security.ApiKeyStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class AuthInterceptor_Factory implements Factory<AuthInterceptor> {
  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private AuthInterceptor_Factory(Provider<ApiKeyStore> apiKeyStoreProvider) {
    this.apiKeyStoreProvider = apiKeyStoreProvider;
  }

  @Override
  public AuthInterceptor get() {
    return newInstance(apiKeyStoreProvider.get());
  }

  public static AuthInterceptor_Factory create(Provider<ApiKeyStore> apiKeyStoreProvider) {
    return new AuthInterceptor_Factory(apiKeyStoreProvider);
  }

  public static AuthInterceptor newInstance(ApiKeyStore apiKeyStore) {
    return new AuthInterceptor(apiKeyStore);
  }
}
