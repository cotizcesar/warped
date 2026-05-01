package com.warped.data.repository;

import com.warped.data.local.db.dao.PresetDao;
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
public final class PresetRepositoryImpl_Factory implements Factory<PresetRepositoryImpl> {
  private final Provider<PresetDao> presetDaoProvider;

  private PresetRepositoryImpl_Factory(Provider<PresetDao> presetDaoProvider) {
    this.presetDaoProvider = presetDaoProvider;
  }

  @Override
  public PresetRepositoryImpl get() {
    return newInstance(presetDaoProvider.get());
  }

  public static PresetRepositoryImpl_Factory create(Provider<PresetDao> presetDaoProvider) {
    return new PresetRepositoryImpl_Factory(presetDaoProvider);
  }

  public static PresetRepositoryImpl newInstance(PresetDao presetDao) {
    return new PresetRepositoryImpl(presetDao);
  }
}
