package xyz.hanoman.messenger.groups.ui.invitesandrequests.requesting;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.groups.GroupChangeException;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.groups.GroupManager;
import xyz.hanoman.messenger.groups.ui.GroupChangeFailureReason;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.util.AsynchronousCallback;

import java.io.IOException;
import java.util.Collection;

/**
 * Repository for modifying the requesting members on a single group.
 */
final class RequestingMemberRepository {

  private static final String TAG = Log.tag(RequestingMemberRepository.class);

  private final Context    context;
  private final GroupId.V2 groupId;

  RequestingMemberRepository(@NonNull Context context, @NonNull GroupId.V2 groupId) {
    this.context = context.getApplicationContext();
    this.groupId = groupId;
  }

  void approveRequests(@NonNull Collection<RecipientId> recipientIds,
                       @NonNull AsynchronousCallback.WorkerThread<Void, GroupChangeFailureReason> callback)
  {
    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        GroupManager.approveRequests(context, groupId, recipientIds);
        callback.onComplete(null);
      } catch (GroupChangeException | IOException e) {
        Log.w(TAG, e);
        callback.onError(GroupChangeFailureReason.fromException(e));
      }
    });
  }

  void denyRequests(@NonNull Collection<RecipientId> recipientIds,
                    @NonNull AsynchronousCallback.WorkerThread<Void, GroupChangeFailureReason> callback)
  {
    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        GroupManager.denyRequests(context, groupId, recipientIds);
        callback.onComplete(null);
      } catch (GroupChangeException | IOException e) {
        Log.w(TAG, e);
        callback.onError(GroupChangeFailureReason.fromException(e));
      }
    });
  }
}
