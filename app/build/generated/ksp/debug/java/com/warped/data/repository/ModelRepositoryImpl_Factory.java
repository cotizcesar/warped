package com.warped.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ModelRepositoryImpl_Factory implements Factory<ModelRepositoryImpl> {
  @Override
  public ModelRepositoryImpl get() {
    return newInstance();
  }

  public static ModelRepositoryImpl_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ModelRepositoryImpl newInstance() {
    return new ModelRepositoryImpl();
  }

  private static final class InstanceHolder {
    static final ModelRepositoryImpl_Factory INSTANCE = new ModelRepositoryImpl_Factory();
  }
}
