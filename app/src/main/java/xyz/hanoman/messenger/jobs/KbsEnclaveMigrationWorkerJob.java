package xyz.hanoman.messenger.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.impl.NetworkConstraint;
import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.migrations.KbsEnclaveMigrationJob;
import xyz.hanoman.messenger.pin.PinState;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;

/**
 * Should only be enqueued by {@link KbsEnclaveMigrationJob}. Does the actual work of migrating KBS
 * data to the new enclave and deleting it from the old enclave(s).
 */
public class KbsEnclaveMigrationWorkerJob extends BaseJob {

  public static final String KEY = "KbsEnclaveMigrationWorkerJob";

  private static final String TAG = Log.tag(KbsEnclaveMigrationWorkerJob.class);

  public KbsEnclaveMigrationWorkerJob() {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(Parameters.IMMORTAL)
                       .setMaxAttempts(Parameters.UNLIMITED)
                       .setQueue("KbsEnclaveMigrationWorkerJob")
                       .setMaxInstancesForFactory(1)
                       .build());
  }

  private KbsEnclaveMigrationWorkerJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public void onRun() throws IOException, UnauthenticatedResponseException {
    String pin = SignalStore.kbsValues().getPin();

    if (SignalStore.kbsValues().hasOptedOut()) {
      Log.w(TAG, "Opted out of KBS! Nothing to migrate.");
      return;
    }

    if (pin == null) {
      Log.w(TAG, "No PIN available! Can't migrate!");
      return;
    }

    PinState.onMigrateToNewEnclave(pin);
    Log.i(TAG, "Migration successful!");
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof IOException ||
           e instanceof UnauthenticatedResponseException;
  }

  @Override
  public void onFailure() {
    throw new AssertionError("This job should never fail. " + getClass().getSimpleName());
  }

  public static class Factory implements Job.Factory<KbsEnclaveMigrationWorkerJob> {
    @Override
    public @NonNull KbsEnclaveMigrationWorkerJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new KbsEnclaveMigrationWorkerJob(parameters);
    }
  }
}
