package com.warped.domain.model;

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
public final class ParameterStore_Factory implements Factory<ParameterStore> {
  @Override
  public ParameterStore get() {
    return newInstance();
  }

  public static ParameterStore_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ParameterStore newInstance() {
    return new ParameterStore();
  }

  private static final class InstanceHolder {
    static final ParameterStore_Factory INSTANCE = new ParameterStore_Factory();
  }
}
