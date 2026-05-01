package com.warped.data.repository;

import com.warped.data.local.db.dao.RemoteEndpointDao;
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
public final class EndpointRepositoryImpl_Factory implements Factory<EndpointRepositoryImpl> {
  private final Provider<RemoteEndpointDao> endpointDaoProvider;

  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private EndpointRepositoryImpl_Factory(Provider<RemoteEndpointDao> endpointDaoProvider,
      Provider<ApiKeyStore> apiKeyStoreProvider) {
    this.endpointDaoProvider = endpointDaoProvider;
    this.apiKeyStoreProvider = apiKeyStoreProvider;
  }

  @Override
  public EndpointRepositoryImpl get() {
    return newInstance(endpointDaoProvider.get(), apiKeyStoreProvider.get());
  }

  public static EndpointRepositoryImpl_Factory create(
      Provider<RemoteEndpointDao> endpointDaoProvider, Provider<ApiKeyStore> apiKeyStoreProvider) {
    return new EndpointRepositoryImpl_Factory(endpointDaoProvider, apiKeyStoreProvider);
  }

  public static EndpointRepositoryImpl newInstance(RemoteEndpointDao endpointDao,
      ApiKeyStore apiKeyStore) {
    return new EndpointRepositoryImpl(endpointDao, apiKeyStore);
  }
}
