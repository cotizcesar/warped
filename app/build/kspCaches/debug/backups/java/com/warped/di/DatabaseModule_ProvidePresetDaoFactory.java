package com.warped.di;

import com.warped.data.local.db.AppDatabase;
import com.warped.data.local.db.dao.PresetDao;
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
public final class DatabaseModule_ProvidePresetDaoFactory implements Factory<PresetDao> {
  private final Provider<AppDatabase> dbProvider;

  private DatabaseModule_ProvidePresetDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public PresetDao get() {
    return providePresetDao(dbProvider.get());
  }

  public static DatabaseModule_ProvidePresetDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvidePresetDaoFactory(dbProvider);
  }

  public static PresetDao providePresetDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.providePresetDao(db));
  }
}
