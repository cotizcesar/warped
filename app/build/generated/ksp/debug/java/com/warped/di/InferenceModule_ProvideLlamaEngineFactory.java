package com.warped.di;

import com.warped.data.local.inference.LlamaEngine;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class InferenceModule_ProvideLlamaEngineFactory implements Factory<LlamaEngine> {
  @Override
  public LlamaEngine get() {
    return provideLlamaEngine();
  }

  public static InferenceModule_ProvideLlamaEngineFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LlamaEngine provideLlamaEngine() {
    return Preconditions.checkNotNullFromProvides(InferenceModule.INSTANCE.provideLlamaEngine());
  }

  private static final class InstanceHolder {
    static final InferenceModule_ProvideLlamaEngineFactory INSTANCE = new InferenceModule_ProvideLlamaEngineFactory();
  }
}
