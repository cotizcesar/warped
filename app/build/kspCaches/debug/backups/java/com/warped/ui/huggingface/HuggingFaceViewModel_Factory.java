package com.warped.ui.huggingface;

import com.warped.data.local.download.ModelDownloadManager;
import com.warped.data.local.security.ApiKeyStore;
import com.warped.domain.repository.HuggingFaceRepository;
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
public final class HuggingFaceViewModel_Factory implements Factory<HuggingFaceViewModel> {
  private final Provider<HuggingFaceRepository> huggingFaceRepositoryProvider;

  private final Provider<ModelDownloadManager> downloadManagerProvider;

  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private HuggingFaceViewModel_Factory(
      Provider<HuggingFaceRepository> huggingFaceRepositoryProvider,
      Provider<ModelDownloadManager> downloadManagerProvider,
      Provider<ApiKeyStore> apiKeyStoreProvider) {
    this.huggingFaceRepositoryProvider = huggingFaceRepositoryProvider;
    this.downloadManagerProvider = downloadManagerProvider;
    this.apiKeyStoreProvider = apiKeyStoreProvider;
  }

  @Override
  public HuggingFaceViewModel get() {
    return newInstance(huggingFaceRepositoryProvider.get(), downloadManagerProvider.get(), apiKeyStoreProvider.get());
  }

  public static HuggingFaceViewModel_Factory create(
      Provider<HuggingFaceRepository> huggingFaceRepositoryProvider,
      Provider<ModelDownloadManager> downloadManagerProvider,
      Provider<ApiKeyStore> apiKeyStoreProvider) {
    return new HuggingFaceViewModel_Factory(huggingFaceRepositoryProvider, downloadManagerProvider, apiKeyStoreProvider);
  }

  public static HuggingFaceViewModel newInstance(HuggingFaceRepository huggingFaceRepository,
      ModelDownloadManager downloadManager, ApiKeyStore apiKeyStore) {
    return new HuggingFaceViewModel(huggingFaceRepository, downloadManager, apiKeyStore);
  }
}
