package com.warped.data.remote.provider;

import com.warped.data.local.inference.InputSanitizer;
import com.warped.data.local.inference.LiteRTLmProvider;
import com.warped.data.local.security.ApiKeyStore;
import com.warped.domain.llm.LlmModelHelper;
import dagger.Lazy;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("javax.inject.Named")
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

  private final Provider<LiteRTLmProvider> liteRTLmProvider;

  private final Provider<LlmModelHelper> liteRtLmHelperProvider;

  private final Provider<LmStudioHelper> lmStudioHelperProvider;

  private ProviderRouter_Factory(Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<InputSanitizer> inputSanitizerProvider, Provider<LiteRTLmProvider> liteRTLmProvider,
      Provider<LlmModelHelper> liteRtLmHelperProvider,
      Provider<LmStudioHelper> lmStudioHelperProvider) {
    this.apiKeyStoreProvider = apiKeyStoreProvider;
    this.inputSanitizerProvider = inputSanitizerProvider;
    this.liteRTLmProvider = liteRTLmProvider;
    this.liteRtLmHelperProvider = liteRtLmHelperProvider;
    this.lmStudioHelperProvider = lmStudioHelperProvider;
  }

  @Override
  public ProviderRouter get() {
    return newInstance(apiKeyStoreProvider.get(), inputSanitizerProvider.get(), DoubleCheck.lazy(liteRTLmProvider), DoubleCheck.lazy(liteRtLmHelperProvider), DoubleCheck.lazy(lmStudioHelperProvider));
  }

  public static ProviderRouter_Factory create(Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<InputSanitizer> inputSanitizerProvider, Provider<LiteRTLmProvider> liteRTLmProvider,
      Provider<LlmModelHelper> liteRtLmHelperProvider,
      Provider<LmStudioHelper> lmStudioHelperProvider) {
    return new ProviderRouter_Factory(apiKeyStoreProvider, inputSanitizerProvider, liteRTLmProvider, liteRtLmHelperProvider, lmStudioHelperProvider);
  }

  public static ProviderRouter newInstance(ApiKeyStore apiKeyStore, InputSanitizer inputSanitizer,
      Lazy<LiteRTLmProvider> liteRTLmProvider, Lazy<LlmModelHelper> liteRtLmHelper,
      Lazy<LmStudioHelper> lmStudioHelper) {
    return new ProviderRouter(apiKeyStore, inputSanitizer, liteRTLmProvider, liteRtLmHelper, lmStudioHelper);
  }
}
