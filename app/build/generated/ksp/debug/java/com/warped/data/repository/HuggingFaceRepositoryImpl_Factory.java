package com.warped.data.repository;

import com.warped.data.remote.network.HuggingFaceAuthInterceptor;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.serialization.json.Json;
import okhttp3.OkHttpClient;

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
public final class HuggingFaceRepositoryImpl_Factory implements Factory<HuggingFaceRepositoryImpl> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<Json> jsonProvider;

  private final Provider<HuggingFaceAuthInterceptor> huggingFaceAuthInterceptorProvider;

  private HuggingFaceRepositoryImpl_Factory(Provider<OkHttpClient> okHttpClientProvider,
      Provider<Json> jsonProvider,
      Provider<HuggingFaceAuthInterceptor> huggingFaceAuthInterceptorProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.jsonProvider = jsonProvider;
    this.huggingFaceAuthInterceptorProvider = huggingFaceAuthInterceptorProvider;
  }

  @Override
  public HuggingFaceRepositoryImpl get() {
    return newInstance(okHttpClientProvider.get(), jsonProvider.get(), huggingFaceAuthInterceptorProvider.get());
  }

  public static HuggingFaceRepositoryImpl_Factory create(
      Provider<OkHttpClient> okHttpClientProvider, Provider<Json> jsonProvider,
      Provider<HuggingFaceAuthInterceptor> huggingFaceAuthInterceptorProvider) {
    return new HuggingFaceRepositoryImpl_Factory(okHttpClientProvider, jsonProvider, huggingFaceAuthInterceptorProvider);
  }

  public static HuggingFaceRepositoryImpl newInstance(OkHttpClient okHttpClient, Json json,
      HuggingFaceAuthInterceptor huggingFaceAuthInterceptor) {
    return new HuggingFaceRepositoryImpl(okHttpClient, json, huggingFaceAuthInterceptor);
  }
}
