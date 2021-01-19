package xyz.hanoman.messenger.jobs;


import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.crypto.UnidentifiedAccessUtil;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.GroupDatabase;
import xyz.hanoman.messenger.database.GroupDatabase.GroupRecord;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.jobmanager.Data;
import xyz.hanoman.messenger.jobmanager.Job;
import xyz.hanoman.messenger.jobmanager.impl.NetworkConstraint;
import xyz.hanoman.messenger.profiles.AvatarHelper;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.recipients.RecipientUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup.Type;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PushGroupUpdateJob extends BaseJob {

  public static final String KEY = "PushGroupUpdateJob";

  private static final String TAG = PushGroupUpdateJob.class.getSimpleName();

  private static final String KEY_SOURCE   = "source";
  private static final String KEY_GROUP_ID = "group_id";

  private final RecipientId source;
  private final GroupId     groupId;

  public PushGroupUpdateJob(@NonNull RecipientId source, @NonNull GroupId groupId) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
        source,
        groupId);
  }

  private PushGroupUpdateJob(@NonNull Job.Parameters parameters, RecipientId source, @NonNull GroupId groupId) {
    super(parameters);

    this.source  = source;
    this.groupId = groupId;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putString(KEY_SOURCE, source.serialize())
                             .putString(KEY_GROUP_ID, groupId.toString())
                             .build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    GroupDatabase           groupDatabase = DatabaseFactory.getGroupDatabase(context);
    Optional<GroupRecord>   record        = groupDatabase.getGroup(groupId);
    SignalServiceAttachment avatar        = null;

    if (record == null || !record.isPresent()) {
      Log.w(TAG, "No information for group record info request: " + groupId.toString());
      return;
    }

    if (AvatarHelper.hasAvatar(context, record.get().getRecipientId())) {
      avatar = SignalServiceAttachmentStream.newStreamBuilder()
                                            .withContentType("image/jpeg")
                                            .withStream(AvatarHelper.getAvatar(context, record.get().getRecipientId()))
                                            .withLength(AvatarHelper.getAvatarLength(context, record.get().getRecipientId()))
                                            .build();
    }

    List<SignalServiceAddress> members = new LinkedList<>();

    for (RecipientId member : record.get().getMembers()) {
      Recipient recipient = Recipient.resolved(member);
      members.add(RecipientUtil.toSignalServiceAddress(context, recipient));
    }

    SignalServiceGroup groupContext = SignalServiceGroup.newBuilder(Type.UPDATE)
                                                        .withAvatar(avatar)
                                                        .withId(groupId.getDecodedId())
                                                        .withMembers(members)
                                                        .withName(record.get().getTitle())
                                                        .build();

    RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);

    SignalServiceDataMessage message = SignalServiceDataMessage.newBuilder()
                                                               .asGroupMessage(groupContext)
                                                               .withTimestamp(System.currentTimeMillis())
                                                               .withExpiration(groupRecipient.getExpireMessages())
                                                               .build();

    SignalServiceMessageSender messageSender = ApplicationDependencies.getSignalServiceMessageSender();
    Recipient                  recipient     = Recipient.resolved(source);

    messageSender.sendMessage(RecipientUtil.toSignalServiceAddress(context, recipient),
                              UnidentifiedAccessUtil.getAccessFor(context, recipient),
                              message);
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    Log.w(TAG, e);
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<PushGroupUpdateJob> {
    @Override
    public @NonNull PushGroupUpdateJob create(@NonNull Parameters parameters, @NonNull xyz.hanoman.messenger.jobmanager.Data data) {
      return new PushGroupUpdateJob(parameters,
                                    RecipientId.from(data.getString(KEY_SOURCE)),
                                    GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)));
    }
  }
}
