package xyz.hanoman.messenger.conversation;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.MessageDatabase;
import xyz.hanoman.messenger.database.ThreadDatabase;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.notifications.MarkReadReceiver;
import xyz.hanoman.messenger.util.Debouncer;
import xyz.hanoman.messenger.util.concurrent.SerialMonoLifoExecutor;

import java.util.List;
import java.util.concurrent.Executor;

class MarkReadHelper {
  private static final String TAG = Log.tag(MarkReadHelper.class);

  private static final long     DEBOUNCE_TIMEOUT = 100;
  private static final Executor EXECUTOR         = new SerialMonoLifoExecutor(SignalExecutors.BOUNDED);

  private final long      threadId;
  private final Context   context;
  private final Debouncer debouncer = new Debouncer(DEBOUNCE_TIMEOUT);
  private       long      latestTimestamp;

  MarkReadHelper(long threadId, @NonNull Context context) {
    this.threadId = threadId;
    this.context  = context.getApplicationContext();
  }

  public void onViewsRevealed(long timestamp) {
    if (timestamp <= latestTimestamp) {
      return;
    }

    latestTimestamp = timestamp;

    debouncer.publish(() -> {
      EXECUTOR.execute(() -> {
        ThreadDatabase                          threadDatabase = DatabaseFactory.getThreadDatabase(context);
        List<MessageDatabase.MarkedMessageInfo> infos          = threadDatabase.setReadSince(threadId, false, timestamp);

        Log.d(TAG, "Marking " + infos.size() + " messages as read.");

        ApplicationDependencies.getMessageNotifier().updateNotification(context);
        MarkReadReceiver.process(context, infos);
      });
    });
  }
}
