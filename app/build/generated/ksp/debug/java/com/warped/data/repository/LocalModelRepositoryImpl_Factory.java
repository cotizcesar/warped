package com.warped.data.repository;

import com.warped.data.local.db.dao.LocalModelDao;
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
public final class LocalModelRepositoryImpl_Factory implements Factory<LocalModelRepositoryImpl> {
  private final Provider<LocalModelDao> localModelDaoProvider;

  private LocalModelRepositoryImpl_Factory(Provider<LocalModelDao> localModelDaoProvider) {
    this.localModelDaoProvider = localModelDaoProvider;
  }

  @Override
  public LocalModelRepositoryImpl get() {
    return newInstance(localModelDaoProvider.get());
  }

  public static LocalModelRepositoryImpl_Factory create(
      Provider<LocalModelDao> localModelDaoProvider) {
    return new LocalModelRepositoryImpl_Factory(localModelDaoProvider);
  }

  public static LocalModelRepositoryImpl newInstance(LocalModelDao localModelDao) {
    return new LocalModelRepositoryImpl(localModelDao);
  }
}
