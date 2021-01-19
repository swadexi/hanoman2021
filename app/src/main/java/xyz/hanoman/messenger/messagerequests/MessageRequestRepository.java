package xyz.hanoman.messenger.messagerequests;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.GroupDatabase;
import xyz.hanoman.messenger.database.MessageDatabase;
import xyz.hanoman.messenger.database.RecipientDatabase;
import xyz.hanoman.messenger.database.ThreadDatabase;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.groups.GroupChangeException;
import xyz.hanoman.messenger.groups.GroupManager;
import xyz.hanoman.messenger.groups.ui.GroupChangeErrorCallback;
import xyz.hanoman.messenger.groups.ui.GroupChangeFailureReason;
import xyz.hanoman.messenger.jobs.MultiDeviceMessageRequestResponseJob;
import xyz.hanoman.messenger.jobs.SendViewedReceiptJob;
import xyz.hanoman.messenger.notifications.MarkReadReceiver;
import xyz.hanoman.messenger.recipients.LiveRecipient;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.recipients.RecipientUtil;
import xyz.hanoman.messenger.sms.MessageSender;
import xyz.hanoman.messenger.util.FeatureFlags;
import xyz.hanoman.messenger.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

final class MessageRequestRepository {

  private static final String TAG = Log.tag(MessageRequestRepository.class);

  private final Context  context;
  private final Executor executor;

  MessageRequestRepository(@NonNull Context context) {
    this.context  = context.getApplicationContext();
    this.executor = SignalExecutors.BOUNDED;
  }

  void getGroups(@NonNull RecipientId recipientId, @NonNull Consumer<List<String>> onGroupsLoaded) {
    executor.execute(() -> {
      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
      onGroupsLoaded.accept(groupDatabase.getPushGroupNamesContainingMember(recipientId));
    });
  }

  void getMemberCount(@NonNull RecipientId recipientId, @NonNull Consumer<GroupMemberCount> onMemberCountLoaded) {
    executor.execute(() -> {
      GroupDatabase                       groupDatabase = DatabaseFactory.getGroupDatabase(context);
      Optional<GroupDatabase.GroupRecord> groupRecord   = groupDatabase.getGroup(recipientId);
      onMemberCountLoaded.accept(groupRecord.transform(record -> {
        if (record.isV2Group()) {
          DecryptedGroup decryptedGroup = record.requireV2GroupProperties().getDecryptedGroup();
          return new GroupMemberCount(decryptedGroup.getMembersCount(), decryptedGroup.getPendingMembersCount());
        } else {
          return new GroupMemberCount(record.getMembers().size(), 0);
        }
      }).or(GroupMemberCount.ZERO));
    });
  }

  @WorkerThread
  @NonNull MessageRequestState getMessageRequestState(@NonNull Recipient recipient, long threadId) {
    if (recipient.isBlocked()) {
      if (recipient.isGroup()) {
        return MessageRequestState.BLOCKED_GROUP;
      } else {
        return MessageRequestState.BLOCKED_INDIVIDUAL;
      }
    } else if (threadId <= 0) {
      return MessageRequestState.NONE;
    } else if (recipient.isPushV2Group()) {
      switch (getGroupMemberLevel(recipient.getId())) {
        case NOT_A_MEMBER:
          return MessageRequestState.NONE;
        case PENDING_MEMBER:
          return MessageRequestState.GROUP_V2_INVITE;
        default:
          if (RecipientUtil.isMessageRequestAccepted(context, threadId)) {
            return MessageRequestState.NONE;
          } else {
            return MessageRequestState.GROUP_V2_ADD;
          }
      }
    } else if (!RecipientUtil.isLegacyProfileSharingAccepted(recipient) && isLegacyThread(recipient)) {
      if (recipient.isGroup()) {
        return MessageRequestState.LEGACY_GROUP_V1;
      } else {
        return MessageRequestState.LEGACY_INDIVIDUAL;
      }
    } else if (recipient.isPushV1Group()) {
      if (RecipientUtil.isMessageRequestAccepted(context, threadId)) {
        if (FeatureFlags.groupsV1ForcedMigration()) {
          if (recipient.getParticipants().size() > FeatureFlags.groupLimits().getHardLimit()) {
            return MessageRequestState.DEPRECATED_GROUP_V1_TOO_LARGE;
          } else {
            return MessageRequestState.DEPRECATED_GROUP_V1;
          }
        } else {
          return MessageRequestState.NONE;
        }
      } else if (!recipient.isActiveGroup()) {
        return MessageRequestState.NONE;
      } else {
        return MessageRequestState.GROUP_V1;
      }
    } else {
      if (RecipientUtil.isMessageRequestAccepted(context, threadId)) {
        return MessageRequestState.NONE;
      } else {
        return MessageRequestState.INDIVIDUAL;
      }
    }
  }

