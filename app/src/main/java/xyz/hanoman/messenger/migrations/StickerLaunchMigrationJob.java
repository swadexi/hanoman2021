package xyz.hanoman.messenger.migrations;

import android.content.Context;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.StickerDatabase;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.JobManager;
import xyz.hanoman.messenger.jobs.MultiDeviceStickerPackOperationJob;
import xyz.hanoman.messenger.jobs.StickerPackDownloadJob;
import xyz.hanoman.messenger.stickers.BlessedPacks;
import xyz.hanoman.messenger.util.TextSecurePreferences;

public class StickerLaunchMigrationJob extends MigrationJob {

  public static final String KEY = "StickerLaunchMigrationJob";

  StickerLaunchMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private StickerLaunchMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return false;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    installPack(context, BlessedPacks.ZOZO);
    installPack(context, BlessedPacks.BANDIT);
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  private static void installPack(@NonNull Context context, @NonNull BlessedPacks.Pack pack) {
    JobManager      jobManager      = ApplicationDependencies.getJobManager();
    StickerDatabase stickerDatabase = DatabaseFactory.getStickerDatabase(context);

    if (stickerDatabase.isPackAvailableAsReference(pack.getPackId())) {
      stickerDatabase.markPackAsInstalled(pack.getPackId(), false);
    }

    jobManager.add(StickerPackDownloadJob.forInstall(pack.getPackId(), pack.getPackKey(), false));

    if (TextSecurePreferences.isMultiDevice(context)) {
      jobManager.add(new MultiDeviceStickerPackOperationJob(pack.getPackId(), pack.getPackKey(), MultiDeviceStickerPackOperationJob.Type.INSTALL));
    }
  }

  public static class Factory implements Job.Factory<StickerLaunchMigrationJob> {
    @Override
    public @NonNull
    StickerLaunchMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new StickerLaunchMigrationJob(parameters);
    }
  }
}
