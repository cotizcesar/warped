package com.warped.domain.model;

import com.warped.data.local.security.KeystoreManager;
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
public final class ActiveModelSelection_Factory implements Factory<ActiveModelSelection> {
  private final Provider<KeystoreManager> keystoreManagerProvider;

  private ActiveModelSelection_Factory(Provider<KeystoreManager> keystoreManagerProvider) {
    this.keystoreManagerProvider = keystoreManagerProvider;
  }

  @Override
  public ActiveModelSelection get() {
    return newInstance(keystoreManagerProvider.get());
  }

  public static ActiveModelSelection_Factory create(
      Provider<KeystoreManager> keystoreManagerProvider) {
    return new ActiveModelSelection_Factory(keystoreManagerProvider);
  }

  public static ActiveModelSelection newInstance(KeystoreManager keystoreManager) {
    return new ActiveModelSelection(keystoreManager);
  }
}
