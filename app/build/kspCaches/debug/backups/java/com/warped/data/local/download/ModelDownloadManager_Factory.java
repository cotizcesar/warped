package com.warped.data.local.download;

import android.content.Context;
import androidx.work.WorkManager;
import com.warped.data.local.db.dao.DownloadCheckpointDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class ModelDownloadManager_Factory implements Factory<ModelDownloadManager> {
  private final Provider<Context> contextProvider;

  private final Provider<WorkManager> workManagerProvider;

  private final Provider<DownloadCheckpointDao> checkpointDaoProvider;

  private ModelDownloadManager_Factory(Provider<Context> contextProvider,
      Provider<WorkManager> workManagerProvider,
      Provider<DownloadCheckpointDao> checkpointDaoProvider) {
    this.contextProvider = contextProvider;
    this.workManagerProvider = workManagerProvider;
    this.checkpointDaoProvider = checkpointDaoProvider;
  }

  @Override
  public ModelDownloadManager get() {
    return newInstance(contextProvider.get(), workManagerProvider.get(), checkpointDaoProvider.get());
  }

  public static ModelDownloadManager_Factory create(Provider<Context> contextProvider,
      Provider<WorkManager> workManagerProvider,
      Provider<DownloadCheckpointDao> checkpointDaoProvider) {
    return new ModelDownloadManager_Factory(contextProvider, workManagerProvider, checkpointDaoProvider);
  }

  public static ModelDownloadManager newInstance(Context context, WorkManager workManager,
      DownloadCheckpointDao checkpointDao) {
    return new ModelDownloadManager(context, workManager, checkpointDao);
  }
}
