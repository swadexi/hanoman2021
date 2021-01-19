package xyz.hanoman.messenger.recipients.ui.managerecipient;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.color.MaterialColor;
import xyz.hanoman.messenger.color.MaterialColors;
import xyz.hanoman.messenger.contacts.sync.DirectoryHelper;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.GroupDatabase;
import xyz.hanoman.messenger.database.IdentityDatabase;
import xyz.hanoman.messenger.database.ThreadDatabase;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.jobs.MultiDeviceContactUpdateJob;
import xyz.hanoman.messenger.mms.OutgoingExpirationUpdateMessage;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.sms.MessageSender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class ManageRecipientRepository {

  private static final String TAG = Log.tag(ManageRecipientRepository.class);

  private final Context     context;
  private final RecipientId recipientId;

  ManageRecipientRepository(@NonNull Context context, @NonNull RecipientId recipientId) {
    this.context     = context;
    this.recipientId = recipientId;
  }

  public RecipientId getRecipientId() {
    return recipientId;
  }

  void getThreadId(@NonNull Consumer<Long> onGetThreadId) {
    SignalExecutors.BOUNDED.execute(() -> onGetThreadId.accept(getThreadId()));
  }

  @WorkerThread
  private long getThreadId() {
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    Recipient      groupRecipient = Recipient.resolved(recipientId);

    return threadDatabase.getThreadIdFor(groupRecipient);
  }

  void getIdentity(@NonNull Consumer<IdentityDatabase.IdentityRecord> callback) {
    SignalExecutors.BOUNDED.execute(() -> callback.accept(DatabaseFactory.getIdentityDatabase(context)
                                                  .getIdentity(recipientId)
                                                  .orNull()));
  }

  void setExpiration(int newExpirationTime) {
    SignalExecutors.BOUNDED.execute(() -> {
      DatabaseFactory.getRecipientDatabase(context).setExpireMessages(recipientId, newExpirationTime);
      OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(Recipient.resolved(recipientId), System.currentTimeMillis(), newExpirationTime * 1000L);
      MessageSender.send(context, outgoingMessage, getThreadId(), false, null);
    });
  }

  void getGroupMembership(@NonNull Consumer<List<RecipientId>> onComplete) {
    SignalExecutors.BOUNDED.execute(() -> {
      GroupDatabase                   groupDatabase   = DatabaseFactory.getGroupDatabase(context);
      List<GroupDatabase.GroupRecord> groupRecords    = groupDatabase.getPushGroupsContainingMember(recipientId);
      ArrayList<RecipientId>          groupRecipients = new ArrayList<>(groupRecords.size());

      for (GroupDatabase.GroupRecord groupRecord : groupRecords) {
        groupRecipients.add(groupRecord.getRecipientId());
      }

      onComplete.accept(groupRecipients);
    });
  }

  public void getRecipient(@NonNull Consumer<Recipient> recipientCallback) {
    SignalExecutors.BOUNDED.execute(() -> recipientCallback.accept(Recipient.resolved(recipientId)));
  }

  void setMuteUntil(long until) {
    SignalExecutors.BOUNDED.execute(() -> DatabaseFactory.getRecipientDatabase(context).setMuted(recipientId, until));
  }

  void setColor(int color) {
    SignalExecutors.BOUNDED.execute(() -> {
      MaterialColor selectedColor = MaterialColors.CONVERSATION_PALETTE.getByColor(context, color);
      if (selectedColor != null) {
        DatabaseFactory.getRecipientDatabase(context).setColor(recipientId, selectedColor);
        ApplicationDependencies.getJobManager().add(new MultiDeviceContactUpdateJob(recipientId));
      }
    });
  }

  void refreshRecipient() {
    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        DirectoryHelper.refreshDirectoryFor(context, Recipient.resolved(recipientId), false);
      } catch (IOException e) {
        Log.w(TAG, "Failed to refresh user after adding to contacts.");
      }
    });
  }

  @WorkerThread
  @NonNull List<Recipient> getSharedGroups(@NonNull RecipientId recipientId) {
    return Stream.of(DatabaseFactory.getGroupDatabase(context)
                                    .getPushGroupsContainingMember(recipientId))
                 .filter(g -> g.getMembers().contains(Recipient.self().getId()))
                 .map(GroupDatabase.GroupRecord::getRecipientId)
                 .map(Recipient::resolved)
                 .sortBy(gr -> gr.getDisplayName(context))
                 .toList();
  }

  void getActiveGroupCount(@NonNull Consumer<Integer> onComplete) {
    SignalExecutors.BOUNDED.execute(() -> onComplete.accept(DatabaseFactory.getGroupDatabase(context).getActiveGroupCount()));
  }
}
