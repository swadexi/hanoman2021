package xyz.hanoman.messenger.conversation;

import android.app.Application;
import android.content.Context;
import android.database.ContentObserver;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import org.signal.core.util.concurrent.SignalExecutors;
import xyz.hanoman.messenger.database.DatabaseContentProviders;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.MessageDatabase;
import xyz.hanoman.messenger.database.MmsSmsDatabase;
import xyz.hanoman.messenger.dependencies.ApplicationDependencies;
import xyz.hanoman.messenger.util.concurrent.SerialMonoLifoExecutor;
import org.whispersystems.libsignal.util.Pair;

import java.util.concurrent.Executor;

public class MessageCountsViewModel extends ViewModel {

  private static final Executor EXECUTOR = new SerialMonoLifoExecutor(SignalExecutors.BOUNDED);

  private final Application                      context;
  private final MutableLiveData<Long>            threadId       = new MutableLiveData<>(-1L);
  private final LiveData<Pair<Integer, Integer>> unreadCounts;

  private ContentObserver observer;

  public MessageCountsViewModel() {
    this.context      = ApplicationDependencies.getApplication();
    this.unreadCounts = Transformations.switchMap(Transformations.distinctUntilChanged(threadId), id -> {

      MutableLiveData<Pair<Integer, Integer>> counts = new MutableLiveData<>(new Pair<>(0, 0));

      if (id == -1L) {
        return counts;
      }

      observer = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
          EXECUTOR.execute(() -> {
            counts.postValue(getCounts(context, id));
          });
        }
      };

      observer.onChange(false);

      context.getContentResolver().registerContentObserver(DatabaseContentProviders.Conversation.getUriForThread(id), true, observer);

      return counts;
    });

  }

  void setThreadId(long threadId) {
    this.threadId.setValue(threadId);
  }

  void clearThreadId() {
    this.threadId.postValue(-1L);
  }

  @NonNull LiveData<Integer> getUnreadMessagesCount() {
    return Transformations.map(unreadCounts, Pair::first);
  }

  @NonNull LiveData<Integer> getUnreadMentionsCount() {
    return Transformations.map(unreadCounts, Pair::second);
  }

  private Pair<Integer, Integer> getCounts(@NonNull Context context, long threadId) {
    MmsSmsDatabase  mmsSmsDatabase     = DatabaseFactory.getMmsSmsDatabase(context);
    MessageDatabase mmsDatabase        = DatabaseFactory.getMmsDatabase(context);
    int             unreadCount        = mmsSmsDatabase.getUnreadCount(threadId);
    int             unreadMentionCount = mmsDatabase.getUnreadMentionCount(threadId);

    return new Pair<>(unreadCount, unreadMentionCount);
  }

  @Override
  protected void onCleared() {
    if (observer != null) {
      context.getContentResolver().unregisterContentObserver(observer);
    }
  }
}
