package com.warped.di;

import android.content.Context;
import com.warped.data.local.inference.MemoryChecker;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class InferenceModule_ProvideMemoryCheckerFactory implements Factory<MemoryChecker> {
  private final Provider<Context> contextProvider;

  private InferenceModule_ProvideMemoryCheckerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public MemoryChecker get() {
    return provideMemoryChecker(contextProvider.get());
  }

  public static InferenceModule_ProvideMemoryCheckerFactory create(
      Provider<Context> contextProvider) {
    return new InferenceModule_ProvideMemoryCheckerFactory(contextProvider);
  }

  public static MemoryChecker provideMemoryChecker(Context context) {
    return Preconditions.checkNotNullFromProvides(InferenceModule.INSTANCE.provideMemoryChecker(context));
  }
}
