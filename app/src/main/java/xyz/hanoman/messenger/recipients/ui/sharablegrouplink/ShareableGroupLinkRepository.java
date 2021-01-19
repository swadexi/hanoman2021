package xyz.hanoman.messenger.recipients.ui.sharablegrouplink;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.storageservice.protos.groups.AccessControl;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.groups.GroupChangeBusyException;
import xyz.hanoman.messenger.groups.GroupChangeFailedException;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.groups.GroupInsufficientRightsException;
import xyz.hanoman.messenger.groups.GroupManager;
import xyz.hanoman.messenger.groups.GroupNotAMemberException;
import xyz.hanoman.messenger.groups.ui.GroupChangeFailureReason;
import xyz.hanoman.messenger.util.AsynchronousCallback;

import java.io.IOException;

final class ShareableGroupLinkRepository {

  private final Context    context;
  private final GroupId.V2 groupId;

  ShareableGroupLinkRepository(@NonNull Context context, @NonNull GroupId.V2 groupId) {
    this.context = context;
    this.groupId = groupId;
  }

  void cycleGroupLinkPassword(@NonNull AsynchronousCallback.WorkerThread<Void, GroupChangeFailureReason> callback) {
    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        GroupManager.cycleGroupLinkPassword(context, groupId);
        callback.onComplete(null);
      } catch (GroupNotAMemberException | GroupChangeFailedException | GroupInsufficientRightsException | IOException | GroupChangeBusyException e) {
        callback.onError(GroupChangeFailureReason.fromException(e));
      }
    });
  }

  void toggleGroupLinkEnabled(@NonNull AsynchronousCallback.WorkerThread<Void, GroupChangeFailureReason> callback) {
    setGroupLinkEnabledState(toggleGroupLinkState(true, false), callback);
  }

  void toggleGroupLinkApprovalRequired(@NonNull AsynchronousCallback.WorkerThread<Void, GroupChangeFailureReason> callback) {
    setGroupLinkEnabledState(toggleGroupLinkState(false, true), callback);
  }

  private void setGroupLinkEnabledState(@NonNull GroupManager.GroupLinkState state,
                                        @NonNull AsynchronousCallback.WorkerThread<Void, GroupChangeFailureReason> callback)
  {
    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        GroupManager.setGroupLinkEnabledState(context, groupId, state);
        callback.onComplete(null);
      } catch (GroupNotAMemberException | GroupChangeFailedException | GroupInsufficientRightsException | IOException | GroupChangeBusyException e) {
        callback.onError(GroupChangeFailureReason.fromException(e));
      }
    });
  }

  @WorkerThread
  private GroupManager.GroupLinkState toggleGroupLinkState(boolean toggleEnabled, boolean toggleApprovalNeeded) {
    AccessControl.AccessRequired currentState = DatabaseFactory.getGroupDatabase(context)
                                                               .getGroup(groupId)
                                                               .get()
                                                               .requireV2GroupProperties()
                                                               .getDecryptedGroup()
                                                               .getAccessControl()
                                                               .getAddFromInviteLink();

    boolean enabled;
    boolean approvalNeeded;

    switch (currentState) {
      case UNKNOWN:
      case UNSATISFIABLE:
      case UNRECOGNIZED:
      case MEMBER:
        enabled        = false;
        approvalNeeded = false;
        break;
      case ANY:
        enabled        = true;
        approvalNeeded = false;
        break;
      case ADMINISTRATOR:
        enabled        = true;
        approvalNeeded = true;
        break;
      default: throw new AssertionError();
    }

    if (toggleApprovalNeeded) {
      approvalNeeded = !approvalNeeded;
    }

    if (toggleEnabled) {
      enabled = !enabled;
    }

    if (approvalNeeded && enabled) {
      return GroupManager.GroupLinkState.ENABLED_WITH_APPROVAL;
    } else {
      if (enabled) {
        return GroupManager.GroupLinkState.ENABLED;
      }
    }

    return GroupManager.GroupLinkState.DISABLED;
  }
}
