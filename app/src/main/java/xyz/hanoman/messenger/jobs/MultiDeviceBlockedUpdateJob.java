package xyz.hanoman.messenger.jobs;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.crypto.UnidentifiedAccessUtil;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.RecipientDatabase;
import xyz.hanoman.messenger.database.RecipientDatabase.RecipientReader;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.impl.NetworkConstraint;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientUtil;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MultiDeviceBlockedUpdateJob extends BaseJob {

  public static final String KEY = "MultiDeviceBlockedUpdateJob";

  @SuppressWarnings("unused")
  private static final String TAG = MultiDeviceBlockedUpdateJob.class.getSimpleName();

  public MultiDeviceBlockedUpdateJob() {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("MultiDeviceBlockedUpdateJob")
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build());
  }

  private MultiDeviceBlockedUpdateJob(@NonNull Job.Parameters parameters) {
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
  public void onRun()
      throws IOException, UntrustedIdentityException
  {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    RecipientDatabase database = DatabaseFactory.getRecipientDatabase(context);

    try (RecipientReader reader = database.readerForBlocked(database.getBlocked())) {
      List<SignalServiceAddress> blockedIndividuals = new LinkedList<>();
      List<byte[]>               blockedGroups      = new LinkedList<>();

      Recipient recipient;

      while ((recipient = reader.getNext()) != null) {
        if (recipient.isPushGroup()) {
          blockedGroups.add(recipient.requireGroupId().getDecodedId());
        } else if (recipient.hasServiceIdentifier()) {
          blockedIndividuals.add(RecipientUtil.toSignalServiceAddress(context, recipient));
        }
      }

      SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
      messageSender.sendMessage(SignalServiceSyncMessage.forBlocked(new BlockedListMessage(blockedIndividuals, blockedGroups)),
                                UnidentifiedAccessUtil.getAccessForSync(context));
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<MultiDeviceBlockedUpdateJob> {
    @Override
    public @NonNull MultiDeviceBlockedUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MultiDeviceBlockedUpdateJob(parameters);
    }
  }
}
