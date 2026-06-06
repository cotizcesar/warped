package com.warped.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import okhttp3.Interceptor;
import okhttp3.logging.HttpLoggingInterceptor;

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
public final class NetworkModule_ProvideLoggingInterceptorFactory implements Factory<Interceptor> {
  private final Provider<HttpLoggingInterceptor> bodyProvider;

  private NetworkModule_ProvideLoggingInterceptorFactory(
      Provider<HttpLoggingInterceptor> bodyProvider) {
    this.bodyProvider = bodyProvider;
  }

  @Override
  public Interceptor get() {
    return provideLoggingInterceptor(bodyProvider.get());
  }

  public static NetworkModule_ProvideLoggingInterceptorFactory create(
      Provider<HttpLoggingInterceptor> bodyProvider) {
    return new NetworkModule_ProvideLoggingInterceptorFactory(bodyProvider);
  }

  public static Interceptor provideLoggingInterceptor(HttpLoggingInterceptor body) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideLoggingInterceptor(body));
  }
}
