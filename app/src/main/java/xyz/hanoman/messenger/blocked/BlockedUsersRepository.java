package xyz.hanoman.messenger.blocked;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.RecipientDatabase;
import xyz.hanoman.messenger.groups.GroupChangeBusyException;
import xyz.hanoman.messenger.groups.GroupChangeFailedException;
import xyz.hanoman.messenger.recipients.Recipient;
import xyz.hanoman.messenger.recipients.RecipientId;
import xyz.hanoman.messenger.recipients.RecipientUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class BlockedUsersRepository {

  private static final String TAG = Log.tag(BlockedUsersRepository.class);

  private final Context context;

  BlockedUsersRepository(@NonNull Context context) {
    this.context = context;
  }

  void getBlocked(@NonNull Consumer<List<Recipient>> blockedUsers) {
    SignalExecutors.BOUNDED.execute(() -> {
      RecipientDatabase db = DatabaseFactory.getRecipientDatabase(context);
      try (RecipientDatabase.RecipientReader reader = db.readerForBlocked(db.getBlocked())) {
        int count = reader.getCount();
        if (count == 0) {
          blockedUsers.accept(Collections.emptyList());
        } else {
          List<Recipient> recipients = new ArrayList<>();
          while (reader.getNext() != null) {
            recipients.add(reader.getCurrent());
          }
          blockedUsers.accept(recipients);
        }
      }
    });
  }

  void block(@NonNull RecipientId recipientId, @NonNull Runnable success, @NonNull Runnable failure) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        RecipientUtil.block(context, Recipient.resolved(recipientId));
        success.run();
      } catch (IOException | GroupChangeFailedException | GroupChangeBusyException e) {
        Log.w(TAG, "block: failed to block recipient: ", e);
        failure.run();
      }
    });
  }

  void createAndBlock(@NonNull String number, @NonNull Runnable success) {
    SignalExecutors.BOUNDED.execute(() -> {
      RecipientUtil.blockNonGroup(context, Recipient.external(context, number));
      success.run();
    });
  }

  void unblock(@NonNull RecipientId recipientId, @NonNull Runnable success) {
    SignalExecutors.BOUNDED.execute(() -> {
      RecipientUtil.unblock(context, Recipient.resolved(recipientId));
      success.run();
    });
  }
}
