package com.warped.data.local.inference;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class MemoryChecker_Factory implements Factory<MemoryChecker> {
  private final Provider<Context> contextProvider;

  private MemoryChecker_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public MemoryChecker get() {
    return newInstance(contextProvider.get());
  }

  public static MemoryChecker_Factory create(Provider<Context> contextProvider) {
    return new MemoryChecker_Factory(contextProvider);
  }

  public static MemoryChecker newInstance(Context context) {
    return new MemoryChecker(context);
  }
}
