package com.warped.data.repository;

import com.warped.data.local.db.dao.ConversationDao;
import com.warped.data.local.db.dao.MessageDao;
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
public final class ChatRepositoryImpl_Factory implements Factory<ChatRepositoryImpl> {
  private final Provider<ConversationDao> conversationDaoProvider;

  private final Provider<MessageDao> messageDaoProvider;

  private ChatRepositoryImpl_Factory(Provider<ConversationDao> conversationDaoProvider,
      Provider<MessageDao> messageDaoProvider) {
    this.conversationDaoProvider = conversationDaoProvider;
    this.messageDaoProvider = messageDaoProvider;
  }

  @Override
  public ChatRepositoryImpl get() {
    return newInstance(conversationDaoProvider.get(), messageDaoProvider.get());
  }

  public static ChatRepositoryImpl_Factory create(Provider<ConversationDao> conversationDaoProvider,
      Provider<MessageDao> messageDaoProvider) {
    return new ChatRepositoryImpl_Factory(conversationDaoProvider, messageDaoProvider);
  }

  public static ChatRepositoryImpl newInstance(ConversationDao conversationDao,
      MessageDao messageDao) {
    return new ChatRepositoryImpl(conversationDao, messageDao);
  }
}
