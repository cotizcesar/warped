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
public final class HttpClientFactory_Factory implements Factory<HttpClientFactory> {
  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private HttpClientFactory_Factory(Provider<ApiKeyStore> apiKeyStoreProvider) {
    this.apiKeyStoreProvider = apiKeyStoreProvider;
  }

  @Override
  public HttpClientFactory get() {
    return newInstance(apiKeyStoreProvider.get());
  }

  public static HttpClientFactory_Factory create(Provider<ApiKeyStore> apiKeyStoreProvider) {
    return new HttpClientFactory_Factory(apiKeyStoreProvider);
  }

  public static HttpClientFactory newInstance(ApiKeyStore apiKeyStore) {
    return new HttpClientFactory(apiKeyStore);
  }
}
