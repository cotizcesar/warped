package com.warped.di;

import com.warped.data.local.inference.LlamaEngine;
import com.warped.data.local.inference.LocalLlmProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class InferenceModule_ProvideLocalLlmProviderFactory implements Factory<LocalLlmProvider> {
  private final Provider<LlamaEngine> llamaEngineProvider;

  private InferenceModule_ProvideLocalLlmProviderFactory(
      Provider<LlamaEngine> llamaEngineProvider) {
    this.llamaEngineProvider = llamaEngineProvider;
  }

  @Override
  public LocalLlmProvider get() {
    return provideLocalLlmProvider(llamaEngineProvider.get());
  }

  public static InferenceModule_ProvideLocalLlmProviderFactory create(
      Provider<LlamaEngine> llamaEngineProvider) {
    return new InferenceModule_ProvideLocalLlmProviderFactory(llamaEngineProvider);
  }

  public static LocalLlmProvider provideLocalLlmProvider(LlamaEngine llamaEngine) {
    return Preconditions.checkNotNullFromProvides(InferenceModule.INSTANCE.provideLocalLlmProvider(llamaEngine));
  }
}
