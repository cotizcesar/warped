package com.warped.ui.chat;

import androidx.lifecycle.SavedStateHandle;
import com.warped.data.local.inference.LlamaEngine;
import com.warped.data.remote.provider.ProviderRouter;
import com.warped.domain.model.ActiveModelSelection;
import com.warped.domain.model.ParameterStore;
import com.warped.domain.repository.ChatRepository;
import com.warped.domain.repository.EndpointRepository;
import com.warped.domain.repository.LocalModelRepository;
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
public final class ChatViewModel_Factory implements Factory<ChatViewModel> {
  private final Provider<ChatRepository> chatRepositoryProvider;

  private final Provider<EndpointRepository> endpointRepositoryProvider;

  private final Provider<LocalModelRepository> localModelRepositoryProvider;

  private final Provider<ActiveModelSelection> activeModelSelectionProvider;

  private final Provider<ProviderRouter> providerRouterProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<ParameterStore> parameterStoreProvider;

  private final Provider<LlamaEngine> llamaEngineProvider;

  private ChatViewModel_Factory(Provider<ChatRepository> chatRepositoryProvider,
      Provider<EndpointRepository> endpointRepositoryProvider,
      Provider<LocalModelRepository> localModelRepositoryProvider,
      Provider<ActiveModelSelection> activeModelSelectionProvider,
      Provider<ProviderRouter> providerRouterProvider,
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<ParameterStore> parameterStoreProvider, Provider<LlamaEngine> llamaEngineProvider) {
    this.chatRepositoryProvider = chatRepositoryProvider;
    this.endpointRepositoryProvider = endpointRepositoryProvider;
    this.localModelRepositoryProvider = localModelRepositoryProvider;
    this.activeModelSelectionProvider = activeModelSelectionProvider;
    this.providerRouterProvider = providerRouterProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.parameterStoreProvider = parameterStoreProvider;
    this.llamaEngineProvider = llamaEngineProvider;
  }

  @Override
  public ChatViewModel get() {
    return newInstance(chatRepositoryProvider.get(), endpointRepositoryProvider.get(), localModelRepositoryProvider.get(), activeModelSelectionProvider.get(), providerRouterProvider.get(), savedStateHandleProvider.get(), parameterStoreProvider.get(), llamaEngineProvider.get());
  }

  public static ChatViewModel_Factory create(Provider<ChatRepository> chatRepositoryProvider,
      Provider<EndpointRepository> endpointRepositoryProvider,
      Provider<LocalModelRepository> localModelRepositoryProvider,
      Provider<ActiveModelSelection> activeModelSelectionProvider,
      Provider<ProviderRouter> providerRouterProvider,
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<ParameterStore> parameterStoreProvider, Provider<LlamaEngine> llamaEngineProvider) {
    return new ChatViewModel_Factory(chatRepositoryProvider, endpointRepositoryProvider, localModelRepositoryProvider, activeModelSelectionProvider, providerRouterProvider, savedStateHandleProvider, parameterStoreProvider, llamaEngineProvider);
  }

  public static ChatViewModel newInstance(ChatRepository chatRepository,
      EndpointRepository endpointRepository, LocalModelRepository localModelRepository,
      ActiveModelSelection activeModelSelection, ProviderRouter providerRouter,
      SavedStateHandle savedStateHandle, ParameterStore parameterStore, LlamaEngine llamaEngine) {
    return new ChatViewModel(chatRepository, endpointRepository, localModelRepository, activeModelSelection, providerRouter, savedStateHandle, parameterStore, llamaEngine);
  }
}
