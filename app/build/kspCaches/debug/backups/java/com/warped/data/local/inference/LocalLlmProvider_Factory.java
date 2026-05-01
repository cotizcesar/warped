package com.warped.data.local.inference;

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
public final class LocalLlmProvider_Factory implements Factory<LocalLlmProvider> {
  private final Provider<LlamaEngine> llamaEngineProvider;

  private LocalLlmProvider_Factory(Provider<LlamaEngine> llamaEngineProvider) {
    this.llamaEngineProvider = llamaEngineProvider;
  }

  @Override
  public LocalLlmProvider get() {
    return newInstance(llamaEngineProvider.get());
  }

  public static LocalLlmProvider_Factory create(Provider<LlamaEngine> llamaEngineProvider) {
    return new LocalLlmProvider_Factory(llamaEngineProvider);
  }

  public static LocalLlmProvider newInstance(LlamaEngine llamaEngine) {
    return new LocalLlmProvider(llamaEngine);
  }
}
