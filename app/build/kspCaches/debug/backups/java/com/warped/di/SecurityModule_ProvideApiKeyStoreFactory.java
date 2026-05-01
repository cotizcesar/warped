package com.warped.di;

import com.warped.data.local.security.ApiKeyStore;
import com.warped.data.local.security.KeystoreManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
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
public final class SecurityModule_ProvideApiKeyStoreFactory implements Factory<ApiKeyStore> {
  private final Provider<KeystoreManager> keystoreManagerProvider;

  private SecurityModule_ProvideApiKeyStoreFactory(
      Provider<KeystoreManager> keystoreManagerProvider) {
    this.keystoreManagerProvider = keystoreManagerProvider;
  }

  @Override
  public ApiKeyStore get() {
    return provideApiKeyStore(keystoreManagerProvider.get());
  }

  public static SecurityModule_ProvideApiKeyStoreFactory create(
      Provider<KeystoreManager> keystoreManagerProvider) {
    return new SecurityModule_ProvideApiKeyStoreFactory(keystoreManagerProvider);
  }

  public static ApiKeyStore provideApiKeyStore(KeystoreManager keystoreManager) {
    return Preconditions.checkNotNullFromProvides(SecurityModule.INSTANCE.provideApiKeyStore(keystoreManager));
  }
}
