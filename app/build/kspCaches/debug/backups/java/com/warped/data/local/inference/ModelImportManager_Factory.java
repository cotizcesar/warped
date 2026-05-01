package com.warped.data.local.inference;

import android.content.Context;
import com.warped.domain.repository.LocalModelRepository;
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
public final class ModelImportManager_Factory implements Factory<ModelImportManager> {
  private final Provider<Context> contextProvider;

  private final Provider<LocalModelRepository> localModelRepositoryProvider;

  private ModelImportManager_Factory(Provider<Context> contextProvider,
      Provider<LocalModelRepository> localModelRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.localModelRepositoryProvider = localModelRepositoryProvider;
  }

  @Override
  public ModelImportManager get() {
    return newInstance(contextProvider.get(), localModelRepositoryProvider.get());
  }

  public static ModelImportManager_Factory create(Provider<Context> contextProvider,
      Provider<LocalModelRepository> localModelRepositoryProvider) {
    return new ModelImportManager_Factory(contextProvider, localModelRepositoryProvider);
  }

  public static ModelImportManager newInstance(Context context,
      LocalModelRepository localModelRepository) {
    return new ModelImportManager(context, localModelRepository);
  }
}
