package xyz.hanoman.messenger.registration;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobs.DirectoryRefreshJob;
import xyz.hanoman.messenger.jobs.StorageSyncJob;
import xyz.hanoman.messenger.keyvalue.SignalStore;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.util.TextSecurePreferences;

public final class RegistrationUtil {

  private static final String TAG = Log.tag(RegistrationUtil.class);

  private RegistrationUtil() {}

  /**
   * There's several events where a registration may or may not be considered complete based on what
   * path a user has taken. This will only truly mark registration as complete if all of the
   * requirements are met.
   */
  public static void maybeMarkRegistrationComplete(@NonNull Context context) {
    if (!SignalStore.registrationValues().isRegistrationComplete() &&
        TextSecurePreferences.isPushRegistered(context)            &&
        !Recipient.self().getProfileName().isEmpty()               &&
        (SignalStore.kbsValues().hasPin() || SignalStore.kbsValues().hasOptedOut()))
    {
      Log.i(TAG, "Marking registration completed.", new Throwable());
      SignalStore.registrationValues().setRegistrationComplete();
      ApplicationDependencies.getJobManager().startChain(new StorageSyncJob())
                                             .then(new DirectoryRefreshJob(false))
                                             .enqueue();
    } else if (!SignalStore.registrationValues().isRegistrationComplete()) {
      Log.i(TAG, "Registration is not yet complete.", new Throwable());
    }
  }
}
