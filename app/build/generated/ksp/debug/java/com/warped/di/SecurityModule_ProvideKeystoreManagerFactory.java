package com.warped.di;

import android.content.Context;
import com.warped.data.local.security.KeystoreManager;
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
public final class SecurityModule_ProvideKeystoreManagerFactory implements Factory<KeystoreManager> {
  private final Provider<Context> contextProvider;

  private SecurityModule_ProvideKeystoreManagerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public KeystoreManager get() {
    return provideKeystoreManager(contextProvider.get());
  }

  public static SecurityModule_ProvideKeystoreManagerFactory create(
      Provider<Context> contextProvider) {
    return new SecurityModule_ProvideKeystoreManagerFactory(contextProvider);
  }

  public static KeystoreManager provideKeystoreManager(Context context) {
    return Preconditions.checkNotNullFromProvides(SecurityModule.INSTANCE.provideKeystoreManager(context));
  }
}
