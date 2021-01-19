package xyz.hanoman.messenger.groups.ui.addtogroup;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.groups.GroupChangeException;
import xyz.hanoman.messenger.groups.GroupId;
import xyz.hanoman.messenger.groups.GroupManager;
import xyz.hanoman.messenger.groups.MembershipNotSuitableForV2Exception;
import xyz.hanoman.messenger.groups.ui.GroupChangeErrorCallback;
import xyz.hanoman.messenger.groups.ui.GroupChangeFailureReason;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;

import java.io.IOException;
import java.util.Collections;

final class AddToGroupRepository {

  private static final String TAG = Log.tag(AddToGroupRepository.class);

  private final Context context;

  AddToGroupRepository() {
    this.context = ApplicationDependencies.getApplication();
  }

  public void add(@NonNull RecipientId recipientId,
                  @NonNull Recipient groupRecipient,
                  @NonNull GroupChangeErrorCallback error,
                  @NonNull Runnable success)
  {
    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        GroupId.Push pushGroupId = groupRecipient.requireGroupId().requirePush();

        GroupManager.addMembers(context, pushGroupId, Collections.singletonList(recipientId));

        success.run();
        } catch (GroupChangeException | MembershipNotSuitableForV2Exception | IOException e) {
        Log.w(TAG, e);
        error.onError(GroupChangeFailureReason.fromException(e));
      }
    });
  }
}
