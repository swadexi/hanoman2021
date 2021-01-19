package xyz.hanoman.messenger.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.impl.NetworkConstraint;
import xyz.hanoman.messenger.util.FeatureFlags;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RemoteConfigRefreshJob extends BaseJob {

  private static final String TAG = Log.tag(RemoteConfigRefreshJob.class);

  public static final String KEY = "RemoteConfigRefreshJob";

  public RemoteConfigRefreshJob() {
    this(new Job.Parameters.Builder()
                           .setQueue("RemoteConfigRefreshJob")
                           .setMaxInstancesForFactory(1)
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .build());
  }

  private RemoteConfigRefreshJob(@NonNull Parameters parameters) {
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
  protected void onRun() throws Exception {
    if (!TextSecurePreferences.isPushRegistered(context)) {
      Log.w(TAG, "Not registered. Skipping.");
      return;
    }

    Map<String, Object> config = ApplicationDependencies.getSignalServiceAccountManager().getRemoteConfig();
    FeatureFlags.update(config);
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<RemoteConfigRefreshJob> {
    @Override
    public @NonNull RemoteConfigRefreshJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RemoteConfigRefreshJob(parameters);
    }
  }
}
