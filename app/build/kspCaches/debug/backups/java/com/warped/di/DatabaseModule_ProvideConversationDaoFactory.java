package com.warped.di;

import com.warped.data.local.db.AppDatabase;
import com.warped.data.local.db.dao.ConversationDao;
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
public final class DatabaseModule_ProvideConversationDaoFactory implements Factory<ConversationDao> {
  private final Provider<AppDatabase> dbProvider;

  private DatabaseModule_ProvideConversationDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ConversationDao get() {
    return provideConversationDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideConversationDaoFactory create(
      Provider<AppDatabase> dbProvider) {
    return new DatabaseModule_ProvideConversationDaoFactory(dbProvider);
  }

  public static ConversationDao provideConversationDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideConversationDao(db));
  }
}
