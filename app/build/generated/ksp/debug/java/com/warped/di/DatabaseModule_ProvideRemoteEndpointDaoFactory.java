package com.warped.di;

import com.warped.data.local.db.AppDatabase;
import com.warped.data.local.db.dao.RemoteEndpointDao;
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
public final class DatabaseModule_ProvideRemoteEndpointDaoFactory implements Factory<RemoteEndpointDao> {
  private final Provider<AppDatabase> dbProvider;

  private DatabaseModule_ProvideRemoteEndpointDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public RemoteEndpointDao get() {
    return provideRemoteEndpointDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideRemoteEndpointDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideRemoteEndpointDaoFactory(dbProvider);
  }

  public static RemoteEndpointDao provideRemoteEndpointDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideRemoteEndpointDao(db));
  }
}
