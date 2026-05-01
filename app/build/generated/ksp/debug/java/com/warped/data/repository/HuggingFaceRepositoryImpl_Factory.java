package com.warped.data.repository;

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

  private HuggingFaceRepositoryImpl_Factory(Provider<OkHttpClient> okHttpClientProvider,
      Provider<Json> jsonProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public HuggingFaceRepositoryImpl get() {
    return newInstance(okHttpClientProvider.get(), jsonProvider.get());
  }

  public static HuggingFaceRepositoryImpl_Factory create(
      Provider<OkHttpClient> okHttpClientProvider, Provider<Json> jsonProvider) {
    return new HuggingFaceRepositoryImpl_Factory(okHttpClientProvider, jsonProvider);
  }

  public static HuggingFaceRepositoryImpl newInstance(OkHttpClient okHttpClient, Json json) {
    return new HuggingFaceRepositoryImpl(okHttpClient, json);
  }
}