  void acceptMessageRequest(@NonNull LiveRecipient liveRecipient,
                            long threadId,
                            @NonNull Runnable onMessageRequestAccepted,
                            @NonNull GroupChangeErrorCallback error)
  {
    executor.execute(()-> {
      if (liveRecipient.get().isPushV2Group()) {
        try {
          Log.i(TAG, "GV2 accepting invite");
          GroupManager.acceptInvite(context, liveRecipient.get().requireGroupId().requireV2());

          RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
          recipientDatabase.setProfileSharing(liveRecipient.getId(), true);

          onMessageRequestAccepted.run();
        } catch (GroupChangeException | IOException e) {
          Log.w(TAG, e);
          error.onError(GroupChangeFailureReason.fromException(e));
        }
      } else {
        RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
        recipientDatabase.setProfileSharing(liveRecipient.getId(), true);

        MessageSender.sendProfileKey(context, threadId);

        List<MessageDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context)
                                                                            .setEntireThreadRead(threadId);
        ApplicationDependencies.getMessageNotifier().updateNotification(context);
        MarkReadReceiver.process(context, messageIds);

        List<MessageDatabase.MarkedMessageInfo> viewedInfos = DatabaseFactory.getMmsDatabase(context)
                                                                             .getViewedIncomingMessages(threadId);

        ApplicationDependencies.getJobManager()
                               .add(new SendViewedReceiptJob(threadId,
                                                             liveRecipient.getId(),
                                                             Stream.of(viewedInfos)
                                                                   .map(info -> info.getSyncMessageId().getTimetamp())
                                                                   .toList()));

        if (TextSecurePreferences.isMultiDevice(context)) {
          ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forAccept(liveRecipient.getId()));
        }

        onMessageRequestAccepted.run();
      }
    });
  }

  void deleteMessageRequest(@NonNull LiveRecipient recipient,
                            long threadId,
                            @NonNull Runnable onMessageRequestDeleted,
                            @NonNull GroupChangeErrorCallback error)
  {
    executor.execute(() -> {
      Recipient resolved = recipient.resolve();

      if (resolved.isGroup() && resolved.requireGroupId().isPush()) {
        try {
          GroupManager.leaveGroupFromBlockOrMessageRequest(context, resolved.requireGroupId().requirePush());
        } catch (GroupChangeException | IOException e) {
          Log.w(TAG, e);
          error.onError(GroupChangeFailureReason.fromException(e));
          return;
        }
      }

      if (TextSecurePreferences.isMultiDevice(context)) {
        ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forDelete(recipient.getId()));
      }

      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
      threadDatabase.deleteConversation(threadId);

      onMessageRequestDeleted.run();
    });
  }

  void blockMessageRequest(@NonNull LiveRecipient liveRecipient,
                           @NonNull Runnable onMessageRequestBlocked,
                           @NonNull GroupChangeErrorCallback error)
  {
    executor.execute(() -> {
      Recipient recipient = liveRecipient.resolve();
      try {
        RecipientUtil.block(context, recipient);
      } catch (GroupChangeException | IOException e) {
        Log.w(TAG, e);
        error.onError(GroupChangeFailureReason.fromException(e));
        return;
      }
      liveRecipient.refresh();

      if (TextSecurePreferences.isMultiDevice(context)) {
        ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forBlock(liveRecipient.getId()));
      }

      onMessageRequestBlocked.run();
    });
  }

  void blockAndDeleteMessageRequest(@NonNull LiveRecipient liveRecipient,
                                    long threadId,
                                    @NonNull Runnable onMessageRequestBlocked,
                                    @NonNull GroupChangeErrorCallback error)
  {
    executor.execute(() -> {
      Recipient recipient = liveRecipient.resolve();
      try{
        RecipientUtil.block(context, recipient);
      } catch (GroupChangeException | IOException e) {
        Log.w(TAG, e);
        error.onError(GroupChangeFailureReason.fromException(e));
        return;
      }
      liveRecipient.refresh();

      DatabaseFactory.getThreadDatabase(context).deleteConversation(threadId);

      if (TextSecurePreferences.isMultiDevice(context)) {
        ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forBlockAndDelete(liveRecipient.getId()));
      }

      onMessageRequestBlocked.run();
    });
  }

  void unblockAndAccept(@NonNull LiveRecipient liveRecipient, long threadId, @NonNull Runnable onMessageRequestUnblocked) {
    executor.execute(() -> {
      Recipient recipient = liveRecipient.resolve();

      RecipientUtil.unblock(context, recipient);

      List<MessageDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context)
                                                                          .setEntireThreadRead(threadId);
      ApplicationDependencies.getMessageNotifier().updateNotification(context);
      MarkReadReceiver.process(context, messageIds);

      if (TextSecurePreferences.isMultiDevice(context)) {
        ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forAccept(liveRecipient.getId()));
      }

      onMessageRequestUnblocked.run();
    });
  }

  private GroupDatabase.MemberLevel getGroupMemberLevel(@NonNull RecipientId recipientId) {
    return DatabaseFactory.getGroupDatabase(context)
                          .getGroup(recipientId)
                          .transform(g -> g.memberLevel(Recipient.self()))
                          .or(GroupDatabase.MemberLevel.NOT_A_MEMBER);
  }


  @WorkerThread
  private boolean isLegacyThread(@NonNull Recipient recipient) {
    Context context  = ApplicationDependencies.getApplication();
    Long    threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient.getId());

    return threadId != null &&
        (RecipientUtil.hasSentMessageInThread(context, threadId) || RecipientUtil.isPreMessageRequestThread(context, threadId));
  }
}
