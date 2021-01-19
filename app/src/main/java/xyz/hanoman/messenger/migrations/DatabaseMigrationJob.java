package xyz.hanoman.messenger.migrations;

import androidx.annotation.NonNull;

import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;

/**
 * Triggers a database access, forcing the database to upgrade if it hasn't already. Should be used
 * when you expect a database migration to take a particularly long time.
 */
public class DatabaseMigrationJob extends MigrationJob {

  public static final String KEY = "DatabaseMigrationJob";

  DatabaseMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private DatabaseMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return true;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    DatabaseFactory.getInstance(context).triggerDatabaseAccess();
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<DatabaseMigrationJob> {
    @Override
    public @NonNull DatabaseMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new DatabaseMigrationJob(parameters);
    }
  }
}
