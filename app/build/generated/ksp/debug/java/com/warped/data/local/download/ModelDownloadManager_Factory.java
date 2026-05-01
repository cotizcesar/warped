package com.warped.data.local.download;

import android.content.Context;
import com.warped.domain.repository.LocalModelRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ModelDownloadManager_Factory implements Factory<ModelDownloadManager> {
  private final Provider<Context> contextProvider;

  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<LocalModelRepository> localModelRepositoryProvider;

  private ModelDownloadManager_Factory(Provider<Context> contextProvider,
      Provider<OkHttpClient> okHttpClientProvider,
      Provider<LocalModelRepository> localModelRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.okHttpClientProvider = okHttpClientProvider;
    this.localModelRepositoryProvider = localModelRepositoryProvider;
  }

  @Override
  public ModelDownloadManager get() {
    return newInstance(contextProvider.get(), okHttpClientProvider.get(), localModelRepositoryProvider.get());
  }

  public static ModelDownloadManager_Factory create(Provider<Context> contextProvider,
      Provider<OkHttpClient> okHttpClientProvider,
      Provider<LocalModelRepository> localModelRepositoryProvider) {
    return new ModelDownloadManager_Factory(contextProvider, okHttpClientProvider, localModelRepositoryProvider);
  }

  public static ModelDownloadManager newInstance(Context context, OkHttpClient okHttpClient,
      LocalModelRepository localModelRepository) {
    return new ModelDownloadManager(context, okHttpClient, localModelRepository);
  }
}
