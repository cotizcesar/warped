package com.warped.ui.presets;

import com.warped.domain.model.ParameterStore;
import com.warped.domain.repository.PresetRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class PresetsViewModel_Factory implements Factory<PresetsViewModel> {
  private final Provider<PresetRepository> presetRepositoryProvider;

  private final Provider<ParameterStore> parameterStoreProvider;

  private PresetsViewModel_Factory(Provider<PresetRepository> presetRepositoryProvider,
      Provider<ParameterStore> parameterStoreProvider) {
    this.presetRepositoryProvider = presetRepositoryProvider;
    this.parameterStoreProvider = parameterStoreProvider;
  }

  @Override
  public PresetsViewModel get() {
    return newInstance(presetRepositoryProvider.get(), parameterStoreProvider.get());
  }

  public static PresetsViewModel_Factory create(Provider<PresetRepository> presetRepositoryProvider,
      Provider<ParameterStore> parameterStoreProvider) {
    return new PresetsViewModel_Factory(presetRepositoryProvider, parameterStoreProvider);
  }

  public static PresetsViewModel newInstance(PresetRepository presetRepository,
      ParameterStore parameterStore) {
    return new PresetsViewModel(presetRepository, parameterStore);
  }
}
