package com.warped.ui.models;

import com.warped.data.local.download.ModelDownloadManager;
import com.warped.data.local.inference.MemoryChecker;
import com.warped.data.local.inference.ModelImportManager;
import com.warped.data.local.security.ApiKeyStore;
import com.warped.data.remote.provider.ProviderRouter;
import com.warped.domain.model.ActiveModelSelection;
import com.warped.domain.repository.EndpointRepository;
import com.warped.domain.repository.LocalModelRepository;
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
public final class ModelsViewModel_Factory implements Factory<ModelsViewModel> {
  private final Provider<LocalModelRepository> localModelRepositoryProvider;

  private final Provider<EndpointRepository> endpointRepositoryProvider;

  private final Provider<ActiveModelSelection> activeModelSelectionProvider;

  private final Provider<ModelImportManager> modelImportManagerProvider;

  private final Provider<ModelDownloadManager> modelDownloadManagerProvider;

  private final Provider<MemoryChecker> memoryCheckerProvider;

  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private final Provider<ProviderRouter> providerRouterProvider;

  private ModelsViewModel_Factory(Provider<LocalModelRepository> localModelRepositoryProvider,
      Provider<EndpointRepository> endpointRepositoryProvider,
      Provider<ActiveModelSelection> activeModelSelectionProvider,
      Provider<ModelImportManager> modelImportManagerProvider,
      Provider<ModelDownloadManager> modelDownloadManagerProvider,
      Provider<MemoryChecker> memoryCheckerProvider, Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<ProviderRouter> providerRouterProvider) {
    this.localModelRepositoryProvider = localModelRepositoryProvider;
    this.endpointRepositoryProvider = endpointRepositoryProvider;
    this.activeModelSelectionProvider = activeModelSelectionProvider;
    this.modelImportManagerProvider = modelImportManagerProvider;
    this.modelDownloadManagerProvider = modelDownloadManagerProvider;
    this.memoryCheckerProvider = memoryCheckerProvider;
    this.apiKeyStoreProvider = apiKeyStoreProvider;
    this.providerRouterProvider = providerRouterProvider;
  }

  @Override
  public ModelsViewModel get() {
    return newInstance(localModelRepositoryProvider.get(), endpointRepositoryProvider.get(), activeModelSelectionProvider.get(), modelImportManagerProvider.get(), modelDownloadManagerProvider.get(), memoryCheckerProvider.get(), apiKeyStoreProvider.get(), providerRouterProvider.get());
  }

  public static ModelsViewModel_Factory create(
      Provider<LocalModelRepository> localModelRepositoryProvider,
      Provider<EndpointRepository> endpointRepositoryProvider,
      Provider<ActiveModelSelection> activeModelSelectionProvider,
      Provider<ModelImportManager> modelImportManagerProvider,
      Provider<ModelDownloadManager> modelDownloadManagerProvider,
      Provider<MemoryChecker> memoryCheckerProvider, Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<ProviderRouter> providerRouterProvider) {
    return new ModelsViewModel_Factory(localModelRepositoryProvider, endpointRepositoryProvider, activeModelSelectionProvider, modelImportManagerProvider, modelDownloadManagerProvider, memoryCheckerProvider, apiKeyStoreProvider, providerRouterProvider);
  }

  public static ModelsViewModel newInstance(LocalModelRepository localModelRepository,
      EndpointRepository endpointRepository, ActiveModelSelection activeModelSelection,
      ModelImportManager modelImportManager, ModelDownloadManager modelDownloadManager,
      MemoryChecker memoryChecker, ApiKeyStore apiKeyStore, ProviderRouter providerRouter) {
    return new ModelsViewModel(localModelRepository, endpointRepository, activeModelSelection, modelImportManager, modelDownloadManager, memoryChecker, apiKeyStore, providerRouter);
  }
}
