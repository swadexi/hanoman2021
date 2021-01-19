package xyz.hanoman.messenger.jobs;


import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.crypto.IdentityKeyUtil;
import xyz.hanoman.messenger.crypto.PreKeyUtil;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.impl.NetworkConstraint;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.util.concurrent.TimeUnit;

public class RotateSignedPreKeyJob extends BaseJob {

  public static final String KEY = "RotateSignedPreKeyJob";

  private static final String TAG = RotateSignedPreKeyJob.class.getSimpleName();

  public RotateSignedPreKeyJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("RotateSignedPreKeyJob")
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxInstancesForFactory(1)
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .setLifespan(TimeUnit.DAYS.toMillis(2))
                           .build());
  }

  private RotateSignedPreKeyJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws Exception {
    Log.i(TAG, "Rotating signed prekey...");

    SignalServiceAccountManager accountManager     = ApplicationDependencies.getSignalServiceAccountManager();
    IdentityKeyPair             identityKey        = IdentityKeyUtil.getIdentityKeyPair(context);
    SignedPreKeyRecord          signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(context, identityKey, false);

    accountManager.setSignedPreKey(signedPreKeyRecord);

    PreKeyUtil.setActiveSignedPreKeyId(context, signedPreKeyRecord.getId());
    TextSecurePreferences.setSignedPreKeyRegistered(context, true);
    TextSecurePreferences.setSignedPreKeyFailureCount(context, 0);

    ApplicationDependencies.getJobManager().add(new CleanPreKeysJob());
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
    TextSecurePreferences.setSignedPreKeyFailureCount(context, TextSecurePreferences.getSignedPreKeyFailureCount(context) + 1);
  }

  public static final class Factory implements Job.Factory<RotateSignedPreKeyJob> {
    @Override
    public @NonNull RotateSignedPreKeyJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RotateSignedPreKeyJob(parameters);
    }
  }
}
