package com.warped.data.local.inference;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
  @Override
  public LocalLlmProvider get() {
    return newInstance();
  }

  public static LocalLlmProvider_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static LocalLlmProvider newInstance() {
    return new LocalLlmProvider();
  }

  private static final class InstanceHolder {
    static final LocalLlmProvider_Factory INSTANCE = new LocalLlmProvider_Factory();
  }
}
