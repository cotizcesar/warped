package com.warped.data.local.security;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ApiKeyStore_Factory implements Factory<ApiKeyStore> {
  private final Provider<KeystoreManager> keystoreManagerProvider;

  private ApiKeyStore_Factory(Provider<KeystoreManager> keystoreManagerProvider) {
    this.keystoreManagerProvider = keystoreManagerProvider;
  }

  @Override
  public ApiKeyStore get() {
    return newInstance(keystoreManagerProvider.get());
  }

  public static ApiKeyStore_Factory create(Provider<KeystoreManager> keystoreManagerProvider) {
    return new ApiKeyStore_Factory(keystoreManagerProvider);
  }

  public static ApiKeyStore newInstance(KeystoreManager keystoreManager) {
    return new ApiKeyStore(keystoreManager);
  }
}
