package xyz.hanoman.messenger.profiles.spoofing;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.ThreadDatabase;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.groups.GroupChangeException;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.groups.GroupManager;
import xyz.hanoman.messenger.jobs.MultiDeviceMessageRequestResponseJob;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.recipients.RecipientUtil;
import xyz.hanoman.messenger.util.TextSecurePreferences;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

class ReviewCardRepository {

  private final Context     context;
  private final GroupId.V2  groupId;
  private final RecipientId recipientId;

  protected ReviewCardRepository(@NonNull Context context,
                                 @NonNull GroupId.V2 groupId)
  {
    this.context     = context;
    this.groupId     = groupId;
    this.recipientId = null;
  }

  protected ReviewCardRepository(@NonNull Context context,
                                 @NonNull RecipientId recipientId)
  {
    this.context     = context;
    this.groupId     = null;
    this.recipientId = recipientId;
  }

  void loadRecipients(@NonNull OnRecipientsLoadedListener onRecipientsLoadedListener) {
    if (groupId != null) {
      loadRecipientsForGroup(groupId, onRecipientsLoadedListener);
    } else if (recipientId != null) {
      loadSimilarRecipients(context, recipientId, onRecipientsLoadedListener);
    } else {
      throw new AssertionError();
    }
  }

  @WorkerThread
  int loadGroupsInCommonCount(@NonNull ReviewRecipient reviewRecipient) {
    return ReviewUtil.getGroupsInCommonCount(context, reviewRecipient.getRecipient().getId());
  }

  void block(@NonNull ReviewCard reviewCard, @NonNull Runnable onActionCompleteListener) {
    if (recipientId == null) {
      throw new UnsupportedOperationException();
    }

    SignalExecutors.BOUNDED.execute(() -> {
      RecipientUtil.blockNonGroup(context, reviewCard.getReviewRecipient());
      onActionCompleteListener.run();
    });
  }

  void delete(@NonNull ReviewCard reviewCard, @NonNull Runnable onActionCompleteListener) {
    if (recipientId == null) {
      throw new UnsupportedOperationException();
    }

    SignalExecutors.BOUNDED.execute(() -> {
      Recipient resolved = Recipient.resolved(recipientId);

      if (resolved.isGroup()) throw new AssertionError();

      if (TextSecurePreferences.isMultiDevice(context)) {
        ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forDelete(recipientId));
      }

      ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
      long           threadId       = Objects.requireNonNull(threadDatabase.getThreadIdFor(recipientId));

      threadDatabase.deleteConversation(threadId);
      onActionCompleteListener.run();
    });
  }

  void removeFromGroup(@NonNull ReviewCard reviewCard, @NonNull OnRemoveFromGroupListener onRemoveFromGroupListener) {
    if (groupId == null) {
      throw new UnsupportedOperationException();
    }

    SignalExecutors.BOUNDED.execute(() -> {
      try {
        GroupManager.ejectFromGroup(context, groupId, reviewCard.getReviewRecipient());
        onRemoveFromGroupListener.onActionCompleted();
      } catch (GroupChangeException | IOException e) {
        onRemoveFromGroupListener.onActionFailed();
      }
    });
  }

  private static void loadRecipientsForGroup(@NonNull GroupId.V2 groupId,
                                             @NonNull OnRecipientsLoadedListener onRecipientsLoadedListener)
  {
    SignalExecutors.BOUNDED.execute(() -> onRecipientsLoadedListener.onRecipientsLoaded(ReviewUtil.getDuplicatedRecipients(groupId)));
  }

  private static void loadSimilarRecipients(@NonNull Context context,
                                            @NonNull RecipientId recipientId,
                                            @NonNull OnRecipientsLoadedListener onRecipientsLoadedListener)
  {
    SignalExecutors.BOUNDED.execute(() -> {
      Recipient resolved = Recipient.resolved(recipientId);

      List<RecipientId> recipientIds = DatabaseFactory.getRecipientDatabase(context)
          .getSimilarRecipientIds(resolved);

      if (recipientIds.isEmpty()) {
        onRecipientsLoadedListener.onRecipientsLoadFailed();
        return;
      }

      List<ReviewRecipient> recipients = Stream.of(recipientIds)
          .map(Recipient::resolved)
          .map(ReviewRecipient::new)
          .sorted(new ReviewRecipient.Comparator(context, recipientId))
          .toList();

      onRecipientsLoadedListener.onRecipientsLoaded(recipients);
    });
  }

  interface OnRecipientsLoadedListener {
    void onRecipientsLoaded(@NonNull List<ReviewRecipient> recipients);
    void onRecipientsLoadFailed();
  }

  interface OnRemoveFromGroupListener {
    void onActionCompleted();
    void onActionFailed();
  }
}
