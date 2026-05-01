package com.warped;

import android.app.Activity;
import android.app.Service;
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.warped.data.local.db.AppDatabase;
import com.warped.data.local.db.dao.ConversationDao;
import com.warped.data.local.db.dao.LocalModelDao;
import com.warped.data.local.db.dao.MessageDao;
import com.warped.data.local.db.dao.PresetDao;
import com.warped.data.local.db.dao.RemoteEndpointDao;
import com.warped.data.local.download.ModelDownloadManager;
import com.warped.data.local.inference.LlamaEngine;
import com.warped.data.local.inference.LocalLlmProvider;
import com.warped.data.local.inference.MemoryChecker;
import com.warped.data.local.inference.ModelImportManager;
import com.warped.data.local.security.ApiKeyStore;
import com.warped.data.local.security.KeystoreManager;
import com.warped.data.remote.network.AuthInterceptor;
import com.warped.data.remote.provider.ProviderRouter;
import com.warped.data.repository.ChatRepositoryImpl;
import com.warped.data.repository.EndpointRepositoryImpl;
import com.warped.data.repository.HuggingFaceRepositoryImpl;
import com.warped.data.repository.LocalModelRepositoryImpl;
import com.warped.data.repository.PresetRepositoryImpl;
import com.warped.di.DatabaseModule_ProvideConversationDaoFactory;
import com.warped.di.DatabaseModule_ProvideDatabaseFactory;
import com.warped.di.DatabaseModule_ProvideLocalModelDaoFactory;
import com.warped.di.DatabaseModule_ProvideMessageDaoFactory;
import com.warped.di.DatabaseModule_ProvidePresetDaoFactory;
import com.warped.di.DatabaseModule_ProvideRemoteEndpointDaoFactory;
import com.warped.di.InferenceModule_ProvideLlamaEngineFactory;
import com.warped.di.InferenceModule_ProvideLocalLlmProviderFactory;
import com.warped.di.InferenceModule_ProvideMemoryCheckerFactory;
import com.warped.di.NetworkModule_ProvideJsonFactory;
import com.warped.di.NetworkModule_ProvideLoggingInterceptorFactory;
import com.warped.di.NetworkModule_ProvideOkHttpClientFactory;
import com.warped.di.SecurityModule_ProvideApiKeyStoreFactory;
import com.warped.di.SecurityModule_ProvideKeystoreManagerFactory;
import com.warped.domain.model.ActiveModelSelection;
import com.warped.domain.model.ParameterStore;
import com.warped.ui.chat.ChatViewModel;
import com.warped.ui.chat.ChatViewModel_HiltModules;
import com.warped.ui.chat.ChatViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.warped.ui.chat.ChatViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.warped.ui.endpoints.EndpointsViewModel;
import com.warped.ui.endpoints.EndpointsViewModel_HiltModules;
import com.warped.ui.endpoints.EndpointsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.warped.ui.endpoints.EndpointsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.warped.ui.huggingface.HuggingFaceViewModel;
import com.warped.ui.huggingface.HuggingFaceViewModel_HiltModules;
import com.warped.ui.huggingface.HuggingFaceViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.warped.ui.huggingface.HuggingFaceViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.warped.ui.models.ModelsViewModel;
import com.warped.ui.models.ModelsViewModel_HiltModules;
import com.warped.ui.models.ModelsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.warped.ui.models.ModelsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.warped.ui.presets.PresetsViewModel;
import com.warped.ui.presets.PresetsViewModel_HiltModules;
import com.warped.ui.presets.PresetsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.warped.ui.presets.PresetsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import com.warped.ui.settings.SettingsViewModel;
import com.warped.ui.settings.SettingsViewModel_HiltModules;
import com.warped.ui.settings.SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey;
import com.warped.ui.settings.SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import kotlinx.serialization.json.Json;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

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
public final class DaggerWarpedApplication_HiltComponents_SingletonC {
  private DaggerWarpedApplication_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public WarpedApplication_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements WarpedApplication_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public WarpedApplication_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements WarpedApplication_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public WarpedApplication_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements WarpedApplication_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public WarpedApplication_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements WarpedApplication_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public WarpedApplication_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements WarpedApplication_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public WarpedApplication_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements WarpedApplication_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public WarpedApplication_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements WarpedApplication_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public WarpedApplication_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends WarpedApplication_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends WarpedApplication_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    FragmentCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends WarpedApplication_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends WarpedApplication_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    ActivityCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(6).put(ChatViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ChatViewModel_HiltModules.KeyModule.provide()).put(EndpointsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, EndpointsViewModel_HiltModules.KeyModule.provide()).put(HuggingFaceViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, HuggingFaceViewModel_HiltModules.KeyModule.provide()).put(ModelsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, ModelsViewModel_HiltModules.KeyModule.provide()).put(PresetsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, PresetsViewModel_HiltModules.KeyModule.provide()).put(SettingsViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, SettingsViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }
  }

  private static final class ViewModelCImpl extends WarpedApplication_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    Provider<ChatViewModel> chatViewModelProvider;

    Provider<EndpointsViewModel> endpointsViewModelProvider;

    Provider<HuggingFaceViewModel> huggingFaceViewModelProvider;

    Provider<ModelsViewModel> modelsViewModelProvider;

    Provider<PresetsViewModel> presetsViewModelProvider;

    Provider<SettingsViewModel> settingsViewModelProvider;

    ViewModelCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        SavedStateHandle savedStateHandleParam, ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.chatViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.endpointsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.huggingFaceViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.modelsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.presetsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(6).put(ChatViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (chatViewModelProvider))).put(EndpointsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (endpointsViewModelProvider))).put(HuggingFaceViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (huggingFaceViewModelProvider))).put(ModelsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (modelsViewModelProvider))).put(PresetsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (presetsViewModelProvider))).put(SettingsViewModel_HiltModules_BindsModule_Binds_LazyMapKey.lazyClassKeyName, ((Provider) (settingsViewModelProvider))).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.warped.ui.chat.ChatViewModel
          return (T) new ChatViewModel(singletonCImpl.chatRepositoryImplProvider.get(), singletonCImpl.endpointRepositoryImplProvider.get(), singletonCImpl.localModelRepositoryImplProvider.get(), singletonCImpl.activeModelSelectionProvider.get(), singletonCImpl.providerRouterProvider.get(), viewModelCImpl.savedStateHandle, singletonCImpl.parameterStoreProvider.get(), singletonCImpl.provideLlamaEngineProvider.get());

          case 1: // com.warped.ui.endpoints.EndpointsViewModel
          return (T) new EndpointsViewModel(singletonCImpl.endpointRepositoryImplProvider.get(), singletonCImpl.providerRouterProvider.get(), singletonCImpl.provideApiKeyStoreProvider.get(), viewModelCImpl.savedStateHandle);

          case 2: // com.warped.ui.huggingface.HuggingFaceViewModel
          return (T) new HuggingFaceViewModel(singletonCImpl.huggingFaceRepositoryImplProvider.get(), singletonCImpl.modelDownloadManagerProvider.get(), singletonCImpl.provideMemoryCheckerProvider.get());

          case 3: // com.warped.ui.models.ModelsViewModel
          return (T) new ModelsViewModel(singletonCImpl.localModelRepositoryImplProvider.get(), singletonCImpl.endpointRepositoryImplProvider.get(), singletonCImpl.activeModelSelectionProvider.get(), singletonCImpl.modelImportManagerProvider.get(), singletonCImpl.modelDownloadManagerProvider.get(), singletonCImpl.provideMemoryCheckerProvider.get(), singletonCImpl.provideApiKeyStoreProvider.get(), singletonCImpl.providerRouterProvider.get());

          case 4: // com.warped.ui.presets.PresetsViewModel
          return (T) new PresetsViewModel(singletonCImpl.presetRepositoryImplProvider.get(), singletonCImpl.parameterStoreProvider.get());

          case 5: // com.warped.ui.settings.SettingsViewModel
          return (T) new SettingsViewModel(singletonCImpl.chatRepositoryImplProvider.get(), singletonCImpl.endpointRepositoryImplProvider.get(), singletonCImpl.localModelRepositoryImplProvider.get(), singletonCImpl.presetRepositoryImplProvider.get(), singletonCImpl.provideApiKeyStoreProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends WarpedApplication_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends WarpedApplication_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends WarpedApplication_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    Provider<AppDatabase> provideDatabaseProvider;

    Provider<ChatRepositoryImpl> chatRepositoryImplProvider;

    Provider<KeystoreManager> provideKeystoreManagerProvider;

    Provider<ApiKeyStore> provideApiKeyStoreProvider;

    Provider<EndpointRepositoryImpl> endpointRepositoryImplProvider;

    Provider<LocalModelRepositoryImpl> localModelRepositoryImplProvider;

    Provider<ActiveModelSelection> activeModelSelectionProvider;

    Provider<LlamaEngine> provideLlamaEngineProvider;

    Provider<LocalLlmProvider> provideLocalLlmProvider;

    Provider<ProviderRouter> providerRouterProvider;

    Provider<ParameterStore> parameterStoreProvider;

    Provider<HttpLoggingInterceptor> provideLoggingInterceptorProvider;

    Provider<AuthInterceptor> authInterceptorProvider;

    Provider<OkHttpClient> provideOkHttpClientProvider;

    Provider<Json> provideJsonProvider;

    Provider<HuggingFaceRepositoryImpl> huggingFaceRepositoryImplProvider;

    Provider<ModelDownloadManager> modelDownloadManagerProvider;

    Provider<MemoryChecker> provideMemoryCheckerProvider;

    Provider<ModelImportManager> modelImportManagerProvider;

    Provider<PresetRepositoryImpl> presetRepositoryImplProvider;

    SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    ConversationDao conversationDao() {
      return DatabaseModule_ProvideConversationDaoFactory.provideConversationDao(provideDatabaseProvider.get());
    }

    MessageDao messageDao() {
      return DatabaseModule_ProvideMessageDaoFactory.provideMessageDao(provideDatabaseProvider.get());
    }

    RemoteEndpointDao remoteEndpointDao() {
      return DatabaseModule_ProvideRemoteEndpointDaoFactory.provideRemoteEndpointDao(provideDatabaseProvider.get());
    }

    LocalModelDao localModelDao() {
      return DatabaseModule_ProvideLocalModelDaoFactory.provideLocalModelDao(provideDatabaseProvider.get());
    }

    PresetDao presetDao() {
      return DatabaseModule_ProvidePresetDaoFactory.providePresetDao(provideDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<AppDatabase>(singletonCImpl, 1));
      this.chatRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<ChatRepositoryImpl>(singletonCImpl, 0));
      this.provideKeystoreManagerProvider = DoubleCheck.provider(new SwitchingProvider<KeystoreManager>(singletonCImpl, 4));
      this.provideApiKeyStoreProvider = DoubleCheck.provider(new SwitchingProvider<ApiKeyStore>(singletonCImpl, 3));
      this.endpointRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<EndpointRepositoryImpl>(singletonCImpl, 2));
      this.localModelRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<LocalModelRepositoryImpl>(singletonCImpl, 5));
      this.activeModelSelectionProvider = DoubleCheck.provider(new SwitchingProvider<ActiveModelSelection>(singletonCImpl, 6));
      this.provideLlamaEngineProvider = DoubleCheck.provider(new SwitchingProvider<LlamaEngine>(singletonCImpl, 9));
      this.provideLocalLlmProvider = DoubleCheck.provider(new SwitchingProvider<LocalLlmProvider>(singletonCImpl, 8));
      this.providerRouterProvider = DoubleCheck.provider(new SwitchingProvider<ProviderRouter>(singletonCImpl, 7));
      this.parameterStoreProvider = DoubleCheck.provider(new SwitchingProvider<ParameterStore>(singletonCImpl, 10));
      this.provideLoggingInterceptorProvider = DoubleCheck.provider(new SwitchingProvider<HttpLoggingInterceptor>(singletonCImpl, 13));
      this.authInterceptorProvider = DoubleCheck.provider(new SwitchingProvider<AuthInterceptor>(singletonCImpl, 14));
      this.provideOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 12));
      this.provideJsonProvider = DoubleCheck.provider(new SwitchingProvider<Json>(singletonCImpl, 15));
      this.huggingFaceRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<HuggingFaceRepositoryImpl>(singletonCImpl, 11));
      this.modelDownloadManagerProvider = DoubleCheck.provider(new SwitchingProvider<ModelDownloadManager>(singletonCImpl, 16));
      this.provideMemoryCheckerProvider = DoubleCheck.provider(new SwitchingProvider<MemoryChecker>(singletonCImpl, 17));
      this.modelImportManagerProvider = DoubleCheck.provider(new SwitchingProvider<ModelImportManager>(singletonCImpl, 18));
      this.presetRepositoryImplProvider = DoubleCheck.provider(new SwitchingProvider<PresetRepositoryImpl>(singletonCImpl, 19));
    }

    @Override
    public void injectWarpedApplication(WarpedApplication warpedApplication) {
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @Override
      @SuppressWarnings("unchecked")
      public T get() {
        switch (id) {
          case 0: // com.warped.data.repository.ChatRepositoryImpl
          return (T) new ChatRepositoryImpl(singletonCImpl.conversationDao(), singletonCImpl.messageDao());

          case 1: // com.warped.data.local.db.AppDatabase
          return (T) DatabaseModule_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 2: // com.warped.data.repository.EndpointRepositoryImpl
          return (T) new EndpointRepositoryImpl(singletonCImpl.remoteEndpointDao(), singletonCImpl.provideApiKeyStoreProvider.get());

          case 3: // com.warped.data.local.security.ApiKeyStore
          return (T) SecurityModule_ProvideApiKeyStoreFactory.provideApiKeyStore(singletonCImpl.provideKeystoreManagerProvider.get());

          case 4: // com.warped.data.local.security.KeystoreManager
          return (T) SecurityModule_ProvideKeystoreManagerFactory.provideKeystoreManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 5: // com.warped.data.repository.LocalModelRepositoryImpl
          return (T) new LocalModelRepositoryImpl(singletonCImpl.localModelDao());

          case 6: // com.warped.domain.model.ActiveModelSelection
          return (T) new ActiveModelSelection();

          case 7: // com.warped.data.remote.provider.ProviderRouter
          return (T) new ProviderRouter(singletonCImpl.provideApiKeyStoreProvider.get(), DoubleCheck.lazy(singletonCImpl.provideLocalLlmProvider));

          case 8: // com.warped.data.local.inference.LocalLlmProvider
          return (T) InferenceModule_ProvideLocalLlmProviderFactory.provideLocalLlmProvider(singletonCImpl.provideLlamaEngineProvider.get());

          case 9: // com.warped.data.local.inference.LlamaEngine
          return (T) InferenceModule_ProvideLlamaEngineFactory.provideLlamaEngine();

          case 10: // com.warped.domain.model.ParameterStore
          return (T) new ParameterStore();

          case 11: // com.warped.data.repository.HuggingFaceRepositoryImpl
          return (T) new HuggingFaceRepositoryImpl(singletonCImpl.provideOkHttpClientProvider.get(), singletonCImpl.provideJsonProvider.get());

          case 12: // okhttp3.OkHttpClient
          return (T) NetworkModule_ProvideOkHttpClientFactory.provideOkHttpClient(singletonCImpl.provideLoggingInterceptorProvider.get(), singletonCImpl.authInterceptorProvider.get());

          case 13: // okhttp3.logging.HttpLoggingInterceptor
          return (T) NetworkModule_ProvideLoggingInterceptorFactory.provideLoggingInterceptor();

          case 14: // com.warped.data.remote.network.AuthInterceptor
          return (T) new AuthInterceptor(singletonCImpl.provideApiKeyStoreProvider.get());

          case 15: // kotlinx.serialization.json.Json
          return (T) NetworkModule_ProvideJsonFactory.provideJson();

          case 16: // com.warped.data.local.download.ModelDownloadManager
          return (T) new ModelDownloadManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideOkHttpClientProvider.get(), singletonCImpl.localModelRepositoryImplProvider.get());

          case 17: // com.warped.data.local.inference.MemoryChecker
          return (T) InferenceModule_ProvideMemoryCheckerFactory.provideMemoryChecker(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 18: // com.warped.data.local.inference.ModelImportManager
          return (T) new ModelImportManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.localModelRepositoryImplProvider.get());

          case 19: // com.warped.data.repository.PresetRepositoryImpl
          return (T) new PresetRepositoryImpl(singletonCImpl.presetDao());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}
