package com.warped.di;

import com.warped.data.local.db.AppDatabase;
import com.warped.data.local.db.dao.LocalModelDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideLocalModelDaoFactory implements Factory<LocalModelDao> {
  private final Provider<AppDatabase> dbProvider;

  private DatabaseModule_ProvideLocalModelDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public LocalModelDao get() {
    return provideLocalModelDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideLocalModelDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideLocalModelDaoFactory(dbProvider);
  }

  public static LocalModelDao provideLocalModelDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideLocalModelDao(db));
  }
}
