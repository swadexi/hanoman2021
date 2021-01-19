package xyz.hanoman.messenger.migrations;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobs.ProfileUploadJob;

/**
 * Schedules a re-upload of the users profile.
 */
public final class ProfileMigrationJob extends MigrationJob {

  private static final String TAG = Log.tag(ProfileMigrationJob.class);

  public static final String KEY = "ProfileMigrationJob";

  ProfileMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private ProfileMigrationJob(@NonNull Parameters parameters) {
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
    Log.i(TAG, "Scheduling profile upload job");
    ApplicationDependencies.getJobManager().add(new ProfileUploadJob());
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<ProfileMigrationJob> {
    @Override
    public @NonNull ProfileMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new ProfileMigrationJob(parameters);
    }
  }
}
