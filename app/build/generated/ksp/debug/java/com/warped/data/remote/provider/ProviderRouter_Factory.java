package com.warped.data.remote.provider;

import com.warped.data.local.inference.InputSanitizer;
import com.warped.data.local.inference.LiteRTLmProvider;
import com.warped.data.local.inference.LocalLlmProvider;
import com.warped.data.local.security.ApiKeyStore;
import dagger.Lazy;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
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
public final class ProviderRouter_Factory implements Factory<ProviderRouter> {
  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private final Provider<InputSanitizer> inputSanitizerProvider;

  private final Provider<LocalLlmProvider> localLlmProvider;

  private final Provider<LiteRTLmProvider> liteRTLmProvider;

  private ProviderRouter_Factory(Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<InputSanitizer> inputSanitizerProvider, Provider<LocalLlmProvider> localLlmProvider,
      Provider<LiteRTLmProvider> liteRTLmProvider) {
    this.apiKeyStoreProvider = apiKeyStoreProvider;
    this.inputSanitizerProvider = inputSanitizerProvider;
    this.localLlmProvider = localLlmProvider;
    this.liteRTLmProvider = liteRTLmProvider;
  }

  @Override
  public ProviderRouter get() {
    return newInstance(apiKeyStoreProvider.get(), inputSanitizerProvider.get(), DoubleCheck.lazy(localLlmProvider), DoubleCheck.lazy(liteRTLmProvider));
  }

  public static ProviderRouter_Factory create(Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<InputSanitizer> inputSanitizerProvider, Provider<LocalLlmProvider> localLlmProvider,
      Provider<LiteRTLmProvider> liteRTLmProvider) {
    return new ProviderRouter_Factory(apiKeyStoreProvider, inputSanitizerProvider, localLlmProvider, liteRTLmProvider);
  }

  public static ProviderRouter newInstance(ApiKeyStore apiKeyStore, InputSanitizer inputSanitizer,
      Lazy<LocalLlmProvider> localLlmProvider, Lazy<LiteRTLmProvider> liteRTLmProvider) {
    return new ProviderRouter(apiKeyStore, inputSanitizer, localLlmProvider, liteRTLmProvider);
  }
}
