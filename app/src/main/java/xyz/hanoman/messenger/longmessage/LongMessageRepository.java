package xyz.hanoman.messenger.longmessage;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import xyz.hanoman.messenger.conversation.ConversationMessage.ConversationMessageFactory;
import xyz.hanoman.messenger.database.DatabaseFactory;
import xyz.hanoman.messenger.database.MessageDatabase;
import xyz.hanoman.messenger.database.MmsDatabase;
import xyz.hanoman.messenger.database.SmsDatabase;
import xyz.hanoman.messenger.database.model.MessageRecord;
import xyz.hanoman.messenger.database.model.MmsMessageRecord;
import xyz.hanoman.messenger.mms.PartAuthority;
import xyz.hanoman.messenger.mms.TextSlide;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;

class LongMessageRepository {

  private final static String TAG = LongMessageRepository.class.getSimpleName();

  private final MessageDatabase mmsDatabase;
  private final MessageDatabase smsDatabase;

  LongMessageRepository(@NonNull Context context) {
    this.mmsDatabase = DatabaseFactory.getMmsDatabase(context);
    this.smsDatabase = DatabaseFactory.getSmsDatabase(context);
  }

  void getMessage(@NonNull Context context, long messageId, boolean isMms, @NonNull Callback<Optional<LongMessage>> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      if (isMms) {
        callback.onComplete(getMmsLongMessage(context, mmsDatabase, messageId));
      } else {
        callback.onComplete(getSmsLongMessage(context, smsDatabase, messageId));
      }
    });
  }

  @WorkerThread
  private Optional<LongMessage> getMmsLongMessage(@NonNull Context context, @NonNull MessageDatabase mmsDatabase, long messageId) {
    Optional<MmsMessageRecord> record = getMmsMessage(mmsDatabase, messageId);

    if (record.isPresent()) {
      TextSlide textSlide = record.get().getSlideDeck().getTextSlide();

      if (textSlide != null && textSlide.getUri() != null) {
        return Optional.of(new LongMessage(ConversationMessageFactory.createWithUnresolvedData(context, record.get()), readFullBody(context, textSlide.getUri())));
      } else {
        return Optional.of(new LongMessage(ConversationMessageFactory.createWithUnresolvedData(context, record.get()), ""));
      }
    } else {
      return Optional.absent();
    }
  }

  @WorkerThread
  private Optional<LongMessage> getSmsLongMessage(@NonNull Context context, @NonNull MessageDatabase smsDatabase, long messageId) {
    Optional<MessageRecord> record = getSmsMessage(smsDatabase, messageId);

    if (record.isPresent()) {
      return Optional.of(new LongMessage(ConversationMessageFactory.createWithUnresolvedData(context, record.get()), ""));
    } else {
      return Optional.absent();
    }
  }


  @WorkerThread
  private Optional<MmsMessageRecord> getMmsMessage(@NonNull MessageDatabase mmsDatabase, long messageId) {
    try (Cursor cursor = mmsDatabase.getMessageCursor(messageId)) {
      return Optional.fromNullable((MmsMessageRecord) MmsDatabase.readerFor(cursor).getNext());
    }
  }

  @WorkerThread
  private Optional<MessageRecord> getSmsMessage(@NonNull MessageDatabase smsDatabase, long messageId) {
    try (Cursor cursor = smsDatabase.getMessageCursor(messageId)) {
      return Optional.fromNullable(SmsDatabase.readerFor(cursor).getNext());
    }
  }

  private String readFullBody(@NonNull Context context, @NonNull Uri uri) {
    try (InputStream stream = PartAuthority.getAttachmentStream(context, uri)) {
      return StreamUtil.readFullyAsString(stream);
    } catch (IOException e) {
      Log.w(TAG, "Failed to read full text body.", e);
      return "";
    }
  }

  interface Callback<T> {
    void onComplete(T result);
  }
}
