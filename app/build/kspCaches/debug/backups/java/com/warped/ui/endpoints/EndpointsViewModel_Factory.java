package com.warped.ui.endpoints;

import androidx.lifecycle.SavedStateHandle;
import com.warped.data.local.security.ApiKeyStore;
import com.warped.data.remote.provider.ProviderRouter;
import com.warped.domain.repository.EndpointRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class EndpointsViewModel_Factory implements Factory<EndpointsViewModel> {
  private final Provider<EndpointRepository> endpointRepositoryProvider;

  private final Provider<ProviderRouter> providerRouterProvider;

  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private EndpointsViewModel_Factory(Provider<EndpointRepository> endpointRepositoryProvider,
      Provider<ProviderRouter> providerRouterProvider, Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.endpointRepositoryProvider = endpointRepositoryProvider;
    this.providerRouterProvider = providerRouterProvider;
    this.apiKeyStoreProvider = apiKeyStoreProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public EndpointsViewModel get() {
    return newInstance(endpointRepositoryProvider.get(), providerRouterProvider.get(), apiKeyStoreProvider.get(), savedStateHandleProvider.get());
  }

  public static EndpointsViewModel_Factory create(
      Provider<EndpointRepository> endpointRepositoryProvider,
      Provider<ProviderRouter> providerRouterProvider, Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new EndpointsViewModel_Factory(endpointRepositoryProvider, providerRouterProvider, apiKeyStoreProvider, savedStateHandleProvider);
  }

  public static EndpointsViewModel newInstance(EndpointRepository endpointRepository,
      ProviderRouter providerRouter, ApiKeyStore apiKeyStore, SavedStateHandle savedStateHandle) {
    return new EndpointsViewModel(endpointRepository, providerRouter, apiKeyStore, savedStateHandle);
  }
}
