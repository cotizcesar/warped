package com.warped.ui.settings;

import com.warped.data.local.preferences.AdvancedPreferences;
import com.warped.data.local.security.ApiKeyStore;
import com.warped.domain.repository.ChatRepository;
import com.warped.domain.repository.EndpointRepository;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<ChatRepository> chatRepositoryProvider;

  private final Provider<EndpointRepository> endpointRepositoryProvider;

  private final Provider<LocalModelRepository> localModelRepositoryProvider;

  private final Provider<PresetRepository> presetRepositoryProvider;

  private final Provider<ApiKeyStore> apiKeyStoreProvider;

  private final Provider<AdvancedPreferences> advancedPreferencesProvider;

  private SettingsViewModel_Factory(Provider<ChatRepository> chatRepositoryProvider,
      Provider<EndpointRepository> endpointRepositoryProvider,
      Provider<LocalModelRepository> localModelRepositoryProvider,
      Provider<PresetRepository> presetRepositoryProvider,
      Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<AdvancedPreferences> advancedPreferencesProvider) {
    this.chatRepositoryProvider = chatRepositoryProvider;
    this.endpointRepositoryProvider = endpointRepositoryProvider;
    this.localModelRepositoryProvider = localModelRepositoryProvider;
    this.presetRepositoryProvider = presetRepositoryProvider;
    this.apiKeyStoreProvider = apiKeyStoreProvider;
    this.advancedPreferencesProvider = advancedPreferencesProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(chatRepositoryProvider.get(), endpointRepositoryProvider.get(), localModelRepositoryProvider.get(), presetRepositoryProvider.get(), apiKeyStoreProvider.get(), advancedPreferencesProvider.get());
  }

  public static SettingsViewModel_Factory create(Provider<ChatRepository> chatRepositoryProvider,
      Provider<EndpointRepository> endpointRepositoryProvider,
      Provider<LocalModelRepository> localModelRepositoryProvider,
      Provider<PresetRepository> presetRepositoryProvider,
      Provider<ApiKeyStore> apiKeyStoreProvider,
      Provider<AdvancedPreferences> advancedPreferencesProvider) {
    return new SettingsViewModel_Factory(chatRepositoryProvider, endpointRepositoryProvider, localModelRepositoryProvider, presetRepositoryProvider, apiKeyStoreProvider, advancedPreferencesProvider);
  }

  public static SettingsViewModel newInstance(ChatRepository chatRepository,
      EndpointRepository endpointRepository, LocalModelRepository localModelRepository,
      PresetRepository presetRepository, ApiKeyStore apiKeyStore,
      AdvancedPreferences advancedPreferences) {
    return new SettingsViewModel(chatRepository, endpointRepository, localModelRepository, presetRepository, apiKeyStore, advancedPreferences);
  }
}
