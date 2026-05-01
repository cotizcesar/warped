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
public final class ActiveModelSelection_Factory implements Factory<ActiveModelSelection> {
  @Override
  public ActiveModelSelection get() {
    return newInstance();
  }

  public static ActiveModelSelection_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ActiveModelSelection newInstance() {
    return new ActiveModelSelection();
  }

  private static final class InstanceHolder {
    static final ActiveModelSelection_Factory INSTANCE = new ActiveModelSelection_Factory();
  }
}
