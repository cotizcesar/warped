package com.warped.ui.presets;

import com.warped.data.local.inference.EngineManager;
import com.warped.data.local.inference.MemoryChecker;
import com.warped.domain.model.ActiveModelSelection;
import com.warped.domain.model.ParameterStore;
import com.warped.domain.repository.LocalModelRepository;
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

  private final Provider<EngineManager> engineManagerProvider;

  private final Provider<MemoryChecker> memoryCheckerProvider;

  private final Provider<ActiveModelSelection> activeModelSelectionProvider;

  private final Provider<LocalModelRepository> localModelRepositoryProvider;

  private PresetsViewModel_Factory(Provider<PresetRepository> presetRepositoryProvider,
      Provider<ParameterStore> parameterStoreProvider,
      Provider<EngineManager> engineManagerProvider, Provider<MemoryChecker> memoryCheckerProvider,
      Provider<ActiveModelSelection> activeModelSelectionProvider,
      Provider<LocalModelRepository> localModelRepositoryProvider) {
    this.presetRepositoryProvider = presetRepositoryProvider;
    this.parameterStoreProvider = parameterStoreProvider;
    this.engineManagerProvider = engineManagerProvider;
    this.memoryCheckerProvider = memoryCheckerProvider;
    this.activeModelSelectionProvider = activeModelSelectionProvider;
    this.localModelRepositoryProvider = localModelRepositoryProvider;
  }

  @Override
  public PresetsViewModel get() {
    return newInstance(presetRepositoryProvider.get(), parameterStoreProvider.get(), engineManagerProvider.get(), memoryCheckerProvider.get(), activeModelSelectionProvider.get(), localModelRepositoryProvider.get());
  }

  public static PresetsViewModel_Factory create(Provider<PresetRepository> presetRepositoryProvider,
      Provider<ParameterStore> parameterStoreProvider,
      Provider<EngineManager> engineManagerProvider, Provider<MemoryChecker> memoryCheckerProvider,
      Provider<ActiveModelSelection> activeModelSelectionProvider,
      Provider<LocalModelRepository> localModelRepositoryProvider) {
    return new PresetsViewModel_Factory(presetRepositoryProvider, parameterStoreProvider, engineManagerProvider, memoryCheckerProvider, activeModelSelectionProvider, localModelRepositoryProvider);
  }

  public static PresetsViewModel newInstance(PresetRepository presetRepository,
      ParameterStore parameterStore, EngineManager engineManager, MemoryChecker memoryChecker,
      ActiveModelSelection activeModelSelection, LocalModelRepository localModelRepository) {
    return new PresetsViewModel(presetRepository, parameterStore, engineManager, memoryChecker, activeModelSelection, localModelRepository);
  }
}
