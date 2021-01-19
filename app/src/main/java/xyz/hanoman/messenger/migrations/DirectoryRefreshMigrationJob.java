package xyz.hanoman.messenger.migrations;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.contacts.sync.DirectoryHelper;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.util.TextSecurePreferences;

import java.io.IOException;

/**
 * Does a full directory refresh.
 */
public final class DirectoryRefreshMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(DirectoryRefreshMigrationJob.class);

  public static final String KEY = "DirectoryRefreshMigrationJob";

  DirectoryRefreshMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private DirectoryRefreshMigrationJob(@NonNull Parameters parameters) {
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
  public void performMigration() throws IOException {
    if (!TextSecurePreferences.isPushRegistered(context)           ||
        !SignalStore.registrationValues().isRegistrationComplete() ||
        TextSecurePreferences.getLocalUuid(context) == null)
    {
      Log.w(TAG, "Not registered! Skipping.");
      return;
    }

    DirectoryHelper.refreshDirectory(context, true);
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  public static class Factory implements Job.Factory<DirectoryRefreshMigrationJob> {
    @Override
    public @NonNull DirectoryRefreshMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new DirectoryRefreshMigrationJob(parameters);
    }
  }
}
