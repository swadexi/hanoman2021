package xyz.hanoman.messenger.migrations;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.impl.NetworkConstraint;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.util.TextSecurePreferences;

import java.io.IOException;
import java.util.UUID;

/**
 * Couple migrations steps need to happen after we move to UUIDS.
 *  - We need to get our own UUID.
 *  - We need to fetch the new UUID sealed sender cert.
 *  - We need to do a directory sync so we can guarantee that all active users have UUIDs.
 */
public class UuidMigrationJob extends MigrationJob {

  public static final String KEY = "UuidMigrationJob";

  private static final String TAG = Log.tag(UuidMigrationJob.class);

  UuidMigrationJob() {
    this(new Parameters.Builder().addConstraint(NetworkConstraint.KEY).build());
  }

  private UuidMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  boolean isUiBlocking() {
    return false;
  }

  @Override
  void performMigration() throws Exception {
    if (!TextSecurePreferences.isPushRegistered(context) || TextUtils.isEmpty(TextSecurePreferences.getLocalNumber(context))) {
      Log.w(TAG, "Not registered! Skipping migration, as it wouldn't do anything.");
      return;
    }

    ensureSelfRecipientExists(context);
    fetchOwnUuid(context);
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  private static void ensureSelfRecipientExists(@NonNull Context context) {
    DatabaseFactory.getRecipientDatabase(context).getOrInsertFromE164(TextSecurePreferences.getLocalNumber(context));
  }

  private static void fetchOwnUuid(@NonNull Context context) throws IOException {
    RecipientId self      = Recipient.self().getId();
    UUID        localUuid = ApplicationDependencies.getSignalServiceAccountManager().getOwnUuid();

    DatabaseFactory.getRecipientDatabase(context).markRegisteredOrThrow(self, localUuid);
    TextSecurePreferences.setLocalUuid(context, localUuid);
  }

  public static class Factory implements Job.Factory<UuidMigrationJob> {
    @Override
    public @NonNull UuidMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new UuidMigrationJob(parameters);
    }
  }
}
